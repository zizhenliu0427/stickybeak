package au.com.stickybeak.common.exception;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;

/**
 * Spring Security 异常 → 统一 Result。
 * 独立成类并按类路径条件加载：未引入 spring-security 的服务
 * （如 product/cart）扫描 common 时不会因类缺失而崩溃。
 * 必须最高优先级：Spring 跨 advice 是「先注册先匹配」而非最具体优先，
 * 否则 GlobalExceptionHandler 的 Exception 兜底会抢先吞掉安全异常返回 500。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.security.core.AuthenticationException")
public class SecurityExceptionHandler {

    /**
     * 越权 → 403。两类都要接：
     * 过滤器链抛 AccessDeniedException；@PreAuthorize 在 Security 6.x 抛 AuthorizationDeniedException（互不继承）。
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Result<Void>> handleAccessDenied(Exception e) {
        return GlobalExceptionHandler.build(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage());
    }

    /** 未认证（Security 过滤器链） → 401 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException e) {
        return GlobalExceptionHandler.build(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage());
    }
}
