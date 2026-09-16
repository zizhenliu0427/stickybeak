package au.com.stickybeak.order.service;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.dto.AdminOrderStatusUpdateRequest;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.mapper.OrderItemMapper;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.service.impl.AdminOrderServiceImpl;
import au.com.stickybeak.order.statemachine.OrderStateMachine;
import au.com.stickybeak.order.vo.AnalyticsSummaryVO;
import au.com.stickybeak.order.vo.SalesTrendItemVO;
import au.com.stickybeak.order.vo.TopProductVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderStateMachine orderStateMachine;
    @Mock
    private ProductClient productClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AdminOrderServiceImpl adminOrderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        adminOrderService = new AdminOrderServiceImpl(
                orderMapper, orderItemMapper, orderStateMachine, productClient, stringRedisTemplate, objectMapper);
    }

    @Test
    void testGetAnalyticsSummary() {
        when(orderMapper.sumTotalGmv()).thenReturn(new BigDecimal("12500.50"));
        when(orderMapper.sumTodayGmv()).thenReturn(new BigDecimal("350.00"));
        when(orderMapper.selectCount(any())).thenReturn(100L);

        AnalyticsSummaryVO summary = adminOrderService.getAnalyticsSummary();

        assertNotNull(summary);
        assertEquals(new BigDecimal("12500.50"), summary.getTotalGmv());
        assertEquals(new BigDecimal("350.00"), summary.getTodayGmv());
        assertEquals(100L, summary.getTotalOrders());
    }

    @Test
    void testGetSalesTrend() {
        Map<String, Object> row = Map.of("order_date", "2026-09-15", "order_count", 5, "daily_gmv", 150.0);
        when(orderMapper.selectDailySalesTrend(7)).thenReturn(List.of(row));

        List<SalesTrendItemVO> trend = adminOrderService.getSalesTrend(7);

        assertEquals(7, trend.size());
        assertTrue(trend.stream().anyMatch(t -> "2026-09-15".equals(t.getDate()) && t.getOrderCount() == 5));
    }

    @Test
    void testGetTopProducts() {
        Map<String, Object> r1 = Map.of(
                "product_id", 1000001L,
                "product_name", "Kangaroo Magnet",
                "product_image", "/img/kangaroo.jpg",
                "total_quantity", 42,
                "total_revenue", 840.00
        );
        when(orderItemMapper.selectTopSellingProducts(10)).thenReturn(List.of(r1));

        List<TopProductVO> tops = adminOrderService.getTopProducts(10);

        assertEquals(1, tops.size());
        assertEquals(1000001L, tops.get(0).getProductId());
        assertEquals("Kangaroo Magnet", tops.get(0).getProductName());
        assertEquals(42, tops.get(0).getTotalQuantity());
    }

    @Test
    void testUpdateOrderStatus_Shipped() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNo("SO20260916001");
        order.setStatus("processing");
        when(orderMapper.selectOne(any())).thenReturn(order);

        AdminOrderStatusUpdateRequest req = new AdminOrderStatusUpdateRequest();
        req.setStatus("shipped");
        req.setTrackingNo("AUSPOST_998877");
        req.setRemark("Dispatched via express");

        adminOrderService.updateOrderStatus("SO20260916001", req, 1L);

        verify(orderStateMachine).transition(order, OrderStatus.SHIPPED, 1L, "admin");
        verify(orderMapper).updateById(order);
        assertTrue(order.getRemark().contains("Tracking: AUSPOST_998877"));
    }

    @Test
    void testUpdateOrderStatus_CancelledRollback() {
        Order order = new Order();
        order.setId(11L);
        order.setOrderNo("SO20260916002");
        order.setStatus("paid");
        when(orderMapper.selectOne(any())).thenReturn(order);

        AdminOrderStatusUpdateRequest req = new AdminOrderStatusUpdateRequest();
        req.setStatus("cancelled");
        req.setRemark("Customer requested full refund");

        adminOrderService.updateOrderStatus("SO20260916002", req, 1L);

        verify(productClient).rollbackStock("SO20260916002");
        verify(orderStateMachine).transition(order, OrderStatus.CANCELLED, 1L, "admin");
        verify(orderMapper).updateById(order);
    }
}
