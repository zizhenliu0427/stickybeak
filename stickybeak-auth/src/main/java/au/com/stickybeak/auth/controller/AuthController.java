package au.com.stickybeak.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import au.com.stickybeak.auth.dto.LoginRequest;
import au.com.stickybeak.auth.dto.RegisterRequest;
import au.com.stickybeak.auth.service.AuthService;
import au.com.stickybeak.auth.vo.AuthVO;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;

/**
 * 认证端点。token 通过 HttpOnly Cookie 下发（防 XSS 读取），body 只带用户信息。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    public static final String COOKIE_ACCESS = "sb_access";
    public static final String COOKIE_REFRESH = "sb_refresh";

    private final AuthService authService;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final long refreshTtlDays;

    public AuthController(AuthService authService,
                          @Value("${app.cookie.secure:false}") boolean cookieSecure,
                          @Value("${app.cookie.same-site:Lax}") String cookieSameSite,
                          @Value("${app.jwt.refresh-ttl-days:7}") long refreshTtlDays) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.refreshTtlDays = refreshTtlDays;
    }

    @PostMapping("/register")
    public Result<AuthVO> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
        AuthService.AuthResult result = authService.register(req);
        writeTokenCookies(response, result);
        return Result.ok(new AuthVO(result.user(), result.accessExpiresIn()));
    }

    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(req);
        writeTokenCookies(response, result);
        return Result.ok(new AuthVO(result.user(), result.accessExpiresIn()));
    }

    @PostMapping("/refresh")
    public Result<AuthVO> refresh(
            @CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "missing refresh token");
        }
        AuthService.AuthResult result = authService.refresh(refreshToken);
        writeTokenCookies(response, result);
        return Result.ok(new AuthVO(result.user(), result.accessExpiresIn()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @CookieValue(name = COOKIE_ACCESS, required = false) String accessToken,
            @CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(accessToken, refreshToken);
        clearCookie(response, COOKIE_ACCESS);
        clearCookie(response, COOKIE_REFRESH);
        return Result.ok();
    }

    private void writeTokenCookies(HttpServletResponse response, AuthService.AuthResult result) {
        addCookie(response, COOKIE_ACCESS, result.accessToken(), result.accessExpiresIn());
        addCookie(response, COOKIE_REFRESH, result.refreshToken(), refreshTtlDays * 24 * 3600);
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
