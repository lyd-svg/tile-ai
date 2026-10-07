"""Python 智能体服务入口(FastAPI)。

职责见架构 §4.2:Agent 编排、对话咨询(路径 C)、户型参数解析、材质映射、
陈设提示词组装、图像生成调用。

不可违反的边界(见 CLAUDE.md「架构不变量」):
  - 本服务**仅内网**,不直接对外;`/internal/*` 对外由网关阻断。
  - **不参与报价** —— 报价只归 Java(架构 §4.1,一致性评审 A1)。
  - 不持有业务权威数据,业务读写一律经 Java;除 Qdrant 向量索引外无数据副本。

契约:同步 `openapi-internal.yaml`,异步 `asyncapi-internal.yaml`。
"""

from __future__ import annotations

import logging
import os
import sys
import threading
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from app.api.internal import router as internal_router
from app.clients.java_api import JavaApiClient
from app.core.config import settings
from app.gateway.factory import build_image_gateway

logger = logging.getLogger(__name__)

#: 吵得没必要的第三方 logger。
#:
#: `httpx` 每发一个 HTTP 请求打一行;`pika` 光是建一条连接就要打十几行
#: (实测:两个消费端一起启动时刷了 15 行连接细节,把真正有用的两行
#: "索引同步消费端已在后台线程启动"淹在中间)。
#: 降到 WARNING **不是静音** —— 连接异常、心跳超时这类我们真正关心的事件
#: 都在 WARNING 以上,照样会打出来。
_NOISY_LOGGERS = ("httpx", "httpcore", "urllib3", "qdrant_client",
                  "sentence_transformers", "pika")


def _configure_logging() -> None:
    """给本服务配一个根 handler。**没有它,应用里所有 `logger.info` 都进不了终端。**

    ⚠️ **这是 2026-10-07 才发现的**:此前全仓**没有任何日志配置**(没有 `basicConfig`、
    没有 `dictConfig`、没有 logging 配置文件、`.env` 里也没有级别)—— 于是所有
    `logger.info(...)` 都只是把消息交给根 logger,而根 logger **一个 handler 都没有**,
    最终被 `logging.lastResort` 丢掉(它只放 WARNING 以上)。

    后果与 §62 那次事故**同型**:消费端线程要是死了,日志里**什么都看不出来** ——
    进程活着、`/health` 照样 200、INFO 日志一条不落,表现成"服务好好的,只是不干活"。
    本次插索引消费端时又踩到一次:启动后终端里**看不到**"已在后台线程启动",
    只能靠去管理接口数消费者来确认。

    **只在根 logger 真的没有 handler 时动手** —— uvicorn 启动时若配过,就让它说了算。
    """
    root = logging.getLogger()
    if root.handlers:
        return

    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
        stream=sys.stderr,
    )
    for name in _NOISY_LOGGERS:
        logging.getLogger(name).setLevel(logging.WARNING)


_configure_logging()


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    """启动/关闭钩子。

    **两个消费端都跑在后台线程里**,不是 asyncio 任务:整条链路都是同步的
    (`pika.BlockingConnection`、`httpx.Client`、`QdrantClient`),塞进事件循环
    会把这些阻塞调用变成整个服务的阻塞。线程是 `daemon`,进程退出时不需要
    额外的停机握手 —— 消费是**手动 ACK** 的,线程被杀时未 ACK 的消息会重新投递,
    这正是要的语义(§6.4)。

    **顺序:先建索引集合,再启消费端。** 索引消费端一开机就要往 Qdrant 写,
    集合不存在的话它会把**每条**消息都失败掉(重试 5 次后进死信队列,
    表现成"启动了、但一块砖都没更新,死信队列还满了")。
    建集合**不受任何开关影响** —— 对话检索同样依赖它。

    ⚠️ **索引消费端(`queue.index.sync`)已从"故意不启动"改为随进程启动**
    (2026-10-07,`openapi-verification.md` §79,反转 §35.11)。
    当时把它推给 T-2.4 的部署编排,理由是"由谁托管是个部署决定";
    但契约 `asyncapi-internal.yaml` 的 `consumeIndexSync` 里
    `x-consumer` 写的就是 **Python 智能体服务** —— 所以那不是未决,
    是**实现没跟上契约**。且交付要求"改砖自动更新、不靠人工重建"。
    回退方式:`index_sync_consumer_in_process=false`。
    """
    _ensure_index_collections()
    generate_thread = _start_generate_consumer()
    index_thread = _start_index_sync_consumer()
    try:
        yield
    finally:
        if generate_thread is not None or index_thread is not None:
            logger.info("服务关闭:消费端随进程退出(daemon 线程)")


