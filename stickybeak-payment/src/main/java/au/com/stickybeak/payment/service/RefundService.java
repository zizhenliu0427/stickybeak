package au.com.stickybeak.payment.service;

public interface RefundService {

    String STATUS_REFUNDING = "refunding";
    String STATUS_REFUNDED = "refunded";

    /**
     * 对已支付订单发起全额退款，返回 refunded（渠道同步成功）或 refunding（等待 webhook 确认）。
     * 幂等：已退款的订单直接返回 refunded 并重新发布退款事件，帮助订单侧在事件丢失后恢复。
     */
    String refund(String orderNo, String reason);

    /** webhook 确认退款到账（按渠道交易号定位），未找到对应支付记录时返回 false */
    boolean confirmRefund(String transactionId, String refundId);
}
