package au.com.stickybeak.order.service;

import au.com.stickybeak.order.client.PaymentClient;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.config.RabbitMQConfig;
import au.com.stickybeak.order.listener.MessageRetrySupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderResourceReleaserTest {

    private static final String ORDER_NO = "SO20261025001";

    private PaymentClient paymentClient;
    private ProductClient productClient;
    private RabbitTemplate rabbitTemplate;
    private OrderResourceReleaser releaser;

    @BeforeEach
    void setUp() {
        paymentClient = mock(PaymentClient.class);
        productClient = mock(ProductClient.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        releaser = new OrderResourceReleaser(paymentClient, productClient, rabbitTemplate);
    }

    @Test
    void releaseExpiresSessionThenReturnsStock() {
        releaser.release(ORDER_NO);

        verify(paymentClient).expireSession(ORDER_NO);
        verify(productClient).rollbackStock(ORDER_NO);
    }

    @Test
    void releaseStillReturnsStockWhenSessionExpiryFails() {
        doThrow(new RuntimeException("payment down")).when(paymentClient).expireSession(ORDER_NO);

        releaser.release(ORDER_NO);

        verify(productClient).rollbackStock(ORDER_NO);
    }

    @Test
    void releaseThrowsWhenStockCannotBeReturned() {
        doThrow(new RuntimeException("global lock wait timeout")).when(productClient).rollbackStock(ORDER_NO);

        assertThrows(RuntimeException.class, () -> releaser.release(ORDER_NO));
    }

    @Test
    void quietReleaseDoesNotRetryWhenStockIsReturned() {
        releaser.releaseQuietly(ORDER_NO);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void quietReleaseSchedulesAQuickRetryWhenStockCannotBeReturned() {
        doThrow(new RuntimeException("global lock wait timeout")).when(productClient).rollbackStock(ORDER_NO);

        releaser.releaseQuietly(ORDER_NO);

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(""), eq(RabbitMQConfig.ORDER_CLOSE_RETRY_QUEUE), eq(ORDER_NO), processor.capture());
        Message message = processor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertEquals(1, (Integer) message.getMessageProperties().getHeader(MessageRetrySupport.RETRY_COUNT_HEADER));
        assertEquals("global lock wait timeout",
                message.getMessageProperties().getHeader(MessageRetrySupport.FAILURE_REASON_HEADER));
    }

    @Test
    void quietReleaseSwallowsBrokerFailureAndLeavesItToTheTimeoutMessage() {
        doThrow(new RuntimeException("global lock wait timeout")).when(productClient).rollbackStock(ORDER_NO);
        doThrow(new AmqpConnectException(new RuntimeException("broker down")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));

        assertDoesNotThrow(() -> releaser.releaseQuietly(ORDER_NO));
    }
}
