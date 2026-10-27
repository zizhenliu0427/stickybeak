import httpx
from langchain_core.messages import HumanMessage

from .conftest import answer, make_graph, run
from .fakes import call_tool, route_to


def setup_products(api):
    api.orders["SO-PAID"]["items"] = [{"productId": 1001, "name": "Kookaburra magnet"}]
    api.products[1001]["inStock"] = True
    api.products[1002] = {**api.products[1001], "id": 1002, "slug": "new-bird", "name": "New bird"}
    api.products[1003] = {**api.products[1002], "id": 1003, "inStock": False}


async def test_two_agent_handoff_filters_bought_items_even_if_selector_invents_ids(api):
    setup_products(api)
    responses = [
        route_to("recommend_unpurchased"),
        call_tool("RecommendationPlan", {"category": "bird"}),
        call_tool("ProductSelection", {"product_ids": ["1001", "1002", "9999"]}),
    ]
    async with make_graph(api, responses) as (graph, model):
        events = await run(graph, {"messages": [HumanMessage("根据订单推荐没买过的")]})
        text = answer(events)
        assert "New bird" in text and "Kookaburra magnet" not in text and "9999" not in text
        state = await graph.aget_state({"configurable": {"thread_id": "7-thread"}})
        handoff = state.values["recommendation"]
        assert handoff["purchased_ids"] == ["1001"]
        assert len(handoff["candidates"]) == 1
        assert len(model.calls) == 3  # router + purchase research + shopping selector
        assert api.cart == {} and api.cancelled == []


async def test_order_failure_abstains_without_inventing_unpurchased_products(api):
    async def broken(*args):
        raise httpx.ConnectError("offline")

    api.list_my_orders = broken
    async with make_graph(api, [route_to("recommend_unpurchased")]) as (graph, model):
        events = await run(graph, {"messages": [HumanMessage("推荐没买过的")]})
        assert "不能确认" in answer(events)
        assert len(model.calls) == 1


async def test_history_limit_is_disclosed_and_only_current_user_orders_are_read(api):
    setup_products(api)
    api.orders.update({f"PAID-{i}": {**api.orders["SO-PAID"], "orderNo": f"PAID-{i}", "owner": "7"} for i in range(21)})
    responses = [
        route_to("recommend_unpurchased"),
        call_tool("RecommendationPlan", {}),
        call_tool("ProductSelection", {"product_ids": ["1002"]}),
    ]
    async with make_graph(api, responses) as (graph, _):
        events = await run(graph, {"messages": [HumanMessage("推荐没买过的")]})
        assert "更早的订单未核对" in answer(events)
        state = await graph.aget_state({"configurable": {"thread_id": "7-thread"}})
        assert state.values["recommendation"]["checked_orders"] == 20


async def test_other_user_purchase_history_does_not_filter_current_users_candidates(api):
    setup_products(api)
    responses = [
        route_to("recommend_unpurchased"),
        call_tool("RecommendationPlan", {}),
        call_tool("ProductSelection", {"product_ids": ["1001"]}),
    ]
    async with make_graph(api, responses) as (graph, _):
        events = await run(
            graph,
            {"messages": [HumanMessage("Recommend something I haven't bought")]},
            user_id="8",
            thread_id="8-thread",
        )
        assert "Kookaburra magnet" in answer(events)
