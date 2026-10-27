"""模型接入（Issue 13.2）。

通义千问（DashScope）、DeepSeek、OpenAI、Gemini、本地 Ollama 都提供 OpenAI 兼容接口，统一用 ChatOpenAI 接入，
换模型只改环境变量（LLM_CHAIN 与各家的 *_API_KEY / *_MODEL）。
- 按 LLM_CHAIN 的顺序降级：前一个报错（超时、限流、5xx、连不上、key 无效）时用下一个
- 每个模型单独设超时与重试；全部没配置或全部失败时，调用方返回友好提示，不影响商城其他功能
"""

from __future__ import annotations

import logging
from collections.abc import Sequence
from dataclasses import dataclass, field
from typing import Any

import httpx
import openai
from langchain_core.language_models import BaseChatModel
from langchain_core.runnables import Runnable
from langchain_openai import ChatOpenAI

from .config import Settings

log = logging.getLogger(__name__)

# 模型侧的这些错误都按"模型暂不可用"处理（参数错误等 4xx 不在此列，属于代码问题，应当暴露）
MODEL_UNAVAILABLE_ERRORS: tuple[type[BaseException], ...] = (
    openai.APITimeoutError,
    openai.APIConnectionError,
    openai.RateLimitError,
    openai.InternalServerError,
    openai.AuthenticationError,
    httpx.TimeoutException,
    httpx.ConnectError,
)


class ModelUnavailableError(Exception):
    """所有模型都不可用。"""


PROVIDERS = ("qwen", "deepseek", "openai", "gemini", "ollama", "vllm")


@dataclass(frozen=True)
class ProviderEndpoint:
    api_key: str
    base_url: str
    model: str
    # 只发给这一家的额外请求参数（如 Ollama 关闭思考）
    extra_body: dict[str, Any] = field(default_factory=dict)


def provider_endpoint(settings: Settings, provider: str) -> ProviderEndpoint | None:
    """provider 的连接参数；云端 provider 没配 key 时返回 None（跳过）。"""
    if provider == "qwen":
        endpoint = ProviderEndpoint(settings.dashscope_api_key, settings.qwen_base_url, settings.qwen_model)
    elif provider == "deepseek":
        endpoint = ProviderEndpoint(settings.deepseek_api_key, settings.deepseek_base_url, settings.deepseek_model)
    elif provider == "openai":
        endpoint = ProviderEndpoint(settings.openai_api_key, settings.openai_base_url, settings.openai_model)
    elif provider == "gemini":
        endpoint = ProviderEndpoint(settings.gemini_api_key, settings.gemini_base_url, settings.gemini_model)
    elif provider == "vllm":
        return ProviderEndpoint(
            settings.vllm_api_key,
            settings.vllm_base_url,
            settings.vllm_model,
            {"chat_template_kwargs": {"enable_thinking": False}},
        )
    elif provider == "ollama":
        # Ollama 不校验 key，但 OpenAI SDK 要求非空
        extra = {"reasoning_effort": settings.ollama_reasoning_effort} if settings.ollama_reasoning_effort else {}
        return ProviderEndpoint("ollama", settings.ollama_base_url, settings.ollama_model, extra)
    else:
        raise ValueError(f"unknown LLM provider: {provider} (expected one of {', '.join(PROVIDERS)})")
    if not endpoint.api_key:
        return None
    return endpoint


def provider_chain(settings: Settings) -> list[str]:
    raw = settings.llm_chain or f"{settings.llm_primary},{settings.llm_fallback}"
    chain: list[str] = []
    for name in (p.strip().lower() for p in raw.split(",")):
        if name and name not in chain:
            chain.append(name)
    return chain


class ModelProvider:
    def __init__(self, settings: Settings, models: Sequence[BaseChatModel] | None = None):
        if models is None:
            models = [m for m in (self._build(settings, p) for p in provider_chain(settings)) if m is not None]
        self._models = list(models)

    @property
    def available(self) -> bool:
        return bool(self._models)

    @property
    def model_names(self) -> list[str]:
        return [getattr(m, "model_name", type(m).__name__) for m in self._models]

    def chat(self, tools: Sequence[Any] | None = None) -> Runnable:
        """绑定工具后的模型，主模型失败时自动切到备用模型。"""
        if not self._models:
            raise ModelUnavailableError("no model configured")
        bound = [m.bind_tools(tools) if tools else m for m in self._models]
        return self._with_fallbacks(bound)

    def structured(self, schema: type) -> Runnable:
        # DashScope 兼容模式不支持 response_format=json_schema，用 function calling 拿结构化输出
        if not self._models:
            raise ModelUnavailableError("no model configured")
        bound = [m.with_structured_output(schema, method="function_calling") for m in self._models]
        return self._with_fallbacks(bound)

    @staticmethod
    def _with_fallbacks(runnables: list[Runnable]) -> Runnable:
        if len(runnables) == 1:
            return runnables[0]
        return runnables[0].with_fallbacks(runnables[1:], exceptions_to_handle=MODEL_UNAVAILABLE_ERRORS)

    @staticmethod
    def _build(settings: Settings, provider: str) -> BaseChatModel | None:
        endpoint = provider_endpoint(settings, provider)
        if endpoint is None:
            log.warning("LLM provider %s has no API key, skipped", provider)
            return None
        return ChatOpenAI(
            model=endpoint.model,
            base_url=endpoint.base_url,
            api_key=endpoint.api_key,
            temperature=settings.llm_temperature,
            timeout=settings.llm_timeout_seconds,
            max_retries=settings.llm_max_retries,
            max_tokens=settings.llm_max_output_tokens,
            streaming=True,
            # 流式时也返回 token 用量（13.6 记录成本）
            stream_usage=True,
            extra_body=endpoint.extra_body or None,
        )
