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
import threading
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from app.api.internal import router as internal_router
from app.clients.java_api import JavaApiClient
from app.core.config import settings
from app.gateway.factory import build_image_gateway

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    """启动/关闭钩子。

    **生成消费端跑在后台线程里**,不是 asyncio 任务:整条链路都是同步的
    (`pika.BlockingConnection`、`httpx.Client`、`QdrantClient`),塞进事件循环
    会把这些阻塞调用变成整个服务的阻塞。线程是 `daemon`,进程退出时不需要
    额外的停机握手 —— 消费是**手动 ACK** 的,线程被杀时未 ACK 的消息会重新投递,
    这正是要的语义(§6.4)。

    ⚠️ **索引消费端(`queue.index.sync`)仍不在这里启动。** 它从 T-0.13 起就只被
    测试直接驱动,原因不是"忘了",而是"由谁托管它"是个部署决定(一期最终由
    T-2.4 的编排决定),不该在这里顺手定下来。见 `openapi-verification.md` §35.11。
    """
    consumer_thread = _start_generate_consumer()
    try:
        yield
    finally:
        if consumer_thread is not None:
            logger.info("服务关闭:生成消费端随进程退出(daemon 线程)")


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
