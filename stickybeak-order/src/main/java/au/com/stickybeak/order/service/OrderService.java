package au.com.stickybeak.order.service;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.order.dto.CreateOrderRequest;
import au.com.stickybeak.order.vo.CheckoutResponseVO;
import au.com.stickybeak.order.vo.OrderVO;

import java.util.List;

public interface OrderService {

    /** 支付成功事件的处理结果 */
    enum PaidOutcome {
        /** 本次把 pending 推进为 paid */
        PAID_NOW,
        /** 已经是 paid 或之后的状态（重复投递 / 重试） */
        ALREADY_PAID,
        /** 订单已取消却收到付款，需要人工退款 */
        NOT_PAYABLE,
        NOT_FOUND
    }

    /** 退款到账事件的处理结果 */
    enum RefundOutcome {
        /** 本次把 refunding 推进为 refunded */
        REFUNDED_NOW,
        ALREADY_REFUNDED,
        /** 订单不在退款中（例如关单后到账、已原路退回的订单仍保持 cancelled） */
        NOT_REFUNDING,
        NOT_FOUND
    }

    /** 超时关单的处理结果 */
    enum TimeoutOutcome {
        /** 本次把 pending 关闭为 cancelled */
        CLOSED_NOW,
        /** 订单已被取消（用户主动或之前的消息） */
        ALREADY_CLOSED,
        /** 已支付等非 pending 状态，无需关单 */
        NOT_PENDING,
        NOT_FOUND
    }

    CheckoutResponseVO checkout(Long userId, CreateOrderRequest req);

    OrderVO getByOrderNo(String orderNo, Long userId);

    List<OrderVO> listUserOrders(Long userId, String status);

    void cancelOrder(String orderNo, Long userId);

    /**
     * 为自己的待支付订单重新取支付链接（「我的订单」里的立即支付），不重新下单、不重复占库存。
     * 支付服务会先让该订单的旧会话失效，保证同一时刻只有一个可付款的会话。
     */
    CheckoutResponseVO resumePayment(String orderNo, Long userId);

    /** 事务内把订单推进为 paid（条件更新），不做远程调用 */
    PaidOutcome markPaid(OrderPaidEvent event);

    /** 支付后的副作用：确认库存、清空购物车。均幂等，失败抛出以便重试 */
    void completePaidOrder(String orderNo);

    /** 事务内把仍为 pending 的订单关闭（条件更新），不做远程调用 */
    TimeoutOutcome closeIfUnpaid(String orderNo);

    /** 关单后释放支付会话与库存，幂等，失败抛出以便重试 */
    void releaseOrderResources(String orderNo);

    /** 事务内把 refunding 推进为 refunded（条件更新），不做远程调用 */
    RefundOutcome markRefunded(String orderNo);

    /** 退款后回补已确认的库存，幂等，失败抛出以便重试 */
    void restockRefundedOrder(String orderNo);

    /** 订单已取消后才到账的付款：原路退回（支付侧幂等），失败抛出以便重试 */
    void refundPaidAfterCancel(String orderNo);
}
