package au.com.stickybeak.order.listener;

import au.com.stickybeak.common.event.OrderPaidEvent;
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
 * Consumer for OrderPaidEvent with Manual Acknowledgment.
 * Guarantees at-least-once delivery, prevents poison message deadlock, and ensures data consistency.
 */
@Component
public class OrderPaidListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidListener.class);

    private final OrderService orderService;

    public OrderPaidListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_STATUS_QUEUE)
    public void onOrderPaid(OrderPaidEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("Received order.paid message for orderNo: {}, deliveryTag: {}", event.getOrderNo(), deliveryTag);
        try {
            orderService.handleOrderPaid(event);
            channel.basicAck(deliveryTag, false);
            log.info("Successfully ACKed order.paid for orderNo: {}", event.getOrderNo());
        } catch (Exception e) {
            log.error("Failed to handle order.paid message for {}: {}", event.getOrderNo(), e.getMessage(), e);
            // In case of fatal/unrecoverable error, reject without requeue to route message to DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
