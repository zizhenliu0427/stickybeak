"""记录每次模型调用的 token 用量与耗时（Issue 13.6）。

挂在每次对话的 callbacks 上：每次模型调用结束写一行 JSON 日志（logger stickybeak_agent.usage），
对话结束时汇总，用于累加每日 token 额度和返回给前端。
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any
from uuid import UUID

from langchain_core.callbacks import AsyncCallbackHandler
from langchain_core.outputs import LLMResult

log = logging.getLogger("stickybeak_agent.usage")


class UsageTracker(AsyncCallbackHandler):
    def __init__(self, user_id: str, thread_id: str):
        self.user_id = user_id
        self.thread_id = thread_id
        self.calls: list[dict[str, Any]] = []
        self._started: dict[UUID, tuple[float, str | None]] = {}

    async def on_chat_model_start(
        self, serialized: dict, messages: list, *, run_id: UUID, metadata: dict | None = None, **kwargs: Any
    ) -> None:
        self._started[run_id] = (time.perf_counter(), (metadata or {}).get("langgraph_node"))

    async def on_llm_end(self, response: LLMResult, *, run_id: UUID, **kwargs: Any) -> None:
        started, node = self._started.pop(run_id, (time.perf_counter(), None))
        usage: dict[str, int] = {}
        model = None
        for generations in response.generations:
            for generation in generations:
                message = getattr(generation, "message", None)
                if message is None:
                    continue
                usage = dict(getattr(message, "usage_metadata", None) or usage)
                model = message.response_metadata.get("model_name") or model
        self._record(node, model, started, usage, error=None)

    async def on_llm_error(self, error: BaseException, *, run_id: UUID, **kwargs: Any) -> None:
        started, node = self._started.pop(run_id, (time.perf_counter(), None))
        self._record(node, None, started, {}, error=type(error).__name__)

    def _record(self, node: str | None, model: str | None, started: float, usage: dict, error: str | None) -> None:
        call = {
            "user": self.user_id,
            "thread": self.thread_id,
            "node": node,
            "model": model,
            "inputTokens": usage.get("input_tokens", 0),
            "outputTokens": usage.get("output_tokens", 0),
            "latencyMs": round((time.perf_counter() - started) * 1000),
            "error": error,
        }
        self.calls.append(call)
        log.info(json.dumps(call, ensure_ascii=False))

    @property
    def total_tokens(self) -> int:
        return sum(c["inputTokens"] + c["outputTokens"] for c in self.calls)

    def summary(self) -> dict[str, Any]:
        return {
            "modelCalls": len(self.calls),
            "inputTokens": sum(c["inputTokens"] for c in self.calls),
            "outputTokens": sum(c["outputTokens"] for c in self.calls),
            "modelLatencyMs": sum(c["latencyMs"] for c in self.calls),
        }
