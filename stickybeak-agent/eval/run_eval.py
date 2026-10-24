"""一键评测：uv run python -m eval.run_eval [--kb memory|es] [--only 前缀] [--concurrency 4]

需要模型 key（DASHSCOPE_API_KEY 或 DEEPSEEK_API_KEY）。业务数据用 eval/fixtures.py 的固定数据，
知识库默认用内存版（--kb memory，不需要 ES），--kb es 使用 ES_URIS 上已入库的真实索引。
报告写到 eval/reports/<时间>/：report.md（汇总）与 results.json（逐条明细）。
修改提示词、检索参数、模型后都要跑一遍，与上一次报告对比。
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
import uuid
from dataclasses import asdict
from datetime import datetime
from pathlib import Path

import httpx
import yaml
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from pydantic import BaseModel, Field

from stickybeak_agent.config import Settings, get_settings
from stickybeak_agent.graph.builder import GraphDeps, build_graph, format_documents, retrieval_query
from stickybeak_agent.graph.tools import make_write_tools
from stickybeak_agent.llm import ModelProvider
from stickybeak_agent.mcp_client import McpKnowledgeBase, load_mall_tools
from stickybeak_agent.mcp_server import McpDeps, serve_in_process
from stickybeak_agent.rag.corpus import load_corpus
from stickybeak_agent.rag.embeddings import build_embeddings
from stickybeak_agent.rag.rerank import Reranker
from stickybeak_agent.rag.retriever import HybridRetriever
from stickybeak_agent.stream import stream_run
from stickybeak_agent.usage import UsageTracker

from .fixtures import FixtureJavaApi
from .memory_store import MemoryKnowledgeStore
from .metrics import CaseResult, evaluate, summarize

HERE = Path(__file__).resolve().parent

JUDGE_PROMPT = """You are grading a customer-service answer for faithfulness.
Documents:
{documents}

Answer:
{answer}

