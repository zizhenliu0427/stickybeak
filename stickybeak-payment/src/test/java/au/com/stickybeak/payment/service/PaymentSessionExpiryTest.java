package au.com.stickybeak.payment.service;

import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.provider.PaymentProvider;
import au.com.stickybeak.payment.provider.PaymentProviderFactory;
import au.com.stickybeak.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
