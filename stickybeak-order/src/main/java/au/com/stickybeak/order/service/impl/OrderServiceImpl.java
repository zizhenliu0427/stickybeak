package au.com.stickybeak.order.service.impl;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.common.tx.AfterCommit;
import au.com.stickybeak.order.client.AuthClient;
import au.com.stickybeak.order.client.CartClient;
import au.com.stickybeak.order.client.PaymentClient;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.dto.AddressDTO;
import au.com.stickybeak.order.dto.CreateOrderRequest;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderItem;
import au.com.stickybeak.order.mapper.OrderItemMapper;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.service.CheckoutTransaction;
import au.com.stickybeak.order.service.OrderResourceReleaser;
import au.com.stickybeak.order.service.OrderService;
import au.com.stickybeak.order.statemachine.OrderStateMachine;
import au.com.stickybeak.order.vo.CheckoutResponseVO;
import au.com.stickybeak.order.vo.OrderItemVO;
import au.com.stickybeak.order.vo.OrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartClient cartClient;
    private final PaymentClient paymentClient;
    private final AuthClient authClient;
    private final ProductClient productClient;
    private final OrderStateMachine orderStateMachine;
    private final OrderResourceReleaser orderResourceReleaser;
    private final CheckoutTransaction checkoutTransaction;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OrderServiceImpl(OrderMapper orderMapper,
                            OrderItemMapper orderItemMapper,
                            CartClient cartClient,
                            PaymentClient paymentClient,
                            AuthClient authClient,
                            ProductClient productClient,
                            OrderStateMachine orderStateMachine,
                            OrderResourceReleaser orderResourceReleaser,
                            CheckoutTransaction checkoutTransaction,
                            RabbitTemplate rabbitTemplate,
                            ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.cartClient = cartClient;
        this.paymentClient = paymentClient;
        this.authClient = authClient;
        this.productClient = productClient;
        this.orderStateMachine = orderStateMachine;
        this.orderResourceReleaser = orderResourceReleaser;
        this.checkoutTransaction = checkoutTransaction;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 不加 @Transactional：分布式事务边界在 {@link CheckoutTransaction#placeOrder}，
     * 前面的读购物车、地址、汇率与后面的支付会话都不应占着全局锁。
     */
    @Override
    public CheckoutResponseVO checkout(Long userId, CreateOrderRequest req) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 1. Fetch cart
        CartClient.CartData cart = cartClient.getCart(userId);
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Shopping cart is empty");
        }

        // 2. Resolve address snapshot
        AddressDTO address = req.getAddress();
        if (address == null && req.getAddressId() != null) {
            address = authClient.getAddressById(userId, req.getAddressId());
        }
        if (address == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Shipping address is required");
        }

        String addressSnapshotJson;
        try {
            addressSnapshotJson = objectMapper.writeValueAsString(address);
        } catch (Exception e) {
            log.error("Failed to serialize address snapshot: {}", e.getMessage());
            addressSnapshotJson = "{}";
        }

        // 3. Multi-currency and amount calculation
        String currency = StringUtils.hasText(req.getCurrency()) ? req.getCurrency().toUpperCase() : "AUD";
        BigDecimal totalAmount = cart.getTotalAmount();
        BigDecimal rate = BigDecimal.ONE;
        BigDecimal payAmount = totalAmount;

        if ("CNY".equalsIgnoreCase(currency)) {
            rate = paymentClient.getExchangeRate("AUD", "CNY");
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                rate = new BigDecimal("4.750000");
            }
            payAmount = totalAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }

        // 4. Generate orderNo
        // 日期 + 雪花 ID（"SO" + 8 + 19 位 = 29 位，列宽 32）。原先的 8 位随机后缀在日均几千单时就可能撞号，
        // 撞号会让两张订单共用库存预占记录
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String orderNo = "SO" + datePrefix + IdWorker.getId();

        // 5. 扣库存 + 写订单：Seata 全局事务，失败时商品库与订单库一起回滚
        checkoutTransaction.placeOrder(new CheckoutTransaction.PlaceOrderCommand(
                userId, orderNo, cart.getItems(), addressSnapshotJson,
                currency, totalAmount, payAmount, rate, req.getRemark()));

        // 6. 全局事务已提交。30 分钟未支付自动关单（DLX 延迟消息；丢失时由 OrderTimeoutSweeper 兜底）
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_DELAY_EXCHANGE, RabbitMQConfig.ORDER_DELAY_ROUTING_KEY, orderNo);
            log.info("Sent order delay message for order: {}", orderNo);
        } catch (Exception e) {
            log.error("Failed to send delay message to RabbitMQ for order {}: {}", orderNo, e.getMessage());
        }

        // 7. 创建支付会话（外部调用，不放进全局事务）；失败时订单无法支付，立即关闭并归还库存
        PaymentClient.PaymentSessionResult sessionResult;
        try {
            sessionResult = paymentClient.createCheckoutSession(
                    orderNo,
                    userId,
                    address.getEmail(),
                    payAmount,
                    currency,
                    req.getPaymentMethod(),
                    "StickyBeak Order " + orderNo
            );
        } catch (RuntimeException e) {
            log.error("Payment session creation failed for order {}, closing it: {}", orderNo, e.getMessage());
            closeUnpayableQuietly(orderNo);
            throw e;
        }

        return new CheckoutResponseVO(
                orderNo,
                totalAmount,
                payAmount,
                currency,
                sessionResult.getCheckoutUrl(),
                sessionResult.getSessionId()
        );
    }

    private void closeUnpayableQuietly(String orderNo) {
        try {
            checkoutTransaction.cancelUnpayable(orderNo);
        } catch (Exception e) {
            // 关单失败时订单保持待支付，30 分钟超时消息 / 扫描任务会再关一次
            log.error("Failed to close unpayable order {}, the timeout will close it: {}", orderNo, e.getMessage());
        }
    }

    @Override
    public OrderVO getByOrderNo(String orderNo, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Order not found");
        }
        if (userId != null && !userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())
        );

        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setItems(items.stream().map(item -> {
            OrderItemVO itemVO = new OrderItemVO();
            BeanUtils.copyProperties(item, itemVO);
            return itemVO;
        }).collect(Collectors.toList()));

        return vo;
    }

    @Override
    public List<OrderVO> listUserOrders(Long userId, String status) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId);

        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())) {
            wrapper.eq(Order::getStatus, status.trim().toLowerCase());
        }

        wrapper.orderByDesc(Order::getCreateTime);
        List<Order> orders = orderMapper.selectList(wrapper);

        List<OrderVO> result = new ArrayList<>();
        for (Order order : orders) {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())
            );
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(order, vo);
            vo.setItems(items.stream().map(item -> {
                OrderItemVO itemVO = new OrderItemVO();
                BeanUtils.copyProperties(item, itemVO);
                return itemVO;
            }).collect(Collectors.toList()));
            result.add(vo);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo, Long userId) {
        log.info("User {} requested cancellation for order: {}", userId, orderNo);
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Order not found");
        }
        if (userId != null && !userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        // 已付款订单的取消涉及退款，只能由客服 / 管理员处理
        if (!OrderStatus.PENDING.getCode().equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION,
                    "Only unpaid orders can be cancelled; please contact support for paid orders");
        }

        String fromStatus = order.getStatus();
        orderStateMachine.transition(order, OrderStatus.CANCELLED, userId, "user");
        persistTransition(order, fromStatus);

        // 提交后再释放：先让取消落库，避免"库存已归还但订单仍可支付"
        AfterCommit.run(() -> orderResourceReleaser.releaseQuietly(orderNo));
        log.info("Order {} cancelled by user {}", orderNo, userId);
    }

    @Override
    public CheckoutResponseVO resumePayment(String orderNo, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Order not found");
        }
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (!OrderStatus.PENDING.getCode().equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_ALREADY_CLOSED, "Only unpaid orders can be paid");
        }

        String email = null;
        try {
            AddressDTO address = objectMapper.readValue(order.getAddressSnapshot(), AddressDTO.class);
            email = address.getEmail();
        } catch (Exception e) {
            log.warn("Unreadable address snapshot on order {}, paying without a receipt email: {}", orderNo, e.getMessage());
        }

        // 支付方式传 null：支付服务沿用下单时选的方式
        PaymentClient.PaymentSessionResult session = paymentClient.createCheckoutSession(
                orderNo, userId, email, order.getPayAmount(), order.getCurrency(), null,
                "StickyBeak Order " + orderNo);
        log.info("Issued a new payment session for pending order {}", orderNo);
        return new CheckoutResponseVO(orderNo, order.getTotalAmount(), order.getPayAmount(), order.getCurrency(),
                session.getCheckoutUrl(), session.getSessionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaidOutcome markPaid(OrderPaidEvent event) {
        log.info("Handling OrderPaidEvent for order: {}", event.getOrderNo());
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, event.getOrderNo())
        );
        if (order == null) {
            log.warn("Order {} not found when processing OrderPaidEvent", event.getOrderNo());
            return PaidOutcome.NOT_FOUND;
        }

        OrderStatus current = OrderStatus.fromCode(order.getStatus());
        if (current == OrderStatus.PAID || current == OrderStatus.PROCESSING
                || current == OrderStatus.SHIPPED || current == OrderStatus.COMPLETED
                || current == OrderStatus.REFUNDING || current == OrderStatus.REFUNDED) {
            log.info("Order {} is already {}, skipping paid transition", order.getOrderNo(), order.getStatus());
            return PaidOutcome.ALREADY_PAID;
        }
        if (current != OrderStatus.PENDING) {
            log.error("Order {} is {} but a payment arrived, refund required", order.getOrderNo(), order.getStatus());
            return PaidOutcome.NOT_PAYABLE;
        }

        String fromStatus = order.getStatus();
        orderStateMachine.transition(order, OrderStatus.PAID, null, "payment_webhook");
        if (event.getPayTime() != null) {
            order.setPayTime(event.getPayTime());
        }
        persistTransition(order, fromStatus);
        return PaidOutcome.PAID_NOW;
    }

    @Override
    public void completePaidOrder(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            return;
        }
        productClient.confirmStock(orderNo);
        cartClient.clearCart(order.getUserId());
        log.info("Order {} paid: stock confirmed and cart cleared", orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TimeoutOutcome closeIfUnpaid(String orderNo) {
        log.info("Checking timeout for order: {}", orderNo);
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            log.warn("Order {} not found on timeout check", orderNo);
            return TimeoutOutcome.NOT_FOUND;
        }
        if (OrderStatus.CANCELLED.getCode().equalsIgnoreCase(order.getStatus())) {
            return TimeoutOutcome.ALREADY_CLOSED;
        }
        if (!OrderStatus.PENDING.getCode().equalsIgnoreCase(order.getStatus())) {
            log.info("Order {} is currently in status '{}', not pending. Skipping timeout cancellation.", orderNo, order.getStatus());
            return TimeoutOutcome.NOT_PENDING;
        }

        String fromStatus = order.getStatus();
        orderStateMachine.transition(order, OrderStatus.CANCELLED, null, "system_timeout");
        persistTransition(order, fromStatus);
        log.info("Order {} auto-cancelled on timeout", orderNo);
        return TimeoutOutcome.CLOSED_NOW;
    }

    @Override
    public void releaseOrderResources(String orderNo) {
        orderResourceReleaser.release(orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundOutcome markRefunded(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            log.warn("Order {} not found when processing OrderRefundedEvent", orderNo);
            return RefundOutcome.NOT_FOUND;
        }
        if (OrderStatus.REFUNDED.getCode().equalsIgnoreCase(order.getStatus())) {
            return RefundOutcome.ALREADY_REFUNDED;
        }
        if (!OrderStatus.REFUNDING.getCode().equalsIgnoreCase(order.getStatus())) {
            log.info("Order {} is {}, refund recorded without status change", orderNo, order.getStatus());
            return RefundOutcome.NOT_REFUNDING;
        }

        String fromStatus = order.getStatus();
        orderStateMachine.transition(order, OrderStatus.REFUNDED, null, "payment_webhook");
        persistTransition(order, fromStatus);
        log.info("Order {} refunded", orderNo);
        return RefundOutcome.REFUNDED_NOW;
    }

    @Override
    public void restockRefundedOrder(String orderNo) {
        productClient.restockStock(orderNo);
    }

    @Override
    public void refundPaidAfterCancel(String orderNo) {
        log.warn("Order {} was paid after it was cancelled, refunding automatically", orderNo);
        paymentClient.refund(orderNo, "Order was cancelled before the payment completed");
    }

    /**
     * 条件更新落库。影响 0 行说明状态已被其他流程改掉（并发的支付 / 超时 / 取消），
     * 抛出异常让本事务（含状态历史）回滚，由调用方重试或提示用户。
     */
    private void persistTransition(Order order, String expectedStatus) {
        if (orderMapper.updateIfStatus(order, expectedStatus) == 0) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "Order " + order.getOrderNo() + " was changed concurrently, please retry");
        }
    }
}