The answer is faithful if every factual claim in it (time frames, fees, conditions, policies) is supported by the
documents. Saying "I don't know" or pointing to customer support is faithful. Ignore style and language."""


class FaithfulnessVerdict(BaseModel):
    """Whether the answer is fully supported by the documents."""

    faithful: bool = Field(description="true if every factual claim is supported by the documents")
    reason: str = Field(description="one short sentence")


def load_dataset(path: Path = HERE / "dataset.yaml") -> list[dict]:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


async def run_case(case: dict, models: ModelProvider, retriever: HybridRetriever | None) -> CaseResult:
    """与生产相同的接法：只读工具与售后检索经（进程内的）MCP Server，写工具在进程内。"""
    result = CaseResult(id=case["id"], category=case["category"], expect=case.get("expect") or {})
    api = FixtureJavaApi()
    config = {"configurable": {"thread_id": f"7-{uuid.uuid4().hex}", "user_id": "7"}, "recursion_limit": 25}
    try:
        async with serve_in_process(McpDeps(api=api, retriever=retriever)) as factory:
            mall = await load_mall_tools("http://mcp/mcp", factory)
            deps = GraphDeps(models, {**mall, **make_write_tools(api)}, api, McpKnowledgeBase(mall["search_faq"]))
            graph = build_graph(deps, InMemorySaver())
            for turn in case["turns"]:
                usage = UsageTracker("7", config["configurable"]["thread_id"])
                events = [
                    e
                    async for e in stream_run(
                        graph, {"messages": [HumanMessage(turn)]}, {**config, "callbacks": [usage]}, usage, turn
                    )
                ]
            state = await graph.aget_state(config)
        messages = state.values["messages"]
        last_human = max(i for i, m in enumerate(messages) if isinstance(m, HumanMessage))
        result.intent = state.values.get("intent")
        result.tools = [c["name"] for m in messages[last_human:] for c in getattr(m, "tool_calls", None) or []]
        result.confirm_requested = any(e["event"] == "confirm" for e in events)
        result.answer = "".join(json.loads(e["data"])["text"] for e in events if e["event"] == "token")
        result.sources = [s["source"] for s in state.values.get("sources") or []]
        errors = [json.loads(e["data"]) for e in events if e["event"] == "error"]
        if errors:
            result.error = errors[0].get("code")
        result.writes = api.writes

        if retriever is not None and result.category == "after_sales":
            retrieval = await retriever.retrieve(retrieval_query(messages))
            result.retrieved = [p["doc_id"] for p in retrieval.passages]
            if retrieval.passages and result.sources:
                judge = models.structured(FaithfulnessVerdict)
                verdict = await judge.ainvoke(
                    JUDGE_PROMPT.format(documents=format_documents(retrieval.passages), answer=result.answer)
                )
                result.faithful = verdict.faithful
    except Exception as e:  # 单条失败不影响整体评测
        result.error = f"{type(e).__name__}: {e}"
    return evaluate(result)


def build_retriever(settings: Settings, kb: str, http: httpx.AsyncClient):
    embeddings = build_embeddings(settings)
    if kb == "memory":
        store = MemoryKnowledgeStore(load_corpus(), embeddings)
    else:
        from elasticsearch import AsyncElasticsearch

        from stickybeak_agent.rag.store import KnowledgeStore

        store = KnowledgeStore(
            AsyncElasticsearch(settings.es_uris.split(",")), settings.kb_index, settings.embedding_dimensions
        )
    return HybridRetriever(settings, store, embeddings, Reranker(settings, http))


def render_report(summary: dict, results: list[CaseResult], meta: dict) -> str:
    def rate(name: str) -> str:
        m = summary[name]
        return "-" if m["rate"] is None else f"{m['rate']:.1%} ({m['passed']}/{m['total']})"

    lines = [
        f"# Agent eval {meta['startedAt']}",
        "",
        f"models: {', '.join(meta['models'])} · kb: {meta['kb']} · duration: {meta['durationSeconds']}s",
        "",
        "| metric | value |",
        "|---|---|",
        f"| cases passed | {summary['passed']}/{summary['cases']} |",
        f"| intent accuracy | {rate('intent_accuracy')} |",
        f"| tool-call accuracy | {rate('tool_call_accuracy')} |",
        f"| confirmation accuracy | {rate('confirm_accuracy')} |",
        f"| RAG recall@k | {rate('recall_at_k')} |",
        f"| faithfulness | {rate('faithfulness')} |",
        f"| refusal accuracy | {rate('refusal_accuracy')} |",
        f"| no leak | {rate('no_leak')} |",
        f"| unconfirmed writes | {summary['unconfirmed_writes']} |",
        f"| run errors | {summary['errors']} |",
        "",
        "| category | passed |",
        "|---|---|",
        *[f"| {c} | {s['passed']}/{s['total']} |" for c, s in sorted(summary["by_category"].items())],
        "",
        "## Failed cases",
        "",
    ]
    failed = [r for r in results if not r.passed]
    if not failed:
        lines.append("None.")
    for r in failed:
        bad = [k for k, ok in r.checks.items() if not ok]
        lines.append(f"- **{r.id}** failed {bad or ''} {('error: ' + r.error) if r.error else ''}")
        lines.append(f"  - intent={r.intent} tools={r.tools} confirm={r.confirm_requested} retrieved={r.retrieved}")
        lines.append(f"  - answer: {r.answer[:200]!r}")
    return "\n".join(lines) + "\n"


async def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kb", choices=["memory", "es"], default="memory")
    parser.add_argument("--only", default="", help="only run cases whose id starts with this prefix")
    parser.add_argument("--concurrency", type=int, default=4)
    args = parser.parse_args()

    settings = get_settings()
    models = ModelProvider(settings)
    if not models.available:
        print("No model configured: set DASHSCOPE_API_KEY or DEEPSEEK_API_KEY", file=sys.stderr)
        return 2
    cases = [c for c in load_dataset() if c["id"].startswith(args.only)]
    started = time.perf_counter()
    started_at = datetime.now().strftime("%Y%m%d-%H%M%S")
    async with httpx.AsyncClient() as http:
        retriever = build_retriever(settings, args.kb, http)
        semaphore = asyncio.Semaphore(args.concurrency)

        async def limited(case: dict) -> CaseResult:
            async with semaphore:
                result = await run_case(case, models, retriever)
                print(f"{'PASS' if result.passed else 'FAIL'} {result.id}", flush=True)
                return result

        results = await asyncio.gather(*(limited(c) for c in cases))

    summary = summarize(results)
    meta = {
        "startedAt": started_at,
        "models": models.model_names,
        "kb": args.kb,
        "durationSeconds": round(time.perf_counter() - started),
    }
    out = HERE / "reports" / started_at
    out.mkdir(parents=True, exist_ok=True)
    (out / "report.md").write_text(render_report(summary, results, meta), encoding="utf-8")
    (out / "results.json").write_text(
        json.dumps(
            {"meta": meta, "summary": summary, "results": [asdict(r) for r in results]},
            ensure_ascii=False,
            indent=2,
            default=str,
        ),
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"report: {out / 'report.md'}")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
