"""Python → Java 的取数与回传客户端(架构 §6.1、§4.3)。

本服务**不直连 MySQL、不持有业务数据副本**(不变量 4),所以"某块砖的规格与照片"
"这个空间配什么陈设""这次生成的结果回给谁"这三件事只能问 Java。三条都在这里:

    GET  /internal/products/{sku}        权威 SKU(含**照片** —— 出图的参考图)
    GET  /internal/furnish/prompts       按「空间 × 风格」取陈设提示词
    POST /internal/generate/callback     回传出图进度与结果

## 两条不变量

**1. 绝不把异常抛进消费循环。** 取数失败一律转成 `None`(或降级值)+ 一条日志。
   消费端是个长驻循环,一次网络抖动让它抛出去就意味着消息重投、整批停顿。
   取不到产品详情 → T-1.10 的 `prepare_prompts` 已经定义好该怎么办(保留部位、
   材质短语为空、记 WARN)。故障的**判断权**在调用方,这一层只做翻译。

**2. 只认响应体里的 `code`,不认 HTTP 状态码。** 契约规定业务失败也是 HTTP 200
   (§10.4 / T-13),只有 401/403 用真实状态码。若按 HTTP 状态判成败,
   "SKU 已下架"(200 + 30001)会被当成成功,`data` 是 null,下游拿到一个空壳。

## 为什么回调也在这里

回传与取数走的是同一个 Java 服务、同一份令牌、同一套超时与错误翻译。
单独拆一个类只会多一处要同步改的地方。它的 `send_callback` 与
`app/generate/orchestrator.py` 的 `CallbackPort` **结构一致**(Protocol 是结构化
类型,无需 import —— 同 `ports.py` 里 `EmbeddingGateway` 对 `EmbeddingPort` 的做法)。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any, Mapping

import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)

#: 契约里 `/internal/*` 不带 `/api/v1` 前缀 —— 它不属于对外 API 的版本体系
#: (不变量 3:Java 是唯一对外 API,而 `/internal/*` 对外由网关阻断)。
INTERNAL_PREFIX = "/internal"

#: 契约 `common.ErrorCode` 的两个值,本层要用它们做判断。
#: 其余码一律按"取不到/没送达"处理,不需要认识。
CODE_OK = 0
#: 40003 —— 任务不存在。回调收到它意味着"别重试了":任务都不在,重试一万次也不在。
CODE_TASK_NOT_FOUND = 40003

#: 响应体片段在日志里的截断长度。
_SNIPPET_LIMIT = 300


@dataclass(frozen=True)
class CallbackResult:
    """一次回调的结局。

    `accepted=False` 有两类原因,调用方要分开对待:
    - `code == 40003` —— **确定性**失败,任务不存在,重试无意义;
    - 其余(连不上、超时、5xx 折算的 90001) —— **瞬态**,值得重试,
      但也不该让整轮生成失败(图已经出来了,进度回调丢一两条不影响终局)。
    """

    accepted: bool
    code: int = 0
    message: str = ""


class JavaApiClient:
    """Java 业务核的 HTTP 客户端。

    端点自己拼成绝对 URL,不用 `httpx` 的 `base_url` —— 外部注入一个 base_url
    不同的客户端时,请求会**静默打到别的主机**上(同 `openai_compat.py` 的取舍)。
    """

    def __init__(
        self,
        *,
        base_url: str | None = None,
        service_token: str | None = None,
        read_timeout_seconds: float | None = None,
        callback_timeout_seconds: float | None = None,
        client: httpx.Client | None = None,
    ) -> None:
        self._base_url = (base_url if base_url is not None else settings.java_base_url).rstrip("/")
        self._service_token = (
            service_token
            if service_token is not None
            else settings.service_token.get_secret_value()
        )
        # 两个超时**刻意分开**:取数是一次索引查询,超过几秒就是出问题了;
        # 回调要写库、还会顺带保存方案(一次事务),给它更宽的余量。
        # 但两者都与 60s 的**图像**超时无关 —— 那是模型调用的硬指标(§7.3)。
        self._read_timeout = (
            read_timeout_seconds
            if read_timeout_seconds is not None
            else settings.java_api_timeout_seconds
        )
        self._callback_timeout = (
            callback_timeout_seconds
            if callback_timeout_seconds is not None
            else settings.java_callback_timeout_seconds
        )
        self._owns_client = client is None
        # 每个请求各带自己的 timeout,故客户端这里给一个宽松的上界即可
        self._client = client or httpx.Client(
            timeout=httpx.Timeout(max(self._read_timeout, self._callback_timeout))
        )

    def close(self) -> None:
        """只关闭自己建的客户端 —— 外部注入的客户端归外部管。"""
        if self._owns_client:
            self._client.close()

    def __repr__(self) -> str:
        """只列可公开字段,杜绝把令牌带进日志。"""
        return f"<JavaApiClient base_url={self._base_url}>"

    # ------------------------------------------------------------------
    # 取数
    # ------------------------------------------------------------------

    def get_product(self, sku: str, *, trace_id: str = "") -> dict[str, Any] | None:
        """取权威 SKU(**含照片**)。取不到返回 None,不抛。

        返回体里的 `coverImage` / `images[].url` 就是出图要用的参考图
        (§15.3 多参考图条件生成)—— 所以这一个请求同时解决了"材质描述"
        与"参考图"两件事,不需要再加一个"取照片"的接口。
        """
        if not sku:
            return None
        data = self._get_json(f"{INTERNAL_PREFIX}/products/{sku}", trace_id=trace_id)
        return data if isinstance(data, dict) else None

    def get_furnish_prompts(
        self, space: str, style: str, *, trace_id: str = ""
    ) -> dict[str, Any] | None:
        """取陈设提示词。取不到返回 None —— 调用方降级为"无陈设段"(架构 §13.3)。

        Java 侧对"未配置"已经返回 `{prompt:"", furnishConfigured:false}` 的**正常响应**,
        所以 None 只代表"这一次没问到",不代表"没配置"。两者在提示词组装上
        结果相同(都跳过陈设段),但日志里要能分辨。
        """
        return self._get_json(
            f"{INTERNAL_PREFIX}/furnish/prompts",
            params={"space": space, "style": style},
            trace_id=trace_id,
        )

    # ------------------------------------------------------------------
    # 回传
    # ------------------------------------------------------------------

    def send_callback(self, body: Mapping[str, Any], *, trace_id: str = "") -> CallbackResult:
        """回传一次生成进度或结果。**不抛异常**,失败翻译成 `CallbackResult`。

        响应体里的 `data` 恒为 null(契约只声明了"受理结果"),所以这里不看 data。
        """
        envelope = self._post_envelope(
            f"{INTERNAL_PREFIX}/generate/callback", body, trace_id=trace_id
        )
        if envelope is None:
            # 连不上 / 超时 / 响应不是 JSON —— 全是瞬态,交给调用方决定要不要重试
            return CallbackResult(accepted=False, code=0, message="回调未送达")

        code = int(envelope.get("code") or 0)
        if code == CODE_OK:
            return CallbackResult(accepted=True)
        return CallbackResult(accepted=False, code=code, message=str(envelope.get("message") or ""))

    # ------------------------------------------------------------------
    # HTTP 细节
    # ------------------------------------------------------------------

    def _get_json(
        self,
        path: str,
        *,
        params: Mapping[str, Any] | None = None,
        trace_id: str = "",
    ) -> Any:
        """GET 一个 `ApiResponse` 信封,成功返回 `data`,其余一律返回 None。"""
        try:
            response = self._client.get(
                self._url(path),
                params=dict(params or {}),
                headers=self._headers(trace_id),
                timeout=self._read_timeout,
            )
        except httpx.HTTPError as error:
            logger.warning("请求 Java 失败 path=%s:%s", path, type(error).__name__)
            return None
        return self._data_of(response, path)

    def _post_envelope(
        self, path: str, body: Mapping[str, Any], *, trace_id: str = ""
    ) -> dict[str, Any] | None:
        """POST 一个 JSON 体,返回**整个信封**(回传要读 code,不能只取 data)。"""
        try:
            response = self._client.post(
                self._url(path),
                json=dict(body),
                headers=self._headers(trace_id),
                timeout=self._callback_timeout,
            )
        except httpx.HTTPError as error:
            logger.warning("回调 Java 失败 path=%s:%s", path, type(error).__name__)
            return None

        payload = _decode(response)
        if not isinstance(payload, dict):
            logger.warning("回调 Java 的响应不是信封 path=%s status=%s", path, response.status_code)
            return None
        return payload

    def _data_of(self, response: httpx.Response, path: str) -> Any:
        payload = _decode(response)
        if not isinstance(payload, dict):
            logger.warning("Java 响应不是信封 path=%s status=%s", path, response.status_code)
            return None

        code = int(payload.get("code") or 0)
        if code != CODE_OK:
            # 30001(SKU 已下架/不存在)是最常见的一个,属**预期内**降级路径,
            # 故用 info;其余码记 warning,它们通常意味着契约或部署出了问题。
            level = logging.INFO if payload.get("code") == 30001 else logging.WARNING
            logger.log(level, "Java 返回业务失败 path=%s code=%s message=%s",
                       path, code, payload.get("message"))
            return None
        return payload.get("data")

    def _url(self, path: str) -> str:
        return f"{self._base_url}{path}"

    def _headers(self, trace_id: str) -> dict[str, str]:
        """服务令牌 + 全链路追踪 ID(架构 §13.7 跨进程串联)。

        令牌**未配置时不带这个头**:Java 侧对空令牌是"放行 + 告警"同口径,
        带一个空字符串反而会走到"令牌无效"分支上去。
        """
        headers: dict[str, str] = {"Content-Type": "application/json"}
        if self._service_token:
            headers["X-Service-Token"] = self._service_token
        if trace_id:
            headers["X-Trace-Id"] = trace_id
        return headers


def _decode(response: httpx.Response) -> Any:
    """解析响应体。解析不了返回 None,不抛 —— 本层的契约就是"不抛进消费循环"。"""
    try:
        return response.json()
    except Exception:  # noqa: BLE001 —— 非 JSON 响应(网关 HTML 错误页等)
        logger.warning(
            "Java 响应无法解析为 JSON status=%s body=%s",
            response.status_code,
            response.text[:_SNIPPET_LIMIT],
        )
        return None
