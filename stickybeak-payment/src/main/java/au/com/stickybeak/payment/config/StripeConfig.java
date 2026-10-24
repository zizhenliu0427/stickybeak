package au.com.stickybeak.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "stripe")
public class StripeConfig {

    private boolean enabled = true;
    private String apiKey = "sk_test_mock";
    private String webhookSecret = "whsec_mock";
    private String successUrl = "http://localhost:5173/checkout/success?session_id={CHECKOUT_SESSION_ID}&order_no={ORDER_NO}";
    private String cancelUrl = "http://localhost:5173/checkout/cancel?order_no={ORDER_NO}";
    /** Checkout Session 有效期（分钟），Stripe 要求 30–1440；与订单 30 分钟超时对齐 */
    private long sessionExpiryMinutes = 31;

    public StripeConfig() {
    }

    public long getSessionExpiryMinutes() {
        return sessionExpiryMinutes;
    }

    public void setSessionExpiryMinutes(long sessionExpiryMinutes) {
        this.sessionExpiryMinutes = sessionExpiryMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
