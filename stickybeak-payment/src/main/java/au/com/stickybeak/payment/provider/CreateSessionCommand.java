package au.com.stickybeak.payment.provider;

import java.io.Serializable;
import java.math.BigDecimal;

public class CreateSessionCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Long userId;
    private String email;
    private BigDecimal amount;
    private String currency; // AUD or CNY
    private String paymentMethod; // card, alipay, wechat_pay
    private String description;

    public CreateSessionCommand() {
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
