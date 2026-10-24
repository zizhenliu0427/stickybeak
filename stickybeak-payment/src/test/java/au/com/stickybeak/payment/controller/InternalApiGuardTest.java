package au.com.stickybeak.payment.controller;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.internal.InternalApiInterceptor;
import au.com.stickybeak.common.internal.InternalCall;
import au.com.stickybeak.payment.dto.CreatePaymentSessionRequest;
import au.com.stickybeak.payment.service.ExchangeRateService;
import au.com.stickybeak.payment.service.PaymentService;
import au.com.stickybeak.payment.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 支付服务对外暴露面：会话创建 / 记录查询只允许内部调用，mock 回调默认不注册。
 */
class InternalApiGuardTest {

    private final InternalApiInterceptor interceptor = new InternalApiInterceptor();
    private final PaymentController controller = new PaymentController(mock(PaymentService.class), mock(ExchangeRateService.class));

    @Test
    void checkoutSessionRejectsExternalCallers() throws Exception {
        HandlerMethod handler = new HandlerMethod(controller,
                PaymentController.class.getMethod("createCheckoutSession", CreatePaymentSessionRequest.class));

        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler));

        MockHttpServletRequest internal = new MockHttpServletRequest();
        internal.addHeader(InternalCall.HEADER, InternalCall.VALUE);
        assertDoesNotThrow(() -> interceptor.preHandle(internal, new MockHttpServletResponse(), handler));
    }

    @Test
    void paymentRecordAndExpireAreInternalOnly() throws Exception {
        HandlerMethod record = new HandlerMethod(controller, PaymentController.class.getMethod("getRecord", String.class));
        HandlerMethod expire = new HandlerMethod(controller, PaymentController.class.getMethod("expireSession", String.class));

        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), record));
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), expire));
    }

    @Test
    void exchangeRateStaysPublic() throws Exception {
        HandlerMethod handler = new HandlerMethod(controller,
                PaymentController.class.getMethod("getExchangeRate", String.class, String.class));
        assertDoesNotThrow(() -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler));
    }

    @Test
    void mockWebhookIsNotRegisteredByDefault() {
        new WebApplicationContextRunner()
                .withBean(WebhookService.class, () -> mock(WebhookService.class))
                .withUserConfiguration(MockWebhookController.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MockWebhookController.class));
    }

    @Test
    void mockWebhookIsRegisteredOnlyWhenEnabled() {
        new WebApplicationContextRunner()
                .withBean(WebhookService.class, () -> mock(WebhookService.class))
                .withUserConfiguration(MockWebhookController.class)
                .withPropertyValues("stickybeak.payment.mock-webhook-enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(MockWebhookController.class));
    }
}
