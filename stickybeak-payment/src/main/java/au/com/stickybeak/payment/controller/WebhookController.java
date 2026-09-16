package au.com.stickybeak.payment.controller;

import au.com.stickybeak.payment.service.WebhookService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static au.com.stickybeak.payment.config.WebhookExecutorConfig.EXECUTOR;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService webhookService;
    private final ThreadPoolTaskExecutor webhookTaskExecutor;

    public WebhookController(WebhookService webhookService,
                             @Qualifier(EXECUTOR) ThreadPoolTaskExecutor webhookTaskExecutor) {
        this.webhookService = webhookService;
        this.webhookTaskExecutor = webhookTaskExecutor;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        String result = webhookService.handleStripeWebhook(payload, sigHeader);
        return ResponseEntity.ok(Map.of("received", true, "status", result));
    }

    @PostMapping("/mock")
    public ResponseEntity<Map<String, Object>> handleMockWebhook(@RequestBody String payload) {
        String result = webhookService.handleMockWebhook(payload);
        return ResponseEntity.ok(Map.of("received", true, "status", result));
    }

    /**
     * 故事线 9.6 监控端点：webhook 线程池实时水位。
     * 生产建议挂 Micrometer 指标（task.pool.* ）进 Prometheus，此处保留最简观测口。
     */
    @GetMapping("/pool-stats")
    public ResponseEntity<Map<String, Object>> poolStats() {
        ThreadPoolExecutor pool = webhookTaskExecutor.getThreadPoolExecutor();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeCount", pool.getActiveCount());
        stats.put("poolSize", pool.getPoolSize());
        stats.put("corePoolSize", pool.getCorePoolSize());
        stats.put("maximumPoolSize", pool.getMaximumPoolSize());
        stats.put("largestPoolSize", pool.getLargestPoolSize());
        stats.put("queueSize", pool.getQueue().size());
        stats.put("queueRemainingCapacity", pool.getQueue().remainingCapacity());
        stats.put("taskCount", pool.getTaskCount());
        stats.put("completedTaskCount", pool.getCompletedTaskCount());
        stats.put("rejectionPolicy", pool.getRejectedExecutionHandler().getClass().getSimpleName());
        return ResponseEntity.ok(stats);
    }
}
