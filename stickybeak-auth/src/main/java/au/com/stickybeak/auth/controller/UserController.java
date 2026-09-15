package au.com.stickybeak.auth.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import au.com.stickybeak.auth.dto.UpdateProfileRequest;
import au.com.stickybeak.auth.security.SecurityUtils;
import au.com.stickybeak.auth.service.UserService;
import au.com.stickybeak.auth.vo.UserVO;
import au.com.stickybeak.common.result.Result;

/**
 * 用户资料。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getProfile(SecurityUtils.currentUserId()));
    }

    @PatchMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return Result.ok(userService.updateProfile(SecurityUtils.currentUserId(), req));
    }

    /** 管理端用户列表：服务内角色兜底（Issue 1.3 验收用） */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SYSADMIN')")
    public Result<List<UserVO>> listUsers() {
        return Result.ok(userService.listUsers());
    }
}
