"""FastAPI 入口。网关把 /api/agent/** 去掉 /api 前缀后转发到这里（/agent/**），并注入 X-User-Id。

只读工具与售后检索来自 MCP Server（stickybeak-mcp），写工具在进程内；对话状态存 MySQL。
"""

from __future__ import annotations

import asyncio
import logging
import re
import uuid
import warnings
from collections.abc import AsyncIterator
from contextlib import AsyncExitStack, asynccontextmanager
from dataclasses import dataclass
from typing import Annotated

import aiomysql
import httpx
from fastapi import APIRouter, Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command
from pydantic import BaseModel, Field
from redis.asyncio import Redis
from redis.exceptions import RedisError
from sse_starlette import EventSourceResponse

from .config import Settings, get_settings
from .discovery import DiscoveryTransport, ServiceResolver
from .graph.builder import GraphDeps, build_graph
from .graph.prompts import MODEL_UNAVAILABLE
from .graph.tools import make_write_tools
from .guard import Guard, QuotaExceeded
from .java_api import JavaApi
from .llm import ModelProvider
from .mcp_client import McpKnowledgeBase, load_mall_tools
from .rag.retriever import detect_lang
from .stream import sse, stream_run
from .usage import UsageTracker

log = logging.getLogger(__name__)

# 一次对话里节点执行次数上限，防止模型反复调用工具绕圈
RECURSION_LIMIT = 25


@dataclass
class Services:
    settings: Settings
    # MCP 工具加载完成前为 None
    graph: object | None
    models: ModelProvider
    guard: Guard
    api: JavaApi | None = None

    @property
    def ready(self) -> bool:
        return self.graph is not None and self.models.available


async def open_checkpointer(settings: Settings, stack: AsyncExitStack) -> BaseCheckpointSaver:
    if settings.checkpoint_backend == "memory":
        return InMemorySaver()
    from langgraph.checkpoint.mysql.aio import AIOMySQLSaver

    if not re.fullmatch(r"\w+", settings.agent_db):
        raise ValueError("AGENT_DB must be a plain identifier")
    server = dict(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        autocommit=True,
    )
    # 已有数据卷不会重跑 init 脚本，这里兜底建库。库表已存在时 MySQL 会返回 warning，属正常情况
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        conn = await aiomysql.connect(**server)
        try:
            async with conn.cursor() as cur:
                await cur.execute(f"CREATE DATABASE IF NOT EXISTS `{settings.agent_db}` DEFAULT CHARACTER SET utf8mb4")
        finally:
            conn.close()
    pool = await aiomysql.create_pool(**server, db=settings.agent_db, minsize=1, maxsize=10)

    async def close_pool() -> None:
        pool.close()
        await pool.wait_closed()

    stack.push_async_callback(close_pool)
    saver = AIOMySQLSaver(pool)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        await saver.setup()
    return saver


def mcp_client_factory(resolver: ServiceResolver):
    """MCP 客户端用的 httpx 工厂：MCP_URL 里的服务名（stickybeak-mcp）每次请求经 Nacos 解析。"""

    def factory(headers=None, timeout=None, auth=None) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            headers=headers, timeout=timeout, auth=auth, follow_redirects=True, transport=DiscoveryTransport(resolver)
        )

    return factory


async def connect_mcp(app: FastAPI, deps_factory, checkpointer, settings: Settings) -> None:
    """从 MCP Server 加载只读工具后再建图；MCP 服务还没起来时每隔几秒重试，期间助手返回"暂不可用"。"""
    delay = 2
    while True:
        try:
            mall_tools = await deps_factory()
        except Exception as e:  # 连接失败、MCP 服务未就绪
            log.warning("MCP server not ready (%s), retrying in %ss", e, delay)
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30)
            continue
        svc: Services = app.state.services
        svc.graph = build_graph(
            GraphDeps(
                svc.models,
                {**mall_tools, **make_write_tools(svc.api)},
                svc.api,
                McpKnowledgeBase(mall_tools["search_faq"]),
            ),
            checkpointer,
        )
        log.info("loaded %d tools from %s", len(mall_tools), settings.mcp_url)
        return


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    async with AsyncExitStack() as stack:
        resolver = ServiceResolver(settings)
        await resolver.start()
        stack.push_async_callback(resolver.stop)

        http = httpx.AsyncClient(timeout=settings.downstream_timeout_seconds)
        stack.push_async_callback(http.aclose)
        redis = Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            password=settings.redis_password or None,
            socket_timeout=3,
        )
        stack.push_async_callback(redis.aclose)

        api = JavaApi(resolver, http)
        models = ModelProvider(settings)
        checkpointer = await open_checkpointer(settings, stack)
        app.state.services = Services(settings, None, models, Guard(redis, settings), api)
        task = asyncio.create_task(
            connect_mcp(
                app,
                lambda: load_mall_tools(settings.mcp_url, mcp_client_factory(resolver)),
                checkpointer,
                settings,
            )
        )
        stack.callback(task.cancel)
        log.info("agent started, models=%s", models.model_names)
        yield


def services(request: Request) -> Services:
    return request.app.state.services


def current_user(x_user_id: Annotated[str | None, Header()] = None) -> str:
    # 网关已校验登录并注入 X-User-Id，且会剥离外部请求自带的同名头
    if not x_user_id or not x_user_id.isdigit():
        raise HTTPException(status_code=401, detail="login required")
    return x_user_id


