"""把韧性层套到网关上(架构 §7.2「统一封装」)。

`resilience.py` 提供零件(重试、熔断、令牌桶、成本计量),本文件把它们
**统一**套到三类网关上 —— 逐个网关各写一份重试逻辑,迟早会走形。

套法:装饰器式包装。适配器只管"怎么跟这个厂商说话",这里只管
"说不上话时怎么办"。两者互不知情,换厂商不影响韧性,调韧性不影响协议。
"""

from __future__ import annotations

import itertools
from typing import Callable, Iterator, Sequence, TypeVar

from app.gateway.ports import (
    ImageGenGateway,
    Images,
    LLMGateway,
    Message,
    Reply,
    ReplyChunk,
    ToolCallPlan,
    ToolDef,
)
from app.gateway.resilience import CallGuard, CostMeter

T = TypeVar("T")


def start_stream(factory: Callable[[], Iterator[ReplyChunk]]) -> Iterator[ReplyChunk]:
    """把"建立流并取到第一帧"变成一次**会真正抛错**的操作。

    httpx 的流式调用是惰性的:拿到生成器不产生任何 I/O,错误要到迭代时才冒出来。
    若直接把生成器交给 guard,重试与熔断就永远看不到失败。

    取到第一帧后**不再重试** —— 已经吐给调用方的帧收不回来,
    重放会造成内容重复。中途断流由调用方降级处理(架构 §13.3)。
    """
    iterator = factory()
    try:
        first = next(iterator)
    except StopIteration:
        return iter(())
    return itertools.chain([first], iterator)


class ResilientLLMGateway:
    """LLMGateway 的韧性包装。"""

    def __init__(
        self,
        inner: LLMGateway,
        *,
        guard: CallGuard,
        provider: str = "",
        meter: CostMeter | None = None,
    ) -> None:
        self._inner = inner
        self._guard = guard
        self._provider = provider
        self._meter = meter

    def chat(
        self,
        messages: Sequence[Message],
        tools: Sequence[ToolDef] | None = None,
        stream: bool = False,
    ) -> Reply | Iterator[ReplyChunk]:
        if stream:
            chunks = self._guard.execute(
                lambda: start_stream(lambda: self._inner.chat(messages, tools, True))  # type: ignore[arg-type]
            )
            return self._meter_stream(chunks)

        result = self._guard.execute(lambda: self._inner.chat(messages, tools, False))
        assert isinstance(result, Reply)
        self._record_usage(result.usage.prompt_tokens, result.usage.completion_tokens)
        return result

    def chat_with_tools(
        self,
        messages: Sequence[Message],
        tool_defs: Sequence[ToolDef],
    ) -> ToolCallPlan:
        plan = self._guard.execute(lambda: self._inner.chat_with_tools(messages, tool_defs))
        self._record_usage(plan.usage.prompt_tokens, plan.usage.completion_tokens)
        return plan

    def _meter_stream(self, chunks: Iterator[ReplyChunk]) -> Iterator[ReplyChunk]:
        """流式用量的记账点。

        用量只出现在**最后一帧** —— 按帧累加会重复计数:
        供应商在每一帧都可能带上累计 usage,而不是增量。
        """
        for chunk in chunks:
            if chunk.usage is not None:
                self._record_usage(chunk.usage.prompt_tokens, chunk.usage.completion_tokens)
            yield chunk

    def _record_usage(self, prompt_tokens: int, completion_tokens: int) -> None:
        if self._meter is None:
            return
        if prompt_tokens == 0 and completion_tokens == 0:
            # 供应商没回 usage(某些兼容端点会省)。记 0 笔总比按猜测的下限记账诚实。
            return
        self._meter.record_tokens(self._provider, prompt_tokens, completion_tokens)


class ResilientImageGenGateway:
    """ImageGenGateway 的韧性包装。"""

    def __init__(
        self,
        inner: ImageGenGateway,
        *,
        guard: CallGuard,
        provider: str = "",
        meter: CostMeter | None = None,
    ) -> None:
        self._inner = inner
        self._guard = guard
        self._provider = provider
        self._meter = meter

    def generate(
        self,
        prompt: str,
        ref_images: Sequence[str] | None = None,
        size: str | None = None,
        n: int = 1,
        seed: int | None = None,
    ) -> Images:
        images = self._guard.execute(
            lambda: self._inner.generate(prompt, ref_images, size, n, seed)
        )
        if self._meter is not None and images.images:
            # 按**实际返回的张数**计价,不是请求的 n —— 少给一张就少算一张的钱。
            self._meter.record_images(self._provider, len(images.images))
        return images
