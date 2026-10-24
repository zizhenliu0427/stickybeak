package au.com.stickybeak.order.listener;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.service.OrderService;
import au.com.stickybeak.order.service.OrderService.PaidOutcome;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumer for OrderPaidEvent with Manual Acknowledgment.
 * 先在本地事务里把订单推进为 paid 并提交，再做确认库存、清空购物车等远程副作用；
 * 副作用失败不会回滚已支付状态，而是经延迟重试队列重做，重试用完进入死信队列。
 */
@Component
public class OrderPaidListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidListener.class);

    private final OrderService orderService;
    private final MessageRetrySupport retrySupport;

    public OrderPaidListener(OrderService orderService, MessageRetrySupport retrySupport) {
        this.orderService = orderService;
        this.retrySupport = retrySupport;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_STATUS_QUEUE)
    public void onOrderPaid(OrderPaidEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String orderNo = event.getOrderNo();
        log.info("Received order.paid message for orderNo: {}, deliveryTag: {}", orderNo, deliveryTag);
        try {
            PaidOutcome outcome = orderService.markPaid(event);
            switch (outcome) {
                case PAID_NOW -> orderService.completePaidOrder(orderNo);
                case ALREADY_PAID -> {
                    // 只有重试 / 重投时才补做副作用；真正的重复事件不能再清一次购物车
                    if (retrySupport.isRetryOrRedelivery(message)) {
                        orderService.completePaidOrder(orderNo);
                    }
                }
                // 订单已取消后才到账：原路退回；失败走重试，用完进入死信队列人工处理
                case NOT_PAYABLE -> orderService.refundPaidAfterCancel(orderNo);
                case NOT_FOUND -> {
                    // 可能是结账事务尚未提交，稍后重试
                    retrySupport.retryOrDeadLetter(message, channel, RabbitMQConfig.ORDER_STATUS_RETRY_QUEUE,
                            RabbitMQConfig.ORDER_STATUS_DLQ, "ORDER_NOT_FOUND: " + orderNo);
                    return;
                }
            }
            channel.basicAck(deliveryTag, false);
            log.info("Successfully ACKed order.paid for orderNo: {}", orderNo);
        } catch (Exception e) {
            log.error("Failed to handle order.paid message for {}: {}", orderNo, e.getMessage(), e);
            retrySupport.retryOrDeadLetter(message, channel, RabbitMQConfig.ORDER_STATUS_RETRY_QUEUE,
                    RabbitMQConfig.ORDER_STATUS_DLQ, e.getMessage());
        }
    }
}
