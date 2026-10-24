"""LangGraph 状态图（Issue 13.3 / 13.4）。

    START → route ─┬─ shopping ⇄ shopping_tools        （search_products / get_product / add_to_cart）
                   ├─ orders   ⇄ order_tools           （list_my_orders / get_order / cancel_order）
                   ├─ after_sales（RAG，带引用）→ END
                   └─ other → END
    shopping / orders 产生写工具调用时先进 confirm：interrupt 暂停，前端确认后恢复；拒绝则不执行，告诉模型用户取消了

对话状态由 checkpointer 按 thread_id 持久化，刷新页面后可继续，也能恢复等待确认的卡片。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Literal

from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, SystemMessage, ToolMessage, trim_messages
from langchain_core.runnables import RunnableConfig
from langchain_core.tools import BaseTool
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph import END, START, MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from langgraph.types import Command, interrupt
from pydantic import BaseModel, Field

from ..java_api import ApiError, JavaApi
from ..llm import ModelProvider
from ..rag.retriever import HybridRetriever, detect_lang
from . import prompts
from .tools import ORDER_TOOLS, SHOPPING_TOOLS, WRITE_TOOLS, as_error, current_user

log = logging.getLogger(__name__)

Intent = Literal["shopping", "orders", "after_sales", "other"]
# 会把回答逐字流给前端的节点（route 的结构化输出不展示）
ANSWER_NODES = frozenset({"shopping", "orders", "after_sales", "other"})
# 带进模型的历史条数上限（按消息条数截断，且从用户消息开始，避免截出没有配对的工具消息）。
# 要大于一轮对话最多产生的消息数（递归上限 25 步，约 25 条），否则会把本轮的用户消息也截掉
HISTORY_MESSAGES = 40


class AgentState(MessagesState):
    intent: str
    # 最近一次售后回答引用的资料，流式输出时推给前端
    sources: list[dict]


class RouteDecision(BaseModel):
    """The intent of the user's latest message."""

    intent: Intent = Field(description="one of shopping, orders, after_sales, other")


@dataclass
class GraphDeps:
    models: ModelProvider
    tools: dict[str, BaseTool]
    api: JavaApi
    retriever: HybridRetriever | None


def latest_user_text(messages: list[AnyMessage]) -> str:
    for message in reversed(messages):
        if isinstance(message, HumanMessage):
            return message.text
    return ""


def dialogue(messages: list[AnyMessage], limit: int) -> list[AnyMessage]:
    """只保留用户消息与模型的文字回答（去掉工具调用与工具结果），用于不需要工具上下文的节点。"""
    kept = [
        m for m in messages if isinstance(m, HumanMessage) or (isinstance(m, AIMessage) and m.text and not m.tool_calls)
    ]
    kept = kept[-limit:]
    while kept and not isinstance(kept[0], HumanMessage):
        kept = kept[1:]
    return kept


def repair_tool_calls(messages: list[AnyMessage]) -> list[AnyMessage]:
    """等待确认时用户没点确认、直接发了新消息：上一条工具调用没有结果，补一条"未执行"，否则模型接口会报错。"""
    answered = {m.tool_call_id for m in messages if isinstance(m, ToolMessage)}
    repaired: list[AnyMessage] = []
    for message in messages:
        repaired.append(message)
        if isinstance(message, AIMessage):
            for call in message.tool_calls:
                if call["id"] not in answered:
                    repaired.append(
                        ToolMessage(
                            content=as_error("not executed: the user did not confirm"),
                            tool_call_id=call["id"],
                            name=call["name"],
                        )
                    )
    return repaired


def with_tools_history(messages: list[AnyMessage]) -> list[AnyMessage]:
    return trim_messages(
        repair_tool_calls(messages),
        max_tokens=HISTORY_MESSAGES,
        token_counter=len,
        strategy="last",
        start_on="human",
        include_system=False,
    )


def retrieval_query(messages: list[AnyMessage]) -> str:
    """多轮追问（"那运费呢？"）单独拿去检索会丢上下文：太短时拼上上一个问题。"""
    questions = [m.text for m in messages if isinstance(m, HumanMessage)]
    if not questions:
        return ""
    if len(questions) >= 2 and len(questions[-1]) <= 15:
        return f"{questions[-2]} {questions[-1]}"
    return questions[-1]


def format_documents(passages: list[dict]) -> str:
    blocks = []
    for i, p in enumerate(passages, start=1):
        # 资料原文不能闭合外层的 <documents> 标签
        content = p["content"].replace("<documents>", "").replace("</documents>", "")
        blocks.append(f"[{i}] {p['title']} - {p['section']}\n{content}")
    return "\n\n".join(blocks)


