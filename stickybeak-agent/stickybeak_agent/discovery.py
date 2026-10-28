"""Nacos 注册与下游服务寻址。

- 启动时把自己注册为 stickybeak-agent，网关用 lb://stickybeak-agent 转发 /api/agent/**
- 调 Java 服务时按服务名从 Nacos 取健康实例，随机选一个；没配置 Nacos 时用 SERVICE_URLS 里的静态地址
"""

from __future__ import annotations

import asyncio
import logging
import random
import socket

import httpx

from .config import Settings

log = logging.getLogger(__name__)


def local_ip() -> str:
    """本机出口 IP（不会真的发包）。"""
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        try:
            s.connect(("10.255.255.255", 1))
            return s.getsockname()[0]
        except OSError:
            return "127.0.0.1"


class ServiceResolver:
    def __init__(self, settings: Settings, service_name: str | None = None, port: int | None = None):
        """service_name / port：注册到 Nacos 的名字和端口（agent 与 MCP 服务各自不同）。"""
        self._settings = settings
        self._static = settings.static_service_urls()
        self._naming = None
        self._ip = settings.advertise_ip or local_ip()
        self._service = service_name or settings.service_name
        self._port = port or settings.app_port

    @property
    def nacos_enabled(self) -> bool:
        return bool(self._settings.nacos_addr)

    async def start(self, attempts: int = 30, retry_delay: float = 2.0) -> None:
        """注册到 Nacos。和 Nacos 同时启动（compose / ECS）时它往往还没就绪，所以失败后按间隔重试，而不是直接退出。"""
        if not self.nacos_enabled:
            log.info("NACOS_ADDR not set, using static service urls %s", self._static)
            return
        from v2.nacos import ClientConfigBuilder, NacosNamingService, RegisterInstanceParam

        builder = ClientConfigBuilder().server_address(self._settings.nacos_addr).log_level("WARNING")
        if self._settings.nacos_username:
            builder = builder.username(self._settings.nacos_username).password(self._settings.nacos_password)
        instance = RegisterInstanceParam(
            service_name=self._service,
            ip=self._ip,
            port=self._port,
            metadata={"lang": "python"},
        )
        for attempt in range(1, attempts + 1):
            try:
                if self._naming is None:
                    self._naming = await NacosNamingService.create_naming_service(builder.build())
                await self._naming.register_instance(instance)
                log.info("registered %s at %s:%s in Nacos", self._service, self._ip, self._port)
                return
            except Exception as exc:  # gRPC 还没连上时抛 NacosException(client not connected)
                if attempt == attempts:
                    raise
                log.warning("Nacos registration attempt %s/%s failed: %s", attempt, attempts, exc)
                await asyncio.sleep(retry_delay)

    async def stop(self) -> None:
        if self._naming is None:
            return
        from v2.nacos import DeregisterInstanceParam

        try:
            await self._naming.deregister_instance(
                DeregisterInstanceParam(service_name=self._service, ip=self._ip, port=self._port)
            )
        finally:
            await self._naming.shutdown()

    async def base_url(self, service: str) -> str:
        if self._naming is not None:
            from v2.nacos import ListInstanceParam

            instances = await self._naming.list_instances(ListInstanceParam(service_name=service, healthy_only=True))
            instances = [i for i in instances if i.enabled]
            if instances:
                chosen = random.choice(instances)
                return f"http://{chosen.ip}:{chosen.port}"
            log.warning("no healthy instance of %s in Nacos, trying static url", service)
        url = self._static.get(service)
        if not url:
            raise LookupError(f"no instance of {service}")
        return url


class DiscoveryTransport(httpx.AsyncBaseTransport):
    """类似 Spring 的 lb://：URL 里的主机名是服务名（stickybeak-*）时，每次请求按服务名解析成实例地址。"""

    def __init__(self, resolver: ServiceResolver, inner: httpx.AsyncBaseTransport | None = None):
        self._resolver = resolver
        self._inner = inner or httpx.AsyncHTTPTransport()

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        if request.url.host.startswith("stickybeak-"):
            target = httpx.URL(await self._resolver.base_url(request.url.host))
            request.url = request.url.copy_with(scheme=target.scheme, host=target.host, port=target.port)
            request.headers["Host"] = request.url.netloc.decode("ascii")
        return await self._inner.handle_async_request(request)

    async def aclose(self) -> None:
        await self._inner.aclose()
