package au.com.stickybeak.payment.service.impl;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.common.tx.AfterCommit;
import au.com.stickybeak.payment.config.WebhookExecutorConfig;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.entity.PaymentWebhook;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.mapper.PaymentWebhookMapper;
import au.com.stickybeak.payment.provider.MockPaymentProvider;
import au.com.stickybeak.payment.provider.StripePaymentProvider;
import au.com.stickybeak.payment.service.RefundService;
import au.com.stickybeak.payment.service.WebhookService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 支付回调：验签 → 按 event_id 幂等落档 → 校验并推进支付（事件类型、金额、币种都要对得上）。
 * <p>
 * 默认同步处理：失败时事务回滚并返回 5xx，Stripe 按自己的退避策略重试。
 * 开启 stickybeak.webhook.async.enabled（故事线 9.6）后，同步段只做验签 + 落档，提交后把同一段处理逻辑
 * 交给 webhookTaskExecutor 线程池执行，立即返回 ACCEPTED；代价是失败不再触发 Stripe 重试，
 * 只留档 processed=2 等人工 / 下次同一事件重投时再处理。
 */
@Service
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_IGNORED = "IGNORED";
    public static final String RESULT_REJECTED = "REJECTED";
    public static final String RESULT_ACCEPTED = "ACCEPTED";

    /** 视为"已付款"的 Stripe 事件；async_payment_succeeded 对应异步支付方式的延迟到账 */
    private static final Set<String> STRIPE_PAID_EVENT_TYPES = Set.of(
            "checkout.session.completed",
            "checkout.session.async_payment_succeeded"
    );
    private static final String STRIPE_REFUND_EVENT_TYPE = "charge.refunded";

    private final StripePaymentProvider stripePaymentProvider;
    private final MockPaymentProvider mockPaymentProvider;
    private final PaymentWebhookMapper paymentWebhookMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RefundService refundService;
    /** 异步模式才注入；为空时同步处理 */
    private ThreadPoolTaskExecutor asyncExecutor;
    private TransactionTemplate transactionTemplate;

    public WebhookServiceImpl(StripePaymentProvider stripePaymentProvider,
                              MockPaymentProvider mockPaymentProvider,
                              PaymentWebhookMapper paymentWebhookMapper,
                              PaymentRecordMapper paymentRecordMapper,
                              RabbitTemplate rabbitTemplate,
                              ObjectMapper objectMapper,
                              RefundService refundService) {
        this.stripePaymentProvider = stripePaymentProvider;
        this.mockPaymentProvider = mockPaymentProvider;
        this.paymentWebhookMapper = paymentWebhookMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.refundService = refundService;
    }

    @Autowired(required = false)
    public void enableAsyncProcessing(@Qualifier(WebhookExecutorConfig.EXECUTOR) ThreadPoolTaskExecutor asyncExecutor,
                                      TransactionTemplate transactionTemplate) {
        this.asyncExecutor = asyncExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleStripeWebhook(String payload, String sigHeader) {
        log.info("Received Stripe webhook request");

        // 1. Verify signature
        String eventId = stripePaymentProvider.verifyWebhook(payload, sigHeader);

        // 2. Process event with idempotency
        return processEvent(eventId, payload, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleMockWebhook(String payload) {
        log.info("Received Mock webhook request");

        String eventId;
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("eventId")) {
                eventId = root.get("eventId").asText();
            } else if (root.has("id")) {
                eventId = root.get("id").asText();
            } else {
                eventId = "evt_mock_" + System.currentTimeMillis();
            }
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Malformed JSON payload");
        }

        return processEvent(eventId, payload, true);
    }

    private String processEvent(String eventId, String payload, boolean isMock) {
        // Idempotency check: query by event_id
        PaymentWebhook existing = paymentWebhookMapper.selectOne(
                new LambdaQueryWrapper<PaymentWebhook>().eq(PaymentWebhook::getEventId, eventId)
        );

        if (existing != null && existing.getProcessed() == 1) {
            log.info("Webhook event {} already successfully processed. Idempotent return.", eventId);
            return "ALREADY_PROCESSED";
        }

        PaymentWebhook webhookRecord = existing;
        if (webhookRecord == null) {
            webhookRecord = new PaymentWebhook();
            webhookRecord.setEventId(eventId);
            webhookRecord.setEventType("unknown");
            webhookRecord.setPayload(payload);
            webhookRecord.setProcessed(0);
            try {
                paymentWebhookMapper.insert(webhookRecord);
            } catch (DuplicateKeyException dke) {
                log.info("Concurrent webhook event {} caught by UK constraint.", eventId);
                return "ALREADY_PROCESSED";
            }
        }

        if (asyncExecutor != null) {
            PaymentWebhook accepted = webhookRecord;
            // 落档提交后再投递，线程池里读到的一定是已提交的记录。CallerRunsPolicy：队列满时由请求线程自己跑（背压，不丢事件）
            AfterCommit.run(() -> asyncExecutor.execute(() -> processAsync(accepted, payload, isMock)));
            return RESULT_ACCEPTED;
        }
        return processRecord(webhookRecord, payload, isMock);
    }

    /** 线程池上的独立事务；失败时事务已回滚，在事务外把记录标记为失败留档 */
    private void processAsync(PaymentWebhook webhookRecord, String payload, boolean isMock) {
        try {
            transactionTemplate.executeWithoutResult(status -> processRecord(webhookRecord, payload, isMock));
        } catch (Exception e) {
            log.error("Async webhook processing failed for {}: {}", webhookRecord.getEventId(), e.getMessage());
            try {
                markProcessed(webhookRecord, 2, e.getMessage());
            } catch (Exception inner) {
                log.error("Failed to mark webhook {} as failed: {}", webhookRecord.getEventId(), inner.getMessage());
            }
        }
    }

    private String processRecord(PaymentWebhook webhookRecord, String payload, boolean isMock) {
        String eventId = webhookRecord.getEventId();
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.has("type") ? root.get("type").asText() : (isMock ? "mock.payment.paid" : "unknown");
            webhookRecord.setEventType(eventType);

            if (!isMock && STRIPE_REFUND_EVENT_TYPE.equals(eventType)) {
                return handleChargeRefunded(eventId, root, webhookRecord);
            }

            PaidPayment paid = isMock ? parseMockPayment(root) : parseStripePayment(root, eventType);
            if (paid == null) {
                // 非支付成功事件（如 checkout.session.expired）只记录，不推进订单
                log.info("Ignoring webhook event {} of type {}", eventId, eventType);
                markProcessed(webhookRecord, 1, null);
                return RESULT_IGNORED;
            }

            log.info("Extracted webhook info: eventId={}, eventType={}, orderNo={}, transactionId={}",
                    eventId, eventType, paid.orderNo, paid.transactionId);

            PaymentRecord record = paymentRecordMapper.selectOne(
                    new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderNo, paid.orderNo)
            );
            String rejectReason = verifyAgainstRecord(paid, record, isMock);
            if (rejectReason != null) {
                // 返回 200 让 Stripe 停止重试；记录原因供人工核查
                log.error("Webhook event {} for order {} rejected: {}", eventId, paid.orderNo, rejectReason);
                markProcessed(webhookRecord, 2, rejectReason);
                return RESULT_REJECTED;
            }

            LocalDateTime now = LocalDateTime.now();
            record.setStatus("succeeded");
            if (paid.transactionId != null) {
                record.setTransactionId(paid.transactionId);
            }
            record.setPayTime(now);
            paymentRecordMapper.updateById(record);

            // Publish OrderPaidEvent to RabbitMQ
            OrderPaidEvent event = new OrderPaidEvent();
            event.setOrderNo(paid.orderNo);
            event.setAmount(paid.amount != null ? paid.amount : record.getAmount());
            event.setCurrency(paid.currency != null ? paid.currency : record.getCurrency());
            event.setPaymentMethod(record.getPaymentMethod());
            event.setTransactionId(paid.transactionId != null ? paid.transactionId : record.getTransactionId());
            event.setPayTime(now);
            event.setEmail(paid.email);

            log.info("Publishing OrderPaidEvent to RabbitMQ: exchange={}, routingKey={}, orderNo={}",
                    OrderPaidEvent.EXCHANGE, OrderPaidEvent.ROUTING_KEY, paid.orderNo);
            // 有意在事务内发送（SkyWalking 链路里能看到它早于 commit）：发送失败 → 事务回滚 → Stripe 重试；
            // 发送成功但提交失败 → 重试时再发一次，订单端按状态幂等处理。改成提交后发送反而会在发送失败时丢事件
            rabbitTemplate.convertAndSend(OrderPaidEvent.EXCHANGE, OrderPaidEvent.ROUTING_KEY, event);

            markProcessed(webhookRecord, 1, null);
            return RESULT_SUCCESS;

        } catch (Exception e) {
            log.error("Failed to process webhook event {}: {}", eventId, e.getMessage(), e);
            webhookRecord.setProcessed(2);
            webhookRecord.setErrorMsg(e.getMessage());
            paymentWebhookMapper.updateById(webhookRecord);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Webhook processing error: " + e.getMessage());
        }
    }

    /** 退款到账：只处理全额退款（refunded=true），按 payment_intent 找到支付记录 */
    private String handleChargeRefunded(String eventId, JsonNode root, PaymentWebhook webhookRecord) {
        JsonNode charge = root.path("data").path("object");
        if (!charge.path("refunded").asBoolean(false)) {
            log.info("Ignoring partial refund event {}", eventId);
            markProcessed(webhookRecord, 1, null);
            return RESULT_IGNORED;
        }
        String paymentIntent = charge.path("payment_intent").asText(null);
        String refundId = charge.path("refunds").path("data").path(0).path("id").asText(null);
        if (!refundService.confirmRefund(paymentIntent, refundId)) {
            String reason = "UNKNOWN_PAYMENT_INTENT: " + paymentIntent;
            log.error("Refund event {} rejected: {}", eventId, reason);
            markProcessed(webhookRecord, 2, reason);
            return RESULT_REJECTED;
        }
        markProcessed(webhookRecord, 1, null);
        return RESULT_SUCCESS;
    }

    /** Stripe：只有支付成功的结账事件才算付款，其余事件（expired、unpaid 等）返回 null */
    private PaidPayment parseStripePayment(JsonNode root, String eventType) {
        if (!STRIPE_PAID_EVENT_TYPES.contains(eventType)) {
            return null;
        }
        JsonNode obj = root.path("data").path("object");
        if (!"paid".equals(obj.path("payment_status").asText())) {
            return null;
        }

        PaidPayment paid = new PaidPayment();
        if (obj.hasNonNull("client_reference_id")) {
            paid.orderNo = obj.get("client_reference_id").asText();
        } else if (obj.path("metadata").hasNonNull("orderNo")) {
            paid.orderNo = obj.get("metadata").get("orderNo").asText();
        }
        if (!StringUtils.hasText(paid.orderNo)) {
            return null;
        }

        if (obj.hasNonNull("payment_intent")) {
            paid.transactionId = obj.get("payment_intent").asText();
        } else if (obj.hasNonNull("id")) {
            paid.transactionId = obj.get("id").asText();
        }
        if (obj.hasNonNull("amount_total")) {
            long cents = obj.get("amount_total").asLong();
            paid.amount = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (obj.hasNonNull("currency")) {
            paid.currency = obj.get("currency").asText().toUpperCase();
        }
        if (obj.path("customer_details").hasNonNull("email")) {
            paid.email = obj.get("customer_details").get("email").asText();
        }
        return paid;
    }

    /** Mock（仅开发环境）：金额、币种可省略 */
    private PaidPayment parseMockPayment(JsonNode root) {
        if (!root.hasNonNull("orderNo")) {
            return null;
        }
        PaidPayment paid = new PaidPayment();
        paid.orderNo = root.get("orderNo").asText();
        paid.transactionId = root.has("transactionId") ? root.get("transactionId").asText() : "mock_tx_" + System.currentTimeMillis();
        paid.amount = root.has("amount") ? new BigDecimal(root.get("amount").asText()) : null;
        paid.currency = root.has("currency") ? root.get("currency").asText().toUpperCase() : null;
        paid.email = root.has("email") ? root.get("email").asText() : null;
        return paid;
    }

    /** 实付金额与币种必须与下单时的支付记录一致，返回拒绝原因；一致时返回 null */
    private String verifyAgainstRecord(PaidPayment paid, PaymentRecord record, boolean isMock) {
        if (record == null) {
            return "UNKNOWN_ORDER: no payment record for " + paid.orderNo;
        }
        if (!isMock && (paid.amount == null || paid.currency == null)) {
            return "MISSING_AMOUNT: event has no amount_total or currency";
        }
        if (paid.amount != null && paid.amount.compareTo(record.getAmount()) != 0) {
            return "AMOUNT_MISMATCH: expected " + record.getAmount() + ", got " + paid.amount;
        }
        if (paid.currency != null && !paid.currency.equalsIgnoreCase(record.getCurrency())) {
            return "CURRENCY_MISMATCH: expected " + record.getCurrency() + ", got " + paid.currency;
        }
        return null;
    }

    private void markProcessed(PaymentWebhook webhookRecord, int processed, String errorMsg) {
        webhookRecord.setProcessed(processed);
        webhookRecord.setErrorMsg(errorMsg);
        paymentWebhookMapper.updateById(webhookRecord);
    }

    private static class PaidPayment {
        private String orderNo;
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private String email;
    }
}
