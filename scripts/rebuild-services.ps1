# StickyBeak dev helper: rebuild all backend jars via Docker Maven and restart the sb-run-* containers.
# On Windows, running containers lock target/*.jar, so: stop ALL -> clean package -> start ALL.
# (clean matters: stale target/classes once produced a fat jar with a duplicated method.)
# Usage: powershell -File scripts/rebuild-services.ps1
$ErrorActionPreference = "Stop"
$repo = Resolve-Path "$PSScriptRoot\.."

$all = @("gateway", "auth", "product", "cart", "order", "payment", "notification")
$runContainers = $all | ForEach-Object { "sb-run-stickybeak-$_" }

Write-Host "==> stopping all service containers (Windows jar file locks)"
docker stop $runContainers 2>$null | Out-Null

Write-Host "==> mvn clean package (docker)"
docker run --rm -v "${repo}:/workspace" -v stickybeak-m2:/root/.m2 -w /workspace `
    maven:3.9-eclipse-temurin-17 mvn -q -DskipTests clean package
if ($LASTEXITCODE -ne 0) { throw "maven build failed" }

# Env vars inside the docker_default container network
$envMap = @{
    "auth"         = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis")
    "product"      = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis", "ES_URIS=http://sb-elasticsearch:9200", "RABBITMQ_HOST=sb-rabbitmq", "MINIO_ENDPOINT=http://sb-minio:9000", "MINIO_PUBLIC_ENDPOINT=http://localhost:9000")
    "cart"         = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis")
    "order"        = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis", "RABBITMQ_HOST=sb-rabbitmq")
    "payment"      = @("MYSQL_HOST=sb-mysql", "REDIS_HOST=sb-redis", "RABBITMQ_HOST=sb-rabbitmq")
    "notification" = @("RABBITMQ_HOST=sb-rabbitmq", "MAIL_HOST=sb-mailhog")
    "gateway"      = @("REDIS_HOST=sb-redis")
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
