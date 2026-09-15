package au.com.stickybeak.auth.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * access token 黑名单（登出后剩余有效期内拒绝）。key: auth:blacklist:{jti}
 */
@Service
public class TokenBlacklistService {

    public static final String KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redis;

    public TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String jti, Duration ttl) {
        if (jti == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
