# stickybeak-agent — AI 购物助手与 MCP Server（Sprint 13–14）

Python 3.12 项目，同一个镜像部署成两个服务，都注册在 Nacos：

2026-10-27 补充：个人中心显式偏好记忆、Token 预算与历史摘录摘要、基于已购记录的双 Agent 推荐、可选 vLLM 与 K8s 演示。
实现边界、运行方式及实测记录见 [AI 增强验收](../docs/AI_ENHANCEMENTS.md)。

| 服务 | 启动命令 | 端口 | 网关路径 | 作用 |
|---|---|---|---|---|
| `stickybeak-agent` | `python -m stickybeak_agent` | 8087 | `/api/agent/**` | LangGraph 购物助手：导购、订单、售后问答，写操作需用户确认 |
| `stickybeak-mcp` | `python -m stickybeak_agent.mcp_server` | 8089 | `/api/mcp` | MCP Server：商城的只读能力，给自家 agent 和外部 AI 客户端用 |

常规聊天的只读工具（商品、订单查询、售后检索）从 MCP Server 加载；MCP Server 调用现有 Java 服务，并用 Elasticsearch 做售后知识库检索。
新增的有界协作节点通过同一个 JavaApi 适配器读取当前用户订单和候选商品。

```
浏览器 ──SSE──► 网关（JWT 鉴权、Sentinel 限流）──► stickybeak-agent ──MCP──► stickybeak-mcp ──► Java 服务 / ES
Cursor、Cherry Studio 等 ──MCP（Bearer 令牌）──► 网关 /api/mcp ──────────────────►┘

stickybeak-agent 内部：
                                                   │
       ┌─────────────── LangGraph 状态图 ───────────┴───────────────────────────┐
       │ route（意图识别）                                                       │
       │   ├─ shopping ⇄ 工具：search_products / get_product（MCP）/ add_to_cart │
       │   ├─ orders   ⇄ 工具：list_my_orders / get_order（MCP）/ cancel_order   │
       │   │     写操作 → confirm 节点 interrupt 暂停 → 前端确认后恢复执行       │
       │   ├─ after_sales：MCP search_faq（kNN + BM25 → RRF → 重排）→ 带引用回答  │
       │   └─ other                                                              │
       └──────────────────────────────────────────────────────────────────────────┘
          │ MCP / httpx（X-User-Id）    │ checkpoint            │ 限流 / 额度
          ▼                             ▼                       ▼
   stickybeak-mcp、cart / order     MySQL stickybeak_agent     Redis
```

## 设计要点

- **身份不由模型决定**：用户 ID 来自网关注入的 `X-User-Id`，通过运行配置注入工具，不是工具参数。模型（或被注入的提示词）没法指定别人的 ID。
  - 订单服务还会再校验一次订单归属。
  - 查不到和无权访问返回同一句话，避免泄露"这个订单号存在"。
- **写操作必须确认**：`add_to_cart`、`cancel_order` 先进入 `confirm` 节点，用 `interrupt` 暂停。
  - 确认卡片上的商品名、价格、订单金额由服务端查询，不使用模型的描述。
  - 用户拒绝，或没确认就发了新消息：都不会执行，模型会被告知"用户取消"。
- **对话可恢复**：对话状态用 `langgraph-checkpoint-mysql` 按 `thread_id` 存进 MySQL。刷新页面或服务重启后对话都能继续，等待确认的卡片也会恢复。
  - `thread_id` 由服务端生成，前缀是用户 ID，别人的对话一律返回 404。
- **售后回答有据可查**：
  - 检索流程：向量（text-embedding-v3）与关键词（IK BM25）两路各取 20 条，在应用层做 RRF 融合（ES 8.14 自带的 RRF 需要付费许可），再用 gte-rerank 重排。
  - 重排分数低于阈值的片段丢弃。一条不剩时直接回答"不知道"并给出客服邮箱，不调用模型。
  - 回答用 `[1]` 标注引用，来源一起推给前端。
- **模型可替换、可降级**：通义千问（主）和 DeepSeek（备）都走 OpenAI 兼容接口，每个模型单独设超时与重试。
  - 主模型超时、限流或 5xx 时切到备用模型。
  - 都不可用时返回友好提示，商城其他功能不受影响。
