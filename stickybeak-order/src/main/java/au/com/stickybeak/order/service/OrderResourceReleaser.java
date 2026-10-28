package au.com.stickybeak.order.service;

import au.com.stickybeak.order.client.PaymentClient;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.listener.MessageRetrySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单关闭（取消 / 超时）后释放占用的资源：先让支付会话失效，再归还库存。
 * 两步都是幂等的，可以被超时消息、补偿扫描重复调用。
 */
@Component
public class OrderResourceReleaser {

    private static final Logger log = LoggerFactory.getLogger(OrderResourceReleaser.class);

    private final PaymentClient paymentClient;
    private final ProductClient productClient;
    private final RabbitTemplate rabbitTemplate;

    public OrderResourceReleaser(PaymentClient paymentClient, ProductClient productClient, RabbitTemplate rabbitTemplate) {
        this.paymentClient = paymentClient;
        this.productClient = productClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 库存归还失败会抛出，供消息监听器重试 */
    public void release(String orderNo) {
        try {
            paymentClient.expireSession(orderNo);
        } catch (Exception e) {
            // 会话最多 31 分钟后自然过期；关单后到账的支付会进入死信队列等待退款
            log.warn("Failed to expire payment session for closed order {}: {}", orderNo, e.getMessage());
        }
        productClient.rollbackStock(orderNo);
    }

    /**
     * 用户主动取消 / 下单失败时使用，不向调用方抛错。归还失败（热点商品被结账全局锁占着等）时
     * 把订单号投进关单重试队列，约 10 秒后由关单监听器再释放一次；不必等 30 分钟后的超时消息，
     * 否则秒杀时被取消的库存会被锁住半小时。投递也失败时，仍由超时消息兜底。
     */
    public void releaseQuietly(String orderNo) {
        try {
            release(orderNo);
        } catch (Exception e) {
            log.error("Failed to release resources for order {}, scheduling a retry: {}", orderNo, e.getMessage());
            try {
                rabbitTemplate.convertAndSend("", RabbitMQConfig.ORDER_CLOSE_RETRY_QUEUE, orderNo, message -> {
                    message.getMessageProperties().setHeader(MessageRetrySupport.RETRY_COUNT_HEADER, 1);
                    message.getMessageProperties().setHeader(MessageRetrySupport.FAILURE_REASON_HEADER,
                            String.valueOf(e.getMessage()));
                    return message;
                });
            } catch (Exception sendError) {
                log.error("Failed to schedule release retry for order {}, the timeout message will retry: {}",
                        orderNo, sendError.getMessage());
            }
        }
    }
}
