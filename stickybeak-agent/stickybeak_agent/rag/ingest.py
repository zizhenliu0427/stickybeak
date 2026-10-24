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
from .embeddings import build_embeddings
from .store import KnowledgeStore

log = logging.getLogger(__name__)


async def ingest(store: KnowledgeStore, chunks: list[Chunk], embeddings, recreate: bool = False) -> dict:
    await store.ensure_index(recreate=recreate)
    existing = {} if recreate else await store.existing_hashes()
    changed = [c for c in chunks if existing.get(c.id) != c.content_hash]
    removed = sorted(set(existing) - {c.id for c in chunks})

    vectors: list[list[float] | None] = [None] * len(changed)
    if changed and embeddings is not None:
        vectors = await embeddings.aembed_documents([c.embedding_input() for c in changed])
    if changed:
        await store.upsert(changed, vectors)
    await store.delete(removed)
    return {"total": len(chunks), "upserted": len(changed), "deleted": len(removed), "embedded": embeddings is not None}


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recreate", action="store_true")
    args = parser.parse_args()
    settings = get_settings()
    embeddings = build_embeddings(settings)
    if embeddings is None:
        log.warning("DASHSCOPE_API_KEY not set: indexing text only, vector search will be unavailable")
    es = AsyncElasticsearch(settings.es_uris.split(","))
    try:
        store = KnowledgeStore(es, settings.kb_index, settings.embedding_dimensions)
        print(await ingest(store, load_corpus(), embeddings, recreate=args.recreate))
    finally:
        await es.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    asyncio.run(main())
