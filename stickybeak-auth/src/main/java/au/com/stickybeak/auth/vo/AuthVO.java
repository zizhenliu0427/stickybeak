package au.com.stickybeak.auth.vo;

/**
 * 登录/注册/刷新响应。token 本体走 HttpOnly Cookie，body 只带用户信息与过期时间。
 */
public record AuthVO(
        UserVO user,
        /** access token 有效期（秒），前端用于展示/调度 */
        long accessExpiresIn
) {
}
