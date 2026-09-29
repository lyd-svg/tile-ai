"""`queue.index.sync` 消费者(架构 §6.2、§6.4)。

Java 侧在 SKU/搭配变更后经 **Outbox → MQ** 投递事件,本消费者把它们落到 Qdrant。

**可靠性**(§6.4):
  - 手动 ACK —— 只有处理成功才 ack,进程崩在中途消息不会丢;
  - 幂等 —— 事件的应用是 upsert/delete 语义,重复消费结果相同;
  - 有界重试 —— 失败带计数重投,超过阈值 nack 到死信队列(由 Java 声明拓扑时配置的 DLX 完成);
  - 兜底 —— 长期漂移由「全量重建」纠正(§15.2)。
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any

import pika
from qdrant_client import QdrantClient

from app.core.config import settings
from app.index.apply import ApplyResult, apply_event
from app.index.embedding import EmbeddingPort

logger = logging.getLogger(__name__)

QUEUE_INDEX_SYNC = "queue.index.sync"
EXCHANGE = "tile.topic"

# 重试次数上限。超过就 nack 到 DLQ —— 无界重投会让一条坏消息永远占着队列。
MAX_RETRY = 5

# 重投前等待,给下游恢复的时间(粗略退避;精确的指数退避属运维增强)。
RETRY_DELAY_SECONDS = 2.0

RETRY_HEADER = "x-index-sync-retry"


class IndexSyncConsumer:
    """消费 `queue.index.sync` 并更新 Qdrant。"""

    def __init__(self, qdrant: QdrantClient, embedding: EmbeddingPort) -> None:
        self._qdrant = qdrant
        self._embedding = embedding

    def run_forever(self) -> None:
        """阻塞消费。

        **不声明拓扑** —— 队列、交换机、DLX 由 Java 侧(`RabbitTopologyConfig`)声明。
        两边都声明很容易因为参数不一致而报 PRECONDITION_FAILED(比如一边带 DLX
        参数、一边没带),那类错误只在启动时才暴露,且提示很难看懂。
        队列不存在时这里会直接报错,是清晰的失败。
        """
        while True:
            try:
                self._consume()
            except pika.exceptions.AMQPError:
                logger.exception("与 RabbitMQ 的连接中断,%.1f 秒后重连", RETRY_DELAY_SECONDS)
                time.sleep(RETRY_DELAY_SECONDS)

    def _consume(self) -> None:
        parameters = pika.URLParameters(settings.rabbitmq_url)
        connection = pika.BlockingConnection(parameters)
        try:
            channel = connection.channel()
            # 逐个取,避免预取太多导致某条失败时一整批都要重投
            channel.basic_qos(prefetch_count=1)
            channel.basic_consume(queue=QUEUE_INDEX_SYNC, on_message_callback=self._on_message)
            logger.info("索引同步消费者已启动,监听 %s", QUEUE_INDEX_SYNC)
            channel.start_consuming()
        finally:
            if connection.is_open:
                connection.close()

    def _on_message(self, channel: Any, method: Any, properties: Any, body: bytes) -> None:
        try:
            event = json.loads(body)
            result = apply_event(self._qdrant, self._embedding, event)
            # 处理成功才 ACK —— 这意味着"索引真的更新了",而不是"消息收到了"
            channel.basic_ack(delivery_tag=method.delivery_tag)
            logger.info(
                "索引同步完成 eventId=%s collection=%s upsert=%d delete=%d",
                event.get("eventId"), event.get("collection"),
                result.upserted, result.deleted,
            )
        except Exception:  # noqa: BLE001 —— 任何异常都不能让消费者线程死掉
            retry = int((properties.headers or {}).get(RETRY_HEADER, 0))
            logger.exception("索引同步失败(第 %d 次)eventId=%s", retry + 1, _event_id_of(body))
            self._retry_or_dead_letter(channel, method, properties, body, retry)

    def _retry_or_dead_letter(self, channel: Any, method: Any, properties: Any,
                              body: bytes, retry: int) -> None:
        """失败处理:未超阈值则带计数重投,超了则 nack 进死信队列。"""
        if retry >= MAX_RETRY:
            # requeue=False → 由 Java 声明队列时配置的 DLX 转投到 queue.index.sync.dlq。
            # DLQ 没有自动消费方,由人工或脚本兜底(§6.4)。
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
            logger.error("索引同步重试已达上限,转入死信队列 eventId=%s", _event_id_of(body))
            return

        headers = dict(properties.headers or {})
        headers[RETRY_HEADER] = retry + 1
        time.sleep(RETRY_DELAY_SECONDS)

        # 重投 + ACK 原消息:不这样做的话,requeue=True 会立刻被再次投递,
        # 形成热循环且没有退避的余地。
        channel.basic_publish(
            exchange=EXCHANGE,
            routing_key=QUEUE_INDEX_SYNC,
            body=body,
            properties=pika.BasicProperties(
                content_type=properties.content_type or "application/json",
                delivery_mode=2,  # 持久化,与 Java 声明队列时的口径一致
                headers=headers,
            ),
        )
        channel.basic_ack(delivery_tag=method.delivery_tag)


def _event_id_of(body: bytes) -> str:
    """尽量从消息体里取出 eventId 用于日志;取不到也不该再抛异常。"""
    try:
        return str(json.loads(body).get("eventId", "<unknown>"))
    except Exception:  # noqa: BLE001
        return "<unparsable>"
