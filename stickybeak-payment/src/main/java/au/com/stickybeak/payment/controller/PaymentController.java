package au.com.stickybeak.payment.controller;

import au.com.stickybeak.common.internal.InternalApi;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.payment.dto.CreatePaymentSessionRequest;
import au.com.stickybeak.payment.service.ExchangeRateService;
import au.com.stickybeak.payment.service.PaymentService;
import au.com.stickybeak.payment.vo.ExchangeRateVO;
import au.com.stickybeak.payment.vo.PaymentRecordVO;
import au.com.stickybeak.payment.vo.PaymentSessionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final ExchangeRateService exchangeRateService;

    public PaymentController(PaymentService paymentService, ExchangeRateService exchangeRateService) {
        this.paymentService = paymentService;
        this.exchangeRateService = exchangeRateService;
    }

    /** 仅订单服务调用：金额由订单服务计算，不能让用户直接指定 */
    @InternalApi
    @PostMapping("/checkout-session")
    public Result<PaymentSessionVO> createCheckoutSession(@RequestBody @Valid CreatePaymentSessionRequest req) {
        PaymentSessionVO vo = paymentService.createCheckoutSession(req);
        return Result.ok(vo);
    }

    @GetMapping("/exchange-rate")
    public Result<ExchangeRateVO> getExchangeRate(
            @RequestParam(defaultValue = "AUD") String base,
            @RequestParam(defaultValue = "CNY") String quote) {
        ExchangeRateVO vo = exchangeRateService.getRate(base, quote);
        return Result.ok(vo);
    }

    /** 记录不含用户归属，无法做越权校验，只开放给内部服务 */
    @InternalApi
    @GetMapping("/records/{orderNo}")
    public Result<PaymentRecordVO> getRecord(@PathVariable String orderNo) {
        PaymentRecordVO vo = paymentService.getByOrderNo(orderNo);
        return Result.ok(vo);
    }

    @InternalApi
    @PostMapping("/{orderNo}/expire-session")
    public Result<Void> expireSession(@PathVariable String orderNo) {
        paymentService.expireSession(orderNo);
        return Result.ok();
    }
}
