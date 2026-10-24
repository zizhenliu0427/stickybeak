package au.com.stickybeak.gateway.security;

import au.com.stickybeak.common.internal.InternalCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthGlobalFilterTest {

    private static final String SECRET =
            "c3RpY2t5YmVhay1kZXYtc2VjcmV0LWp3dC1oczI1Ni0yMDI2LWRvLW5vdC11c2UtaW4tcHJvZA==";

    private AuthGlobalFilter filter;
    private AtomicReference<ServerWebExchange> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthGlobalFilter(new JwtValidator(SECRET), mock(ReactiveStringRedisTemplate.class), new ObjectMapper());
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private MockServerWebExchange run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void stripeWebhookIsPublic() {
        run(MockServerHttpRequest.post("/api/webhooks/stripe").build());
        assertNotNull(forwarded.get());
    }

    @Test
    void mockWebhookRequiresLogin() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.post("/api/webhooks/mock").build());
        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void stockWritesAreBlocked() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.post("/api/products/stock/deduct").build());
        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void encodedAndTraversalStockPathsAreBlocked() {
        for (String path : new String[]{
                "/api/products/%73tock/rollback",
                "/api/products/x/../stock/confirm",
                "/api/products/stock;v=1/deduct"}) {
            forwarded.set(null);
            // 传入已编码的 URI，避免 MockServerHttpRequest 把 % 再编码一次
            MockServerWebExchange exchange = run(MockServerHttpRequest.method(HttpMethod.POST, URI.create(path)).build());
            assertNull(forwarded.get(), path);
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode(), path);
        }
    }

    @Test
    void stockQueryStaysPublic() {
        run(MockServerHttpRequest.get("/api/products/stock/1001").build());
        assertNotNull(forwarded.get());
    }

    @Test
    void paymentInternalEndpointsAreBlocked() {
        for (String path : new String[]{
                "/api/payments/checkout-session",
                "/api/payments/records/SO1",
                "/api/payments/SO1/expire-session"}) {
            forwarded.set(null);
            MockServerWebExchange exchange = run(MockServerHttpRequest.post(path).build());
            assertNull(forwarded.get(), path);
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode(), path);
        }
    }

    @Test
    void forgedIdentityAndInternalHeadersAreStripped() {
        run(MockServerHttpRequest.get("/api/products")
                .header(AuthGlobalFilter.HEADER_USER_ID, "1")
                .header(AuthGlobalFilter.HEADER_USER_ROLES, "ROLE_ADMIN")
                .header(InternalCall.HEADER, InternalCall.VALUE)
                .build());

        var headers = forwarded.get().getRequest().getHeaders();
        assertFalse(headers.containsKey(AuthGlobalFilter.HEADER_USER_ID));
        assertFalse(headers.containsKey(AuthGlobalFilter.HEADER_USER_ROLES));
        assertFalse(headers.containsKey(InternalCall.HEADER));
    }

    @Test
    void encodedAdminPathStillNeedsAuth() {
        MockServerWebExchange exchange = run(
                MockServerHttpRequest.method(HttpMethod.GET, URI.create("/api/products/%61dmin/page")).build());
        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void normalizePathHandlesEncodingAndDots() {
        assertEquals("/api/products/stock/deduct", AuthGlobalFilter.normalizePath("/api/products/%73tock/deduct"));
        assertEquals("/api/orders/my", AuthGlobalFilter.normalizePath("/api/products/../orders/my"));
        assertNull(AuthGlobalFilter.normalizePath("/api/products/%zz"));
    }
}
