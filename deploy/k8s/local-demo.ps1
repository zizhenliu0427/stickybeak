param(
    [string]$K3dPath = 'k3d',
    [string]$ClusterName = 'stickybeak-ai-demo'
)
# 从仓库根目录运行；复用正在运行的本地 Compose 基础设施，绝不切换默认 kube context。
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$kubeFile = Join-Path $PSScriptRoot 'kubeconfig.yaml'
$clusters = & $K3dPath cluster list -o json | ConvertFrom-Json
if (-not ($clusters | Where-Object name -eq $ClusterName)) {
    & $K3dPath cluster create $ClusterName --servers 1 --agents 0 --network docker_default `
        --api-port 127.0.0.1:16443 --kubeconfig-switch-context=false --kubeconfig-update-default=false --wait --timeout 180s
}
& $K3dPath kubeconfig get $ClusterName | Set-Content -LiteralPath $kubeFile -Encoding utf8
& $K3dPath image import stickybeak-agent:ai-demo -c $ClusterName
kubectl --kubeconfig $kubeFile apply -f (Join-Path $PSScriptRoot 'namespace.yaml')

function Get-ContainerIp([string]$Name) {
    $containerInfo = docker inspect $Name | ConvertFrom-Json
    $ip = $containerInfo[0].NetworkSettings.Networks.docker_default.IPAddress
    if (-not $ip) { throw "$Name is not running on docker_default" }
    return $ip
}
$existingAgent = docker inspect sb-agent | ConvertFrom-Json
$agentEnv = @{}
foreach ($entry in $existingAgent[0].Config.Env) {
    $parts = $entry.Split('=', 2)
    $agentEnv[$parts[0]] = $parts[1]
}
# 仅向 Kubernetes Secret 传递必需字段；不打印、不把凭据写到工作区。
$secretValues = @{ MYSQL_PASSWORD = 'root' }
foreach ($key in @('MYSQL_PASSWORD', 'REDIS_PASSWORD', 'OPENAI_API_KEY', 'DASHSCOPE_API_KEY',
                   'DEEPSEEK_API_KEY', 'GEMINI_API_KEY', 'VLLM_API_KEY')) {
    if ($agentEnv[$key]) { $secretValues[$key] = $agentEnv[$key] }
}
@{ apiVersion = 'v1'; kind = 'Secret'; metadata = @{ name = 'agent-secret'; namespace = 'stickybeak-ai' };
   type = 'Opaque'; stringData = $secretValues } | ConvertTo-Json -Depth 5 -Compress |
    kubectl --kubeconfig $kubeFile apply -f -

kubectl --kubeconfig $kubeFile apply -k $PSScriptRoot
$serviceUrls = @(
    "stickybeak-product=http://$(Get-ContainerIp 'sb-product'):8082"
    "stickybeak-cart=http://$(Get-ContainerIp 'sb-cart'):8083"
    "stickybeak-order=http://$(Get-ContainerIp 'sb-order'):8084"
    "stickybeak-mcp=http://$(Get-ContainerIp 'sb-mcp'):8089"
) -join ','
$localConfig = @{
    MYSQL_HOST = (Get-ContainerIp 'sb-mysql'); REDIS_HOST = (Get-ContainerIp 'sb-redis')
    SERVICE_URLS = $serviceUrls; NACOS_ADDR = ''
}
foreach ($key in @('LLM_CHAIN', 'OPENAI_BASE_URL', 'OPENAI_MODEL', 'QWEN_MODEL', 'QWEN_BASE_URL',
                   'DEEPSEEK_MODEL', 'DEEPSEEK_BASE_URL', 'GEMINI_MODEL', 'GEMINI_BASE_URL',
                   'OLLAMA_BASE_URL', 'OLLAMA_MODEL', 'VLLM_BASE_URL', 'VLLM_MODEL')) {
    if ($agentEnv[$key]) { $localConfig[$key] = $agentEnv[$key] }
}
# Patch 只含地址与模型名称。配置变更后滚动重启，让新副本读取更新值。
@{ data = $localConfig } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'local-config.yaml')
kubectl --kubeconfig $kubeFile -n stickybeak-ai patch configmap agent-config --patch-file (Join-Path $PSScriptRoot 'local-config.yaml')
kubectl --kubeconfig $kubeFile -n stickybeak-ai rollout restart deployment/agent
kubectl --kubeconfig $kubeFile -n stickybeak-ai rollout status deployment/agent --timeout=180s
kubectl --kubeconfig $kubeFile -n stickybeak-ai get pods
