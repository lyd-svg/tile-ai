"""T-2.4(提前):`queue.index.sync` 消费者的行为(架构 §6.2、§6.4、§9.2)。

**纯单测,不需要 MySQL / RabbitMQ / Qdrant** —— pika 的 channel 用假对象替掉,
Qdrant 用只实现 `upsert` / `delete` 的假客户端替掉。

## 为什么这个文件此前不存在

`app/index/consumer.py` 从 T-0.13 起就写好了,但**一直没随服务启动**
(`app/main.py` 里那段"故意不启动"的注释),所以从来没有任何测试碰过它 ——
它是一段**从没运行过一次的代码**。2026-10-07 把它接进 `lifespan` 之前,
先把它的行为钉下来(§79)。

## 与 `tests/test_generate.py` 的关系

假 channel 那四个类(`FakeProperties` / `FakeMethod` / `FakeChannel` / `DeadChannel`)
**在那边也有一份**,定义在该文件内部、没有共享。这里**再写一份**而不是抽公共模块:
抽出去要动 `test_generate.py`,而那份文件已经验证通过、有回归价值 ——
"碰已验证模块"的风险大于"重复二十行"。**这是刻意的重复,不是疏忽。**

两者的唯一差别:`FakeChannel` 在索引侧多了个 `prefetch` 记录(见下),
且重试头是 `x-index-sync-retry`(生成侧是 `x-generate-retry`)。
"""

from __future__ import annotations

import json

import pika
import pytest

from app.index import collections
from app.index.consumer import (
    EXCHANGE,
    MAX_RETRY,
    QUEUE_INDEX_SYNC,
    RETRY_HEADER,
    IndexSyncConsumer,
)
from app.index.embedding import DeterministicPlaceholderEmbedding

SKU = "T-NEW-IDXCONS-SKU"


@pytest.fixture(autouse=True)
def _no_real_sleep(monkeypatch):
    """重试路径里有 `time.sleep(RETRY_DELAY_SECONDS)`(默认 2 秒)。

    不绕开的话每个失败用例都要真等 2 秒,而等出来的时间没有任何断言价值。
    **退避本身是行为**(顺序是先睡再重投),所以只把时长换成 0,不动调用点。
    """
    monkeypatch.setattr("time.sleep", lambda _seconds: None)


# ======================================================================
# 假对象
# ======================================================================

class FakeProperties:
    def __init__(self, headers: dict | None = None,
                 content_type: str = "application/json") -> None:
        self.headers = headers or {}
        self.content_type = content_type


class FakeMethod:
    def __init__(self, tag: int = 1) -> None:
        self.delivery_tag = tag


class FakeChannel:
    """记录 ACK / NACK / 重投。"""

    def __init__(self) -> None:
        self.acked: list[int] = []
        self.nacked: list[tuple[int, bool]] = []
        self.published: list[dict] = []

    def basic_ack(self, delivery_tag: int) -> None:
        self.acked.append(delivery_tag)

    def basic_nack(self, delivery_tag: int, requeue: bool) -> None:
        self.nacked.append((delivery_tag, requeue))

    def basic_publish(self, **kwargs) -> None:
        self.published.append(kwargs)


class DeadChannel(FakeChannel):
    """连接已断:任何 AMQP 动作都抛 `ChannelWrongStateError`(同生成侧的假对象)。"""

    def basic_publish(self, **kwargs) -> None:
        raise pika.exceptions.ChannelWrongStateError("Channel is closed.")

    def basic_ack(self, delivery_tag: int) -> None:
        raise pika.exceptions.ChannelWrongStateError("Channel is closed.")

    def basic_nack(self, delivery_tag: int, requeue: bool) -> None:
        raise pika.exceptions.ChannelWrongStateError("Channel is closed.")


class FakeQdrant:
    """只实现 `apply_event` 真正用到的两个方法。

    用假客户端而不是给 `apply_event` 打桩 —— 这样**真实的应用逻辑**(点 id 映射、
    幂等语义、payload → 嵌入文本)全都被走到,测的才是那条真链路。
    """

    def __init__(self, error: Exception | None = None) -> None:
        self.upserted: list[tuple[str, list]] = []
        self.deleted: list[tuple[str, object]] = []
        self._error = error

    def upsert(self, *, collection_name: str, points: list) -> None:
        if self._error is not None:
            raise self._error
        self.upserted.append((collection_name, points))

    def delete(self, *, collection_name: str, points_selector: object) -> None:
        if self._error is not None:
            raise self._error
        self.deleted.append((collection_name, points_selector))


def _embedding() -> DeterministicPlaceholderEmbedding:
    """真嵌入实现(确定性占位)。**不加载任何模型、不打网络。**"""
    return DeterministicPlaceholderEmbedding(collections.DEFAULT_VECTOR_SIZE)


