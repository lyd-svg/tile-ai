"""韧性层:超时、重试、限流、熔断、成本计量(架构 §7.2)。

架构 §7.2 要求「统一封装:超时、重试、限流、熔断、成本计量、返回结构标准化」,
但**没有给出任何阈值** —— 阈值是实现决策,故全部走配置(见 `app/core/config.py`),
不在这里硬编码。唯一由文档钉死的是:图像单图 ≤60s(§7.3 / §13.1)与
模型失败率 > 10% 触发切换备份(§13.7)。

**施加顺序:限流 → 熔断 → 重试 → 超时。**

- 限流最先:令牌桶就是为了保护下游额度,超额时不该白白消耗熔断统计。
- 熔断在重试之外:熔断打开时立即失败,不做无谓重试。
- 超时在最内层,由 HTTP 客户端(httpx)承担,故不在本模块 —— 本模块只负责
  把超时异常翻译成 `GatewayTimeout`。

**时钟与 sleep 一律注入**,所以重试/熔断的测试毫秒级跑完,不真等。
"""

from __future__ import annotations

import logging
import math
import random
import threading
import time
from collections import deque
from dataclasses import dataclass
from typing import Any, Callable, Mapping, Protocol, TypeVar

from app.gateway.errors import (
    CircuitOpen,
    GatewayError,
    RateLimited,
    counts_toward_failure_rate,
)

logger = logging.getLogger(__name__)

T = TypeVar("T")

#: 单调时钟 —— 测时长用。用 time.monotonic 而非 time.time,系统时间被调整时不受影响。
Clock = Callable[[], float]
Sleeper = Callable[[float], None]
#: 返回 [0, 1) 的随机源。注入它是为了让退避时长的断言可复现。
JitterSource = Callable[[], float]


# ----------------------------------------------------------------------
# 重试
# ----------------------------------------------------------------------

@dataclass(frozen=True)
class RetryPolicy:
    """指数退避重试策略。"""

    max_attempts: int = 3
    base_delay_seconds: float = 0.5
    max_delay_seconds: float = 8.0
    jitter_ratio: float = 0.1

    def __post_init__(self) -> None:
        if self.max_attempts < 1:
            raise ValueError("max_attempts 至少为 1")
        if self.base_delay_seconds < 0 or self.max_delay_seconds < 0:
            raise ValueError("退避时长不能为负")
        if not 0.0 <= self.jitter_ratio < 1.0:
            raise ValueError("jitter_ratio 应在 [0, 1) 内")

    def delay_for(self, attempt: int, jitter: float = 0.0) -> float:
        """第 `attempt` 次失败后该等多久(attempt 从 1 开始)。

        抖动**只向上加、不向下减**。常见做法是两个方向都抖,但那会让实际退避
        短于计算值 —— 退避时长的下界是我们要守住的东西(它是对下游故障的尊重),
        不能为了"打散"而被破坏。
        """
        raw = self.base_delay_seconds * (2 ** max(0, attempt - 1))
        capped = min(raw, self.max_delay_seconds)
        return capped * (1.0 + self.jitter_ratio * jitter)


# ----------------------------------------------------------------------
# 熔断
# ----------------------------------------------------------------------

