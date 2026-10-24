import httpx
from langchain_core.runnables import RunnableLambda
from langchain_mcp_adapters.interceptors import MCPToolCallRequest

from stickybeak_agent.config import Settings
from stickybeak_agent.discovery import DiscoveryTransport, ServiceResolver
from stickybeak_agent.mcp_client import UserIdentityInterceptor, load_mall_tools, tool_text
from stickybeak_agent.mcp_server import McpDeps, serve_in_process

from .fakes import FakeJavaApi


async def _headers_seen(config=None, headers=None):
    seen = {}

    async def handler(request: MCPToolCallRequest):
        seen.update(request.headers or {})
        return "ok"

    request = MCPToolCallRequest(name="get_order", args={}, server_name="stickybeak", headers=headers)
    if config is None:
        await UserIdentityInterceptor()(request, handler)
    else:
        # 在 Runnable 的执行上下文里调用，get_config() 才能拿到运行配置
        async def call(_):
            return await UserIdentityInterceptor()(request, handler)

        await RunnableLambda(call).ainvoke(None, config)
    return seen


async def test_interceptor_sends_the_signed_in_user():
    seen = await _headers_seen({"configurable": {"user_id": "7"}})
    assert seen == {"X-User-Id": "7"}


async def test_interceptor_drops_any_other_identity_header():
    seen = await _headers_seen({"configurable": {"user_id": "7"}}, headers={"x-user-id": "8"})
    assert seen == {"X-User-Id": "7"}
    assert await _headers_seen(None, headers={"X-User-Id": "8"}) == {}


async def test_loaded_tools_have_no_identity_argument_and_anonymous_calls_cannot_read_orders():
    async with serve_in_process(McpDeps(api=FakeJavaApi())) as factory:
        tools = await load_mall_tools("http://mcp/mcp", factory)
        message = await tools["list_my_orders"].ainvoke(
            {"type": "tool_call", "name": "list_my_orders", "args": {}, "id": "t1"}
        )
    assert set(tools) == {"search_products", "get_product", "list_my_orders", "get_order", "search_faq"}
    assert all("user_id" not in (t.args or {}) for t in tools.values())
    assert message.status == "error" and "login required" in tool_text(message.content)


async def test_discovery_transport_resolves_service_names():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"], seen["host"] = str(request.url), request.headers["host"]
        return httpx.Response(200, json={})

    resolver = ServiceResolver(Settings(service_urls="stickybeak-mcp=http://10.0.0.5:8089"))
    transport = DiscoveryTransport(resolver, inner=httpx.MockTransport(handler))
    async with httpx.AsyncClient(transport=transport) as client:
        await client.post("http://stickybeak-mcp/mcp", json={})
    assert seen == {"url": "http://10.0.0.5:8089/mcp", "host": "10.0.0.5:8089"}


def test_tool_text_joins_content_blocks_and_compacts_json():
    assert tool_text([{"type": "text", "text": "a"}, {"type": "text", "text": "b"}]) == "a\nb"
    assert tool_text("plain") == "plain"
    assert tool_text([{"type": "text", "text": '{\n  "data": {\n    "名": 1\n  }\n}'}]) == '{"data":{"名":1}}'
