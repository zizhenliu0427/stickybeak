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
