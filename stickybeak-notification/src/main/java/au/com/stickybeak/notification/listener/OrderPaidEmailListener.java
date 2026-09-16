package au.com.stickybeak.notification.listener;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.notification.config.RabbitMQConfig;
import au.com.stickybeak.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaidEmailListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEmailListener.class);

    private final EmailService emailService;

    public OrderPaidEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_EMAIL_QUEUE)
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("Notification service received order.paid event for order: {}", event.getOrderNo());
        try {
            emailService.sendOrderConfirmation(event);
        } catch (Exception e) {
            log.error("Failed to process order confirmation notification for {}: {}", event.getOrderNo(), e.getMessage(), e);
        }
    }
}
