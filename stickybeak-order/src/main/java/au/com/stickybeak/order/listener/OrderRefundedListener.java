package au.com.stickybeak.order.listener;

import au.com.stickybeak.common.event.OrderRefundedEvent;
import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.service.OrderService;
import au.com.stickybeak.order.service.OrderService.RefundOutcome;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 退款到账：refunding → refunded（条件更新，事务内），提交后回补已确认的库存。
 * 回补是幂等的（只处理 CONFIRMED 预占），重复事件可以安全地再执行一次。
 */
@Component
public class OrderRefundedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderRefundedListener.class);

    private final OrderService orderService;
    private final MessageRetrySupport retrySupport;

    public OrderRefundedListener(OrderService orderService, MessageRetrySupport retrySupport) {
        this.orderService = orderService;
        this.retrySupport = retrySupport;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_REFUND_QUEUE)
    public void onOrderRefunded(OrderRefundedEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String orderNo = event.getOrderNo();
        log.info("Received order.refunded message for orderNo: {}, deliveryTag: {}", orderNo, deliveryTag);
        try {
            RefundOutcome outcome = orderService.markRefunded(orderNo);
            switch (outcome) {
                case REFUNDED_NOW, ALREADY_REFUNDED -> orderService.restockRefundedOrder(orderNo);
                // 关单后到账被自动退回的订单：库存在关单时已释放，订单保持 cancelled
                case NOT_REFUNDING -> log.info("Refund for order {} recorded, no status change", orderNo);
                case NOT_FOUND -> {
                    retrySupport.retryOrDeadLetter(message, channel, RabbitMQConfig.ORDER_REFUND_RETRY_QUEUE,
                            RabbitMQConfig.ORDER_REFUND_DLQ, "ORDER_NOT_FOUND: " + orderNo);
                    return;
                }
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to handle order.refunded message for {}: {}", orderNo, e.getMessage(), e);
            retrySupport.retryOrDeadLetter(message, channel, RabbitMQConfig.ORDER_REFUND_RETRY_QUEUE,
                    RabbitMQConfig.ORDER_REFUND_DLQ, e.getMessage());
        }
    }
}
