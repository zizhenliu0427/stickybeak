"""调用现有 Java 服务（product / cart / order）。

用户身份只来自网关注入的 X-User-Id，由调用方（工具）从运行配置里取，模型无法指定。
订单服务自己也会校验订单归属（他人订单返回 403），这里是第二道防线之外的第一道。
返回给模型的数据只保留必要字段，并截断长文本，减少 token 与注入面。
"""

from __future__ import annotations

from decimal import Decimal
from typing import Any

import httpx

from .discovery import ServiceResolver

PRODUCT = "stickybeak-product"
CART = "stickybeak-cart"
ORDER = "stickybeak-order"


class ApiError(Exception):
    def __init__(self, status: int, code: int | None, message: str):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


class JavaApi:
    def __init__(self, resolver: ServiceResolver, client: httpx.AsyncClient):
        self._resolver = resolver
        self._client = client

    async def _call(self, service: str, method: str, path: str, *, user_id: str | None = None, **kwargs) -> Any:
        headers = kwargs.pop("headers", {})
        if user_id is not None:
            headers["X-User-Id"] = user_id
        url = await self._resolver.base_url(service) + path
        resp = await self._client.request(method, url, headers=headers, **kwargs)
        try:
            body = resp.json()
        except ValueError:
            raise ApiError(resp.status_code, None, f"{service} returned non-JSON ({resp.status_code})") from None
        if resp.status_code >= 400 or body.get("code") != 0:
            raise ApiError(resp.status_code, body.get("code"), body.get("message") or "request failed")
        return body.get("data")

    # ---- 商品（公开数据） ----
    async def search_products(
        self, query: str | None, category: str | None, max_price_aud: float | None, sort: str | None, size: int
    ) -> dict:
        params: dict[str, Any] = {"page": 1, "size": size}
        if query:
            params["q"] = query
        if category:
            params["category"] = category
        if max_price_aud is not None:
            params["maxPrice"] = max_price_aud
        if sort:
            params["sort"] = sort
        data = await self._call(PRODUCT, "GET", "/products", params=params)
        return {"total": data.get("total"), "items": [product_brief(p) for p in data.get("items", [])]}

    async def get_product(self, slug_or_id: str) -> dict:
        path = f"/products/id/{slug_or_id}" if slug_or_id.isdigit() else f"/products/{slug_or_id}"
        return product_detail(await self._call(PRODUCT, "GET", path))

    # ---- 购物车（写操作，需用户确认后才会被调用） ----
    async def add_to_cart(self, user_id: str, product_id: int, qty: int) -> dict:
        data = await self._call(
            CART, "POST", "/cart/items", user_id=user_id, json={"productId": product_id, "qty": qty}
        )
        items = data.get("items", []) if isinstance(data, dict) else []
        return {"cartItemCount": sum(i.get("qty", 0) for i in items)}

    # ---- 订单（只能看自己的） ----
    async def list_my_orders(self, user_id: str, status: str | None) -> list[dict]:
        params = {"status": status} if status else None
        data = await self._call(ORDER, "GET", "/orders/my", user_id=user_id, params=params)
        return [order_brief(o) for o in data or []]

    async def get_order(self, user_id: str, order_no: str) -> dict:
        return order_detail(await self._call(ORDER, "GET", f"/orders/{order_no}", user_id=user_id))

    async def cancel_order(self, user_id: str, order_no: str) -> None:
        await self._call(ORDER, "POST", f"/orders/{order_no}/cancel", user_id=user_id)


def _truncate(text: str | None, limit: int) -> str:
    if not text:
        return ""
    return text if len(text) <= limit else text[:limit] + "…"


def _money(value) -> str | None:
    """订单金额是 BigDecimal，JSON 里是数字（9.9），统一成两位小数的字符串。"""
    return None if value is None else f"{Decimal(str(value)):.2f}"


def _aud(cents: int | None) -> str | None:
    return None if cents is None else f"{cents / 100:.2f}"


def product_brief(p: dict) -> dict:
    return {
        "id": p.get("id"),
        "slug": p.get("slug"),
        "name": p.get("name"),
        "category": p.get("categoryName") or p.get("category"),
        "priceAud": _aud(p.get("priceCents")),
        "inStock": (p.get("stock") or 0) > 0,
        "sales": p.get("sales"),
        "tags": (p.get("tags") or [])[:5],
        "image": (p.get("images") or [None])[0],
    }


def product_detail(p: dict) -> dict:
    brief = product_brief(p)
    brief["stock"] = p.get("stock")
    brief["description"] = _truncate(p.get("description"), 600)
    return brief


def order_brief(o: dict) -> dict:
    return {
        "orderNo": o.get("orderNo"),
        "status": o.get("status"),
        "payAmount": _money(o.get("payAmount")),
        "currency": o.get("currency"),
        "createTime": o.get("createTime"),
        "itemCount": sum(i.get("qty", 0) for i in o.get("items") or []),
    }


def order_detail(o: dict) -> dict:
    detail = order_brief(o)
    detail.update(
        {
            "payTime": o.get("payTime"),
            "shipTime": o.get("shipTime"),
            "completeTime": o.get("completeTime"),
            "items": [
                {
                    "productId": i.get("productId"),
                    "name": i.get("productName"),
                    "qty": i.get("qty"),
                    "price": _money(i.get("price")),
                }
                for i in o.get("items") or []
            ],
        }
    )
    return detail
