package au.com.stickybeak.order.listener;

import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.service.OrderService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumer for Order timeout close with Manual Acknowledgment.
 */
@Component
public class OrderTimeoutCloseListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCloseListener.class);

    private final OrderService orderService;

    public OrderTimeoutCloseListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CLOSE_QUEUE)
    public void onOrderTimeout(String orderNo, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("Received order timeout message from DLX queue for order: {}, deliveryTag: {}", orderNo, deliveryTag);
        try {
            orderService.handleOrderTimeout(orderNo);
            channel.basicAck(deliveryTag, false);
            log.info("Successfully ACKed order timeout for order: {}", orderNo);
        } catch (Exception e) {
            log.error("Failed to handle order timeout for order {}: {}", orderNo, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
