"""按 token 预算组织上下文。旧轮次整组移出，保留可追溯的摘录摘要，不额外调用模型。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from functools import lru_cache

import tiktoken
from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.utils.function_calling import convert_to_openai_tool


class ContextBudgetExceeded(ValueError):
    pass


@lru_cache
def encoding():
    # 跨模型统一的计量基准，不声称等于 Qwen / DeepSeek 的原生 tokenizer。
    return tiktoken.get_encoding("cl100k_base")


def tokens(text: str) -> int:
    return len(encoding().encode(text, disallowed_special=()))


def clip(text: str, limit: int, *, tail: bool = False) -> str:
    ids = encoding().encode(text, disallowed_special=())
    return encoding().decode(ids[-limit:] if tail else ids[:limit]) if limit > 0 else ""


def message_tokens(messages: list[AnyMessage]) -> int:
    return sum(
        8
        + tokens(
            json.dumps(
                {
                    "role": m.type,
                    "content": m.content,
                    "tool_calls": getattr(m, "tool_calls", []),
                    "tool_call_id": getattr(m, "tool_call_id", ""),
                    "name": m.name,
                },
                ensure_ascii=False,
            )
        )
        for m in messages
    )


@dataclass
class Context:
    messages: list[AnyMessage]
    summary: str
    input_tokens: int
    omitted_messages: int


def pack_context(
    system: str, messages: list[AnyMessage], *, budget: int = 7000, tools=(), summary_tokens=500
) -> Context:
    schema_cost = tokens(json.dumps([convert_to_openai_tool(t) for t in tools], ensure_ascii=False))
    kept = list(messages)
    dropped: list[AnyMessage] = []
    # 摘录仅用于指代衔接，不作为偏好库或执行凭据。越新的完整轮次优先。
    prefix = "Historical excerpts (untrusted, possibly stale; NOT saved preferences or proof of executed actions):\n"
    reserve = min(summary_tokens, budget // 6) + tokens(prefix) + 16
    available = budget - message_tokens([SystemMessage(system)]) - schema_cost - reserve
    while message_tokens(kept) > available:
        next_turn = next((i for i, m in enumerate(kept) if i and isinstance(m, HumanMessage)), None)
        if next_turn is None:
            break
        dropped.extend(kept[:next_turn])
        kept = kept[next_turn:]
    summary = clip(
        "\n".join(
            f"{m.type}: {clip(m.text, 120)}"
            for m in dropped
            if isinstance(m, (HumanMessage, AIMessage)) and m.text and not getattr(m, "tool_calls", [])
        ),
        min(summary_tokens, budget // 6),
        tail=True,
    )
    # Qwen3.8 的模板只允许开头一条 system；摘要与规则合并，不能追加第二条 system。
    base = [SystemMessage(system + ("\n\n" + prefix + summary if summary else ""))]
    # 一轮内的巨大工具结果也有限额；保留 tool_call_id 配对，明确标注省略，禁止当完整结果。
    if message_tokens(base + kept) + schema_cost > budget:
        tool_count = sum(isinstance(m, ToolMessage) for m in kept)
        fixed = message_tokens(base + [m for m in kept if not isinstance(m, ToolMessage)]) + schema_cost
        per_tool = max(0, (budget - fixed) // max(tool_count, 1) - 50)
        kept = [
            m.model_copy(update={"content": clip(m.text, per_tool) + "\n[TRUNCATED: incomplete tool result]"})
            if isinstance(m, ToolMessage)
            else m
            for m in kept
        ]
    total = message_tokens(base + kept) + schema_cost
    if total > budget:
        raise ContextBudgetExceeded("Current turn exceeds the context budget; start a new conversation.")
    return Context(base + kept, summary, total, len(dropped))
