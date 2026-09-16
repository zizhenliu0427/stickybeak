package au.com.stickybeak.payment.provider;

import java.io.Serializable;

public class CreateSessionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String checkoutUrl;
    private String provider;

    public CreateSessionResult() {
    }

    public CreateSessionResult(String sessionId, String checkoutUrl, String provider) {
        this.sessionId = sessionId;
        this.checkoutUrl = checkoutUrl;
        this.provider = provider;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
