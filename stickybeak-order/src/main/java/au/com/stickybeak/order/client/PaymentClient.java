package au.com.stickybeak.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);
    private static final String PAYMENT_SERVICE_URL = "http://stickybeak-payment/payments";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PaymentClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public static class PaymentSessionResult {
        private String orderNo;
        private String sessionId;
        private String checkoutUrl;
        private String provider;

        public PaymentSessionResult() {
        }

        public PaymentSessionResult(String orderNo, String sessionId, String checkoutUrl, String provider) {
            this.orderNo = orderNo;
            this.sessionId = sessionId;
            this.checkoutUrl = checkoutUrl;
            this.provider = provider;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }

        public void setCheckoutUrl(String checkoutUrl) {
            this.checkoutUrl = checkoutUrl;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    public BigDecimal getExchangeRate(String base, String quote) {
        if (base == null || quote == null || base.equalsIgnoreCase(quote)) {
            return BigDecimal.ONE;
        }
        try {
            String url = PAYMENT_SERVICE_URL + "/exchange-rate?base=" + base + "&quote=" + quote;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("data") && root.get("data").has("rate")) {
                    return new BigDecimal(root.get("data").get("rate").asText());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get exchange rate {}->{} from payment service: {}, falling back to 4.75", base, quote, e.getMessage());
        }
        return new BigDecimal("4.750000");
    }

    public PaymentSessionResult createCheckoutSession(String orderNo, Long userId, String email,
                                                      BigDecimal amount, String currency,
                                                      String paymentMethod, String description) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("orderNo", orderNo);
        reqBody.put("userId", userId);
        reqBody.put("email", email);
        reqBody.put("amount", amount);
        reqBody.put("currency", currency);
        reqBody.put("paymentMethod", paymentMethod);
        reqBody.put("description", description);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);
        String url = PAYMENT_SERVICE_URL + "/checkout-session";

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("data") && !root.get("data").isNull()) {
                    JsonNode data = root.get("data");
                    PaymentSessionResult result = new PaymentSessionResult();
                    result.setOrderNo(orderNo);
                    result.setSessionId(data.has("sessionId") ? data.get("sessionId").asText() : null);
                    result.setCheckoutUrl(data.has("checkoutUrl") ? data.get("checkoutUrl").asText() : null);
                    result.setProvider(data.has("provider") ? data.get("provider").asText() : "stripe");
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("Failed to create checkout session for order {}: {}", orderNo, e.getMessage(), e);
        }
        return new PaymentSessionResult(orderNo, null, null, "unknown");
    }
}
