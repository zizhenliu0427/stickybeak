# Mac 补测：5090 已测项目（2026-10-28 至 29）

本轮在 Apple M4 Pro / 48 GiB / macOS 27.0.1 上补齐先前仅在 RTX 5090 环境运行过的项目。
这是现有桌面环境实测：保留了其他项目的 Docker 容器和应用，测试开始时整机已经使用约 15 GiB swap。
没有固定供电、功耗模式或温度；27B Metal 测试期间电池持续放电。结果用于比较本轮部署，不视为受控硬件基准。
使用独立容器项目 `stickybeak-mac-validation`、独立数据库与本机测试端口；未修改其他项目的数据。

## Ollama 27B 同口径请求矩阵

复用 `eval/benchmark.py`：目标输入 256 / 2048 个词、客户端并发 1 / 4、每组 8 次请求，固定输出上限 128 token。
每个请求使用唯一随机前缀，预热不计入结果，思考关闭。
**32/32 请求成功，所有请求实际均输出 128 token。**

| 输入 / 并发 | Mac TTFT p50 / p95 (s) | Mac 总延迟 p95 (s) | Mac 总吞吐 (token/s) | 5090 旧总吞吐 (token/s) |
|---|---:|---:|---:|---:|
| 约 381 token / 1 | 3.22 / 3.38 | 8.64 | 15.31 | 8.68 |
| 约 381 token / 4 | 27.71 / 29.01 | 33.90 | 15.44 | 29.32 |
| 约 2620 token / 1 | 22.36 / 23.93 | 29.64 | 4.61 | 8.70 |
| 约 2621 token / 4 | 111.30 / 111.67 | 117.29 | 4.37 | 28.29 |

吞吐按所有请求的服务端 completion_tokens 总和 / 整组墙钟时间计算，包含输入处理与排队，不是纯解码速度。
每组只有 8 个样本，p95 等于该组最大值。这不是长期稳定性或容量上限测试。

Mac 是 Ollama 0.35.1 的 `qwen3.8:27b-mlx` NVFP4、32768 上下文，日志观察到自动推测解码；
5090 是 vLLM 0.32.0 的 NVIDIA NVFP4/FP8 checkpoint、12288 上下文、FP8 KV、未启用 MTP。
因此表格比较的是两套实测部署，不能归因成纯硬件差距。

