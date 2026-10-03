"""`queue.generate` 消费者(架构 §6.2、§6.4;T-1.11)。

结构照抄 `app/index/consumer.py`(pika、手动 ACK、有界重试 + DLQ、**不声明拓扑**),
只换三处:队列名、处理逻辑、以及**进 DLQ 前必须先回调 failed**。

## 与索引消费端的三个差别

**1. 重试上限是 2(共 3 次处理),不是 5。** 一次处理可能等于 4 次真实模型调用
   (4 个视角各一次),而网关层的 `CallGuard` 自己已经重试 3 次了 —— 消费端再来 5 次
   就是三重重复。长周期的重投归 Java 的补偿扫描(§35.1)。

**2. 进 DLQ 前必须先发一次 `failed` 回调。** 索引消息没有"等待中的用户",
   生成任务有:少了这一步,用户要一直轮询到补偿扫描(最长 30 分钟)接管才知道失败。

**3. 有些失败不该重试。** 回调返回 40003(Java 已无此任务)时直接 ACK 丢弃 ——
   重投只会拿一条注定失败的消息把队列堵满。见 `orchestrator.TaskGoneError`。

## 为什么新建 Source 而不复用

`HttpSkuSource` / `HttpFurnishSource` 的缓存是**按任务**的(见 `prompt_sources.py`):
每条消息新建一个,任务结束即随之丢弃。跨消息复用会让缓存无限增长,
而"产品详情改了没有"没有任何依据可循。
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any

import pika

from app.agent.prompt import FurnishSource, SkuSource
from app.agent.prompt_sources import HttpFurnishSource, HttpSkuSource
from app.clients.java_api import JavaApiClient
from app.core.config import settings
from app.gateway.ports import ImageGenGateway
from app.generate.orchestrator import (
    CODE_GENERATE_FAILED,
    TaskGoneError,
    run_generate_task,
)

logger = logging.getLogger(__name__)

QUEUE_GENERATE = "queue.generate"
EXCHANGE = "tile.topic"

#: 重试次数上限。**从索引消费端的 5 降到 2** —— 理由见模块头第 1 条。
MAX_RETRY = 2

#: 重投前等待,给下游恢复的时间(与索引消费端同值,粗略退避)。
RETRY_DELAY_SECONDS = 2.0

RETRY_HEADER = "x-generate-retry"


class GenerateConsumer:
    """消费 `queue.generate` 并把结果回调给 Java。"""

    def __init__(
        self,
        *,
        api_client: JavaApiClient,
        image_gateway: ImageGenGateway,
        sku_source_factory: Any = HttpSkuSource,
        furnish_source_factory: Any = HttpFurnishSource,
    ) -> None:
        self._api = api_client
        self._image_gateway = image_gateway
        # 工厂默认就是两个 HTTP 实现;测试可注入替身(不打网络)。
        self._sku_source_factory = sku_source_factory
        self._furnish_source_factory = furnish_source_factory

    def run_forever(self) -> None:
        """阻塞消费。**不声明拓扑** —— 队列、交换机、DLX 由 Java 声明
        (`RabbitTopologyConfig`),两边都声明很容易因参数不一致报
        `PRECONDITION_FAILED`(同 `app/index/consumer.py` 的理由)。

        ⚠️ **这里必须吃掉任何异常,不能只吃 AMQPError。** 2026-10-03 实测:
        一次连接中断之后,处理路径里又抛了别的异常,消费者线程**当场死掉** ——
        而进程还活着、`/health` 照样 200,于是表现成"服务好好的,但一个任务都不处理"
        (见 `openapi-verification.md` §62)。消费端是**长期跑的循环**,
        它的第一职责是"活着",任何异常都该换来一次重连而不是一次退出。
        """
        while True:
            try:
                self._consume()
            except pika.exceptions.AMQPError:
                logger.exception("与 RabbitMQ 的连接中断,%.1f 秒后重连", RETRY_DELAY_SECONDS)
                time.sleep(RETRY_DELAY_SECONDS)
            except Exception:  # noqa: BLE001 —— 见上面那段:异常不能带走消费者线程
                logger.exception("生成消费者异常退出本轮消费,%.1f 秒后重连", RETRY_DELAY_SECONDS)
                time.sleep(RETRY_DELAY_SECONDS)

    def _consume(self) -> None:
        parameters = pika.URLParameters(settings.rabbitmq_url)
        # ⚠️ 显式设心跳:默认协商出来是 60 秒,而一次模型调用最长可阻塞 600 秒 ——
        # 那期间消费端发不出心跳,broker 会把连接掐掉、消息被重投(§63)。
        parameters.heartbeat = settings.rabbitmq_heartbeat_seconds
        connection = pika.BlockingConnection(parameters)
        try:
            channel = connection.channel()
            # 逐个取:一条消息可能等于 4 次模型调用,预取多了会把额度和内存一起占满
            channel.basic_qos(prefetch_count=1)
            channel.basic_consume(queue=QUEUE_GENERATE, on_message_callback=self._on_message)
            logger.info("生成消费者已启动,监听 %s", QUEUE_GENERATE)
            channel.start_consuming()
        finally:
            if connection.is_open:
                connection.close()

    def _on_message(self, channel: Any, method: Any, properties: Any, body: bytes) -> None:
        try:
            payload = json.loads(body)
            self._handle(payload)
            # 只有真的跑完才 ACK —— "消息收到了"不等于"图出来了"
            channel.basic_ack(delivery_tag=method.delivery_tag)
        except TaskGoneError as error:
            # 任务已经不在 Java 那边。**终局,不重投** —— 重投一万次也还是不在。
            channel.basic_ack(delivery_tag=method.delivery_tag)
            logger.warning("任务已不存在于 Java,丢弃本条消息:%s", error)
        except Exception as error:  # noqa: BLE001 —— 任何异常都不能让消费者线程死掉
            retry = int((properties.headers or {}).get(RETRY_HEADER, 0))
            logger.exception("生成任务失败(第 %d 次)taskId=%s", retry + 1, _task_id_of(body))
            self._retry_or_dead_letter(channel, method, properties, body, retry, error)

    def _handle(self, payload: dict[str, Any]) -> None:
        """取数 + 编排 + 回调。**每条消息新建一套按任务缓存的取数接缝。**"""
        trace_id = str(payload.get("traceId") or "")
        sku_source: SkuSource = self._sku_source_factory(self._api, trace_id=trace_id)
        furnish_source: FurnishSource = self._furnish_source_factory(self._api, trace_id=trace_id)
        run_generate_task(
            payload,
            sku_source=sku_source,
            furnish_source=furnish_source,
            image_gateway=self._image_gateway,
            callback=self._api,
            anchor_first_view=settings.image_anchor_first_view,
        )

    def _retry_or_dead_letter(self, channel: Any, method: Any, properties: Any,
                              body: bytes, retry: int, error: Exception) -> None:
        """失败处理:未超阈值则带计数重投,超了则**先回调 failed** 再 nack 进 DLQ。"""
        if retry >= MAX_RETRY:
            self._notify_dead_letter(body, error)
            # requeue=False → 由 Java 声明队列时配置的 DLX 转投到 queue.generate.dlq。
            # DLQ 没有自动消费方,由人工或脚本兜底(§6.4)。
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
            logger.error("生成任务重试已达上限,转入死信队列 taskId=%s", _task_id_of(body))
            return

        headers = dict(properties.headers or {})
        headers[RETRY_HEADER] = retry + 1
        time.sleep(RETRY_DELAY_SECONDS)

        # 重投 + ACK 原消息:不这样做的话 requeue=True 会立刻被再次投递,
        # 形成热循环且没有退避的余地(同索引消费端)。
        #
        # ⚠️ **整段必须能容忍"连接已经断了"。** 走到这里的常见原因**就是**连接断了
        # (长调用期间没发心跳被 broker 掐掉)—— 那时 channel 已关,
        # `basic_publish` 会抛 `ChannelWrongStateError`,而它原先会一路冒出
        # `_on_message`,把消费者线程带走(2026-10-03 实测,§62)。
        #
        # 正确做法是**什么也别做**:消息没被 ACK,连接恢复后 broker 会把它重投,
        # 那正是我们要的语义 —— 比"假装重投成功"更安全。
        try:
            channel.basic_publish(
                exchange=EXCHANGE,
                routing_key=QUEUE_GENERATE,
                body=body,
                properties=pika.BasicProperties(
                    content_type=properties.content_type or "application/json",
                    delivery_mode=2,  # 持久化,与 Java 声明队列时的口径一致
                    headers=headers,
                ),
            )
            channel.basic_ack(delivery_tag=method.delivery_tag)
        except pika.exceptions.AMQPError as publish_error:
            logger.warning(
                "重投失败(连接已断),本条**不 ACK** —— 等连接恢复后由 broker 重投:"
                "taskId=%s 原因=%s", _task_id_of(body), type(publish_error).__name__,
            )

    def _notify_dead_letter(self, body: bytes, error: Exception) -> None:
        """进 DLQ 之前,把失败告诉 Java —— 否则用户只能干等到补偿扫描接管。

        这一步自己出错**不能**影响进 DLQ:消息该进死信队列还是得进。
        故整段吞异常,只记日志。
        """
        try:
            payload = json.loads(body)
        except Exception:  # noqa: BLE001 —— 消息体本身就坏了,没有 taskId 可回传
            logger.warning("消息体无法解析,跳过失败回调")
            return

        task_id = str(payload.get("taskId") or "")
        if not task_id:
            logger.warning("消息体没有 taskId,跳过失败回调")
            return

        self._api.send_callback(
            {
                "taskId": task_id,
                "status": "failed",
                "error": {
                    "code": CODE_GENERATE_FAILED,
                    "message": f"重试已达上限,转入死信队列:{type(error).__name__}",
                },
                "traceId": str(payload.get("traceId") or ""),
            },
            trace_id=str(payload.get("traceId") or ""),
        )


def _task_id_of(body: bytes) -> str:
    """尽量从消息体里取出 taskId 用于日志;取不到也不该再抛异常。"""
    try:
        return str(json.loads(body).get("taskId", "<unknown>"))
    except Exception:  # noqa: BLE001
        return "<unparsable>"
