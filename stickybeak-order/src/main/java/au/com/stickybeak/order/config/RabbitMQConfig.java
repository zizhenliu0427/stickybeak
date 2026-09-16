package au.com.stickybeak.order.config;

import au.com.stickybeak.common.event.OrderPaidEvent;
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

    @Value("${stickybeak.order.ttl-millis:1800000}")
    private long orderTtlMillis;

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
