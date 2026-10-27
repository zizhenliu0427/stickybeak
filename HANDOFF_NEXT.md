# HANDOFF_NEXT — 剩余工作交接（2026-10-27 更新）

给接手的 AI / 开发者：Sprint 10–14 已合入 `develop` 并推送，在本机完整 Docker 环境里验收过（结果写在 `docs/SPRINT_ISSUES.md` 各 Issue 的实现说明里）。后续已补齐轻量 AI 能力并修复支付回跳。剩下的事项包括真实运营政策、Stripe 测试账号及可选模型接入；桌面客户端录屏按用户要求暂缓。最新状态以上方日期对应的补充记录为准，后文保留较早阶段的验收背景。

项目背景、约定与踩过的坑先读 `HANDOFF.md`。整体介绍与设计说明见 `docs/PROJECT_OVERVIEW.md`，演示步骤见 `docs/DEMO_GUIDE.md`，AI 助手与 MCP 的细节见 `stickybeak-agent/README.md`。

### 2026-10-27 最新补充：轻量 AI 能力完善

- 已实现个人中心购物偏好（MySQL、跨会话、替换/清除/90 天过期）、Token 预算与旧对话摘录摘要、根据已购记录推荐新品的双 Agent 流程。
- 可选 vLLM 已升级到 `vllm/vllm-openai:v0.32.0` + `nvidia/Qwen3.8-27B-NVFP4`，固定权重修订；RTX 5090 实测 32/32 压测请求成功，短上下文并发 1/4 吞吐约 8.7/29.3 Token/s，整卡峰值约 30 GB。记忆/协作复测 3/4、泛推荐 8/8，仍有忽略已保存偏好的失败；旧 4B 报告保留。配置、限制与原始证据见 [vLLM 27B](docs/VLLM_27B.md)。实验完成后停止释放显存，权重保留，日常链路不变。
- 修复 Qwen3.8 只接受首条 system 的聊天模板兼容问题，将历史摘要合并进首条消息；真实压缩上下文请求通过。补清偏好缺省值和已购推荐路由规则，Luna 定向回归 4/4 + 8/8，108 项 pytest 与 ruff 通过。Agent 镜像已重建并部署到本地 Compose 与现有 K8s 演示集群。K8s 清单与复现脚本位于 `deploy/k8s/`。
- 后端 **108 项单测**、前端 **35 项单测**通过；前端类型/构建、ruff 通过。提交前补齐浏览器测试替身的偏好读取接口，**25 项 Playwright** 通过。云端全量评测 **63/64**，新增功能 **4/4**；唯一剩余项是评测器漏传系统给定的客服邮箱，补齐后定向复测 **1/1**，不记作单次全量 64/64。
- 本地商城 Agent/Frontend 已更新并运行，仍用原来的云端主模型与 Ollama 备用。独立 `stickybeak-ai-demo` 集群实测了 2 副本、滚动更新、2→3→2 扩缩容、跨副本偏好持久化与删除；默认 kube context 没有切换。
- 详细配置、模型性能数据、验收证据与资源清理命令以 [AI 增强验收](docs/AI_ENHANCEMENTS.md) 为准。用户暂不做 MCP 桌面/computer-use 演示。本轮提交包含此前的支付回跳修复及 AI 增强；本地凭据与启动配置不入库。
- 用户已确认日常模型策略：`LLM_CHAIN=openai,ollama`，现有反代 `gpt-6-luna` 为主，本地 Ollama `qwen3.8` 兜底；vLLM 仅用于独立实验。

### 2026-10-27 较早记录：泛推荐先问偏好