class CircuitBreaker:
    """熔断器:closed → open → half_open → closed。

    以**连续失败次数**为打开条件,任一成功即清零。用连续失败而非滑动窗口,
    是因为它的语义更直白:下游"连着挂了 N 次"就停手,而不是把稀疏的历史失败
    一直记着。

    冷却是时间驱动:open 满 `recovery_seconds` 后进入 half_open,放行
    `half_open_probes` 个探测请求;探测成功回 closed,失败立刻回 open 并重新计时。
    """

    CLOSED = "closed"
    OPEN = "open"
    HALF_OPEN = "half_open"

    def __init__(
        self,
        *,
        failure_threshold: int = 5,
        recovery_seconds: float = 30.0,
        half_open_probes: int = 1,
        clock: Clock = time.monotonic,
    ) -> None:
        if failure_threshold < 1:
            raise ValueError("failure_threshold 至少为 1")
        if recovery_seconds < 0:
            raise ValueError("recovery_seconds 不能为负")
        if half_open_probes < 1:
            raise ValueError("half_open_probes 至少为 1")

        self._failure_threshold = failure_threshold
        self._recovery_seconds = recovery_seconds
        self._half_open_probes = half_open_probes
        self._clock = clock

        self._lock = threading.Lock()
        self._state = self.CLOSED
        self._consecutive_failures = 0
        self._opened_at = 0.0
        self._probes_in_flight = 0

    @property
    def state(self) -> str:
        with self._lock:
            self._maybe_half_open()
            return self._state

    @property
    def consecutive_failures(self) -> int:
        with self._lock:
            return self._consecutive_failures

    def before_call(self) -> None:
        """调用前问路。熔断打开时抛 `CircuitOpen`,**不发请求**。"""
        with self._lock:
            self._maybe_half_open()
            if self._state == self.CLOSED:
                return
            if self._state == self.OPEN:
                remaining = self._recovery_seconds - (self._clock() - self._opened_at)
                raise CircuitOpen(
                    f"熔断打开,{max(0.0, remaining):.1f}s 后进入半开探测"
                )
            # half_open:探测名额有限,超了就不放行
            if self._probes_in_flight >= self._half_open_probes:
                raise CircuitOpen("熔断半开,探测名额已满")
            self._probes_in_flight += 1

    def record_success(self) -> None:
        with self._lock:
            self._probes_in_flight = max(0, self._probes_in_flight - 1)
            self._consecutive_failures = 0
            self._state = self.CLOSED

    def release(self) -> None:
        """只归还半开探测名额,**不改变任何状态**。

        用于"这次调用确实失败了,但那不是下游的锅"的情形(如本服务的编程错误)。
        没有它,半开阶段每遇到一次这类失败就永久少一个探测名额 ——
        名额耗尽后熔断再也不会尝试恢复,变成事实上的永久打开。
        """
        with self._lock:
            self._probes_in_flight = max(0, self._probes_in_flight - 1)

    def record_failure(self) -> None:
        with self._lock:
            self._probes_in_flight = max(0, self._probes_in_flight - 1)
            self._consecutive_failures += 1
            # 半开探测失败 = 下游还没好,立刻回到 open 并重新计时,
            # 不能"再多试几次看看" —— 那等于没有熔断。
            if self._state == self.HALF_OPEN or self._consecutive_failures >= self._failure_threshold:
                self._state = self.OPEN
                self._opened_at = self._clock()

    def _maybe_half_open(self) -> None:
        """调用方须持锁。"""
        if self._state != self.OPEN:
            return
        if self._clock() - self._opened_at >= self._recovery_seconds:
            self._state = self.HALF_OPEN
            self._probes_in_flight = 0


# ----------------------------------------------------------------------
# 失败率(§13.7 的判据来源)
# ----------------------------------------------------------------------

class FailureRateTracker:
    """按供应商记最近 N 次调用的成败,算失败率。

    §13.7:「模型调用失败率 > 10%(触发切换备份供应商)」—— 本类只负责"算",
    切换策略在 `app/gateway/failover.py`。

    **只记瞬态故障**:本地限流与熔断拒绝不计入(见
    `errors.counts_toward_failure_rate`),否则一次限流会被误读成一次供应商故障。
    """

    def __init__(self, *, window: int = 20) -> None:
        if window < 1:
            raise ValueError("window 至少为 1")
        self._window = window
        self._lock = threading.Lock()
        self._samples: dict[str, deque[bool]] = {}

    def record(self, provider: str, *, ok: bool) -> None:
        with self._lock:
            samples = self._samples.setdefault(provider, deque(maxlen=self._window))
            samples.append(ok)

    def failure_rate(self, provider: str) -> float:
        with self._lock:
            samples = self._samples.get(provider)
            if not samples:
                return 0.0
            failures = sum(1 for ok in samples if not ok)
            return failures / len(samples)

    def total(self, provider: str) -> int:
        with self._lock:
            return len(self._samples.get(provider, ()))

    def reset(self, provider: str | None = None) -> None:
        """清空样本。切回主供应商后调用 —— 否则旧故障样本会把主力按在备份位上。"""
        with self._lock:
            if provider is None:
                self._samples.clear()
            else:
                self._samples.pop(provider, None)


# ----------------------------------------------------------------------
# 限流(架构 §13.2 明文指定 Redis 令牌桶)
# ----------------------------------------------------------------------

