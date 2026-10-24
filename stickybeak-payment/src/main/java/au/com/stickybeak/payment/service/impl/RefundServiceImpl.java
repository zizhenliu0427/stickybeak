package au.com.stickybeak.payment.service.impl;

import au.com.stickybeak.common.event.OrderRefundedEvent;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.common.tx.AfterCommit;
import au.com.stickybeak.payment.entity.PaymentRecord;
import au.com.stickybeak.payment.mapper.PaymentRecordMapper;
import au.com.stickybeak.payment.provider.PaymentProviderFactory;
import au.com.stickybeak.payment.provider.RefundCommand;
import au.com.stickybeak.payment.provider.RefundResult;
import au.com.stickybeak.payment.service.RefundService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);

    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentProviderFactory paymentProviderFactory;
    private final RabbitTemplate rabbitTemplate;

    public RefundServiceImpl(PaymentRecordMapper paymentRecordMapper,
                             PaymentProviderFactory paymentProviderFactory,
                             RabbitTemplate rabbitTemplate) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.paymentProviderFactory = paymentProviderFactory;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refund(String orderNo, String reason) {
        PaymentRecord record = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderNo, orderNo)
        );
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "No payment record for order " + orderNo);
        }
        if (STATUS_REFUNDED.equalsIgnoreCase(record.getStatus())) {
            log.info("Order {} already refunded, re-publishing refund event", orderNo);
            publishRefunded(record, null);
            return STATUS_REFUNDED;
        }
        if (!"succeeded".equalsIgnoreCase(record.getStatus()) && !STATUS_REFUNDING.equalsIgnoreCase(record.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "Order " + orderNo + " is not paid (payment status " + record.getStatus() + ")");
        }

        RefundResult result = paymentProviderFactory.getProviderByName(record.getProvider()).refund(
                new RefundCommand(orderNo, record.getTransactionId(), record.getAmount(), record.getCurrency(), reason));
        if (result.isFailed()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Refund " + result.getRefundId() + " " + result.getStatus());
        }
        if (result.isSucceeded()) {
            markRefunded(record, result.getRefundId());
            return STATUS_REFUNDED;
        }

        // pending / requires_action：等待 charge.refunded webhook
        record.setStatus(STATUS_REFUNDING);
        paymentRecordMapper.updateById(record);
        log.info("Refund {} for order {} is {}, waiting for webhook", result.getRefundId(), orderNo, result.getStatus());
        return STATUS_REFUNDING;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmRefund(String transactionId, String refundId) {
        if (!StringUtils.hasText(transactionId)) {
            return false;
        }
        PaymentRecord record = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getTransactionId, transactionId)
        );
        if (record == null) {
            return false;
        }
        if (STATUS_REFUNDED.equalsIgnoreCase(record.getStatus())) {
            return true;
        }
        markRefunded(record, refundId);
        return true;
    }

    private void markRefunded(PaymentRecord record, String refundId) {
        record.setStatus(STATUS_REFUNDED);
        paymentRecordMapper.updateById(record);
        log.info("Order {} refunded (refund {})", record.getOrderNo(), refundId);
        publishRefunded(record, refundId);
    }

    /** 事务提交后再发布，避免数据库回滚了事件却已发出 */
    private void publishRefunded(PaymentRecord record, String refundId) {
        OrderRefundedEvent event = new OrderRefundedEvent(record.getOrderNo(), record.getAmount(),
                record.getCurrency(), refundId, LocalDateTime.now());
        AfterCommit.run(() -> rabbitTemplate.convertAndSend(OrderRefundedEvent.EXCHANGE, OrderRefundedEvent.ROUTING_KEY, event));
    }
}
