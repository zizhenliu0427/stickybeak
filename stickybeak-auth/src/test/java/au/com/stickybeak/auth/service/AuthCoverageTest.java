package au.com.stickybeak.auth.service;

import au.com.stickybeak.auth.dto.LoginRequest;
import au.com.stickybeak.auth.dto.RegisterRequest;
import au.com.stickybeak.auth.entity.Role;
import au.com.stickybeak.auth.entity.User;
import au.com.stickybeak.auth.entity.UserRole;
import au.com.stickybeak.auth.mapper.RoleMapper;
import au.com.stickybeak.auth.mapper.UserMapper;
import au.com.stickybeak.auth.mapper.UserRoleMapper;
import au.com.stickybeak.auth.security.JwtTokenProvider;
import au.com.stickybeak.auth.security.TokenBlacklistService;
import au.com.stickybeak.auth.service.impl.AuthServiceImpl;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthCoverageTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("register: Auto-generates nickname from email when nickname is null or blank")
    void testRegisterWithBlankNickname() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hashed_pwd");

        Role role = new Role();
        role.setId(1L);
        role.setCode(Role.CODE_CUSTOMER);
        when(roleMapper.selectOne(any())).thenReturn(role);

        when(jwtTokenProvider.createAccessToken(any(), any(), any())).thenReturn("mock_jwt");
        when(jwtTokenProvider.getAccessTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(any())).thenReturn("mock_refresh");

        RegisterRequest req = new RegisterRequest("aussie_buyer@domain.com", "Secret123!", "   ");
        AuthService.AuthResult result = authService.register(req);

        assertNotNull(result);
        assertEquals("aussie_buyer", result.user().nickname());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(1)).insert(userCaptor.capture());
        assertEquals("aussie_buyer", userCaptor.getValue().getNickname());
    }

    @Test
    @DisplayName("register: Throws SYSTEM_ERROR when default customer role is not seeded")
    void testRegisterRoleNotSeeded() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(roleMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.register(new RegisterRequest("new@test.com", "pass123", "Nick"))
        );
        assertEquals(ResultCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("role not seeded"));
    }

    @Test
    @DisplayName("login: Null email safely throws UNAUTHORIZED without NPE")
    void testLoginNullEmail() {
        LoginRequest req = new LoginRequest(null, "some_password");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("refresh: Throws UNAUTHORIZED if user does not exist or is disabled")
    void testRefreshUserDisabled() {
        RefreshTokenService.Rotation rotation = new RefreshTokenService.Rotation(999L, "new_raw_token");
        when(refreshTokenService.rotate("valid_raw_token")).thenReturn(rotation);

        User disabledUser = new User();
        disabledUser.setId(999L);
        disabledUser.setStatus(User.STATUS_DISABLED);
        when(userMapper.selectById(999L)).thenReturn(disabledUser);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.refresh("valid_raw_token")
        );
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertEquals("account unavailable", ex.getMessage());
    }

    @Test
    @DisplayName("logout: Gracefully handles malformed or expired JWT and revokes refresh token")
    void testLogoutMalformedJwtHandledSilently() {
        when(jwtTokenProvider.parse("bad_token")).thenThrow(new JwtException("Invalid signature"));

        assertDoesNotThrow(() -> authService.logout("bad_token", "raw_refresh_123"));
        verify(tokenBlacklistService, never()).blacklist(any(), any());
        verify(refreshTokenService, times(1)).revoke("raw_refresh_123");
    }

    @Test
    @DisplayName("logout: Successful JWT blacklist calculation and execution")
    void testLogoutSuccess() {
        String access = "valid_access_token";
        String jti = "jwt_id_777";
        Instant exp = Instant.now().plusSeconds(600);

        Jws<Claims> jws = mock(Jws.class);
        Claims claims = mock(Claims.class);
        when(jws.getPayload()).thenReturn(claims);
        when(claims.getId()).thenReturn(jti);
        when(claims.getExpiration()).thenReturn(Date.from(exp));
        when(jwtTokenProvider.parse(access)).thenReturn(jws);

        authService.logout(access, "refresh_to_revoke");

        verify(tokenBlacklistService, times(1)).blacklist(eq(jti), any(Duration.class));
        verify(refreshTokenService, times(1)).revoke("refresh_to_revoke");
    }
}
