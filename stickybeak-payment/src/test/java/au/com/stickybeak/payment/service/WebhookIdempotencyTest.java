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
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebhookIdempotencyTest {

    private StripePaymentProvider stripePaymentProvider;
    private MockPaymentProvider mockPaymentProvider;
    private PaymentWebhookMapper paymentWebhookMapper;
    private PaymentRecordMapper paymentRecordMapper;
    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        stripePaymentProvider = mock(StripePaymentProvider.class);
        mockPaymentProvider = mock(MockPaymentProvider.class);
        paymentWebhookMapper = mock(PaymentWebhookMapper.class);
        paymentRecordMapper = mock(PaymentRecordMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
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
    void testWebhookProcessSuccessFirstTime() {
        String payload = "{\"eventId\":\"evt_1001\",\"orderNo\":\"SO20260916001\",\"amount\":120.00,\"currency\":\"AUD\",\"transactionId\":\"pi_test_1\"}";

        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setOrderNo("SO20260916001");
        record.setStatus("pending");
        record.setPaymentMethod("card");
        record.setCurrency("AUD");
        record.setAmount(new BigDecimal("120.00"));
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        String result = webhookService.handleMockWebhook(payload);

        assertEquals("SUCCESS", result);
        verify(paymentWebhookMapper, times(1)).insert(any(PaymentWebhook.class));
        verify(paymentRecordMapper, times(1)).updateById(record);
        assertEquals("succeeded", record.getStatus());
        assertEquals("pi_test_1", record.getTransactionId());

        // Check RabbitMQ message published
        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), captor.capture());
        OrderPaidEvent sentEvent = captor.getValue();
        assertEquals("SO20260916001", sentEvent.getOrderNo());
        assertEquals(0, new BigDecimal("120.00").compareTo(sentEvent.getAmount()));
    }

    @Test
    void testWebhookIdempotencyAlreadyProcessed() {
        String payload = "{\"eventId\":\"evt_1001\",\"orderNo\":\"SO20260916001\"}";

        PaymentWebhook existing = new PaymentWebhook();
        existing.setId(1L);
        existing.setEventId("evt_1001");
        existing.setProcessed(1);
        when(paymentWebhookMapper.selectOne(any())).thenReturn(existing);

        String result = webhookService.handleMockWebhook(payload);

        assertEquals("ALREADY_PROCESSED", result);
        // Ensure no MQ and no status update occurred
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
        verify(paymentWebhookMapper, never()).insert(any(PaymentWebhook.class));
    }

    @Test
    void testWebhookRejectsMalformedJson() {
        assertThrows(BusinessException.class, () ->
                webhookService.handleMockWebhook("not a json")
        );
    }
}
