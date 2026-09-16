package au.com.stickybeak.payment.service.impl;

import au.com.stickybeak.payment.entity.ExchangeRate;
import au.com.stickybeak.payment.mapper.ExchangeRateMapper;
import au.com.stickybeak.payment.service.ExchangeRateService;
import au.com.stickybeak.payment.vo.ExchangeRateVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateServiceImpl.class);
    private static final String RATE_CACHE_PREFIX = "stickybeak:rate:";

    private final ExchangeRateMapper exchangeRateMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public ExchangeRateServiceImpl(ExchangeRateMapper exchangeRateMapper, RedisTemplate<String, Object> redisTemplate) {
        this.exchangeRateMapper = exchangeRateMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public ExchangeRateVO getRate(String baseCurrency, String quoteCurrency) {
        String base = baseCurrency != null ? baseCurrency.toUpperCase() : "AUD";
        String quote = quoteCurrency != null ? quoteCurrency.toUpperCase() : "CNY";

        if (base.equals(quote)) {
            return new ExchangeRateVO(base, quote, BigDecimal.ONE, LocalDateTime.now());
        }

        String cacheKey = RATE_CACHE_PREFIX + base + ":" + quote;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof ExchangeRateVO vo) {
                return vo;
            }
        } catch (Exception e) {
            log.warn("Failed to read exchange rate from Redis cache: {}", e.getMessage());
        }

        ExchangeRate entity = exchangeRateMapper.selectOne(
                new LambdaQueryWrapper<ExchangeRate>()
                        .eq(ExchangeRate::getBaseCurrency, base)
                        .eq(ExchangeRate::getQuoteCurrency, quote)
        );

        BigDecimal rate;
        LocalDateTime fetchedAt;
        if (entity != null) {
            rate = entity.getRate();
            fetchedAt = entity.getFetchedAt();
        } else {
            // Default fallback
            rate = (base.equals("AUD") && quote.equals("CNY")) ? new BigDecimal("4.750000") : BigDecimal.ONE;
            fetchedAt = LocalDateTime.now();
        }

        ExchangeRateVO result = new ExchangeRateVO(base, quote, rate, fetchedAt);
        try {
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Failed to write exchange rate to Redis cache: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public void updateRate(String baseCurrency, String quoteCurrency, BigDecimal rate) {
        String base = baseCurrency.toUpperCase();
        String quote = quoteCurrency.toUpperCase();

        ExchangeRate entity = exchangeRateMapper.selectOne(
                new LambdaQueryWrapper<ExchangeRate>()
                        .eq(ExchangeRate::getBaseCurrency, base)
                        .eq(ExchangeRate::getQuoteCurrency, quote)
        );

        LocalDateTime now = LocalDateTime.now();
        if (entity != null) {
            entity.setRate(rate);
            entity.setFetchedAt(now);
            exchangeRateMapper.updateById(entity);
        } else {
            entity = new ExchangeRate();
            entity.setBaseCurrency(base);
            entity.setQuoteCurrency(quote);
            entity.setRate(rate);
            entity.setFetchedAt(now);
            exchangeRateMapper.insert(entity);
        }

        String cacheKey = RATE_CACHE_PREFIX + base + ":" + quote;
        try {
            redisTemplate.opsForValue().set(cacheKey, new ExchangeRateVO(base, quote, rate, now), Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Failed to update exchange rate cache in Redis: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void dailyRefreshRate() {
        log.info("Refreshing exchange rates cache...");
        getRate("AUD", "CNY");
    }
}
