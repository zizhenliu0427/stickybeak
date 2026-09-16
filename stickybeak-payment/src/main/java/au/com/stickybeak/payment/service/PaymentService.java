package au.com.stickybeak.payment.service;

import au.com.stickybeak.payment.dto.CreatePaymentSessionRequest;
import au.com.stickybeak.payment.vo.PaymentRecordVO;
import au.com.stickybeak.payment.vo.PaymentSessionVO;

public interface PaymentService {

    PaymentSessionVO createCheckoutSession(CreatePaymentSessionRequest req);

    PaymentRecordVO getByOrderNo(String orderNo);
}
