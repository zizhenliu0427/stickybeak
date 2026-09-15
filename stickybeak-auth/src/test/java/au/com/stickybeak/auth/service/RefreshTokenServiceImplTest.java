package au.com.stickybeak.auth.service;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import au.com.stickybeak.auth.entity.RefreshToken;
import au.com.stickybeak.auth.mapper.RefreshTokenMapper;
import au.com.stickybeak.auth.service.impl.RefreshTokenServiceImpl;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * refresh token 轮换与复用检测（Issue 1.2 核心边界）。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(refreshTokenMapper, 7);
    }

    @Test
    @DisplayName("issue: 落库的是哈希不是明文，7 天过期")
    void issue_persistsHashOnly() {
        String raw = service.issue(1001L);

        assertNotNull(raw);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).insert(captor.capture());
        RefreshToken saved = captor.getValue();
        assertEquals(1001L, saved.getUserId());
        assertNotEquals(raw, saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length()); // SHA-256 hex
        assertEquals(0, saved.getRevoked());
        assertNotNull(saved.getExpiresAt());
    }

    @Test
    @DisplayName("rotate: 有效 token → 吊销旧的 + 签发新的")
    void rotate_valid_rotates() {
        RefreshToken existing = validToken(1L, 1001L);
        when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        RefreshTokenService.Rotation rotation = service.rotate("raw-token");

        assertEquals(1001L, rotation.userId());
        assertNotNull(rotation.newRawToken());
        // 旧 token 被置 revoked=1
        ArgumentCaptor<RefreshToken> updateCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).updateById(updateCaptor.capture());
        assertEquals(1, updateCaptor.getValue().getRevoked());
        // 新 token 落库
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
    }

    @Test
    @DisplayName("rotate: 已吊销 token 再次使用 → 复用检测，吊销该用户全部 + 401")
    void rotate_revokedToken_reuseDetection() {
        RefreshToken existing = validToken(1L, 1001L);
        existing.setRevoked(1);
        when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.rotate("stolen"));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        // revokeAll：按 user_id 批量置 revoked
        verify(refreshTokenMapper).update(any(), any(Wrapper.class));
        // 绝不签发新 token
        verify(refreshTokenMapper, never()).insert(any(RefreshToken.class));
    }

    @Test
    @DisplayName("rotate: 过期 token → 401")
    void rotate_expired_unauthorized() {
        RefreshToken existing = validToken(1L, 1001L);
        existing.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.rotate("expired"));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("rotate: 未知 token → 401")
    void rotate_unknown_unauthorized() {
        when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.rotate("nope"));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    private RefreshToken validToken(Long id, Long userId) {
        RefreshToken token = new RefreshToken();
        token.setId(id);
        token.setUserId(userId);
        token.setTokenHash("hash");
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setRevoked(0);
        return token;
    }
}
