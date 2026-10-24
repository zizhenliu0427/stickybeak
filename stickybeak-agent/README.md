# stickybeak-agent — AI 购物助手与 MCP Server（Sprint 13–14）

Python 3.12 项目，同一个镜像部署成两个服务，都注册在 Nacos：

| 服务 | 启动命令 | 端口 | 网关路径 | 作用 |
|---|---|---|---|---|
| `stickybeak-agent` | `python -m stickybeak_agent` | 8087 | `/api/agent/**` | LangGraph 购物助手：导购、订单、售后问答，写操作需用户确认 |
| `stickybeak-mcp` | `python -m stickybeak_agent.mcp_server` | 8089 | `/api/mcp` | MCP Server：商城的只读能力，给自家 agent 和外部 AI 客户端用 |

agent 的只读工具（商品、订单查询、售后检索）都从 MCP Server 加载；MCP Server 调用现有 Java 服务，并用 Elasticsearch 做售后知识库检索。

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

```bash
cd stickybeak-agent
uv sync                                   # Python 3.12，依赖锁定在 uv.lock
export DASHSCOPE_API_KEY=sk-...           # 通义千问；可选 DEEPSEEK_API_KEY 作为备用
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
uv run python -m eval.run_eval    # 需要模型 key；报告写到 eval/reports/<时间>/report.md
```

- 评测集共 50 条（`eval/dataset.yaml`）：导购 10、订单 10、售后 15（其中 2 条知识库没覆盖）、闲聊 3、越权 6、注入 6。
- 指标：意图准确率、工具调用正确率、确认正确率、RAG 召回率@k、回答忠实度（模型评审）、拒答正确率、越权泄露。所有用例都检查"未确认的写操作没有执行"。
- 业务数据用 `eval/fixtures.py` 的固定数据。其中一个商品描述里藏了注入指令。
- 与生产一样，只读工具经（进程内的）MCP Server 调用。
- 知识库默认用内存版（`--kb memory`，不需要 ES）；`--kb es` 用真实索引。

## 主要配置（环境变量）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DASHSCOPE_API_KEY` / `DEEPSEEK_API_KEY` | 空 | 都为空时助手返回"暂不可用" |
| `QWEN_MODEL` / `DEEPSEEK_MODEL` | `qwen-plus` / `deepseek-chat` | |
| `LLM_TIMEOUT_SECONDS` / `LLM_MAX_RETRIES` | 30 / 1 | |
| `EMBEDDING_MODEL` / `RERANK_MODEL` | `text-embedding-v3` / `gte-rerank-v2` | `RERANK_MODEL` 为空时不重排，也不做阈值拒答 |
| `RAG_MIN_RERANK_SCORE` | 0.35 | 低于该分数的片段视为不相关 |
| `RATE_LIMIT_PER_MINUTE` / `DAILY_REQUEST_QUOTA` / `DAILY_TOKEN_QUOTA` | 10 / 100 / 200000 | |
| `MCP_URL` | `http://stickybeak-mcp/mcp` | agent 连接的 MCP Server，主机名是服务名时经 Nacos 解析 |
| `NACOS_ADDR`、`MYSQL_*`、`REDIS_*`、`ES_URIS` | 本机 | 与 Java 服务同名 |
| `SW_AGENT_COLLECTOR_BACKEND_SERVICES` | 空 | 设置后启用 SkyWalking |
