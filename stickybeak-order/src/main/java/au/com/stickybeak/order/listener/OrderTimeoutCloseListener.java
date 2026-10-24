package au.com.stickybeak.order.listener;

import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.service.OrderService;
import au.com.stickybeak.order.service.OrderService.TimeoutOutcome;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumer for Order timeout close with Manual Acknowledgment.
 * 每个订单下单 30 分钟后都会收到一条消息：仍未支付就关单；
 * 只要订单处于已取消状态（含用户主动取消），都再执行一次幂等的资源释放，兜住之前释放失败的情况。
 */
@Component
public class OrderTimeoutCloseListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCloseListener.class);

    private final OrderService orderService;
    private final MessageRetrySupport retrySupport;

    public OrderTimeoutCloseListener(OrderService orderService, MessageRetrySupport retrySupport) {
        this.orderService = orderService;
        this.retrySupport = retrySupport;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CLOSE_QUEUE)
    public void onOrderTimeout(String orderNo, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("Received order timeout message from DLX queue for order: {}, deliveryTag: {}", orderNo, deliveryTag);
        try {
            TimeoutOutcome outcome = orderService.closeIfUnpaid(orderNo);
            if (outcome == TimeoutOutcome.CLOSED_NOW || outcome == TimeoutOutcome.ALREADY_CLOSED) {
                orderService.releaseOrderResources(orderNo);
            }
            channel.basicAck(deliveryTag, false);
            log.info("Successfully ACKed order timeout for order: {} ({})", orderNo, outcome);
        } catch (Exception e) {
            log.error("Failed to handle order timeout for order {}: {}", orderNo, e.getMessage(), e);
            retrySupport.retryOrDeadLetter(message, channel, RabbitMQConfig.ORDER_CLOSE_RETRY_QUEUE,
                    RabbitMQConfig.ORDER_CLOSE_DLQ, e.getMessage());
        }
    }
}
