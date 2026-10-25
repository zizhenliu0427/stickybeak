# HANDOFF_NEXT — 剩余工作交接（2026-10-25）

给接手的 AI / 开发者：Sprint 10–14 的代码都已写完并推送，但有一批验收项**只能在有 Docker、模型 key 或桌面客户端的本机环境完成**，之前的云端环境做不了。本文列出这些事项、怎么做、做完要更新哪里。

项目背景、约定与踩过的坑先读 `HANDOFF.md`。各 Sprint 的实现说明与验收标准见 `docs/SPRINT_ISSUES.md`，AI 助手与 MCP 的细节见 `stickybeak-agent/README.md`。

---

## 0. 代码在哪

Sprint 10–14 是**叠在一起的分支**，每个 PR 基于前一个。最新的 `feature/sprint-14-mcp` 包含全部改动，本地直接检出它工作即可：

| PR | 分支 | base | 合并后打 tag |
|---|---|---|---|
| [#1](https://github.com/zizhenliu0427/stickybeak/pull/1) | `feature/sprint-10-security-fixes` | `develop` | `v1.2.0` |
| [#2](https://github.com/zizhenliu0427/stickybeak/pull/2) | `feature/sprint-11-readme-capabilities` | #1 | `v1.3.0` |
| [#3](https://github.com/zizhenliu0427/stickybeak/pull/3) | `feature/sprint-12-skywalking` | #2 | `v1.4.0` |
| [#4](https://github.com/zizhenliu0427/stickybeak/pull/4) | `feature/sprint-13-ai-agent` | #3 | `v2.0.0` |
| [#5](https://github.com/zizhenliu0427/stickybeak/pull/5) | `feature/sprint-14-mcp` | #4 | `v2.1.0` |

```bash
git fetch origin && git checkout feature/sprint-14-mcp
```

- 合并顺序 #1 → #5（由项目所有者操作）。前一个合并、删除分支后，GitHub 会把下一个 PR 的 base 自动改到 `develop`。
- 旧的远程分支 `claude/kind-bardeen-rqmpgs` 可以删除。
- 新的修复要么直接提交到对应的 sprint 分支（PR 还没合并时），要么在合并后从 `develop` 开新分支 `feature/xxx`。

## 1. 工作约定（项目所有者的要求）

- **commit 和 PR 里不要署名 AI**：不加 `Co-Authored-By`、`Generated with ...` 之类的行；PR 描述里如果被工具自动加了尾注，要删掉。
- 分支 `feature/sprint-N-xxx`（Git Flow）；commit 用 `feat:` `fix:` `docs:` `test:` `build:` 前缀。
- 文档用中文。做完一项就在 `docs/SPRINT_ISSUES.md` 对应 Issue 的"实现说明"里把"未完成"改成实测结果，必要时同步 `HANDOFF.md`、`README.md` / `README_CN.md`。
- **只写实测数据**：评测分数、延迟、压测结果都要真跑出来，跑不了就写"未完成"，不要估算或编造。
- 本机是 Windows（见 `HANDOFF.md` 的"环境差异"和"踩过的坑"）：
  - 宿主端口：Redis 16379、ES 19200、Nacos gRPC 19848。
  - 火绒会拦截新的 `.ps1` 脚本，自动化脚本用 Node.js 或在 Docker 容器里跑。

## 2. 提交前必跑的检查

```bash
# 后端（全部单测 + JaCoCo 覆盖率门禁）
docker run --rm -v "${PWD}:/workspace" -v stickybeak-m2:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-17 mvn -B verify

# 前端
cd stickybeak-frontend && npm install && npm run typecheck && npm run test && npm run build && npm run e2e

# AI 助手 / MCP（Python 3.12，uv）
cd stickybeak-agent && uv sync && uv run ruff check . && uv run ruff format --check . && uv run pytest -q
```

截至交接时全部通过：
- 后端：246 个测试，7 个覆盖率门禁。
- 前端：28 个 vitest、23 个 Playwright。
- agent：64 个 pytest。

GitHub Actions 被项目所有者关掉了（没付费）。重新打开时要确认新增的 `agent` job 能跑通。

---

## 3. 待办（按优先级）

### P0-1 用 Docker 跑起整套服务（Sprint 12–14 的镜像都没实际构建过）

之前的环境没有 Docker daemon，compose 文件只通过了 `docker compose config` 校验。需要实际执行：

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

逐项确认：
1. **`docker/Dockerfile.backend`**：
   - 能从 `apache/skywalking-java-agent:9.7.0-java17` 复制 agent。
   - 4 个 optional 插件的 jar 文件名能匹配 `optional-plugins/${p}-*.jar`。
   - 没设置 `SW_AGENT_COLLECTOR_BACKEND_SERVICES` 时服务照常启动（不挂 agent）。
2. **`docker/elasticsearch/Dockerfile`**：IK 插件能装上。下载地址 `get.infini.cloud` 在国内或受限网络可能不通，文件里有离线 zip 的替代写法。
3. **`stickybeak-agent/Dockerfile`**：
   - 镜像 `ghcr.io/astral-sh/uv:0.11.32` 这个 tag 是否存在；不存在就换成一个存在的版本。
   - `apache-skywalking` 是从源码包构建的（`pyproject.toml` 里的 `no-binary-package`，原因见 `HANDOFF.md` 第 55 条），确认在镜像里能构建成功。
4. **SkyWalking**（BanyanDB + OAP 10.4 + UI :8088）：
   - 7 个 Java 服务和 `stickybeak-agent`、`stickybeak-mcp` 都出现在 UI 里。
   - 下一单能看到 gateway → order → product → payment 的链路；支付回调后能看到 MQ → order + notification。
   - 网关端点按 `/api/products/{var}` 分组。
   - 死信队列积压告警能触发。
   - 之前这些都是用同版本二进制直接跑验证的，不是 Docker。
5. `stickybeak-mcp` 启动时会把知识库增量写入 ES（索引 `stickybeak_kb`），确认日志里有 `knowledge base ingest: {...}`。
6. `http://localhost:8080/api/agent/health` 返回 `mcpConnected: true`。

发现的问题直接修，修完在 `docs/SPRINT_ISSUES.md` 的 12.1、12.2、13.1 实现说明里补充"Docker 环境实测"。

### P0-2 用真实模型跑通 AI 助手并完成评测（13.2 / 13.4 / 13.5 / 13.7 / 14.3）

需要 `DASHSCOPE_API_KEY`（通义千问，主）。可选 `DEEPSEEK_API_KEY`（备用）。放在 `docker/.env` 或环境变量里，**不要提交到仓库**。

1. **先确认通义接口的几处假设**（之前没能联网验证）：
   - `qwen-plus` 通过 OpenAI 兼容接口做 function calling（意图路由用 `with_structured_output(method="function_calling")`）。
   - `stream_usage=True` 时流式响应能返回 token 用量（`stickybeak_agent/usage.py` 依赖它）。
   - `text-embedding-v3` 通过兼容接口的 `dimensions=1024`、每批 10 条（`rag/embeddings.py`）。
   - `gte-rerank-v2` 的请求和响应格式（`rag/rerank.py`：`input.query/documents` → `output.results[].index/relevance_score`）。格式不对就按官方文档改，并补单测。
2. **知识库入库**：在真实 ES（带 IK）上执行 `uv run python -m stickybeak_agent.rag.ingest --recreate`，确认 kNN 和 BM25 都能查到。
3. **校准拒答阈值**：`RAG_MIN_RERANK_SCORE=0.35` 是估的。
   - 用评测集里能回答的问题和知识库没覆盖的问题（`kb-unknown-*`）分别看重排分数的分布，定一个能把两者分开的值。
   - 把数据和最终取值写进 13.4 的实现说明。
4. **跑评测**：`uv run python -m eval.run_eval --kb es`（没有 ES 时用 `--kb memory`）。
   - 报告在 `eval/reports/<时间>/report.md`，该目录已在 `.gitignore`。
   - 把汇总表抄进 `docs/SPRINT_ISSUES.md` 的 13.7。
   - 失败的用例逐条看：是提示词问题就改 `graph/prompts.py`，是检索问题就调检索参数。每改一次重跑一遍，前后对比。
   - 14.3 的验收是"评测结果不下降"。Sprint 13 和 14 的工具定义完全一致，所以现在这一份就是基线；以后再改动时和它对比。
5. **首字延迟**（13.5 验收）：SSE 的 `done` 事件和 agent 日志里都有 `firstTokenMs`。
   - 经网关加 nginx 实测 10 次以上，记录 p50 / p95，写进 13.5。
   - 同时确认 nginx 对 `/api/agent/` 没有缓冲，回答是逐字出现的。
6. **前端手测**（中英文各一遍）：
   - 导购推荐、加购确认、拒绝确认。
   - 查订单、取消待支付订单。
   - 售后问答（带引用）、知识库没覆盖的问题。
   - 刷新页面后恢复对话和待确认的卡片。
   - 断开模型 key 后，商城其他功能正常。

### P0-3 知识库语料换成真实政策

`stickybeak-agent/knowledge/{zh,en}/*.md` 里的退货期限、运费、时效、定制起订量等运营政策是**示例值**（见 `knowledge/README.md`）。和系统行为相关的内容（30 分钟关单、只能取消待支付订单、支付方式、币种、退款状态）已经与代码一致。

要做的事：
- 让项目所有者确认真实政策，替换示例值，中英文两份同步。
- 重新执行入库（增量）。
- 评测集里依赖具体数字的用例（如 `kb-04`、`kb-05`、`kb-13`）要一起检查。

### P1-1 Sprint 11 遗留的验收

- **Seata AT 回滚**：在 Docker 环境（seata-server 2.0.0）里故意让订单写库失败，确认商品库的 MySQL 库存自动回滚，Redis 预扣也被归还（`TransactionHook#afterRollback`）。
- **热点压测**：同一个商品并发结账，记录 AT 全局锁对吞吐的影响。可以参考 `scripts/test-sprint5-e2e.js` 的并发写法，结果写进 11.1。
- **ES 搜索**：
  - 同一组查询下 MySQL `LIKE` 与 ES 的耗时对比，写进 11.2（验收要求）。
  - 后台改价或上下架后，几秒内能在搜索里看到变化。
- **Stripe 测试模式真实退款**：管理员退款 → Stripe Refund API → `charge.refunded` webhook → 订单变为 REFUNDED 并回补库存。见 `docs/STRIPE_LOCAL_GUIDE.md`，需要 Stripe 测试 key 和 Stripe CLI 转发 webhook。

### P1-2 MCP 外部客户端演示（14.4）

1. 登录网站 → 个人中心 →"AI 客户端接入（MCP）"→ 生成令牌，复制配置。
2. 在 Cherry Studio 和 Cursor 里接入，配置写法见 `stickybeak-agent/README.md`。
   - 实测客户端发起的 Streamable HTTP 请求经过网关和 nginx 能正常工作。
   - 实测请求头 `Authorization: Bearer` 能被带上。
   - 某个客户端的写法不对就改 README。
3. 让客户端搜索商品、查询自己的订单、问一个售后问题，**录制 GIF**，放到 `docs/images/agent/` 并插进 `README.md` / `README_CN.md` 的 AI 助手章节。
4. 在网站上撤销令牌后，客户端应该收到 401。

### P2 已知小问题（不阻塞，有空再做）

- 前端 `npm run lint` 是死脚本：没装 ESLint，也没有配置文件。可以补 ESLint 配置并加进 CI，或者删掉这个脚本。
- "我的订单"里待支付订单的"立即支付"会跳到结账页重新下单，而不是复用原订单的支付会话；旧订单要等 30 分钟超时才释放库存。
- 前端 5xx 错误提示里可以带上响应头 `X-Trace-Id`，方便用户报障时定位链路。这是 Sprint 12 提过的想法，还没做。
- 个人中心页面（`ProfilePage.tsx`）的大部分文案还是硬编码英文，没有走 `lib/i18n.ts`（新加的 MCP 卡片已经做了双语）。
- 搜索结果按"销量"排序最多滞后一天：下单引起的销量变化只在每日全量对账时进入 ES。如果需要更实时，可以在订单支付事件里增量更新。

---

## 4. 本地跑 AI 助手的最短路径（不用 Docker 时）

```bash
# 先起基础设施（含 ES、Redis、MySQL、Nacos、SkyWalking）
docker compose -f docker/docker-compose-infra.yml up -d
# Java 服务照旧（rebuild-services.ps1 或 IDE）

cd stickybeak-agent
uv sync
export DASHSCOPE_API_KEY=...                      # 不要写进仓库
export ES_URIS=http://localhost:19200 REDIS_PORT=16379
uv run python -m stickybeak_agent.mcp_server      # :8089，先起
uv run python -m stickybeak_agent                 # :8087
```

- 网关要能找到这两个服务：
  - 用 Nacos 时，设置 `NACOS_ADDR`，两个服务会自动注册。
  - 不用 Nacos 时，网关用 simple discovery 指过来，例如 `--spring.cloud.discovery.client.simple.instances.stickybeak-agent[0].uri=http://localhost:8087`，`stickybeak-mcp` 同理指向 8089。
- 命令行验证 MCP：

  ```bash
  uv run python -m stickybeak_agent.mcp_demo --url http://localhost:8080/api/mcp [--token <MCP 令牌>]
  ```

## 5. 交接时的状态

- 所有改动都已推送到上面 5 个分支，工作区干净。
- 之前云端环境里用于本地验证的辅助脚本（直接运行 OAP / BanyanDB 二进制、查询 OAP GraphQL 的小工具）没有提交，接手后用 Docker 和 SkyWalking UI 代替即可。
