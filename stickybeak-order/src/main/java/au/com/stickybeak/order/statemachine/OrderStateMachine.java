package au.com.stickybeak.order.statemachine;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderStatusHistory;
import au.com.stickybeak.order.mapper.OrderStatusHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachine.class);
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        VALID_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.PAID, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED));
        VALID_TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderStatusHistoryMapper orderStatusHistoryMapper;

    public OrderStateMachine(OrderStatusHistoryMapper orderStatusHistoryMapper) {
        this.orderStatusHistoryMapper = orderStatusHistoryMapper;
    }

    public boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<OrderStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public void transition(Order order, OrderStatus targetStatus, Long operatorId, String operatorRole) {
        if (order == null || targetStatus == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order or target status cannot be null");
        }

        OrderStatus currentStatus = OrderStatus.fromCode(order.getStatus());
        if (currentStatus == null) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "Unknown current order status: " + order.getStatus());
        }

        if (!canTransition(currentStatus, targetStatus)) {
            log.warn("Illegal order state transition attempted for order {}: {} -> {}", order.getOrderNo(), currentStatus.getCode(), targetStatus.getCode());
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION,
                    "Illegal state transition from " + currentStatus.getCode() + " to " + targetStatus.getCode());
        }

        order.setStatus(targetStatus.getCode());

        LocalDateTime now = LocalDateTime.now();
        if (targetStatus == OrderStatus.PAID && order.getPayTime() == null) {
            order.setPayTime(now);
        } else if (targetStatus == OrderStatus.SHIPPED && order.getShipTime() == null) {
            order.setShipTime(now);
        } else if (targetStatus == OrderStatus.COMPLETED && order.getCompleteTime() == null) {
            order.setCompleteTime(now);
        }

        // Record audit history
        OrderStatusHistory history = new OrderStatusHistory(
                order.getId(),
                currentStatus.getCode(),
                targetStatus.getCode(),
                operatorId,
                operatorRole
        );
        orderStatusHistoryMapper.insert(history);
        log.info("Order {} transitioned from {} to {} by {} ({})", order.getOrderNo(), currentStatus.getCode(), targetStatus.getCode(), operatorRole, operatorId);
    }
}
