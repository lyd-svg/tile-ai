"""T-1.6:会话短期上下文(Redis,TTL 30min,架构 §4.2)。

**降级不阻断**是这里最要紧的一条:Redis 挂了不该让问答跟着挂 —— 只是退化成单轮。
"""

from __future__ import annotations

from app.chat.session_store import (
    DEFAULT_MAX_TURNS,
    DEFAULT_TTL_SECONDS,
    KEY_PREFIX,
    NullChatContextStore,
    RedisChatContextStore,
)


class FakeRedis:
    """够用的内存替身:list 命令 + pipeline。"""

    def __init__(self) -> None:
        self.data: dict[str, list[str]] = {}
        self.expires: dict[str, int] = {}

    def rpush(self, key, value):
        self.data.setdefault(key, []).append(value)
        return len(self.data[key])

    def ltrim(self, key, start, end):
        items = self.data.get(key, [])
        n = len(items)
        s = start if start >= 0 else max(n + start, 0)
        e = end if end >= 0 else n + end
        self.data[key] = items[s:e + 1] if e >= s else []
        return True

    def expire(self, key, ttl):
        self.expires[key] = ttl
        return True

    def lrange(self, key, start, end):
        return list(self.data.get(key, []))

    def pipeline(self):
        return _FakePipeline(self)


class _FakePipeline:
    def __init__(self, redis: FakeRedis) -> None:
        self._redis = redis
        self._ops: list[tuple[str, tuple]] = []

    def rpush(self, *args):
        self._ops.append(("rpush", args))
        return self

    def ltrim(self, *args):
        self._ops.append(("ltrim", args))
        return self

    def expire(self, *args):
        self._ops.append(("expire", args))
        return self

    def execute(self):
        for name, args in self._ops:
            getattr(self._redis, name)(*args)
        self._ops = []


class BrokenRedis:
    def lrange(self, *args, **kwargs):
        raise RuntimeError("redis down")

    def pipeline(self):
        raise RuntimeError("redis down")


def test_append_then_load_roundtrip():
    store = RedisChatContextStore(FakeRedis())

    store.append("s_1", "user", "卫生间配什么墙砖")
    store.append("s_1", "assistant", "推荐雪瓷白")

    turns = store.load("s_1")
    assert [(t.role, t.content) for t in turns] == [
        ("user", "卫生间配什么墙砖"),
        ("assistant", "推荐雪瓷白"),
    ]


def test_sessions_are_isolated():
    store = RedisChatContextStore(FakeRedis())

    store.append("s_1", "user", "第一句")
    store.append("s_2", "user", "第二句")

    assert [t.content for t in store.load("s_1")] == ["第一句"]
    assert [t.content for t in store.load("s_2")] == ["第二句"]


def test_ttl_is_set_on_write():
    redis = FakeRedis()
    store = RedisChatContextStore(redis)

    store.append("s_1", "user", "x")

    assert redis.expires[f"{KEY_PREFIX}s_1"] == DEFAULT_TTL_SECONDS == 1800


def test_history_is_trimmed_to_max_turns():
    """上下文只留最近若干条 —— 无限增长既费 token 又会把早期话题拖进来。"""
    store = RedisChatContextStore(FakeRedis(), max_turns=2)

    for i in range(5):
        store.append("s_1", "user", f"第{i}句")

    assert [t.content for t in store.load("s_1")] == ["第3句", "第4句"]
    assert DEFAULT_MAX_TURNS >= 2


def test_broken_redis_degrades_instead_of_raising():
    """**Redis 挂了不抛异常** —— 读回空表、写忽略,问答照常(§13.3)。"""
    store = RedisChatContextStore(BrokenRedis())

    assert store.load("s_1") == []
    store.append("s_1", "user", "x")  # 不抛即通过


def test_none_client_degrades():
    store = RedisChatContextStore(None)

    assert store.load("s_1") == []
    store.append("s_1", "user", "x")


def test_corrupt_row_is_skipped_not_fatal():
    redis = FakeRedis()
    redis.data[f"{KEY_PREFIX}s_1"] = ["not-json", '{"role":"user","content":"好的"}']
    store = RedisChatContextStore(redis)

    turns = store.load("s_1")

    assert [t.content for t in turns] == ["好的"], "坏行跳过,不整体失败"


def test_null_store_is_a_noop():
    store = NullChatContextStore()

    assert store.load("s_1") == []
    store.append("s_1", "user", "x")
