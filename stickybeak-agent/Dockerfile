# StickyBeak AI 助手与 MCP Server（Python）共用一个镜像。构建上下文为本目录：docker build -t stickybeak-agent stickybeak-agent
# 默认启动 agent（:8087）；MCP Server 用 command: python -m stickybeak_agent.mcp_server（:8089）
FROM ghcr.io/astral-sh/uv:0.11.32 AS uv

FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    PATH="/app/.venv/bin:$PATH"
COPY --from=uv /uv /usr/local/bin/uv
WORKDIR /app
# 先只装依赖，代码改动不会让依赖层失效
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY stickybeak_agent ./stickybeak_agent
COPY knowledge ./knowledge
RUN uv sync --frozen --no-dev
# 设置 SW_AGENT_COLLECTOR_BACKEND_SERVICES（如 oap:11800）时启用 SkyWalking Python agent，见 stickybeak_agent/__main__.py
ENV SW_AGENT_NAME=stickybeak-agent
EXPOSE 8087 8089
CMD ["python", "-m", "stickybeak_agent"]
