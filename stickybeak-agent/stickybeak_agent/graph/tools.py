"""agent 的工具（Issue 13.3 / 14.3）。

- 只读工具（商品、订单查询、售后检索）从 MCP Server 加载，见 mcp_client.py；这里只剩写操作工具
- 用户身份从运行配置 configurable.user_id 读取（API 层取自网关注入的 X-User-Id），不是工具参数，模型无法指定
- 写操作（加购、取消订单）在图里先经过 confirm 节点让用户确认，确认后才会真正执行
- 工具返回统一包成 {"data": ...} 或 {"error": ...} 的 JSON，提示词里声明这些内容只是数据
"""

from __future__ import annotations

import json
from typing import Annotated, Any, Literal

from langchain_core.runnables import RunnableConfig
from langchain_core.tools import BaseTool, tool
from pydantic import Field

from ..java_api import ApiError, JavaApi

WRITE_TOOLS = frozenset({"add_to_cart", "cancel_order"})
SHOPPING_TOOLS = ("search_products", "get_product", "add_to_cart")
ORDER_TOOLS = ("list_my_orders", "get_order", "cancel_order")

# 与商品服务 t_category 的 slug 一致（2026-09 对齐的 10 个子品类；原 booze 已并入 road-sign）。改类目时同步这里
CATEGORIES = Literal[
    "bus-sign", "supermarket", "train", "tram", "bird", "road-sign", "collections", "achievement", "slogan", "phonecase"
]


def current_user(config: RunnableConfig) -> str | None:
    user_id = (config.get("configurable") or {}).get("user_id")
    return str(user_id) if user_id else None


def as_data(data: Any) -> str:
    return json.dumps({"data": data}, ensure_ascii=False, default=str)


def as_error(message: str) -> str:
    return json.dumps({"error": message}, ensure_ascii=False)


def api_error_message(e: ApiError) -> str:
    # 403 与 404 返回同一句话：不向调用方透露"这个订单号存在但属于别人"
    if e.status in (403, 404):
        return "not found, or it does not belong to the current user"
    if e.status == 401:
        return "the user is not logged in"
    return e.message


def make_write_tools(api: JavaApi) -> dict[str, BaseTool]:
    """写操作工具：只在自家 agent 里使用（经 confirm 节点让用户确认），不通过 MCP 对外提供。"""

    @tool
    async def add_to_cart(
        product_id: Annotated[int, Field(description="numeric product id from search results")],
        qty: Annotated[int, Field(ge=1, le=20)] = 1,
        *,
        config: RunnableConfig,
    ) -> str:
        """Add a product to the current user's cart. The user is asked to confirm before it runs."""
        user_id = current_user(config)
        if user_id is None:
            return as_error("the user is not logged in")
        try:
            return as_data(await api.add_to_cart(user_id, product_id, qty))
        except ApiError as e:
            return as_error(api_error_message(e))

    @tool
    async def cancel_order(
        order_no: Annotated[str, Field(description="order number of a pending (unpaid) order")],
        *,
        config: RunnableConfig,
    ) -> str:
        """Cancel one of the current user's unpaid orders. The user is asked to confirm before it runs."""
        user_id = current_user(config)
        if user_id is None:
            return as_error("the user is not logged in")
        try:
            await api.cancel_order(user_id, order_no)
            return as_data({"orderNo": order_no, "status": "cancelled"})
        except ApiError as e:
            return as_error(api_error_message(e))

    return {t.name: t for t in (add_to_cart, cancel_order)}
