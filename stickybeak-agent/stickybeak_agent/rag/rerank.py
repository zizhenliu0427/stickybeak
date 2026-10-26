"""重排序：DashScope gte-rerank（交叉编码器，对 query 与片段逐对打分，比向量相似度更准）。

RRF 只能排序，不知道"有没有相关的"；重排分数有绝对意义，用来判断知识库是否覆盖了这个问题。
"""

from __future__ import annotations

import logging
from collections.abc import Sequence

import httpx

from ..config import Settings

log = logging.getLogger(__name__)


class Reranker:
    def __init__(self, settings: Settings, client: httpx.AsyncClient):
        self._settings = settings
        self._client = client

    @property
    def enabled(self) -> bool:
        # 目前只有通义提供重排接口；其他厂商（OpenAI、Gemini、DeepSeek、Ollama）没有，RERANK_PROVIDER=none
        return self._settings.rerank_provider.strip().lower() == "qwen" and bool(
            self._settings.rerank_model and self._settings.dashscope_api_key
        )

    async def scores(self, query: str, documents: Sequence[str]) -> list[float]:
        """与 documents 一一对应的相关度分数（0~1）。"""
        resp = await self._client.post(
            self._settings.rerank_url,
            headers={"Authorization": f"Bearer {self._settings.dashscope_api_key}"},
            json={
                "model": self._settings.rerank_model,
                "input": {"query": query, "documents": list(documents)},
                "parameters": {"return_documents": False, "top_n": len(documents)},
            },
            timeout=self._settings.llm_timeout_seconds,
        )
        resp.raise_for_status()
        result = [0.0] * len(documents)
        for item in resp.json()["output"]["results"]:
            result[item["index"]] = float(item["relevance_score"])
        return result
