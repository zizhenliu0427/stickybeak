package au.com.stickybeak.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;

/**
 * 从 SecurityContext 取当前用户（由 HeaderAuthFilter 写入）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getPrincipal().toString());
    }
}