- 修复截图中的 `do you have some suggestion` 先搜索问题：没有偏好时先简短追问；已有条件直接查；明确要热门或说“随便推荐”时按销量查询，不编造关键词。分类和预算用专门参数，避免同时把“鸟类”塞进关键词而误过滤商品。调整了导购/路由提示词与 MCP 工具说明。
- 新增 8 条中英文导购回归，评测集从 52 扩为 60 条；报告记录工具参数并检查追问前未调用工具。最终 `gpt-6-luna` + 现有 ES 知识库全量 **58/60**：导购 **18/18**、订单 **10/10**、闲聊 **3/3**、越权 **6/6**、注入 **6/6**，未确认写操作 **0**。备用 `qwen3.8` 新增场景 **8/8**；ruff 与 **94 个 pytest** 通过。
- 余项：全量评测中 `kb-02`（商品破损）和 `kb-unknown-02`（开发票）被忠实度模型评审判为失败；两条意图正确，发票问题已正常拒答。需单独核对检索片段、回答与评审结果，不能记作全量通过。报告在本机 `stickybeak-agent/eval/reports/20261027-012730-914357/`，本地模型报告在 `20261027-012730-830952/`。
- Agent / MCP 镜像与容器已更新，保持停止状态；启动项目后加载新规则。为评测临时启动的 ES 已停止。本次改动尚未提交，原有结账回跳待办未处理。

---

## 0. 代码在哪

- `develop` 已推送到 `origin/develop`（2026-10-26，`7436a39`），包含：原 develop 上的提交（ES / MinIO / 视频 / 类目对齐 / SKU 合并 / 前台改版）、PR #1–#5（Sprint 10–14）、合并后在 Docker 环境发现并修复的问题、多模型接入与云端模型评测。
- PR #1–#5 的 base 都已改成 `develop`，GitHub 上显示为已合并；tag `v1.2.0`（#1）… `v2.1.0`（#5）已打在对应 PR 的提交上。
- `main` 还停在 `v1.1.0`（2026-09-16），比 `develop` 落后 58 个提交（另有 1 个只在 `main` 上的发布提交）。发布新版本时按 Git Flow 把 `develop` 合进 `main`。
- 合并时的取舍（ES 搜索保留 Sprint 11.2 的实现、webhook 线程池改为可选等）见合并提交说明和 `HANDOFF.md` 的 9.2 / 9.6 两节。
- 远程分支 `claude/kind-bardeen-rqmpgs` 和 `feature/sprint-10` … `feature/sprint-14` 都已合入，可以删除。

## 1. 工作约定（项目所有者的要求）

- **commit 和 PR 里不要署名 AI**：不加 `Co-Authored-By`、`Generated with ...` 之类的行；PR 描述里如果被工具自动加了尾注，要删掉。
- 分支 `feature/xxx`（Git Flow）；commit 用 `feat:` `fix:` `docs:` `test:` `build:` 前缀。
- 文档用中文。做完一项就在 `docs/SPRINT_ISSUES.md` 对应 Issue 的"实现说明"里把"未完成"改成实测结果，必要时同步 `HANDOFF.md`、`README.md` / `README_CN.md`。
- **只写实测数据**：评测分数、延迟、压测结果都要真跑出来，跑不了就写"未完成"，不要估算或编造。
- 本机是 Windows（见 `HANDOFF.md` 的"环境差异"和"踩过的坑"）：
  - 宿主端口：Redis 16379、ES 19200、Nacos gRPC 19848。
  - 火绒会拦截新的 `.ps1` 脚本，自动化脚本用 Node.js 或在 Docker 容器里跑。
  - 本机没有 uv、Python 是 3.13（agent 要求 3.12），agent 的检查在容器里跑（见下）。

## 2. 提交前必跑的检查

```bash
# 后端（全部单测 + JaCoCo 覆盖率门禁）
docker run --rm -v "${PWD}:/workspace" -v stickybeak-m2:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-17 mvn -B verify

# 前端
cd stickybeak-frontend && npm install && npm run typecheck && npm run lint && npm run test && npm run build && npm run e2e

# AI 助手 / MCP（Python 3.12，uv；本机用容器）
docker run --rm -v "${PWD}/stickybeak-agent:/app" -w /app -e UV_PROJECT_ENVIRONMENT=/tmp/venv ghcr.io/astral-sh/uv:python3.12-bookworm-slim \
  sh -c "uv sync --frozen && uv run ruff check . && uv run ruff format --check . && uv run pytest -q"

# 全链路（需要 docker compose -f docker/docker-compose.yml up -d --build 起整套服务）
node scripts/test-sprint5-e2e.js && node scripts/test-sprint6-e2e.js && node scripts/test-sprint8-e2e.js
node scripts/test-seata-rollback.cjs && node scripts/test-search-freshness.cjs
```