- **防提示词注入**：
  - 工具结果统一包成 `{"data": ...}`，提示词声明其中的指令一律不执行；知识库原文不能闭合 `<documents>` 标签。
  - 每个子 agent 只绑定自己的工具。
  - 结构上的兜底：写操作必须经过用户确认。
- **成本**：
  - 按用户限流（默认每分钟 10 次），每日请求数与 token 数都有额度，超出返回 429 和 `Retry-After`。Redis 不可用时拒绝服务，不放开额度。
  - 每次模型调用记录 token 用量与耗时（logger `stickybeak_agent.usage`）。
  - SSE 的 `done` 事件带回首字延迟和总耗时。
- **可观测**：设置 `SW_AGENT_COLLECTOR_BACKEND_SERVICES` 时启动 SkyWalking Python agent，和 Java 网关、下游 Java 服务串成一条跨语言链路。

## MCP Server（Sprint 14）

| 工具 | 登录 | 说明 |
|---|---|---|
| `search_products` | 否 | 关键词、分类、价格上限、排序 |
| `get_product` | 否 | 商品详情、价格、库存 |
| `search_faq` | 否 | 售后知识库检索，返回带出处的原文片段；空列表表示知识库没覆盖 |
| `list_my_orders` | 是 | 当前用户的订单 |
| `get_order` | 是 | 当前用户的单个订单 |

- 全部标注 `readOnlyHint`。写操作（加购、取消订单）不对外提供，只在自家 agent 里经用户在页面上确认后执行。
- 传输：Streamable HTTP，无状态 + JSON 响应。每个请求独立，可以直接放在网关和负载均衡后面。
- **身份**：工具参数里没有用户 ID，MCP Server 只认网关注入的 `X-User-Id`。
  - 未登录调用订单工具直接报错。
  - 订单服务还会再校验一次订单归属。
- **令牌**：网页登录的 access token 只有 15 分钟，配进 AI 客户端很快就失效。所以在个人中心"AI 客户端接入（MCP）"里生成专用令牌：
  - 有效期 30 天，claims 带 `scope=mcp`。
  - 网关只允许它访问 `/api/mcp`，拿它调其他接口（包括再签发令牌）返回 403。
  - 每个用户同时只有一个有效令牌，当前 jti 存在 Redis 的 `auth:mcp:{userId}`。重新生成或撤销后，旧令牌立即返回 401。
- agent 作为客户端：用 langchain-mcp-adapters 加载工具。
  - 拦截器从当前运行配置取出用户 ID，写进 `X-User-Id` 请求头。
  - `MCP_URL` 的主机名是服务名（`http://stickybeak-mcp/mcp`），每次请求经 Nacos 解析（`discovery.DiscoveryTransport`，类似 `lb://`）。
  - MCP 服务没起来时，agent 每隔几秒重试，期间助手返回"暂不可用"。

### 外部客户端接入

先在网站个人中心生成令牌。页面会给出可直接粘贴的配置；以下示例里的 `<token>` 换成生成的令牌。

**Cursor**：写入 `~/.cursor/mcp.json` 或项目内的 `.cursor/mcp.json`。

```json
{
  "mcpServers": {
    "stickybeak": {
      "url": "https://<你的域名>/api/mcp",
      "headers": { "Authorization": "Bearer <token>" }
    }
  }
}
```

**Cherry Studio**：设置 → MCP 服务器 → 添加。
- 类型选"可流式传输的 HTTP（streamableHttp）"。
- URL 填 `https://<你的域名>/api/mcp`。
- 请求头填 `Authorization=Bearer <token>`。

**Claude Code**：

```bash
claude mcp add --transport http stickybeak https://<你的域名>/api/mcp --header "Authorization: Bearer <token>"
```

**只支持 stdio 的客户端**：用 `npx mcp-remote https://<你的域名>/api/mcp --header "Authorization: Bearer <token>"` 转接。

**命令行演示**（官方 Python 客户端）：`uv run python -m stickybeak_agent.mcp_demo --url http://localhost:8080/api/mcp [--token <token>]`。下面是本地经网关的实际输出（节选）：

