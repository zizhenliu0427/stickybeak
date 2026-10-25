package au.com.stickybeak.payment.service;

import au.com.stickybeak.common.event.OrderRefundedEvent;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.provider.MockPaymentProvider;
import au.com.stickybeak.payment.provider.PaymentProvider;
import au.com.stickybeak.payment.provider.PaymentProviderFactory;
import au.com.stickybeak.payment.provider.RefundCommand;
import au.com.stickybeak.payment.provider.RefundResult;
import au.com.stickybeak.payment.service.impl.RefundServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private PaymentProviderFactory paymentProviderFactory;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private PaymentProvider stripeProvider;

    private RefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundServiceImpl(paymentRecordMapper, paymentProviderFactory, rabbitTemplate);
    }

    private static PaymentRecord record(String status) {
        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setOrderNo("SO1");
        record.setProvider("stripe");
        record.setTransactionId("pi_123");
        record.setAmount(new BigDecimal("59.90"));
        record.setCurrency("AUD");
        record.setStatus(status);
        return record;
    }

    @Test
    void synchronousSuccessMarksRefundedAndPublishes() {
        PaymentRecord record = record("succeeded");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);
        when(paymentProviderFactory.getProviderByName("stripe")).thenReturn(stripeProvider);
        when(stripeProvider.refund(any(RefundCommand.class))).thenReturn(new RefundResult("re_1", "succeeded"));

        assertEquals(RefundService.STATUS_REFUNDED, refundService.refund("SO1", "customer request"));

        assertEquals("refunded", record.getStatus());
        ArgumentCaptor<RefundCommand> cmd = ArgumentCaptor.forClass(RefundCommand.class);
        verify(stripeProvider).refund(cmd.capture());
        assertEquals("pi_123", cmd.getValue().getTransactionId());
        ArgumentCaptor<OrderRefundedEvent> event = ArgumentCaptor.forClass(OrderRefundedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(OrderRefundedEvent.EXCHANGE), eq(OrderRefundedEvent.ROUTING_KEY), event.capture());
        assertEquals("SO1", event.getValue().getOrderNo());
        assertEquals("re_1", event.getValue().getRefundId());
    }

    @Test
    void pendingRefundWaitsForWebhook() {
        PaymentRecord record = record("succeeded");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);
        when(paymentProviderFactory.getProviderByName("stripe")).thenReturn(stripeProvider);
        when(stripeProvider.refund(any(RefundCommand.class))).thenReturn(new RefundResult("re_2", "pending"));

        assertEquals(RefundService.STATUS_REFUNDING, refundService.refund("SO1", null));

        assertEquals("refunding", record.getStatus());
        verify(paymentRecordMapper).updateById(record);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void failedRefundThrowsAndKeepsRecord() {
        PaymentRecord record = record("succeeded");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);
        when(paymentProviderFactory.getProviderByName("stripe")).thenReturn(stripeProvider);
        when(stripeProvider.refund(any(RefundCommand.class))).thenReturn(new RefundResult("re_3", "failed"));

        assertThrows(BusinessException.class, () -> refundService.refund("SO1", null));
        assertEquals("succeeded", record.getStatus());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void alreadyRefundedIsIdempotentAndRepublishes() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("refunded"));

        assertEquals(RefundService.STATUS_REFUNDED, refundService.refund("SO1", null));

        verifyNoInteractions(paymentProviderFactory);
        verify(rabbitTemplate).convertAndSend(eq(OrderRefundedEvent.EXCHANGE), eq(OrderRefundedEvent.ROUTING_KEY), any(OrderRefundedEvent.class));
    }

    @Test
    void unpaidOrderCannotBeRefunded() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("pending"));
        assertThrows(BusinessException.class, () -> refundService.refund("SO1", null));

        when(paymentRecordMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> refundService.refund("SO_NONE", null));
        verifyNoInteractions(paymentProviderFactory, rabbitTemplate);
    }

    @Test
    void webhookConfirmationMarksRefundedOnce() {
        PaymentRecord record = record("refunding");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        assertTrue(refundService.confirmRefund("pi_123", "re_2"));
        assertEquals("refunded", record.getStatus());
        verify(rabbitTemplate, times(1)).convertAndSend(eq(OrderRefundedEvent.EXCHANGE), eq(OrderRefundedEvent.ROUTING_KEY), any(OrderRefundedEvent.class));

        // 重复的 webhook 不再发事件
        assertTrue(refundService.confirmRefund("pi_123", "re_2"));
        verify(rabbitTemplate, times(1)).convertAndSend(eq(OrderRefundedEvent.EXCHANGE), eq(OrderRefundedEvent.ROUTING_KEY), any(OrderRefundedEvent.class));
    }

    @Test
    void webhookForUnknownTransactionReturnsFalse() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);
        assertFalse(refundService.confirmRefund("pi_unknown", "re_x"));
        assertFalse(refundService.confirmRefund(null, "re_x"));
    }

    @Test
    void mockProviderRefundsImmediately() {
        RefundResult result = new MockPaymentProvider(new ObjectMapper())
                .refund(new RefundCommand("SO1", "mock_tx", new BigDecimal("10.00"), "AUD", null));
        assertTrue(result.isSucceeded());
        assertTrue(result.getRefundId().startsWith("mock_re_"));
    }
}
