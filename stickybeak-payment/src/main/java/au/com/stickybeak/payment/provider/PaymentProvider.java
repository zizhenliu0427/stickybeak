package au.com.stickybeak.payment.provider;

public interface PaymentProvider {

    String getProviderName();

    CreateSessionResult createSession(CreateSessionCommand cmd);

    String verifyWebhook(String payload, String sigHeader);

    /** 让未支付的会话失效（订单取消 / 超时后调用），默认无操作 */
    default void expireSession(String sessionId) {
    }
}
