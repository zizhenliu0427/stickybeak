package au.com.stickybeak.common.security;

import org.springframework.util.StringUtils;

import java.util.Base64;

/**
 * 解析 HS256 密钥（auth 签发与网关校验共用），配置错误时启动即失败并给出可操作的提示。
 */
public final class JwtSecrets {

    private static final int MIN_KEY_BYTES = 32;
    private static final String HINT = "generate one with: openssl rand -base64 48";

    private JwtSecrets() {
    }

    public static byte[] decode(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) is not set; " + HINT);
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be Base64 encoded; " + HINT, e);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 256 bits; " + HINT);
        }
        return key;
    }
}
