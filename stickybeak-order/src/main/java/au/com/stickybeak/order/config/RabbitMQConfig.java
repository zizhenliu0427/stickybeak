package au.com.stickybeak.order.config;

import au.com.stickybeak.common.event.OrderPaidEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_STATUS_QUEUE = "order.status.queue";

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
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
