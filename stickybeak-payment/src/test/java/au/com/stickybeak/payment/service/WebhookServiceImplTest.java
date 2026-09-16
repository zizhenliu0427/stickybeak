package au.com.stickybeak.payment.service;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.payment.entity.PaymentWebhook;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 接收端（9.6 新架构）：验签 + 幂等落档 + 投递线程池，返回 ACCEPTED。
 */
@ExtendWith(MockitoExtension.class)
class WebhookServiceImplTest {

    @Mock
    private StripePaymentProvider stripePaymentProvider;
    @Mock
    private MockPaymentProvider mockPaymentProvider;
    @Mock
    private PaymentWebhookMapper paymentWebhookMapper;
    @Mock
    private WebhookEventProcessor webhookEventProcessor;
    @Mock
    private ThreadPoolTaskExecutor webhookTaskExecutor;

    private WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        // debug 日志会访问线程池内部状态，mock 出一个真实的空池
        ThreadPoolExecutor realPool = new ThreadPoolExecutor(0, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        lenient().when(webhookTaskExecutor.getThreadPoolExecutor()).thenReturn(realPool);
        webhookService = new WebhookServiceImpl(
                stripePaymentProvider, mockPaymentProvider, paymentWebhookMapper,
                new ObjectMapper(), webhookEventProcessor, webhookTaskExecutor);
    }

    @Test
    @DisplayName("首次事件：幂等落档 + 投递线程池 + ACCEPTED")
    void firstTime_enqueuesToPool() {
        String payload = "{\"eventId\":\"evt_1\",\"orderNo\":\"SO1\"}";
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        String result = webhookService.handleMockWebhook(payload);

        assertEquals("ACCEPTED", result);
        verify(paymentWebhookMapper).insert(any(PaymentWebhook.class));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(webhookTaskExecutor).execute(task.capture());
        // 线程池任务内容 = 调用处理器
        task.getValue().run();
        verify(webhookEventProcessor).process(eq("evt_1"), eq(payload), eq(true));
    }

    @Test
    @DisplayName("已处理事件重复投递：ALREADY_PROCESSED，不进线程池")
    void alreadyProcessed_noop() {
        PaymentWebhook existing = new PaymentWebhook();
        existing.setEventId("evt_dup");
        existing.setProcessed(1);
        when(paymentWebhookMapper.selectOne(any())).thenReturn(existing);

        String result = webhookService.handleMockWebhook("{\"eventId\":\"evt_dup\"}");

        assertEquals("ALREADY_PROCESSED", result);
        verify(webhookTaskExecutor, never()).execute(any());
        verify(paymentWebhookMapper, never()).insert(any(PaymentWebhook.class));
    }

    @Test
    @DisplayName("并发同事件：UK 约束兜底 ALREADY_PROCESSED")
    void concurrentDuplicate_ukFallback() {
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("dup")).when(paymentWebhookMapper).insert(any(PaymentWebhook.class));

        String result = webhookService.handleMockWebhook("{\"eventId\":\"evt_race\"}");

        assertEquals("ALREADY_PROCESSED", result);
        verify(webhookTaskExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("mock 缺 eventId：自动生成 evt_mock_ 前缀")
    void mockMissingEventId_generates() {
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        String result = webhookService.handleMockWebhook("{\"orderNo\":\"SO_X\"}");

        assertEquals("ACCEPTED", result);
        ArgumentCaptor<PaymentWebhook> captor = ArgumentCaptor.forClass(PaymentWebhook.class);
        verify(paymentWebhookMapper).insert(captor.capture());
        assertTrue(captor.getValue().getEventId().startsWith("evt_mock_"));
    }

    @Test
    @DisplayName("畸形 JSON：400")
    void malformedJson_rejected() {
        assertThrows(BusinessException.class, () -> webhookService.handleMockWebhook("not-json"));
        verify(webhookTaskExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("stripe 路径：验签通过后投递（isMock=false）")
    void stripePath_verifiesAndEnqueues() {
        String payload = "{\"id\":\"evt_stripe_1\"}";
        when(stripePaymentProvider.verifyWebhook(payload, "sig")).thenReturn("evt_stripe_1");
        when(paymentWebhookMapper.selectOne(any())).thenReturn(null);

        String result = webhookService.handleStripeWebhook(payload, "sig");

        assertEquals("ACCEPTED", result);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(webhookTaskExecutor).execute(task.capture());
        task.getValue().run();
        verify(webhookEventProcessor).process(eq("evt_stripe_1"), eq(payload), eq(false));
    }

    @Test
    @DisplayName("崩溃恢复：已落档但未处理完的记录允许重投")
    void unprocessedRecord_reenqueued() {
        PaymentWebhook existing = new PaymentWebhook();
        existing.setEventId("evt_crash");
        existing.setProcessed(0);
        when(paymentWebhookMapper.selectOne(any())).thenReturn(existing);

        String result = webhookService.handleMockWebhook("{\"eventId\":\"evt_crash\"}");

        assertEquals("ACCEPTED", result);
        verify(paymentWebhookMapper, never()).insert(any(PaymentWebhook.class));
        verify(webhookTaskExecutor).execute(any());
    }
}
