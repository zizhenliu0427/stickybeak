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
                objectMapper
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
                      "amount_total": 3500,
                      "currency": "aud"
                    }
                  }
                }
                """;
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_stripe_456");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

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
}
