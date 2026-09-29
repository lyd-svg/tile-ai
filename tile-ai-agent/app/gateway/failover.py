"""主力 / 备份供应商的故障切换(架构 §13.7、§15.1)。

§13.7 指标表原文:「模型调用失败率 > 10%(触发切换备份供应商)」。
§15.1 要求 LLM 采用**主力 + 备份双供应商**,由模型网关做故障切换;
备份选智谱 GLM 的理由就是它与主力 DeepSeek **故障域独立**(不同厂商),
同厂商挂两个模型解决不了供应商级故障。

**恢复用半开探测,且不额外发探针请求**:切到备份后,每隔
`probe_seconds` 放**一个真实请求**去主力试探 —— 成功就切回,失败就继续用备份。
比"定时发一个 ping"省一次调用,也不用为 ping 编造假对话。

探测失败时**这一次请求不能让用户承担**:立刻改用备份把它答完。
用户不该为我们自己的探测买单。
"""

from __future__ import annotations

import logging
import time
from typing import Callable, Iterator, Sequence, TypeVar

from app.gateway.errors import GatewayError
from app.gateway.ports import (
    LLMGateway,
    Message,
    Reply,
    ReplyChunk,
    ToolCallPlan,
    ToolDef,
)
from app.gateway.resilience import Clock, FailureRateTracker
from app.gateway.resilient import start_stream

logger = logging.getLogger(__name__)

T = TypeVar("T")


class FailoverLLMGateway:
    """按失败率在主力与备份之间切换的 LLMGateway。"""

    def __init__(
        self,
        *,
        primary: LLMGateway,
        backup: LLMGateway,
        primary_name: str,
        backup_name: str,
        tracker: FailureRateTracker,
        threshold: float = 0.10,
        min_samples: int = 5,
        probe_seconds: float = 60.0,
        clock: Clock = time.monotonic,
    ) -> None:
        if not 0.0 < threshold < 1.0:
            raise ValueError("threshold 应在 (0, 1) 内")
        if min_samples < 1:
            raise ValueError("min_samples 至少为 1")
        self._primary = primary
        self._backup = backup
        self._primary_name = primary_name
        self._backup_name = backup_name
        self._tracker = tracker
        self._threshold = threshold
        self._min_samples = min_samples
        self._probe_seconds = probe_seconds
        self._clock = clock

        self._using_backup = False
        self._probing = False
        self._switched_at = 0.0

    @property
    def current_provider(self) -> str:
        """当前在用哪个供应商。供可观测与测试断言。"""
        return self._backup_name if self._using_backup else self._primary_name

    @property
    def failure_rate(self) -> float:
        return self._tracker.failure_rate(self._primary_name)

    def chat(
        self,
        messages: Sequence[Message],
        tools: Sequence[ToolDef] | None = None,
        stream: bool = False,
    ) -> Reply | Iterator[ReplyChunk]:
        return self._dispatch(
            lambda: self._call(self._primary, messages, tools, stream),
            lambda: self._call(self._backup, messages, tools, stream),
        )

    def chat_with_tools(
        self,
        messages: Sequence[Message],
        tool_defs: Sequence[ToolDef],
    ) -> ToolCallPlan:
        return self._dispatch(
            lambda: self._primary.chat_with_tools(messages, tool_defs),
            lambda: self._backup.chat_with_tools(messages, tool_defs),
        )

    # ------------------------------------------------------------------

    def _dispatch(self, call_primary: Callable[[], T], call_backup: Callable[[], T]) -> T:
        if self._should_use_backup():
            return call_backup()

        if self._probing:
            try:
                result = call_primary()
            except GatewayError:
                self._probing = False
                self._switch_to_backup()
                logger.warning(
                    "主供应商探测失败,继续使用备份 primary=%s backup=%s",
                    self._primary_name,
                    self._backup_name,
                )
                # 这一次请求改由备份答完,不让用户承担探测的代价
                return call_backup()
            self._probing = False
            self._switch_to_primary()
            logger.info("主供应商已恢复 primary=%s", self._primary_name)
            return result

        return call_primary()

    def _should_use_backup(self) -> bool:
        if self._using_backup:
            # 到点了就放一个真请求去试探主力(半开)
            if self._clock() - self._switched_at >= self._probe_seconds:
                self._probing = True
                return False
            return True

        # 样本不足时不下判定 —— 否则第一次调用失败(1/1 = 100%)就会切走,
        # 一次抖动换来一次没有必要的供应商切换。
        if self._tracker.total(self._primary_name) < self._min_samples:
            return False

        if self._tracker.failure_rate(self._primary_name) > self._threshold:
            logger.warning(
                "主供应商失败率 %.3f 超过阈值 %.3f,切换备份 primary=%s backup=%s",
                self._tracker.failure_rate(self._primary_name),
                self._threshold,
                self._primary_name,
                self._backup_name,
            )
            self._switch_to_backup()
            return True
        return False

    def _switch_to_backup(self) -> None:
        self._using_backup = True
        self._probing = False
        self._switched_at = self._clock()

    def _switch_to_primary(self) -> None:
        self._using_backup = False
        self._probing = False
        # 清掉旧样本:不清的话那批失败样本会立刻把主力再按回备份位,
        # 变成"恢复即切走"的抖动。
        self._tracker.reset(self._primary_name)

    @staticmethod
    def _call(
        provider: LLMGateway,
        messages: Sequence[Message],
        tools: Sequence[ToolDef] | None,
        stream: bool,
    ) -> Reply | Iterator[ReplyChunk]:
        """调用指定供应商。

        流式时先把**第一帧**取出来,好让连接层的失败在这里就抛出去 ——
        否则失败要到调用方开始迭代才暴露,这一层的 try/except 形同虚设。
        """
        result = provider.chat(messages, tools, stream)
        if stream:
            assert not isinstance(result, Reply)
            return start_stream(lambda: result)
        return result
