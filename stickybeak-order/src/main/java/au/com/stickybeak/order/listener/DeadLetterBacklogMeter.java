package au.com.stickybeak.order.listener;

import au.com.stickybeak.order.config.RabbitMQConfig;
import org.apache.skywalking.apm.toolkit.meter.MeterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把各死信队列里的消息总数作为 SkyWalking 指标上报（{@code order_dlq_backlog}）。
 * 支付、关单、退款消息重试耗尽后进入死信队列，需要人工处理；OAP 的 dlq_backlog_rule 在积压大于 0 时告警
 * （docker/skywalking/alarm-settings.yml、meter-analyzer-config/stickybeak.yaml）。
 * agent 每个上报周期调用一次 {@link #backlog()}；没挂 agent 时 MeterFactory 是空实现，不会调用。
 */
@Component
public class DeadLetterBacklogMeter {

    static final String METER_NAME = "order_dlq_backlog";
    static final List<String> DEAD_LETTER_QUEUES = List.of(
            RabbitMQConfig.ORDER_STATUS_DLQ,
            RabbitMQConfig.ORDER_CLOSE_DLQ,
            RabbitMQConfig.ORDER_REFUND_DLQ);

    private static final Logger log = LoggerFactory.getLogger(DeadLetterBacklogMeter.class);

    private final AmqpAdmin amqpAdmin;

    public DeadLetterBacklogMeter(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    /** 队列由 RabbitMQConfig 声明，应用就绪后再注册，避免查询时队列还不存在 */
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        MeterFactory.gauge(METER_NAME, this::backlog).build();
    }

    /** 查询失败返回 -1（不触发告警）：broker 不可用会由服务错误率告警发现，这里不重复 */
    double backlog() {
        try {
            long total = 0;
            for (String queue : DEAD_LETTER_QUEUES) {
                QueueInformation info = amqpAdmin.getQueueInfo(queue);
                if (info != null) {
                    total += info.getMessageCount();
                }
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to read dead-letter queue depth: {}", e.getMessage());
            return -1;
        }
    }
}
