import httpx
import pytest

from stickybeak_agent.config import Settings
from stickybeak_agent.discovery import ServiceResolver
from stickybeak_agent.java_api import ApiError, JavaApi


def client(handler):
    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


async def test_passes_user_id_header_and_formats_amounts():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["user"] = request.headers.get("X-User-Id")
        order = {
            "orderNo": "SO1",
            "status": "paid",
            "payAmount": 9.9,
            "currency": "AUD",
            "items": [{"productId": 1, "productName": "Magnet", "qty": 2, "price": 4.95}],
        }
        return httpx.Response(200, json={"code": 0, "message": "success", "data": order})

    api = JavaApi(ServiceResolver(Settings(service_urls="stickybeak-order=http://order:8084")), client(handler))
    order = await api.get_order("7", "SO1")

    assert seen == {"url": "http://order:8084/orders/SO1", "user": "7"}
    assert order["payAmount"] == "9.90" and order["items"][0]["price"] == "4.95" and order["itemCount"] == 2


async def test_business_errors_become_api_errors():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403, json={"code": 403, "message": "forbidden", "data": None})

    api = JavaApi(ServiceResolver(Settings(service_urls="stickybeak-order=http://order:8084")), client(handler))
    with pytest.raises(ApiError) as e:
        await api.cancel_order("7", "SO-OTHER")
    assert e.value.status == 403


async def test_product_search_keeps_only_fields_the_model_needs():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.params["q"] == "UNSW" and request.url.params["maxPrice"] == "20.0"
        item = {
            "id": 1,
            "slug": "s",
            "name": "UNSW sign",
            "categoryName": "Bus",
            "priceCents": 990,
            "stock": 3,
            "sales": 5,
            "tags": ["UNSW"],
            "images": ["/a.jpg", "/b.jpg"],
            "description": "x" * 5000,
        }
        return httpx.Response(200, json={"code": 0, "data": {"total": 1, "items": [item]}})

    api = JavaApi(ServiceResolver(Settings(service_urls="stickybeak-product=http://product:8082")), client(handler))
    result = await api.search_products("UNSW", None, 20.0, None, 5)
    assert result["items"][0] == {
        "id": 1,
        "slug": "s",
        "name": "UNSW sign",
        "category": "Bus",
        "priceAud": "9.90",
        "inStock": True,
        "sales": 5,
        "tags": ["UNSW"],
        "image": "/a.jpg",
    }


async def test_product_search_total_is_a_number_even_though_java_sends_longs_as_strings():
    def handler(request: httpx.Request) -> httpx.Response:
        item = {"id": "1000002", "slug": "bus-unsw", "name": "UNSW", "priceCents": 3990, "stock": 0}
        return httpx.Response(200, json={"code": 0, "data": {"total": "1", "items": [item]}})

    api = JavaApi(ServiceResolver(Settings(service_urls="stickybeak-product=http://product:8082")), client(handler))
    result = await api.search_products("UNSW", None, None, None, 3)

    assert result["total"] == 1
    assert result["items"][0]["id"] == "1000002"
