"""agent 作为 MCP 客户端（Issue 14.3）：商城的只读能力从 MCP Server 加载，不再在进程内定义。

- 用 langchain-mcp-adapters 把 MCP 工具转成 LangChain 工具；每次调用新建一个无状态的 MCP 请求
- 用户身份：拦截器从当前运行配置（configurable.user_id，来自网关的 X-User-Id）取出，写进请求头 X-User-Id。
  工具参数里没有用户 ID，模型和工具结果里的注入内容都改变不了它
- 售后检索也走 MCP 的 search_faq，agent 自己不再连 ES
"""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable

from langchain_core.tools import BaseTool
from langchain_mcp_adapters.interceptors import MCPToolCallRequest
from langchain_mcp_adapters.tools import load_mcp_tools
from langgraph.config import get_config

from .graph.tools import current_user
from .rag.retriever import RetrievalResult

MCP_TOOLS = frozenset({"search_products", "get_product", "list_my_orders", "get_order", "search_faq"})
USER_HEADER = "X-User-Id"


class UserIdentityInterceptor:
    """每次工具调用前，把当前用户写进 X-User-Id；没有登录用户时去掉这个头。"""

    async def __call__(self, request: MCPToolCallRequest, handler: Callable[[MCPToolCallRequest], Awaitable]):
        try:
            user_id = current_user(get_config())
        except RuntimeError:  # 不在图的执行上下文里
            user_id = None
        headers = {k: v for k, v in (request.headers or {}).items() if k.lower() != USER_HEADER.lower()}
        if user_id:
            headers[USER_HEADER] = user_id
        return await handler(request.override(headers=headers))


async def load_mall_tools(
    url: str, client_factory: Callable | None = None, request_timeout: float = 30
) -> dict[str, BaseTool]:
    connection = {"transport": "streamable_http", "url": url, "timeout": request_timeout}
    if client_factory is not None:
        connection["httpx_client_factory"] = client_factory
    tools = await load_mcp_tools(
        None, connection=connection, tool_interceptors=[UserIdentityInterceptor()], server_name="stickybeak"
    )
    loaded = {t.name: t for t in tools}
    missing = MCP_TOOLS - set(loaded)
    if missing:
        raise RuntimeError(f"MCP server is missing tools: {sorted(missing)}")
    return {name: loaded[name] for name in MCP_TOOLS}


class McpKnowledgeBase:
    """把 MCP 的 search_faq 适配成 after_sales 节点用的检索接口。"""

    def __init__(self, tool: BaseTool):
        self._tool = tool

    async def retrieve(self, query: str) -> RetrievalResult:
        message = await self._tool.ainvoke(
            {"type": "tool_call", "name": self._tool.name, "args": {"question": query}, "id": "kb-search"}
        )
        if message.status == "error" or not message.artifact:
            raise RuntimeError(f"search_faq failed: {tool_text(message.content)}")
        return RetrievalResult(passages=message.artifact["structured_content"]["data"]["passages"])


def tool_text(content) -> str:
    """MCP 工具结果是内容块列表；发给模型前拼成纯文本（部分 OpenAI 兼容接口不接受 tool 消息的数组内容）。
    FastMCP 输出的是缩进格式的 JSON，这里压缩成一行，省 token。"""
    if isinstance(content, str):
        return _compact(content)
    return "\n".join(_compact(block.get("text", "")) if isinstance(block, dict) else str(block) for block in content)


def _compact(text: str) -> str:
    try:
        return json.dumps(json.loads(text), ensure_ascii=False, separators=(",", ":"))
    except (json.JSONDecodeError, TypeError):
        return text
