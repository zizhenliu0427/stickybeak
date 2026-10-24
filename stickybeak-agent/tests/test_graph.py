import json

import httpx
import openai
from langchain_core.messages import HumanMessage, ToolMessage
from langgraph.types import Command

from stickybeak_agent.config import Settings
from stickybeak_agent.graph.builder import repair_tool_calls
from stickybeak_agent.graph.tools import ORDER_TOOLS, SHOPPING_TOOLS
from stickybeak_agent.rag.retriever import RetrievalResult

from .conftest import answer, events_of, make_graph, run
from .fakes import ScriptedChatModel, call_tool, route_to


def _tool_results(model, call_index):
    return [json.loads(m.content) for m in model.calls[call_index] if isinstance(m, ToolMessage)]


async def test_write_tool_waits_for_confirmation_and_runs_after_approval(api):
    graph, _ = make_graph(api, [route_to("orders"), call_tool("cancel_order", {"order_no": "SO-PENDING"}), "已取消"])

    events = await run(graph, {"messages": [HumanMessage("取消订单 SO-PENDING")]})
    confirm = events_of(events, "confirm")
    assert confirm and confirm[0]["actions"][0]["orderNo"] == "SO-PENDING"
    assert confirm[0]["actions"][0]["payAmount"] == "29.90"  # 卡片内容来自订单服务，不是模型
    assert api.cancelled == []

    events = await run(graph, Command(resume={"approved": True}))
    assert api.cancelled == ["SO-PENDING"]
    assert answer(events) == "已取消"


async def test_declined_write_tool_is_not_executed_and_model_is_told(api):
    graph, model = make_graph(
        api, [route_to("orders"), call_tool("cancel_order", {"order_no": "SO-PENDING"}), "好的，没有取消"]
    )
    await run(graph, {"messages": [HumanMessage("取消订单 SO-PENDING")]})
    events = await run(graph, Command(resume={"approved": False}))

    assert api.cancelled == []
    assert answer(events) == "好的，没有取消"
    assert "declined" in _tool_results(model, 2)[0]["error"]


async def test_new_message_while_confirmation_pending_leaves_action_unexecuted(api):
    graph, model = make_graph(
        api,
        [route_to("shopping"), call_tool("add_to_cart", {"product_id": 1001, "qty": 2}), route_to("shopping"), "好的"],
    )
    await run(graph, {"messages": [HumanMessage("把笑翠鸟冰箱贴加两个到购物车")]})
    await run(graph, {"messages": [HumanMessage("算了，先不加了")]})

    assert api.cart == {}
    # 上一轮悬空的工具调用被补上"未执行"，模型接口不会因为缺少工具结果而报错
    assert "did not confirm" in _tool_results(model, 3)[0]["error"]


async def test_cannot_read_another_users_order(api):
    graph, model = make_graph(
        api, [route_to("orders"), call_tool("get_order", {"order_no": "SO-OTHER"}), "没有找到这个订单"]
    )
    await run(graph, {"messages": [HumanMessage("查一下 SO-OTHER")]})

    result = _tool_results(model, 2)[0]
    assert "not found" in result["error"] and "data" not in result


async def test_user_identity_cannot_be_supplied_by_the_model(api):
    # 模型（或被注入的提示词）试图在参数里指定别的用户：参数被忽略，仍按当前登录用户 7 查询
    graph, model = make_graph(
        api, [route_to("orders"), call_tool("get_order", {"order_no": "SO-OTHER", "user_id": "8"}), "查不到"]
    )
    await run(graph, {"messages": [HumanMessage("查 SO-OTHER")]})
    assert "not found" in _tool_results(model, 2)[0]["error"]


async def test_read_tools_run_without_confirmation(api):
    graph, model = make_graph(api, [route_to("orders"), call_tool("list_my_orders", {}), "你有两个订单"])
    events = await run(graph, {"messages": [HumanMessage("我的订单")]})

    assert not events_of(events, "confirm")
    assert events_of(events, "tool") == [{"name": "list_my_orders"}]
    orders = _tool_results(model, 2)[0]["data"]
    assert {o["orderNo"] for o in orders} == {"SO-PENDING", "SO-PAID"}


async def test_each_agent_only_gets_its_own_tools(api):
    graph, model = make_graph(api, [route_to("shopping"), "推荐这个", route_to("orders"), "你的订单"])
    await run(graph, {"messages": [HumanMessage("推荐冰箱贴")]})
    await run(graph, {"messages": [HumanMessage("我的订单")]})

    assert model.bound_tools == [list(SHOPPING_TOOLS), list(ORDER_TOOLS)]


async def test_conversation_survives_restart_with_same_checkpointer(api, saver):
    graph, _ = make_graph(api, [route_to("other"), "你好"], saver=saver)
    await run(graph, {"messages": [HumanMessage("你好")]})

    restarted, model = make_graph(api, [route_to("other"), "还记得"], saver=saver)
    await run(restarted, {"messages": [HumanMessage("你还记得我吗")]})
    seen = [m.text for m in model.calls[1]]
    assert "你好" in seen and "你还记得我吗" in seen


