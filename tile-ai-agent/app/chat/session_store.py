"""会话短期上下文(架构 §4.2)。

架构原文:会话上下文**短期存 Redis(sessionId 键,TTL 约 30 分钟)**,长期消息由
Java 落 `t_chat_message`;**智能体无状态**。所以这里只保存"最近几轮"供模型接着聊,
不承担任何权威职责 —— Redis 丢了,这一轮退化成"只看到当前这一句",
**不报错**(§13.3 降级不阻断主流程)。这正是"短期上下文"的应有语义,不是故障。
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any, Protocol

logger = logging.getLogger(__name__)

#: 30 分钟(架构 §4.2 明文)。
DEFAULT_TTL_SECONDS = 1800
#: 只留最近 12 条(约 6 轮)。上下文无限增长既费 token 又会把早期的
#: 无关话题拖进当前回答,限制条数是刻意取舍。
DEFAULT_MAX_TURNS = 12

KEY_PREFIX = "chat:ctx:"


@dataclass(frozen=True)
class Turn:
    """一轮历史消息。`role` 取 user / assistant。"""

    role: str
    content: str


class ChatContextStore(Protocol):
    """短期上下文存储。测试里注入实现即可,不必真连 Redis。"""

    def load(self, session_id: str) -> list[Turn]:
        """取历史(最旧在前)。取不到/读失败一律返回空表,不抛。"""
        ...

    def append(self, session_id: str, role: str, content: str) -> None:
        """追加一轮。写失败只告警,不抛。"""
        ...


class NullChatContextStore:
    """空实现 —— Redis 不可用时的降级,或测试里的显式替身。"""

    def load(self, session_id: str) -> list[Turn]:
        return []

    def append(self, session_id: str, role: str, content: str) -> None:
        return None


class RedisChatContextStore:
    """Redis 实现:一个 list 存 JSON 行,`LTRIM` 限长,`EXPIRE` 续期。

    `client is None` 表示 Redis 不可用(工厂连不上时会给 None)——
    此时退化成空实现,而不是让每一次问答都炸在连接错误上。
    """

    def __init__(
        self,
        client: Any,
        *,
        ttl_seconds: int = DEFAULT_TTL_SECONDS,
        max_turns: int = DEFAULT_MAX_TURNS,
    ) -> None:
        self._client = client
        self._ttl = ttl_seconds
        self._max_turns = max_turns

    def load(self, session_id: str) -> list[Turn]:
        if self._client is None or not session_id:
            return []
        try:
            raw = self._client.lrange(self._key(session_id), 0, -1)
        except Exception as error:  # noqa: BLE001 —— 降级不阻断
            logger.warning("读取会话上下文失败 session=%s:%s", session_id, type(error).__name__)
            return []
        return [turn for turn in (_decode(item) for item in raw) if turn is not None]

    def append(self, session_id: str, role: str, content: str) -> None:
        if self._client is None or not session_id:
            return
        try:
            key = self._key(session_id)
            payload = json.dumps({"role": role, "content": content}, ensure_ascii=False)
            # 一次往返完成"追加 + 限长 + 续期",用 pipeline 而不是四条独立命令。
            pipe = self._client.pipeline()
            pipe.rpush(key, payload)
            pipe.ltrim(key, -self._max_turns, -1)
            pipe.expire(key, self._ttl)
            pipe.execute()
        except Exception as error:  # noqa: BLE001 —— 降级不阻断
            logger.warning("写入会话上下文失败 session=%s:%s", session_id, type(error).__name__)

    @staticmethod
    def _key(session_id: str) -> str:
        return f"{KEY_PREFIX}{session_id}"


def _decode(raw: Any) -> Turn | None:
    """一行 JSON → Turn。坏行跳过(旧版本格式、被写坏的数据),不整体失败。"""
    try:
        obj = json.loads(raw)
        return Turn(role=str(obj["role"]), content=str(obj["content"]))
    except Exception:  # noqa: BLE001 —— 单条坏数据不该拖垮整段历史
        logger.warning("会话上下文存在无法解析的一条,已跳过")
        return None
