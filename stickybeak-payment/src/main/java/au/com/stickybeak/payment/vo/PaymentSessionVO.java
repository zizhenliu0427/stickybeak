package au.com.stickybeak.payment.vo;

import java.io.Serializable;

public class PaymentSessionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String sessionId;
    private String checkoutUrl;
    private String provider;

    public PaymentSessionVO() {
    }

    public PaymentSessionVO(String orderNo, String sessionId, String checkoutUrl, String provider) {
        this.orderNo = orderNo;
        this.sessionId = sessionId;
        this.checkoutUrl = checkoutUrl;
        this.provider = provider;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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
