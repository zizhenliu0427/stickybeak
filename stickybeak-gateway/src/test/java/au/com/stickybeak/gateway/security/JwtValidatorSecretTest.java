package au.com.stickybeak.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密钥配置错误时启动即失败，并提示如何生成。
 */
class JwtValidatorSecretTest {

    @Test
    void rejectsMissingSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtValidator(""));
        assertTrue(ex.getMessage().contains("openssl rand -base64"));
    }

    @Test
    void rejectsNonBase64Secret() {
        // 旧版 .env.prod.example 中的示例值，含 '_' 不是合法 Base64
        assertThrows(IllegalStateException.class,
                () -> new JwtValidator("StickybeakEnterpriseJwtSecretSuperKey2026_AU_SYDNEY_99999"));
    }

    @Test
    void rejectsShortKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new JwtValidator(shortKey));
    }

    @Test
    void acceptsValidKey() {
        String key = Base64.getEncoder().encodeToString(new byte[48]);
        assertDoesNotThrow(() -> new JwtValidator(key));
    }
}
