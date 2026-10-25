package au.com.stickybeak.common.event;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款到账事件：支付服务在渠道确认退款成功后发布，订单服务据此推进为 refunded 并回补库存。
 * 与 OrderPaidEvent 共用 order.topic 交换机，路由键不同（通知服务只绑定 order.paid，不会收到）。
 */
public class OrderRefundedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String EXCHANGE = OrderPaidEvent.EXCHANGE;
    public static final String ROUTING_KEY = "order.refunded";

    private String orderNo;
    private BigDecimal amount;
    private String currency;
    private String refundId;
    private LocalDateTime refundTime;

    public OrderRefundedEvent() {
    }

    public OrderRefundedEvent(String orderNo, BigDecimal amount, String currency, String refundId, LocalDateTime refundTime) {
        this.orderNo = orderNo;
        this.amount = amount;
        this.currency = currency;
        this.refundId = refundId;
        this.refundTime = refundTime;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }

    public LocalDateTime getRefundTime() {
        return refundTime;
    }

    public void setRefundTime(LocalDateTime refundTime) {
        this.refundTime = refundTime;
    }
}
