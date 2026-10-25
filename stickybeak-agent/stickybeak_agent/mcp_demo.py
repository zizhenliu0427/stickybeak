"""用官方 MCP 客户端连商城演示（Issue 14.4）：

    uv run python -m stickybeak_agent.mcp_demo --url http://localhost/api/mcp [--token <MCP 令牌>]

不带令牌时只能用公开工具（商品、售后检索），订单工具会提示需要登录；令牌在个人中心"AI 客户端接入（MCP）"里生成。
Cherry Studio、Cursor 等客户端的配置见 stickybeak-agent/README.md。
"""

import argparse
import asyncio
import json

import httpx
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

CALLS = [
    ("search_products", {"query": "UNSW", "size": 3}),
    ("search_faq", {"question": "退款多久到账？"}),
    ("list_my_orders", {}),
]


def show(result) -> str:
    text = result.content[0].text if result.content else ""
    if result.isError:
        return f"ERROR {text}"
    try:
        return json.dumps(json.loads(text), ensure_ascii=False)[:400]
    except json.JSONDecodeError:
        return text[:400]


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8080/api/mcp")
    parser.add_argument("--token", default="")
    args = parser.parse_args()

    headers = {"Authorization": f"Bearer {args.token}"} if args.token else None
    async with (
        httpx.AsyncClient(headers=headers, timeout=30, follow_redirects=True) as http,
        streamable_http_client(args.url, http_client=http) as (read, write, _),
        ClientSession(read, write) as session,
    ):
        info = await session.initialize()
        print(f"connected to {info.serverInfo.name} (protocol {info.protocolVersion})")
        for tool in (await session.list_tools()).tools:
            hint = "read-only" if tool.annotations and tool.annotations.readOnlyHint else ""
            print(f"  tool {tool.name:<16} {hint}  {tool.description.splitlines()[0]}")
        for name, arguments in CALLS:
            print(f"\n> {name} {json.dumps(arguments, ensure_ascii=False)}")
            print(show(await session.call_tool(name, arguments)))


if __name__ == "__main__":
    asyncio.run(main())
