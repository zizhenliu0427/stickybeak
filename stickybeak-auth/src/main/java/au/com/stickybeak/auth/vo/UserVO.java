package au.com.stickybeak.auth.vo;

import java.util.List;

/**
 * 用户视图（出参，不含任何凭证信息）。
 */
public record UserVO(
        Long id,
        String email,
        String nickname,
        String phone,
        String avatarUrl,
        List<String> roles
) {
}
