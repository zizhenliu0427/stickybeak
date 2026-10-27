import json

import httpx
import pytest
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from stickybeak_agent.config import Settings
from stickybeak_agent.context import ContextBudgetExceeded, message_tokens, pack_context, tokens
from stickybeak_agent.main import Services, create_app
from stickybeak_agent.memory import InMemoryPreferences, Preferences

from .conftest import make_graph, run
from .fakes import call_tool, route_to


async def test_preferences_cross_threads_correction_forgetting_and_isolation(api):
    store = InMemoryPreferences()
    await store.put("7", Preferences(category="bird", max_price_aud=20))
    async with make_graph(api, [route_to("shopping"), "a"] * 5, preferences=store) as (graph, model):
        await run(graph, {"messages": [HumanMessage("推荐")]}, thread_id="7-first")
        await run(graph, {"messages": [HumanMessage("推荐")]}, thread_id="7-second")
        assert all('"category":"bird"' in model.calls[i][0].text for i in (1, 3))
        await store.put("7", Preferences(category="train", max_price_aud=10))
        await run(graph, {"messages": [HumanMessage("再推荐")]}, thread_id="7-first")
        assert '"category":"train"' in model.calls[5][0].text
        assert '"category":"bird"' not in model.calls[5][0].text
        await run(graph, {"messages": [HumanMessage("推荐")]}, user_id="8", thread_id="8-first")
        assert "No saved preferences" in model.calls[7][0].text
        await store.delete("7")
        await run(graph, {"messages": [HumanMessage("推荐")]}, thread_id="7-first")
        assert "No saved preferences" in model.calls[9][0].text
        state = await graph.aget_state({"configurable": {"thread_id": "7-first"}})
        assert "preferences" not in state.values


async def test_expired_memory_is_not_returned(monkeypatch):
    monkeypatch.setattr("stickybeak_agent.memory.time.time", lambda: 1000)
    store = InMemoryPreferences(ttl_days=1)
    await store.put("7", Preferences(category="bird"))
    monkeypatch.setattr("stickybeak_agent.memory.time.time", lambda: 87400)
    assert await store.get("7") is None


async def test_preferences_api_auth_validation_and_replace():
    app = create_app(with_lifespan=False)
    app.state.services = Services(Settings(), None, None, None, preferences=InMemoryPreferences())
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://agent") as client:
        assert (await client.get("/agent/preferences")).status_code == 401
        headers = {"X-User-Id": "7"}
        assert (
            await client.put("/agent/preferences", headers=headers, json={"category": "bird", "user_id": "8"})
        ).status_code == 422
        assert (await client.put("/agent/preferences", headers=headers, json={"max_price_aud": -1})).status_code == 422
        await client.put("/agent/preferences", headers=headers, json={"category": "bird", "max_price_aud": 20})
        result = await client.put("/agent/preferences", headers=headers, json={"category": "train"})
        assert result.json()["memory"]["preferences"] == {"category": "train", "max_price_aud": None}
        assert (await client.get("/agent/preferences", headers={"X-User-Id": "8"})).json()["memory"] is None
        await client.delete("/agent/preferences", headers=headers)
        assert (await client.get("/agent/preferences", headers=headers)).json()["memory"] is None


def test_budget_removes_whole_turns_and_keeps_tools_paired():
    messages = [HumanMessage("old " * 2000), AIMessage("previous answer")]
    messages += [
        HumanMessage("find a bird"),
        call_tool("search_products", {}, "c1"),
        ToolMessage(content="data " * 3000, tool_call_id="c1"),
    ]
    context = pack_context("Rules", messages, budget=700)
    assert context.input_tokens <= 700
    assert context.omitted_messages == 2
    assert "previous answer" in context.summary
    # Qwen3.8 / vLLM 对第二条 system 会返回 400，历史摘录必须留在首条消息。
    assert sum(isinstance(m, SystemMessage) for m in context.messages) == 1
    assert "Historical excerpts" in context.messages[0].text
    assert "find a bird" in [m.text for m in context.messages]
    assert len([m for m in context.messages if isinstance(m, ToolMessage) and m.tool_call_id == "c1"]) == 1
    assert "TRUNCATED" in context.messages[-1].text
    assert message_tokens(context.messages) <= 700


def test_does_not_silently_cut_current_question_or_tool_arguments():
    with pytest.raises(ContextBudgetExceeded):
        pack_context("Rules", [HumanMessage("question " * 3000)], budget=500)


async def test_summary_is_checkpointed_but_internal_context_is_not_streamed(api):
    async with make_graph(api, [route_to("other"), "done"], settings=Settings(context_input_tokens=2500)) as (graph, _):
        events = await run(
            graph, {"messages": [HumanMessage("old " * 4000), AIMessage("old answer"), HumanMessage("hi")]}
        )
        state = await graph.aget_state({"configurable": {"thread_id": "7-thread"}})
        assert "old answer" in state.values["history_summary"]
        assert state.values["context_tokens"] <= 2500
        assert tokens(state.values["history_summary"]) <= 500
        assert "Historical excerpts" not in json.dumps(events)
