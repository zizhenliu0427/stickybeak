package au.com.stickybeak.auth.vo;

import java.time.Instant;

/**
 * MCP 访问令牌。token 只在签发时返回一次；查询状态时为 null。
 */
public record McpTokenVO(
        boolean active,
        /** 签发时才有：给 Cherry Studio / Cursor 等 MCP 客户端配置的 Bearer 令牌 */
        String token,
        Instant expiresAt
) {
}
