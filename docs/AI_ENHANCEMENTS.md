# AI 能力补齐：轻量实现与验收

目标是补齐可解释、可演示的工程实践，继续复用商城已有服务。没有加入自动记忆抽取、通用任务规划器、分布式 Agent 平台或全商城 K8s 迁移。

## 1. 跨会话偏好与上下文

个人中心新增「助手购物偏好」：一个商品类别与单件 AUD 预算。用户显式保存；助手不从对话中自动收集个人信息。

- `GET /api/agent/preferences` 查看；`PUT` 整体替换；`DELETE` 清除。身份只取网关登录信息，不能在 JSON 中指定用户。
- MySQL `stickybeak_agent.user_preferences` 按用户存储，默认 90 天有效。到期立即不再读取，服务启动时清理过期行。
- 每次回答重新读当前偏好，更新/删除不依赖新建对话；当前明确要求优先于默认偏好。偏好不会另存进对话 checkpoint。
- 清除的是保存的偏好，聊天原文仍在；界面明确提示这一点。历史中用户自己说过的内容仍可用于本次聊天。
- 开发模式 `CHECKPOINT_BACKEND=memory` 使用内存存储，重启会丢失；Compose/K8s 使用 MySQL。

上下文改为 `cl100k_base` Token 计量，包含系统提示、消息、工具参数与工具 schema，默认输入预算 7000，输出上限 1024。
这是跨供应商统一计量基准，**不是 Qwen 等模型原生 tokenizer 的精确窗口保证**；切换模型仍需预留余量。
旧对话按完整轮次移出输入，保留最多 500 Token 的用户/助手文字摘录摘要，写入 checkpoint 的 `history_summary`。
摘要不调用额外模型，也不把旧回答视为已执行写操作的证据。近期原文优先，工具调用与结果保持配对。
当一轮工具结果过大时明确标记截断；连当前问题和必要参数都放不下时返回 `context_budget`，要求开启新对话。
原始历史仍保留以便刷新恢复，压缩只控制发给模型的内容。

相关文件：`stickybeak-agent/stickybeak_agent/memory.py`、`context.py`、`graph/builder.py`。

## 2. 一个实用的双 Agent 协作

示例：“根据我的订单推荐没买过的鸟类冰箱贴，15 澳元以内。”

```text
路由 → 订单分析 Agent → 导购选择 Agent → 校验后的商品回答
          ↓                  ↓
     当前用户订单        商品检索、代码排除已购
     生成检索计划        模型仅选择候选 ID
```

这是同一 LangGraph 中两个有独立职责、独立模型调用的有界节点，不是分布式多 Agent 平台。
共享交接状态包含 `status / purchased_ids / checked_orders / plan / candidates / selected`。
订单分析最多检查最近 20 笔已付款（含后续发货、退款状态）订单；导购搜索最多 30 个商品，模型从最多 12 个候选选 3 个。
模型不能修改当前用户身份和已购 ID 集合；最终商品名称、价格、链接都从候选读取，模型捏造的 ID 会被丢弃。
订单或商品查询失败时不声称“这些没买过”；超出 20 笔会说明旧订单尚未核对。普通泛泛推荐仍走原购物助手。
这个流程只读，不加入购物车、不取消订单，原有写操作仍需用户确认。

相关文件：`stickybeak-agent/stickybeak_agent/graph/recommendation.py`。

## 3. 可选 vLLM 推理与性能实验

当前实验配置已升级为 `vllm/vllm-openai:v0.32.0` + `nvidia/Qwen3.8-27B-NVFP4`，固定模型修订，关闭思考，使用 `qwen3_coder` 工具解析。
仅加载文本模型，最大上下文 12288、最多 4 个序列，GPU 显存使用比例 0.82；服务只绑定本机 `127.0.0.1:8000`。
27B 已完成真实压测 32/32 请求；短上下文并发 1/4 吞吐约 8.7/29.3 Token/s，整卡峰值约 30 GB。
记忆/协作复测 3/4、泛推荐 8/8；仍有一条忽略已保存偏好。配置、兼容修复、数据与限制见 [vLLM 27B](VLLM_27B.md)。下方 4B 数据作为历史基线保留。

