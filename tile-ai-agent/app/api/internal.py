"""`/internal/*` 路由(Java ↔ Python)。

契约:`openapi-internal.yaml`。端点自 T-1.5 起逐个落地 —— 目前实装了
`POST /internal/agent/parse-layout`(T-1.7)与
`POST /internal/agent/chat`(T-1.6,SSE 流式)。

鉴权:服务令牌 `X-Service-Token`
(`openapi-common.yaml#/components/securitySchemes/serviceToken`),
叠加内网 mTLS;对外由网关阻断(架构 §6.1)。

**错误码与对外同一套**(架构 §10.4 约定 1):本组接口的失败直接用 §10.5 的业务码,
不引入"内部码 → 对外码"映射。Java 侧只透传已定义业务码,其余折 90001。
"""

from __future__ import annotations

import asyncio
import logging
import threading
from typing import Any, AsyncIterator, Iterator

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.agent.layout_parse import LayoutParseError, parse_layout, validate_layout
from app.chat.pipeline import ChatDeps, chat_frames
from app.chat.session_store import RedisChatContextStore
from app.chat.sse import CODE_INTERNAL_ERROR as CHAT_CODE_INTERNAL_ERROR
from app.chat.sse import error_frame
from app.core import clients
from app.core.config import settings
from app.gateway.factory import build_llm_gateway

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/internal", tags=["internal"])

#: 10001 参数校验失败;明细放 data.errors[](契约 ErrorItem)。
CODE_PARAM_INVALID = 10001
#: 90001 系统内部错误。
CODE_INTERNAL_ERROR = 90001

#: 契约已冻结、尚未实装的端点(带 x-todo 者需先评审,勿自行定夺):
#:   POST /internal/agent/material-map    Java → Python
#:   POST /internal/agent/complete-parts  Java → Python
#:   GET  /internal/products/{sku}        Python → Java
#:   GET  /internal/furnish/prompts       Python → Java
#:   POST /internal/generate/callback     Python → Java
#:
#: 注意:契约中**不存在** /internal/quote/* —— 报价只归 Java(架构 §4.1 / A1)。


class ParseLayoutRequest(BaseModel):
    """契约 `POST /internal/agent/parse-layout` 的请求体。

    两种输入形态(契约原文):`text`(自然语言)或 `params`(结构化表单)。
    **`params` 优先于 `text`**;两者都不给 → 10001。

    `params` 用 `dict` 而不是建一个 pydantic 模型,是为了**不让失败跑到信封外面**:
    FastAPI 对模型校验失败会直接返 HTTP 422(detail 结构也与契约信封不同),
    而本组接口的约定是"失败一律 200 + 业务码"(架构 §10.4 约定 1)。收下 `Any` 形状、
    用与文本路径**同一个** `validate_layout` 校验,错误才长得一样。
    """

    text: str = Field(default="", description="自然语言描述")

    params: dict[str, Any] | None = Field(
        default=None,
        description="结构化表单(契约 LayoutModel);与 text 同时给出时以本字段为准",
    )


def _envelope(code: int, message: str, data: Any = None, trace_id: str = "") -> dict[str, Any]:
    """统一响应体(§10.1)。业务失败也是 HTTP 200,成败看 code。"""
    return {"code": code, "message": message, "data": data, "traceId": trace_id}


def _trace_of(request: Request) -> str:
    """透传 Java 传来的 traceId(架构 §13.7 跨进程串联)。"""
    return request.headers.get("X-Trace-Id", "")


def require_service_token(request: Request) -> None:
    """服务令牌校验(契约 `serviceToken`)。

    未配置令牌时**放行并告警**:本地开发默认不配,若直接拒绝会让所有联调卡住。
    生产必须配置(架构 §13.6),届时这里会真正生效。
    """
    expected = settings.service_token.get_secret_value()
    if not expected:
        return
    if request.headers.get("X-Service-Token") != expected:
        raise HTTPException(status_code=401, detail="服务令牌无效")


@router.post(
    "/agent/parse-layout",
    dependencies=[Depends(require_service_token)],
    summary="自然语言 → 结构化户型",
)
def parse_layout_endpoint(request: Request, payload: ParseLayoutRequest) -> dict[str, Any]:
    """把自然语言**或结构化表单**解析为 `LayoutModel`。

    失败**一律 10001 + `data.errors[]`**,而不是 5xx ——
    "用户描述里没给尺寸"是**输入问题**,不是服务故障,前端据此提示用户补齐。
    真正的服务故障(模型不可用、密钥错)才落到 90001。
    """
    trace_id = _trace_of(request)

    if payload.params is not None:
        # 表单路径:不调模型(契约:params 优先于 text)。
        # 校验复用 text 路径同一个 validate_layout —— 两条入口对同一份数据
        # 必须给出同样的结论,否则"从表单进来能过、从描述进来不过"这种差异
        # 会变成后端之间的口径分叉。
        errors = validate_layout(payload.params)
        if errors:
            return _envelope(CODE_PARAM_INVALID, "参数校验失败",
                             {"errors": errors}, trace_id)
        # 与 text 路径一样**不派生 areas** —— 派生只在 Java 发生一次(见下)。
        # 注:Java 目前对结构化表单就地处理、不调本端点(T-1.7),
        # 所以这条分支暂无生产调用方;实装它是为了与契约对齐(§36.8)。
        return _envelope(0, "ok", payload.params, trace_id)

    if not payload.text.strip():
        # 走到这里 = params 没给、text 也空 —— 契约的「两者都不给 → 10001」。
        # 两条都给时不会到这里(params 已经在上面返回了)。
        return _envelope(
            CODE_PARAM_INVALID,
            "参数校验失败",
            {"errors": [{"field": "text", "reason": "描述为空,且未给出结构化参数 params"}]},
            trace_id,
        )

    try:
        gateway = build_llm_gateway()
    except Exception as error:  # noqa: BLE001 —— 未配置 provider/密钥
        # 配置缺失属**服务方**问题,不该伪装成"用户输入有误"
        return _envelope(CODE_INTERNAL_ERROR, f"模型网关未就绪:{error}", None, trace_id)

    try:
        layout = parse_layout(gateway, payload.text)
    except LayoutParseError as error:
        return _envelope(CODE_PARAM_INVALID, "参数校验失败",
                         {"errors": list(error.errors)}, trace_id)
    except Exception as error:  # noqa: BLE001 —— 模型超时/熔断/限流等
        return _envelope(CODE_INTERNAL_ERROR, f"解析服务暂时不可用:{type(error).__name__}",
                         None, trace_id)

    # 刻意**不派生 areas** —— 那是 Java 的职责(唯一实现),
    # 否则派生逻辑会出现两份,迟早不一致。见 layout_parse 模块头第 3 条。
    return _envelope(0, "ok", layout, trace_id)