```
connected to stickybeak (protocol 2025-11-25)
  tool search_products  read-only  Search the StickyBeak catalogue (Australian-themed fridge magnets and souvenirs). Prices are AUD.
  tool list_my_orders   read-only  List the signed-in user's orders, newest first, optionally filtered by status. Requires login.
  ...
> search_products {"query": "UNSW", "size": 3}
{"data": {"total": 18, "items": [{"id": 1033, "slug": "aussie-uni-bus-stop-set-v3", "name": "土澳大学公交站牌冰箱贴套装 3", ...
> list_my_orders {}                       # 不带令牌
ERROR Error executing tool list_my_orders: login required: connect with a StickyBeak MCP token to use order tools
> list_my_orders {}                       # 带令牌
{"data": [{"orderNo": "SOIT20261024A1", "status": "cancelled", "payAmount": "9.90", "currency": "AUD", ...}]}
```

## 接口

| 方法 | 路径（经网关加 `/api` 前缀） | 说明 |
|---|---|---|
| GET | `/agent/health` | 公开；返回是否配置了模型 |
| POST | `/agent/chat` | `{message, threadId?}` → SSE |
| POST | `/agent/chat/{threadId}/resume` | `{approved}` → SSE，处理等待确认的写操作 |
| GET | `/agent/threads/{threadId}` | 刷新页面后恢复对话 |

SSE 事件：

| 事件 | 含义 |
|---|---|
| `meta` | 对话开始，带 threadId |
| `token` | 回答的增量文字 |
| `tool` | 正在调用的工具 |
| `sources` | 售后回答引用的资料 |
| `confirm` | 写操作等待确认，图已暂停 |
| `error` | 模型不可用等错误 |
| `done` | 结束，带 token 用量、首字延迟、总耗时 |

## 本地运行

Mac 的 Ollama 配置与首轮记录见 [Mac 本地模型测试](../docs/MAC_LOCAL_MODELS.md)；并发性能、vLLM Metal 与真实服务 / Kubernetes 验证见 [Mac 补测报告](../docs/MAC_VALIDATION_GAPS.md)。

```bash
cd stickybeak-agent
uv sync                                   # Python 3.12，依赖锁定在 uv.lock
export LLM_CHAIN=ollama,deepseek          # 降级顺序；可选 qwen、deepseek、openai、gemini、ollama
export OLLAMA_MODEL=qwen3.8 DEEPSEEK_API_KEY=sk-...   # 各家的 key / 模型见下方"主要配置"
export SERVICE_URLS="stickybeak-product=http://localhost:8082,stickybeak-cart=http://localhost:8083,stickybeak-order=http://localhost:8084"
export ES_URIS=http://localhost:19200 REDIS_PORT=16379   # 对应 docker-compose-infra 的宿主端口
uv run python -m stickybeak_agent.mcp_server   # MCP Server :8089（先启动）
uv run python -m stickybeak_agent         # :8087
```

- 不设置 `NACOS_ADDR` 时不注册到 Nacos，下游地址（含 `stickybeak-mcp`）取 `SERVICE_URLS`。网关那边用 simple discovery 指过来。
- `CHECKPOINT_BACKEND=memory` 可以不连 MySQL。

知识库入库：MCP Server 启动时自动做增量入库，也可以手动执行：

```bash
uv run python -m stickybeak_agent.rag.ingest            # 增量：只向量化新增或改动的片段
uv run python -m stickybeak_agent.rag.ingest --recreate # 改了 mapping 或换了向量模型时重建
```

语料在 `knowledge/{zh,en}/*.md`，按二级标题切分。退货期限、运费等运营政策是示例值，见 `knowledge/README.md`。

## 测试与评测

```bash
uv run ruff check . && uv run ruff format --check .
uv run pytest -q                  # 用脚本化的假模型，不调用真实模型；MCP Server 在进程内运行（ASGI），走真实的 MCP 协议
uv run python -m eval.run_eval    # 需要至少一个可用模型（云端 key 或本地 Ollama）；报告写到 eval/reports/<时间>/report.md
```

