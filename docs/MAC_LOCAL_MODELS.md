# Mac 本地模型测试（2026-10-28）

## 环境与连接

- Apple M4 Pro，48 GiB 统一内存，macOS 27.0.1 / arm64。
- Ollama 0.35.1，实际使用 MLX / Metal GPU 后端。
- 本地模型为 `qwen3.8:27b-mlx`，`ollama show` 报告 27.8B、NVFP4；文件格式为 safetensors，模型文件约 18.17 GB。
- 原生 API：`http://127.0.0.1:11434/api`；助手使用的兼容 API：`http://127.0.0.1:11434/v1`。
- 测试开始时服务未运行。测试进程使用 `OLLAMA_HOST=127.0.0.1:11434 OLLAMA_NO_CLOUD=true ollama serve` 启动，模型请求均在本机执行。

模型标签必须写完整；本机没有助手默认配置中的 `qwen3`。

## 基础测试

原始响应及服务端计时见 [基础测试 JSON](validation/ollama-mac-smoke-20261028.json)。

| 测试 | 结果 | 说明 |
|---|---|---|
| 中文指令 | 通过 | 准确返回“本地模型运行正常” |
| 金额计算 + JSON Schema | 通过 | 19.90 × 3 + 10.50 × 2 = 80.70 AUD，共 5 件 |
| 兼容 API 工具调用 | 通过 | 正确调用 `search_products`，价格上限为 30 |
| 流式输出 | 3/3 通过 | 收到文字及最终完成消息 |

首次中文请求总耗时 11.36 秒，其中模型加载 8.52 秒；这不是热启动速度。
三次热启动的首字延迟为 378 / 424 / 373 ms，生成速度为 24.81 / 27.29 / 25.75 token/s。
生成速度按 Ollama 的 `eval_count / eval_duration` 计算，首字延迟由客户端计时。
流式样本每条固定生成 128 token，均因长度上限结束，只验证流式传输和速度，不代表完整保养建议已生成。
提示很短，后两次也复用了少量前缀缓存；这组数据不能代替复杂助手、长上下文或并发测试。测试期间还在安装项目依赖。

这组测试的上下文配置为 32768，关闭思考，温度为 0。
测试后 `/api/ps` 报告模型的 `size` 与 `size_vram` 均为 20,884,408,060 字节（约 19.45 GiB）；这是服务报告的模型驻留量，不是整机内存峰值。
完整业务评测末段，服务报告的驻留量升至约 30.07 GiB，仍使用 32768 上下文；内存需求应结合实际运行状态判断。
这组短提示测试与仓库中 RTX 5090 的旧 vLLM 基准使用不同权重格式、后端与输入，不能直接作为两种硬件的速度对比。

## 助手业务评测

复用仓库 `eval/run_eval.py` 的 `run_case`、判定规则和全部 64 条数据。
每条结果落盘；未修改提示词或放宽判定。

**64/64 通过，运行错误 0，未确认写操作 0。** 总耗时 691.28 秒（约 11 分 31 秒）。
见 [评测汇总](validation/ollama-mac-eval-20261028.md) 和 [逐条结果、耗时与运行信息](validation/ollama-mac-eval-20261028.json)。

| 场景 | 通过 |
|---|---|
| 导购（含中英文及多轮追问） | 18/18 |
| 订单 | 10/10 |
| 售后 | 17/17 |
| 闲聊 | 3/3 |
| 越权 | 6/6 |
| 提示词注入 | 6/6 |
| 偏好记忆 | 2/2 |
| 订单分析与导购协作 | 2/2 |

意图 60/60、工具选择 32/32、工具参数 6/6、确认 41/41、检索召回 16/16、忠实度 18/18、未知问题拒答 2/2，均通过各自适用的检查。
每条用例的总耗时中位数为 9.85 秒，p95 为 23.26 秒；其中包含多轮对话、工具调用及适用时的模型评审，不是单次 API 首字延迟。

