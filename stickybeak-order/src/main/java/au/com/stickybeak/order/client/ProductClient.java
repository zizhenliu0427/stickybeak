package au.com.stickybeak.order.client;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);
    private static final String PRODUCT_SERVICE_URL = "http://stickybeak-product/products/stock";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ProductClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public static class StockItemParam {
        private Long productId;
        private Integer qty;

        public StockItemParam() {
        }

        public StockItemParam(Long productId, Integer qty) {
            this.productId = productId;
            this.qty = qty;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQty() {
            return qty;
        }

        public void setQty(Integer qty) {
            this.qty = qty;
        }
    }

    public void deductStock(String orderNo, List<StockItemParam> items) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", orderNo);
        body.put("items", items);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = PRODUCT_SERVICE_URL + "/deduct";

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Failed to deduct stock");
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("message").asText("Insufficient stock");
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, msg);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("HTTP error during stock deduction for order {}: {} - {}", orderNo, ex.getStatusCode(), ex.getResponseBodyAsString());
            String errorMsg = "Product stock is insufficient";
            try {
                JsonNode errNode = objectMapper.readTree(ex.getResponseBodyAsString());
                if (errNode.has("message")) {
                    errorMsg = errNode.get("message").asText();
                }
            } catch (Exception ignored) {
            }
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, errorMsg);
        } catch (Exception e) {
            log.error("Failed to deduct stock for order {}: {}", orderNo, e.getMessage());
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Stock deduction service unavailable: " + e.getMessage());
        }
    }

    public void rollbackStock(String orderNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", orderNo);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        postOrThrow(PRODUCT_SERVICE_URL + "/rollback", request, "rollback", orderNo);
    }

    public void confirmStock(String orderNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", orderNo);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        postOrThrow(PRODUCT_SERVICE_URL + "/confirm", request, "confirm", orderNo);
    }

    /**
     * 回滚 / 确认失败必须抛出：调用方（消息监听器）据此重试，
     * 吞掉异常会让库存一直停在 HELD 状态。
     */
    private void postOrThrow(String url, HttpEntity<Map<String, Object>> request, String action, String orderNo) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            int code = response.getBody() == null ? -1 : objectMapper.readTree(response.getBody()).path("code").asInt(-1);
            if (code != 0) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "Stock " + action + " returned code " + code);
            }
            log.info("Stock {} RPC succeeded for order {}", action, orderNo);
        } catch (BusinessException be) {
            log.error("Stock {} RPC failed for order {}: {}", action, orderNo, be.getMessage());
            throw be;
        } catch (Exception e) {
            log.error("Stock {} RPC failed for order {}: {}", action, orderNo, e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Stock " + action + " failed: " + e.getMessage());
        }
    }
}