def build_graph(deps: GraphDeps, checkpointer: BaseCheckpointSaver | None = None):
    shopping_tools = [deps.tools[n] for n in SHOPPING_TOOLS]
    order_tools = [deps.tools[n] for n in ORDER_TOOLS]

    async def route(state: AgentState, config: RunnableConfig) -> dict:
        transcript = "\n".join(
            f"{'user' if isinstance(m, HumanMessage) else 'assistant'}: {m.text[:300]}"
            for m in dialogue(state["messages"], 6)
        )
        decision = await deps.models.structured(RouteDecision).ainvoke(
            [
                SystemMessage(prompts.ROUTER),
                HumanMessage(f"Conversation:\n{transcript}\n\nClassify the last user message."),
            ],
            config,
        )
        return {"intent": decision.intent, "sources": []}

    def agent(system_prompt: str, tools: list[BaseTool]):
        async def node(state: AgentState, config: RunnableConfig) -> dict:
            response = await deps.models.chat(tools).ainvoke(
                [SystemMessage(system_prompt), *with_tools_history(state["messages"])], config
            )
            return {"messages": [response]}

        return node

    def after_agent(tools_node: str):
        def decide(state: AgentState) -> str:
            last = state["messages"][-1]
            if not isinstance(last, AIMessage) or not last.tool_calls:
                return END
            if any(call["name"] in WRITE_TOOLS for call in last.tool_calls):
                return "confirm"
            return tools_node

        return decide

    async def describe(call: dict, user_id: str | None) -> dict:
        """确认卡片上展示的内容：从业务服务查出真实商品 / 订单信息，而不是用模型给的描述。"""
        args = call["args"]
        action = {"id": call["id"], "tool": call["name"]}
        try:
            if call["name"] == "add_to_cart":
                product = await deps.api.get_product(str(args.get("product_id")))
                action.update(
                    productId=product["id"],
                    name=product["name"],
                    priceAud=product["priceAud"],
                    qty=args.get("qty", 1),
                    image=product.get("image"),
                )
            elif call["name"] == "cancel_order" and user_id is not None:
                order = await deps.api.get_order(user_id, str(args.get("order_no")))
                action.update(
                    orderNo=order["orderNo"],
                    status=order["status"],
                    payAmount=order["payAmount"],
                    currency=order["currency"],
                )
            else:
                action.update(args)
        except ApiError:
            action.update(args, notFound=True)
        return action

    async def confirm(state: AgentState, config: RunnableConfig) -> Command:
        last = state["messages"][-1]
        actions = [
            await describe(call, current_user(config)) for call in last.tool_calls if call["name"] in WRITE_TOOLS
        ]
        decision = interrupt({"type": "confirm", "actions": actions})
        agent_node, tools_node = (
            ("shopping", "shopping_tools") if state["intent"] == "shopping" else ("orders", "order_tools")
        )
        if isinstance(decision, dict) and decision.get("approved") is True:
            return Command(goto=tools_node)
        declined = [
            ToolMessage(
                content=as_error("the user declined; nothing was executed"), tool_call_id=call["id"], name=call["name"]
            )
            for call in last.tool_calls
        ]
        return Command(goto=agent_node, update={"messages": declined})

    async def after_sales(state: AgentState, config: RunnableConfig) -> dict:
        question = latest_user_text(state["messages"])
        lang = detect_lang(question)
        passages = []
        if deps.retriever is not None:
            try:
                passages = (await deps.retriever.retrieve(retrieval_query(state["messages"]))).passages
            except Exception:
                # ES 挂了不能让模型凭记忆回答政策问题
                log.exception("knowledge base retrieval failed")
                return {
                    "messages": [AIMessage(prompts.KB_UNAVAILABLE[lang], additional_kwargs={"sources": []})],
                    "sources": [],
                }
        if not passages:
            # 知识库没有覆盖：直接说不知道，不让模型自由发挥
            return {"messages": [AIMessage(prompts.NO_ANSWER[lang], additional_kwargs={"sources": []})], "sources": []}
        sources = [
            {"n": i, "title": p["title"], "section": p["section"], "source": p["source"], "score": p.get("score")}
            for i, p in enumerate(passages, start=1)
        ]
        system = prompts.AFTER_SALES.replace("{documents}", format_documents(passages))
        response = await deps.models.chat().ainvoke([SystemMessage(system), *dialogue(state["messages"], 6)], config)
        response.additional_kwargs["sources"] = sources
        return {"messages": [response], "sources": sources}

    async def other(state: AgentState, config: RunnableConfig) -> dict:
        response = await deps.models.chat().ainvoke(
            [SystemMessage(prompts.OTHER), *dialogue(state["messages"], 6)], config
        )
        return {"messages": [response]}

    graph = StateGraph(AgentState)
    graph.add_node("route", route)
    graph.add_node("shopping", agent(prompts.SHOPPING, shopping_tools))
    graph.add_node("orders", agent(prompts.ORDERS, order_tools))
    graph.add_node("shopping_tools", ToolNode(shopping_tools))
    graph.add_node("order_tools", ToolNode(order_tools))
    graph.add_node("confirm", confirm, destinations=("shopping", "orders", "shopping_tools", "order_tools"))
    graph.add_node("after_sales", after_sales)
    graph.add_node("other", other)

    graph.add_edge(START, "route")
    graph.add_conditional_edges(
        "route",
        lambda s: s["intent"],
        {"shopping": "shopping", "orders": "orders", "after_sales": "after_sales", "other": "other"},
    )
    graph.add_conditional_edges("shopping", after_agent("shopping_tools"), ["shopping_tools", "confirm", END])
    graph.add_conditional_edges("orders", after_agent("order_tools"), ["order_tools", "confirm", END])
    graph.add_edge("shopping_tools", "shopping")
    graph.add_edge("order_tools", "orders")
    graph.add_edge("after_sales", END)
    graph.add_edge("other", END)
    return graph.compile(checkpointer=checkpointer)
