package au.com.stickybeak.payment.service.impl;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.entity.PaymentWebhook;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.mapper.PaymentWebhookMapper;
import au.com.stickybeak.payment.provider.MockPaymentProvider;
import au.com.stickybeak.payment.provider.StripePaymentProvider;
import au.com.stickybeak.payment.service.WebhookService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private final StripePaymentProvider stripePaymentProvider;
    private final MockPaymentProvider mockPaymentProvider;
    private final PaymentWebhookMapper paymentWebhookMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public WebhookServiceImpl(StripePaymentProvider stripePaymentProvider,
                              MockPaymentProvider mockPaymentProvider,
                              PaymentWebhookMapper paymentWebhookMapper,
                              PaymentRecordMapper paymentRecordMapper,
                              RabbitTemplate rabbitTemplate,
                              ObjectMapper objectMapper) {
        this.stripePaymentProvider = stripePaymentProvider;
        this.mockPaymentProvider = mockPaymentProvider;
        this.paymentWebhookMapper = paymentWebhookMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
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

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.has("type") ? root.get("type").asText() : (isMock ? "mock.payment.paid" : "unknown");
            webhookRecord.setEventType(eventType);

            // Extract order details
            String orderNo = null;
            String transactionId = null;
            BigDecimal amount = null;
            String currency = null;
            String customerEmail = null;

            if (isMock && root.has("orderNo")) {
                orderNo = root.get("orderNo").asText();
                transactionId = root.has("transactionId") ? root.get("transactionId").asText() : "mock_tx_" + System.currentTimeMillis();
                amount = root.has("amount") ? new BigDecimal(root.get("amount").asText()) : null;
                currency = root.has("currency") ? root.get("currency").asText() : "AUD";
                customerEmail = root.has("email") ? root.get("email").asText() : null;
            } else if (root.has("data") && root.get("data").has("object")) {
                JsonNode obj = root.get("data").get("object");

                // Check client_reference_id or metadata.orderNo
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
                    long cents = obj.get("amount_total").asLong();
                    amount = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                }

                if (obj.hasNonNull("currency")) {
                    currency = obj.get("currency").asText().toUpperCase();
                }

                if (obj.has("customer_details") && obj.get("customer_details").hasNonNull("email")) {
                    customerEmail = obj.get("customer_details").get("email").asText();
                }
            }

            log.info("Extracted webhook info: eventId={}, eventType={}, orderNo={}, transactionId={}",
                    eventId, eventType, orderNo, transactionId);

            if (StringUtils.hasText(orderNo)) {
                // Update payment record
                PaymentRecord record = paymentRecordMapper.selectOne(
                        new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderNo, orderNo)
                );

                LocalDateTime now = LocalDateTime.now();
                if (record != null) {
                    record.setStatus("succeeded");
                    if (transactionId != null) {
                        record.setTransactionId(transactionId);
                    }
                    record.setPayTime(now);
                    paymentRecordMapper.updateById(record);
                }

                // Publish OrderPaidEvent to RabbitMQ
                OrderPaidEvent event = new OrderPaidEvent();
                event.setOrderNo(orderNo);
                event.setAmount(amount != null ? amount : (record != null ? record.getAmount() : BigDecimal.ZERO));
                event.setCurrency(currency != null ? currency : (record != null ? record.getCurrency() : "AUD"));
                event.setPaymentMethod(record != null ? record.getPaymentMethod() : "card");
                event.setTransactionId(transactionId != null ? transactionId : (record != null ? record.getTransactionId() : ""));
                event.setPayTime(now);
                event.setEmail(customerEmail);

                log.info("Publishing OrderPaidEvent to RabbitMQ: exchange={}, routingKey={}, orderNo={}",
                        OrderPaidEvent.EXCHANGE, OrderPaidEvent.ROUTING_KEY, orderNo);
                rabbitTemplate.convertAndSend(OrderPaidEvent.EXCHANGE, OrderPaidEvent.ROUTING_KEY, event);
            }

            webhookRecord.setProcessed(1);
            webhookRecord.setErrorMsg(null);
            paymentWebhookMapper.updateById(webhookRecord);
            return "SUCCESS";

        } catch (Exception e) {
            log.error("Failed to process webhook event {}: {}", eventId, e.getMessage(), e);
            webhookRecord.setProcessed(2);
            webhookRecord.setErrorMsg(e.getMessage());
            paymentWebhookMapper.updateById(webhookRecord);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Webhook processing error: " + e.getMessage());
        }
    }
}
