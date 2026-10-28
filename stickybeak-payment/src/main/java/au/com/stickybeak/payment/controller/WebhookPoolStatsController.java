package au.com.stickybeak.payment.controller;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.payment.config.WebhookExecutorConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 故事线 9.6 监控端点：webhook 线程池实时水位（仅异步模式，管理员可见）。
 * 生产建议挂 Micrometer 指标（task.pool.*）进 Prometheus，此处保留最简观测口。
 */
@RestController
@RequestMapping("/webhooks")
@ConditionalOnProperty(prefix = "stickybeak.webhook.async", name = "enabled", havingValue = "true")
public class WebhookPoolStatsController {

    private final ThreadPoolTaskExecutor webhookTaskExecutor;

    public WebhookPoolStatsController(@Qualifier(WebhookExecutorConfig.EXECUTOR) ThreadPoolTaskExecutor webhookTaskExecutor) {
        this.webhookTaskExecutor = webhookTaskExecutor;
    }

    @GetMapping("/pool-stats")
    public ResponseEntity<Map<String, Object>> poolStats(
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        if (!StringUtils.hasText(roles) || !roles.toLowerCase().contains("admin")) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Admin permission required");
        }
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
