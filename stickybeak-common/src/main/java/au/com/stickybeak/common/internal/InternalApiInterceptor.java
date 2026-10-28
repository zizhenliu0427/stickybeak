package au.com.stickybeak.common.internal;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 拦截 {@link InternalApi} 标注的接口：缺少内部调用标识时返回 403。
 */
public class InternalApiInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean internal = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), InternalApi.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), InternalApi.class);
        if (internal && !InternalCall.VALUE.equals(request.getHeader(InternalCall.HEADER))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Internal API");
        }
        return true;
    }
}
