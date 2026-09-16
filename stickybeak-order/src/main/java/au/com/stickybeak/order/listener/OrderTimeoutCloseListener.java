package au.com.stickybeak.order.listener;

import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutCloseListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCloseListener.class);

    private final OrderService orderService;

    public OrderTimeoutCloseListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CLOSE_QUEUE)
    public void onOrderTimeout(String orderNo) {
        log.info("Received order timeout message from DLX queue for order: {}", orderNo);
        try {
            orderService.handleOrderTimeout(orderNo);
        } catch (Exception e) {
            log.error("Failed to handle order timeout for order {}: {}", orderNo, e.getMessage(), e);
        }
    }
}
