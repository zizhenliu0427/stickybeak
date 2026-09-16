package au.com.stickybeak.payment.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.payment.entity.PaymentWebhook;
import au.com.stickybeak.payment.mapper.PaymentWebhookMapper;
import au.com.stickybeak.payment.provider.MockPaymentProvider;
import au.com.stickybeak.payment.provider.StripePaymentProvider;
import au.com.stickybeak.payment.service.WebhookEventProcessor;
import au.com.stickybeak.payment.service.WebhookService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import static au.com.stickybeak.payment.config.WebhookExecutorConfig.EXECUTOR;

/**
 * Webhook 接收端（故事线 9.6）：
 * 同步段只做验签 + 幂等落档（快），重处理投递到 webhookTaskExecutor 线程池异步执行。
 * 快速返回 200 ACCEPTED，Stripe 不再因处理耗时而超时重试。
 */
@Service
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private final StripePaymentProvider stripePaymentProvider;
    private final MockPaymentProvider mockPaymentProvider;
    private final PaymentWebhookMapper paymentWebhookMapper;
    private final ObjectMapper objectMapper;
    private final WebhookEventProcessor webhookEventProcessor;
    private final ThreadPoolTaskExecutor webhookTaskExecutor;

    public WebhookServiceImpl(StripePaymentProvider stripePaymentProvider,
                              MockPaymentProvider mockPaymentProvider,
                              PaymentWebhookMapper paymentWebhookMapper,
                              ObjectMapper objectMapper,
                              WebhookEventProcessor webhookEventProcessor,
                              @Qualifier(EXECUTOR) ThreadPoolTaskExecutor webhookTaskExecutor) {
        this.stripePaymentProvider = stripePaymentProvider;
        this.mockPaymentProvider = mockPaymentProvider;
        this.paymentWebhookMapper = paymentWebhookMapper;
        this.objectMapper = objectMapper;
        this.webhookEventProcessor = webhookEventProcessor;
        this.webhookTaskExecutor = webhookTaskExecutor;
    }

    @Override
    public String handleStripeWebhook(String payload, String sigHeader) {
        log.info("Received Stripe webhook request");
        // 1. 验签（同步，快）
        String eventId = stripePaymentProvider.verifyWebhook(payload, sigHeader);
        // 2. 幂等落档 + 投递线程池
        return enqueue(eventId, payload, false);
    }

    @Override
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
        return enqueue(eventId, payload, true);
    }

    /**
     * 幂等落档后投递线程池。重复投递直接 no-op（UK 兜底并发）；
     * 已存在但未处理完的记录允许重投（崩溃恢复）。
     */
    private String enqueue(String eventId, String payload, boolean isMock) {
        PaymentWebhook existing = paymentWebhookMapper.selectOne(
                new LambdaQueryWrapper<PaymentWebhook>().eq(PaymentWebhook::getEventId, eventId));

        if (existing != null && existing.getProcessed() == 1) {
            log.info("Webhook event {} already processed. Idempotent no-op.", eventId);
            return "ALREADY_PROCESSED";
        }
        if (existing == null) {
            PaymentWebhook record = new PaymentWebhook();
            record.setEventId(eventId);
            record.setEventType("unknown");
            record.setPayload(payload);
            record.setProcessed(0);
            try {
                paymentWebhookMapper.insert(record);
            } catch (DuplicateKeyException dke) {
                log.info("Concurrent webhook event {} caught by UK constraint.", eventId);
                return "ALREADY_PROCESSED";
            }
        }

        // CallerRunsPolicy 兜底：队列满时本方法阻塞在执行里（背压），事件不丢
        webhookTaskExecutor.execute(() -> webhookEventProcessor.process(eventId, payload, isMock));
        log.debug("webhook {} enqueued (active={}, queue={})",
                eventId,
                webhookTaskExecutor.getThreadPoolExecutor().getActiveCount(),
                webhookTaskExecutor.getThreadPoolExecutor().getQueue().size());
        return "ACCEPTED";
    }
}
