package au.com.stickybeak.auth.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.springframework.stereotype.Service;

import au.com.stickybeak.auth.dto.UpdateProfileRequest;
import au.com.stickybeak.auth.entity.Role;
import au.com.stickybeak.auth.entity.User;
import au.com.stickybeak.auth.entity.UserRole;
import au.com.stickybeak.auth.mapper.RoleMapper;
import au.com.stickybeak.auth.mapper.UserMapper;
import au.com.stickybeak.auth.mapper.UserRoleMapper;
import au.com.stickybeak.auth.service.UserService;
import au.com.stickybeak.auth.vo.UserVO;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public UserServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "user not found");
        }
        return toVO(user, loadRoleCodes(userId));
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "user not found");
        }
        if (req.nickname() != null) {
            user.setNickname(req.nickname().trim());
        }
        if (req.phone() != null) {
            user.setPhone(req.phone().trim());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl().trim());
        }
        userMapper.updateById(user);
        return toVO(user, loadRoleCodes(userId));
    }

    @Override
    public List<UserVO> listUsers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime));
        Map<Long, String> roleNameById = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(Role::getId, Role::getCode));
        Map<Long, List<String>> rolesByUser = userRoleMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(UserRole::getUserId,
                        Collectors.mapping(ur -> roleNameById.getOrDefault(ur.getRoleId(), "?"),
                                Collectors.toList())));
        return users.stream()
                .map(u -> toVO(u, rolesByUser.getOrDefault(u.getId(), List.of())))
                .toList();
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
