"""知识库的 ES 索引：与商品搜索共用集群，单独一个索引。

- content / title / section：IK 中文分词（入库 ik_max_word、查询 ik_smart），另带 english 子字段给英文语料
- embedding：dense_vector（cosine，HNSW），用于 kNN
- lang：中英文语料各自一份，按提问语言过滤，避免同一知识点中英两份重复占位
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from typing import Any

from elasticsearch import AsyncElasticsearch
from elasticsearch.helpers import async_bulk

from .corpus import Chunk

_TEXT = {
    "type": "text",
    "analyzer": "ik_max_word",
    "search_analyzer": "ik_smart",
    "fields": {"en": {"type": "text", "analyzer": "english"}},
}


def index_body(dims: int) -> dict[str, Any]:
    return {
        "mappings": {
            "properties": {
                "doc_id": {"type": "keyword"},
                "lang": {"type": "keyword"},
                "source": {"type": "keyword"},
                "content_hash": {"type": "keyword"},
                "title": _TEXT,
                "section": _TEXT,
                "content": _TEXT,
                "embedding": {"type": "dense_vector", "dims": dims, "index": True, "similarity": "cosine"},
            }
        }
    }


class KnowledgeStore:
    def __init__(self, es: AsyncElasticsearch, index: str, dims: int):
        self._es = es
        self._index = index
        self._dims = dims

    async def ensure_index(self, recreate: bool = False) -> None:
        exists = await self._es.indices.exists(index=self._index)
        if exists and recreate:
            await self._es.indices.delete(index=self._index)
            exists = False
        if not exists:
            await self._es.indices.create(index=self._index, **index_body(self._dims))

    async def existing_hashes(self) -> dict[str, str]:
        resp = await self._es.search(index=self._index, size=10_000, source=["content_hash"], query={"match_all": {}})
        return {hit["_id"]: hit["_source"]["content_hash"] for hit in resp["hits"]["hits"]}

    async def upsert(self, chunks: Sequence[Chunk], vectors: Sequence[list[float] | None]) -> int:
        def actions() -> Iterable[dict]:
            for chunk, vector in zip(chunks, vectors, strict=True):
                source = {
                    "doc_id": chunk.doc_id,
                    "lang": chunk.lang,
                    "source": chunk.source,
                    "content_hash": chunk.content_hash,
                    "title": chunk.title,
                    "section": chunk.section,
                    "content": chunk.text,
                }
                if vector is not None:
                    source["embedding"] = vector
                yield {"_index": self._index, "_id": chunk.id, "_source": source}

        ok, _ = await async_bulk(self._es, actions(), refresh="wait_for")
        return ok

    async def delete(self, ids: Sequence[str]) -> None:
        if ids:
            await async_bulk(
                self._es, ({"_op_type": "delete", "_index": self._index, "_id": i} for i in ids), refresh="wait_for"
            )

    async def keyword_search(self, query: str, lang: str, size: int) -> list[dict]:
        resp = await self._es.search(
            index=self._index,
            size=size,
            source={"excludes": ["embedding"]},
            query={
                "bool": {
                    "must": {
                        "multi_match": {
                            "query": query,
                            "fields": ["title^2", "section^2", "content", "title.en^2", "section.en^2", "content.en"],
                        }
                    },
                    "filter": {"term": {"lang": lang}},
                }
            },
        )
        return [_hit(h) for h in resp["hits"]["hits"]]

    async def vector_search(self, vector: list[float], lang: str, size: int) -> list[dict]:
        resp = await self._es.search(
            index=self._index,
            size=size,
            source={"excludes": ["embedding"]},
            knn={
                "field": "embedding",
                "query_vector": vector,
                "k": size,
                "num_candidates": max(100, size * 5),
                "filter": {"term": {"lang": lang}},
            },
        )
        return [_hit(h) for h in resp["hits"]["hits"]]


def _hit(hit: dict) -> dict:
    src = hit["_source"]
    return {
        "id": hit["_id"],
        "doc_id": src["doc_id"],
        "lang": src["lang"],
        "source": src["source"],
        "title": src["title"],
        "section": src["section"],
        "content": src["content"],
    }
