package au.com.stickybeak.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import au.com.stickybeak.auth.security.JwtTokenProvider;
import au.com.stickybeak.auth.service.impl.McpTokenServiceImpl;
import au.com.stickybeak.auth.vo.McpTokenVO;
import au.com.stickybeak.auth.vo.UserVO;

import io.jsonwebtoken.Claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpTokenServiceImplTest {

    private static final String SECRET =
            "c3RpY2t5YmVhay1kZXYtc2VjcmV0LWp3dC1oczI1Ni0yMDI2LWRvLW5vdC11c2UtaW4tcHJvZA==";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> ops;
    @Mock
    private UserService userService;

    private final JwtTokenProvider jwt = new JwtTokenProvider(SECRET, 15);
    private McpTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new McpTokenServiceImpl(redis, jwt, userService, 30);
    }

    @Test
    void issuesAScopedTokenAndRecordsItsJtiAsTheOnlyValidOne() {
        when(redis.opsForValue()).thenReturn(ops);
        when(userService.getProfile(7L)).thenReturn(
                new UserVO(7L, "buyer@example.com", "Buyer", null, null, List.of("customer")));

        McpTokenVO vo = service.issue(7L);

        Claims claims = jwt.parse(vo.token()).getPayload();
        assertEquals("mcp", claims.get("scope", String.class));
        assertEquals("7", claims.getSubject());
        assertTrue(vo.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(29))));
        ArgumentCaptor<String> jti = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq("auth:mcp:7"), jti.capture(), eq(Duration.ofDays(30)));
        assertEquals(claims.getId(), jti.getValue());
    }

    @Test
    void statusNeverReturnsTheTokenItself() {
        when(redis.getExpire("auth:mcp:7", TimeUnit.SECONDS)).thenReturn(3600L);
        McpTokenVO active = service.status(7L);
        assertTrue(active.active());
        assertNull(active.token());
        assertNotNull(active.expiresAt());

        when(redis.getExpire("auth:mcp:8", TimeUnit.SECONDS)).thenReturn(-2L);
        assertFalse(service.status(8L).active());
    }

    @Test
    void revokeDeletesTheCurrentJti() {
        service.revoke(7L);
        verify(redis).delete("auth:mcp:7");
    }
}
