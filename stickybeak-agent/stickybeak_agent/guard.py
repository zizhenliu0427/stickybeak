"""按用户限流与每日额度（Issue 13.6），计数放在 Redis，多实例共享。

- 每分钟请求数：防刷
- 每日请求数与每日 token 数：控成本（token 在每次对话结束后按实际用量累加）
Redis 不可用时拒绝服务（返回"助手暂不可用"），宁可助手停用也不放开成本上限。
"""

from __future__ import annotations

import time
from datetime import UTC, datetime

from redis.asyncio import Redis

from .config import Settings


class QuotaExceeded(Exception):
    def __init__(self, reason: str, retry_after: int):
        super().__init__(reason)
        self.reason = reason
        self.retry_after = retry_after


def _seconds_to_midnight_utc(now: float) -> int:
    return int(86400 - now % 86400)


class Guard:
    def __init__(self, redis: Redis, settings: Settings):
        self._redis = redis
        self._settings = settings

    @staticmethod
    def _day(now: float) -> str:
        return datetime.fromtimestamp(now, UTC).strftime("%Y%m%d")

    async def check(self, user_id: str) -> None:
        now = time.time()
        minute_key = f"agent:rl:{user_id}:{int(now // 60)}"
        day = self._day(now)
        request_key = f"agent:quota:req:{user_id}:{day}"
        token_key = f"agent:quota:tok:{user_id}:{day}"
        async with self._redis.pipeline(transaction=False) as pipe:
            pipe.incr(minute_key)
            pipe.expire(minute_key, 120)
            pipe.incr(request_key)
            pipe.expire(request_key, 2 * 86400)
            pipe.get(token_key)
            per_minute, _, per_day, _, tokens = await pipe.execute()
        if per_minute > self._settings.rate_limit_per_minute:
            raise QuotaExceeded("rate_limited", retry_after=int(60 - now % 60) or 1)
        if per_day > self._settings.daily_request_quota:
            raise QuotaExceeded("daily_request_quota", retry_after=_seconds_to_midnight_utc(now))
        if int(tokens or 0) >= self._settings.daily_token_quota:
            raise QuotaExceeded("daily_token_quota", retry_after=_seconds_to_midnight_utc(now))

    async def add_tokens(self, user_id: str, tokens: int) -> None:
        if tokens <= 0:
            return
        key = f"agent:quota:tok:{user_id}:{self._day(time.time())}"
        async with self._redis.pipeline(transaction=False) as pipe:
            pipe.incrby(key, tokens)
            pipe.expire(key, 2 * 86400)
            await pipe.execute()
