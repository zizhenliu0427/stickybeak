package au.com.stickybeak.order.service;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.order.client.AuthClient;
import au.com.stickybeak.order.client.CartClient;
import au.com.stickybeak.order.client.PaymentClient;
import au.com.stickybeak.order.dto.AddressDTO;
import au.com.stickybeak.order.dto.CreateOrderRequest;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderItem;
import au.com.stickybeak.order.entity.OrderStatusHistory;
import au.com.stickybeak.order.mapper.OrderItemMapper;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.mapper.OrderStatusHistoryMapper;
import au.com.stickybeak.order.service.impl.OrderServiceImpl;
import au.com.stickybeak.order.vo.CheckoutResponseVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        objectMapper = new ObjectMapper();

        orderService = new OrderServiceImpl(
                orderMapper,
                orderItemMapper,
                orderStatusHistoryMapper,
                cartClient,
                paymentClient,
                authClient,
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
    }

    @Test
    void testHandleOrderPaidUpdatesStatusAndClearsCart() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("SO202609160001");
        order.setUserId(200L);
        order.setStatus("pending");

        when(orderMapper.selectOne(any())).thenReturn(order);

        OrderPaidEvent event = new OrderPaidEvent();
        event.setOrderNo("SO202609160001");
        event.setPayTime(LocalDateTime.now());

        orderService.handleOrderPaid(event);

        assertEquals("paid", order.getStatus());
        verify(orderMapper, times(1)).updateById(order);
        verify(orderStatusHistoryMapper, times(1)).insert(any(OrderStatusHistory.class));
        verify(cartClient, times(1)).clearCart(200L);
    }
}
