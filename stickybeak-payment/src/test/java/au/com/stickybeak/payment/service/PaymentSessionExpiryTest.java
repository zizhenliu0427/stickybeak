package au.com.stickybeak.payment.service;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.payment.dto.CreatePaymentSessionRequest;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.provider.CreateSessionCommand;
import au.com.stickybeak.payment.provider.CreateSessionResult;
import au.com.stickybeak.payment.provider.PaymentProvider;
import au.com.stickybeak.payment.provider.PaymentProviderFactory;
import au.com.stickybeak.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSessionExpiryTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private PaymentProviderFactory paymentProviderFactory;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private PaymentProvider stripeProvider;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRecordMapper, paymentProviderFactory, exchangeRateService);
    }

    @Test
    void expiresPendingSessionAndMarksRecord() {
        PaymentRecord record = record("pending", "cs_test_open");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);
        when(paymentProviderFactory.getProviderByName("stripe")).thenReturn(stripeProvider);

        paymentService.expireSession("SO1");

        verify(stripeProvider).expireSession("cs_test_open");
        assertEquals("expired", record.getStatus());
        verify(paymentRecordMapper).updateById(record);
    }

    @Test
    void skipsWhenAlreadyPaid() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("succeeded", "cs_test_done"));

        paymentService.expireSession("SO1");

        verify(stripeProvider, never()).expireSession(anyString());
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
    }

    @Test
    void skipsWhenNoRecord() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        paymentService.expireSession("SO_NONE");

        verifyNoInteractions(paymentProviderFactory);
    }

    @Test
    void reissuingPaymentExpiresTheOldSessionAndKeepsThePaymentMethod() {
        PaymentRecord record = record("pending", "cs_test_old");
        record.setPaymentMethod("alipay");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);
        when(paymentProviderFactory.getProviderByName("stripe")).thenReturn(stripeProvider);
        when(paymentProviderFactory.getProvider()).thenReturn(stripeProvider);
        when(stripeProvider.createSession(any())).thenReturn(new CreateSessionResult("cs_test_new", "https://pay/new", "stripe"));

        paymentService.createCheckoutSession(resumeRequest());

        var order = inOrder(stripeProvider);
        order.verify(stripeProvider).expireSession("cs_test_old");
        ArgumentCaptor<CreateSessionCommand> cmd = ArgumentCaptor.forClass(CreateSessionCommand.class);
        order.verify(stripeProvider).createSession(cmd.capture());
        assertEquals("alipay", cmd.getValue().getPaymentMethod());
        assertEquals("cs_test_new", record.getSessionId());
        verify(paymentRecordMapper).updateById(record);
    }

    @Test
    void doesNotIssueANewLinkWhileTheOldOneCannotBeClosed() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(record("pending", "cs_test_old"));
        when(paymentProviderFactory.getProviderByName("stripe")).thenReturn(stripeProvider);
        doThrow(new RuntimeException("stripe unavailable")).when(stripeProvider).expireSession("cs_test_old");

        assertThrows(BusinessException.class, () -> paymentService.createCheckoutSession(resumeRequest()));

        verify(stripeProvider, never()).createSession(any());
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
    }

    private static CreatePaymentSessionRequest resumeRequest() {
        CreatePaymentSessionRequest req = new CreatePaymentSessionRequest();
        req.setOrderNo("SO1");
        req.setUserId(7L);
        req.setAmount(new BigDecimal("19.90"));
        req.setCurrency("AUD");
        req.setPaymentMethod(null);
        return req;
    }

    private static PaymentRecord record(String status, String sessionId) {
        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setOrderNo("SO1");
        record.setProvider("stripe");
        record.setSessionId(sessionId);
        record.setStatus(status);
        return record;
    }
}