class StubRetriever:
    def __init__(self, passages):
        self.passages = passages
        self.queries = []

    async def retrieve(self, query):
        self.queries.append(query)
        return RetrievalResult(passages=self.passages)


async def test_after_sales_answer_cites_sources(api):
    retriever = StubRetriever(
        [
            {
                "id": "a",
                "title": "退换货与退款",
                "section": "退款多久到账",
                "source": "zh/r.md",
                "content": "银行卡通常 5–10 个工作日到账",
                "score": 0.9,
            }
        ]
    )
    graph, model = make_graph(api, [route_to("after_sales"), "银行卡 5–10 个工作日到账 [1]"], retriever=retriever)
    events = await run(graph, {"messages": [HumanMessage("退款多久到账？")]})

    assert answer(events) == "银行卡 5–10 个工作日到账 [1]"
    assert events_of(events, "sources")[0]["items"][0]["section"] == "退款多久到账"
    assert "5–10 个工作日" in model.calls[1][0].text  # 资料进了系统提示词


async def test_after_sales_says_unknown_without_calling_model_when_nothing_relevant(api):
    graph, model = make_graph(api, [route_to("after_sales")], retriever=StubRetriever([]))
    events = await run(graph, {"messages": [HumanMessage("你们支持比特币付款吗？")]})

    assert "没有找到" in answer(events)
    assert len(model.calls) == 1  # 只有意图识别调用了模型


async def test_short_follow_up_question_is_expanded_for_retrieval(api):
    retriever = StubRetriever([])
    graph, _ = make_graph(api, [route_to("after_sales"), route_to("after_sales")], retriever=retriever)
    await run(graph, {"messages": [HumanMessage("寄到中国要多久？")]})
    await run(graph, {"messages": [HumanMessage("那运费呢？")]})
    assert retriever.queries[-1] == "寄到中国要多久？ 那运费呢？"


async def test_falls_back_to_second_model_when_primary_times_out(api):
    timeout = openai.APITimeoutError(request=httpx.Request("POST", "https://dashscope.example"))
    primary = ScriptedChatModel(responses=[timeout, timeout])
    backup = ScriptedChatModel(responses=[route_to("other"), "你好（备用模型）"])
    graph, _ = make_graph(api, [], models=[primary, backup])
    events = await run(graph, {"messages": [HumanMessage("你好")]})
    assert answer(events) == "你好（备用模型）"


async def test_friendly_error_when_all_models_are_down(api):
    down = openai.APIConnectionError(request=httpx.Request("POST", "https://dashscope.example"))
    graph, _ = make_graph(api, [], models=[ScriptedChatModel(responses=[down])])
    events = await run(graph, {"messages": [HumanMessage("你好")]})
    assert events_of(events, "error")[0]["code"] == "model_unavailable"
    assert "暂时不可用" in events_of(events, "error")[0]["message"]
    assert events[-1][0] == "done"


def test_repair_only_adds_results_for_unanswered_calls():
    first = call_tool("list_my_orders", {}, "c1")
    answered = ToolMessage(content="{}", tool_call_id="c1")
    dangling = call_tool("cancel_order", {"order_no": "X"}, "c2")
    repaired = repair_tool_calls([HumanMessage("a"), first, answered, HumanMessage("b"), dangling])
    tool_ids = [m.tool_call_id for m in repaired if isinstance(m, ToolMessage)]
    assert tool_ids == ["c1", "c2"]


def test_settings_parse_static_service_urls():
    settings = Settings(service_urls="stickybeak-order=http://order:8084/, stickybeak-cart=http://cart:8083")
    assert settings.static_service_urls() == {
        "stickybeak-order": "http://order:8084",
        "stickybeak-cart": "http://cart:8083",
    }


class BrokenRetriever:
    async def retrieve(self, query):
        raise ConnectionError("elasticsearch down")


async def test_after_sales_does_not_guess_when_knowledge_base_is_down(api):
    graph, model = make_graph(api, [route_to("after_sales")], retriever=BrokenRetriever())
    events = await run(graph, {"messages": [HumanMessage("How long do refunds take?")]})
    assert "temporarily unavailable" in answer(events)
    assert len(model.calls) == 1


def test_history_trimming_keeps_the_current_question_in_a_long_turn():
    from stickybeak_agent.graph.builder import with_tools_history

    messages = [HumanMessage("old question"), HumanMessage("find 12 things")]
    for i in range(12):
        messages.append(call_tool("search_products", {"query": str(i)}, f"c{i}"))
        messages.append(ToolMessage(content="{}", tool_call_id=f"c{i}"))
    kept = with_tools_history(messages)
    assert "find 12 things" in [m.text for m in kept]
    assert len(kept) == len(messages)
