import json
from contextlib import asynccontextmanager

import pytest
from langgraph.checkpoint.memory import InMemorySaver

from stickybeak_agent.config import Settings
from stickybeak_agent.graph.builder import GraphDeps, build_graph
from stickybeak_agent.graph.tools import make_write_tools
from stickybeak_agent.llm import ModelProvider
from stickybeak_agent.mcp_client import McpKnowledgeBase, load_mall_tools
from stickybeak_agent.mcp_server import McpDeps, serve_in_process
from stickybeak_agent.stream import stream_run
from stickybeak_agent.usage import UsageTracker

from .fakes import FakeJavaApi, ScriptedChatModel


@pytest.fixture
def api():
    return FakeJavaApi()


@pytest.fixture
def saver():
    return InMemorySaver()


@asynccontextmanager
async def make_graph(api, responses, saver=None, retriever=None, models=None, preferences=None, settings=None):
    """与生产相同的接法：只读工具与售后检索经进程内的 MCP Server 调用，写工具在进程内。"""
    model = ScriptedChatModel(responses=list(responses))
    provider = ModelProvider(Settings(), models if models is not None else [model])
    async with serve_in_process(McpDeps(api=api, retriever=retriever)) as factory:
        mall = await load_mall_tools("http://mcp/mcp", factory)
        deps = GraphDeps(provider, {**mall, **make_write_tools(api)}, api, McpKnowledgeBase(mall["search_faq"]))
        deps.preferences = preferences
        deps.settings = settings
        yield build_graph(deps, saver or InMemorySaver()), model


async def run(graph, graph_input, user_id="7", thread_id="7-thread"):
    """执行一轮，返回 [(event, data), ...]。"""
    usage = UsageTracker(user_id, thread_id)
    config = {"configurable": {"thread_id": thread_id, "user_id": user_id}, "callbacks": [usage]}
    hint = graph_input["messages"][0].text if isinstance(graph_input, dict) else ""
    return [(e["event"], json.loads(e["data"])) async for e in stream_run(graph, graph_input, config, usage, hint)]


def events_of(events, name):
    return [data for event, data in events if event == name]


def answer(events):
    return "".join(d["text"] for d in events_of(events, "token"))
