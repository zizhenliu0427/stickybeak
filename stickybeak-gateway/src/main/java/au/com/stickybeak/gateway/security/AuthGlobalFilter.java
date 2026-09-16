package au.com.stickybeak.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;

import reactor.core.publisher.Mono;

/**
 * 网关统一鉴权（Issue 1.3）：
 * 1. 剥离外部伪造的 X-User-Id / X-User-Roles 头
 * 2. 白名单路径直接放行
 * 3. 校验 JWT（cookie sb_access 或 Authorization: Bearer）+ Redis 黑名单
 * 4. 注入 X-User-Id / X-User-Roles 供下游服务 @PreAuthorize 兜底
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String COOKIE_ACCESS = "sb_access";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    /** 全方法公开前缀 */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/webhooks",
            "/actuator"
    );

    /** GET 公开前缀（商品浏览、公开汇率） */
    private static final List<String> PUBLIC_GET_PREFIXES = List.of(
            "/api/products",
            "/api/categories",
            "/api/tags",
            "/api/payments/exchange-rate"
    );

    /** 可选认证前缀（购物车、心愿单等，支持游客与登录用户） */
    private static final List<String> OPTIONAL_AUTH_PREFIXES = List.of(
            "/api/cart",
            "/api/wishlist"
    );

    private final JwtValidator jwtValidator;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public AuthGlobalFilter(JwtValidator jwtValidator, ReactiveStringRedisTemplate redis,
                            ObjectMapper objectMapper) {
        this.jwtValidator = jwtValidator;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 1. 无论是否登录，先剥离可伪造的身份头
        ServerHttpRequest sanitized = request.mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLES);
                })
                .build();

        String path = request.getPath().value();
        if (isPublic(path, request.getMethod())) {
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String token = resolveToken(request);
        if (token == null) {
            if (isOptionalAuth(path)) {
                return chain.filter(exchange.mutate().request(sanitized).build());
            }
            return writeUnauthorized(exchange, "missing token");
        }

        Jws<Claims> jws;
        try {
            jws = jwtValidator.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return writeUnauthorized(exchange, "invalid token");
        }

        String jti = jws.getPayload().getId();
        return redis.hasKey(BLACKLIST_PREFIX + jti).flatMap(blacklisted -> {
            if (Boolean.TRUE.equals(blacklisted)) {
                return writeUnauthorized(exchange, "token revoked");
            }
            Claims claims = jws.getPayload();
            List<?> roles = claims.get("roles", List.class);
            String rolesHeader = roles == null ? "" :
                    String.join(",", roles.stream().map(String::valueOf).toList());
            if (path.contains("/admin")) {
                boolean hasAdminRole = roles != null && roles.stream().anyMatch(r -> {
                    String s = String.valueOf(r).toLowerCase();
                    return s.contains("admin") || s.contains("sysadmin");
                });
                if (!hasAdminRole) {
                    return writeForbidden(exchange, "admin permission required");
                }
            }

            ServerHttpRequest authenticated = sanitized.mutate()
                    .header(HEADER_USER_ID, claims.getSubject())
                    .header(HEADER_USER_ROLES, rolesHeader)
                    .build();
            return chain.filter(exchange.mutate().request(authenticated).build());
        });
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (path.endsWith("/health")) {
            return true;
        }
        if (path.contains("/admin")) {
            return false;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        if (HttpMethod.GET.equals(method)) {
            for (String prefix : PUBLIC_GET_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOptionalAuth(String path) {
        for (String prefix : OPTIONAL_AUTH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String resolveToken(ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(COOKIE_ACCESS);
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsString(
                    Result.fail(ResultCode.UNAUTHORIZED.getCode(), message)).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsString(
                    Result.fail(ResultCode.FORBIDDEN.getCode(), message)).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 在 NettyRoutingFilter 之前执行
        return -100;
    }
}
