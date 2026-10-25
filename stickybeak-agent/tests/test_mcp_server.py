import json

from stickybeak_agent.mcp_server import McpDeps
from stickybeak_agent.rag.retriever import RetrievalResult

from .fakes import FakeJavaApi
from .mcp_support import mcp_session


class StubRetriever:
    async def retrieve(self, query):
        if "比特币" in query:
            return RetrievalResult(passages=[])
        return RetrievalResult(
            passages=[
                {
                    "id": "a",
                    "doc_id": "returns-refunds",
                    "lang": "zh",
                    "source": "zh/r.md",
                    "title": "退换货与退款",
                    "section": "退款多久到账",
                    "content": "银行卡通常 5–10 个工作日到账",
                    "score": 0.91,
                }
            ]
        )


def deps():
    return McpDeps(api=FakeJavaApi(), retriever=StubRetriever())


def payload(result):
    return json.loads(result.content[0].text)


async def test_lists_five_read_only_tools_without_identity_parameters():
    async with mcp_session(deps()) as session:
        tools = {t.name: t for t in (await session.list_tools()).tools}
    assert set(tools) == {"search_products", "get_product", "list_my_orders", "get_order", "search_faq"}
    for tool in tools.values():
        assert tool.annotations.readOnlyHint is True and tool.annotations.destructiveHint is False
        assert not {"user_id", "userId", "ctx"} & set(tool.inputSchema.get("properties", {}))


async def test_public_tools_work_without_login():
    async with mcp_session(deps()) as session:
        result = await session.call_tool("search_products", {"query": "kookaburra"})
    assert not result.isError
    assert payload(result)["data"]["items"][0]["slug"] == "kookaburra-magnet"
    assert result.structuredContent["data"]["total"] == 1


async def test_order_tools_require_login():
    async with mcp_session(deps()) as session:
        result = await session.call_tool("list_my_orders", {})
    assert result.isError and "login required" in result.content[0].text


async def test_order_tools_use_the_gateway_identity():
    async with mcp_session(deps(), user_id="7") as session:
        mine = await session.call_tool("list_my_orders", {})
        other = await session.call_tool("get_order", {"order_no": "SO-OTHER"})
    assert {o["orderNo"] for o in payload(mine)["data"]} == {"SO-PENDING", "SO-PAID"}
    assert other.isError and "not found" in other.content[0].text


async def test_search_faq_returns_cited_passages_or_nothing():
    async with mcp_session(deps()) as session:
        hit = await session.call_tool("search_faq", {"question": "退款多久到账？"})
        miss = await session.call_tool("search_faq", {"question": "支持比特币吗"})
    passage = payload(hit)["data"]["passages"][0]
    assert passage["n"] == 1 and passage["source"] == "zh/r.md" and "5–10" in passage["content"]
    assert payload(miss)["data"]["passages"] == []


class BrokenRetriever:
    async def retrieve(self, query):
        raise ConnectionError("Cannot connect to host es-internal:9200")


async def test_search_faq_does_not_leak_internal_errors():
    async with mcp_session(McpDeps(api=FakeJavaApi(), retriever=BrokenRetriever())) as session:
        result = await session.call_tool("search_faq", {"question": "退款"})
    assert result.isError
    assert "temporarily unavailable" in result.content[0].text and "9200" not in result.content[0].text
