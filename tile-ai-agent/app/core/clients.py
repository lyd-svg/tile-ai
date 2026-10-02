"""共享客户端的**惰性单例**(T-1.6)。

为什么必须缓存、而不是每次请求新建:

- **LLM 网关**内含熔断器、失败率统计、令牌桶与 `httpx.Client` 连接池。
  每次请求新建一个 ⇒ 熔断**永远打不开**(每次都是全新的、零失败的实例)、
  失败率的样本永远只有一个,还白搭一次 Redis `ping`。
- **Qdrant / Redis / Java 取数客户端**是长连接,复用才有意义。

为什么**不放进 `lifespan` 硬启动**:
本地常常没填模型密钥。若在启动时装配,配错了会让**进程起不来、`/health` 也死**
—— 与 `main.py` 里生成消费端的处置完全一致("启动失败只告警,别把 /health 一起打掉")。
所以这里把装配推迟到**第一次真正调用**,配置缺失时由调用方转成 error 帧。
"""

from __future__ import annotations

import logging
import threading
from typing import Any

from app.clients.java_api import JavaApiClient
from app.core.config import settings

logger = logging.getLogger(__name__)

# ⚠️ **必须用可重入锁(RLock)**。`get_llm_gateway()` 会在持锁期间调用
# `get_redis_client()`,而后者要拿同一把锁 —— 用普通 `Lock` 会**自己把自己锁死**
# (2026-10-02 真实联调踩到:整个对话请求永久挂起,且没有任何日志)。
# 下面还额外做了"先取 redis 再进锁"的调整,两道保险。
_lock = threading.RLock()

_redis: Any = None
_redis_ready = False
_qdrant: Any = None
_java_api: JavaApiClient | None = None
_llm: Any = None
_embedding: Any = None


def get_redis_client() -> Any | None:
    """共享 Redis 客户端。连不上返回 None(调用方降级),不抛。"""
    global _redis, _redis_ready
    with _lock:
        if not _redis_ready:
            _redis = _connect_redis()
            _redis_ready = True
        return _redis


def get_qdrant_client() -> Any:
    """共享 Qdrant 客户端(派生索引,不变量 4)。"""
    global _qdrant
    with _lock:
        if _qdrant is None:
            from qdrant_client import QdrantClient

            api_key = settings.qdrant_api_key.get_secret_value()
            _qdrant = QdrantClient(url=settings.qdrant_url, api_key=api_key or None)
        return _qdrant


def get_java_api_client() -> JavaApiClient:
    """共享 Java 取数客户端(SKU 校验、陈设提示词)。"""
    global _java_api
    with _lock:
        if _java_api is None:
            _java_api = JavaApiClient()
        return _java_api


def get_llm_gateway() -> Any:
    """共享 LLM 网关。**首次调用才装配** —— 配置缺失会抛,由调用方转 error 帧。

    ⚠️ 取 redis 客户端的调用放在**进锁之前**:`get_redis_client()` 自己也要拿锁,
    在持锁时再调它就是"同一把锁套自己"(普通锁会死锁,RLock 能过但仍是坏味道)。
    """
    global _llm
    if _llm is not None:
        return _llm

    redis_client = get_redis_client()
    with _lock:
        if _llm is None:
            from app.gateway.factory import build_llm_gateway

            _llm = build_llm_gateway(redis_client=redis_client)
        return _llm


def get_embedding_gateway() -> Any:
    """共享 Embedding 网关(当前是确定性占位实现,见 factory 的说明)。"""
    global _embedding
    with _lock:
        if _embedding is None:
            from app.gateway.factory import build_embedding_gateway

            _embedding = build_embedding_gateway()
        return _embedding


def reset_clients() -> None:
    """清空单例 —— **仅供测试**,让每个用例从干净状态开始。"""
    global _redis, _redis_ready, _qdrant, _java_api, _llm, _embedding
    with _lock:
        _redis = None
        _redis_ready = False
        _qdrant = None
        _java_api = None
        _llm = None
        _embedding = None


def _connect_redis() -> Any | None:
    """连 Redis。连不上返回 None(由调用方决定降级),不抛。

    延迟 import:没有 Redis 的环境(或只跑纯单测的环境)不该因为
    import 本模块就失败。
    """
    try:
        import redis

        client = redis.Redis.from_url(
            settings.redis_url, socket_connect_timeout=2, socket_timeout=2, decode_responses=True
        )
        client.ping()
        return client
    except Exception as error:  # noqa: BLE001 —— 连不上就是连不上,原因不重要
        logger.warning("连接 Redis 失败(%s):%s", settings.redis_url, type(error).__name__)
        return None
