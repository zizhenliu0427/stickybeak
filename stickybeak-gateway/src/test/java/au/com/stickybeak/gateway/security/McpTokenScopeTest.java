package au.com.stickybeak.gateway.security;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MCP 令牌（scope=mcp）：只能访问 /api/mcp，jti 必须是 Redis 里当前有效的那个；MCP 端点允许匿名。 */
class McpTokenScopeTest {

    private static final String SECRET =
            "c3RpY2t5YmVhay1kZXYtc2VjcmV0LWp3dC1oczI1Ni0yMDI2LWRvLW5vdC11c2UtaW4tcHJvZA==";

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };
    private AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthGlobalFilter(new JwtValidator(SECRET), redis, new ObjectMapper());
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
    }

    private static String token(String jti, String scope) {
        var builder = Jwts.builder().id(jti).subject("7").claim("roles", List.of("customer"))
                .expiration(new Date(System.currentTimeMillis() + 60_000));
        if (scope != null) {
            builder.claim("scope", scope);
        }
        return builder.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
    }

    private MockServerWebExchange post(String path, String token) {
        var request = MockServerHttpRequest.post(path);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void currentMcpTokenReachesTheMcpEndpointWithIdentity() {
        when(ops.get("auth:mcp:7")).thenReturn(Mono.just("jti-current"));

        post("/api/mcp", token("jti-current", "mcp"));

        assertNotNull(forwarded.get());
        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst(AuthGlobalFilter.HEADER_USER_ID));
    }

    @Test
    void mcpTokenCannotCallOtherApis() {
        MockServerWebExchange exchange = post("/api/orders/my", token("jti-current", "mcp"));
        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());

        exchange = post("/api/users/me/mcp-token", token("jti-current", "mcp"));
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void replacedOrRevokedMcpTokenIsRejected() {
        when(ops.get("auth:mcp:7")).thenReturn(Mono.just("jti-newer"));
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/mcp", token("jti-old", "mcp")).getResponse().getStatusCode());

        when(ops.get("auth:mcp:7")).thenReturn(Mono.empty());
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/mcp", token("jti-old", "mcp")).getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void anonymousCallersReachMcpWithoutIdentity() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/mcp").header("X-User-Id", "8").build();
        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(AuthGlobalFilter.HEADER_USER_ID));
    }

    @Test
    void normalSessionTokensAlsoWorkOnMcp() {
        post("/api/mcp", token("jti-web", null));
        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst(AuthGlobalFilter.HEADER_USER_ID));
    }
}
