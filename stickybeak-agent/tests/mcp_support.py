"""测试里在进程内跑 MCP Server，用官方客户端直接调用（不开端口）。"""

from contextlib import asynccontextmanager

from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

from stickybeak_agent.mcp_server import McpDeps, serve_in_process


@asynccontextmanager
async def mcp_session(deps: McpDeps, user_id: str | None = None):
    async with serve_in_process(deps) as factory:
        headers = {"X-User-Id": user_id} if user_id else None
        async with (
            factory(headers=headers) as http,
            streamable_http_client("http://mcp/mcp", http_client=http) as (
                r,
                w,
                _,
            ),
        ):
            async with ClientSession(r, w) as session:
                await session.initialize()
                yield session
