package au.com.stickybeak.order.service;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.tx.AfterCommit;
import au.com.stickybeak.order.client.CartClient;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderItem;
import au.com.stickybeak.order.entity.OrderStatusHistory;
import au.com.stickybeak.order.mapper.OrderItemMapper;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.mapper.OrderStatusHistoryMapper;
import au.com.stickybeak.order.statemachine.OrderStateMachine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import io.seata.tm.api.transaction.TransactionHookAdapter;
import io.seata.tm.api.transaction.TransactionHookManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 结账的分布式事务边界（Seata AT）。
 * <p>
 * 全局事务只包住"扣库存（product 库，远程分支）+ 写订单（order 库，本地分支）"，任一步失败两个库一起回滚。
 * 支付会话（调用 Stripe）放在全局事务之外：AT 分支提交后，商品行的全局锁要到全局提交才释放，
 * 把几百毫秒的外部 HTTP 调用放进来，同一热门商品的结账会在全局锁上排队甚至超时失败。
 */
@Component
public class CheckoutTransaction {

    private static final Logger log = LoggerFactory.getLogger(CheckoutTransaction.class);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final ProductClient productClient;
    private final OrderStateMachine orderStateMachine;
    private final OrderResourceReleaser orderResourceReleaser;

    public CheckoutTransaction(OrderMapper orderMapper,
                               OrderItemMapper orderItemMapper,
                               OrderStatusHistoryMapper orderStatusHistoryMapper,
                               ProductClient productClient,
                               OrderStateMachine orderStateMachine,
                               OrderResourceReleaser orderResourceReleaser) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStatusHistoryMapper = orderStatusHistoryMapper;
        this.productClient = productClient;
        this.orderStateMachine = orderStateMachine;
        this.orderResourceReleaser = orderResourceReleaser;
    }

    /** 下单所需的、在全局事务开始前已经准备好的数据 */
    public record PlaceOrderCommand(Long userId,
                                    String orderNo,
                                    List<CartClient.CartItemData> items,
                                    String addressSnapshotJson,
                                    String currency,
                                    BigDecimal totalAmount,
                                    BigDecimal payAmount,
                                    BigDecimal exchangeRate,
                                    String remark) {
    }

    @GlobalTransactional(name = "stickybeak-checkout", timeoutMills = 15000, rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public Order placeOrder(PlaceOrderCommand cmd) {
        // 先注册再扣库存：扣库存的请求超时时商品服务可能已经扣了，只要全局事务回滚就要归还 Redis
        registerRedisCompensation(cmd.orderNo());

        List<ProductClient.StockItemParam> stockItems = cmd.items().stream()
                .map(item -> new ProductClient.StockItemParam(item.getProductId(), item.getQty()))
                .toList();
        productClient.deductStock(cmd.orderNo(), stockItems);

        Order order = new Order();
        order.setOrderNo(cmd.orderNo());
        order.setUserId(cmd.userId());
        order.setAddressSnapshot(cmd.addressSnapshotJson());
        order.setTotalAmount(cmd.totalAmount());
        order.setPayAmount(cmd.payAmount());
        order.setCurrency(cmd.currency());
        order.setExchangeRateAtPay(cmd.exchangeRate());
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setRemark(cmd.remark());
        orderMapper.insert(order);

        for (CartClient.CartItemData item : cmd.items()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(item.getName());
            orderItem.setProductImage(item.getImageUrl());
            orderItem.setPrice(item.getPrice());
            orderItem.setQty(item.getQty());
            orderItemMapper.insert(orderItem);
        }

        orderStatusHistoryMapper.insert(
                new OrderStatusHistory(order.getId(), "none", OrderStatus.PENDING.getCode(), cmd.userId(), "user"));
        return order;
    }

    /**
     * MySQL 的扣减由 undo_log 撤销，Redis 预扣不在全局事务里：全局回滚完成后通知商品服务按订单账本归还。
     * 归还是幂等的，商品服务本地事务失败时已经归还过也没关系。
     */
    private void registerRedisCompensation(String orderNo) {
        if (!RootContext.inGlobalTransaction()) {
            return;
        }
        TransactionHookManager.registerHook(new TransactionHookAdapter() {
            @Override
            public void afterRollback() {
                try {
                    productClient.restoreRedisReservation(orderNo);
                } catch (Exception e) {
                    // Redis 只会少不会多（不会超卖）；管理员调整库存或 key 过期后从 MySQL 重新预热
                    log.error("Failed to restore Redis stock after checkout rollback for order {}: {}",
                            orderNo, e.getMessage());
                }
            }
        });
    }

    /**
     * 全局事务已提交、但支付会话没建成：订单无法支付，立即关闭并释放库存，不等 30 分钟超时。
     *
     * @return 是否由本次调用关闭（订单已不是待支付时返回 false）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelUnpayable(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null || !OrderStatus.PENDING.getCode().equalsIgnoreCase(order.getStatus())) {
            return false;
        }
        String fromStatus = order.getStatus();
        orderStateMachine.transition(order, OrderStatus.CANCELLED, null, "system_payment_setup");
        if (orderMapper.updateIfStatus(order, fromStatus) == 0) {
            // 并发的取消 / 超时已经处理过，本事务（含状态历史）回滚
            throw new IllegalStateException("Order " + orderNo + " changed concurrently while closing");
        }
        AfterCommit.run(() -> orderResourceReleaser.releaseQuietly(orderNo));
        return true;
    }
}
