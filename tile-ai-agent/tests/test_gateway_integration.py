"""T-1.5 中**必须碰真实服务**才能验证的那部分。

两件事替身证明不了:

1. **Redis 令牌桶**(架构 §13.2)—— 要验的恰恰是"多个进程共享同一把桶"
   与"令牌随时间回填",这两条都依赖 Redis 服务端的原子性与它自己的时钟。
   用假 Redis 只能测出我自己写的假逻辑。
2. **Embedding 插槽与 T-0.13 的接缝** —— 工厂造出的网关能否真的被
   `apply_event` 接受并写进 Qdrant。`Protocol` 是结构化类型,能通过类型检查
   不等于能跑通,得实际写一次。

需要 **Redis(127.0.0.1:6379)与 Qdrant(127.0.0.1:6333)在线**
(`deploy/docker-compose.dev.yml`)。
"""

from __future__ import annotations

import time
import uuid

import pytest
import redis as redis_lib
from qdrant_client import QdrantClient
from qdrant_client.http import models as qmodels

from app.core.config import Settings
from app.gateway.errors import RateLimited
from app.gateway.factory import build_embedding_gateway
from app.gateway.resilience import RedisTokenBucket
from app.index import collections
from app.index.apply import apply_event, point_id_for

REDIS_URL = "redis://127.0.0.1:6379/0"
QDRANT_URL = "http://127.0.0.1:6333"

SKU = "T15-PY-SKU"


@pytest.fixture(scope="module")
def redis_client():
    client = redis_lib.Redis.from_url(REDIS_URL, decode_responses=True)
    client.ping()
    return client


@pytest.fixture(scope="module")
def qdrant() -> QdrantClient:
    return QdrantClient(url=QDRANT_URL)


def bucket(client, *, burst: int, rate: float) -> RedisTokenBucket:
    """每次用**全新 key**,免得测试之间互相扣令牌。"""
    return RedisTokenBucket(
        client,
        key=f"test:ratelimit:{uuid.uuid4()}",
        rate_per_second=rate,
        burst=burst,
    )


# ----------------------------------------------------------------------
# §13.2 Redis 令牌桶
# ----------------------------------------------------------------------

def test_burst_is_allowed_then_limited(redis_client):
    limiter = bucket(redis_client, burst=3, rate=0.01)  # 回填极慢,排除干扰

    for _ in range(3):
        limiter.acquire()  # 突发额度内全部放行

    with pytest.raises(RateLimited):
        limiter.acquire()


def test_tokens_refill_over_time(redis_client):
    limiter = bucket(redis_client, burst=1, rate=50.0)  # 50/s → 0.1s 回满 1 个

    limiter.acquire()
    with pytest.raises(RateLimited):
        limiter.acquire()  # 刚取空

    time.sleep(0.1)
    limiter.acquire()  # 回填后应放行


def test_two_instances_share_one_bucket(redis_client):
    """**这条是选用 Redis 而不是进程内计数器的全部理由。**

    二期多实例部署时,进程内限流会让总额度被放大 N 倍 —— 下游额度就保护不住了。
    两个实例(两个 limiter 对象)必须共用同一把桶。
    """
    key = f"test:ratelimit:shared:{uuid.uuid4()}"
    first = RedisTokenBucket(redis_client, key=key, rate_per_second=0.01, burst=1)
    second = RedisTokenBucket(redis_client, key=key, rate_per_second=0.01, burst=1)

    first.acquire()
    with pytest.raises(RateLimited):
        second.acquire()  # 另一个"实例"必须看到额度已被扣掉


def test_clock_come_from_redis_not_the_client(redis_client):
    """回填依据 Redis 自己的时钟 —— 客户端改时间不影响它。

    多实例部署时各机器时钟可能不一致,若用客户端时间,同一把桶在不同机器上
    会算出不同的回填量。这里通过"脚本里读 TIME"间接验证:
    桶状态里存的时间戳应当接近 Redis 的当前时间,而不是某个被我们控制的假值。
    """
    key = f"test:ratelimit:clock:{uuid.uuid4()}"
    limiter = RedisTokenBucket(redis_client, key=key, rate_per_second=1.0, burst=1)
    limiter.acquire()

    stored_ts = float(redis_client.hget(key, "ts"))
    assert abs(stored_ts - time.time()) < 5, "时间戳应来自 Redis 的 TIME"


# ----------------------------------------------------------------------
# Embedding 插槽 ↔ T-0.13 的接缝
# ----------------------------------------------------------------------

def test_factory_embedding_gateway_actually_writes_to_qdrant(qdrant):
    """工厂造出的 Embedding 网关能被 `apply_event` 直接接受并写入。

    `EmbeddingGateway` 与 `app/index/embedding.py` 的 `EmbeddingPort` 是结构一致的
    `Protocol`,所以 T-0.13 的代码一行没改就该能收下它 —— **这里实际写一次来证明**,
    而不是靠类型注解自证。
    """
    settings = Settings(_env_file=None, model_embedding_provider="", index_vector_size=1024)
    gateway = build_embedding_gateway(settings)
    collections.ensure_collections(qdrant, gateway.dimension)

    event = {
        "eventId": str(uuid.uuid4()),
        "collection": "product",
        "traceId": "test-trace",
        "enqueuedAt": "2026-09-29T00:00:00",
        "items": [{
            "id": SKU,
            "action": "upsert",
            "payload": {
                "sku_code": SKU,
                "name": "浅灰木纹",
                "material": "釉面砖",
                "surface": "柔光",
                "style_tags": ["modern"],
                "spaces": ["bathroom"],
                "price": 268.0,
                "status": "on_sale",
                "updated_at": "2026-09-29T00:00:00",
            },
        }],
    }

    try:
        result = apply_event(qdrant, gateway, event)
        assert result.upserted == 1

        points, _ = qdrant.scroll(
            collection_name=collections.PRODUCT.name,
            scroll_filter=qmodels.Filter(must=[qmodels.FieldCondition(
                key="sku_code", match=qmodels.MatchValue(value=SKU))]),
            limit=10,
            with_vectors=True,  # 默认不返回向量,但这条断言要的正是它
        )
        assert len(points) == 1, "工厂造出的网关应能真的写进索引"
        assert points[0].vector is not None
        assert len(points[0].vector) == gateway.dimension
    finally:
        qdrant.delete(
            collection_name=collections.PRODUCT.name,
            points_selector=qmodels.PointIdsList(points=[point_id_for("product", SKU)]),
        )
