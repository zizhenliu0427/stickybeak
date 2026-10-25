package au.com.stickybeak.order.service;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.exception.BusinessException;
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
import au.com.stickybeak.order.service.OrderService.PaidOutcome;
import au.com.stickybeak.order.service.OrderService.RefundOutcome;
import au.com.stickybeak.order.service.OrderService.TimeoutOutcome;
import au.com.stickybeak.order.service.impl.OrderServiceImpl;
import au.com.stickybeak.order.statemachine.OrderStateMachine;
import au.com.stickybeak.order.vo.CheckoutResponseVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceImplTest {

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OrderStatusHistoryMapper orderStatusHistoryMapper;
    private CartClient cartClient;
    private PaymentClient paymentClient;
    private AuthClient authClient;
    private ProductClient productClient;
    private OrderStateMachine orderStateMachine;
    private OrderResourceReleaser orderResourceReleaser;
    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        orderStatusHistoryMapper = mock(OrderStatusHistoryMapper.class);
        cartClient = mock(CartClient.class);
        paymentClient = mock(PaymentClient.class);
        authClient = mock(AuthClient.class);
        productClient = mock(ProductClient.class);
        orderStateMachine = new OrderStateMachine(orderStatusHistoryMapper);
        orderResourceReleaser = mock(OrderResourceReleaser.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper();

        // 真实的事务边界对象（单测里没有 Seata / Spring 事务，只验证调用编排）
        CheckoutTransaction checkoutTransaction = new CheckoutTransaction(orderMapper, orderItemMapper,
                orderStatusHistoryMapper, productClient, orderStateMachine, orderResourceReleaser);

        orderService = new OrderServiceImpl(
                orderMapper,
                orderItemMapper,
                cartClient,
                paymentClient,
                authClient,
                productClient,
                orderStateMachine,
                orderResourceReleaser,
                checkoutTransaction,
                rabbitTemplate,
                objectMapper
        );
    }

    private CreateOrderRequest checkoutRequest(Long userId) {
        CartClient.CartData cart = new CartClient.CartData();
        cart.setTotalAmount(new BigDecimal("50.00"));
        CartClient.CartItemData item = new CartClient.CartItemData();
        item.setProductId(1L);
        item.setName("Sydney Opera House Magnet");
        item.setImageUrl("https://example.com/magnet.jpg");
        item.setPrice(new BigDecimal("25.00"));
        item.setQty(2);
        cart.setItems(List.of(item));
        when(cartClient.getCart(userId)).thenReturn(cart);

        AddressDTO address = new AddressDTO();
        address.setId(1L);
        address.setReceiverName("Jane Doe");
        address.setPhone("0400111222");
        address.setEmail("jane@example.com");
        address.setCity("Sydney");
        address.setDetailAddress("1 Martin Place");

        CreateOrderRequest req = new CreateOrderRequest();
        req.setAddress(address);
        req.setCurrency("AUD");
        req.setPaymentMethod("card");
        return req;
    }

    private static Order order(String orderNo, OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setUserId(200L);
        order.setStatus(status.getCode());
        return order;
    }

    @Test
    void testCheckoutSuccess() {
        Long userId = 100L;
        CreateOrderRequest req = checkoutRequest(userId);
        when(paymentClient.createCheckoutSession(any(), eq(userId), any(), any(), eq("AUD"), eq("card"), any()))
                .thenReturn(new PaymentClient.PaymentSessionResult("SO123", "mock_cs_1", "https://checkout.stripe.com/test", "mock"));

        CheckoutResponseVO result = orderService.checkout(userId, req);

        assertNotNull(result);
        assertNotNull(result.getOrderNo());
        // SO + yyyyMMdd + 雪花 ID，需放得进 VARCHAR(32)
        assertTrue(result.getOrderNo().matches("SO\\d{8}\\d{15,19}"), result.getOrderNo());
        assertTrue(result.getOrderNo().length() <= 32);
        assertEquals("https://checkout.stripe.com/test", result.getCheckoutUrl());
        assertEquals("mock_cs_1", result.getSessionId());

        // Verify stock pre-deduction RPC invoked
        verify(productClient, times(1)).deductStock(eq(result.getOrderNo()), anyList());
        // Verify delay message sent to RabbitMQ
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.ORDER_DELAY_EXCHANGE), eq(RabbitMQConfig.ORDER_DELAY_ROUTING_KEY), eq(result.getOrderNo()));
        verify(orderMapper, times(1)).insert(any(Order.class));
        verify(orderItemMapper, times(1)).insert(any(OrderItem.class));
        verify(orderStatusHistoryMapper, times(1)).insert(any(OrderStatusHistory.class));
        verify(orderResourceReleaser, never()).releaseQuietly(anyString());
    }

    @Test
    void testCheckoutClosesOrderWhenPaymentSessionFails() {
        Long userId = 100L;
        CreateOrderRequest req = checkoutRequest(userId);
        when(paymentClient.createCheckoutSession(any(), eq(userId), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(au.com.stickybeak.common.result.ResultCode.SYSTEM_ERROR, "payment down"));
        // 全局事务已提交，订单已落库；关单时按订单号查回来
        Order[] saved = new Order[1];
        doAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            return 1;
        }).when(orderMapper).insert(any(Order.class));
        when(orderMapper.selectOne(any())).thenAnswer(inv -> saved[0]);
        when(orderMapper.updateIfStatus(any(Order.class), eq(OrderStatus.PENDING.getCode()))).thenReturn(1);

        assertThrows(BusinessException.class, () -> orderService.checkout(userId, req));

        verify(productClient).deductStock(anyString(), anyList());
        assertEquals(OrderStatus.CANCELLED.getCode(), saved[0].getStatus());
        verify(orderResourceReleaser).releaseQuietly(saved[0].getOrderNo());
    }

    @Test
    void testCheckoutStillThrowsPaymentErrorWhenClosingFails() {
        Long userId = 100L;
        CreateOrderRequest req = checkoutRequest(userId);
        when(paymentClient.createCheckoutSession(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(au.com.stickybeak.common.result.ResultCode.SYSTEM_ERROR, "payment down"));
        when(orderMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.checkout(userId, req));

        assertEquals("payment down", ex.getMessage());
        verify(orderResourceReleaser, never()).releaseQuietly(anyString());
    }

    @Test
    void testCheckoutOrderInsertFailureIsLeftToGlobalRollback() {
        Long userId = 100L;
        CreateOrderRequest req = checkoutRequest(userId);
        doThrow(new RuntimeException("db down")).when(orderMapper).insert(any(Order.class));

        assertThrows(RuntimeException.class, () -> orderService.checkout(userId, req));

        // 库存扣减由 Seata 全局回滚撤销，不再手工调用库存回滚；也不会去建支付会话、发超时消息
        verify(orderResourceReleaser, never()).releaseQuietly(anyString());
        verify(paymentClient, never()).createCheckoutSession(any(), any(), any(), any(), any(), any(), any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void testCheckoutEmptyCartThrows() {
        Long userId = 100L;
        when(cartClient.getCart(userId)).thenReturn(new CartClient.CartData());

        CreateOrderRequest req = new CreateOrderRequest();
        assertThrows(BusinessException.class, () -> orderService.checkout(userId, req));
        verify(productClient, never()).deductStock(anyString(), anyList());
    }

    @Test
    void testCancelOrderSuccess() {
        Order order = order("SO202609160010", OrderStatus.PENDING);
        order.setUserId(100L);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateIfStatus(order, OrderStatus.PENDING.getCode())).thenReturn(1);

        orderService.cancelOrder("SO202609160010", 100L);

        assertEquals(OrderStatus.CANCELLED.getCode(), order.getStatus());
        verify(orderMapper, times(1)).updateIfStatus(order, OrderStatus.PENDING.getCode());
        verify(orderResourceReleaser, times(1)).releaseQuietly("SO202609160010");
        verify(orderStatusHistoryMapper, times(1)).insert(any(OrderStatusHistory.class));
    }

    @Test
    void testCancelPaidOrderRejected() {
        Order order = order("SO202609160011", OrderStatus.PAID);
        order.setUserId(100L);
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertThrows(BusinessException.class, () -> orderService.cancelOrder("SO202609160011", 100L));
        verify(orderMapper, never()).updateIfStatus(any(), anyString());
        verify(orderResourceReleaser, never()).releaseQuietly(anyString());
    }

    @Test
    void testCancelOrderLosesRaceToPayment() {
        Order order = order("SO202609160012", OrderStatus.PENDING);
        order.setUserId(100L);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateIfStatus(order, OrderStatus.PENDING.getCode())).thenReturn(0);

        assertThrows(BusinessException.class, () -> orderService.cancelOrder("SO202609160012", 100L));
        verify(orderResourceReleaser, never()).releaseQuietly(anyString());
    }

    @Test
    void testMarkPaidUpdatesStatusWithoutRemoteCalls() {
        Order order = order("SO202609160001", OrderStatus.PENDING);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateIfStatus(order, OrderStatus.PENDING.getCode())).thenReturn(1);

        OrderPaidEvent event = new OrderPaidEvent();
        event.setOrderNo("SO202609160001");
        event.setPayTime(LocalDateTime.now());

        assertEquals(PaidOutcome.PAID_NOW, orderService.markPaid(event));
        assertEquals(OrderStatus.PAID.getCode(), order.getStatus());
        verify(orderMapper, times(1)).updateIfStatus(order, OrderStatus.PENDING.getCode());
        verifyNoInteractions(productClient, cartClient);
    }

    @Test
    void testMarkPaidOutcomes() {
        OrderPaidEvent event = new OrderPaidEvent();
        event.setOrderNo("SO1");

        when(orderMapper.selectOne(any())).thenReturn(null);
        assertEquals(PaidOutcome.NOT_FOUND, orderService.markPaid(event));

        when(orderMapper.selectOne(any())).thenReturn(order("SO1", OrderStatus.SHIPPED));
        assertEquals(PaidOutcome.ALREADY_PAID, orderService.markPaid(event));

        when(orderMapper.selectOne(any())).thenReturn(order("SO1", OrderStatus.REFUNDING));
        assertEquals(PaidOutcome.ALREADY_PAID, orderService.markPaid(event));

        when(orderMapper.selectOne(any())).thenReturn(order("SO1", OrderStatus.CANCELLED));
        assertEquals(PaidOutcome.NOT_PAYABLE, orderService.markPaid(event));

        verify(orderMapper, never()).updateIfStatus(any(), anyString());
    }

    @Test
    void testMarkPaidConflictThrowsSoTransactionRollsBack() {
        Order order = order("SO2", OrderStatus.PENDING);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateIfStatus(order, OrderStatus.PENDING.getCode())).thenReturn(0);

        OrderPaidEvent event = new OrderPaidEvent();
        event.setOrderNo("SO2");
        assertThrows(BusinessException.class, () -> orderService.markPaid(event));
    }

    @Test
    void testCompletePaidOrderConfirmsStockAndClearsCart() {
        when(orderMapper.selectOne(any())).thenReturn(order("SO202609160001", OrderStatus.PAID));

        orderService.completePaidOrder("SO202609160001");

        verify(productClient, times(1)).confirmStock("SO202609160001");
        verify(cartClient, times(1)).clearCart(200L);
    }

    @Test
    void testCloseIfUnpaidCancelsPendingOrder() {
        Order order = order("SO202609160002", OrderStatus.PENDING);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateIfStatus(order, OrderStatus.PENDING.getCode())).thenReturn(1);

        assertEquals(TimeoutOutcome.CLOSED_NOW, orderService.closeIfUnpaid("SO202609160002"));
        assertEquals(OrderStatus.CANCELLED.getCode(), order.getStatus());
        // 释放由监听器在事务提交后调用
        verifyNoInteractions(orderResourceReleaser, productClient);
    }

    @Test
    void testCloseIfUnpaidSkipsPaidAndReportsCancelled() {
        Order paid = order("SO202609160003", OrderStatus.PAID);
        when(orderMapper.selectOne(any())).thenReturn(paid);
        assertEquals(TimeoutOutcome.NOT_PENDING, orderService.closeIfUnpaid("SO202609160003"));
        assertEquals(OrderStatus.PAID.getCode(), paid.getStatus());

        when(orderMapper.selectOne(any())).thenReturn(order("SO202609160004", OrderStatus.CANCELLED));
        assertEquals(TimeoutOutcome.ALREADY_CLOSED, orderService.closeIfUnpaid("SO202609160004"));

        when(orderMapper.selectOne(any())).thenReturn(null);
        assertEquals(TimeoutOutcome.NOT_FOUND, orderService.closeIfUnpaid("SO_NONE"));

        verify(orderMapper, never()).updateIfStatus(any(), anyString());
    }

    @Test
    void testReleaseOrderResourcesDelegates() {
        orderService.releaseOrderResources("SO9");
        verify(orderResourceReleaser).release("SO9");
    }

    @Test
    void testMarkRefundedMovesRefundingToRefunded() {
        Order order = order("SO_R1", OrderStatus.REFUNDING);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateIfStatus(order, OrderStatus.REFUNDING.getCode())).thenReturn(1);

        assertEquals(RefundOutcome.REFUNDED_NOW, orderService.markRefunded("SO_R1"));
        assertEquals(OrderStatus.REFUNDED.getCode(), order.getStatus());
        verify(orderStatusHistoryMapper).insert(any(OrderStatusHistory.class));
    }

    @Test
    void testMarkRefundedOutcomes() {
        when(orderMapper.selectOne(any())).thenReturn(order("SO_R2", OrderStatus.REFUNDED));
        assertEquals(RefundOutcome.ALREADY_REFUNDED, orderService.markRefunded("SO_R2"));

        when(orderMapper.selectOne(any())).thenReturn(order("SO_R3", OrderStatus.CANCELLED));
        assertEquals(RefundOutcome.NOT_REFUNDING, orderService.markRefunded("SO_R3"));

        when(orderMapper.selectOne(any())).thenReturn(null);
        assertEquals(RefundOutcome.NOT_FOUND, orderService.markRefunded("SO_NONE"));

        verify(orderMapper, never()).updateIfStatus(any(), anyString());
    }

    @Test
    void testRefundHelpersDelegate() {
        orderService.restockRefundedOrder("SO_R4");
        verify(productClient).restockStock("SO_R4");

        orderService.refundPaidAfterCancel("SO_R5");
        verify(paymentClient).refund(eq("SO_R5"), anyString());
    }
}
