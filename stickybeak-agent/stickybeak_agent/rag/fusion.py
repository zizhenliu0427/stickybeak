"""倒数排名融合（Reciprocal Rank Fusion）。

ES 自带的 RRF 检索在 8.14 需要付费许可，这里在应用层做：
score(d) = Σ 1 / (k + rank_i(d))，k 取经验值 60。只用名次、不用原始分数，
所以 BM25 分数和向量相似度不在一个量纲上也能直接合并。
"""

from __future__ import annotations

from collections.abc import Sequence

RRF_K = 60


def rrf(rankings: Sequence[Sequence[str]], k: int = RRF_K) -> list[tuple[str, float]]:
    scores: dict[str, float] = {}
    for ranking in rankings:
        for rank, doc_id in enumerate(ranking, start=1):
            scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (k + rank)
    return sorted(scores.items(), key=lambda item: item[1], reverse=True)
