"""对话咨询编排 —— 逐帧产出 SSE(D-7 事件集)。

**这是一个同步生成器**,不是 async 的:整条链路(httpx 流式、Qdrant、Java 取数)
都是同步的,而事件循环不能被阻塞。桥接由 `internal.py` 的后台线程负责
(单线程 producer + `asyncio.Queue`)—— 这里只管"按顺序吐帧"。

**绝不把异常抛出去**:一旦开始吐帧,HTTP 状态码就改不了了(契约原文),
所以任何失败都必须变成一帧 `error`。抛出去只会让连接被硬断,
前端分不清"模型报错"还是"网络断了"。见 `sse.py` 的文件头。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any, Iterator

from app.chat import rag, scope
from app.chat.session_store import ChatContextStore, Turn
from app.chat.sse import (
    CODE_INTERNAL_ERROR,
    CODE_OUT_OF_SCOPE,
    CODE_PARAM_INVALID,
    SSE_DONE,
    error_frame,
    sse_frame,
)

logger = logging.getLogger(__name__)

_OUT_OF_SCOPE_MESSAGE = "抱歉,我只能回答瓷砖信息、产品推荐与搭配方案相关的问题。"

#: **模型一个字都没吐**时的兜底文案。
#:
#: 这一档既不是"越界"(50001:这问题不归我管),也不是"故障"(90001:我这边坏了),
#: 而是"服务正常、但这次没答上来"。契约里**没有第三档码**可以表达它 —— 也不该有:
#: 这次问答本身是**成功的**,只是内容为空。所以不发明错误码,而是把它**当作正文**
#: 照常发出去(`delta` → `refs` → `[DONE]`)。
#:
#: ⚠️ 必须**作为一帧 delta 发出**(而不只是写进 Redis):Java 是靠累积 delta 决定
#: 落库正文的,前端也是靠 delta 渲染的 —— 只在内部替换,会让"用户看到的"与
#: "库里存的"对不上,前端仍然是一片空白。
_FALLBACK_ANSWER = (
    "抱歉,这个问题我暂时没能给出合适的建议。"
    "可以换个说法再问一次,或者直接告诉我空间和想要的风格,我帮你挑几款砖。"
)


@dataclass
class ChatDeps:
    """一次问答要用到的依赖。**显式注入**,沿用本项目"不引 DI 容器"的风格。

    测试里把每个字段换成替身即可,不必真连模型/Redis/Qdrant。
    """

    gateway: Any
    embedding: Any
    qdrant: Any
    store: ChatContextStore
    java_api: Any
    trace_id: str = ""


def chat_frames(*, session_id: str, message: str, deps: ChatDeps) -> Iterator[str]:
    """把一次提问编排成若干 SSE 帧。

    ⚠️ **不产出 `sessionId` 帧** —— 那是 Java 的职责(它是会话权威,C 端首帧由它下发)。
    内部契约的正常序列就是 `delta → refs → [DONE]`,Python 侧不发 sessionId。
    """
    try:
        if not (message or "").strip():
            yield error_frame(CODE_PARAM_INVALID, "消息不能为空", deps.trace_id)
            return

        history = deps.store.load(session_id)

        # ① 确定性闸门:问价/越界 —— 命中即拒答,不调模型
        if scope.fast_out_of_scope(message):
            yield error_frame(CODE_OUT_OF_SCOPE, _OUT_OF_SCOPE_MESSAGE, deps.trace_id)
            return

        # ② 检索(只召回在售产品)
        items = rag.retrieve(message, embedding=deps.embedding, qdrant=deps.qdrant)

        # ③ 模型闸门:补隐晦越界;分类器故障一律放行(scope 内部已兜)
        if scope.llm_out_of_scope(deps.gateway, message):
            yield error_frame(CODE_OUT_OF_SCOPE, _OUT_OF_SCOPE_MESSAGE, deps.trace_id)
            return

        # ④ 流式生成
        messages = _build_messages(history, items, message)
        chunks: list[str] = []
        for chunk in deps.gateway.chat(messages, stream=True):
            delta = getattr(chunk, "delta", "")
            if delta:
                chunks.append(delta)
                yield sse_frame({"delta": delta})

        # ⑤ 生成完毕:出 refs(至多一帧)→ 写短期上下文 → [DONE]
        if chunks:
            answer = "".join(chunks)
        else:
            # 模型一个字都没吐 —— 给一句人话,而不是让前端看到空白(见 _FALLBACK_ANSWER)
            logger.warning("模型未产出任何内容,使用兜底文案 session=%s", session_id)
            answer = _FALLBACK_ANSWER
            yield sse_frame({"delta": answer})

        refs = rag.build_refs(
            items,
            part=rag.infer_part(message),
            java_api=deps.java_api,
            trace_id=deps.trace_id,
        )
        yield sse_frame({"refs": refs})

        deps.store.append(session_id, "user", message)
        deps.store.append(session_id, "assistant", answer)

        yield SSE_DONE

    except Exception as error:  # noqa: BLE001 —— 流已开始,故障只能变成 error 帧
        logger.exception("对话编排失败 session=%s", session_id)
        yield error_frame(CODE_INTERNAL_ERROR, "系统内部错误", deps.trace_id)


def _build_messages(history: list[Turn], items: list[Any], message: str) -> list[Any]:
    """组装给模型的消息:系统提示 + 候选产品 + 历史 + 当前提问。"""
    from app.gateway.ports import Message  # 延迟 import,避免模块级耦合

    messages: list[Any] = [Message(role="system", content=rag.SYSTEM_PROMPT)]

    context = rag.context_block(items)
    if context:
        messages.append(
            Message(role="system", content="可推荐的候选产品(只能从中推荐):\n" + context)
        )
    for turn in history:
        messages.append(Message(role=turn.role, content=turn.content))
    messages.append(Message(role="user", content=message))
    return messages