```powershell
docker compose -f docker/docker-compose.vllm.yml up -d
```

日常模型链固定为 `LLM_CHAIN=openai,ollama`：现有反代的 `gpt-6-luna` 优先，连接失败、超时、限流等不可用错误时回落 `qwen3.8`。
`OPENAI_BASE_URL` 与 key 使用现有反代配置。vLLM 仅在独立实验进程中临时使用以下环境变量，不修改商城的 `docker/.env`：

```dotenv
LLM_CHAIN=vllm
VLLM_BASE_URL=http://host.docker.internal:8000/v1
VLLM_MODEL=nvidia/Qwen3.8-27B-NVFP4
VLLM_API_KEY=local-vllm
```

从宿主 Python 3.12 环境运行（或在可访问 GPU 和服务的容器里运行）：

```powershell
cd stickybeak-agent
uv run python -m eval.benchmark --concurrency 1 4 --context-words 256 2048 --requests 8 --output-tokens 128
```

脚本先 warmup，再对并发×上下文矩阵采样，保存每个请求及汇总 JSON。每个请求使用唯一前缀，避免不同组复用同一提示的 prefix cache。
报告 TTFT p50/p95、总延迟 p95、服务端真实 `completion_tokens / 墙钟时间` 吞吐、GPU 峰值显存和错误数。
`context-words` 是输入目标词数，实际 `prompt_tokens` 取服务端 usage；没有 usage/GPU 采样就记 null，绝不推算成实测。
显存是整张 GPU 的使用量，包含其他进程。每组默认只有 8 次请求，是小规模演示，不是容量承诺。

### 4B 历史基线（vLLM 0.11）

RTX 5090（32607 MiB）、Docker Desktop / WSL2、PyTorch 2.8.0 + CUDA 12.8。每组 8 次请求、最多生成 128 Token；32/32 成功。
模型修订、镜像 digest、每个请求的 usage 和计时均保存在 [原始性能报告](validation/vllm-20261027.json)。

| 实际平均输入 Token | 并发 | TTFT p50 / p95（ms） | 总延迟 p95（ms） | 输出吞吐（Token/s） | 整卡峰值显存（MiB） |
|---:|---:|---:|---:|---:|---:|
| 379.9 | 1 | 52.48 / 63.67 | 2333.06 | 59.34 | 22204 |
| 379.8 | 4 | 135.72 / 185.93 | 2716.81 | 203.29 | 22169 |
| 2620.4 | 1 | 219.64 / 234.16 | 2543.76 | 52.32 | 22264 |
| 2620.5 | 4 | 421.24 / 806.72 | 4247.72 | 129.02 | 22232 |

短上下文并发 4 的总吞吐约为并发 1 的 3.43 倍，但单请求首字延迟更高。长上下文增加预填充计算，并发 4 时首字 p95 达到约 807ms，总吞吐提升约 2.47 倍。
服务启动日志显示模型权重约 7.56 GiB、预留 KV cache 9.76 GiB；四组整卡显存接近，不能据此说长上下文“不消耗额外资源”，因为缓存容量已提前分配。
本实验使用 eager 模式减少启动编译和图捕获复杂度，未比较 CUDA Graph 或不同显存比例，不能作为 RTX 5090 的吞吐上限。
首次试跑发现重复提示会命中前缀缓存，因此舍弃该组对比结论，表格使用加入唯一前缀后的重测数据。

集成质量单独看：[vLLM 助手回归](validation/vllm-assistant-eval-20261027.json) **2/4**，两条协作用例通过，偏好用例中出现忽略已存偏好与把 train 选成 tram。
这说明推理服务、结构化输出和协作链路可用，不能说明这个 4B 模型达到了现有云模型的任务质量。商城仍默认 `gpt-6-luna` + `qwen3.8` 备用；vLLM 作为可选实验。

