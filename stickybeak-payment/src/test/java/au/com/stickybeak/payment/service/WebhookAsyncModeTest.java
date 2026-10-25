package au.com.stickybeak.payment.service;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.entity.PaymentWebhook;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.mapper.PaymentWebhookMapper;
import au.com.stickybeak.payment.provider.MockPaymentProvider;
import au.com.stickybeak.payment.provider.StripePaymentProvider;
import au.com.stickybeak.payment.service.impl.WebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 异步模式（故事线 9.6）：请求线程只落档并返回 ACCEPTED，线程池里跑的是与同步模式同一套校验逻辑。
 * 线程池和事务模板替换成就地执行，便于断言。
 */
class WebhookAsyncModeTest {

    private PaymentWebhookMapper paymentWebhookMapper;
    private PaymentRecordMapper paymentRecordMapper;
    private RabbitTemplate rabbitTemplate;
    private ThreadPoolTaskExecutor executor;
    private WebhookServiceImpl webhookService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        paymentWebhookMapper = mock(PaymentWebhookMapper.class);
        paymentRecordMapper = mock(PaymentRecordMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        webhookService = new WebhookServiceImpl(
                mock(StripePaymentProvider.class),
                mock(MockPaymentProvider.class),
                paymentWebhookMapper,
                paymentRecordMapper,
                rabbitTemplate,
                new ObjectMapper(),
                mock(RefundService.class));

        executor = mock(ThreadPoolTaskExecutor.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        webhookService.enableAsyncProcessing(executor, transactionTemplate);

        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
    }

    private PaymentRecord pendingRecord() {
        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setOrderNo("SO_ASYNC_1");
        record.setStatus("pending");
        record.setPaymentMethod("card");
        record.setCurrency("AUD");
        record.setAmount(new BigDecimal("30.00"));
        return record;
    }

    @Test
    void acceptsThenProcessesOnThePool() {
        PaymentRecord record = pendingRecord();
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        String result = webhookService.handleMockWebhook(
                "{\"eventId\":\"evt_async_1\",\"orderNo\":\"SO_ASYNC_1\",\"amount\":30.00,\"currency\":\"AUD\"}");

        assertEquals(WebhookServiceImpl.RESULT_ACCEPTED, result);
        verify(executor).execute(any(Runnable.class));
        assertEquals("succeeded", record.getStatus());
        verify(rabbitTemplate).convertAndSend(eq(OrderPaidEvent.EXCHANGE), eq(OrderPaidEvent.ROUTING_KEY), any(OrderPaidEvent.class));
    }

    @Test
    void amountMismatchIsStillRejectedInAsyncMode() {
        PaymentRecord record = pendingRecord();
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        String result = webhookService.handleMockWebhook(
                "{\"eventId\":\"evt_async_2\",\"orderNo\":\"SO_ASYNC_1\",\"amount\":0.01,\"currency\":\"AUD\"}");

        assertEquals(WebhookServiceImpl.RESULT_ACCEPTED, result);
        assertEquals("pending", record.getStatus());
        verifyNoInteractions(rabbitTemplate);
        ArgumentCaptor<PaymentWebhook> captor = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper, atLeastOnce()).updateById(captor.capture());
        PaymentWebhook last = captor.getValue();
        assertEquals(2, last.getProcessed());
        assertTrue(last.getErrorMsg().startsWith("AMOUNT_MISMATCH"));
    }

    @Test
    void processingFailureIsRecordedInsteadOfThrown() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(pendingRecord());
        doThrow(new RuntimeException("RabbitMQ connection down"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        String result = webhookService.handleMockWebhook(
                "{\"eventId\":\"evt_async_3\",\"orderNo\":\"SO_ASYNC_1\",\"amount\":30.00,\"currency\":\"AUD\"}");

        assertEquals(WebhookServiceImpl.RESULT_ACCEPTED, result);
        ArgumentCaptor<PaymentWebhook> captor = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper, atLeastOnce()).updateById(captor.capture());
        List<PaymentWebhook> updates = captor.getAllValues();
        PaymentWebhook last = updates.get(updates.size() - 1);
        assertEquals(2, last.getProcessed());
        assertTrue(last.getErrorMsg().contains("RabbitMQ connection down"));
    }
}