def consumer_with(*, qdrant: FakeQdrant | None = None,
                  embedding: object | None = None) -> IndexSyncConsumer:
    return IndexSyncConsumer(
        qdrant=qdrant if qdrant is not None else FakeQdrant(),
        embedding=embedding if embedding is not None else _embedding(),
    )


def _event(action: str = "upsert", collection: str = "product",
           business_id: str = SKU) -> dict:
    return {
        "eventId": "evt-1",
        "collection": collection,
        "traceId": "trace-1",
        "enqueuedAt": "2026-10-07T00:00:00",
        "items": [{
            "id": business_id,
            "action": action,
            "payload": {
                "sku_code": business_id,
                "name": "探针砖",
                "material": "釉面砖",
                "spec": "750×1500mm",
                "color_pattern": "仿石",
                "surface": "柔光",
                "style_tags": ["modern"],
                "spaces": ["bathroom"],
                "price": 268.0,
                "status": "on_sale",
                "updated_at": "2026-10-07T00:00:00",
            },
        }],
    }


def _body(event: dict | None = None) -> bytes:
    return json.dumps(event if event is not None else _event()).encode()


# ======================================================================
# 正常路径
# ======================================================================

def test_success_acks_and_writes_to_qdrant() -> None:
    """处理**真的落进 Qdrant** 才 ACK —— "消息收到了"不等于"索引更新了"。"""
    qdrant = FakeQdrant()
    channel = FakeChannel()

    consumer_with(qdrant=qdrant)._on_message(
        channel, FakeMethod(tag=7), FakeProperties(), _body(),
    )

    assert channel.acked == [7]
    assert channel.nacked == []
    assert channel.published == []
    assert len(qdrant.upserted) == 1, "成功处理必须真的写了一次"
    assert qdrant.upserted[0][0] == collections.PRODUCT.name


def test_delete_event_removes_from_qdrant() -> None:
    """delete 事件走删除路径,同样 ACK。"""
    qdrant = FakeQdrant()
    channel = FakeChannel()

    consumer_with(qdrant=qdrant)._on_message(
        channel, FakeMethod(tag=9), FakeProperties(), _body(_event(action="delete")),
    )

    assert channel.acked == [9]
    assert len(qdrant.deleted) == 1
    assert qdrant.upserted == []


def test_redelivery_of_the_same_event_is_safe() -> None:
    """**契约要求以 `eventId` 去重**(asyncapi「重复投递不得产生副作用」)。

    实现里没有去重表 —— 因为 upsert/delete **本身就是幂等的**,重复应用结果相同。
    这条把那个"更进一步"的理由钉住:故意投两次,断言最终只有一个点。
    """
    qdrant = FakeQdrant()
    consumer = consumer_with(qdrant=qdrant)
    body = _body()

    consumer._on_message(FakeChannel(), FakeMethod(tag=1), FakeProperties(), body)
    consumer._on_message(FakeChannel(), FakeMethod(tag=2), FakeProperties(), body)

    assert len(qdrant.upserted) == 2, "两次都写了 —— 幂等不是靠「不写」实现的"
    ids = {point.id for _, points in qdrant.upserted for point in points}
    assert len(ids) == 1, "但两次落在**同一个点 id** 上,重复投递不会产生第二个点"


# ======================================================================
# 失败 → 重投 → 死信
# ======================================================================

def test_failure_republishes_with_incremented_retry_header() -> None:
    """失败重投:带计数、且**重投后 ACK 原消息**。

    不 ACK 原消息而只重投,会让同一条被投两次(重投的 + 没 ACK 的);
    而 `requeue=True` 又会立刻再投一次,形成没有退避的热循环。
    """
    channel = FakeChannel()
    body = _body()

    consumer_with(qdrant=FakeQdrant(error=RuntimeError("炸")))._on_message(
        channel, FakeMethod(tag=1), FakeProperties(headers={RETRY_HEADER: 0}), body,
    )

    assert len(channel.published) == 1
    assert channel.published[0]["properties"].headers[RETRY_HEADER] == 1
    assert channel.published[0]["exchange"] == EXCHANGE
    assert channel.published[0]["routing_key"] == QUEUE_INDEX_SYNC
    assert channel.acked == [1], "重投之后必须 ACK 原消息"
    assert channel.nacked == []


def test_retry_header_counts_up_towards_the_limit() -> None:
    """已重试 4 次再失败 ⇒ 头变成 5(**上限边界**,还没到 `nack`)。"""
    channel = FakeChannel()

    consumer_with(qdrant=FakeQdrant(error=RuntimeError("炸")))._on_message(
        channel, FakeMethod(tag=2),
        FakeProperties(headers={RETRY_HEADER: MAX_RETRY - 1}),
        _body(),
    )

    assert channel.published[0]["properties"].headers[RETRY_HEADER] == MAX_RETRY
    assert channel.nacked == [], "到上限之前不该进死信队列"


