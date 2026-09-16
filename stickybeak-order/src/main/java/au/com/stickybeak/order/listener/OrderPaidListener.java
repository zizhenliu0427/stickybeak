package au.com.stickybeak.order.listener;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaidListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidListener.class);

    private final OrderService orderService;

    public OrderPaidListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_STATUS_QUEUE)
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("Received order.paid message for orderNo: {}", event.getOrderNo());
        try {
            orderService.handleOrderPaid(event);
        } catch (Exception e) {
            log.error("Failed to handle order.paid message for {}: {}", event.getOrderNo(), e.getMessage(), e);
            throw e; // throw so RabbitMQ retry / dead-letter handles it
        }
    }
}