- 评测集共 64 条（`eval/dataset.yaml`）：导购 18、订单 10、售后 17（其中 2 条知识库没覆盖）、闲聊 3、越权 6、注入 6、偏好记忆 2、协作推荐 2。导购包含中英文泛推荐先追问、热门推荐不乱加关键词，以及补充偏好后的多轮对话。
- 指标：意图准确率、工具调用及参数正确率、先追问检查、确认正确率、RAG 召回率@k、回答忠实度（模型评审）、拒答正确率、越权泄露。先追问自动检查仅验证有问号且未调用工具，回答是否自然还需看报告原文。所有用例都检查"未确认的写操作没有执行"。
- 业务数据用 `eval/fixtures.py` 的固定数据。其中一个商品描述里藏了注入指令。
- 与生产一样，只读工具经（进程内的）MCP Server 调用。
- 知识库默认用内存版（`--kb memory`，不需要 ES）；`--kb es` 用真实索引。

## 主要配置（环境变量）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `LLM_CHAIN` | 空（= `LLM_PRIMARY,LLM_FALLBACK` = `qwen,deepseek`） | 降级顺序，逗号分隔：`qwen` `deepseek` `openai` `gemini` `ollama` `vllm`；没配 key 的云端模型跳过。本机商城使用 `openai,ollama` |
| `DASHSCOPE_API_KEY` / `DEEPSEEK_API_KEY` / `OPENAI_API_KEY` / `GEMINI_API_KEY` | 空 | 各家的 key；全部为空且链里没有 `ollama` 时助手返回"暂不可用" |
| `QWEN_MODEL` / `DEEPSEEK_MODEL` / `OPENAI_MODEL` / `GEMINI_MODEL` | `qwen-plus` / `deepseek-chat` / `gpt-4.1-mini` / `gemini-2.5-flash` | `*_BASE_URL` 可改成代理或兼容网关 |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | `http://localhost:11434/v1` / `qwen3` | 全栈 compose 里默认 `http://host.docker.internal:11434/v1` |
| `OLLAMA_REASONING_EFFORT` | `none` | 思考型模型默认关闭思考（否则每次调用多等几十秒）；留空用模型默认 |
| `VLLM_BASE_URL` / `VLLM_MODEL` / `VLLM_API_KEY` | `http://localhost:8000/v1` / `nvidia/Qwen3.8-27B-NVFP4` / `local-vllm` | 独立 GPU 实验，关闭思考；Docker 内访问宿主服务用 `http://host.docker.internal:8000/v1`，见 [27B 运行与实测](../docs/VLLM_27B.md) |
| `LLM_TIMEOUT_SECONDS` / `LLM_MAX_RETRIES` | 30 / 1 | |
| `EMBEDDING_PROVIDER` / `EMBEDDING_MODEL` | `qwen` / 按 provider 默认 | `qwen`→text-embedding-v3、`openai`→text-embedding-3-small、`gemini`→gemini-embedding-001、`ollama`→bge-m3、`none`；维度要等于 `EMBEDDING_DIMENSIONS`（1024），换模型后 MCP 启动时自动重新向量化 |
| `RERANK_PROVIDER` / `RERANK_MODEL` | `qwen` / `gte-rerank-v2` | 只有通义有重排接口；其他情况设 `none` |
| `RAG_MIN_RERANK_SCORE` | 0.35 | 有重排时：低于该分数的片段视为不相关 |
| `RAG_MIN_VECTOR_SCORE` | 0 | 没有重排时：向量相似度（(1+cos)/2）低于该值的片段丢掉，一条不剩就回答"不知道"。bge-m3 的校准值是 0.79 |
| `RATE_LIMIT_PER_MINUTE` / `DAILY_REQUEST_QUOTA` / `DAILY_TOKEN_QUOTA` | 10 / 100 / 200000 | |
| `MCP_URL` | `http://stickybeak-mcp/mcp` | agent 连接的 MCP Server，主机名是服务名时经 Nacos 解析 |
| `NACOS_ADDR`、`MYSQL_*`、`REDIS_*`、`ES_URIS` | 本机 | 与 Java 服务同名 |
| `SW_AGENT_COLLECTOR_BACKEND_SERVICES` | 空 | 设置后启用 SkyWalking |
