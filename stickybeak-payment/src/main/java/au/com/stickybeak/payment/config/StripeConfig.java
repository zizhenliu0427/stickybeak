package au.com.stickybeak.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConfigurationProperties(prefix = "stripe")
public class StripeConfig {

    private boolean enabled = true;
    private String apiKey = "sk_test_mock";
    private String webhookSecret = "whsec_mock";
    private String storefrontUrl = "http://localhost:5173";
    private String successUrl;
    private String cancelUrl;
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
        return StringUtils.hasText(successUrl) ? successUrl : storefrontBase()
                + "/checkout/success?session_id={CHECKOUT_SESSION_ID}&order_no={ORDER_NO}";
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return StringUtils.hasText(cancelUrl) ? cancelUrl : storefrontBase()
                + "/checkout/cancel?order_no={ORDER_NO}";
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public String getStorefrontUrl() {
        return storefrontUrl;
    }

    public void setStorefrontUrl(String storefrontUrl) {
        this.storefrontUrl = storefrontUrl;
    }

    private String storefrontBase() {
        return (StringUtils.hasText(storefrontUrl) ? storefrontUrl.trim() : "http://localhost:5173")
                .replaceAll("/+$", "");
    }
}
