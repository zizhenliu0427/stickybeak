# 最小 Kubernetes 实践

只部署无状态 AI 服务，MySQL、Redis、MCP 与 Java 业务服务沿用现有部署。不是整个商城的生产 K8s 迁移。
两个副本共享 MySQL 对话/偏好与 Redis 限流；单个 thread 的并发写入仍需客户端串行发送，不承诺并发编辑同一对话。

## 部署

1. 构建 `docker build -t stickybeak-agent:ai-demo stickybeak-agent`，推送至集群可访问的仓库或导入本地集群。
2. 修改 `configmap.yaml` 的私网地址、模型；复制 `secret.env.example` 为 `secret.env` 并填写。
3. 在选定 context 下执行：

```powershell
kubectl apply -f deploy/k8s/namespace.yaml
kubectl -n stickybeak-ai create secret generic agent-secret --from-env-file=deploy/k8s/secret.env
kubectl apply -k deploy/k8s
kubectl -n stickybeak-ai rollout status deployment/agent --timeout=180s
kubectl -n stickybeak-ai get pods
```

`/agent/health` 是进程存活；`/agent/ready` 等待模型配置与 MCP 工具加载完成，未就绪返回 503。
就绪不意味着外部模型端点/数据库永不失败；运行期仍由调用错误处理兜底。
Service 只用 ClusterIP，不把相信 `X-User-Id` 的助手端口公开给公网；业务访问必须经过剥离外部身份头的网关。

## 演示滚动更新与扩缩容

```powershell
kubectl -n stickybeak-ai rollout restart deployment/agent
kubectl -n stickybeak-ai rollout status deployment/agent --timeout=180s
kubectl -n stickybeak-ai scale deployment/agent --replicas=3
kubectl -n stickybeak-ai rollout status deployment/agent --timeout=180s
kubectl -n stickybeak-ai scale deployment/agent --replicas=2
```

`maxUnavailable=0`、`maxSurge=1` 保留旧就绪副本，资源 requests/limits 为 100m/256Mi 与 1 CPU/768Mi。
HPA 是可选项：有 metrics-server 后 `kubectl apply -f deploy/k8s/hpa.yaml`，检查 `kubectl get hpa -n stickybeak-ai`。
CPU 只能反映 HTTP 编排服务负载，远程推理瓶颈不能靠增加 Agent 副本解决。长 SSE 在重启时仍可能中断，不能宣称无损迁移。

本机验证步骤和实测结果见 [AI 增强验收](../../docs/AI_ENHANCEMENTS.md)。
