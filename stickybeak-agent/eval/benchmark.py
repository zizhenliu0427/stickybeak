"""OpenAI-compatible SSE 压测：记录 TTFT、总耗时、真实 usage 吞吐、GPU 峰值显存。"""

from __future__ import annotations

import argparse
import asyncio
import json
import math
import os
import shutil
import statistics
import time
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

import httpx


def percentile(values, percent):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent) - 1)] if ordered else None


async def request(client, args, prompt, index):
    start = time.perf_counter()
    first = None
    usage = {}
    completed = False
    try:
        async with client.stream(
            "POST",
            f"{args.base_url.rstrip('/')}/chat/completions",
            json={
                "model": args.model,
                # 唯一前缀避免后一组复用前一组相同提示的 prefix cache，影响并发对比。
                "messages": [{"role": "user", "content": f"{uuid4().hex} Request {index}. {prompt}"}],
                "max_tokens": args.output_tokens,
                "temperature": 0,
                "stream": True,
                "stream_options": {"include_usage": True},
                "chat_template_kwargs": {"enable_thinking": False},
                # Ollama uses a different switch; leave the vLLM default request unchanged.
                **({"reasoning_effort": args.reasoning_effort} if getattr(args, "reasoning_effort", None) else {}),
            },
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    completed = True
                    break
                event = json.loads(data)
                if event.get("error"):
                    raise ValueError("server returned a stream error")
                if any(c.get("delta", {}).get("content") for c in event.get("choices", [])) and first is None:
                    first = time.perf_counter() - start
                if event.get("usage"):
                    usage = event["usage"]
        if first is None or not completed:
            raise ValueError("incomplete SSE response")
        return {
            "ok": True,
            "ttft_ms": round(first * 1000, 2),
            "total_ms": round((time.perf_counter() - start) * 1000, 2),
            **usage,
        }
    except (httpx.HTTPError, ValueError) as exc:
        # 不记录响应体或请求头，避免将服务内部信息/密钥写入报告。
        return {"ok": False, "error": type(exc).__name__, "total_ms": round((time.perf_counter() - start) * 1000, 2)}


async def sample_gpu(stop, samples):
    if not shutil.which("nvidia-smi"):
        return
    while not stop.is_set():
        process = await asyncio.create_subprocess_exec(
            "nvidia-smi",
            "--query-gpu=memory.used",
            "--format=csv,noheader,nounits",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.DEVNULL,
        )
        stdout, _ = await process.communicate()
        if process.returncode == 0:
            samples.append(sum(int(line) for line in stdout.decode().splitlines() if line.strip().isdigit()))
        try:
            await asyncio.wait_for(stop.wait(), timeout=0.5)
        except TimeoutError:
            pass


async def benchmark(args):
    report = {
        "timestamp": datetime.now(UTC).isoformat(),
        "model": args.model,
        "base_url": args.base_url,
        "reasoning_effort": getattr(args, "reasoning_effort", None),
        "notes": "Unique random prefix per request prevents cross-run prefix-cache reuse. "
        "Context sizes are target words; actual prompt_tokens come from server usage. "
        "GPU memory is whole-device usage, includes other processes. Warmup excluded.",
        "runs": [],
    }
    headers = {"Authorization": f"Bearer {os.environ.get('VLLM_API_KEY', 'local-vllm')}"}
    async with httpx.AsyncClient(headers=headers, timeout=args.timeout) as client:
        warmup = await request(client, args, "Say hello briefly.", -1)
        if not warmup["ok"]:
            raise RuntimeError(f"Warmup failed: {warmup['error']}")
        for words in args.context_words:
            prompt = ("Australia bird souvenir shop. " * math.ceil(words / 4)) + (
                "\nWrite a detailed product care guide with at least 20 numbered tips."
            )
            for concurrency in args.concurrency:
                semaphore = asyncio.Semaphore(concurrency)

                async def worker(index, semaphore=semaphore, prompt=prompt):
                    async with semaphore:
                        return await request(client, args, prompt, index)

                stop, samples = asyncio.Event(), []
                gpu_task = asyncio.create_task(sample_gpu(stop, samples))
                start = time.perf_counter()
                try:
                    rows = await asyncio.gather(*(worker(i) for i in range(args.requests)))
                    seconds = time.perf_counter() - start
                finally:
                    stop.set()
                    await gpu_task
                ok = [r for r in rows if r["ok"]]
                has_usage = bool(ok) and all("completion_tokens" in r for r in ok)
                run = {
                    "context_words": words,
                    "concurrency": concurrency,
                    "requests": args.requests,
                    "successes": len(ok),
                    "errors": len(rows) - len(ok),
                    "wall_seconds": round(seconds, 3),
                    "ttft_p50_ms": percentile([r["ttft_ms"] for r in ok], 0.5),
                    "ttft_p95_ms": percentile([r["ttft_ms"] for r in ok], 0.95),
                    "latency_p95_ms": percentile([r["total_ms"] for r in ok], 0.95),
                    "mean_prompt_tokens": statistics.mean([r["prompt_tokens"] for r in ok])
                    if ok and all("prompt_tokens" in r for r in ok)
                    else None,
                    "output_tokens_per_second": round(sum(r["completion_tokens"] for r in ok) / seconds, 2)
                    if has_usage
                    else None,
                    "peak_gpu_memory_mib": max(samples) if samples else None,
                    "samples": rows,
                }
                report["runs"].append(run)
                print(json.dumps({k: v for k, v in run.items() if k != "samples"}), flush=True)
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return 1 if any(r["errors"] for r in report["runs"]) else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8000/v1")
    parser.add_argument("--model", default="nvidia/Qwen3.8-27B-NVFP4")
    parser.add_argument("--concurrency", nargs="+", type=int, default=[1, 4])
    parser.add_argument("--context-words", nargs="+", type=int, default=[256, 2048])
    parser.add_argument("--requests", type=int, default=8)
    parser.add_argument("--output-tokens", type=int, default=128)
    parser.add_argument("--timeout", type=float, default=120)
    parser.add_argument("--reasoning-effort", choices=["none", "low", "medium", "high"])
    parser.add_argument("--output", type=Path, default=Path("eval/reports/vllm-benchmark.json"))
    args = parser.parse_args()
    if min(*args.concurrency, *args.context_words, args.requests, args.output_tokens) < 1:
        parser.error("counts must be positive")
    return asyncio.run(benchmark(args))


if __name__ == "__main__":
    raise SystemExit(main())
