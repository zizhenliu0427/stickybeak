package au.com.stickybeak.order.listener;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 手动 ACK 模式下的失败处理：
 * 重试次数未用完 → 转发到延迟重试队列；用完或不可恢复 → 转发到死信队列；随后 ACK 原消息。
 * 转发失败（如 broker 断开）时 NACK 并重新入队，等连接恢复后重新投递。
 */
@Component
public class MessageRetrySupport {

    private static final Logger log = LoggerFactory.getLogger(MessageRetrySupport.class);

    public static final String RETRY_COUNT_HEADER = "x-retry-count";
    public static final String FAILURE_REASON_HEADER = "x-failure-reason";
    private static final int MAX_REASON_LENGTH = 500;

    private final RabbitTemplate rabbitTemplate;
    private final int maxRetries;

    public MessageRetrySupport(RabbitTemplate rabbitTemplate,
                               @Value("${stickybeak.order.mq.max-retries:5}") int maxRetries) {
        this.rabbitTemplate = rabbitTemplate;
        this.maxRetries = maxRetries;
    }

    public int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeader(RETRY_COUNT_HEADER);
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** 是否为重试或 broker 重投（上次处理可能已完成一半） */
    public boolean isRetryOrRedelivery(Message message) {
        return retryCount(message) > 0 || Boolean.TRUE.equals(message.getMessageProperties().getRedelivered());
    }

    public void retryOrDeadLetter(Message message, Channel channel, String retryQueue, String deadLetterQueue,
                                  String reason) throws IOException {
        int attempts = retryCount(message);
        if (attempts < maxRetries) {
            log.warn("Scheduling retry {}/{} via {}: {}", attempts + 1, maxRetries, retryQueue, reason);
            forward(message, channel, retryQueue, attempts + 1, reason);
        } else {
            log.error("Retries exhausted, moving message to {}: {}", deadLetterQueue, reason);
            forward(message, channel, deadLetterQueue, null, reason);
        }
    }

    private void forward(Message message, Channel channel, String queue, Integer retryCount, String reason)
            throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            MessageProperties props = message.getMessageProperties();
            // x-death 等由 broker 维护，转发前去掉，避免与新的死信记录冲突
            props.getHeaders().keySet().removeIf(key -> key.startsWith("x-death")
                    || key.startsWith("x-first-death") || key.startsWith("x-last-death"));
            if (retryCount != null) {
                props.setHeader(RETRY_COUNT_HEADER, retryCount);
            }
            props.setHeader(FAILURE_REASON_HEADER, truncate(reason));
            rabbitTemplate.send("", queue, message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to forward message to {}, requeueing: {}", queue, e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "unknown";
        }
        return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
    }
}
