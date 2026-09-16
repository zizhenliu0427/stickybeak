package au.com.stickybeak.order.client;

import au.com.stickybeak.order.dto.AddressDTO;
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

@Component
public class AuthClient {

    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);
    private static final String ADDRESS_URL = "http://stickybeak-auth/users/me/addresses";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AuthClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public AddressDTO getAddressById(Long userId, Long addressId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(ADDRESS_URL, HttpMethod.GET, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("data") && root.get("data").isArray()) {
                    for (JsonNode addrNode : root.get("data")) {
                        if (addressId == null || (addrNode.has("id") && addrNode.get("id").asLong() == addressId)) {
                            AddressDTO dto = new AddressDTO();
                            dto.setId(addrNode.has("id") ? addrNode.get("id").asLong() : null);
                            dto.setReceiverName(addrNode.has("receiverName") ? addrNode.get("receiverName").asText() : "");
                            dto.setPhone(addrNode.has("phone") ? addrNode.get("phone").asText() : "");
                            dto.setCountry(addrNode.has("country") ? addrNode.get("country").asText() : "Australia");
                            dto.setState(addrNode.has("state") ? addrNode.get("state").asText() : "");
                            dto.setCity(addrNode.has("city") ? addrNode.get("city").asText() : "");
                            dto.setPostcode(addrNode.has("postcode") ? addrNode.get("postcode").asText() : "");
                            dto.setDetailAddress(addrNode.has("detailAddress") ? addrNode.get("detailAddress").asText() : "");
                            return dto;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query addresses for user {}: {}", userId, e.getMessage());
        }
        return null;
    }
}
