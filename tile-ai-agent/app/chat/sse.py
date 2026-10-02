"""SSE 帧工具 —— 事件集由 D-7 裁决冻结。

契约(`openapi-c.yaml` / `openapi-internal.yaml`)规定:**全部是默认 `data:` 帧,
没有 `event:` 名**,靠 payload 里的键区分。

正常结局::

    data: {"sessionId":"..."}     ← 仅 C 端首帧,由 Java 下发(Python 不发)
    data: {"delta":"..."}         ← 若干
    data: {"refs":[...]}          ← 至多一个
    data: [DONE]                  ← 字面量,不是 JSON

失败结局(**与上者互斥**)::

    data: {"error":{"code":…,"message":…,"traceId":…}}   ← 必为最后一帧,不发 [DONE]

⚠️ 契约的事件集**不在任何门禁的覆盖范围内**(G1–G7 与契约比对都只看结构,
不看 SSE 事件序列,见 `openapi-verification.md` §40.4)。所以这里的每个常量
都必须由 T-1.6 自己的测试钉住 —— 测试是这条契约唯一的判据。
"""

from __future__ import annotations

import json
from typing import Any, Final

#: 请求参数不合法(如 message 为空)。与 `internal.py` 的同名常量同值。
CODE_PARAM_INVALID: Final[int] = 10001
#: AI 咨询超出范围(问价、施工、售后等)。CLAUDE.md 铁律 1:问价一律拒答。
CODE_OUT_OF_SCOPE: Final[int] = 50001
#: 系统内部错误(模型不可用、检索故障等)。
CODE_INTERNAL_ERROR: Final[int] = 90001

#: 结束标记。**字面量 `[DONE]`,不是 JSON** —— 前端按它判断"答完了"。
SSE_DONE: Final[str] = "data: [DONE]\n\n"


def sse_frame(payload: dict[str, Any]) -> str:
    """把一个 JSON payload 包成 SSE 帧。

    `ensure_ascii=False`:中文按原样写出,不被转义成 `\\uXXXX`
    —— SSE 流是给前端读的,转义只会让排查问题时看不懂。
    """
    return "data: " + json.dumps(payload, ensure_ascii=False) + "\n\n"


def error_frame(code: int, message: str, trace_id: str = "") -> str:
    """失败帧。形状取自契约:`{"error":{"code","message","traceId"}}`。"""
    return sse_frame({"error": {"code": code, "message": message, "traceId": trace_id}})
