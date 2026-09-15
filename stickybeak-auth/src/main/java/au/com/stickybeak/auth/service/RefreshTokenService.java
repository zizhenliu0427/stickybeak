package au.com.stickybeak.auth.service;

/**
 * 刷新令牌：不透明随机串，DB 只存 SHA-256 哈希；轮换 + 复用检测。
 */
public interface RefreshTokenService {

    /** 签发新 refresh token，返回明文（只此一次），DB 存哈希 */
    String issue(Long userId);

    /**
     * 轮换：校验旧 token 有效 → 吊销旧 → 签发新。
     * 复用检测：已吊销的 token 被再次使用 → 吊销该用户全部 token 并抛 401。
     */
    Rotation rotate(String rawToken);

    /** 登出吊销单个 token（不存在则静默忽略） */
    void revoke(String rawToken);

    /** 吊销某用户全部有效 token */
    void revokeAll(Long userId);

    /** 轮换结果 */
    record Rotation(Long userId, String newRawToken) {
    }
}
