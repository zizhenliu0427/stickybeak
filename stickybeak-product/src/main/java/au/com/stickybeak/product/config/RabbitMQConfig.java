package au.com.stickybeak.product.config;

import au.com.stickybeak.common.event.ProductChangedEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品变更事件 MQ 拓扑：product.topic → product.search.index（本服务自产自消）。
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange productTopicExchange() {
        return new TopicExchange(ProductChangedEvent.EXCHANGE, true, false);
    }

    @Bean
    public Queue productIndexQueue() {
        return new Queue(ProductChangedEvent.INDEX_QUEUE, true);
    }

    @Bean
    public Binding productIndexBinding(TopicExchange productTopicExchange, Queue productIndexQueue) {
        return BindingBuilder.bind(productIndexQueue)
                .to(productTopicExchange)
                .with(ProductChangedEvent.ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
