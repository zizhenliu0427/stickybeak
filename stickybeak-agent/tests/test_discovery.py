import sys
import types

import pytest

from stickybeak_agent.config import Settings
from stickybeak_agent.discovery import ServiceResolver


class _Builder:
    def server_address(self, _addr):
        return self

    def log_level(self, _level):
        return self

    def username(self, _name):
        return self

    def password(self, _password):
        return self

    def build(self):
        return object()


class _FlakyNaming:
    """前几次注册时 gRPC 还没连上（和 Nacos 同时启动的情形）。"""

    def __init__(self, failures: int):
        self.failures = failures
        self.registered = []

    async def register_instance(self, instance):
        if self.failures > 0:
            self.failures -= 1
            raise RuntimeError("client not connected")
        self.registered.append(instance)


def _fake_nacos(monkeypatch, naming):
    module = types.ModuleType("v2.nacos")
    module.ClientConfigBuilder = _Builder
    module.RegisterInstanceParam = lambda **kwargs: kwargs

    class NacosNamingService:
        @staticmethod
        async def create_naming_service(_config):
            return naming

    module.NacosNamingService = NacosNamingService
    monkeypatch.setitem(sys.modules, "v2.nacos", module)


async def test_registration_retries_until_nacos_is_ready(monkeypatch):
    naming = _FlakyNaming(failures=2)
    _fake_nacos(monkeypatch, naming)
    resolver = ServiceResolver(Settings(nacos_addr="nacos:8848", advertise_ip="10.0.0.5"), port=8087)

    await resolver.start(attempts=5, retry_delay=0)

    assert naming.failures == 0
    assert naming.registered == [
        {"service_name": "stickybeak-agent", "ip": "10.0.0.5", "port": 8087, "metadata": {"lang": "python"}}
    ]


async def test_registration_gives_up_after_the_last_attempt(monkeypatch):
    _fake_nacos(monkeypatch, _FlakyNaming(failures=10))
    resolver = ServiceResolver(Settings(nacos_addr="nacos:8848", advertise_ip="10.0.0.5"))

    with pytest.raises(RuntimeError, match="client not connected"):
        await resolver.start(attempts=3, retry_delay=0)
