# vLLM：Qwen3.8-27B 实验

商城日常链路仍是反代 Luna → Ollama `qwen3.8`（27.3B / Q4_K_M）。本页是独立 vLLM 实验，不修改主模型链。

## 版本与模型

- GPU：RTX 5090，32607 MiB；宿主驱动 621.01，CUDA 驱动支持 13.5。
- 镜像：`vllm/vllm-openai:v0.32.0`，替换不支持 Qwen3.5 架构的旧 0.11 镜像。
- 实际镜像 digest：`sha256:befccf0e7e4089aa23926bb6f887a85c0d46c2a99e6de5c5ffdf5cb7957c5b34`；PyTorch `2.13.0+cu130`，运行时 CUDA `13.0`。
- 权重：[nvidia/Qwen3.8-27B-NVFP4](https://huggingface.co/nvidia/Qwen3.8-27B-NVFP4)，来源是官方 `Qwen/Qwen3.8-27B`，并非换成 Qwen3.5 模型。
- 固定修订：`482ca0f3832238542f8f5295dde86b5f22711d80`；safetensors 权重总计约 21.92 GB。
- 架构名为 `Qwen3_5ForConditionalGeneration`，因为 Qwen3.8 沿用了该架构。量化为 NVIDIA ModelOpt 的 NVFP4/FP8 混合精度，和 Ollama 的 GGUF Q4_K_M 是不同量化版本。

依据：[vLLM 0.32 发布说明](https://github.com/vllm-project/vllm/releases/tag/v0.32.0)、[模型卡与推荐参数](https://huggingface.co/nvidia/Qwen3.8-27B-NVFP4/blob/main/README.md)。

## 运行

```powershell
docker compose -f docker/docker-compose.vllm.yml up -d
docker compose -f docker/docker-compose.vllm.yml logs -f vllm
```

首次需要下载镜像和权重；缓存复用 Docker 卷 `stickybeak-inference_models`，旧 4B 权重保留。
健康接口：`http://localhost:8000/health`；OpenAI 兼容端点：`http://localhost:8000/v1`。
实验默认 key 为 `local-vllm`，服务仅绑定本机回环地址。

当前配置选择文本模式、12288 上下文、最多 4 个序列、1024 Token 的预填充分块、0.82 显存比例、eager 模式和 FP8 KV cache。
这些参数以单张 32 GB 显卡上的文本工具调用为目标，没有启用视觉输入或 MTP。
工具解析使用 `qwen3_coder`，思考解析使用 `qwen3`；助手请求仍通过 `enable_thinking=false` 关闭思考。

独立评测进程需要：

```dotenv
LLM_CHAIN=vllm
VLLM_BASE_URL=http://host.docker.internal:8000/v1
VLLM_MODEL=nvidia/Qwen3.8-27B-NVFP4
VLLM_API_KEY=local-vllm
```

压测脚本默认模型已经同步为 27B，仍可通过 `--model` 选择其他已部署模型。并发×上下文测试使用每请求唯一前缀，不把复用前缀缓存的收益记为并发收益。

```powershell
cd stickybeak-agent
uv run python -m eval.benchmark --concurrency 1 4 --context-words 256 2048 --requests 8 --output-tokens 128
```

没有宿主 Python 3.12/uv 时，从仓库根目录使用已下载的 vLLM 镜像运行压测（也能采样 GPU 显存）：

```powershell
$agentPath = (Resolve-Path stickybeak-agent).Path
docker run --rm --gpus all --network stickybeak-inference_default `
  --mount "type=bind,source=$agentPath,target=/workspace" -w /workspace `
  --entrypoint python3 vllm/vllm-openai:v0.32.0 -m eval.benchmark `
  --base-url http://vllm:8000/v1 --concurrency 1 4 --context-words 256 2048 `
  --requests 8 --output-tokens 128 --output eval/reports/vllm-27b.json
```

Ollama 27B 与 vLLM 27B 的权重不能同时完整驻留在这张 32 GB 显卡上。vLLM 实验完成后停止容器，让日常 Ollama 兜底能重新加载：

```powershell
docker compose -f docker/docker-compose.vllm.yml stop
```

## 验证记录

- 模型成功加载，`/health` 返回 200，`/v1/models` 返回 `nvidia/Qwen3.8-27B-NVFP4`。
- 修复 Qwen3.8 聊天模板只允许开头一条 system 的兼容问题：历史摘要合并进首条 system，保留“不可信历史摘录”标记。真实压缩上下文请求返回 `READY_27B`，包含摘要且只有一条 system。
- 后端完整 **108 项 pytest 通过**，ruff check / format 与 Compose 配置检查通过。
- 当前使用 eager 模式；启动日志记录权重与非 Torch 占用约 19.49 GiB、KV cache 约 6.44 GiB。整卡显存包含 Windows 显示与其他进程，不能当作模型权重大小。
- FP8 KV cache 未提供模型自带的 KV 量化尺度，启动有精度提醒；不能声称和 BF16 KV 或 Ollama Q4_K_M 的输出完全等价。

### 性能（2026-10-27）

每组 8 次请求、最多生成 128 Token，**32/32 成功**。原始每请求计时、usage 和环境信息见 [27B 性能报告](validation/vllm-27b-20261027.json)。

| 实际平均输入 Token | 并发 | TTFT p50 / p95（ms） | 总延迟 p95（ms） | 输出吞吐（Token/s） | 整卡峰值显存（MiB） |
|---:|---:|---:|---:|---:|---:|
| 380.4 | 1 | 630.21 / 735.14 | 16523.97 | 8.68 | 30675 |
| 380.4 | 4 | 1264.45 / 5392.12 | 19988.77 | 29.32 | 30672 |
| 2620.5 | 1 | 878.93 / 952.16 | 15498.76 | 8.70 | 30700 |
| 2620.0 | 4 | 1367.80 / 2822.36 | 19203.69 | 28.29 | 30672 |

这套保守配置下，短/长上下文的并发 4 总吞吐分别约为并发 1 的 3.38/3.25 倍，但首字延迟更高。
性能测试期间未并行运行其他 GPU 推理用例。基准只做一次短请求 warmup，没有为每种并发和输入形状分别预热；首次遇到的内核形状初始化可能影响尾延迟。
每组只有 8 次请求，p95 实际取该组最大值；结果是小规模演示，不代表长期稳定容量或 RTX 5090 的速度上限。
eager 模式未启用 CUDA Graph，尚未定位 CPU 调度、量化内核等各项开销；不能仅凭本次数据断言具体性能瓶颈。
旧 4B 使用不同模型、精度和 vLLM 版本，见 [历史基线](AI_ENHANCEMENTS.md#4b-历史基线vllm-011)，不能把差异归因于引擎版本本身。

### 助手质量

- [首轮 2/4](validation/vllm-27b-assistant-eval-initial-20261027.json)：已购排除 2 条通过；泛推荐忽略已保存偏好，改偏好被误路由到按购买记录推荐。
- 补清通用规则：已有偏好直接用作缺省筛选条件；排除商品类别不等于排除已购商品。[复测 3/4](validation/vllm-27b-assistant-eval-20261027.json)，意图 4/4、已购排除 2/2；仍有一条泛推荐重复询问已保存偏好，保留失败记录，不视为完整验收通过。
- 同一规则下，Luna 的 [记忆/协作 4/4](validation/luna-27b-adaptation-regression-20261027.json) 与 [泛推荐 8/8](validation/luna-27b-suggestions-regression-20261027.json) 均通过。
- 27B [泛推荐 8/8](validation/vllm-27b-suggestions-eval-20261027.json)：无偏好先追问、热门商品筛选、补充条件后的工具调用与多轮衔接均通过。
- 27B [加购确认 1/1](validation/vllm-27b-confirm-eval-20261027.json)：搜索商品后生成确认卡片，未确认写操作为 0。
  首次把两轮评测放在同一进程、分别调用 `asyncio.run`，导致共享 HTTP 客户端使用已关闭的事件循环；该次 [测试命令错误记录](validation/vllm-27b-confirm-harness-error-20261027.json) 保留。改为独立进程执行原用例后通过，没有修改判定规则。

这些集成用例使用固定商品/订单数据和实际模型，不是对生产订单的写操作，也不是全量 64 条售后与权限回归。vLLM 保持独立实验；商城仍使用 Luna → Ollama。

适配后的 Agent 已构建并同步本机 Compose 与现有 K8s 演示集群（2 副本）。商城 `/api/agent/health` 返回 UP，模型列表仍为 `gpt-6-luna`、`qwen3.8`。
实验结束后已停止 vLLM 容器释放显存，镜像与权重缓存保留；按上方 Compose 命令可再次启动。