def _ensure_index_collections() -> None:
    """启动时确保 Qdrant 的三个集合存在(架构 §9)。**失败只告警,不让服务起不来。**

    ⚠️ **这一步不是可选的。** 在它出现之前,`ensure_collections` 的调用点只有
    测试和 `tools/rebuild_index.py` —— `app/` 底下一处都没有。后果有两件:

      ① **索引消费端**对不存在的集合写入 ⇒ 每条消息都失败 ⇒ 全部进死信队列;
      ② **对话检索**直接抛异常 ⇒ 被 `pipeline.py` 兜成 `90001 系统内部错误` ——
         用户看到的是"系统出错了",而不是"没搜到"。`retrieval.py` 没有兜底。

    ⚠️ **它不加载嵌入模型。** `LocalEmbedding.dimension` 只读配置
    (`local_embedding.py` 的 `dimension` 属性),模型要到第一次 `embed()` 才加载
    (`_model` 初值为 `None`)。所以这一步是廉价的,不会拖慢启动。

    ⚠️ 维度失配时 `ensure_collections` 会**抛异常而不是静默重建** —— 那是刻意的:
    换嵌入模型意味着要重新嵌入全部数据,不该由启动流程顺手做掉。这里把它转成告警。
    """
    try:
        from app.core import clients
        from app.index import collections

        qdrant = clients.get_qdrant_client()
        dimension = clients.get_embedding_gateway().dimension
        collections.ensure_collections(qdrant, dimension)
        logger.info("Qdrant 集合已就绪(维度 %d)", dimension)
    except Exception:  # noqa: BLE001 —— 与消费端同因:Qdrant 挂了不该把 /health 一起打掉
        logger.exception(
            "Qdrant 集合初始化失败。**索引同步与对话检索都会不可用**;"
            "确认 Qdrant 已启动后重启本服务即可。"
        )


def _start_index_sync_consumer() -> threading.Thread | None:
    """按配置启动索引同步消费端。**任何启动失败都只告警,不让服务起不来。**

    装配用的是现成的单例(`clients.get_qdrant_client()` / `get_embedding_gateway()`),
    不另建一套 —— 否则同一个进程里会有两个 Qdrant 客户端、两个嵌入模型实例。
    """
    if not settings.index_sync_consumer_in_process:
        logger.info("index_sync_consumer_in_process=false,本进程不启动索引同步消费端")
        return None

    try:
        from app.core import clients
        from app.index.consumer import IndexSyncConsumer

        consumer = IndexSyncConsumer(
            qdrant=clients.get_qdrant_client(),
            embedding=clients.get_embedding_gateway(),
        )
    except Exception:  # noqa: BLE001 —— 装配失败(嵌入模型没配好)不该把 /health 一起打掉
        logger.exception(
            "索引同步消费端未启动:依赖装配失败。"
            "常见原因:MODEL_EMBEDDING_PROVIDER 未配置(见 .env.example);"
            "改砖将不再自动进搜索库,需手工跑 tools/rebuild_index.py。"
            "HTTP 接口、生成链路与 /internal/* 仍可用"
        )
        return None

    thread = threading.Thread(
        target=consumer.run_forever, name="index-sync-consumer", daemon=True,
    )
    thread.start()
    logger.info("索引同步消费端已在后台线程启动,监听 queue.index.sync")
    return thread


def _start_generate_consumer() -> threading.Thread | None:
    """按配置启动生成消费端。**任何启动失败都只告警,不让服务起不来。**"""
    if not settings.generate_consumer_in_process:
        logger.info("generate_consumer_in_process=false,本进程不启动生成消费端")
        return None

    try:
        from app.generate.consumer import GenerateConsumer

        api_client = JavaApiClient()
        consumer = GenerateConsumer(
            api_client=api_client,
            image_gateway=build_image_gateway(),
        )
    except Exception:  # noqa: BLE001 —— 网关未配置(本地没填密钥)不该把 /health 一起打掉
        logger.exception(
            "生成消费端未启动:依赖装配失败。"
            "常见原因:MODEL_IMAGE_PROVIDER 未配置(见 .env.example);"
            "HTTP 接口与 /internal/* 仍可用"
        )
        return None

    thread = threading.Thread(target=consumer.run_forever, name="generate-consumer", daemon=True)
    thread.start()
    logger.info("生成消费端已在后台线程启动")
    return thread


app = FastAPI(
    title="瓷砖选购 AI — 智能体服务",
    version="0.1.0",
    lifespan=lifespan,
    description=(
        "Java 业务核的下游 AI 服务,仅内网可达。"
        "对外契约 `openapi-internal.yaml`,异步契约 `asyncapi-internal.yaml`。"
    ),
)

app.include_router(internal_router)


@app.get("/health", tags=["ops"], summary="健康检查")
async def health() -> dict[str, str]:
    """进程存活探针,供 Docker Compose healthcheck 使用(架构 §12.1 / T-2.4)。"""
    return {"status": "UP", "service": settings.service_name}
