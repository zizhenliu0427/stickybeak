import pytest

from stickybeak_agent.config import Settings
from stickybeak_agent.llm import ModelProvider, provider_chain, provider_endpoint
from stickybeak_agent.rag.embeddings import build_embeddings, embedding_endpoint, embedding_signature
from stickybeak_agent.rag.rerank import Reranker


def test_chain_defaults_to_the_legacy_primary_and_fallback():
    assert provider_chain(Settings()) == ["qwen", "deepseek"]
    assert provider_chain(Settings(llm_chain=" Ollama, gemini ,ollama,openai ")) == ["ollama", "gemini", "openai"]


def test_cloud_providers_without_a_key_are_skipped_and_order_is_kept():
    settings = Settings(
        llm_chain="openai,qwen,gemini,deepseek",
        openai_api_key="sk-test",
        gemini_api_key="g-test",
        openai_model="gpt-test",
        gemini_model="gemini-test",
    )
    provider = ModelProvider(settings)
    assert provider.available and provider.model_names == ["gpt-test", "gemini-test"]


def test_ollama_needs_no_key_and_turns_thinking_off_by_default():
    endpoint = provider_endpoint(Settings(ollama_model="qwen3.8"), "ollama")
    assert endpoint.model == "qwen3.8" and endpoint.extra_body == {"reasoning_effort": "none"}
    assert provider_endpoint(Settings(ollama_reasoning_effort=""), "ollama").extra_body == {}
    assert ModelProvider(Settings(llm_chain="ollama", ollama_model="qwen3.8")).model_names == ["qwen3.8"]


def test_unknown_provider_is_a_configuration_error():
    with pytest.raises(ValueError, match="unknown LLM provider"):
        ModelProvider(Settings(llm_chain="claude-local"))


def test_nothing_configured_means_no_model():
    assert not ModelProvider(Settings()).available


def test_embedding_provider_selection():
    assert embedding_endpoint(Settings()) is None  # 默认 qwen，但没有 key
    assert embedding_endpoint(Settings(embedding_provider="none", dashscope_api_key="k")) is None
    qwen = embedding_endpoint(Settings(dashscope_api_key="k"))
    assert (qwen.model, qwen.batch_size, qwen.send_dimensions) == ("text-embedding-v3", 10, True)
    openai = embedding_endpoint(Settings(embedding_provider="openai", openai_api_key="k"))
    assert openai.model == "text-embedding-3-small"
    gemini = embedding_endpoint(Settings(embedding_provider="gemini", gemini_api_key="k", embedding_model="x-embed"))
    assert gemini.model == "x-embed" and gemini.base_url.startswith("https://generativelanguage.googleapis.com")
    ollama = embedding_endpoint(Settings(embedding_provider="ollama"))
    assert (ollama.model, ollama.send_dimensions) == ("bge-m3", False)
    with pytest.raises(ValueError):
        embedding_endpoint(Settings(embedding_provider="word2vec"))


def test_embedding_signature_changes_with_the_model():
    assert embedding_signature(Settings()) == ""
    assert embedding_signature(Settings(embedding_provider="ollama")) == "ollama:bge-m3:1024"
    assert build_embeddings(Settings(embedding_provider="ollama")).dimensions is None
    assert build_embeddings(Settings(embedding_provider="openai", openai_api_key="k")).dimensions == 1024


def test_rerank_only_runs_with_dashscope():
    assert Reranker(Settings(dashscope_api_key="k"), None).enabled
    assert not Reranker(Settings(dashscope_api_key="k", rerank_provider="none"), None).enabled
    assert not Reranker(Settings(rerank_provider="qwen"), None).enabled