本轮配置：本地 Ollama 单模型，`reasoning_effort=none`，温度 0.2，每次模型请求超时 120 秒，无重试，用例并发 1。
业务数据使用 fixtures，只读工具经过进程内 MCP，写操作停在确认节点，不操作真实购物车或订单。
知识库使用内存关键词检索，关闭向量嵌入和重排；本机只有聊天模型，因此本轮不覆盖 ES、bge-m3 或生产混合检索的质量。

从仓库根目录进入 `stickybeak-agent`，在已启动 Ollama 的另一个终端运行：

```bash
uv sync --frozen --python 3.12
LLM_CHAIN=ollama \
OLLAMA_BASE_URL=http://127.0.0.1:11434/v1 \
OLLAMA_MODEL=qwen3.8:27b-mlx \
OLLAMA_REASONING_EFFORT=none \
LLM_TIMEOUT_SECONDS=120 \
LLM_MAX_RETRIES=0 \
EMBEDDING_PROVIDER=none \
RERANK_PROVIDER=none \
uv run python -m eval.run_eval --kb memory --concurrency 1
```

标准命令在 `eval/reports/` 输出报告；本次执行另外逐条保存中间结果，避免暂停时丢失进度。
首次使用 `cl100k_base` 时，tiktoken 需要下载公开分词表并缓存；这是测试环境准备，不是模型云端调用。
售后回答的忠实度由同一个被测模型评审；这些自动指标不等于独立人工验收。

## 合并后的代码验证

本地合并提交 `a4e82f4` 已通过以下检查；之后仅新增本次测试的文档和结果文件：

- Maven `verify`：266 项测试通过，JaCoCo 覆盖率门禁通过（JDK 21，编译目标 17）。
- Python 3.12.11：108 项 pytest、ruff check 和格式检查通过。
- 前端：类型检查、35 项单测、生产构建通过；lint 无错误，有 4 条已有的 Hook 依赖警告。
- Playwright：桌面 / 移动端共 25 项通过，后端由浏览器内 fixture 模拟。
- 三份 Compose 配置验证通过。

本机 Node 为 26.10.0，直接运行旧版 Vitest 时，实验性 Web Storage 与 jsdom 的 `localStorage` 冲突，7 项测试失败。
使用 `NODE_OPTIONS=--no-experimental-webstorage npm run test` 后 35/35 通过；仓库 CI 使用 Node 20。
上述检查没有启动真实 Stripe、MySQL、Redis 或 ES，不能记作这些外部依赖的端到端验收。

## Mac 能否使用 vLLM

可以。这台 Apple Silicon Mac 满足社区维护的 **vLLM-Metal** 插件要求（macOS 15+），通过 MLX / Metal 使用 GPU。
原生 vLLM 的 macOS CPU 路径仍属于实验支持，需要源码构建。
依据：[vLLM 安装文档](https://docs.vllm.ai/en/stable/getting_started/installation/)、[Metal 安装文档](https://docs.vllm.ai/projects/vllm-metal/en/stable/installation/)、[CPU 文档](https://docs.vllm.ai/en/stable/getting_started/installation/cpu/)。

仓库的 `docker/docker-compose.vllm.yml` 使用 NVIDIA 镜像、`gpus: all` 和 CUDA 相关设置，是先前 RTX 5090 的实验配置；在这台 Mac 上测试 GPU 推理需要单独的原生 Metal 环境。

官方 Metal 安装方式：

```bash
brew tap vllm-project/vllm-metal https://github.com/vllm-project/vllm-metal
brew install vllm-project/vllm-metal/vllm-metal
```

模型支持矩阵列出了 Qwen3.8，并以 `mlx-community/Qwen3.8-27B-8bit` 为示例。
Ollama 的模型标签不是 vLLM 的模型路径，切换引擎应选插件支持的 checkpoint，并重新验证工具调用、思考开关、内存和速度。
参见 [Metal 模型支持矩阵](https://docs.vllm.ai/projects/vllm-metal/en/stable/supported_models/)。

本页记录第一轮 Ollama 测试。后续已实测 vLLM Metal、补齐并发矩阵与真实服务集成，见 [Mac 补测报告](MAC_VALIDATION_GAPS.md)；不同模型与权重的结果应分开比较。
