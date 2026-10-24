package au.com.stickybeak.payment.provider;

import java.math.BigDecimal;

public class RefundCommand {

    private final String orderNo;
    /** Stripe 为 payment_intent，Mock 为模拟交易号 */
    private final String transactionId;
    private final BigDecimal amount;
    private final String currency;
    private final String reason;

    public RefundCommand(String orderNo, String transactionId, BigDecimal amount, String currency, String reason) {
        this.orderNo = orderNo;
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }
}
