package au.com.stickybeak.auth.service;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue 1.2 验收：happy path + 边界（重复注册 409 / 错误密码 401 / 禁用 403 / 复用检测 401）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, roleMapper, userRoleMapper, passwordEncoder,
                jwtTokenProvider, refreshTokenService, tokenBlacklistService);
        lenient().when(jwtTokenProvider.getAccessTtlSeconds()).thenReturn(900L);
        lenient().when(jwtTokenProvider.createAccessToken(any(), anyString(), any())).thenReturn("access.jwt");
        lenient().when(refreshTokenService.issue(any())).thenReturn("refresh-opaque");
    }

    // ---------- register ----------

    @Test
    @DisplayName("register: happy path — 加密落库 + 分配 customer 角色 + 发令牌")
    void register_success() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        Role customer = new Role();
        customer.setId(1L);
        customer.setCode(Role.CODE_CUSTOMER);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(customer);

        AuthService.AuthResult result = authService.register(
                new RegisterRequest("NewUser@Example.com", "password123", "  Newbie "));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("newuser@example.com", saved.getEmail());
        assertTrue(passwordEncoder.matches("password123", saved.getPasswordHash()));
        assertEquals("Newbie", saved.getNickname());
        assertEquals(User.STATUS_NORMAL, saved.getStatus());

        ArgumentCaptor<UserRole> urCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(urCaptor.capture());
        assertEquals(1L, urCaptor.getValue().getRoleId());

        assertEquals("access.jwt", result.accessToken());
        assertEquals("refresh-opaque", result.refreshToken());
        assertEquals(List.of(Role.CODE_CUSTOMER), result.user().roles());
    }

    @Test
    @DisplayName("register: 重复邮箱 → 409")
    void register_duplicateEmail_conflict() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.register(new RegisterRequest("dup@example.com", "password123", null)));

        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    // ---------- login ----------

    @Test
    @DisplayName("login: happy path — 返回令牌与用户视图")
    void login_success() {
        User user = activeUser();
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        stubRoles(user.getId(), List.of(Role.CODE_CUSTOMER));

        AuthService.AuthResult result = authService.login(
                new LoginRequest("user@example.com", "password123"));

        assertEquals("access.jwt", result.accessToken());
        assertEquals("user@example.com", result.user().email());
    }

    @Test
    @DisplayName("login: 密码错误 → 401（与邮箱不存在同文案）")
    void login_wrongPassword_unauthorized() {
        User user = activeUser();
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.login(new LoginRequest("user@example.com", "wrong-password")));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertEquals("invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("login: 禁用账号 → 403")
    void login_disabled_forbidden() {
        User user = activeUser();
        user.setStatus(User.STATUS_DISABLED);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.login(new LoginRequest("user@example.com", "password123")));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // ---------- refresh ----------

    @Test
    @DisplayName("refresh: 轮换成功 → 新 access + 新 refresh")
    void refresh_success() {
        User user = activeUser();
        when(refreshTokenService.rotate("old-refresh"))
                .thenReturn(new RefreshTokenService.Rotation(user.getId(), "new-refresh"));
        when(userMapper.selectById(user.getId())).thenReturn(user);
        stubRoles(user.getId(), List.of(Role.CODE_CUSTOMER));

        AuthService.AuthResult result = authService.refresh("old-refresh");

        assertEquals("new-refresh", result.refreshToken());
        assertNotNull(result.accessToken());
    }

    @Test
    @DisplayName("refresh: 复用已吊销 token → 401（复用检测在 RefreshTokenService 内吊销全部）")
    void refresh_reuseDetected_unauthorized() {
        when(refreshTokenService.rotate("stolen-refresh"))
                .thenThrow(new BusinessException(ResultCode.UNAUTHORIZED, "refresh token reuse detected"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.refresh("stolen-refresh"));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    // ---------- logout ----------

    @Test
    @DisplayName("logout: access 进黑名单 + refresh 吊销")
    @SuppressWarnings("unchecked")
    void logout_blacklistAndRevoke() {
        Claims claims = io.jsonwebtoken.Jwts.claims()
                .id("jti-123")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .build();
        Jws<Claims> jws = org.mockito.Mockito.mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtTokenProvider.parse("access.jwt")).thenReturn(jws);

        authService.logout("access.jwt", "refresh-opaque");

        verify(tokenBlacklistService).blacklist(eq("jti-123"), any(java.time.Duration.class));
        verify(refreshTokenService).revoke("refresh-opaque");
    }

    // ---------- helpers ----------

    private User activeUser() {
        User user = new User();
        user.setId(1001L);
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setNickname("user");
        user.setStatus(User.STATUS_NORMAL);
        return user;
    }

    private void stubRoles(Long userId, List<String> codes) {
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(1L);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur));
        Role role = new Role();
        role.setId(1L);
        role.setCode(codes.get(0));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role));
    }
}
