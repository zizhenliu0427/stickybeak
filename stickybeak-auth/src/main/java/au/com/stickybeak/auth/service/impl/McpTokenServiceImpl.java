package au.com.stickybeak.auth.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import au.com.stickybeak.auth.security.JwtTokenProvider;
import au.com.stickybeak.auth.service.McpTokenService;
import au.com.stickybeak.auth.service.UserService;
import au.com.stickybeak.auth.vo.McpTokenVO;
import au.com.stickybeak.auth.vo.UserVO;

@Service
public class McpTokenServiceImpl implements McpTokenService {

    /** auth:mcp:{userId} = 当前有效的 MCP 令牌 jti；网关按它判断令牌是否被撤销或已被新令牌替换 */
    public static final String KEY_PREFIX = "auth:mcp:";

    private final StringRedisTemplate redis;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final Duration ttl;

    public McpTokenServiceImpl(StringRedisTemplate redis, JwtTokenProvider jwtTokenProvider, UserService userService,
                               @Value("${app.jwt.mcp-ttl-days:30}") long ttlDays) {
        this.redis = redis;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.ttl = Duration.ofDays(ttlDays);
    }

    @Override
    public McpTokenVO issue(Long userId) {
        UserVO user = userService.getProfile(userId);
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(ttl);
        String token = jwtTokenProvider.createMcpToken(userId, user.email(), user.roles(), jti, expiresAt);
        // 覆盖旧值 = 旧令牌立即失效
        redis.opsForValue().set(KEY_PREFIX + userId, jti, ttl);
        return new McpTokenVO(true, token, expiresAt);
    }

    @Override
    public McpTokenVO status(Long userId) {
        Long seconds = redis.getExpire(KEY_PREFIX + userId, TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0) {
            return new McpTokenVO(false, null, null);
        }
        return new McpTokenVO(true, null, Instant.now().plusSeconds(seconds));
    }

    @Override
    public void revoke(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }
}
