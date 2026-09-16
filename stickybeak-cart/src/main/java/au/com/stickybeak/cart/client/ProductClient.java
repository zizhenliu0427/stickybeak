package au.com.stickybeak.cart.client;

import com.fasterxml.jackson.core.type.TypeReference;
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

import java.util.*;

@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);
    private static final String PRODUCT_SERVICE_URL = "http://stickybeak-product";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ProductClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据商品 ID 获取单个商品信息
     */
    public ProductDTO getById(Long id) {
        if (id == null) {
            return null;
        }
        try {
            String url = PRODUCT_SERVICE_URL + "/products/id/" + id;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("code") && root.get("code").asInt() == 0 && root.has("data")) {
                    return objectMapper.treeToValue(root.get("data"), ProductDTO.class);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch product by id {}: {}", id, e.getMessage());
        }
        return null;
    }

    /**
     * 批量获取商品信息，返回 productId -> ProductDTO 映射
     */
    public Map<Long, ProductDTO> getByIds(Collection<Long> ids) {
        Map<Long, ProductDTO> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }

        try {
            String url = PRODUCT_SERVICE_URL + "/products/batch";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Collection<Long>> request = new HttpEntity<>(ids, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("code") && root.get("code").asInt() == 0 && root.has("data")) {
                    List<ProductDTO> list = objectMapper.readValue(
                            root.get("data").traverse(),
                            new TypeReference<List<ProductDTO>>() {}
                    );
                    for (ProductDTO dto : list) {
                        result.put(dto.getId(), dto);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to batch fetch products for ids {}: {}", ids, e.getMessage());
        }
        return result;
    }
}
