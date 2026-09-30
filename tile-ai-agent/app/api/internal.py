"""`/internal/*` 路由(Java ↔ Python)。

契约:`openapi-internal.yaml`。端点自 T-1.5 起逐个落地 —— 目前实装了
`POST /internal/agent/parse-layout`(T-1.7)。

鉴权:服务令牌 `X-Service-Token`
(`openapi-common.yaml#/components/securitySchemes/serviceToken`),
叠加内网 mTLS;对外由网关阻断(架构 §6.1)。

**错误码与对外同一套**(架构 §10.4 约定 1):本组接口的失败直接用 §10.5 的业务码,
不引入"内部码 → 对外码"映射。Java 侧只透传已定义业务码,其余折 90001。
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from app.agent.layout_parse import LayoutParseError, parse_layout, validate_layout
from app.core.config import settings
from app.gateway.factory import build_llm_gateway

router = APIRouter(prefix="/internal", tags=["internal"])

#: 10001 参数校验失败;明细放 data.errors[](契约 ErrorItem)。
CODE_PARAM_INVALID = 10001
#: 90001 系统内部错误。
CODE_INTERNAL_ERROR = 90001

#: 契约已冻结、尚未实装的端点(带 x-todo 者需先评审,勿自行定夺):
#:   POST /internal/agent/chat            Java → Python  SSE(事件集见契约,含 error)
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
