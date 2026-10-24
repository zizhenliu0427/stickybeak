package au.com.stickybeak.auth.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import au.com.stickybeak.common.security.JwtSecrets;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT HS256 签发与校验。access token 15 分钟，claims: sub=userId, email, roles, jti。
 * jti 用于登出后的 Redis 黑名单。
 * MCP 令牌（Sprint 14）多一个 scope=mcp，有效期以天计，网关只允许它访问 /api/mcp。
 */
@Component
public class JwtTokenProvider {

    /** MCP 令牌的 scope：网关看到它就只放行 MCP 端点 */
    public static final String MCP_SCOPE = "mcp";

    private final SecretKey key;
    private final long accessTtlMinutes;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes) {
        this.key = Keys.hmacShaKeyFor(JwtSecrets.decode(secret));
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

    public String createMcpToken(Long userId, String email, List<String> roles, String jti, Instant expiresAt) {
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
                .claim("scope", MCP_SCOPE)
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
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
