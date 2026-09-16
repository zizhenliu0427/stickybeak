package au.com.stickybeak.notification.service;

import au.com.stickybeak.common.event.OrderPaidEvent;

public interface EmailService {

    void sendOrderConfirmation(OrderPaidEvent event);
}
