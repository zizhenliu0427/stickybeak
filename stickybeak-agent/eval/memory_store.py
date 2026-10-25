"""评测用的内存知识库：没有 ES 时也能跑评测（--kb memory）。

接口与 KnowledgeStore 的检索方法一致：
- keyword_search：BM25，中文按字二元组切分、英文按单词（近似 IK 分词，不完全等价）
- vector_search：用同一个向量模型给语料向量化后做余弦相似度（与 ES dense_vector cosine 一致）
用它测出的召回率反映"语料 + 向量模型 + 融合 + 重排"的效果；上线前应当用 --kb es 在真实索引上再跑一次。
"""

from __future__ import annotations

import math
import re
from collections import Counter

from stickybeak_agent.rag.corpus import Chunk

_WORD = re.compile(r"[a-z0-9]+")
_CJK_RUN = re.compile(r"[\u4e00-\u9fff]+")
_STOP = set("the a an to of and or is are do does can i my you your for in on it how what if be with we".split())


def tokenize(text: str) -> list[str]:
    text = text.lower()
    tokens = [w for w in _WORD.findall(text) if w not in _STOP]
    for run in _CJK_RUN.findall(text):
        tokens += [run[i : i + 2] for i in range(len(run) - 1)] or [run]
    return tokens


class MemoryKnowledgeStore:
    def __init__(self, chunks: list[Chunk], embeddings=None, k1: float = 1.5, b: float = 0.75):
        self._chunks = chunks
        self._embeddings = embeddings
        self._vectors: list[list[float]] | None = None
        self._docs = [Counter(tokenize(c.embedding_input())) for c in chunks]
        self._lengths = [sum(d.values()) for d in self._docs]
        self._avg = sum(self._lengths) / max(len(self._lengths), 1)
        df = Counter(t for d in self._docs for t in d)
        n = len(chunks)
        self._idf = {t: math.log(1 + (n - f + 0.5) / (f + 0.5)) for t, f in df.items()}
        self._k1, self._b = k1, b

    @staticmethod
    def _passage(chunk: Chunk) -> dict:
        return {
            "id": chunk.id,
            "doc_id": chunk.doc_id,
            "lang": chunk.lang,
            "source": chunk.source,
            "title": chunk.title,
            "section": chunk.section,
            "content": chunk.text,
        }

    async def keyword_search(self, query: str, lang: str, size: int) -> list[dict]:
        terms = tokenize(query)
        scored = []
        for i, chunk in enumerate(self._chunks):
            if chunk.lang != lang:
                continue
            doc, length = self._docs[i], self._lengths[i]
            score = sum(
                self._idf.get(t, 0)
                * doc[t]
                * (self._k1 + 1)
                / (doc[t] + self._k1 * (1 - self._b + self._b * length / self._avg))
                for t in terms
                if t in doc
            )
            if score > 0:
                scored.append((score, chunk))
        scored.sort(key=lambda x: x[0], reverse=True)
        return [self._passage(c) for _, c in scored[:size]]

    async def vector_search(self, vector: list[float], lang: str, size: int) -> list[dict]:
        if self._vectors is None:
            self._vectors = await self._embeddings.aembed_documents([c.embedding_input() for c in self._chunks])
        scored = [(_cosine(vector, v), c) for v, c in zip(self._vectors, self._chunks, strict=True) if c.lang == lang]
        scored.sort(key=lambda x: x[0], reverse=True)
        return [self._passage(c) for _, c in scored[:size]]


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b, strict=True))
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return dot / norm if norm else 0.0
