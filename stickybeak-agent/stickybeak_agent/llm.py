"""模型接入（Issue 13.2）。

通义千问（DashScope）与 DeepSeek 都提供 OpenAI 兼容接口，统一用 ChatOpenAI 接入，换模型只改环境变量。
- 每个模型单独设超时与重试；主模型报错（超时、限流、5xx、连不上）时切到备用模型
- 两个都没配置 key 或都失败时，调用方返回友好提示，不影响商城其他功能
"""

from __future__ import annotations

import logging
from collections.abc import Sequence
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


class ModelProvider:
    def __init__(self, settings: Settings, models: Sequence[BaseChatModel] | None = None):
        if models is None:
            models = [
                m
                for m in (self._build(settings, settings.llm_primary), self._build(settings, settings.llm_fallback))
                if m is not None
            ]
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
        provider = (provider or "").strip().lower()
        if provider == "qwen":
            key, base_url, model = settings.dashscope_api_key, settings.qwen_base_url, settings.qwen_model
        elif provider == "deepseek":
            key, base_url, model = settings.deepseek_api_key, settings.deepseek_base_url, settings.deepseek_model
        elif provider:
            raise ValueError(f"unknown LLM provider: {provider}")
        else:
            return None
        if not key:
            log.warning("LLM provider %s has no API key, skipped", provider)
            return None
        return ChatOpenAI(
            model=model,
            base_url=base_url,
            api_key=key,
            temperature=settings.llm_temperature,
            timeout=settings.llm_timeout_seconds,
            max_retries=settings.llm_max_retries,
            streaming=True,
            # 流式时也返回 token 用量（13.6 记录成本）
            stream_usage=True,
        )
