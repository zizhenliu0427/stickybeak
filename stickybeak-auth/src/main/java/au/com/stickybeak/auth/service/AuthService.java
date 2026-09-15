package au.com.stickybeak.auth.service;

import au.com.stickybeak.auth.dto.LoginRequest;
import au.com.stickybeak.auth.dto.RegisterRequest;
import au.com.stickybeak.auth.vo.UserVO;

/**
 * 认证：注册 / 登录 / 刷新 / 登出。
 */
public interface AuthService {

    AuthResult register(RegisterRequest req);

    AuthResult login(LoginRequest req);

    /** 用 refresh token 换新的一对 token（轮换） */
    AuthResult refresh(String rawRefreshToken);

    /** 登出：access 进黑名单，refresh 吊销。两者都可为空。 */
    void logout(String accessToken, String rawRefreshToken);

    /** 认证结果：token 明文 + 用户视图 */
    record AuthResult(String accessToken, String refreshToken, long accessExpiresIn, UserVO user) {
    }
}
