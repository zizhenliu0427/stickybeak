package au.com.stickybeak.payment.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExchangeRateVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String baseCurrency;
    private String quoteCurrency;
    private BigDecimal rate;
    private LocalDateTime fetchedAt;

    public ExchangeRateVO() {
    }

    public ExchangeRateVO(String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDateTime fetchedAt) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rate = rate;
        this.fetchedAt = fetchedAt;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
