"""把图的执行过程转换成 SSE 事件（Issue 13.5）。

事件：
  meta     {threadId}                          开始
  token    {text}                              回答的增量文字
  tool     {name}                              正在调用的工具（前端显示"正在查询订单…"）
  sources  {items: [...]}                      售后回答引用的资料
  confirm  {actions: [...]}                    写操作等待用户确认（图已暂停）
  error    {message}                           模型不可用等
  done     {usage, firstTokenMs, totalMs}      结束
"""

from __future__ import annotations

import json
import logging
import time
from collections.abc import AsyncIterator
from typing import Any

from langchain_core.messages import AIMessage, ToolMessage

from .context import ContextBudgetExceeded
from .graph.builder import ANSWER_NODES
from .graph.prompts import MODEL_UNAVAILABLE
from .llm import MODEL_UNAVAILABLE_ERRORS, ModelUnavailableError
from .rag.retriever import detect_lang
from .usage import UsageTracker

log = logging.getLogger(__name__)


def sse(event: str, data: dict[str, Any]) -> dict[str, str]:
    return {"event": event, "data": json.dumps(data, ensure_ascii=False, default=str)}


async def stream_run(
    graph, graph_input: Any, config: dict, usage: UsageTracker, lang_hint: str = ""
) -> AsyncIterator[dict[str, str]]:
    started = time.perf_counter()
    first_token_ms: int | None = None
    streamed_nodes: set[str] = set()
    yield sse("meta", {"threadId": config["configurable"]["thread_id"]})
    try:
        async for mode, chunk in graph.astream(graph_input, config, stream_mode=["messages", "updates"]):
            if mode == "messages":
                message, metadata = chunk
                node = metadata.get("langgraph_node")
                if node in ANSWER_NODES and isinstance(message, AIMessage) and message.text and not message.tool_calls:
                    if first_token_ms is None:
                        first_token_ms = round((time.perf_counter() - started) * 1000)
                    streamed_nodes.add(node)
                    yield sse("token", {"text": message.text})
                continue

            for node, update in chunk.items():
                if node == "__interrupt__":
                    for item in update:
                        yield sse("confirm", item.value)
                    continue
                if not isinstance(update, dict):
                    continue
                for message in update.get("messages", []):
                    if isinstance(message, AIMessage):
                        for call in message.tool_calls:
                            yield sse("tool", {"name": call["name"]})
                        # 不经过模型的回答（例如知识库没覆盖时的固定答复）不会产生 token 事件，这里整段补发
                        if (
                            node in ANSWER_NODES
                            and node not in streamed_nodes
                            and message.text
                            and not message.tool_calls
                        ):
                            if first_token_ms is None:
                                first_token_ms = round((time.perf_counter() - started) * 1000)
                            yield sse("token", {"text": message.text})
                    elif isinstance(message, ToolMessage) and message.status == "error":
                        log.warning("tool %s failed: %s", message.name, message.text[:200])
                if update.get("sources"):
                    yield sse("sources", {"items": update["sources"]})
    except ContextBudgetExceeded:
        text = (
            "这轮对话超出上下文预算，请开启新对话后缩小查询范围。"
            if detect_lang(lang_hint) == "zh"
            else "This turn exceeds the context budget. Start a new chat with a narrower request."
        )
        yield sse("error", {"message": text, "code": "context_budget"})
    except (ModelUnavailableError, *MODEL_UNAVAILABLE_ERRORS) as e:
        log.warning("model unavailable: %s", e)
        yield sse("error", {"message": MODEL_UNAVAILABLE[detect_lang(lang_hint)], "code": "model_unavailable"})
    except Exception:
        log.exception("chat run failed")
        yield sse("error", {"message": MODEL_UNAVAILABLE[detect_lang(lang_hint)], "code": "internal_error"})
    total_ms = round((time.perf_counter() - started) * 1000)
    log.info(
        "chat thread=%s firstTokenMs=%s totalMs=%s usage=%s",
        config["configurable"]["thread_id"],
        first_token_ms,
        total_ms,
        usage.summary(),
    )
    yield sse("done", {"usage": usage.summary(), "firstTokenMs": first_token_ms, "totalMs": total_ms})
