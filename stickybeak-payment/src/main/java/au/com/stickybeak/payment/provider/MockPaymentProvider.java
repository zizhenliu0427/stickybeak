package au.com.stickybeak.payment.provider;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private final ObjectMapper objectMapper;

    public MockPaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public CreateSessionResult createSession(CreateSessionCommand cmd) {
        String mockSessionId = "mock_cs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String mockCheckoutUrl = "http://localhost:5173/checkout/success?session_id=" + mockSessionId + "&order_no=" + cmd.getOrderNo();
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
}
