"""网关异常层次(架构 §7.2)。

三类网关共用一套异常,好处是韧性层能**统一**判断两件事:

- `is_transient()` —— 要不要**重试**。重试只对瞬态故障有意义:
  超时、5xx、429 值得再试一次;响应格式不对、4xx,重试一万次也不会变对。
- `counts_toward_failure_rate()` —— 要不要**计入 §13.7 的失败率**。
  这两件事的口径**刻意不同**:不重试 ≠ 不算故障。401 密钥失效、响应格式错乱,
  都说明这个供应商当前不可用,正是该切备份的情形 —— 只是不该重试而已。

沿用本项目既有风格:不用自定义基类体系以外的东西,不引入第三方重试库
(`app/index/consumer.py` 的退避也是手写的)。
"""

from __future__ import annotations

from typing import Final

#: HTTP 429 —— 下游明确要求退避,属瞬态。
HTTP_TOO_MANY_REQUESTS: Final[int] = 429

#: 5xx 起点。>= 此值视为下游故障。
HTTP_SERVER_ERROR: Final[int] = 500


class GatewayError(RuntimeError):
    """网关层异常基类。

    继承 `RuntimeError` 而非 `Exception`,与 `app/index/collections.py`、
    `apply.py` 的既有做法一致(那边用 RuntimeError / ValueError)。
    """

    def is_transient(self) -> bool:
        """瞬态故障:值得重试,且计入失败率。默认否 —— 宁可少重试。"""
        return False


class GatewayConfigError(GatewayError):
    """配置有误:provider 未选或未知、密钥缺失、模型 ID 未填。

    **非瞬态**。这类错误必须响 —— 静默降级会让"配错了"看起来像"模型不行",
    排查时南辕北辙。
    """


class InvalidProviderResponse(GatewayError):
    """供应商返回了无法解析的响应。

    **非瞬态**:格式不对,重试也不会变对。
    """


class GatewayTimeout(GatewayError):
    """调用超时(架构 §7.2 的超时封装)。

    图像单图 ≤60s 是 §7.3 / §13.1 的硬指标,超时即失败,不做无限等待。
    """

    def is_transient(self) -> bool:
        return True


class RateLimited(GatewayError):
    """被限流挡下 —— 本地 Redis 令牌桶拒绝,或下游返回 429。

    瞬态。注意:**本地令牌桶拒绝属预期保护行为,不计入失败率** ——
    我们主动不放行不是"模型故障",把它算进 §13.7 的失败率会导致误切备份。
    """

    def is_transient(self) -> bool:
        return True


class CircuitOpen(GatewayError):
    """熔断打开,调用被直接拒绝,**未发起任何请求**。

    瞬态(冷却后会自愈),但同样**不计入失败率** —— 它本身就是失败率推导出的结论,
    再计入就成了自我印证的循环。
    """

    def is_transient(self) -> bool:
        return True


class ProviderError(GatewayError):
    """供应商返回了错误响应。

    瞬态与否**由状态码决定**,不按供应商区分:
    5xx 与 429 是下游故障(可重试),其余 4xx 是我们发错了(不可重试)。
    """

    def __init__(self, message: str, *, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code

    def is_transient(self) -> bool:
        if self.status_code is None:
            # 连状态码都没拿到(连接层出错),按下游故障处理
            return True
        return self.status_code >= HTTP_SERVER_ERROR or self.status_code == HTTP_TOO_MANY_REQUESTS


def counts_toward_failure_rate(error: BaseException) -> bool:
    """该异常是否计入 §13.7 的"模型调用失败率"(触发切换备份供应商)。

    **除本服务自己的保护动作外,所有网关层故障都计入** —— 是否重试另说。

    `RateLimited` / `CircuitOpen` 明确**不计**:它们是我们主动不放行,
    既不是供应商故障,而且 CircuitOpen 本身就是失败率推导出的结论 ——
    再计回去就成了自我印证的循环,一旦打开就再也掉不下来。
    """
    if isinstance(error, (RateLimited, CircuitOpen)):
        return False
    return isinstance(error, GatewayError)
