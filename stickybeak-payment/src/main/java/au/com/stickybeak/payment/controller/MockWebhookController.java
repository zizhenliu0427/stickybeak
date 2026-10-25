package au.com.stickybeak.payment.controller;

import au.com.stickybeak.payment.service.WebhookService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模拟支付回调，供本地 E2E 脚本使用。无签名校验，默认不注册；
 * 仅当 stickybeak.payment.mock-webhook-enabled=true（环境变量 PAYMENT_MOCK_WEBHOOK_ENABLED）时可用。
 */
@RestController
@RequestMapping("/webhooks")
@ConditionalOnProperty(prefix = "stickybeak.payment", name = "mock-webhook-enabled", havingValue = "true")
public class MockWebhookController {

    private final WebhookService webhookService;

    public MockWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/mock")
    public ResponseEntity<Map<String, Object>> handleMockWebhook(@RequestBody String payload) {
        String result = webhookService.handleMockWebhook(payload);
        return ResponseEntity.ok(Map.of("received", true, "status", result));
    }
}
