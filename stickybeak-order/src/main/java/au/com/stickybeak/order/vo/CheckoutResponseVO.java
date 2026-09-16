package au.com.stickybeak.order.vo;

import java.io.Serializable;
import java.math.BigDecimal;

public class CheckoutResponseVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private String currency;
    private String checkoutUrl;
    private String sessionId;

    public CheckoutResponseVO() {
    }

    public CheckoutResponseVO(String orderNo, BigDecimal totalAmount, BigDecimal payAmount, String currency, String checkoutUrl, String sessionId) {
        this.orderNo = orderNo;
        this.totalAmount = totalAmount;
        this.payAmount = payAmount;
        this.currency = currency;
        this.checkoutUrl = checkoutUrl;
        this.sessionId = sessionId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