2026-10-26 全部通过：
- 后端：260 个测试，7 个覆盖率门禁。
- 前端：lint 0 错误（4 个有意保留的 `exhaustive-deps` 警告）、32 个 vitest、25 个 Playwright（桌面 + 移动）。
- agent：83 个 pytest，ruff 通过。
- 全链路脚本：Sprint 5（6 项）、6（5 项）、8（25 项）、Seata 回滚、索引新鲜度、MinIO 建品（图片 / 视频）、PWA 检查（40 项）。

GitHub Actions 被项目所有者关掉了（没付费）。重新打开时要确认 `agent` job 和前端新增的 lint 步骤能跑通。

---

## 3. 已完成（2026-10-25 ~ 26，本机 Docker）

原清单里不依赖外部条件的部分都做完了，细节和数据在 `docs/SPRINT_ISSUES.md`：

- **P0-1 Docker 全栈**：全部镜像构建成功；SkyWalking 9 个服务、结账链路、端点分组、死信积压告警；知识库启动入库；`/api/agent/health` → `mcpConnected: true`。顺带修了：OAP 健康检查用了不存在的 curl、全栈 compose 的端口与 Windows 保留段冲突、缺 `.dockerignore`、Python 服务与 Nacos 同时启动时注册失败即退出、知识库入库不重试、全栈 / 生产 compose 缺 MinIO 配置。
- **P1-1（除 Stripe 退款）**：Seata 回滚验收；热点压测（AT 开约 35 单/秒、关约 69 单/秒）；压测中发现 **Seata TC 2.0.0 锁泄漏导致库存卡在"已预占"**，TC 升级到 2.3.0 并加了两处补偿；ES 与 `LIKE` 耗时对比、索引新鲜度、ES 宕机回退。
- **P1-2 的命令行部分**：经网关的匿名 / 令牌 / 越权 403 / 撤销 401 全部验证。
- **P2**：前端 lint 补齐并接入 CI；"立即支付"改为为原订单重新取支付链接（旧会话先失效）；5xx 错误提示带 `X-Trace-Id`；个人中心文案走 i18n。
- **P0-2 的大部分**：多模型接入（`LLM_CHAIN` 降级链）、本地 Ollama 与云端 `gpt-6-luna` 的评测和首字延迟实测，见下面 P0-2 的进度。
- **文档**：新增 `docs/PROJECT_OVERVIEW.md`（网站是做什么的、微服务 / 前端 / AI 助手 / MCP 各自怎么设计，内容已对照代码核实）和 `docs/DEMO_GUIDE.md`（中英文对照的演示流程，含准备步骤、提问清单、常见问题速答、出问题时的处理）。

## 4. 待办（需要项目所有者）

### P0-2 用真实模型跑通 AI 助手并完成评测（13.2 / 13.4 / 13.5 / 13.7 / 14.3）

