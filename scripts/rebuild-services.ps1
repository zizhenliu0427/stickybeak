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

# 容器网络 docker_default 内的服务环境变量
$envMap = @{
    "auth"    = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis")
    "product" = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis")
    "gateway" = @("REDIS_HOST=sb-redis")
}

foreach ($svc in $all) {
    $name = "sb-run-stickybeak-$svc"
    docker rm $name 2>$null | Out-Null
    $envArgs = @("-e", "NACOS_ADDR=sb-nacos:8848")
    foreach ($kv in $envMap[$svc]) { $envArgs += @("-e", $kv) }
    $portArgs = @(); if ($svc -eq "gateway") { $portArgs = @("-p", "8080:8080") }
    docker run -d --name $name --network docker_default @envArgs @portArgs `
        -v "${repo}:/workspace" -w /workspace `
        maven:3.9-eclipse-temurin-17 java -jar "stickybeak-$svc/target/stickybeak-$svc-0.0.1-SNAPSHOT.jar" | Out-Null
    Write-Host "==> started $name"
}
Write-Host "==> done. gateway: http://localhost:8080"
