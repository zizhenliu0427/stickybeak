package au.com.stickybeak.payment.provider;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.payment.config.StripeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class StripePaymentProviderTest {

    private StripeConfig stripeConfig;
    private StripePaymentProvider stripePaymentProvider;
    private MockPaymentProvider mockPaymentProvider;
    private PaymentProviderFactory factory;

    @BeforeEach
    void setUp() {
        stripeConfig = new StripeConfig();
        stripeConfig.setEnabled(false);
        stripeConfig.setApiKey("mock-key");
        stripeConfig.setWebhookSecret("mock-secret");

        stripePaymentProvider = new StripePaymentProvider(stripeConfig);
        mockPaymentProvider = new MockPaymentProvider(new ObjectMapper());
        factory = new PaymentProviderFactory(stripePaymentProvider, mockPaymentProvider, stripeConfig);
    }

    @Test
    void testFactoryFallbackToMockWhenDisabledOrMockKey() {
        PaymentProvider provider = factory.getProvider();
        assertEquals("mock", provider.getProviderName());
        assertTrue(provider instanceof MockPaymentProvider);
    }

    @Test
    void testFactoryUsesStripeWhenEnabledAndRealKey() {
        stripeConfig.setEnabled(true);
        stripeConfig.setApiKey("sk_test_1234567890abcdef");

        PaymentProvider provider = factory.getProvider();
        assertEquals("stripe", provider.getProviderName());
        assertTrue(provider instanceof StripePaymentProvider);
    }

    @Test
    void testMockPaymentProviderCreateSession() {
        CreateSessionCommand cmd = new CreateSessionCommand();
        cmd.setOrderNo("SO202609160001");
        cmd.setAmount(new BigDecimal("99.50"));
        cmd.setCurrency("AUD");
        cmd.setPaymentMethod("card");

        CreateSessionResult result = mockPaymentProvider.createSession(cmd);
        assertNotNull(result);
        assertNotNull(result.getSessionId());
        assertTrue(result.getSessionId().startsWith("mock_cs_"));
        assertTrue(result.getCheckoutUrl().contains("SO202609160001"));
        assertEquals("mock", result.getProvider());
    }

    @Test
    void testMockPaymentProviderVerifyWebhook() {
        String payload = "{\"id\":\"evt_mock_123\",\"type\":\"checkout.session.completed\"}";
        String eventId = mockPaymentProvider.verifyWebhook(payload, "sig_valid_test");
        assertEquals("evt_mock_123", eventId);

        // Invalid signature
        assertThrows(BusinessException.class, () ->
                mockPaymentProvider.verifyWebhook(payload, "invalid_signature")
        );
    }

    @Test
    void testStripePaymentProviderRejectsInvalidSignature() {
        assertThrows(BusinessException.class, () ->
                stripePaymentProvider.verifyWebhook("{}", "bad_sig")
        );
    }
}
