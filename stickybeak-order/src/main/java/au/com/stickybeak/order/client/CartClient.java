package au.com.stickybeak.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class CartClient {

    private static final Logger log = LoggerFactory.getLogger(CartClient.class);
    private static final String CART_SERVICE_URL = "http://stickybeak-cart/cart";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CartClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public static class CartData {
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private List<CartItemData> items = new ArrayList<>();

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public List<CartItemData> getItems() {
            return items;
        }

        public void setItems(List<CartItemData> items) {
            this.items = items;
        }
    }

    public static class CartItemData {
        private Long productId;
        private String name;
        private String imageUrl;
        private BigDecimal price;
        private Integer qty;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public Integer getQty() {
            return qty;
        }

        public void setQty(Integer qty) {
            this.qty = qty;
        }
    }

    public CartData getCart(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(CART_SERVICE_URL, HttpMethod.GET, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("data") && !root.get("data").isNull()) {
                    JsonNode data = root.get("data");
                    CartData cartData = new CartData();
                    if (data.has("totalCents")) {
                        long totalCents = data.get("totalCents").asLong(0);
                        cartData.setTotalAmount(BigDecimal.valueOf(totalCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                    }
                    if (data.has("items") && data.get("items").isArray()) {
                        List<CartItemData> itemList = new ArrayList<>();
                        for (JsonNode itemNode : data.get("items")) {
                            CartItemData item = new CartItemData();
                            item.setProductId(itemNode.has("productId") ? itemNode.get("productId").asLong() : null);
                            item.setName(itemNode.has("name") ? itemNode.get("name").asText() : "");
                            item.setImageUrl(itemNode.has("imageUrl") ? itemNode.get("imageUrl").asText() : "");
                            item.setQty(itemNode.has("qty") ? itemNode.get("qty").asInt(1) : 1);

                            if (itemNode.has("priceCents")) {
                                long priceCents = itemNode.get("priceCents").asLong(0);
                                item.setPrice(BigDecimal.valueOf(priceCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                            } else if (itemNode.has("price")) {
                                item.setPrice(new BigDecimal(itemNode.get("price").asText()));
                            } else {
                                item.setPrice(BigDecimal.ZERO);
                            }
                            itemList.add(item);
                        }
                        cartData.setItems(itemList);
                    }
                    return cartData;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch cart for user {}: {}", userId, e.getMessage());
        }
        return new CartData();
    }

    public void clearCart(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(CART_SERVICE_URL, HttpMethod.DELETE, request, Void.class);
            log.info("Successfully cleared cart for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear cart for user {}: {}", userId, e.getMessage());
        }
    }
}