**进度（2026-10-26）**：已支持通义 / DeepSeek / OpenAI / Gemini / 本地 Ollama，用 `LLM_CHAIN` 排降级顺序。数据见 SPRINT_ISSUES 13.2 / 13.4 / 13.5 / 13.7。
- 本地 Ollama（`qwen3.8` + `bge-m3`）：知识库向量化入库、拒答阈值校准（0.79）、52 条评测 51/52、首字延迟 p50 937 ms / p95 3208 ms、经网关的导购 / 加购确认 / 售后问答手测。
- OpenAI（项目所有者提供的 ChatGPT 反代，`OPENAI_BASE_URL` 指过去，地址和 key 只在本机 `docker/.env`）：默认 `gpt-6-luna`，评测 52/52、首字延迟 p50 3951 ms / p95 8876 ms；`gpt-6-sol` 也测过（51/52、p50 5778 ms）。本机 `docker/.env` 现在是 `LLM_CHAIN=openai,ollama,...`，反代不可用时自动降级到本地。评测中发现的两处路由 / 订单提示词问题已修，本地模型复跑仍是 51/52。
- Gemini：提供的 `AQ.` 开头的 key 在 AI Studio 与 Vertex AI 各接口都返回 401，没用上，`docker/.env` 里已注释掉。要用的话换 AI Studio 的 key（`AIza` 开头）。

剩下的是通义 / DeepSeek（没有 key），以及下面第 6 步的浏览器手测。key 填进 `docker/.env`（已 gitignore，模板 `docker/.env.example`；**不要提交到仓库**），重建 agent 与 mcp，按下面的步骤跑一遍，与上面两份基线对比。通义的 text-embedding-v3 + gte-rerank 组合也在这一步实测（有重排时阈值用 `RAG_MIN_RERANK_SCORE`）。

注意：agent 镜像里没有 `eval/` 目录，在容器里跑评测前先 `docker cp stickybeak-agent/eval sb-agent:/app/eval`（Git Bash 下加 `MSYS_NO_PATHCONV=1`），再 `docker exec -e ES_URIS=http://elasticsearch:9200 sb-agent python -m eval.run_eval --kb es`。临时只测某个模型可以加 `-e LLM_CHAIN=ollama`。

1. 先确认通义接口的几处假设（之前没能联网验证）：
   - `qwen-plus` 通过 OpenAI 兼容接口做 function calling（意图路由用 `with_structured_output(method="function_calling")`）。
   - `stream_usage=True` 时流式响应能返回 token 用量（`stickybeak_agent/usage.py`）。
   - `text-embedding-v3` 的 `dimensions=1024`、每批 10 条（`rag/embeddings.py`）。
   - `gte-rerank-v2` 的请求 / 响应格式（`rag/rerank.py`）。格式不对就按官方文档改，并补单测。
2. 知识库入库：`uv run python -m stickybeak_agent.rag.ingest --recreate`（容器里已有 ES + IK），确认 kNN 和 BM25 都能查到。现在没配 key 时只写了 BM25 文本。
3. 校准拒答阈值：`RAG_MIN_RERANK_SCORE=0.35` 是估的。用能回答的问题和 `kb-unknown-*` 分别看重排分数分布，定一个能分开两者的值，写进 13.4。
4. 跑评测：`uv run python -m eval.run_eval --kb es`，汇总表抄进 13.7；失败用例逐条看，改提示词或检索参数后重跑对比。14.3 的"结果不下降"以这一份为基线。
5. 首字延迟（13.5）：经网关 + nginx 实测 10 次以上，记录 `firstTokenMs` 的 p50 / p95，并确认回答逐字出现。
6. 前端手测（中英文各一遍）：导购推荐、加购确认 / 拒绝、查订单、取消待支付订单、售后问答（带引用）、知识库没覆盖的问题、刷新后恢复对话和待确认卡片、断开模型 key 后商城其他功能正常。

### P0-3 知识库语料换成真实政策

`stickybeak-agent/knowledge/{zh,en}/*.md` 里的退货期限、运费、时效、定制起订量等是**示例值**（见 `knowledge/README.md`）；与系统行为相关的内容已与代码一致。需要项目所有者确认真实政策，中英文同步替换后重新入库，并检查依赖具体数字的评测用例（如 `kb-04`、`kb-05`、`kb-13`）。

### P1-1 余项：Stripe 测试模式真实退款

管理员退款 → Stripe Refund API → `charge.refunded` webhook → 订单变为 REFUNDED 并回补库存。需要 Stripe 测试 key 和 Stripe CLI 转发 webhook，见 `docs/STRIPE_LOCAL_GUIDE.md`。结果写进 11.3。

