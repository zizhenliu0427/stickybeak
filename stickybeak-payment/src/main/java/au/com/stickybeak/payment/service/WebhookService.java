package au.com.stickybeak.payment.service;

public interface WebhookService {

    String handleStripeWebhook(String payload, String sigHeader);

    String handleMockWebhook(String payload);
}
