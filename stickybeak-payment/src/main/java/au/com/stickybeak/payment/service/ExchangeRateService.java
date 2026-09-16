package au.com.stickybeak.payment.service;

import au.com.stickybeak.payment.vo.ExchangeRateVO;

import java.math.BigDecimal;

public interface ExchangeRateService {

    ExchangeRateVO getRate(String baseCurrency, String quoteCurrency);

    void updateRate(String baseCurrency, String quoteCurrency, BigDecimal rate);
}
