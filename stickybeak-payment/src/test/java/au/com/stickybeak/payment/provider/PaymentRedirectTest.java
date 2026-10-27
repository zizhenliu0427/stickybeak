package au.com.stickybeak.payment.provider;

import au.com.stickybeak.payment.config.StripeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class PaymentRedirectTest {
    private CreateSessionCommand command() {
        var command = new CreateSessionCommand();
        command.setOrderNo("SO202610270001");
        command.setAmount(new BigDecimal("12.90"));
        command.setCurrency("AUD");
        return command;
    }

    @Test
    void localDevelopmentKeepsViteDefault() {
        var config = new StripeConfig();
        assertTrue(config.getSuccessUrl().startsWith("http://localhost:5173/checkout/success?"));
        assertEquals("http://localhost:5173/checkout/cancel?order_no={ORDER_NO}", config.getCancelUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "http://localhost/", "https://shop.example.com/"})
    void mockUsesStorefrontAndReplacesBothPlaceholders(String storefront) {
        var config = new StripeConfig();
        config.setStorefrontUrl(storefront);
        config.setSuccessUrl("");
        var session = new MockPaymentProvider(new ObjectMapper(), config).createSession(command());
        assertEquals(storefront.replaceAll("/+$", "") + "/checkout/success?session_id="
                + session.getSessionId() + "&order_no=SO202610270001", session.getCheckoutUrl());
        assertFalse(session.getCheckoutUrl().contains("{"));
    }

    @Test
    void explicitSuccessAndCancelTemplatesOverrideStorefront() {
        var config = new StripeConfig();
        config.setStorefrontUrl("https://shop.example.com");
        config.setSuccessUrl("https://checkout.example.com/done/{ORDER_NO}?session={CHECKOUT_SESSION_ID}");
        config.setCancelUrl("https://checkout.example.com/back/{ORDER_NO}");
        var session = new MockPaymentProvider(new ObjectMapper(), config).createSession(command());
        assertEquals("https://checkout.example.com/done/SO202610270001?session=" + session.getSessionId(),
                session.getCheckoutUrl());
        assertStripeUrls(config);
    }

    @Test
    void stripeUsesStorefrontButLeavesSessionPlaceholderForStripe() {
        var config = new StripeConfig();
        config.setStorefrontUrl("http://localhost/");
        assertStripeUrls(config);
    }

    private void assertStripeUrls(StripeConfig config) {
        try (var stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.create(any(SessionCreateParams.class))).thenAnswer(invocation -> {
                SessionCreateParams params = invocation.getArgument(0);
                assertEquals(config.getSuccessUrl().replace("{ORDER_NO}", command().getOrderNo()), params.getSuccessUrl());
                assertEquals(config.getCancelUrl().replace("{ORDER_NO}", command().getOrderNo()), params.getCancelUrl());
                assertTrue(params.getSuccessUrl().contains("{CHECKOUT_SESSION_ID}"));
                var session = new Session();
                session.setId("cs_test_redirect");
                session.setUrl("https://checkout.stripe.com/test");
                return session;
            });
            assertEquals("cs_test_redirect", new StripePaymentProvider(config).createSession(command()).getSessionId());
        }
    }
}
