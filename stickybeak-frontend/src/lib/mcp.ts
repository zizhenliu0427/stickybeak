import { api } from './api';
import type { ApiResult } from './api';

/** MCP 访问令牌（Sprint 14）：给 Cherry Studio、Cursor 等外部 AI 客户端用，只能访问 /api/mcp，30 天有效。 */
export interface McpToken {
  active: boolean;
  /** 只在签发时返回一次 */
  token: string | null;
  expiresAt: string | null;
}

export async function getMcpToken(): Promise<McpToken> {
  const resp = await api.get<ApiResult<McpToken>>('/users/me/mcp-token');
  return resp.data.data;
}

export async function issueMcpToken(): Promise<McpToken> {
  const resp = await api.post<ApiResult<McpToken>>('/users/me/mcp-token');
  return resp.data.data;
}

export async function revokeMcpToken(): Promise<void> {
  await api.delete('/users/me/mcp-token');
}

export function mcpEndpoint(origin = window.location.origin): string {
  return `${origin}/api/mcp`;
}

/** 大多数 MCP 客户端（Cursor、Cherry Studio、Claude Code 等）都接受这种 streamable HTTP + 请求头的写法 */
export function mcpClientConfig(token: string, origin = window.location.origin): string {
  return JSON.stringify(
    {
      mcpServers: {
        stickybeak: {
          type: 'streamableHttp',
          url: mcpEndpoint(origin),
          headers: { Authorization: `Bearer ${token}` },
        },
      },
    },
    null,
    2,
  );
}
