"""向量化（OpenAI 兼容接口）：EMBEDDING_PROVIDER 选 qwen / openai / gemini / ollama，或 none 只走关键词检索。

所需 key 没有配置时返回 None，检索退化为纯关键词。向量维度必须与 ES 索引的 dense_vector 一致（EMBEDDING_DIMENSIONS）：
通义、OpenAI、Gemini 按 dimensions 参数输出；Ollama 不支持该参数，要选原生维度一致的模型（bge-m3 正好 1024）。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from langchain_core.embeddings import Embeddings
from langchain_openai import OpenAIEmbeddings

from ..config import Settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class EmbeddingEndpoint:
    provider: str
    api_key: str
    base_url: str
    model: str
    # 每次请求最多几条（通义 text-embedding-v3 限 10 条）
    batch_size: int
    # 是否发送 dimensions 参数
    send_dimensions: bool = True


DEFAULT_MODELS = {
    "qwen": "text-embedding-v3",
    "openai": "text-embedding-3-small",
    "gemini": "gemini-embedding-001",
    "ollama": "bge-m3",
}


def embedding_endpoint(settings: Settings) -> EmbeddingEndpoint | None:
    provider = settings.embedding_provider.strip().lower()
    if provider in ("", "none"):
        return None
    if provider not in DEFAULT_MODELS:
        raise ValueError(f"unknown embedding provider: {provider} (expected one of {', '.join(DEFAULT_MODELS)}, none)")
    model = settings.embedding_model or DEFAULT_MODELS[provider]
    if provider == "qwen":
        endpoint = EmbeddingEndpoint(provider, settings.dashscope_api_key, settings.qwen_base_url, model, 10)
    elif provider == "openai":
        endpoint = EmbeddingEndpoint(provider, settings.openai_api_key, settings.openai_base_url, model, 256)
    elif provider == "gemini":
        endpoint = EmbeddingEndpoint(provider, settings.gemini_api_key, settings.gemini_base_url, model, 100)
    else:
        return EmbeddingEndpoint(provider, "ollama", settings.ollama_base_url, model, 32, send_dimensions=False)
    if not endpoint.api_key:
        log.warning("embedding provider %s has no API key: vector search disabled", provider)
        return None
    return endpoint


def embedding_signature(settings: Settings) -> str:
    """当前向量模型的标识，存进索引；换了模型（或从无到有）时入库会重新向量化所有片段。"""
    endpoint = embedding_endpoint(settings)
    if endpoint is None:
        return ""
    return f"{endpoint.provider}:{endpoint.model}:{settings.embedding_dimensions}"


def build_embeddings(settings: Settings) -> Embeddings | None:
    endpoint = embedding_endpoint(settings)
    if endpoint is None:
        return None
    return OpenAIEmbeddings(
        model=endpoint.model,
        base_url=endpoint.base_url,
        api_key=endpoint.api_key,
        dimensions=settings.embedding_dimensions if endpoint.send_dimensions else None,
        # 兼容接口只接受原始文本，不接受 tiktoken 切出来的 token id
        check_embedding_ctx_length=False,
        chunk_size=endpoint.batch_size,
        timeout=settings.llm_timeout_seconds,
        max_retries=settings.llm_max_retries,
    )
