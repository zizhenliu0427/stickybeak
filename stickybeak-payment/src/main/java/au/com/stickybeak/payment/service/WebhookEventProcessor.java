package au.com.stickybeak.payment.service;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.entity.PaymentWebhook;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.mapper.PaymentWebhookMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * webhook 事件的重处理逻辑（解析 → 更新支付流水 → 发 MQ），
 * 在 webhookTaskExecutor 线程池上异步执行；自身事务独立于请求线程。
 */
@Service
public class WebhookEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventProcessor.class);

    private final PaymentWebhookMapper paymentWebhookMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public WebhookEventProcessor(PaymentWebhookMapper paymentWebhookMapper,
                                 PaymentRecordMapper paymentRecordMapper,
                                 RabbitTemplate rabbitTemplate,
                                 ObjectMapper objectMapper) {
        this.paymentWebhookMapper = paymentWebhookMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理已落档的 webhook 事件。失败置 processed=2 留档（人工重试），异常不再抛给调用方。
     */
    @Transactional(rollbackFor = Exception.class)
    public void process(String eventId, String payload, boolean isMock) {
        PaymentWebhook record = paymentWebhookMapper.selectOne(
                new LambdaQueryWrapper<PaymentWebhook>().eq(PaymentWebhook::getEventId, eventId));
        if (record == null) {
            log.warn("webhook record vanished before processing: {}", eventId);
            return;
        }
        if (record.getProcessed() != null && record.getProcessed() == 1) {
            return; // 已被并发路径处理
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.has("type") ? root.get("type").asText() : (isMock ? "mock.payment.paid" : "unknown");
            record.setEventType(eventType);

            String orderNo = null;
            String transactionId = null;
            BigDecimal amount = null;
            String currency = null;
            String customerEmail = null;

            if (isMock && root.has("orderNo")) {
                orderNo = root.get("orderNo").asText();
                transactionId = root.has("transactionId") ? root.get("transactionId").asText()
                        : "mock_tx_" + System.currentTimeMillis();
                amount = root.has("amount") ? new BigDecimal(root.get("amount").asText()) : null;
                currency = root.has("currency") ? root.get("currency").asText() : "AUD";
                customerEmail = root.has("email") ? root.get("email").asText() : null;
            } else if (root.has("data") && root.get("data").has("object")) {
                JsonNode obj = root.get("data").get("object");
                if (obj.hasNonNull("client_reference_id")) {
                    orderNo = obj.get("client_reference_id").asText();
                } else if (obj.has("metadata") && obj.get("metadata").hasNonNull("orderNo")) {
                    orderNo = obj.get("metadata").get("orderNo").asText();
                }
                if (obj.hasNonNull("payment_intent")) {
                    transactionId = obj.get("payment_intent").asText();
                } else if (obj.hasNonNull("id")) {
                    transactionId = obj.get("id").asText();
                }
                if (obj.hasNonNull("amount_total")) {
                    amount = BigDecimal.valueOf(obj.get("amount_total").asLong())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                }
                if (obj.hasNonNull("currency")) {
                    currency = obj.get("currency").asText().toUpperCase();
                }
                if (obj.has("customer_details") && obj.get("customer_details").hasNonNull("email")) {
                    customerEmail = obj.get("customer_details").get("email").asText();
                }
            }

            if (StringUtils.hasText(orderNo)) {
                PaymentRecord paymentRecord = paymentRecordMapper.selectOne(
                        new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderNo, orderNo));
                LocalDateTime now = LocalDateTime.now();
                if (paymentRecord != null) {
                    paymentRecord.setStatus("succeeded");
                    if (transactionId != null) {
                        paymentRecord.setTransactionId(transactionId);
                    }
                    paymentRecord.setPayTime(now);
                    paymentRecordMapper.updateById(paymentRecord);
                }

                OrderPaidEvent event = new OrderPaidEvent();
                event.setOrderNo(orderNo);
                event.setAmount(amount != null ? amount
                        : (paymentRecord != null ? paymentRecord.getAmount() : BigDecimal.ZERO));
                event.setCurrency(currency != null ? currency
                        : (paymentRecord != null ? paymentRecord.getCurrency() : "AUD"));
                event.setPaymentMethod(paymentRecord != null ? paymentRecord.getPaymentMethod() : "card");
                event.setTransactionId(transactionId != null ? transactionId
                        : (paymentRecord != null ? paymentRecord.getTransactionId() : ""));
                event.setPayTime(now);
                event.setEmail(customerEmail);

                log.info("Publishing OrderPaidEvent (async pool): orderNo={}", orderNo);
                rabbitTemplate.convertAndSend(OrderPaidEvent.EXCHANGE, OrderPaidEvent.ROUTING_KEY, event);
            }

            record.setProcessed(1);
            record.setErrorMsg(null);
            paymentWebhookMapper.updateById(record);
        } catch (Exception e) {
            log.error("Async webhook processing failed for {}: {}", eventId, e.getMessage(), e);
            // 留档 processed=2，人工/定时任务可捞起重试；不再外抛（调用方线程已返回 200）
            try {
                record.setProcessed(2);
                record.setErrorMsg(e.getMessage());
                paymentWebhookMapper.updateById(record);
            } catch (Exception inner) {
                log.error("Failed to mark webhook {} as failed: {}", eventId, inner.getMessage());
            }
        }
    }
}
