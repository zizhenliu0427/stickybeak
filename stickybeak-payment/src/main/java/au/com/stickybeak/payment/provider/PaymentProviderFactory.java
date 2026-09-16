package au.com.stickybeak.payment.provider;

import au.com.stickybeak.payment.config.StripeConfig;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderFactory {

    private final StripePaymentProvider stripePaymentProvider;
    private final MockPaymentProvider mockPaymentProvider;
    private final StripeConfig stripeConfig;

    public PaymentProviderFactory(StripePaymentProvider stripePaymentProvider,
                                  MockPaymentProvider mockPaymentProvider,
                                  StripeConfig stripeConfig) {
        this.stripePaymentProvider = stripePaymentProvider;
        this.mockPaymentProvider = mockPaymentProvider;
        this.stripeConfig = stripeConfig;
    }

    public PaymentProvider getProvider() {
        if (stripeConfig.isEnabled()
                && stripeConfig.getApiKey() != null
                && !stripeConfig.getApiKey().contains("mock")) {
            return stripePaymentProvider;
        }
        return mockPaymentProvider;
    }

    public PaymentProvider getProviderByName(String name) {
        if ("stripe".equalsIgnoreCase(name)) {
            return stripePaymentProvider;
        }
        return mockPaymentProvider;
    }
}
