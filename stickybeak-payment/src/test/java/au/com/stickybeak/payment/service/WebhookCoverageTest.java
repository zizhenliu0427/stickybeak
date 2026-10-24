package au.com.stickybeak.payment.service;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.entity.PaymentWebhook;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.mapper.PaymentWebhookMapper;
import au.com.stickybeak.payment.provider.MockPaymentProvider;
import au.com.stickybeak.payment.provider.StripePaymentProvider;
import au.com.stickybeak.payment.service.impl.WebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookCoverageTest {

    @Mock
    private StripePaymentProvider stripePaymentProvider;
    @Mock
    private MockPaymentProvider mockPaymentProvider;
    @Mock
    private PaymentWebhookMapper paymentWebhookMapper;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RefundService refundService;

    private ObjectMapper objectMapper;
    private WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookService = new WebhookServiceImpl(
                stripePaymentProvider,
                mockPaymentProvider,
                paymentWebhookMapper,
                paymentRecordMapper,
                rabbitTemplate,
                objectMapper,
                refundService
        );
    }

    @Test
    @DisplayName("handleStripeWebhook: Valid signature and Stripe payload parsing")
    void testStripeWebhookValidPayload() {
        String payload = """
                {
                  "id": "evt_stripe_123",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "client_reference_id": "SO20260916_STRIPE",
                      "payment_intent": "pi_live_stripe_999",
                      "payment_status": "paid",
                      "amount_total": 5990,
                      "currency": "aud",
                      "customer_details": {
                        "email": "customer@example.com"
                      }
                    }
                  }
                }
                """;
        String sig = "test_sig_header";

        when(stripePaymentProvider.verifyWebhook(payload, sig)).thenReturn("evt_stripe_123");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        PaymentRecord record = new PaymentRecord();
        record.setId(10L);
        record.setOrderNo("SO20260916_STRIPE");
        record.setAmount(new BigDecimal("59.90"));
        record.setCurrency("AUD");
        record.setPaymentMethod("card");
        record.setStatus("pending");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        String result = webhookService.handleStripeWebhook(payload, sig);

        assertEquals("SUCCESS", result);
        assertEquals("succeeded", record.getStatus());
        assertEquals("pi_live_stripe_999", record.getTransactionId());

        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), captor.capture());
        OrderPaidEvent sent = captor.getValue();
        assertEquals("SO20260916_STRIPE", sent.getOrderNo());
        assertEquals("customer@example.com", sent.getEmail());
        assertEquals(new BigDecimal("59.90"), sent.getAmount());
    }

    @Test
    @DisplayName("handleStripeWebhook: Fallback metadata.orderNo extraction")
    void testStripeWebhookMetadataOrderNoFallback() {
        String payload = """
                {
                  "id": "evt_stripe_456",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_456",
                      "metadata": {
                        "orderNo": "SO20260916_METADATA"
                      },
                      "payment_status": "paid",
                      "amount_total": 3500,
                      "currency": "aud"
                    }
                  }
                }
                """;
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_stripe_456");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("SO20260916_METADATA", "35.00", "AUD"));

        String result = webhookService.handleStripeWebhook(payload, "sig");

        assertEquals("SUCCESS", result);
        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), captor.capture());
        assertEquals("SO20260916_METADATA", captor.getValue().getOrderNo());
    }

    @Test
    @DisplayName("handleStripeWebhook: Concurrent DuplicateKeyException caught by UK constraint")
    void testConcurrentDuplicateKeyExceptionHandled() {
        String payload = "{\"id\":\"evt_concurrent\"}";
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_concurrent");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("Duplicate entry")).when(paymentWebhookMapper).insert(any(PaymentWebhook.class));

        String result = webhookService.handleStripeWebhook(payload, "sig");

        assertEquals("ALREADY_PROCESSED", result);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("handleMockWebhook: Fallback auto-generated event ID when missing in payload")
    void testMockWebhookMissingEventIdGeneratesFallback() {
        String payload = "{\"orderNo\":\"SO_MOCK_NO_EVT\",\"amount\":10.00}";
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("SO_MOCK_NO_EVT", "10.00", "AUD"));

        String result = webhookService.handleMockWebhook(payload);

        assertEquals("SUCCESS", result);
        ArgumentCaptor<PaymentWebhook> insertCaptor = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper, times(1)).insert(insertCaptor.capture());
        assertNotNull(insertCaptor.getValue().getEventId());
        assertTrue(insertCaptor.getValue().getEventId().startsWith("evt_mock_"));
    }

    @Test
    @DisplayName("handleMockWebhook: Handles exception and marks processed status 2")
    void testWebhookExceptionMarksFailed() {
        String payload = "{\"eventId\":\"evt_error\",\"orderNo\":\"SO_ERR\"}";
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("SO_ERR", "20.00", "AUD"));
        // Throw exception when updating payment record or sending MQ
        doThrow(new RuntimeException("RabbitMQ connection down")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                webhookService.handleMockWebhook(payload)
        );
        assertTrue(ex.getMessage().contains("Webhook processing error"));

        // Verify status marked 2
        ArgumentCaptor<PaymentWebhook> updateCaptor = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper, times(1)).updateById(updateCaptor.capture());
        assertEquals(2, updateCaptor.getValue().getProcessed());
        assertTrue(updateCaptor.getValue().getErrorMsg().contains("RabbitMQ connection down"));
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.expired does not mark the order paid")
    void testStripeExpiredEventIgnored() {
        String payload = stripeEvent("evt_expired", "checkout.session.expired", "SO_EXPIRED", "unpaid", 5990);
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_expired");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        String result = webhookService.handleStripeWebhook(payload, "sig");

        assertEquals(WebhookServiceImpl.RESULT_IGNORED, result);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
        ArgumentCaptor<PaymentWebhook> captor = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getProcessed());
        assertEquals("checkout.session.expired", captor.getValue().getEventType());
    }

    @Test
    @DisplayName("handleStripeWebhook: completed but unpaid (async payment pending) is ignored")
    void testStripeCompletedButUnpaidIgnored() {
        String payload = stripeEvent("evt_unpaid", "checkout.session.completed", "SO_UNPAID", "unpaid", 5990);
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_unpaid");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        assertEquals(WebhookServiceImpl.RESULT_IGNORED, webhookService.handleStripeWebhook(payload, "sig"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("handleStripeWebhook: async_payment_succeeded marks the order paid")
    void testStripeAsyncPaymentSucceeded() {
        String payload = stripeEvent("evt_async", "checkout.session.async_payment_succeeded", "SO_ASYNC", "paid", 5990);
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_async");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("SO_ASYNC", "59.90", "AUD"));

        assertEquals(WebhookServiceImpl.RESULT_SUCCESS, webhookService.handleStripeWebhook(payload, "sig"));
        verify(rabbitTemplate).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), any(OrderPaidEvent.class));
    }

    @Test
    @DisplayName("handleStripeWebhook: paid amount different from the payment record is rejected")
    void testStripeAmountMismatchRejected() {
        String payload = stripeEvent("evt_cheap", "checkout.session.completed", "SO_CHEAP", "paid", 1);
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_cheap");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        PaymentRecord record = record("SO_CHEAP", "59.90", "AUD");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        String result = webhookService.handleStripeWebhook(payload, "sig");

        assertEquals(WebhookServiceImpl.RESULT_REJECTED, result);
        assertEquals("pending", record.getStatus());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        ArgumentCaptor<PaymentWebhook> captor = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getProcessed());
        assertTrue(captor.getValue().getErrorMsg().startsWith("AMOUNT_MISMATCH"));
    }

    @Test
    @DisplayName("handleStripeWebhook: currency different from the payment record is rejected")
    void testStripeCurrencyMismatchRejected() {
        String payload = stripeEvent("evt_cny", "checkout.session.completed", "SO_CNY", "paid", 5990)
                .replace("\"aud\"", "\"cny\"");
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_cny");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("SO_CNY", "59.90", "AUD"));

        assertEquals(WebhookServiceImpl.RESULT_REJECTED, webhookService.handleStripeWebhook(payload, "sig"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("handleStripeWebhook: paid event for an order without payment record is rejected")
    void testStripeUnknownOrderRejected() {
        String payload = stripeEvent("evt_unknown", "checkout.session.completed", "SO_UNKNOWN", "paid", 5990);
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_unknown");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        assertEquals(WebhookServiceImpl.RESULT_REJECTED, webhookService.handleStripeWebhook(payload, "sig"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("handleMockWebhook: mock amount must also match the payment record")
    void testMockAmountMismatchRejected() {
        String payload = "{\"eventId\":\"evt_mock_cheap\",\"orderNo\":\"SO_MOCK_CHEAP\",\"amount\":0.01}";
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("SO_MOCK_CHEAP", "34.90", "AUD"));

        assertEquals(WebhookServiceImpl.RESULT_REJECTED, webhookService.handleMockWebhook(payload));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("handleStripeWebhook: charge.refunded confirms a pending refund")
    void testChargeRefundedConfirmsRefund() {
        String payload = chargeRefunded("evt_refund", "pi_123", true);
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_refund");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        when(refundService.confirmRefund("pi_123", "re_1")).thenReturn(true);

        assertEquals(WebhookServiceImpl.RESULT_SUCCESS, webhookService.handleStripeWebhook(payload, "sig"));
        verify(refundService).confirmRefund("pi_123", "re_1");
        // 退款事件不能被当成支付成功
        verify(rabbitTemplate, never()).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), any(Object.class));
    }

    @Test
    @DisplayName("handleStripeWebhook: partial refund is ignored")
    void testPartialRefundIgnored() {
        String payload = chargeRefunded("evt_partial", "pi_123", false);
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_partial");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        assertEquals(WebhookServiceImpl.RESULT_IGNORED, webhookService.handleStripeWebhook(payload, "sig"));
        verifyNoInteractions(refundService);
    }

    @Test
    @DisplayName("handleStripeWebhook: refund for an unknown payment intent is rejected")
    void testRefundForUnknownPaymentIntentRejected() {
        String payload = chargeRefunded("evt_unknown_refund", "pi_unknown", true);
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_unknown_refund");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        when(refundService.confirmRefund("pi_unknown", "re_1")).thenReturn(false);

        assertEquals(WebhookServiceImpl.RESULT_REJECTED, webhookService.handleStripeWebhook(payload, "sig"));
        ArgumentCaptor<PaymentWebhook> captor = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getProcessed());
    }

    private static String chargeRefunded(String eventId, String paymentIntent, boolean fullyRefunded) {
        return """
                {
                  "id": "%s",
                  "type": "charge.refunded",
                  "data": {
                    "object": {
                      "id": "ch_1",
                      "payment_intent": "%s",
                      "refunded": %s,
                      "refunds": { "data": [ { "id": "re_1" } ] }
                    }
                  }
                }
                """.formatted(eventId, paymentIntent, fullyRefunded);
    }

    private static String stripeEvent(String eventId, String type, String orderNo, String paymentStatus, long amountTotal) {
        return """
                {
                  "id": "%s",
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "cs_test_x",
                      "client_reference_id": "%s",
                      "payment_status": "%s",
                      "amount_total": %d,
                      "currency": "aud"
                    }
                  }
                }
                """.formatted(eventId, type, orderNo, paymentStatus, amountTotal);
    }

    private static PaymentRecord record(String orderNo, String amount, String currency) {
        PaymentRecord record = new PaymentRecord();
        record.setId(99L);
        record.setOrderNo(orderNo);
        record.setAmount(new BigDecimal(amount));
        record.setCurrency(currency);
        record.setPaymentMethod("card");
        record.setStatus("pending");
        return record;
    }
}
