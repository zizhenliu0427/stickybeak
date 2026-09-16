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
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper();

        orderService = new OrderServiceImpl(
                orderMapper,
                orderItemMapper,
                orderStatusHistoryMapper,
                cartClient,
                paymentClient,
                authClient,
                productClient,
                orderStateMachine,
                rabbitTemplate,
                objectMapper
        );
    }

    @Test
    void testCheckoutSuccess() {
        Long userId = 100L;

        // Mock cart
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

        // Mock address
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

        when(paymentClient.createCheckoutSession(any(), eq(userId), any(), any(), eq("AUD"), eq("card"), any()))
                .thenReturn(new PaymentClient.PaymentSessionResult("SO123", "mock_cs_1", "https://checkout.stripe.com/test", "mock"));

        CheckoutResponseVO result = orderService.checkout(userId, req);

        assertNotNull(result);
        assertNotNull(result.getOrderNo());
        assertTrue(result.getOrderNo().startsWith("SO"));
        assertEquals("https://checkout.stripe.com/test", result.getCheckoutUrl());
        assertEquals("mock_cs_1", result.getSessionId());

        // Verify stock pre-deduction RPC invoked
        verify(productClient, times(1)).deductStock(eq(result.getOrderNo()), anyList());
        // Verify delay message sent to RabbitMQ
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.ORDER_DELAY_EXCHANGE), eq(RabbitMQConfig.ORDER_DELAY_ROUTING_KEY), eq(result.getOrderNo()));
        verify(orderMapper, times(1)).insert(any(Order.class));
        verify(orderItemMapper, times(1)).insert(any(OrderItem.class));
        verify(orderStatusHistoryMapper, times(1)).insert(any(OrderStatusHistory.class));
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
        Order order = new Order();
        order.setId(10L);
        order.setOrderNo("SO202609160010");
        order.setUserId(100L);
        order.setStatus(OrderStatus.PENDING.getCode());

        when(orderMapper.selectOne(any())).thenReturn(order);

        orderService.cancelOrder("SO202609160010", 100L);

        assertEquals(OrderStatus.CANCELLED.getCode(), order.getStatus());
        verify(orderMapper, times(1)).updateById(order);
        verify(productClient, times(1)).rollbackStock("SO202609160010");
        verify(orderStatusHistoryMapper, times(1)).insert(any(OrderStatusHistory.class));
    }

    @Test
    void testHandleOrderPaidUpdatesStatusAndConfirmsStock() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("SO202609160001");
        order.setUserId(200L);
        order.setStatus(OrderStatus.PENDING.getCode());

        when(orderMapper.selectOne(any())).thenReturn(order);

        OrderPaidEvent event = new OrderPaidEvent();
        event.setOrderNo("SO202609160001");
        event.setPayTime(LocalDateTime.now());

        orderService.handleOrderPaid(event);

        assertEquals(OrderStatus.PAID.getCode(), order.getStatus());
        verify(orderMapper, times(1)).updateById(order);
        verify(productClient, times(1)).confirmStock("SO202609160001");
        verify(cartClient, times(1)).clearCart(200L);
    }

    @Test
    void testHandleOrderTimeoutAutoCancelsAndRollsBack() {
        Order order = new Order();
        order.setId(2L);
        order.setOrderNo("SO202609160002");
        order.setUserId(200L);
        order.setStatus(OrderStatus.PENDING.getCode());

        when(orderMapper.selectOne(any())).thenReturn(order);

        orderService.handleOrderTimeout("SO202609160002");

        assertEquals(OrderStatus.CANCELLED.getCode(), order.getStatus());
        verify(orderMapper, times(1)).updateById(order);
        verify(productClient, times(1)).rollbackStock("SO202609160002");
    }

    @Test
    void testHandleOrderTimeoutSkipsIfAlreadyPaid() {
        Order order = new Order();
        order.setId(3L);
        order.setOrderNo("SO202609160003");
        order.setUserId(200L);
        order.setStatus(OrderStatus.PAID.getCode());

        when(orderMapper.selectOne(any())).thenReturn(order);

        orderService.handleOrderTimeout("SO202609160003");

        assertEquals(OrderStatus.PAID.getCode(), order.getStatus());
        verify(orderMapper, never()).updateById(order);
        verify(productClient, never()).rollbackStock(anyString());
    }
}
