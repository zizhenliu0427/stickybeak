# StickyBeak AI 助手与 MCP Server（Python）共用一个镜像。构建上下文为本目录：docker build -t stickybeak-agent stickybeak-agent
# 默认启动 agent（:8087）；MCP Server 用 command: python -m stickybeak_agent.mcp_server（:8089）
FROM ghcr.io/astral-sh/uv:0.11.32 AS uv

FROM python:3.12-slim AS build
ENV PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    PATH="/app/.venv/bin:$PATH"
COPY --from=uv /uv /usr/local/bin/uv
WORKDIR /app
# nacos-sdk-python 锁定的 psutil 5.9.5 没有 Linux ARM64 wheel，需要在构建阶段编译。
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc libc6-dev \
    && rm -rf /var/lib/apt/lists/*
# 先只装依赖，代码改动不会让依赖层失效
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
# 将 tokenizer 数据打进镜像，集群首次请求无需临时访问公网。
ENV TIKTOKEN_CACHE_DIR=/app/tokenizers
RUN .venv/bin/python -c "import tiktoken; tiktoken.get_encoding('cl100k_base')"
COPY stickybeak_agent ./stickybeak_agent
COPY knowledge ./knowledge
RUN uv sync --frozen --no-dev

FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    PATH="/app/.venv/bin:$PATH" \
    TIKTOKEN_CACHE_DIR=/app/tokenizers
WORKDIR /app
COPY --from=uv /uv /usr/local/bin/uv
COPY --from=build /app /app
# 设置 SW_AGENT_COLLECTOR_BACKEND_SERVICES（如 oap:11800）时启用 SkyWalking Python agent，见 stickybeak_agent/__main__.py
ENV SW_AGENT_NAME=stickybeak-agent
EXPOSE 8087 8089
CMD ["python", "-m", "stickybeak_agent"]
