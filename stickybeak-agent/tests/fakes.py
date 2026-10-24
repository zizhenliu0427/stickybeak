"""测试替身：按脚本依次返回预设回复的聊天模型，以及内存版的 Java 服务。"""

from __future__ import annotations

from typing import Any

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_core.runnables import RunnableLambda
from pydantic import Field

from stickybeak_agent.java_api import ApiError


class ScriptedChatModel(BaseChatModel):
    """每次调用弹出 responses 里的下一条；calls 记录每次收到的消息，bound_tools 记录绑定过的工具名。"""

    responses: list[Any] = Field(default_factory=list)
    calls: list[list[BaseMessage]] = Field(default_factory=list)
    bound_tools: list[list[str]] = Field(default_factory=list)
    model_name: str = "scripted"

    @property
    def _llm_type(self) -> str:
        return "scripted"

    def _generate(self, messages, stop=None, run_manager=None, **kwargs) -> ChatResult:
        self.calls.append(list(messages))
        if not self.responses:
            raise AssertionError("ScriptedChatModel ran out of responses")
        response = self.responses.pop(0)
        if isinstance(response, BaseException):
            raise response
        if isinstance(response, str):
            response = AIMessage(response)
        response.usage_metadata = {"input_tokens": 10, "output_tokens": 5, "total_tokens": 15}
        return ChatResult(generations=[ChatGeneration(message=response)])

    def bind_tools(self, tools, **kwargs):
        self.bound_tools.append([getattr(t, "name", str(t)) for t in tools])
        return self

    def with_structured_output(self, schema, *, method=None, include_raw=False, **kwargs):
        return self | RunnableLambda(lambda m: schema(**m.tool_calls[0]["args"]))


def route_to(intent: str) -> AIMessage:
    return AIMessage("", tool_calls=[{"name": "RouteDecision", "args": {"intent": intent}, "id": f"route-{intent}"}])


def call_tool(name: str, args: dict, call_id: str = "call-1") -> AIMessage:
    return AIMessage("", tool_calls=[{"name": name, "args": args, "id": call_id}])


class FakeJavaApi:
    """用户 7 有两个订单；用户 8 有一个订单 SO-OTHER。"""

    def __init__(self):
        self.orders = {
            "SO-PENDING": {
                "orderNo": "SO-PENDING",
                "owner": "7",
                "status": "pending",
                "payAmount": "29.90",
                "currency": "AUD",
            },
            "SO-PAID": {"orderNo": "SO-PAID", "owner": "7", "status": "paid", "payAmount": "19.90", "currency": "AUD"},
            "SO-OTHER": {
                "orderNo": "SO-OTHER",
                "owner": "8",
                "status": "pending",
                "payAmount": "9.90",
                "currency": "AUD",
            },
        }
        self.cart: dict[str, list[tuple[int, int]]] = {}
        self.cancelled: list[str] = []
        self.products = {
            1001: {
                "id": 1001,
                "slug": "kookaburra-magnet",
                "name": "Kookaburra magnet",
                "priceAud": "19.90",
                "image": "/k.jpg",
            }
        }

    async def search_products(self, query, category, max_price_aud, sort, size):
        return {"total": 1, "items": list(self.products.values())}

    async def get_product(self, slug_or_id):
        for p in self.products.values():
            if str(p["id"]) == slug_or_id or p["slug"] == slug_or_id:
                return p
        raise ApiError(404, 404, "not found")

    async def add_to_cart(self, user_id, product_id, qty):
        self.cart.setdefault(user_id, []).append((product_id, qty))
        return {"cartItemCount": sum(q for _, q in self.cart[user_id])}

    def _own(self, user_id, order_no):
        order = self.orders.get(order_no)
        if order is None or order["owner"] != user_id:
            raise ApiError(403, 403, "forbidden")
        return order

    async def list_my_orders(self, user_id, status):
        return [{k: v for k, v in o.items() if k != "owner"} for o in self.orders.values() if o["owner"] == user_id]

    async def get_order(self, user_id, order_no):
        return {k: v for k, v in self._own(user_id, order_no).items() if k != "owner"}

    async def cancel_order(self, user_id, order_no):
        order = self._own(user_id, order_no)
        order["status"] = "cancelled"
        self.cancelled.append(order_no)
