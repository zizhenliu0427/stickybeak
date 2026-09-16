package au.com.stickybeak.payment.controller;

import au.com.stickybeak.payment.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        String result = webhookService.handleStripeWebhook(payload, sigHeader);
        return ResponseEntity.ok(Map.of("received", true, "status", result));
    }

    @PostMapping("/mock")
    public ResponseEntity<Map<String, Object>> handleMockWebhook(@RequestBody String payload) {
        String result = webhookService.handleMockWebhook(payload);
        return ResponseEntity.ok(Map.of("received", true, "status", result));
    }
}