## 4. Kubernetes 实践

配置位于 [deploy/k8s](../deploy/k8s/README.md)：2 副本、ClusterIP、启动/存活/就绪探针、CPU/内存资源限制、滚动更新与可选 HPA。
MySQL checkpoint/偏好和 Redis 限流共享；Java 服务及 MCP 继续用现有部署。
不公开可信 `X-User-Id` 的内部 Agent 端口，业务请求仍需经过认证网关。

本机演示使用独立 `stickybeak-ai-demo` k3d 集群，不切换系统默认 context。
已构建 `stickybeak-agent:ai-demo` 且本地 Compose 在运行时，可用脚本重现：

```powershell
./deploy/k8s/local-demo.ps1 -K3dPath '<k3d.exe 的实际路径>'
kubectl --kubeconfig deploy/k8s/kubeconfig.yaml -n stickybeak-ai get pods
```

脚本从已有 Agent 容器读取必要连接配置，把凭据传到 Secret，不打印或提交密钥。kubeconfig 与本地地址覆盖文件被 Git 忽略。
若 Compose 容器 IP 变化，重新运行脚本更新地址。
HPA 的 CPU 指标只反映编排服务负载，不能代表远端模型推理容量；没有宣称增加 Agent 副本能提升 GPU 吞吐。
长 SSE 在滚动重启时仍可能中断，同一 thread 的请求需串行提交。

## 验收记录（2026-10-27）

- 前端：35 项单测通过，TypeScript 检查与生产构建通过；提交前补齐浏览器测试替身的偏好读取接口后，25 项 Playwright（桌面/移动端、模拟后端）全部通过。保留已有大包提示与其他页面的 lint warning。
- 后端：最终完整运行 108 项 pytest 全部通过，ruff check / format 通过。
- 默认模型链已收敛为 `openai,ollama`（反代 Luna → 本地 qwen3.8），并同步本地与 K8s 运行配置。
  在独立请求里临时将主模型指向不可达端口，真实 Ollama 返回 `FALLBACK_OK`；生产使用的反代地址和 key 未改变。
- 真实模型：`gpt-6-luna` + 实际 ES 检索，首轮 62/64，修正提示后全量 63/64（导购 18/18，新记忆/协作 4/4）。
  唯一剩余失败 `kb-14` 的答案正确，但评审未拿到系统提示中给定的客服邮箱；补齐 `trusted_facts` 后定向复测 1/1。
  这不是单次全量 64/64；原报告与失败原因保留，未删除失败用例或放宽政策事实判定。
  全量报告 `20261027-024713-405236`，定向复测 `20261027-025229-825254`。
- K8s：k3s `v1.31.5+k3s1`，2 副本就绪；滚动重启、2→3→2 扩缩容实测通过。
- MySQL：副本 A 写入测试偏好，副本 B 读取；滚动重启后仍能读取，其他用户不可见，删除后返回空。测试记录已清除。
- HPA：metrics-server 可用，已读取 CPU 2% / 目标 70%，副本范围 2–4；未用压力负载验证自动扩容触发。
- vLLM 4B 历史实验：GPU 服务启动与真实压测完成，32/32 请求成功；助手集成 2/4 的限制如上，未替换商城主模型。27B 版本的独立结果见 [vLLM 27B](VLLM_27B.md)。

主模型回归报告在 `stickybeak-agent/eval/reports/`（本地忽略目录）。新增测试覆盖跨会话、修改/遗忘、过期、身份隔离、Token 截断配对、协作过滤及接口失败。

## 释放演示资源

```powershell
docker compose -f docker/docker-compose.vllm.yml stop
k3d cluster delete stickybeak-ai-demo
```

停止推理容器保留模型缓存；删除演示集群不影响 Compose 商城或其 MySQL 数据卷。
