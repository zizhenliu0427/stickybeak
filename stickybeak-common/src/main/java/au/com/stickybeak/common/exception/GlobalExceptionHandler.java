package au.com.stickybeak.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;

/**
 * Global exception handler: converts exceptions into the unified Result body.
 * HTTP 状态码与业务码对齐（401/403 等真实返回，前端 401 自动刷新依赖它）；
 * 1000+ 纯业务码走 HTTP 200。
 * 仅 Servlet 应用加载：网关（WebFlux）无 spring-webmvc，
 * 加载本类会因 NoResourceFoundException 缺失而 NoClassDefFoundError。
 * 最低优先级：让 SecurityExceptionHandler 等专项 advice 先匹配。
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("business exception: code={}, message={}", e.getCode(), e.getMessage());
        return build(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleValidation(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst()
                .orElse(ResultCode.BAD_REQUEST.getMessage());
        return build(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** Unknown path -> 404, wrong method -> 405 (must not fall through to 500). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return build(ResultCode.NOT_FOUND.getCode(), "not found: " + e.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return build(405, "method not supported: " + e.getMethod());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("unexpected exception", e);
        return build(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage());
    }

    /** 业务码若能解析成标准 HTTP 状态则对齐，否则 HTTP 200 + 业务码 */
    static ResponseEntity<Result<Void>> build(int code, String message) {
        HttpStatus status = HttpStatus.resolve(code);
        return ResponseEntity.status(status != null ? status : HttpStatus.OK)
                .body(Result.fail(code, message));
    }
}