def check_thread(user_id: str, thread_id: str) -> None:
    # thread_id 由服务端生成，前缀是用户 ID：别人的对话一律当作不存在
    if not re.fullmatch(rf"{user_id}-[0-9a-f]{{32}}", thread_id):
        raise HTTPException(status_code=404, detail="conversation not found")


UserId = Annotated[str, Depends(current_user)]
Svc = Annotated[Services, Depends(services)]


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)
    thread_id: str | None = Field(default=None, alias="threadId")


class ResumeRequest(BaseModel):
    approved: bool


router = APIRouter(prefix="/agent")


@router.get("/health")
async def health(svc: Svc) -> dict:
    return {
        "status": "UP",
        "modelConfigured": svc.models.available,
        "models": svc.models.model_names,
        "mcpConnected": svc.graph is not None,
    }


def require_graph(svc: Services):
    if svc.graph is None:
        raise HTTPException(status_code=503, detail="assistant unavailable")
    return svc.graph


def _config(svc: Services, user_id: str, thread_id: str, usage: UsageTracker) -> dict:
    return {
        "configurable": {"thread_id": thread_id, "user_id": user_id},
        "callbacks": [usage],
        "recursion_limit": RECURSION_LIMIT,
    }


async def _guard(svc: Services, user_id: str) -> None:
    try:
        await svc.guard.check(user_id)
    except QuotaExceeded as e:
        raise HTTPException(status_code=429, detail=e.reason, headers={"Retry-After": str(e.retry_after)}) from e
    except RedisError as e:
        log.error("quota store unavailable: %s", e)
        raise HTTPException(status_code=503, detail="assistant unavailable") from e


def _respond(svc: Services, user_id: str, graph_input, thread_id: str, lang_hint: str) -> EventSourceResponse:
    usage = UsageTracker(user_id, thread_id)

    async def events():
        if not svc.ready:
            yield sse("meta", {"threadId": thread_id})
            yield sse("error", {"message": MODEL_UNAVAILABLE[detect_lang(lang_hint)], "code": "model_unavailable"})
            yield sse("done", {"usage": usage.summary()})
            return
        async for event in stream_run(
            svc.graph, graph_input, _config(svc, user_id, thread_id, usage), usage, lang_hint
        ):
            yield event
        try:
            await svc.guard.add_tokens(user_id, usage.total_tokens)
        except RedisError as e:
            log.error("failed to record token usage for %s: %s", user_id, e)

    # X-Accel-Buffering 让 nginx 不缓冲这个响应
    return EventSourceResponse(events(), ping=15, headers={"X-Accel-Buffering": "no", "Cache-Control": "no-cache"})


@router.post("/chat")
async def chat(body: ChatRequest, user_id: UserId, svc: Svc):
    message = body.message.strip()
    if not message or len(message) > svc.settings.max_message_chars:
        raise HTTPException(status_code=400, detail=f"message must be 1-{svc.settings.max_message_chars} characters")
    thread_id = body.thread_id or f"{user_id}-{uuid.uuid4().hex}"
    check_thread(user_id, thread_id)
    await _guard(svc, user_id)
    return _respond(svc, user_id, {"messages": [HumanMessage(message)]}, thread_id, message)


@router.post("/chat/{thread_id}/resume")
async def resume(thread_id: str, body: ResumeRequest, user_id: UserId, svc: Svc):
    check_thread(user_id, thread_id)
    state = await require_graph(svc).aget_state({"configurable": {"thread_id": thread_id}})
    if not state.interrupts:
        raise HTTPException(status_code=409, detail="nothing to confirm")
    await _guard(svc, user_id)
    last_question = next(
        (m.text for m in reversed(state.values.get("messages", [])) if isinstance(m, HumanMessage)), ""
    )
    return _respond(svc, user_id, Command(resume={"approved": body.approved}), thread_id, last_question)


@router.get("/threads/{thread_id}")
async def history(thread_id: str, user_id: UserId, svc: Svc) -> dict:
    """刷新页面后恢复对话：文字消息、引用来源，以及仍在等待确认的操作。"""
    check_thread(user_id, thread_id)
    state = await require_graph(svc).aget_state({"configurable": {"thread_id": thread_id}})
    messages = []
    for m in state.values.get("messages", []):
        if isinstance(m, HumanMessage):
            messages.append({"role": "user", "text": m.text})
        elif isinstance(m, AIMessage) and m.text and not m.tool_calls:
            messages.append({"role": "assistant", "text": m.text, "sources": m.additional_kwargs.get("sources", [])})
    pending = state.interrupts[0].value if state.interrupts else None
    return {"threadId": thread_id, "messages": messages, "pendingConfirm": pending}


def create_app(with_lifespan: bool = True) -> FastAPI:
    app = FastAPI(
        title="StickyBeak Agent",
        lifespan=lifespan if with_lifespan else None,
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    app.include_router(router)

    @app.exception_handler(HTTPException)
    async def http_error(request: Request, exc: HTTPException) -> JSONResponse:
        # 与 Java 服务一致的返回体 {code, message, data}
        return JSONResponse(
            status_code=exc.status_code,
            headers=exc.headers,
            content={"code": exc.status_code, "message": exc.detail, "data": None},
        )

    return app


app = create_app()
