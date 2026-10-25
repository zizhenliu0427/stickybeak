package au.com.stickybeak.order.service;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.order.client.AuthClient;
import au.com.stickybeak.order.client.CartClient;
import au.com.stickybeak.order.client.PaymentClient;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.mapper.OrderItemMapper;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.mapper.OrderStatusHistoryMapper;
import au.com.stickybeak.order.service.OrderService.PaidOutcome;
import au.com.stickybeak.order.service.OrderService.TimeoutOutcome;
import au.com.stickybeak.order.service.impl.OrderServiceImpl;
import au.com.stickybeak.order.statemachine.OrderStateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "支付成功"与"超时关单"同时到达同一订单：两边都读到 pending，
 * 但条件更新（WHERE status = 'pending'）只允许一个落库，另一个抛出冲突交给重试。
 * 这里用 compareAndSet 模拟数据库行上的条件更新。
 */
class OrderConcurrencyTest {

    @RepeatedTest(20)
    void paidAndTimeoutRaceHasExactlyOneWinner() throws Exception {
        AtomicReference<String> dbStatus = new AtomicReference<>(OrderStatus.PENDING.getCode());
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(orderMapper.selectOne(any())).thenAnswer(inv -> {
            Order order = new Order();
            order.setId(1L);
            order.setOrderNo("SO_RACE");
            order.setUserId(1L);
            order.setStatus(dbStatus.get());
            return order;
        });
        when(orderMapper.updateIfStatus(any(Order.class), anyString())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            String expected = inv.getArgument(1);
            return dbStatus.compareAndSet(expected, order.getStatus()) ? 1 : 0;
        });

        OrderStatusHistoryMapper historyMapper = mock(OrderStatusHistoryMapper.class);
        OrderServiceImpl service = new OrderServiceImpl(orderMapper, mock(OrderItemMapper.class),
                mock(CartClient.class), mock(PaymentClient.class), mock(AuthClient.class), mock(ProductClient.class),
                new OrderStateMachine(historyMapper), mock(OrderResourceReleaser.class), mock(CheckoutTransaction.class),
                mock(RabbitTemplate.class), new ObjectMapper());

        OrderPaidEvent event = new OrderPaidEvent();
        event.setOrderNo("SO_RACE");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> paid = pool.submit(() -> {
                start.await();
                try {
                    return service.markPaid(event);
                } catch (Exception e) {
                    return e;
                }
            });
            Future<Object> timeout = pool.submit(() -> {
                start.await();
                try {
                    return service.closeIfUnpaid("SO_RACE");
                } catch (Exception e) {
                    return e;
                }
            });
            start.countDown();

            Object paidResult = paid.get(5, TimeUnit.SECONDS);
            Object timeoutResult = timeout.get(5, TimeUnit.SECONDS);

            boolean paidWon = paidResult == PaidOutcome.PAID_NOW;
            boolean timeoutWon = timeoutResult == TimeoutOutcome.CLOSED_NOW;
            assertTrue(paidWon ^ timeoutWon, "exactly one transition must win: " + paidResult + " / " + timeoutResult);
            assertEquals(paidWon ? OrderStatus.PAID.getCode() : OrderStatus.CANCELLED.getCode(), dbStatus.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
