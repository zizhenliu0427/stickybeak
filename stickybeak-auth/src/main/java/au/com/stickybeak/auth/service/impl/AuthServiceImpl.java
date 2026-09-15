package au.com.stickybeak.auth.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import au.com.stickybeak.auth.service.AuthService;
import au.com.stickybeak.auth.service.RefreshTokenService;
import au.com.stickybeak.auth.vo.UserVO;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthServiceImpl(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                           PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider,
                           RefreshTokenService refreshTokenService, TokenBlacklistService tokenBlacklistService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    @Transactional
    public AuthResult register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname() == null || req.nickname().isBlank()
                ? email.substring(0, email.indexOf('@')) : req.nickname().trim());
        user.setStatus(User.STATUS_NORMAL);
        userMapper.insert(user);

        assignRole(user.getId(), Role.CODE_CUSTOMER);
        return buildAuthResult(user, List.of(Role.CODE_CUSTOMER));
    }

    @Override
    public AuthResult login(LoginRequest req) {
        User user = findByEmail(req.email());
        // 邮箱不存在与密码错误返回同一文案，防账号枚举
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "invalid email or password");
        }
        if (user.getStatus() != null && user.getStatus() == User.STATUS_DISABLED) {
            throw new BusinessException(ResultCode.FORBIDDEN, "account disabled");
        }
        List<String> roles = loadRoleCodes(user.getId());
        return buildAuthResult(user, roles);
    }

    @Override
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = userMapper.selectById(rotation.userId());
        if (user == null || (user.getStatus() != null && user.getStatus() == User.STATUS_DISABLED)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "account unavailable");
        }
        List<String> roles = loadRoleCodes(user.getId());
        String access = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), roles);
        return new AuthResult(access, rotation.newRawToken(),
                jwtTokenProvider.getAccessTtlSeconds(), toVO(user, roles));
    }

    @Override
    public void logout(String accessToken, String rawRefreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Jws<Claims> jws = jwtTokenProvider.parse(accessToken);
                String jti = jws.getPayload().getId();
                Instant exp = jws.getPayload().getExpiration().toInstant();
                tokenBlacklistService.blacklist(jti, Duration.between(Instant.now(), exp));
            } catch (JwtException | IllegalArgumentException e) {
                // 过期/非法 access 无需拉黑，静默
            }
        }
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    private AuthResult buildAuthResult(User user, List<String> roles) {
        String access = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), roles);
        String refresh = refreshTokenService.issue(user.getId());
        return new AuthResult(access, refresh, jwtTokenProvider.getAccessTtlSeconds(), toVO(user, roles));
    }

    private User findByEmail(String email) {
        if (email == null) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email.trim().toLowerCase()));
    }

    private void assignRole(Long userId, String roleCode) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode));
        if (role == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "role not seeded: " + roleCode);
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }

    private List<String> loadRoleCodes(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream().map(Role::getCode).toList();
    }

    private UserVO toVO(User user, List<String> roles) {
        return new UserVO(user.getId(), user.getEmail(), user.getNickname(),
                user.getPhone(), user.getAvatarUrl(), roles);
    }
}
