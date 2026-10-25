package au.com.stickybeak.payment.provider;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.payment.config.StripeConfig;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Component
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);
    private final StripeConfig stripeConfig;

    public StripePaymentProvider(StripeConfig stripeConfig) {
        this.stripeConfig = stripeConfig;
    }

    @Override
    public String getProviderName() {
        return "stripe";
    }

    @Override
    public CreateSessionResult createSession(CreateSessionCommand cmd) {
        Stripe.apiKey = stripeConfig.getApiKey();

        String successUrl = stripeConfig.getSuccessUrl().replace("{ORDER_NO}", cmd.getOrderNo());
        String cancelUrl = stripeConfig.getCancelUrl().replace("{ORDER_NO}", cmd.getOrderNo());

        long amountInCents = cmd.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        String currency = StringUtils.hasText(cmd.getCurrency()) ? cmd.getCurrency().toLowerCase() : "aud";

        // 会话默认 24 小时有效，远长于订单的 30 分钟超时；不设上限会出现"订单已关闭仍可付款"
        long expiresAt = Instant.now().plus(Duration.ofMinutes(stripeConfig.getSessionExpiryMinutes())).getEpochSecond();

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setExpiresAt(expiresAt)
                .setClientReferenceId(cmd.getOrderNo())
                .putMetadata("orderNo", cmd.getOrderNo())
                .putMetadata("userId", cmd.getUserId() != null ? String.valueOf(cmd.getUserId()) : "")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(currency)
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("StickyBeak Order " + cmd.getOrderNo())
                                                                .setDescription(cmd.getDescription() != null ? cmd.getDescription() : "Aussie fridge magnets")
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );

        if (StringUtils.hasText(cmd.getEmail())) {
            builder.setCustomerEmail(cmd.getEmail());
        }

        // Issue 4.8: Multi-payment methods (Card, Alipay, WeChat Pay)
        String method = cmd.getPaymentMethod();
        if ("alipay".equalsIgnoreCase(method)) {
            builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.ALIPAY);
        } else if ("wechat_pay".equalsIgnoreCase(method)) {
            builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.WECHAT_PAY)
                    .setPaymentMethodOptions(
                            SessionCreateParams.PaymentMethodOptions.builder()
                                    .setWechatPay(
                                            SessionCreateParams.PaymentMethodOptions.WechatPay.builder()
                                                    .setClient(SessionCreateParams.PaymentMethodOptions.WechatPay.Client.WEB)
                                                    .build()
                                    )
                                    .build()
                    );
        } else {
            builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
        }

        try {
            Session session = Session.create(builder.build());
            return new CreateSessionResult(session.getId(), session.getUrl(), "stripe");
        } catch (Exception e) {
            log.error("Failed to create Stripe Checkout session: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "Stripe session creation failed: " + e.getMessage());
        }
    }

    @Override
    public void expireSession(String sessionId) {
        Stripe.apiKey = stripeConfig.getApiKey();
        try {
            Session session = Session.retrieve(sessionId);
            if ("open".equals(session.getStatus())) {
                session.expire();
                log.info("Expired Stripe Checkout session {}", sessionId);
            } else {
                log.info("Stripe Checkout session {} is already {}, skip expiring", sessionId, session.getStatus());
            }
        } catch (Exception e) {
            log.error("Failed to expire Stripe Checkout session {}: {}", sessionId, e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Stripe session expire failed: " + e.getMessage());
        }
    }

    @Override
    public RefundResult refund(RefundCommand cmd) {
        if (!StringUtils.hasText(cmd.getTransactionId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order " + cmd.getOrderNo() + " has no payment intent to refund");
        }
        Stripe.apiKey = stripeConfig.getApiKey();
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(cmd.getTransactionId())
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata("orderNo", cmd.getOrderNo())
                .build();
        // 同一订单的退款请求重试时复用幂等键，Stripe 不会重复退款
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("refund-" + cmd.getOrderNo())
                .build();
        try {
            Refund refund = Refund.create(params, options);
            log.info("Stripe refund {} for order {} is {}", refund.getId(), cmd.getOrderNo(), refund.getStatus());
            return new RefundResult(refund.getId(), refund.getStatus());
        } catch (Exception e) {
            log.error("Stripe refund failed for order {}: {}", cmd.getOrderNo(), e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Stripe refund failed: " + e.getMessage());
        }
    }

    @Override
    public String verifyWebhook(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
            return event.getId();
        } catch (Exception e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid webhook signature");
        }
    }
}
