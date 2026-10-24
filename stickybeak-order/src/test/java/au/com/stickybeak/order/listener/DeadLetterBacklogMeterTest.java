package au.com.stickybeak.order.listener;

import au.com.stickybeak.order.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeadLetterBacklogMeterTest {

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final DeadLetterBacklogMeter meter = new DeadLetterBacklogMeter(amqpAdmin);

    @Test
    void backlogSumsEveryDeadLetterQueue() {
        when(amqpAdmin.getQueueInfo(RabbitMQConfig.ORDER_STATUS_DLQ))
                .thenReturn(new QueueInformation(RabbitMQConfig.ORDER_STATUS_DLQ, 2, 0));
        when(amqpAdmin.getQueueInfo(RabbitMQConfig.ORDER_CLOSE_DLQ))
                .thenReturn(new QueueInformation(RabbitMQConfig.ORDER_CLOSE_DLQ, 0, 0));
        when(amqpAdmin.getQueueInfo(RabbitMQConfig.ORDER_REFUND_DLQ))
                .thenReturn(new QueueInformation(RabbitMQConfig.ORDER_REFUND_DLQ, 3, 0));

        assertEquals(5.0, meter.backlog());
    }

    @Test
    void missingQueueCountsAsEmpty() {
        when(amqpAdmin.getQueueInfo(anyString())).thenReturn(null);

        assertEquals(0.0, meter.backlog());
    }

    @Test
    void brokerUnavailableReportsMinusOneInsteadOfThrowing() {
        when(amqpAdmin.getQueueInfo(anyString())).thenThrow(new AmqpConnectException(new RuntimeException("down")));

        assertEquals(-1.0, meter.backlog());
    }

    @Test
    void registeringWithoutAgentIsHarmless() {
        assertDoesNotThrow(meter::register);
    }
}
