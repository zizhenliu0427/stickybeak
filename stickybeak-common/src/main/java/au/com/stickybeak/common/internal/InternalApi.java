package au.com.stickybeak.common.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记仅供服务间调用的接口（库存扣减、支付会话等）。
 * 请求未携带 {@link InternalCall#HEADER} 时由 {@link InternalApiInterceptor} 拒绝（403）。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InternalApi {
}
