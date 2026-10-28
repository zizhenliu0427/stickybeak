package au.com.stickybeak.auth.service;

import au.com.stickybeak.auth.vo.McpTokenVO;

/**
 * 给外部 MCP 客户端用的长期令牌（Sprint 14）：
 * 网页登录的 access token 只有 15 分钟，配进 AI 客户端很快就失效；这里签发一个 30 天、只能访问 /api/mcp 的 JWT。
 * 每个用户同时只有一个有效令牌（当前 jti 存 Redis），重新签发即作废旧令牌，也可以随时撤销。
 */
public interface McpTokenService {

    McpTokenVO issue(Long userId);

    McpTokenVO status(Long userId);

    void revoke(Long userId);
}
