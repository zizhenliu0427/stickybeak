package au.com.stickybeak.order.statemachine;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderStatusHistory;
import au.com.stickybeak.order.mapper.OrderStatusHistoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStateMachineTest {

    @Mock
    private OrderStatusHistoryMapper orderStatusHistoryMapper;

    @InjectMocks
    private OrderStateMachine orderStateMachine;

    @Test
    void canTransition_validPaths() {
        assertTrue(orderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.PAID));
        assertTrue(orderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
        assertTrue(orderStateMachine.canTransition(OrderStatus.PAID, OrderStatus.PROCESSING));
        assertTrue(orderStateMachine.canTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED));
        assertTrue(orderStateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.COMPLETED));
    }

    @Test
    void canTransition_invalidPaths() {
        assertFalse(orderStateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.PAID));
        assertFalse(orderStateMachine.canTransition(OrderStatus.COMPLETED, OrderStatus.PENDING));
        assertFalse(orderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.COMPLETED));
        assertFalse(orderStateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.CANCELLED));
    }

    @Test
    void transition_legalTransition_updatesStatusAndHistory() {
        Order order = new Order();
        order.setId(101L);
        order.setOrderNo("SO20260916TEST");
        order.setStatus(OrderStatus.PENDING.getCode());

        orderStateMachine.transition(order, OrderStatus.PAID, 1000L, "payment_webhook");

        assertEquals(OrderStatus.PAID.getCode(), order.getStatus());
        assertNotNull(order.getPayTime());
        verify(orderStatusHistoryMapper, times(1)).insert(any(OrderStatusHistory.class));
    }

    @Test
    void transition_illegalTransition_throwsException() {
        Order order = new Order();
        order.setId(102L);
        order.setOrderNo("SO20260916TEST2");
        order.setStatus(OrderStatus.CANCELLED.getCode());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderStateMachine.transition(order, OrderStatus.PAID, 1000L, "user")
        );

        assertEquals(ResultCode.ILLEGAL_STATE_TRANSITION.getCode(), ex.getCode());
        verify(orderStatusHistoryMapper, never()).insert(any(OrderStatusHistory.class));
    }
}