### P1-3 支付回跳（已修复）

Mock 与 Stripe 共用 `StripeConfig` 生成回跳地址。`STOREFRONT_URL` 在本地开发默认 `http://localhost:5173`，Docker 全栈默认 `http://localhost`，生产 Compose 要求显式提供公开商城地址；`STRIPE_SUCCESS_URL` / `STRIPE_CANCEL_URL` 仍可单独覆盖。

新增 `PaymentRedirectTest` 覆盖默认值、尾斜杠和单独覆盖。2026-10-27 Java 测试报告共 266 项通过（0 失败、0 错误）。Mock 回跳本身不会推进支付状态；真实 Stripe 退款仍需测试账号验证。

### P1-2 余项：MCP 外部客户端演示 GIF（14.4）

1. 登录网站 → 个人中心 →"AI 客户端接入（MCP）"→ 生成令牌，复制配置。
2. 在 Cherry Studio 和 Cursor 里接入（配置见 `stickybeak-agent/README.md`），确认 Streamable HTTP 经网关和 nginx 能用、`Authorization: Bearer` 能带上；写法不对就改 README。
3. 让客户端搜索商品、查询自己的订单、问一个售后问题，录制 GIF 放到 `docs/images/agent/`，插进 `README.md` / `README_CN.md`。
4. 撤销令牌后客户端应收到 401。

### 可选

- `scripts/demo-webhook-threadpool.cjs`（故事线 9.6 的演示）还是按旧规则写的：现在 mock 回调需要 `PAYMENT_MOCK_WEBHOOK_ENABLED=true`、买家登录、金额等于订单实付，线程池需要 `WEBHOOK_ASYNC_ENABLED=true`，`pool-stats` 需要管理员。要重录演示数据时先按这些规则改脚本。
- 搜索按"销量"排序最多滞后一天（只在每日对账进入 ES），暂不改成实时，原因见 `docs/CODE_REVIEW_TODO.md` 末尾。
- 旧文档过时，可以删掉或改写，README 的链接改指新文档：
  - `docs/DEMO_SCRIPT.md`（v1.3.0）：前端写成了 Vue 3 + Pinia，商品写的是袋鼠皮手袋、坚果等，与现在不符。已由 `docs/DEMO_GUIDE.md` 取代，但 `README.md` / `README_CN.md` 顶部仍链接旧文件。
  - `docs/ARCHITECTURE.md`：版本号是旧的（Spring Cloud 2022.x、Seata 2.0），写了不存在的 `stickybeak-admin` 服务，服务间调用写成 OpenFeign（实际是 RestTemplate / RestClient + Nacos 负载均衡），状态机写成配置表（实际在代码里）。现状以 `docs/PROJECT_OVERVIEW.md` 为准。
  - README 的端口表：ES 写的是 9200，本机全栈实际映射到 19200；"AI 购物助手"一行还写着需要 `DASHSCOPE_API_KEY` 或 `DEEPSEEK_API_KEY`，现在任一模型（包括本地 Ollama）都可以。

---

## 5. 本地跑 AI 助手的最短路径（不用全栈 compose 时）

```bash
docker compose -f docker/docker-compose-infra.yml up -d   # MySQL、Redis、ES、Nacos、Seata、SkyWalking ...
# Java 服务：powershell -File scripts/rebuild-services.ps1 或 IDE

cd stickybeak-agent
uv sync
export DASHSCOPE_API_KEY=...                      # 不要写进仓库
export ES_URIS=http://localhost:19200 REDIS_PORT=16379
uv run python -m stickybeak_agent.mcp_server      # :8089，先起
uv run python -m stickybeak_agent                 # :8087
```

命令行验证 MCP（全栈 compose 下也可以在容器里跑）：

```bash
docker exec sb-agent python -m stickybeak_agent.mcp_demo --url http://gateway:8080/api/mcp [--token <MCP 令牌>]
```
