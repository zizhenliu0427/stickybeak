import httpx

from stickybeak_agent.config import Settings
from stickybeak_agent.rag.corpus import MAX_CHARS, load_corpus, split_markdown
from stickybeak_agent.rag.fusion import rrf
from stickybeak_agent.rag.ingest import ingest
from stickybeak_agent.rag.retriever import HybridRetriever, detect_lang
from stickybeak_agent.rag.store import index_body


def test_rrf_rewards_documents_found_by_both_retrievers():
    fused = rrf([["a", "b", "c"], ["c", "d", "a"]])
    order = [doc_id for doc_id, _ in fused]
    assert order[:2] == ["a", "c"]  # 两路都命中的排前面
    assert set(order) == {"a", "b", "c", "d"}


def test_rrf_handles_one_empty_ranking():
    assert [d for d, _ in rrf([["x", "y"], []])] == ["x", "y"]


def test_split_markdown_by_second_level_headings():
    md = "# 退换货\n\n导语一句。\n\n## 退款多久到账\n五到十天。\n\n## 可以换货吗\n不可以。\n"
    chunks = split_markdown(md, doc_id="returns", lang="zh", source="zh/returns.md")
    assert [(c.title, c.section, c.text) for c in chunks] == [
        ("退换货", "退换货", "导语一句。"),
        ("退换货", "退款多久到账", "五到十天。"),
        ("退换货", "可以换货吗", "不可以。"),
    ]
    assert len({c.id for c in chunks}) == 3


def test_long_sections_are_split_on_paragraphs():
    body = "\n\n".join("段落" * 100 for _ in range(5))
    chunks = split_markdown(f"# T\n\n## S\n{body}", doc_id="t", lang="zh", source="zh/t.md")
    assert len(chunks) > 1 and all(len(c.text) <= MAX_CHARS for c in chunks)
    assert all(c.section == "S" for c in chunks)


def test_bundled_corpus_has_matching_zh_and_en_documents():
    chunks = load_corpus()
    zh = {c.doc_id for c in chunks if c.lang == "zh"}
    en = {c.doc_id for c in chunks if c.lang == "en"}
    assert zh == en and len(zh) >= 7
    # 同一知识点中英文各一份，切出来的片段数一致
    assert sum(c.lang == "zh" for c in chunks) == sum(c.lang == "en" for c in chunks)


def test_detect_lang():
    assert detect_lang("退款多久到账") == "zh"
    assert detect_lang("How long do refunds take?") == "en"


def test_index_mapping_uses_ik_and_dense_vector():
    props = index_body(1024)["mappings"]["properties"]
    assert props["content"]["analyzer"] == "ik_max_word"
    assert props["embedding"] == {"type": "dense_vector", "dims": 1024, "index": True, "similarity": "cosine"}


def passage(pid):
    return {
        "id": pid,
        "doc_id": pid,
        "lang": "zh",
        "source": f"zh/{pid}.md",
        "title": "T",
        "section": pid,
        "content": f"content {pid}",
    }


class FakeStore:
    def __init__(self, keyword=(), vector=()):
        self.keyword = [passage(p) for p in keyword]
        self.vector = [passage(p) for p in vector]
        self.calls = []

    async def keyword_search(self, query, lang, size):
        self.calls.append(("keyword", lang))
        return self.keyword

    async def vector_search(self, vector, lang, size):
        self.calls.append(("vector", lang))
        return self.vector


class FakeEmbeddings:
    async def aembed_query(self, text):
        return [0.1, 0.2]


class FakeReranker:
    enabled = True

    def __init__(self, scores=None, error=None):
        self._scores = scores or {}
        self._error = error

    async def scores(self, query, documents):
        if self._error:
            raise self._error
        return [self._scores.get(doc.split("\n")[0], 0.0) for doc in documents]


async def test_retriever_fuses_both_paths_and_filters_by_rerank_score():
    store = FakeStore(keyword=["a", "b"], vector=["b", "c"])
    retriever = HybridRetriever(
        Settings(rag_top_k=2, rag_min_rerank_score=0.5),
        store,
        FakeEmbeddings(),
        FakeReranker({"a": 0.2, "b": 0.6, "c": 0.9}),
    )
    result = await retriever.retrieve("退款")
    assert [p["id"] for p in result.passages] == ["c", "b"]
    assert result.reranked and store.calls == [("keyword", "zh"), ("vector", "zh")]


async def test_retriever_returns_nothing_when_all_scores_are_below_threshold():
    retriever = HybridRetriever(
        Settings(rag_min_rerank_score=0.5), FakeStore(keyword=["a"]), None, FakeReranker({"a": 0.1})
    )
    assert (await retriever.retrieve("比特币")).passages == []


async def test_retriever_without_embeddings_uses_keyword_only():
    store = FakeStore(keyword=["a"], vector=["z"])
    retriever = HybridRetriever(Settings(), store, None, None)
    result = await retriever.retrieve("refund")
    assert [p["id"] for p in result.passages] == ["a"] and store.calls == [("keyword", "en")]


async def test_retriever_falls_back_to_rrf_order_when_rerank_fails():
    retriever = HybridRetriever(
        Settings(rag_top_k=1), FakeStore(keyword=["a", "b"]), None, FakeReranker(error=httpx.ConnectError("down"))
    )
    result = await retriever.retrieve("退款")
    assert [p["id"] for p in result.passages] == ["a"] and not result.reranked


class MemoryKnowledgeStore:
    def __init__(self):
        self.docs = {}
        self.embedded_batches = []

    async def ensure_index(self, recreate=False):
        if recreate:
            self.docs.clear()

    async def existing_hashes(self):
        return {i: d["hash"] for i, d in self.docs.items()}

    async def upsert(self, chunks, vectors):
        for c, v in zip(chunks, vectors, strict=True):
            self.docs[c.id] = {"hash": c.content_hash, "vector": v}

    async def delete(self, ids):
        for i in ids:
            self.docs.pop(i, None)


class CountingEmbeddings:
    def __init__(self):
        self.batches = []

    async def aembed_documents(self, texts):
        self.batches.append(len(texts))
        return [[0.0] for _ in texts]


async def test_ingest_only_embeds_new_or_changed_chunks_and_removes_deleted():
    store, embeddings = MemoryKnowledgeStore(), CountingEmbeddings()
    v1 = split_markdown("# T\n## A\na\n## B\nb\n## C\nc", doc_id="t", lang="zh", source="zh/t.md")
    assert (await ingest(store, v1, embeddings))["upserted"] == 3

    # 改了 B，删了 C
    v2 = split_markdown("# T\n## A\na\n## B\nb changed", doc_id="t", lang="zh", source="zh/t.md")
    report = await ingest(store, v2, embeddings)
    assert report == {"total": 2, "upserted": 1, "deleted": 1, "embedded": True}
    assert embeddings.batches == [3, 1] and len(store.docs) == 2
