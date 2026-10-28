package au.com.stickybeak.order.service;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.order.client.CartClient;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderItem;
import au.com.stickybeak.order.mapper.OrderItemMapper;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.mapper.OrderStatusHistoryMapper;
import au.com.stickybeak.order.statemachine.OrderStateMachine;
import io.seata.core.context.RootContext;
import io.seata.tm.api.transaction.TransactionHook;
import io.seata.tm.api.transaction.TransactionHookManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CheckoutTransactionTest {

    private static final String ORDER_NO = "SO202610240001";

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OrderStatusHistoryMapper historyMapper;
    private ProductClient productClient;
    private OrderResourceReleaser releaser;
    private CheckoutTransaction checkoutTransaction;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        historyMapper = mock(OrderStatusHistoryMapper.class);
        productClient = mock(ProductClient.class);
        releaser = mock(OrderResourceReleaser.class);
        checkoutTransaction = new CheckoutTransaction(orderMapper, orderItemMapper, historyMapper,
                productClient, new OrderStateMachine(historyMapper), releaser);
    }

    @AfterEach
    void cleanSeataContext() {
        RootContext.unbind();
        TransactionHookManager.clear();
    }

    private static CheckoutTransaction.PlaceOrderCommand command() {
        CartClient.CartItemData a = new CartClient.CartItemData();
        a.setProductId(2L);
        a.setName("Harbour Bridge Magnet");
        a.setPrice(new BigDecimal("12.00"));
        a.setQty(1);
        CartClient.CartItemData b = new CartClient.CartItemData();
        b.setProductId(1L);
        b.setName("Opera House Magnet");
        b.setPrice(new BigDecimal("19.00"));
        b.setQty(2);
        return new CheckoutTransaction.PlaceOrderCommand(100L, ORDER_NO, List.of(a, b), "{}",
                "AUD", new BigDecimal("50.00"), new BigDecimal("50.00"), BigDecimal.ONE, "leave at door");
    }

    @Test
    @SuppressWarnings("unchecked")
    void placeOrder_deductsStockThenWritesOrderItemsAndHistory() {
        Order order = checkoutTransaction.placeOrder(command());

        ArgumentCaptor<List<ProductClient.StockItemParam>> stock = ArgumentCaptor.forClass(List.class);
        verify(productClient).deductStock(eq(ORDER_NO), stock.capture());
        assertEquals(List.of(2L, 1L), stock.getValue().stream().map(ProductClient.StockItemParam::getProductId).toList());
        assertEquals(OrderStatus.PENDING.getCode(), order.getStatus());
        assertEquals("leave at door", order.getRemark());
        verify(orderMapper).insert(order);
        verify(orderItemMapper, times(2)).insert(any(OrderItem.class));
        verify(historyMapper).insert(any(au.com.stickybeak.order.entity.OrderStatusHistory.class));
        // 不在全局事务里（单测 / Seata 关闭）时不注册回滚 hook
        assertTrue(TransactionHookManager.getHooks().isEmpty());
    }

    @Test
    void placeOrder_whenStockRejected_writesNothing() {
        doThrow(new BusinessException(ResultCode.INSUFFICIENT_STOCK, "sold out"))
                .when(productClient).deductStock(anyString(), anyList());

        assertThrows(BusinessException.class, () -> checkoutTransaction.placeOrder(command()));

        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void placeOrder_inGlobalTransaction_restoresRedisAfterRollback() {
        RootContext.bind("192.168.1.10:8091:2612345678");

        checkoutTransaction.placeOrder(command());

        List<TransactionHook> hooks = TransactionHookManager.getHooks();
        assertEquals(1, hooks.size());
        verify(productClient, never()).restoreRedisReservation(anyString());

        hooks.get(0).afterCommit();
        verify(productClient, never()).restoreRedisReservation(anyString());

        hooks.get(0).afterRollback();
        verify(productClient).restoreRedisReservation(ORDER_NO);
    }

    @Test
    void placeOrder_hookIsRegisteredBeforeDeductSoATimedOutDeductIsStillCompensated() {
        RootContext.bind("192.168.1.10:8091:2612345679");
        doThrow(new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Stock deduction service unavailable: Read timed out"))
                .when(productClient).deductStock(anyString(), anyList());

        assertThrows(BusinessException.class, () -> checkoutTransaction.placeOrder(command()));

        assertEquals(1, TransactionHookManager.getHooks().size());
    }

    @Test
    void redisRestoreFailureDoesNotEscapeTheHook() {
        RootContext.bind("192.168.1.10:8091:2612345680");
        doThrow(new BusinessException(ResultCode.SYSTEM_ERROR, "product down"))
                .when(productClient).restoreRedisReservation(ORDER_NO);
        checkoutTransaction.placeOrder(command());

        assertDoesNotThrow(() -> TransactionHookManager.getHooks().get(0).afterRollback());
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(9L);
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatus.PENDING.getCode());
        return order;
    }

    @Test
    void cancelUnpayable_closesPendingOrderAndReleasesResources() {
        Order order = pendingOrder();
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateIfStatus(order, OrderStatus.PENDING.getCode())).thenReturn(1);

        assertTrue(checkoutTransaction.cancelUnpayable(ORDER_NO));

        assertEquals(OrderStatus.CANCELLED.getCode(), order.getStatus());
        verify(releaser).releaseQuietly(ORDER_NO);
    }

    @Test
    void cancelUnpayable_skipsOrdersThatAreNoLongerPending() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID.getCode());
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertFalse(checkoutTransaction.cancelUnpayable(ORDER_NO));
        when(orderMapper.selectOne(any())).thenReturn(null);
        assertFalse(checkoutTransaction.cancelUnpayable(ORDER_NO));

        verify(orderMapper, never()).updateIfStatus(any(Order.class), anyString());
        verify(releaser, never()).releaseQuietly(anyString());
    }

    @Test
    void cancelUnpayable_concurrentChangeRollsBack() {
        Order order = pendingOrder();
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateIfStatus(order, OrderStatus.PENDING.getCode())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> checkoutTransaction.cancelUnpayable(ORDER_NO));

        verify(releaser, never()).releaseQuietly(anyString());
    }
}
