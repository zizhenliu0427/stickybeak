package au.com.stickybeak.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 故事线 9.6：webhook 热路径独立线程池。
 * Stripe 最佳实践是「尽快返回 2xx，异步处理」——验收/解析/落库/MQ 不与 Tomcat 请求线程争抢。
 * 拒绝策略 CallerRunsPolicy：队列满时回退调用方线程执行（背压），宁可慢也不丢支付事件。
 */
@Configuration
public class WebhookExecutorConfig {

    public static final String EXECUTOR = "webhookTaskExecutor";

    @Bean(EXECUTOR)
    public ThreadPoolTaskExecutor webhookTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("webhook-");
        // 背压：队列满 → 调用方（Tomcat 线程）自己跑，天然限流且事件不丢
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 停机前把手头事件处理完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
