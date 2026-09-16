package au.com.stickybeak.order.service;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.order.dto.CreateOrderRequest;
import au.com.stickybeak.order.vo.CheckoutResponseVO;
import au.com.stickybeak.order.vo.OrderVO;

import java.util.List;

public interface OrderService {

    CheckoutResponseVO checkout(Long userId, CreateOrderRequest req);

    OrderVO getByOrderNo(String orderNo, Long userId);

    List<OrderVO> listUserOrders(Long userId, String status);

    void cancelOrder(String orderNo, Long userId);

    void handleOrderPaid(OrderPaidEvent event);

    void handleOrderTimeout(String orderNo);
}
