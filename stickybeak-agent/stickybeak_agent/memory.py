"""显式购物偏好；按网关注入的用户 ID 存储，不从聊天中自动抽取个人信息。"""

from __future__ import annotations

import time
from contextlib import AsyncExitStack
from typing import Protocol

import aiomysql
from pydantic import BaseModel, ConfigDict, Field

from .config import Settings
from .graph.tools import CATEGORIES


class Preferences(BaseModel):
    model_config = ConfigDict(extra="forbid")
    category: CATEGORIES | None = None
    max_price_aud: float | None = Field(default=None, gt=0, le=10000, allow_inf_nan=False)


class MemoryRecord(BaseModel):
    preferences: Preferences
    updated_at: int
    expires_at: int


class PreferenceStore(Protocol):
    async def get(self, user_id: str) -> MemoryRecord | None: ...
    async def put(self, user_id: str, preferences: Preferences) -> MemoryRecord: ...
    async def delete(self, user_id: str) -> None: ...


class InMemoryPreferences:
    """仅用于无数据库开发和测试；生产使用 MySQL。"""

    def __init__(self, ttl_days: int = 90):
        self.ttl_seconds = ttl_days * 86400
        self.rows: dict[str, MemoryRecord] = {}

    async def get(self, user_id: str) -> MemoryRecord | None:
        row = self.rows.get(user_id)
        if row and row.expires_at > time.time():
            return row.model_copy(deep=True)
        return None

    async def put(self, user_id: str, preferences: Preferences) -> MemoryRecord:
        now = int(time.time())
        row = MemoryRecord(preferences=preferences, updated_at=now, expires_at=now + self.ttl_seconds)
        self.rows[user_id] = row.model_copy(deep=True)
        return row

    async def delete(self, user_id: str) -> None:
        self.rows.pop(user_id, None)


class MySQLPreferences:
    def __init__(self, pool, ttl_days: int):
        self.pool = pool
        self.ttl_seconds = ttl_days * 86400

    async def get(self, user_id: str) -> MemoryRecord | None:
        async with self.pool.acquire() as conn, conn.cursor() as cur:
            await cur.execute(
                "SELECT body FROM user_preferences WHERE user_id=%s AND expires_at>%s", (user_id, int(time.time()))
            )
            row = await cur.fetchone()
        return MemoryRecord.model_validate_json(row[0]) if row else None

    async def put(self, user_id: str, preferences: Preferences) -> MemoryRecord:
        now = int(time.time())
        row = MemoryRecord(preferences=preferences, updated_at=now, expires_at=now + self.ttl_seconds)
        async with self.pool.acquire() as conn, conn.cursor() as cur:
            await cur.execute(
                "INSERT INTO user_preferences(user_id,body,expires_at) VALUES(%s,%s,%s) "
                "ON DUPLICATE KEY UPDATE body=VALUES(body),expires_at=VALUES(expires_at)",
                (user_id, row.model_dump_json(), row.expires_at),
            )
        return row

    async def delete(self, user_id: str) -> None:
        async with self.pool.acquire() as conn, conn.cursor() as cur:
            await cur.execute("DELETE FROM user_preferences WHERE user_id=%s", (user_id,))


async def open_preferences(settings: Settings, stack: AsyncExitStack) -> PreferenceStore:
    if settings.checkpoint_backend == "memory":
        return InMemoryPreferences(settings.memory_ttl_days)
    pool = await aiomysql.create_pool(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        db=settings.agent_db,
        autocommit=True,
        minsize=1,
        maxsize=3,
    )

    async def close():
        pool.close()
        await pool.wait_closed()

    stack.push_async_callback(close)
    async with pool.acquire() as conn, conn.cursor() as cur:
        await cur.execute(
            "CREATE TABLE IF NOT EXISTS user_preferences ("
            "user_id VARCHAR(32) PRIMARY KEY, body JSON NOT NULL, expires_at BIGINT NOT NULL)"
        )
        # 过期数据不参与读取；每次启动顺便物理清理。
        await cur.execute("DELETE FROM user_preferences WHERE expires_at<=%s", (int(time.time()),))
    return MySQLPreferences(pool, settings.memory_ttl_days)