# 令牌桶的 Lua 脚本。**必须整段原子执行** —— 读-算-写若分成几条命令,
# 两个进程同时取令牌就会超发,"跨实例共享额度"这个目的即落空。
#
# 时间取 **Redis 自己的 TIME**,不用客户端时钟:多实例部署时各机器时钟可能不一致,
# 用客户端时间会让同一把桶在不同机器上算出不同的回填量。
_TOKEN_BUCKET_LUA = """
local key = KEYS[1]
local rate = tonumber(ARGV[1])
local burst = tonumber(ARGV[2])
local want = tonumber(ARGV[3])

local t = redis.call('TIME')
local now = tonumber(t[1]) + tonumber(t[2]) / 1000000

local stored = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(stored[1])
local ts = tonumber(stored[2])
if tokens == nil then
  tokens = burst
  ts = now
end

local elapsed = now - ts
if elapsed < 0 then elapsed = 0 end
tokens = math.min(burst, tokens + elapsed * rate)

local allowed = 0
if tokens >= want then
  tokens = tokens - want
  allowed = 1
end

redis.call('HSET', key, 'tokens', tokens, 'ts', now)
local ttl = math.ceil(burst / rate) + 1
if ttl < 1 then ttl = 1 end
redis.call('EXPIRE', key, ttl)

return allowed
"""


class RedisTokenBucket:
    """Redis 令牌桶(架构 §13.2)。

    跨实例共享同一把桶 —— 这正是用 Redis 而不是进程内计数器的理由:
    二期多实例部署时,进程内限流会让总额度被放大 N 倍。

    取不到令牌抛 `RateLimited`。**本类不吞异常**:Redis 连不上怎么办是调用方
    (工厂)的决策 —— 那里按 §13.3「降级不阻断主流程」选择放行。
    """

    def __init__(
        self,
        client: Any,
        *,
        key: str,
        rate_per_second: float,
        burst: int,
    ) -> None:
        if rate_per_second <= 0:
            raise ValueError("rate_per_second 必须为正")
        if burst < 1:
            raise ValueError("burst 至少为 1")
        self._client = client
        self._key = key
        self._rate = float(rate_per_second)
        self._burst = int(burst)

    def acquire(self, tokens: int = 1) -> None:
        if tokens < 1:
            raise ValueError("tokens 至少为 1")
        allowed = self._client.eval(
            _TOKEN_BUCKET_LUA, 1, self._key, self._rate, self._burst, tokens
        )
        if not allowed:
            raise RateLimited(
                f"限流:{self._key} 令牌不足(rate={self._rate}/s, burst={self._burst})"
            )


# ----------------------------------------------------------------------
# 成本计量
# ----------------------------------------------------------------------

class PriceLike(Protocol):
    """单价结构。`app.core.config.ModelPrice` 结构上满足它,无需转换。"""

    input_per_million: float
    output_per_million: float
    per_image: float


class CostMeter:
    """按供应商累计用量与成本(架构 §7.2「成本计量」)。

    **单价只从配置来**(见 `app/core/config.py` 的 `gateway_model_prices`):
    厂商调价是常态,写死在代码里会让账目悄悄失真。

    它是**估算口径**,用于看板与容量判断,不是对账凭证 —— 真实计费以厂商
    账单为准(折扣、免费额度、峰谷价都不在这里体现)。

    Prometheus 导出属 T-2.3(§13.7 的可观测是独立任务),本次只提供快照。
    """

    def __init__(self, prices: Mapping[str, PriceLike] | None = None) -> None:
        self._prices: dict[str, PriceLike] = dict(prices or {})
        self._lock = threading.Lock()
        self._prompt_tokens: dict[str, int] = {}
        self._completion_tokens: dict[str, int] = {}
        self._images: dict[str, int] = {}
        self._cost: dict[str, float] = {}

    def record_tokens(self, provider: str, prompt_tokens: int, completion_tokens: int) -> float:
        """记一次 LLM 调用的用量,返回这次的成本。"""
        price = self._prices.get(provider)
        amount = 0.0
        if price is not None:
            amount = (
                prompt_tokens / 1_000_000 * price.input_per_million
                + completion_tokens / 1_000_000 * price.output_per_million
            )
        with self._lock:
            self._prompt_tokens[provider] = self._prompt_tokens.get(provider, 0) + prompt_tokens
            self._completion_tokens[provider] = (
                self._completion_tokens.get(provider, 0) + completion_tokens
            )
            self._cost[provider] = self._cost.get(provider, 0.0) + amount
        return amount

    def record_images(self, provider: str, count: int) -> float:
        """记一次出图的张数与成本。"""
        price = self._prices.get(provider)
        amount = price.per_image * count if price is not None else 0.0
        with self._lock:
            self._images[provider] = self._images.get(provider, 0) + count
            self._cost[provider] = self._cost.get(provider, 0.0) + amount
        return amount

    def total(self, provider: str | None = None) -> float:
        with self._lock:
            if provider is not None:
                return self._cost.get(provider, 0.0)
            return sum(self._cost.values())

    def snapshot(self) -> dict[str, dict[str, float]]:
        with self._lock:
            providers = set(self._cost) | set(self._prompt_tokens) | set(self._images)
            return {
                provider: {
                    "prompt_tokens": self._prompt_tokens.get(provider, 0),
                    "completion_tokens": self._completion_tokens.get(provider, 0),
                    "images": self._images.get(provider, 0),
                    "cost": round(self._cost.get(provider, 0.0), 6),
                }
                for provider in sorted(providers)
            }