def test_dead_letters_after_retry_limit() -> None:
    """到上限 ⇒ `nack(requeue=False)`,由 Java 声明的 DLX 转投死信队列。

    **不重投、也不抛** —— 一条坏消息不能永远占着队列。
    """
    channel = FakeChannel()

    consumer_with(qdrant=FakeQdrant(error=RuntimeError("炸")))._on_message(
        channel, FakeMethod(tag=3),
        FakeProperties(headers={RETRY_HEADER: MAX_RETRY}),
        _body(),
    )

    assert channel.nacked == [(3, False)], "requeue=False ⇒ 交给 DLX 转死信队列"
    assert channel.published == []
    assert channel.acked == []


# ======================================================================
# 坏消息不能让消费者线程死掉(§62 的同一课)
# ======================================================================

def test_unknown_collection_goes_down_the_retry_path() -> None:
    """契约 enum 外的集合名 ⇒ 记下来并重投,**不静默丢弃、不抛**。"""
    channel = FakeChannel()

    consumer_with()._on_message(
        channel, FakeMethod(tag=4), FakeProperties(headers={RETRY_HEADER: 0}),
        _body(_event(collection="nope")),
    )

    assert len(channel.published) == 1
    assert channel.nacked == []


def test_unparsable_body_is_retried_not_fatal() -> None:
    """消息体本身不是 JSON ⇒ 走重试路径(最终进死信),但**不能抛**。"""
    channel = FakeChannel()

    consumer_with()._on_message(
        channel, FakeMethod(tag=5), FakeProperties(headers={RETRY_HEADER: 0}),
        b"{ not json",
    )

    assert len(channel.published) == 1
    assert channel.nacked == []


def test_survives_republish_on_a_dead_channel() -> None:
    """**回归(2026-10-03,§62)**:连接断了之后重投会失败,而那**不能**带走消费者线程。

    正确的行为是**什么也别做** —— 消息没 ACK,连接恢复后 broker 会重投它。
    抛出去就等于消费者死了,而 daemon 线程的死亡**外面一点征兆都没有**
    (进程活着、`/health` 照样 200)。
    """
    consumer = consumer_with(qdrant=FakeQdrant(error=RuntimeError("炸")))

    # 这里**不能抛** —— 抛了就等于消费者死了
    consumer._on_message(
        DeadChannel(), FakeMethod(tag=1),
        FakeProperties(headers={RETRY_HEADER: 0}), _body(),
    )


def test_survives_ack_failure_after_success() -> None:
    """写入成功但 ACK 时连接已断 —— 同样不能带走线程。

    消息会被 broker 重投,而应用是幂等的(见上面 `test_redelivery_...`),
    所以最坏只是重复写一次,不会写坏索引。
    """
    consumer_with()._on_message(
        DeadChannel(), FakeMethod(tag=9), FakeProperties(), _body(),
    )


def test_run_forever_keeps_going_after_a_non_amqp_error(monkeypatch) -> None:
    """**非 AMQP 的异常也不能让循环退出**(与生成侧同因,§62)。"""
    consumer = consumer_with()
    rounds: list[int] = []

    def fake_consume() -> None:
        rounds.append(1)
        if len(rounds) == 1:
            raise RuntimeError("一个与 AMQP 无关的异常")
        # 用 BaseException 收尾:`run_forever` 只吃 Exception,故它能穿出去、让循环停下
        raise SystemExit

    monkeypatch.setattr(consumer, "_consume", fake_consume)

    with pytest.raises(SystemExit):
        consumer.run_forever()

    assert len(rounds) == 2, "第一次异常之后必须进入下一轮,而不是退出循环"


def test_consume_asks_for_a_heartbeat(monkeypatch) -> None:
    """连接要显式设心跳(§63)。

    索引侧的处理是**秒级**(本地嵌入 + 一次 Qdrant 写),远不会像出图那样阻塞几分钟 ——
    但两边用同一个配置值、同一套写法,是为了不让"哪一侧要设心跳"变成需要记住的知识。
    """
    from app.core.config import settings

    captured: dict = {}

    class FakeParams:
        heartbeat = None

    monkeypatch.setattr(
        "app.index.consumer.pika.URLParameters", lambda _url: FakeParams()
    )

    def fake_blocking_connection(parameters):
        captured["heartbeat"] = parameters.heartbeat
        raise SystemExit  # 只验参数,不真连

    monkeypatch.setattr(
        "app.index.consumer.pika.BlockingConnection", fake_blocking_connection
    )

    with pytest.raises(SystemExit):
        consumer_with()._consume()

    assert captured["heartbeat"] == settings.rabbitmq_heartbeat_seconds
