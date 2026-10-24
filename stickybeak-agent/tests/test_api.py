import json
from contextlib import asynccontextmanager

import fakeredis
import httpx
import pytest
from langchain_core.messages import HumanMessage

from stickybeak_agent.config import Settings
from stickybeak_agent.guard import Guard
from stickybeak_agent.llm import ModelProvider
from stickybeak_agent.main import Services, create_app

from .conftest import make_graph, run
from .fakes import FakeJavaApi, call_tool, route_to

THREAD = "7-" + "a" * 32


def parse_sse(text):
    events = []
    for block in text.replace("\r\n", "\n").strip().split("\n\n"):
        lines = dict(line.split(": ", 1) for line in block.split("\n") if ": " in line)
        if "event" in lines:
            events.append((lines["event"], json.loads(lines["data"])))
    return events


@pytest.fixture
def setup():
    @asynccontextmanager
    async def build(responses, settings=None, redis=None):
        settings = settings or Settings()
        api = FakeJavaApi()
        redis = redis or fakeredis.FakeAsyncRedis()
        async with make_graph(api, responses) as (graph, model):
            app = create_app(with_lifespan=False)
            app.state.services = Services(
                settings, graph, ModelProvider(settings, [model]), Guard(redis, settings), api
            )
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://agent") as client:
                yield client, graph, api

    return build


async def test_health_is_public(setup):
    async with setup([]) as (client, _, _):
        resp = await client.get("/agent/health")
        assert resp.status_code == 200 and resp.json()["status"] == "UP"


async def test_chat_requires_gateway_user_header(setup):
    async with setup([]) as (client, _, _):
        resp = await client.post("/agent/chat", json={"message": "hi"})
        assert resp.status_code == 401 and resp.json()["code"] == 401


async def test_chat_streams_sse_events(setup):
    async with setup([route_to("other"), "你好！"]) as (client, _, _):
        resp = await client.post("/agent/chat", json={"message": "你好"}, headers={"X-User-Id": "7"})
        assert resp.headers["content-type"].startswith("text/event-stream")
        events = parse_sse(resp.text)
        assert events[0][0] == "meta" and events[0][1]["threadId"].startswith("7-")
        assert ("token", {"text": "你好！"}) in events and events[-1][0] == "done"


async def test_cannot_use_another_users_thread(setup):
    async with setup([]) as (client, _, _):
        other = "8-" + "b" * 32
        for resp in (
            await client.post("/agent/chat", json={"message": "hi", "threadId": other}, headers={"X-User-Id": "7"}),
            await client.get(f"/agent/threads/{other}", headers={"X-User-Id": "7"}),
            await client.post(f"/agent/chat/{other}/resume", json={"approved": True}, headers={"X-User-Id": "7"}),
        ):
            assert resp.status_code == 404


async def test_message_length_is_limited(setup):
    async with setup([], settings=Settings(max_message_chars=10)) as (client, _, _):
        resp = await client.post("/agent/chat", json={"message": "x" * 11}, headers={"X-User-Id": "7"})
        assert resp.status_code == 400


async def test_rate_limit_returns_429_with_retry_after(setup):
    async with setup([route_to("other"), "1", route_to("other"), "2"], settings=Settings(rate_limit_per_minute=2)) as (
        client,
        _,
        _,
    ):
        for _ in range(2):
            assert (
                await client.post("/agent/chat", json={"message": "hi"}, headers={"X-User-Id": "7"})
            ).status_code == 200
        resp = await client.post("/agent/chat", json={"message": "hi"}, headers={"X-User-Id": "7"})
        assert resp.status_code == 429 and int(resp.headers["Retry-After"]) > 0


async def test_daily_token_quota_counts_actual_usage(setup):
    redis = fakeredis.FakeAsyncRedis()
    async with setup([route_to("other"), "1"], settings=Settings(daily_token_quota=30), redis=redis) as (client, _, _):
        # 一轮对话用了 2 次模型调用，共 30 token（ScriptedChatModel 每次 15）
        assert (await client.post("/agent/chat", json={"message": "hi"}, headers={"X-User-Id": "7"})).status_code == 200
        resp = await client.post("/agent/chat", json={"message": "hi"}, headers={"X-User-Id": "7"})
        assert resp.status_code == 429 and resp.json()["message"] == "daily_token_quota"


async def test_assistant_refuses_when_quota_store_is_down(setup):
    class DownRedis(fakeredis.FakeAsyncRedis):
        def pipeline(self, *args, **kwargs):
            raise __import__("redis").exceptions.ConnectionError("down")

    async with setup([], redis=DownRedis()) as (client, _, _):
        resp = await client.post("/agent/chat", json={"message": "hi"}, headers={"X-User-Id": "7"})
        assert resp.status_code == 503


async def test_history_restores_messages_and_pending_confirmation(setup):
    async with setup([route_to("orders"), call_tool("cancel_order", {"order_no": "SO-PENDING"})]) as (client, graph, _):
        await run(graph, {"messages": [HumanMessage("取消 SO-PENDING")]}, thread_id=THREAD)

        resp = await client.get(f"/agent/threads/{THREAD}", headers={"X-User-Id": "7"})
        body = resp.json()
        assert body["messages"] == [{"role": "user", "text": "取消 SO-PENDING"}]
        assert body["pendingConfirm"]["actions"][0]["orderNo"] == "SO-PENDING"


async def test_resume_without_pending_confirmation_is_conflict(setup):
    async with setup([]) as (client, _, _):
        resp = await client.post(f"/agent/chat/{THREAD}/resume", json={"approved": True}, headers={"X-User-Id": "7"})
        assert resp.status_code == 409


async def test_model_not_configured_returns_friendly_error_event(setup):
    async with setup([]) as (client, _, _):
        client._transport.app.state.services.models = ModelProvider(Settings(), [])
        resp = await client.post("/agent/chat", json={"message": "退货"}, headers={"X-User-Id": "7"})
        events = parse_sse(resp.text)
        assert (
            "error",
            {"message": "助手暂时不可用，请稍后再试。商城的浏览、下单和支付不受影响。", "code": "model_unavailable"},
        ) in events


async def test_assistant_is_unavailable_until_mcp_tools_are_loaded(setup):
    async with setup([]) as (client, _, _):
        services = client._transport.app.state.services
        services.graph = None
        health = (await client.get("/agent/health")).json()
        chat = parse_sse((await client.post("/agent/chat", json={"message": "hi"}, headers={"X-User-Id": "7"})).text)
        history = await client.get(f"/agent/threads/{THREAD}", headers={"X-User-Id": "7"})
    assert health["mcpConnected"] is False
    assert [e for e, _ in chat] == ["meta", "error", "done"]
    assert history.status_code == 503