# ----------------------------------------------------------------------
# 组合:把四项施加到一次调用上
# ----------------------------------------------------------------------

class CallGuard:
    """一次受保护的下游调用。

    每次尝试都重新过一遍限流与熔断,失败按下标退避重试。失败率与熔断只吸收
    **瞬态故障** —— 配置写错、响应格式不对不算下游故障,不该把供应商切走。
    """

    def __init__(
        self,
        *,
        provider: str,
        retry: RetryPolicy | None = None,
        rate_limiter: RedisTokenBucket | None = None,
        breaker: CircuitBreaker | None = None,
        tracker: FailureRateTracker | None = None,
        sleep: Sleeper = time.sleep,
        jitter_source: JitterSource = random.random,
    ) -> None:
        self._provider = provider
        self._retry = retry or RetryPolicy()
        self._rate_limiter = rate_limiter
        self._breaker = breaker
        self._tracker = tracker
        self._sleep = sleep
        self._jitter_source = jitter_source

    def execute(self, fn: Callable[[], T]) -> T:
        attempt = 1
        first_error: BaseException | None = None
        while True:
            # 限流与熔断在 try 之外:它们抛出的异常**不是**这次调用的失败,
            # 而是本服务的保护动作 —— 直接上抛,不重试也不记账。
            #
            # 本地限流不重试是刻意的:令牌桶拒绝说明我们主动不放行,退避再试只是
            # 把请求压在网关里。降级与否交给调用方(§13.3「降级不阻断主流程」)。
            if self._rate_limiter is not None:
                self._rate_limiter.acquire()
            if self._breaker is not None:
                try:
                    self._breaker.before_call()
                except CircuitOpen:
                    if first_error is None:
                        raise  # 进来时就是打开的 —— 调用方该看到"熔断"
                    # 熔断是被**本次调用的失败**打开的。再试确实没意义,但调用方
                    # 关心的是"为什么失败",把 500 换成"熔断打开"只会丢失线索。
                    raise first_error

            try:
                result = fn()
            except BaseException as error:  # noqa: BLE001 —— 要按类型判断后再决定是否重试
                self._record_failure(error)
                if not self._should_retry(error, attempt):
                    raise
                if first_error is None:
                    first_error = error
                delay = self._retry.delay_for(attempt, self._jitter_source())
                logger.warning(
                    "模型调用失败,%.2fs 后重试 provider=%s attempt=%d/%d error=%s",
                    delay,
                    self._provider,
                    attempt,
                    self._retry.max_attempts,
                    type(error).__name__,
                )
                self._sleep(delay)
                attempt += 1
                continue

            self._record_success()
            return result

    def _should_retry(self, error: BaseException, attempt: int) -> bool:
        if attempt >= self._retry.max_attempts:
            return False
        # 只重试网关层的瞬态故障。编程错误(TypeError 之类)重试多少次都一样,
        # 而且会把真正的 bug 掩盖成"模型不稳定"。
        if isinstance(error, GatewayError):
            return error.is_transient()
        return False

    def _record_failure(self, error: BaseException) -> None:
        counts = counts_toward_failure_rate(error)
        if self._breaker is not None:
            # 无论如何都要让熔断器知道这次调用结束了 —— 否则半开探测名额不归还
            if counts:
                self._breaker.record_failure()
            else:
                self._breaker.release()
        if counts and self._tracker is not None:
            self._tracker.record(self._provider, ok=False)

    def _record_success(self) -> None:
        if self._breaker is not None:
            self._breaker.record_success()
        if self._tracker is not None:
            self._tracker.record(self._provider, ok=True)
