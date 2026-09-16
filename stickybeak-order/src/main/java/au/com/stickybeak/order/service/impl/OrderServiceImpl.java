package au.com.stickybeak.order.service.impl;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.order.client.AuthClient;
import au.com.stickybeak.order.client.CartClient;
import au.com.stickybeak.order.client.PaymentClient;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.dto.AddressDTO;
import au.com.stickybeak.order.dto.CreateOrderRequest;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderItem;
import au.com.stickybeak.order.entity.OrderStatusHistory;
import au.com.stickybeak.order.mapper.OrderItemMapper;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.mapper.OrderStatusHistoryMapper;
import au.com.stickybeak.order.service.OrderService;
import au.com.stickybeak.order.statemachine.OrderStateMachine;
import au.com.stickybeak.order.vo.CheckoutResponseVO;
import au.com.stickybeak.order.vo.OrderItemVO;
import au.com.stickybeak.order.vo.OrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final CartClient cartClient;
    private final PaymentClient paymentClient;
    private final AuthClient authClient;
    private final ProductClient productClient;
    private final OrderStateMachine orderStateMachine;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OrderServiceImpl(OrderMapper orderMapper,
                            OrderItemMapper orderItemMapper,
                            OrderStatusHistoryMapper orderStatusHistoryMapper,
                            CartClient cartClient,
                            PaymentClient paymentClient,
                            AuthClient authClient,
                            ProductClient productClient,
                            OrderStateMachine orderStateMachine,
                            RabbitTemplate rabbitTemplate,
                            ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStatusHistoryMapper = orderStatusHistoryMapper;
        this.cartClient = cartClient;
        this.paymentClient = paymentClient;
        this.authClient = authClient;
        this.productClient = productClient;
        this.orderStateMachine = orderStateMachine;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));
        String orderNo = "SO" + datePrefix + suffix;

        // 5. Deduct stock using 3-layer anti-overselling defense in product service
        List<ProductClient.StockItemParam> stockItems = cart.getItems().stream()
                .map(item -> new ProductClient.StockItemParam(item.getProductId(), item.getQty()))
                .collect(Collectors.toList());
        productClient.deductStock(orderNo, stockItems);

        // 6. Create Order record
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAddressSnapshot(addressSnapshotJson);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(payAmount);
        order.setCurrency(currency);
        order.setExchangeRateAtPay(rate);
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        // 7. Create OrderItem snapshots
        for (CartClient.CartItemData item : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(item.getName());
            orderItem.setProductImage(item.getImageUrl());
            orderItem.setPrice(item.getPrice());
            orderItem.setQty(item.getQty());
            orderItemMapper.insert(orderItem);
        }

        // 8. Record initial status history
        OrderStatusHistory history = new OrderStatusHistory(order.getId(), "none", OrderStatus.PENDING.getCode(), userId, "user");
        orderStatusHistoryMapper.insert(history);

        // 9. Send delay message to RabbitMQ for 30-minute timeout auto-cancel (DLX)
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_DELAY_EXCHANGE, RabbitMQConfig.ORDER_DELAY_ROUTING_KEY, orderNo);
            log.info("Sent order delay message for order: {}", orderNo);
        } catch (Exception e) {
            log.error("Failed to send delay message to RabbitMQ for order {}: {}", orderNo, e.getMessage());
        }

        // 10. Call payment service to create checkout session
        try {
            PaymentClient.PaymentSessionResult sessionResult = paymentClient.createCheckoutSession(
                    orderNo,
                    userId,
                    address.getEmail(),
                    payAmount,
                    currency,
                    req.getPaymentMethod(),
                    "StickyBeak Order " + orderNo
            );

            return new CheckoutResponseVO(
                    orderNo,
                    totalAmount,
                    payAmount,
                    currency,
                    sessionResult.getCheckoutUrl(),
                    sessionResult.getSessionId()
            );
        } catch (Exception e) {
            log.error("Failed to create checkout session for order {}, rolling back stock: {}", orderNo, e.getMessage());
            productClient.rollbackStock(orderNo);
            throw e;
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

        // State machine validation and transition (pending -> cancelled)
        orderStateMachine.transition(order, OrderStatus.CANCELLED, userId, "user");
        orderMapper.updateById(order);

        // Rollback stock in product service
        productClient.rollbackStock(orderNo);
        log.info("Order {} successfully cancelled by user {} and stock rolled back", orderNo, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaid(OrderPaidEvent event) {
        log.info("Handling OrderPaidEvent for order: {}", event.getOrderNo());
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, event.getOrderNo())
        );

        if (order == null) {
            log.warn("Order {} not found when processing OrderPaidEvent", event.getOrderNo());
            return;
        }

        if (OrderStatus.PAID.getCode().equalsIgnoreCase(order.getStatus())) {
            log.info("Order {} is already paid, skipping event.", order.getOrderNo());
            return;
        }

        // State machine transition: pending -> paid
        orderStateMachine.transition(order, OrderStatus.PAID, null, "payment_webhook");
        if (event.getPayTime() != null) {
            order.setPayTime(event.getPayTime());
        }
        orderMapper.updateById(order);

        // Confirm stock deduction
        productClient.confirmStock(order.getOrderNo());

        // Clear cart for buyer
        cartClient.clearCart(order.getUserId());
        log.info("Order {} updated to paid, stock confirmed, and cart cleared.", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderTimeout(String orderNo) {
        log.info("Checking timeout for order: {}", orderNo);
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );

        if (order == null) {
            log.warn("Order {} not found on timeout check", orderNo);
            return;
        }

        // Only auto-cancel if order is still pending
        if (!OrderStatus.PENDING.getCode().equalsIgnoreCase(order.getStatus())) {
            log.info("Order {} is currently in status '{}', not pending. Skipping timeout cancellation.", orderNo, order.getStatus());
            return;
        }

        // State machine transition: pending -> cancelled
        orderStateMachine.transition(order, OrderStatus.CANCELLED, null, "system_timeout");
        orderMapper.updateById(order);

        // Rollback stock in product service
        productClient.rollbackStock(orderNo);
        log.info("Order {} successfully auto-cancelled on timeout and stock rolled back", orderNo);
    }
}
