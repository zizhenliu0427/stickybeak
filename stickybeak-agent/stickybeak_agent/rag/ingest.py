"""知识库离线入库（增量）：python -m stickybeak_agent.rag.ingest [--recreate]

1. 读取 knowledge/ 下的语料并切分
2. 与索引中已有片段的内容哈希对比：新增或改动的片段才向量化并写入；语料里已删除的片段从索引删除
3. --recreate 删除索引重建（改了 mapping 或换了向量模型 / 维度时用）
"""

from __future__ import annotations

import argparse
import asyncio
import logging

from elasticsearch import AsyncElasticsearch

from ..config import get_settings
from .corpus import Chunk, load_corpus
from .embeddings import build_embeddings, embedding_signature
from .store import KnowledgeStore

log = logging.getLogger(__name__)


async def ingest(
    store: KnowledgeStore, chunks: list[Chunk], embeddings, recreate: bool = False, embedding_model: str = ""
) -> dict:
    """增量入库：内容变了，或生成向量的模型变了（包括之前没配向量模型）的片段才重新处理。"""
    await store.ensure_index(recreate=recreate)
    existing = {} if recreate else await store.existing_hashes()
    model = embedding_model if embeddings is not None else ""
    changed = [c for c in chunks if existing.get(c.id) != (c.content_hash, model)]
    removed = sorted(set(existing) - {c.id for c in chunks})

    vectors: list[list[float] | None] = [None] * len(changed)
    if changed and embeddings is not None:
        vectors = await embeddings.aembed_documents([c.embedding_input() for c in changed])
    if changed:
        await store.upsert(changed, vectors, model)
    await store.delete(removed)
    return {"total": len(chunks), "upserted": len(changed), "deleted": len(removed), "embedded": embeddings is not None}


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recreate", action="store_true")
    args = parser.parse_args()
    settings = get_settings()
    embeddings = build_embeddings(settings)
    if embeddings is None:
        log.warning("no embedding model configured (EMBEDDING_PROVIDER): indexing text only, vector search unavailable")
    es = AsyncElasticsearch(settings.es_uris.split(","))
    try:
        store = KnowledgeStore(es, settings.kb_index, settings.embedding_dimensions)
        print(
            await ingest(
                store,
                load_corpus(),
                embeddings,
                recreate=args.recreate,
                embedding_model=embedding_signature(settings),
            )
        )
    finally:
        await es.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    asyncio.run(main())
