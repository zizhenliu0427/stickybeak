"""混合检索：kNN 向量 + BM25 关键词 → RRF 融合 → 重排 → 按阈值过滤。

- 向量检索擅长同义改写（"东西坏了能退吗" ≈ "商品质量问题退货"），关键词检索擅长精确词（订单号格式、"GST"）
- 两路各取 rag_candidates 条，RRF 合并后取前若干条交给重排
- 重排后低于 rag_min_rerank_score 的丢掉；一条都不剩说明知识库没覆盖，由调用方直接回答"不知道"
- 没有配置向量模型时只走关键词；没有配置重排时按 RRF 顺序取 top_k，不做阈值过滤
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field

import httpx
from langchain_core.embeddings import Embeddings

from ..config import Settings
from .fusion import rrf
from .rerank import Reranker
from .store import KnowledgeStore

log = logging.getLogger(__name__)

_CJK = re.compile(r"[\u4e00-\u9fff]")


def detect_lang(text: str) -> str:
    return "zh" if _CJK.search(text) else "en"


@dataclass
class RetrievalResult:
    passages: list[dict] = field(default_factory=list)
    # 调试与评测用：两路各自的结果顺序
    keyword_ids: list[str] = field(default_factory=list)
    vector_ids: list[str] = field(default_factory=list)
    reranked: bool = False


class HybridRetriever:
    def __init__(
        self, settings: Settings, store: KnowledgeStore, embeddings: Embeddings | None, reranker: Reranker | None
    ):
        self._settings = settings
        self._store = store
        self._embeddings = embeddings
        self._reranker = reranker

    async def retrieve(self, query: str) -> RetrievalResult:
        lang = detect_lang(query)
        n = self._settings.rag_candidates
        keyword = await self._store.keyword_search(query, lang, n)
        vector: list[dict] = []
        if self._embeddings is not None:
            query_vector = await self._embeddings.aembed_query(query)
            vector = await self._store.vector_search(query_vector, lang, n)

        by_id = {p["id"]: p for p in keyword + vector}
        fused = [by_id[doc_id] for doc_id, _ in rrf([[p["id"] for p in keyword], [p["id"] for p in vector]])]
        result = RetrievalResult(keyword_ids=[p["id"] for p in keyword], vector_ids=[p["id"] for p in vector])
        if not fused:
            return result

        top_k = self._settings.rag_top_k
        if self._reranker is not None and self._reranker.enabled:
            candidates = fused[: top_k * 3]
            try:
                scores = await self._reranker.scores(query, [f"{p['section']}\n{p['content']}" for p in candidates])
            except httpx.HTTPError as e:
                # 重排挂了不影响回答，只是少了"是否相关"的判断，交给模型按提示词拒答
                log.warning("rerank failed, falling back to RRF order: %s", e)
            else:
                ranked = sorted(zip(candidates, scores, strict=True), key=lambda x: x[1], reverse=True)
                result.passages = [
                    dict(p, score=round(s, 4)) for p, s in ranked if s >= self._settings.rag_min_rerank_score
                ][:top_k]
                result.reranked = True
                return result
        result.passages = fused[:top_k]
        return result