当前 Ollama MLX 请求串行执行。短输入并发 1 / 4 总吞吐几乎不变，长输入并发 4 的首字延迟中位数约 111 秒。
这是服务调度与输入处理的重要限制；当前版本源码明确使用固定 slot 并说明串行执行：
[Ollama 0.35.1 pipeline.go](https://github.com/ollama/ollama/blob/v0.35.1/mlxrunner/pipeline.go#L90)。

持续采样共 546 次，模型报告的驻留量峰值约 25.13 GiB；runner 日志的分配器峰值为 28.21 GiB。
整机 swap 从约 14.67 GiB 增至 16.78 GiB，包括其他应用，不能当作模型自身 swap。
这些都不是 NVIDIA `nvidia-smi` 的整卡显存口径；Mac 的整机 GPU 峰值没有可比测量。
原始请求、环境和内存采样见 [27B 性能报告](validation/ollama-mac-benchmark-20261028.json)。

复现命令（在 `stickybeak-agent` 目录）：

```bash
uv run python -m eval.benchmark \
  --base-url http://127.0.0.1:11434/v1 --model qwen3.8:27b-mlx \
  --reasoning-effort none --concurrency 1 4 --context-words 256 2048 \
  --requests 8 --output-tokens 128 --timeout 180
```

## 真实模型的上下文压缩与降级

- 经 `pack_context` 压缩的历史只保留一条 system、包含不可信历史摘要，真实 27B 返回 `READY_27B`：通过。
- 主模型指向不可达的本机端口，先单独确认连接失败；生产 `ModelProvider` 降级链转入真实 Ollama，返回 `FALLBACK_OK`：通过。
- 全程只请求本机，不使用云端模型。

见 [原始结果](validation/ollama-mac-model-checks-20261028.json)。

## vLLM Metal：同修订 4B

官方稳定版 vLLM Metal 0.32.0 安装到 `/private/tmp/stickybeak-vllm-metal`，核心为 vLLM 0.32.0+cpu；
`+cpu` 是核心 wheel 名称，实际由插件使用 MLX 0.32.2 与原生 paged-attention Metal kernels，在 Apple GPU 上运行。
PyTorch 2.13.0、mlx-lm 0.32.0、Transformers 5.19.0。

使用 `Qwen/Qwen3-4B`，固定修订 `1cfa9a7208912126459214e8b04321603b3df60c`，与 5090 旧 4B 基线完全相同，BF16、不量化。
上下文 12288、最大序列数 4、分块预填充 1024、内存比例 0.35、eager、关闭思考与推测解码。
KV 分配日志为 5.17 GiB；它不是整机内存峰值。

健康检查、模型列表和 32 个流式请求全部通过。

| 输入 / 并发 | Mac TTFT p50 / p95 (s) | Mac 总延迟 p95 (s) | Mac 总吞吐 (token/s) | 5090 旧总吞吐 (token/s) |
|---|---:|---:|---:|---:|
| 约 381 token / 1 | 0.51 / 0.52 | 5.67 | 22.94 | 59.34 |
| 约 381 token / 4 | 1.87 / 1.92 | 7.71 | 71.03 | 203.29 |
| 约 2620 token / 1 | 3.59 / 3.64 | 9.03 | 14.22 | 52.32 |
| 约 2620 token / 4 | 6.54 / 14.59 | 29.67 | 24.78 | 129.02 |

4B 短输入并发 4 总吞吐约为并发 1 的 3.10 倍，长输入约 1.74 倍。
同权重有助于比较，但 5090 使用旧 vLLM 0.11.0、CUDA、最大序列数 16 等不同配置，仍不等于仅更换 GPU 的严格实验。
见 [4B 原始性能报告](validation/vllm-metal-4b-benchmark-20261028.json)。

四条偏好记忆 / 协作用例 **3/4 通过**，无运行错误、无未确认写操作。
`enhance-memory-01` 没有调用商品检索，也没有带入保存的 category / budget；这是业务质量失败，不是 HTTP 成功即可通过。
另一条偏好覆盖和两条协作用例通过。见 [4B 逐条结果](validation/vllm-metal-4b-assistant-20261028.json)。
4B 临时权重已在结果保存后移除，用于给 27B 实验腾出磁盘；原有 Ollama 模型未删除。

## 真实 RAG 与商城链路

- `bge-m3:latest` 实际返回 1024 维向量，ES 8.14.3 + IK 使用独立索引。
- 当前语料是 **68 个片段**：首次入库 68，第二次跳过全部 68；一个片段的模型签名被标为过期后，仅该片段重新嵌入。
- 四条中英文检索探测：商品损坏与配送问题命中相应资料；发票和量子纠错问题没有返回片段。
- 这些探测沿用阈值 0.79、RRF、无 reranker。英文配送问题的首个融合结果是退款时效，后续含正确配送资料；本轮没有据此声称检索排序完美，也没有重新跑全部售后评测。

旧报告的 66 个片段是历史记录；初次补测脚本沿用了旧数量，之后按当前语料数量修正并在独立索引重跑。
见 [嵌入 / 入库 / 检索原始结果](validation/mac-rag-20261028.json)。

通过本机 `Nginx :18000 → Java gateway :18080 → Python agent / MCP → Java 商品与订单服务`，使用真实 MySQL 与 Redis、合成测试账户 / 商品 / 订单，完成 **15 项检查**：

- 网关拒绝伪造用户头；真实 Java 注册成功。
- 匿名 MCP 可查商品、不可查订单；签发 MCP 令牌后可查自己的测试订单。
- MCP 令牌不能访问普通 REST 订单 API（403）；撤销后 MCP 协议请求返回 401。
- 真实 27B 导购、售后与订单查询 SSE 完成，回答包含测试商品及价格、售后引用、测试订单。
- 取消订单先返回确认事件且 MySQL 状态保持 pending；重启助手进程后确认仍存在，批准后数据库变成 cancelled。
- 独立 MySQL 连接池共享偏好，隔离不同用户，删除对另一连接池可见。
- 真实 Redis Guard 累积 10 次分钟额度后，下一次 HTTP 聊天请求返回 429 与 Retry-After。

最初用公开 `/api/mcp/health` 探测撤销得到 200，该探测不验证凭证有效性，已保留为无效探测说明；补用 `/api/mcp` 验证了撤销前 200 / 撤销后 401。
见 [真实集成原始结果](validation/mac-integration-20261028.json)。

本轮 Java 服务使用静态服务发现，关闭 Nacos、Seata 与 RabbitMQ 消费监听器；未验证真实支付、消息投递、分布式事务或 SkyWalking 链路上报。
取消的是直接种入独立数据库的合成待付款订单，不代表验证了支付会话与库存占用的完整结算生命周期。
取消前后两段模型答复切换到了英文；这些业务检查没有把语言一致性作为通过条件。

## Kubernetes

独立 k3d 5.8.3 / k3s 1.31.5 集群，ARM64 助手镜像，实际共享上面的 MySQL / Redis / MCP。
**7/7 检查通过**：两副本就绪、跨 Pod 偏好读取与用户隔离、滚动重启后偏好保留、手动 2→3→2 扩缩容、跨 Pod 删除可见、HPA 指标可读。
HPA 保持 min=2 / max=4 / CPU target=70%；这轮与旧报告一样，没有用负载触发自动扩容。
使用独立 kubeconfig，集群验证完成后已删除。见 [集群原始结果](validation/mac-kubernetes-20261028.json)。

## 发现并修复的问题

原助手 Dockerfile 在 Linux ARM64 上构建失败：Nacos SDK 锁定的 `psutil 5.9.5` 缺少对应 wheel，而 slim 镜像没有 gcc。
改为多阶段构建，在构建阶段安装 gcc / libc6-dev；最终运行镜像只复制应用与虚拟环境。
修复后 ARM64 镜像构建成功，并用于上面的真实 Kubernetes 验证。

压测 CLI 增加可选 `--reasoning-effort none`，用于关闭 Ollama 思考；默认 vLLM 请求保持原来的参数。
Python **109 项 pytest 全部通过**，ruff check / format 通过。

## 27B Metal（2026-10-29 继续补测）

`mlx-community/Qwen3.8-27B-4bit` 已通过 Metal 加载，固定修订 `10c35caafbb80f7dc6a7a432cdd11af10a6d4818`。
三个 safetensors 权重合计 16,054,541,349 字节，按官方 LFS SHA-256 逐片核验；13 个必需模型文件全部检查完成。
见 [文件与权重校验结果](validation/vllm-metal-27b-weights-20261028.json)。

这是 MLX affine 4bit（group size 64），与 Ollama NVFP4、5090 NVIDIA NVFP4/FP8 都是不同量化。
本轮使用相同 vLLM / Metal 0.32.0 环境，文本模式、12288 上下文、最多 4 个序列、1024 分块预填充、内存比例 0.45、eager、无推测解码。
日志确认使用 Apple GPU 和原生 paged-attention Metal kernels，KV 预算为 2.10 GB。

模型健康与列表、金额计算 JSON Schema、自动工具调用、单 system 压缩历史请求 **4/4 通过**。
见 [27B 基础结果](validation/vllm-metal-27b-smoke-20261028.json)。

已保存短输入（约 381 token）两组共 **16 次成功请求**，全部实际输出 128 token：

| 并发 | TTFT p50 / p95 (s) | 总延迟 p95 (s) | 总输出吞吐 (token/s) |
|---:|---:|---:|---:|
| 1 | 3.15 / 3.22 | 12.70 | 10.25 |
| 4 | 12.45 / 12.65 | 29.08 | 19.77 |

短输入并发 4 总吞吐约为串行的 1.93 倍。它高于本轮 Ollama MLX 的并发吞吐，但量化格式、上下文与推测解码配置不同，不能把差异全归因于引擎。
见 [已完成组与暂停状态](validation/vllm-metal-27b-benchmark-20261028.json)。

用户要求电量到 20% 就暂停。每 5 秒检查一次，于 **2026-10-28 22:15:44（悉尼时间）** 检测到 20% 后停止本次推理与测试进程。
当时长输入串行组被中断，没有保存为完整组；长输入并发 4 和后续 13 条业务用例尚未执行。
这份历史记录本身不能记作完整 32/32 性能通过。
见 [电量暂停记录](validation/mac-battery-stop-20261028.json)。

此前的真实 Java/Python 测试服务、Compose 测试容器与 k3d 集群均已停止或移除；测试数据卷保留。
当时 vLLM Metal 环境及已校验的 27B 权重保留在临时目录，原有 Ollama 聊天模型和新增的 bge-m3 嵌入模型也保留。

10 月 29 日恢复测试时电量为 90%，但系统重启已清空临时环境和权重。
从公开固定修订重新下载三个分片并校验原 SHA-256，恢复 vLLM / Metal 0.32.0、MLX 0.32.2、mlx-lm 0.32.0、Torch 2.13.0、Transformers 5.19.0。
其他依赖由安装器重新解析，完整版本、权重核验与环境说明见 [恢复环境记录](validation/mac-resume-environment-20261029.json)。
清理 uv 未使用缓存；系统随后自动回收可释放空间。没有手工删除用户文件或备份快照。
仍每 5 秒检查电量，20% 暂停阈值继续有效。

只重跑被中断的长输入两组，没有重复已保存的短输入组。上下文 / 并发 / 输出上限保持原参数：

| 实际平均输入 Token | 并发 | TTFT p50 / p95 (s) | 总延迟 p95 (s) | 总输出吞吐 (token/s) |
|---:|---:|---:|---:|---:|
| 2620.75 | 1 | 21.58 / 22.39 | 31.50 | 4.15 |
| 2620.25 | 4 | 29.90 / 85.22 | 159.12 | 5.22 |

补测 **16/16 请求成功**，均实际输出 128 token，合并两天保存的组共 **32/32**。
这不是同一次连续压测；恢复环境、供电、温度与后台负载未受控，第一组早期还在下载 / 解压 OAP 镜像。
每组仍只有 8 个样本，p95 是该组最大值。四并发长输入吞吐约为串行的 1.26 倍；同口径旧 5090 为 28.29 token/s，但两者量化和后端不同。
原始新增组见 [长输入补测](validation/vllm-metal-27b-long-benchmark-20261029.json)，历史短输入和暂停状态保留在 10 月 28 日报告中。

13 条实际模型业务用例已全部执行，**12/13 通过**，运行错误 0，未确认写操作 0，耗时 355.29 秒：

- 泛推荐 8/8、加购确认 1/1、双 Agent 协作 2/2。
- 偏好记忆 1/2；`enhance-memory-01` 回答 “Do you prefer birds or transport signs?”，没有使用已保存的 bird / AUD 15 偏好调用商品检索。
- 同一用例在旧 5090 27B 复测也失败；这不能归因为 Mac 不支持该功能。其他场景通过也不能掩盖这条质量失败。

见 [逐条业务结果](validation/vllm-metal-27b-assistant-20261029.json)。商品 / 订单仍为 fixtures，读工具经进程内 MCP，写操作停在确认节点。
临时脚本最初与仓库 `eval` 包重名，导致两阶段在执行任何用例前启动失败；改名后运行以上正式评测，未改变判定规则。
保留 [评测与追踪探测修正说明](validation/mac-eval-harness-error-20261029.json)。

## SkyWalking 跨语言链路（2026-10-29）

官方 Linux ARM64 OAP 10.4.0 + BanyanDB 0.10.3、Java agent 9.7.0 / JDK 21、仓库 Python agent / Python 3.12，使用独立测试容器和原有独立数据库。
Java agent 发布包按官方 SHA-512 校验，启用与仓库 Dockerfile 相同的四个 Spring 可选插件。
OAP 使用当前版本的 `queryTraces` 协议读取真实上报结果，**7/7 检查通过**：

- 网关匿名 MCP 商品检索实际返回 MySQL 中的测试商品 `990001`，AUD 19.90。
- 同一条 trace 包含 gateway → Python MCP → Java product，共 37 个 span，两段 CROSS_PROCESS 父 segment 均能对应实际上游，无错误 span。
- 经网关访问真实 Python 助手健康接口返回 UP、MCP 已连接，并在 OAP 中确认 gateway → agent 的 CROSS_PROCESS 关联。

见 [请求响应、完整 span 与判定](validation/mac-skywalking-20261029.json)。这轮没有发起支付、MQ 或分布式事务，也没有把 LLM 生成计入上述健康接口检查。

最初要求 Python MCP 返回 `X-Trace-Id` 的探测不适用：仓库响应头过滤器只应用于 Java Servlet，Python 响应不会经过该过滤器。
响应头缺失不等于 trace 缺失。保留 [初次无效探测](validation/mac-skywalking-initial-probe-20261029.json)，改为验证 OAP 实际父子引用后通过；没有修改应用追踪代码。

## 完整真实 ES 售后评测（2026-10-29）

保留的独立 ES + IK 索引共有 68 个片段。恢复时使用明确的 `bge-m3:latest` 签名，重新向量化全部片段；实际 Ollama 模型 digest 与 10 月 28 日一致，输出 1024 维。
聊天模型为上述 Metal 27B 4bit，向量阈值 0.79、RRF、无 reranker。全程使用本机模型，没有云端调用。

现有数据集全部 17 条 `after_sales` 用例已执行，**17/17 通过**，耗时 481.10 秒，运行错误 0、未确认写操作 0：

- 意图 17/17、适用的召回检查 15/15、忠实度评审 16/16、未知问题拒答 2/2。
- 检索实际访问 ES 和 bge-m3；售后读工具经进程内 MCP，商品 / 订单数据依然使用 fixtures。
- 忠实度由同一个被测 27B 模型自动评审，不等于独立人工验收；通过与当前数据集的全部判定相符，不代表所有自然语言问题都能正确回答。

见 [逐条回答、引用、检索证据和判定](validation/vllm-metal-27b-rag-20261029.json)。
临时评测脚本退出时记录了一条 ES aiohttp 客户端未显式关闭的清理警告；结果已完整保存、进程已退出，未计为用例运行错误。这不是模型推理失败。

## 本轮结束状态

10 月 29 日继续的四项清单均已执行完：27B Metal 长输入两组、13 条业务用例、17 条真实 ES 售后用例、SkyWalking 跨语言追踪。
性能四组累计 32/32 成功，业务 12/13、真实 ES 售后 17/17、追踪 7/7；“执行完成”不表示忽略那一条偏好质量失败。
结果核验与停止状态见 [本轮结束记录](validation/mac-validation-completion-20261029.json)。

本轮模型、嵌入、Java/Python 测试进程和独立 Compose 容器均已停止；MySQL / ES 数据卷保留，未停止其他项目的容器。
补测结束时恢复的 Metal 环境和 27B 权重在 `/private/tmp`；随后已按用户要求删除，见下面的模型清理记录。

负载触发 HPA 自动扩容及长期稳定性不在本次对应 5090 的复测清单中，两边先前都未验证。
真实支付、MQ、Seata 完整交易生命周期也未在本轮覆盖；不据本轮 AI 与追踪结果宣称全商城端到端验收。

## 模型清理（2026-10-29）

用户要求只保留最初安装的 Ollama Qwen3.8 27B。已通过 Ollama 官方接口删除新增的 `bge-m3:latest`，并删除约 16.05 GB 的 MLX 4bit 27B 权重、Metal 临时虚拟环境和对应可再下载的依赖缓存。
Ollama 模型列表现仅有 `qwen3.8:27b-mlx`。其 manifest SHA-256 仍为 `5642e97495e1a088883805981563dcdc4a040c2f53388b7a41d1f24d3622cf7e`，全部 1210 个引用文件的存在与大小均再次核验通过。

实测可用空间从约 32.81 GB 增至 48.74 GB（约 30.56 → 45.40 GiB），增加约 15.93 GB；这是文件系统可用空间变化，不是文件逻辑大小相加。
测试报告保留；复现 Metal 或向量检索测试需要重新安装对应环境 / 模型。清理记录见 [模型清理与保留模型核验](validation/mac-model-cleanup-20261029.json)。

## Docker 清理（2026-10-29）

用户随后授权清理无用 Docker 资源。删除 29 个没有容器引用的镜像、所有未使用构建缓存，以及 171 个未引用卷：39 个可重建的 node_modules 卷、130 个空匿名卷、本轮的两份合成 MySQL / ES 测试卷。
先以断网、只读挂载的临时容器识别卷内容，再删除确定可重建或属于本轮测试的卷；其他历史数据库、Redis、监控和未知数据保留。

Docker 镜像数 49→20、卷数 257→86、构建缓存归零。全部 25 个原有容器保留，其中 16 个继续运行，原先 healthy 的状态均未改变。
原始 Ollama 27B manifest 与 1210 个引用文件再次核验通过。

Docker.raw 的实际文件分配从约 89 GiB 降到 26.53 GiB，下降约 62 GiB；这不等于宿主机空闲空间已经增加相同数量。
按 [Docker 官方文档](https://docs.docker.com/desktop/troubleshoot-and-support/faqs/macfaqs/) 又执行了一次空闲块回收，临时回收工具镜像随后也删除。
最后宿主机实际空闲仍约 43.87 GiB。旧 APFS / Time Machine 本地快照仍存在，可能保留已删除的数据块；尚未测得它们的精确保留量，也没有删除恢复点或中断正在运行的备份。
Apple 说明本地快照可按需自动回收，见 [官方说明](https://support.apple.com/en-gb/102154)。

上述两份 MySQL / ES 测试数据卷已在此阶段删除；先前测试结束时“数据卷保留”的记录是当时状态。
详细计数、保护项与实际空间数值见 [Docker 清理记录](validation/mac-docker-cleanup-20261029.json)。
