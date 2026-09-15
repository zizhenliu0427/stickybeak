package au.com.stickybeak.auth.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * JWT HS256 签发与校验。access token 15 分钟，claims: sub=userId, email, roles, jti。
 * jti 用于登出后的 Redis 黑名单。
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTtlMinutes;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public String createAccessToken(Long userId, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** 验签 + 校验过期，失败抛 JwtException */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    public long getAccessTtlSeconds() {
        return accessTtlMinutes * 60;
    }
}
