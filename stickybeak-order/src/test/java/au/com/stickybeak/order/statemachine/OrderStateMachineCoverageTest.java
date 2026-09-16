package au.com.stickybeak.order.statemachine;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderStatusHistory;
import au.com.stickybeak.order.mapper.OrderStatusHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStateMachineCoverageTest {

    @Mock
    private OrderStatusHistoryMapper orderStatusHistoryMapper;

    @InjectMocks
    private OrderStateMachine orderStateMachine;

    @Test
    @DisplayName("canTransition: Null checks and full transition matrix coverage")
    void testCanTransitionNullsAndMatrix() {
        assertFalse(orderStateMachine.canTransition(null, OrderStatus.PAID));
        assertFalse(orderStateMachine.canTransition(OrderStatus.PENDING, null));
        assertFalse(orderStateMachine.canTransition(null, null));

        // Valid transitions
        assertTrue(orderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.PAID));
        assertTrue(orderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
        assertTrue(orderStateMachine.canTransition(OrderStatus.PAID, OrderStatus.PROCESSING));
        assertTrue(orderStateMachine.canTransition(OrderStatus.PAID, OrderStatus.CANCELLED));
        assertTrue(orderStateMachine.canTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED));
        assertTrue(orderStateMachine.canTransition(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        assertTrue(orderStateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.COMPLETED));

        // Invalid transitions
        assertFalse(orderStateMachine.canTransition(OrderStatus.COMPLETED, OrderStatus.PAID));
        assertFalse(orderStateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.PENDING));
        assertFalse(orderStateMachine.canTransition(OrderStatus.PAID, OrderStatus.PENDING));
        assertFalse(orderStateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.PROCESSING));
    }

    @Test
    @DisplayName("getAllowedTransitions: Null, invalid and all status transitions")
    void testGetAllowedTransitions() {
        assertTrue(orderStateMachine.getAllowedTransitions(null).isEmpty());
        assertTrue(orderStateMachine.getAllowedTransitions("non_existent_status").isEmpty());

        List<String> pendingAllowed = orderStateMachine.getAllowedTransitions("pending");
        assertTrue(pendingAllowed.contains("paid"));
        assertTrue(pendingAllowed.contains("cancelled"));

        List<String> paidAllowed = orderStateMachine.getAllowedTransitions("paid");
        assertTrue(paidAllowed.contains("processing"));
        assertTrue(paidAllowed.contains("cancelled"));

        List<String> processingAllowed = orderStateMachine.getAllowedTransitions("processing");
        assertTrue(processingAllowed.contains("shipped"));
        assertTrue(processingAllowed.contains("cancelled"));

        List<String> shippedAllowed = orderStateMachine.getAllowedTransitions("shipped");
        assertEquals(List.of("completed"), shippedAllowed);

        assertTrue(orderStateMachine.getAllowedTransitions("completed").isEmpty());
        assertTrue(orderStateMachine.getAllowedTransitions("cancelled").isEmpty());
    }

    @Test
    @DisplayName("transition: Null validation and unknown status handling")
    void testTransitionNullAndInvalidOrders() {
        Order validOrder = new Order();
        validOrder.setId(10L);
        validOrder.setStatus("pending");

        // Null order
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                orderStateMachine.transition(null, OrderStatus.PAID, 1L, "admin")
        );
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex1.getCode());

        // Null targetStatus
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                orderStateMachine.transition(validOrder, null, 1L, "admin")
        );
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex2.getCode());

        // Unknown current status
        Order invalidStatusOrder = new Order();
        invalidStatusOrder.setId(11L);
        invalidStatusOrder.setStatus("bogus_status");
        BusinessException ex3 = assertThrows(BusinessException.class, () ->
                orderStateMachine.transition(invalidStatusOrder, OrderStatus.PAID, 1L, "admin")
        );
        assertEquals(ResultCode.ILLEGAL_STATE_TRANSITION.getCode(), ex3.getCode());
    }

    @Test
    @DisplayName("transition: Setting shipTime and completeTime timestamps correctly")
    void testTransitionTimestampsAndHistory() {
        // Test transition to SHIPPED
        Order order1 = new Order();
        order1.setId(201L);
        order1.setOrderNo("SO_SHIP_TEST");
        order1.setStatus(OrderStatus.PROCESSING.getCode());
        assertNull(order1.getShipTime());

        orderStateMachine.transition(order1, OrderStatus.SHIPPED, 88L, "admin");

        assertEquals(OrderStatus.SHIPPED.getCode(), order1.getStatus());
        assertNotNull(order1.getShipTime());

        // Verify history capture
        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryMapper, times(1)).insert(historyCaptor.capture());
        OrderStatusHistory history1 = historyCaptor.getValue();
        assertEquals(201L, history1.getOrderId());
        assertEquals("processing", history1.getFromStatus());
        assertEquals("shipped", history1.getToStatus());
        assertEquals(88L, history1.getOperatorId());
        assertEquals("admin", history1.getOperatorRole());

        // Test transition to COMPLETED
        Order order2 = new Order();
        order2.setId(202L);
        order2.setOrderNo("SO_COMPLETE_TEST");
        order2.setStatus(OrderStatus.SHIPPED.getCode());
        assertNull(order2.getCompleteTime());

        orderStateMachine.transition(order2, OrderStatus.COMPLETED, 99L, "system");

        assertEquals(OrderStatus.COMPLETED.getCode(), order2.getStatus());
        assertNotNull(order2.getCompleteTime());
    }

    @Test
    @DisplayName("transition: Does not overwrite existing timestamps")
    void testTransitionExistingTimestampsPreserved() {
        LocalDateTime existingPayTime = LocalDateTime.of(2026, 9, 1, 10, 0);
        Order order = new Order();
        order.setId(301L);
        order.setOrderNo("SO_TIME_PRESERVED");
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setPayTime(existingPayTime);

        orderStateMachine.transition(order, OrderStatus.PAID, 100L, "webhook");

        assertEquals(existingPayTime, order.getPayTime());
    }
}
