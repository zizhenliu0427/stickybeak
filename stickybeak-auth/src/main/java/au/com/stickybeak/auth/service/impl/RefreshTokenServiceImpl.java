package au.com.stickybeak.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import au.com.stickybeak.auth.entity.RefreshToken;
import au.com.stickybeak.auth.mapper.RefreshTokenMapper;
import au.com.stickybeak.auth.service.RefreshTokenService;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenMapper refreshTokenMapper;
    private final long refreshTtlDays;

    public RefreshTokenServiceImpl(RefreshTokenMapper refreshTokenMapper,
                                   @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Override
    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(raw));
        entity.setExpiresAt(LocalDateTime.now().plusDays(refreshTtlDays));
        entity.setRevoked(0);
        refreshTokenMapper.insert(entity);
        return raw;
    }

    @Override
    @Transactional
    public Rotation rotate(String rawToken) {
        RefreshToken existing = findByRaw(rawToken);
        if (existing == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "invalid refresh token");
        }
        if (existing.getRevoked() != null && existing.getRevoked() == 1) {
            // 复用检测：已吊销的 token 再次现身 = 可能被盗，吊销该用户全部会话
            revokeAll(existing.getUserId());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refresh token reuse detected");
        }
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refresh token expired");
        }
        // 轮换：吊销旧的，签发新的
        markRevoked(existing.getId());
        String newRaw = issue(existing.getUserId());
        return new Rotation(existing.getUserId(), newRaw);
    }

    @Override
    public void revoke(String rawToken) {
        RefreshToken existing = findByRaw(rawToken);
        if (existing != null) {
            markRevoked(existing.getId());
        }
    }

    @Override
    public void revokeAll(Long userId) {
        // 用字符串列名 UpdateWrapper：LambdaUpdateWrapper.set 会即时解析列，
        // 依赖 MP 启动期初始化的 lambda 缓存，纯单测下不可用
        refreshTokenMapper.update(null, new UpdateWrapper<RefreshToken>()
                .eq("user_id", userId)
                .eq("revoked", 0)
                .set("revoked", 1));
    }

    private RefreshToken findByRaw(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, hash(rawToken)));
    }

    private void markRevoked(Long id) {
        RefreshToken update = new RefreshToken();
        update.setId(id);
        update.setRevoked(1);
        refreshTokenMapper.updateById(update);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
