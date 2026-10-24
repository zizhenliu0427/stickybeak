package au.com.stickybeak.common.internal;

/**
 * 服务间内部调用标识。
 * 网关会剥离外部请求携带的该请求头（与 X-User-Id 同一信任模型），
 * 因此只有集群内部的服务调用才能带上它。
 */
public final class InternalCall {

    public static final String HEADER = "X-Internal-Call";
    public static final String VALUE = "true";

    private InternalCall() {
    }
}
