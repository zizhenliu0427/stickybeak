package au.com.stickybeak.payment.service;

import au.com.stickybeak.payment.dto.CreatePaymentSessionRequest;
import au.com.stickybeak.payment.vo.PaymentRecordVO;
import au.com.stickybeak.payment.vo.PaymentSessionVO;

public interface PaymentService {

    PaymentSessionVO createCheckoutSession(CreatePaymentSessionRequest req);

    PaymentRecordVO getByOrderNo(String orderNo);

    /** 订单取消 / 超时后让未支付的会话失效，防止关单后仍能付款 */
    void expireSession(String orderNo);
}
