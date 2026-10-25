package au.com.stickybeak.order.service;

import au.com.stickybeak.order.client.PaymentClient;
import au.com.stickybeak.order.client.ProductClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public OrderResourceReleaser(PaymentClient paymentClient, ProductClient productClient) {
        this.paymentClient = paymentClient;
        this.productClient = productClient;
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

    /** 用户主动取消时使用：失败只记日志，30 分钟后的超时消息会再次释放 */
    public void releaseQuietly(String orderNo) {
        try {
            release(orderNo);
        } catch (Exception e) {
            log.error("Failed to release resources for order {}, the timeout message will retry: {}", orderNo, e.getMessage());
        }
    }
}
