package au.com.stickybeak.order.config;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.event.OrderRefundedEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_STATUS_QUEUE = "order.status.queue";

    // -------------------------------------------------------------
    // RabbitMQ 延迟队列（TTL + DLX 死信机制，超时 30 分钟自动关单）
    // -------------------------------------------------------------
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay.routing";

    public static final String ORDER_DLX_EXCHANGE = "order.dlx.exchange";
    public static final String ORDER_CLOSE_QUEUE = "order.close.queue";
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close.routing";

    // -------------------------------------------------------------
    // 消费失败的延迟重试与死信：失败消息发到 retry 队列，TTL 到期后经默认交换机
    // 回到原队列；超过最大次数进入 DLQ 等待人工处理。
    // 全部是新队列，不修改已有队列参数（RabbitMQ 不允许改已声明队列的 arguments）。
    // -------------------------------------------------------------
    public static final String ORDER_STATUS_RETRY_QUEUE = "order.status.retry.queue";
    public static final String ORDER_STATUS_DLQ = "order.status.dlq";
    public static final String ORDER_CLOSE_RETRY_QUEUE = "order.close.retry.queue";
    public static final String ORDER_CLOSE_DLQ = "order.close.dlq";

    public static final String ORDER_REFUND_QUEUE = "order.refund.queue";
    public static final String ORDER_REFUND_RETRY_QUEUE = "order.refund.retry.queue";
    public static final String ORDER_REFUND_DLQ = "order.refund.dlq";

    @Value("${stickybeak.order.ttl-millis:1800000}")
    private long orderTtlMillis;

    @Value("${stickybeak.order.mq.retry-delay-millis:10000}")
    private long retryDelayMillis;

    @Bean
    public Queue orderStatusRetryQueue() {
        return retryQueue(ORDER_STATUS_RETRY_QUEUE, ORDER_STATUS_QUEUE);
    }

    @Bean
    public Queue orderStatusDlq() {
        return new Queue(ORDER_STATUS_DLQ, true);
    }

    @Bean
    public Queue orderCloseRetryQueue() {
        return retryQueue(ORDER_CLOSE_RETRY_QUEUE, ORDER_CLOSE_QUEUE);
    }

    @Bean
    public Queue orderCloseDlq() {
        return new Queue(ORDER_CLOSE_DLQ, true);
    }

    private Queue retryQueue(String name, String targetQueue) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", retryDelayMillis);
        // 默认交换机（""）按队列名路由，只回到目标队列，不会重复投递给通知服务
        args.put("x-dead-letter-exchange", "");
        args.put("x-dead-letter-routing-key", targetQueue);
        return new Queue(name, true, false, false, args);
    }

    @Bean
    public TopicExchange orderTopicExchange() {
        return new TopicExchange(OrderPaidEvent.EXCHANGE, true, false);
    }

    @Bean
    public Queue orderStatusQueue() {
        return new Queue(ORDER_STATUS_QUEUE, true);
    }

    @Bean
    public Binding orderStatusBinding(Queue orderStatusQueue, TopicExchange orderTopicExchange) {
        return BindingBuilder.bind(orderStatusQueue).to(orderTopicExchange).with(OrderPaidEvent.ROUTING_KEY);
    }

    // -------------------------------------------------------------
    // 退款到账事件（支付服务发布 order.refunded）
    // -------------------------------------------------------------
    @Bean
    public Queue orderRefundQueue() {
        return new Queue(ORDER_REFUND_QUEUE, true);
    }

    @Bean
    public Binding orderRefundBinding(Queue orderRefundQueue, TopicExchange orderTopicExchange) {
        return BindingBuilder.bind(orderRefundQueue).to(orderTopicExchange).with(OrderRefundedEvent.ROUTING_KEY);
    }

    @Bean
    public Queue orderRefundRetryQueue() {
        return retryQueue(ORDER_REFUND_RETRY_QUEUE, ORDER_REFUND_QUEUE);
    }

    @Bean
    public Queue orderRefundDlq() {
        return new Queue(ORDER_REFUND_DLQ, true);
    }

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(ORDER_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        // 当延迟队列中的消息超时变成死信时，路由至死信交换机
        args.put("x-dead-letter-exchange", ORDER_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CLOSE_ROUTING_KEY);
        args.put("x-message-ttl", orderTtlMillis);
        return new Queue(ORDER_DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding orderDelayBinding(Queue orderDelayQueue, DirectExchange orderDelayExchange) {
        return BindingBuilder.bind(orderDelayQueue).to(orderDelayExchange).with(ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public Queue orderCloseQueue() {
        return new Queue(ORDER_CLOSE_QUEUE, true);
    }

    @Bean
    public Binding orderCloseBinding(Queue orderCloseQueue, DirectExchange orderDlxExchange) {
        return BindingBuilder.bind(orderCloseQueue).to(orderDlxExchange).with(ORDER_CLOSE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
