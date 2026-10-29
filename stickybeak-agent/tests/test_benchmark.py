import json
from types import SimpleNamespace

import httpx

from eval.benchmark import percentile, request


def test_small_sample_percentiles_do_not_drop_tail():
    assert percentile([1, 2, 3, 100], 0.95) == 100
    assert percentile([], 0.5) is None


async def test_benchmark_requires_complete_stream_and_uses_reported_tokens():
    args = SimpleNamespace(base_url="http://test/v1", model="test", output_tokens=10)
    stream = (
        'data: {"choices":[{"delta":{"content":"Hi"}}]}\n\n'
        'data: {"choices":[],"usage":{"prompt_tokens":4,"completion_tokens":1}}\n\n'
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200, text=stream))) as client:
        assert not (await request(client, args, "hi", 0))["ok"]
    async with httpx.AsyncClient(
        transport=httpx.MockTransport(lambda r: httpx.Response(200, text=stream + "data: [DONE]\n\n"))
    ) as client:
        result = await request(client, args, "hi", 0)
        assert result["ok"] and result["completion_tokens"] == 1 and result["prompt_tokens"] == 4
        assert result["ttft_ms"] <= result["total_ms"]


async def test_ollama_thinking_switch_is_opt_in():
    args = SimpleNamespace(base_url="http://test/v1", model="test", output_tokens=10)
    payloads = []

    def respond(req):
        payloads.append(json.loads(req.content))
        return httpx.Response(200, text='data: {"choices":[{"delta":{"content":"Hi"}}]}\n\ndata: [DONE]\n\n')

    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        await request(client, args, "hi", 0)
        args.reasoning_effort = "none"
        await request(client, args, "hi", 1)
    assert "reasoning_effort" not in payloads[0]
    assert payloads[1]["reasoning_effort"] == "none"
    assert payloads[1]["chat_template_kwargs"] == {"enable_thinking": False}
