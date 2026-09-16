package au.com.stickybeak.notification.config;

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

    public static final String NOTIFICATION_EMAIL_QUEUE = "notification.email.queue";

    @Bean
    public TopicExchange orderTopicExchange() {
        return new TopicExchange(OrderPaidEvent.EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationEmailQueue() {
        return new Queue(NOTIFICATION_EMAIL_QUEUE, true);
    }

    @Bean
    public Binding notificationEmailBinding(Queue notificationEmailQueue, TopicExchange orderTopicExchange) {
        return BindingBuilder.bind(notificationEmailQueue).to(orderTopicExchange).with(OrderPaidEvent.ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
