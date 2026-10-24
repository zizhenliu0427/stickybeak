package au.com.stickybeak.order.service;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.service.OrderService.TimeoutOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderTimeoutSweeperTest {

    private static Order pending(String orderNo) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setStatus(OrderStatus.PENDING.getCode());
        return order;
    }

    @Test
    void closesStaleOrdersAndKeepsGoingAfterFailures() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderService orderService = mock(OrderService.class);
        when(orderMapper.selectList(any())).thenReturn(List.of(pending("SO_A"), pending("SO_B"), pending("SO_C")));
        when(orderService.closeIfUnpaid("SO_A")).thenReturn(TimeoutOutcome.CLOSED_NOW);
        when(orderService.closeIfUnpaid("SO_B")).thenThrow(new RuntimeException("db hiccup"));
        // 扫描期间刚好被支付，条件判断后不再关单
        when(orderService.closeIfUnpaid("SO_C")).thenReturn(TimeoutOutcome.NOT_PENDING);

        OrderTimeoutSweeper sweeper = new OrderTimeoutSweeper(orderMapper, orderService, 1_800_000L, 300_000L, 100);

        assertEquals(1, sweeper.sweep());
        verify(orderService).releaseOrderResources("SO_A");
        verify(orderService, never()).releaseOrderResources("SO_B");
        verify(orderService, never()).releaseOrderResources("SO_C");
    }
}
