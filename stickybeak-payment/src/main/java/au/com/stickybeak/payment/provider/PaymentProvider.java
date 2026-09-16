package au.com.stickybeak.payment.provider;

public interface PaymentProvider {

    String getProviderName();

    CreateSessionResult createSession(CreateSessionCommand cmd);

    String verifyWebhook(String payload, String sigHeader);
}
