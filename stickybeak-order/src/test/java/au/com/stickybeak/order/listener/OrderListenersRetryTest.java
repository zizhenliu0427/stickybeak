package au.com.stickybeak.order.listener;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.common.event.OrderRefundedEvent;
import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.service.OrderService;
import au.com.stickybeak.order.service.OrderService.PaidOutcome;
import au.com.stickybeak.order.service.OrderService.RefundOutcome;
import au.com.stickybeak.order.service.OrderService.TimeoutOutcome;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderListenersRetryTest {

    private static final long TAG = 42L;

    private OrderService orderService;
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private OrderPaidListener paidListener;
    private OrderTimeoutCloseListener timeoutListener;
    private OrderRefundedListener refundedListener;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        MessageRetrySupport retrySupport = new MessageRetrySupport(rabbitTemplate, 3);
        paidListener = new OrderPaidListener(orderService, retrySupport);
        timeoutListener = new OrderTimeoutCloseListener(orderService, retrySupport);
        refundedListener = new OrderRefundedListener(orderService, retrySupport);
    }

    private static Message message(Integer retryCount, boolean redelivered) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        props.setRedelivered(redelivered);
        if (retryCount != null) {
            props.setHeader(MessageRetrySupport.RETRY_COUNT_HEADER, retryCount);
        }
        props.setHeader("x-death", "broker-managed");
        return new Message("{}".getBytes(), props);
    }

    private static OrderPaidEvent event() {
        OrderPaidEvent event = new OrderPaidEvent();
        event.setOrderNo("SO1");
        return event;
    }

    private Message forwardedTo(String queue) {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq(queue), captor.capture());
        return captor.getValue();
    }

    @Test
    void paidNowCompletesAndAcks() throws Exception {
        when(orderService.markPaid(any())).thenReturn(PaidOutcome.PAID_NOW);

        paidListener.onOrderPaid(event(), message(null, false), channel);

        verify(orderService).completePaidOrder("SO1");
        verify(channel).basicAck(TAG, false);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void sideEffectFailureGoesToRetryQueueWithoutLosingPaidStatus() throws Exception {
        when(orderService.markPaid(any())).thenReturn(PaidOutcome.PAID_NOW);
        doThrow(new RuntimeException("product service down")).when(orderService).completePaidOrder("SO1");

        paidListener.onOrderPaid(event(), message(null, false), channel);

        Message retried = forwardedTo(RabbitMQConfig.ORDER_STATUS_RETRY_QUEUE);
        assertEquals(1, retried.getMessageProperties().<Integer>getHeader(MessageRetrySupport.RETRY_COUNT_HEADER));
        assertTrue(retried.getMessageProperties().<String>getHeader(MessageRetrySupport.FAILURE_REASON_HEADER)
                .contains("product service down"));
        assertTrue(retried.getMessageProperties().getHeaders().keySet().stream().noneMatch(k -> k.startsWith("x-death")));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void retriedAlreadyPaidMessageRedoesSideEffects() throws Exception {
        when(orderService.markPaid(any())).thenReturn(PaidOutcome.ALREADY_PAID);

        paidListener.onOrderPaid(event(), message(1, false), channel);

        verify(orderService).completePaidOrder("SO1");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void duplicateAlreadyPaidMessageDoesNotClearCartAgain() throws Exception {
        when(orderService.markPaid(any())).thenReturn(PaidOutcome.ALREADY_PAID);

        paidListener.onOrderPaid(event(), message(null, false), channel);

        verify(orderService, never()).completePaidOrder(anyString());
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void paidAfterCancelIsRefundedAutomatically() throws Exception {
        when(orderService.markPaid(any())).thenReturn(PaidOutcome.NOT_PAYABLE);

        paidListener.onOrderPaid(event(), message(null, false), channel);

        verify(orderService).refundPaidAfterCancel("SO1");
        verify(orderService, never()).completePaidOrder(anyString());
        verify(channel).basicAck(TAG, false);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void paidAfterCancelRefundFailureIsRetried() throws Exception {
        when(orderService.markPaid(any())).thenReturn(PaidOutcome.NOT_PAYABLE);
        doThrow(new RuntimeException("payment down")).when(orderService).refundPaidAfterCancel("SO1");

        paidListener.onOrderPaid(event(), message(null, false), channel);

        forwardedTo(RabbitMQConfig.ORDER_STATUS_RETRY_QUEUE);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void exhaustedRetriesGoToDlq() throws Exception {
        when(orderService.markPaid(any())).thenThrow(new RuntimeException("db down"));

        paidListener.onOrderPaid(event(), message(3, false), channel);

        forwardedTo(RabbitMQConfig.ORDER_STATUS_DLQ);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void brokerUnavailableRequeuesOriginal() throws Exception {
        when(orderService.markPaid(any())).thenThrow(new RuntimeException("db down"));
        doThrow(new AmqpConnectException(new RuntimeException("broker down")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        paidListener.onOrderPaid(event(), message(null, false), channel);

        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void timeoutClosesAndReleases() throws Exception {
        when(orderService.closeIfUnpaid("SO1")).thenReturn(TimeoutOutcome.CLOSED_NOW);

        timeoutListener.onOrderTimeout("SO1", message(null, false), channel);

        verify(orderService).releaseOrderResources("SO1");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void timeoutReleasesAgainForUserCancelledOrder() throws Exception {
        when(orderService.closeIfUnpaid("SO1")).thenReturn(TimeoutOutcome.ALREADY_CLOSED);

        timeoutListener.onOrderTimeout("SO1", message(null, false), channel);

        verify(orderService).releaseOrderResources("SO1");
    }

    @Test
    void timeoutSkipsPaidOrder() throws Exception {
        when(orderService.closeIfUnpaid("SO1")).thenReturn(TimeoutOutcome.NOT_PENDING);

        timeoutListener.onOrderTimeout("SO1", message(null, false), channel);

        verify(orderService, never()).releaseOrderResources(anyString());
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void timeoutReleaseFailureIsRetried() throws Exception {
        when(orderService.closeIfUnpaid("SO1")).thenReturn(TimeoutOutcome.CLOSED_NOW);
        doThrow(new RuntimeException("product down")).when(orderService).releaseOrderResources("SO1");

        timeoutListener.onOrderTimeout("SO1", message(null, false), channel);

        forwardedTo(RabbitMQConfig.ORDER_CLOSE_RETRY_QUEUE);
        verify(channel).basicAck(TAG, false);
    }

    private static OrderRefundedEvent refundEvent() {
        OrderRefundedEvent event = new OrderRefundedEvent();
        event.setOrderNo("SO1");
        return event;
    }

    @Test
    void refundMarksOrderAndRestocks() throws Exception {
        when(orderService.markRefunded("SO1")).thenReturn(RefundOutcome.REFUNDED_NOW);

        refundedListener.onOrderRefunded(refundEvent(), message(null, false), channel);

        verify(orderService).restockRefundedOrder("SO1");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void duplicateRefundEventRestocksIdempotently() throws Exception {
        when(orderService.markRefunded("SO1")).thenReturn(RefundOutcome.ALREADY_REFUNDED);

        refundedListener.onOrderRefunded(refundEvent(), message(null, false), channel);

        verify(orderService).restockRefundedOrder("SO1");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void refundOfCancelledOrderDoesNotRestock() throws Exception {
        // 关单后到账被自动退回：库存在关单时已释放
        when(orderService.markRefunded("SO1")).thenReturn(RefundOutcome.NOT_REFUNDING);

        refundedListener.onOrderRefunded(refundEvent(), message(null, false), channel);

        verify(orderService, never()).restockRefundedOrder(anyString());
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void restockFailureIsRetried() throws Exception {
        when(orderService.markRefunded("SO1")).thenReturn(RefundOutcome.REFUNDED_NOW);
        doThrow(new RuntimeException("product down")).when(orderService).restockRefundedOrder("SO1");

        refundedListener.onOrderRefunded(refundEvent(), message(null, false), channel);

        forwardedTo(RabbitMQConfig.ORDER_REFUND_RETRY_QUEUE);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void refundForUnknownOrderIsRetried() throws Exception {
        when(orderService.markRefunded("SO1")).thenReturn(RefundOutcome.NOT_FOUND);

        refundedListener.onOrderRefunded(refundEvent(), message(null, false), channel);

        forwardedTo(RabbitMQConfig.ORDER_REFUND_RETRY_QUEUE);
    }
}
