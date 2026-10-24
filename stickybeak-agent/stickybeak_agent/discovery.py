"""Nacos 注册与下游服务寻址。

- 启动时把自己注册为 stickybeak-agent，网关用 lb://stickybeak-agent 转发 /api/agent/**
- 调 Java 服务时按服务名从 Nacos 取健康实例，随机选一个；没配置 Nacos 时用 SERVICE_URLS 里的静态地址
"""

from __future__ import annotations

import logging
import random
import socket

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
    def __init__(self, settings: Settings):
        self._settings = settings
        self._static = settings.static_service_urls()
        self._naming = None
        self._ip = settings.advertise_ip or local_ip()

    @property
    def nacos_enabled(self) -> bool:
        return bool(self._settings.nacos_addr)

    async def start(self) -> None:
        if not self.nacos_enabled:
            log.info("NACOS_ADDR not set, using static service urls %s", self._static)
            return
        from v2.nacos import ClientConfigBuilder, NacosNamingService, RegisterInstanceParam

        builder = ClientConfigBuilder().server_address(self._settings.nacos_addr).log_level("WARNING")
        if self._settings.nacos_username:
            builder = builder.username(self._settings.nacos_username).password(self._settings.nacos_password)
        self._naming = await NacosNamingService.create_naming_service(builder.build())
        await self._naming.register_instance(
            RegisterInstanceParam(
                service_name=self._settings.service_name,
                ip=self._ip,
                port=self._settings.app_port,
                metadata={"lang": "python"},
            )
        )
        log.info("registered %s at %s:%s in Nacos", self._settings.service_name, self._ip, self._settings.app_port)

    async def stop(self) -> None:
        if self._naming is None:
            return
        from v2.nacos import DeregisterInstanceParam

        try:
            await self._naming.deregister_instance(
                DeregisterInstanceParam(
                    service_name=self._settings.service_name, ip=self._ip, port=self._settings.app_port
                )
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
