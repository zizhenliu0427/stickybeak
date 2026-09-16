package au.com.stickybeak.payment.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.payment.dto.CreatePaymentSessionRequest;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.provider.CreateSessionCommand;
import au.com.stickybeak.payment.provider.CreateSessionResult;
import au.com.stickybeak.payment.provider.PaymentProvider;
import au.com.stickybeak.payment.provider.PaymentProviderFactory;
import au.com.stickybeak.payment.service.ExchangeRateService;
import au.com.stickybeak.payment.service.PaymentService;
import au.com.stickybeak.payment.vo.ExchangeRateVO;
import au.com.stickybeak.payment.vo.PaymentRecordVO;
import au.com.stickybeak.payment.vo.PaymentSessionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentProviderFactory paymentProviderFactory;
    private final ExchangeRateService exchangeRateService;

    public PaymentServiceImpl(PaymentRecordMapper paymentRecordMapper,
                              PaymentProviderFactory paymentProviderFactory,
                              ExchangeRateService exchangeRateService) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.paymentProviderFactory = paymentProviderFactory;
        this.exchangeRateService = exchangeRateService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentSessionVO createCheckoutSession(CreatePaymentSessionRequest req) {
        PaymentRecord existing = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderNo, req.getOrderNo())
        );

        if (existing != null && "succeeded".equalsIgnoreCase(existing.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order " + req.getOrderNo() + " is already paid");
        }

        PaymentProvider provider;
        if (StringUtils.hasText(req.getProvider())) {
            provider = paymentProviderFactory.getProviderByName(req.getProvider());
        } else {
            provider = paymentProviderFactory.getProvider();
        }

        CreateSessionCommand cmd = new CreateSessionCommand();
        cmd.setOrderNo(req.getOrderNo());
        cmd.setUserId(req.getUserId());
        cmd.setEmail(req.getEmail());
        cmd.setAmount(req.getAmount());
        cmd.setCurrency(StringUtils.hasText(req.getCurrency()) ? req.getCurrency() : "AUD");
        cmd.setPaymentMethod(StringUtils.hasText(req.getPaymentMethod()) ? req.getPaymentMethod() : "card");
        cmd.setDescription(req.getDescription());

        CreateSessionResult result = provider.createSession(cmd);

        ExchangeRateVO rateVO = exchangeRateService.getRate("AUD", cmd.getCurrency().toUpperCase());
        BigDecimal exchangeRate = rateVO != null ? rateVO.getRate() : BigDecimal.ONE;

        if (existing != null) {
            existing.setProvider(result.getProvider());
            existing.setPaymentMethod(cmd.getPaymentMethod());
            existing.setSessionId(result.getSessionId());
            existing.setAmount(cmd.getAmount());
            existing.setCurrency(cmd.getCurrency());
            existing.setExchangeRateAtPay(exchangeRate);
            existing.setStatus("pending");
            paymentRecordMapper.updateById(existing);
        } else {
            PaymentRecord record = new PaymentRecord();
            record.setOrderNo(cmd.getOrderNo());
            record.setProvider(result.getProvider());
            record.setPaymentMethod(cmd.getPaymentMethod());
            record.setSessionId(result.getSessionId());
            record.setAmount(cmd.getAmount());
            record.setCurrency(cmd.getCurrency());
            record.setExchangeRateAtPay(exchangeRate);
            record.setStatus("pending");
            paymentRecordMapper.insert(record);
        }

        return new PaymentSessionVO(req.getOrderNo(), result.getSessionId(), result.getCheckoutUrl(), result.getProvider());
    }

    @Override
    public PaymentRecordVO getByOrderNo(String orderNo) {
        PaymentRecord record = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderNo, orderNo)
        );
        if (record == null) {
            return null;
        }
        PaymentRecordVO vo = new PaymentRecordVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
