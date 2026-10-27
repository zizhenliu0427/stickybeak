package au.com.stickybeak.payment.provider;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.payment.config.StripeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private final ObjectMapper objectMapper;
    private final StripeConfig stripeConfig;

    public MockPaymentProvider(ObjectMapper objectMapper, StripeConfig stripeConfig) {
        this.objectMapper = objectMapper;
        this.stripeConfig = stripeConfig;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public CreateSessionResult createSession(CreateSessionCommand cmd) {
        String mockSessionId = "mock_cs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String mockCheckoutUrl = stripeConfig.getSuccessUrl()
                .replace("{CHECKOUT_SESSION_ID}", mockSessionId)
                .replace("{ORDER_NO}", cmd.getOrderNo());
        return new CreateSessionResult(mockSessionId, mockCheckoutUrl, "mock");
    }

    @Override
    public String verifyWebhook(String payload, String sigHeader) {
        if (sigHeader == null || sigHeader.contains("invalid") || sigHeader.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid webhook signature");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("id")) {
                return root.get("id").asText();
            }
            return "evt_mock_" + System.currentTimeMillis();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Malformed webhook payload");
        }
    }

    /** 模拟渠道：退款同步成功 */
    @Override
    public RefundResult refund(RefundCommand cmd) {
        String refundId = "mock_re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new RefundResult(refundId, RefundResult.STATUS_SUCCEEDED);
    }
}