# ----------------------------------------------------------------------
# 对话咨询(路径 C,T-1.6)—— SSE 流式
# ----------------------------------------------------------------------


class ChatRequest(BaseModel):
    """契约 `POST /internal/agent/chat` 的请求体。

    **所有字段都带默认值**:FastAPI 对缺字段会直接返 HTTP 422(detail 结构与契约
    信封不同),而本端点约定"失败一律走 SSE error 帧"(§10.4 约定 2)。
    收下宽松形状、在编排里判空,失败才长得一样。
    """

    sessionId: str = Field(default="", description="会话 ID(Java 侧生成)")
    message: str = Field(default="", description="用户消息")
    userId: int | None = Field(default=None, description="发起用户(Java 透传,留痕)")
    context: dict[str, Any] | None = Field(default=None, description="可选上下文")


@router.post(
    "/agent/chat",
    dependencies=[Depends(require_service_token)],
    summary="对话咨询编排(路径 C,SSE)",
)
async def agent_chat(request: Request, payload: ChatRequest) -> StreamingResponse:
    """RAG 检索 → LLM 生成 → SSE 逐帧返回(事件集见 D-7 裁决)。"""
    trace_id = _trace_of(request)
    headers = {
        "Cache-Control": "no-cache",
        # 反代(网关)不要缓冲这条流 —— 缓冲会让"边想边说"变成"憋完再说"
        "X-Accel-Buffering": "no",
        "Connection": "keep-alive",
    }

    try:
        deps = _chat_deps(trace_id)
    except Exception as error:  # noqa: BLE001 —— 网关未配置等:仍以 error 帧作答,不返 5xx
        logger.warning("对话依赖装配失败:%s", error)
        return StreamingResponse(
            _one_frame(error_frame(CHAT_CODE_INTERNAL_ERROR, "系统内部错误", trace_id)),
            media_type="text/event-stream",
            headers=headers,
        )

    frames = chat_frames(session_id=payload.sessionId, message=payload.message, deps=deps)
    return StreamingResponse(_bridge(frames), media_type="text/event-stream", headers=headers)


def _chat_deps(trace_id: str) -> ChatDeps:
    return ChatDeps(
        gateway=clients.get_llm_gateway(),
        embedding=clients.get_embedding_gateway(),
        qdrant=clients.get_qdrant_client(),
        store=RedisChatContextStore(clients.get_redis_client()),
        java_api=clients.get_java_api_client(),
        trace_id=trace_id,
    )


async def _one_frame(frame: str) -> AsyncIterator[str]:
    """只吐一帧 —— 用于"依赖装配就失败了"的情形,好让失败也走 SSE 契约。"""
    yield frame


async def _bridge(frames: Iterator[str]) -> AsyncIterator[str]:
    """把**同步**的帧生成器桥到事件循环。

    ⚠️ **不用 `run_in_threadpool` 逐帧**:httpx 的流式迭代器必须**在同一线程**
    创建与消费,逐帧往线程池丢会在不同 worker 之间飘,流式读取就不安全了;
    而且每个 token 一次线程池往返,开销也大。这里改成"**一个后台线程当 producer,
    用 `asyncio.Queue` 把帧交回事件循环**" —— 既不阻塞 loop,又保证迭代器始终在
    自己那一个线程里。

    客户端断开时 `finally` 置 `stop`,producer 在下一帧前收手
    —— 不能让一个已断开的连接把后台线程一直占着。
    """
    loop = asyncio.get_running_loop()
    queue: asyncio.Queue[str | None] = asyncio.Queue()
    stop = threading.Event()

    def produce() -> None:
        try:
            for frame in frames:
                if stop.is_set():
                    break
                loop.call_soon_threadsafe(queue.put_nowait, frame)
        except Exception:  # noqa: BLE001 —— 兜底:绝不能把异常抛进生成器
            logger.exception("对话帧生成崩溃")
        finally:
            try:
                loop.call_soon_threadsafe(queue.put_nowait, None)
            except RuntimeError:  # 事件循环已关闭(进程正在退出)
                pass

    threading.Thread(target=produce, name="chat-producer", daemon=True).start()
    try:
        while True:
            item = await queue.get()
            if item is None:
                break
            yield item
    finally:
        stop.set()
