"""向量化：通义 text-embedding-v3（OpenAI 兼容接口）。没有配置 key 时返回 None，检索退化为纯关键词。"""

from __future__ import annotations

from langchain_core.embeddings import Embeddings
from langchain_openai import OpenAIEmbeddings

from ..config import Settings


def build_embeddings(settings: Settings) -> Embeddings | None:
    if not settings.dashscope_api_key:
        return None
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        base_url=settings.qwen_base_url,
        api_key=settings.dashscope_api_key,
        dimensions=settings.embedding_dimensions,
        # DashScope 只接受原始文本，不接受 tiktoken 切出来的 token id
        check_embedding_ctx_length=False,
        # text-embedding-v3 每次最多 10 条
        chunk_size=10,
        timeout=settings.llm_timeout_seconds,
        max_retries=settings.llm_max_retries,
    )
