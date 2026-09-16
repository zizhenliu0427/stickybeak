package au.com.stickybeak.payment.service;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.entity.PaymentWebhook;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.mapper.PaymentWebhookMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 处理器（线程池异步段）：解析 → 更新支付流水 → 发 MQ → 置 processed。
 */
@ExtendWith(MockitoExtension.class)
class WebhookEventProcessorTest {

    @Mock
    private PaymentWebhookMapper paymentWebhookMapper;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private WebhookEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WebhookEventProcessor(paymentWebhookMapper, paymentRecordMapper,
                rabbitTemplate, new ObjectMapper());
    }

    private PaymentWebhook pendingRecord(String eventId) {
        PaymentWebhook w = new PaymentWebhook();
        w.setId(1L);
        w.setEventId(eventId);
        w.setProcessed(0);
        return w;
    }

    @Test
    @DisplayName("mock 成功路径：流水置 succeeded + MQ order.paid + processed=1")
    void mockSuccess() {
        String payload = "{\"eventId\":\"evt_1001\",\"orderNo\":\"SO20260916001\",\"amount\":120.00,\"currency\":\"AUD\",\"transactionId\":\"pi_test_1\"}";
        when(paymentWebhookMapper.selectOne(any())).thenReturn(pendingRecord("evt_1001"));

        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setOrderNo("SO20260916001");
        record.setStatus("pending");
        record.setPaymentMethod("card");
        record.setCurrency("AUD");
        record.setAmount(new BigDecimal("120.00"));
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        processor.process("evt_1001", payload, true);

        assertEquals("succeeded", record.getStatus());
        assertEquals("pi_test_1", record.getTransactionId());
        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), captor.capture());
        assertEquals("SO20260916001", captor.getValue().getOrderNo());
        assertEquals(0, new BigDecimal("120.00").compareTo(captor.getValue().getAmount()));

        ArgumentCaptor<PaymentWebhook> update = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper).updateById(update.capture());
        assertEquals(1, update.getValue().getProcessed());
    }

    @Test
    @DisplayName("stripe 载荷解析：client_reference_id + payment_intent + customer_details")
    void stripePayloadParsing() {
        String payload = """
                {"id":"evt_stripe_123","type":"checkout.session.completed","data":{"object":{
                  "id":"cs_test_123","client_reference_id":"SO20260916_STRIPE","payment_intent":"pi_live_999",
                  "amount_total":5990,"currency":"aud","customer_details":{"email":"customer@example.com"}}}}
                """;
        when(paymentWebhookMapper.selectOne(any())).thenReturn(pendingRecord("evt_stripe_123"));
        PaymentRecord record = new PaymentRecord();
        record.setOrderNo("SO20260916_STRIPE");
        record.setAmount(new BigDecimal("59.90"));
        record.setCurrency("AUD");
        record.setPaymentMethod("card");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        processor.process("evt_stripe_123", payload, false);

        assertEquals("succeeded", record.getStatus());
        assertEquals("pi_live_999", record.getTransactionId());
        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), captor.capture());
        assertEquals("customer@example.com", captor.getValue().getEmail());
        assertEquals(new BigDecimal("59.90"), captor.getValue().getAmount());
    }

    @Test
    @DisplayName("stripe 载荷：metadata.orderNo 兜底提取")
    void stripeMetadataFallback() {
        String payload = """
                {"id":"evt_stripe_456","type":"checkout.session.completed","data":{"object":{
                  "id":"cs_test_456","metadata":{"orderNo":"SO_META"},"amount_total":3500,"currency":"aud"}}}
                """;
        when(paymentWebhookMapper.selectOne(any())).thenReturn(pendingRecord("evt_stripe_456"));
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        processor.process("evt_stripe_456", payload, false);

        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), captor.capture());
        assertEquals("SO_META", captor.getValue().getOrderNo());
    }

    @Test
    @DisplayName("处理失败：processed=2 留档（不外抛，等待人工/定时重试）")
    void failure_marksFailed() {
        String payload = "{\"eventId\":\"evt_error\",\"orderNo\":\"SO_ERR\"}";
        when(paymentWebhookMapper.selectOne(any())).thenReturn(pendingRecord("evt_error"));
        doThrow(new RuntimeException("RabbitMQ connection down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // 不外抛
        assertDoesNotThrow(() -> processor.process("evt_error", payload, true));

        ArgumentCaptor<PaymentWebhook> update = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper).updateById(update.capture());
        assertEquals(2, update.getValue().getProcessed());
        assertTrue(update.getValue().getErrorMsg().contains("RabbitMQ connection down"));
    }

    @Test
    @DisplayName("并发防线：记录已被处理则直接 no-op")
    void alreadyProcessed_noop() {
        PaymentWebhook done = pendingRecord("evt_done");
        done.setProcessed(1);
        when(paymentWebhookMapper.selectOne(any())).thenReturn(done);

        processor.process("evt_done", "{}", true);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
    }
}
