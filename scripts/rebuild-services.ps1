# StickyBeak dev helper: rebuild all backend jars via Docker Maven and restart the sb-run-* containers.
# Windows 上运行中的容器会锁定 target/*.jar，必须「停全部 → 构建 → 起全部」。
# 用法: powershell -File scripts/rebuild-services.ps1
$ErrorActionPreference = "Stop"
$repo = Resolve-Path "$PSScriptRoot\.."

$all = @("gateway", "auth", "product", "cart", "order", "payment", "notification")
$runContainers = $all | ForEach-Object { "sb-run-stickybeak-$_" }

Write-Host "==> stopping all service containers (Windows jar file locks)"
docker stop $runContainers 2>$null | Out-Null

Write-Host "==> mvn package (docker)"
docker run --rm -v "${repo}:/workspace" -v stickybeak-m2:/root/.m2 -w /workspace `
    maven:3.9-eclipse-temurin-17 mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "maven build failed" }

# SkyWalking agent：从官方镜像取出一次放到 .skywalking/agent（已加入 .gitignore），并启用 Spring Boot 3 / 网关需要的可选插件
$agentDir = Join-Path $repo ".skywalking\agent"
if (-not (Test-Path (Join-Path $agentDir "skywalking-agent.jar"))) {
    Write-Host "==> extracting SkyWalking agent 9.7.0"
    New-Item -ItemType Directory -Force -Path (Join-Path $repo ".skywalking") | Out-Null
    $cid = docker create apache/skywalking-java-agent:9.7.0-java17
    docker cp "${cid}:/skywalking/agent" $agentDir
    docker rm $cid | Out-Null
    foreach ($p in @("apm-springmvc-annotation-6.x-plugin", "apm-resttemplate-6.x-plugin", "apm-spring-cloud-gateway-4.x-plugin", "apm-spring-webflux-6.x-plugin")) {
        Copy-Item (Join-Path $agentDir "optional-plugins\$p-*.jar") (Join-Path $agentDir "plugins")
    }
}

# 容器网络 docker_default 内的服务环境变量
$envMap = @{
    "auth"         = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis")
    "product"      = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis", "ES_URIS=http://sb-elasticsearch:9200", "SEATA_ADDR=sb-seata:8091")
    "cart"         = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis")
    "order"        = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis", "RABBITMQ_HOST=sb-rabbitmq", "SEATA_ADDR=sb-seata:8091")
    "payment"      = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis", "RABBITMQ_HOST=sb-rabbitmq", "PAYMENT_MOCK_WEBHOOK_ENABLED=true")
    "notification" = @("RABBITMQ_HOST=sb-rabbitmq", "MAIL_HOST=sb-mailhog")
    "gateway"      = @("REDIS_HOST=sb-redis")
}

foreach ($svc in $all) {
    $name = "sb-run-stickybeak-$svc"
    docker rm $name 2>$null | Out-Null
    $envArgs = @("-e", "NACOS_ADDR=sb-nacos:8848", "-e", "SW_AGENT_NAME=stickybeak-$svc", "-e", "SW_AGENT_COLLECTOR_BACKEND_SERVICES=sb-oap:11800")
    foreach ($kv in $envMap[$svc]) { $envArgs += @("-e", $kv) }
    $portArgs = @(); if ($svc -eq "gateway") { $portArgs = @("-p", "8080:8080") }
    docker run -d --name $name --network docker_default @envArgs @portArgs `
        -v "${repo}:/workspace" -v "${agentDir}:/skywalking/agent" -w /workspace `
        maven:3.9-eclipse-temurin-17 java -javaagent:/skywalking/agent/skywalking-agent.jar `
        -jar "stickybeak-$svc/target/stickybeak-$svc-0.0.1-SNAPSHOT.jar" | Out-Null
    Write-Host "==> started $name"
}
Write-Host "==> done. gateway: http://localhost:8080  SkyWalking UI: http://localhost:8088"
