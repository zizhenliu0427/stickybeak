"""配置全部来自环境变量（与 Java 服务同名的沿用同名：MYSQL_HOST、REDIS_HOST、ES_URIS、NACOS_ADDR）。"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "stickybeak-agent"
    app_port: int = 8087

    # 服务发现：NACOS_ADDR 为空时不注册，下游地址取 SERVICE_URLS（本地调试 / 单测）
    nacos_addr: str = ""
    nacos_username: str = ""
    nacos_password: str = ""
    # 注册到 Nacos 的 IP；为空时自动取本机出口 IP
    advertise_ip: str = ""
    service_urls: str = (
        "stickybeak-product=http://localhost:8082,"
        "stickybeak-cart=http://localhost:8083,"
        "stickybeak-order=http://localhost:8084,"
        "stickybeak-mcp=http://localhost:8089"
    )
    downstream_timeout_seconds: float = 5.0

    # MCP 服务（Sprint 14）：商城的只读能力（商品、订单查询、售后检索）由它提供，agent 与外部 AI 客户端共用。
    # MCP_URL 的主机名是服务名时按 Nacos / SERVICE_URLS 解析（见 discovery.DiscoveryTransport）
    mcp_service_name: str = "stickybeak-mcp"
    mcp_port: int = 8089
    mcp_url: str = "http://stickybeak-mcp/mcp"

    # 模型：主用通义千问，DeepSeek 备用；都走 OpenAI 兼容接口
    llm_primary: str = "qwen"
    llm_fallback: str = "deepseek"
    llm_timeout_seconds: float = 30.0
    llm_max_retries: int = 1
    llm_temperature: float = 0.2
    dashscope_api_key: str = ""
    qwen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    qwen_model: str = "qwen-plus"
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    # RAG：通义 text-embedding-v3 + gte-rerank；RERANK_MODEL 为空时只用 RRF 融合结果
    embedding_model: str = "text-embedding-v3"
    embedding_dimensions: int = 1024
    rerank_model: str = "gte-rerank-v2"
    rerank_url: str = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"
    es_uris: str = "http://localhost:9200"
    kb_index: str = "stickybeak_kb"
    rag_candidates: int = 20
    rag_top_k: int = 4
    # 重排分低于该值的片段视为不相关；一个都不剩时直接回答"不知道"，不调用模型
    rag_min_rerank_score: float = 0.35
    # 启动时增量入库（只处理新增或改动的片段）
    kb_ingest_on_startup: bool = True

    # 对话状态：mysql（生产）或 memory（单测 / 本地无库时）
    checkpoint_backend: str = "mysql"
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "root"
    agent_db: str = "stickybeak_agent"

    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""

    # 安全与成本（13.6）
    rate_limit_per_minute: int = 10
    daily_request_quota: int = 100
    daily_token_quota: int = 200_000
    max_message_chars: int = 1000

    def static_service_urls(self) -> dict[str, str]:
        urls: dict[str, str] = {}
        for pair in self.service_urls.split(","):
            if "=" in pair:
                name, url = pair.split("=", 1)
                urls[name.strip()] = url.strip().rstrip("/")
        return urls


@lru_cache
def get_settings() -> Settings:
    return Settings()
