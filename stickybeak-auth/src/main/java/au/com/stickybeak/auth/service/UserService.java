package au.com.stickybeak.auth.service;

import java.util.List;

import au.com.stickybeak.auth.dto.UpdateProfileRequest;
import au.com.stickybeak.auth.vo.UserVO;

/**
 * 用户资料。
 */
public interface UserService {

    UserVO getProfile(Long userId);

    UserVO updateProfile(Long userId, UpdateProfileRequest req);

    /** 管理端：用户列表（Issue 1.3 角色兜底验证用） */
    List<UserVO> listUsers();
}
