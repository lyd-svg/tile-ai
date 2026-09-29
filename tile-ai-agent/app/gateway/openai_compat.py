"""OpenAI 兼容协议的供应商适配器(架构 §7.2)。

架构 §7.2:「供应商适配器(示例,均可替换)… 适配器统一走 **OpenAI 兼容协议**」。
选中的主力 DeepSeek、备份智谱 GLM、图像通义万相都提供兼容端点,于是**一个适配器
配不同 base_url + model 就能覆盖全部候选** —— 这正是"厂商未定不阻塞网关层"的含义。

**同步而非异步**:本项目既有代码全是同步的(`pika.BlockingConnection`、
`QdrantClient`、显式构造注入),而且 `EmbeddingPort.embed` 是同步的 ——
若把网关做成 async,`EmbeddingGateway` 就再也不能直接喂给 `apply_event`,
T-0.13 的接缝会断。T-1.6 的 SSE 端点可用 `run_in_threadpool` 调这些同步方法;
流式部分返回同步迭代器,由它逐帧转成 SSE。这个取舍留给 T-1.6 复核。

**绝不把密钥写进日志或异常消息**:Authorization 头只存在于 `_headers`,
任何日志与异常文本都只带 provider / 状态码 / 错误类型。
"""

from __future__ import annotations

import json
from typing import Any, Iterable, Iterator, Sequence

import httpx

from app.gateway.errors import (
    GatewayConfigError,
    GatewayTimeout,
    InvalidProviderResponse,
    ProviderError,
)
from app.gateway.ports import (
    GeneratedImage,
    Images,
    Message,
    Reply,
    ReplyChunk,
    ToolCall,
    ToolCallPlan,
    ToolDef,
    Usage,
)

#: 响应体片段在错误消息里的截断长度。带上一点原文便于排查,
#: 但不整段回灌 —— 供应商出错时可能把我们的提示词原样吐回来。
_ERROR_SNIPPET_LIMIT = 300


# ----------------------------------------------------------------------
# 纯函数:请求构造与响应解析(便于单测直接断言,也便于厂商形态变化时只改这里)
# ----------------------------------------------------------------------

def build_chat_payload(
    *,
    model: str,
    messages: Sequence[Message],
    tools: Sequence[ToolDef] | None = None,
    stream: bool = False,
) -> dict[str, Any]:
    """构造 `/chat/completions` 的请求体。"""
    payload: dict[str, Any] = {
        "model": model,
        "messages": [_message_to_openai(message) for message in messages],
    }
    if stream:
        payload["stream"] = True
        # 不显式要 usage 的话,流式响应最后一帧不会带用量,成本计量就成了瞎账。
        payload["stream_options"] = {"include_usage": True}
    if tools:
        payload["tools"] = [tool.to_openai() for tool in tools]
    return payload


def _message_to_openai(message: Message) -> dict[str, Any]:
    payload: dict[str, Any] = {"role": message.role, "content": message.content}
    if message.tool_call_id:
        payload["tool_call_id"] = message.tool_call_id
    if message.tool_calls:
        payload["tool_calls"] = [
            {
                "id": call.id,
                "type": "function",
                "function": {"name": call.name, "arguments": call.arguments},
            }
            for call in message.tool_calls
        ]
    return payload


def parse_chat_response(data: Any, *, provider: str) -> Reply:
    """解析 `/chat/completions` 的响应体。"""
    choices = _require(data, "choices", provider)
    if not isinstance(choices, list) or not choices:
        raise InvalidProviderResponse(f"{provider} 响应里没有 choices")
    message = choices[0].get("message")
    if not isinstance(message, dict):
        raise InvalidProviderResponse(f"{provider} 响应里没有 choices[0].message")

    return Reply(
        content=message.get("content") or "",
        tool_calls=_parse_tool_calls(message.get("tool_calls")),
        usage=_parse_usage(data.get("usage")),
        model=str(data.get("model") or ""),
    )


def to_tool_call_plan(reply: Reply) -> ToolCallPlan:
    """`Reply` → `ToolCallPlan`。两者字段相同,差别只在语义。"""
    return ToolCallPlan(tool_calls=reply.tool_calls, usage=reply.usage, model=reply.model)


def iter_stream_chunks(lines: Iterable[str]) -> Iterator[ReplyChunk]:
    """把 SSE 行解析成 `ReplyChunk`。

    **流式下的 `tool_calls` 不在这里拼装**:增量分片语义(`index` 关联、参数串
    分片拼接)只有工具循环的调用方(T-1.6)才说得清该怎么合并,本层不替它决定。
    这里保证 `delta` 与最后一帧的 `usage` 正确。

    解析不了的行**跳过而不是抛错** —— 供应商会插心跳与注释行,
    为一行噪音炸掉整条流不划算。
    """
    for line in lines:
        stripped = line.strip()
        if not stripped or not stripped.startswith("data:"):
            continue
        payload = stripped[len("data:"):].strip()
        if payload == "[DONE]":
            yield ReplyChunk(done=True)
            return
        try:
            event = json.loads(payload)
        except ValueError:
            continue

        choices = event.get("choices") or []
        delta = (choices[0].get("delta") or {}) if choices else {}
        usage = event.get("usage")
        yield ReplyChunk(
            delta=delta.get("content") or "",
            usage=_parse_usage(usage) if usage else None,
            model=str(event.get("model") or ""),
        )


def _parse_tool_calls(raw: Any) -> tuple[ToolCall, ...]:
    if not raw:
        return ()
    calls: list[ToolCall] = []
    for item in raw:
        function = item.get("function") or {}
        calls.append(
            ToolCall(
                id=str(item.get("id") or ""),
                name=str(function.get("name") or ""),
                arguments=str(function.get("arguments") or ""),
            )
        )
    return tuple(calls)


def _parse_usage(raw: Any) -> Usage:
    if not isinstance(raw, dict):
        return Usage()
    return Usage(
        prompt_tokens=int(raw.get("prompt_tokens") or 0),
        completion_tokens=int(raw.get("completion_tokens") or 0),
    )


def _require(data: Any, key: str, provider: str) -> Any:
    if not isinstance(data, dict) or key not in data:
        raise InvalidProviderResponse(f"{provider} 响应缺少字段 {key}")
    return data[key]


# ----------------------------------------------------------------------
# 图像:请求构造与响应解析
# ----------------------------------------------------------------------
#
# ⚠️ **下面两处的字段形态尚未与阿里云百炼官方文档核实。**
#
# 架构 §7.2 只说"适配器统一走 OpenAI 兼容协议",而 `wan2.7-image-pro` 的
# **多参考图条件生成**是否就落在 `/images/generations` 上、参考图用哪个字段名,
# 我们既没有厂商文档也没有 API Key 可验 —— 所以**不做任何"已验证"的宣称**。
#
# 隔离方式:厂商形态只出现在下面这两个纯函数里。真实形态一旦确认,改这里即可,
# 韧性层、故障切换、成本计量与调用方都不受影响。
# 单测断言的是**我们自己的契约**(§7.1 的签名 + §15.3 的 ≥4 张参考图),
# 不是厂商会接受这个字段名。

_IMAGE_ENDPOINT = "/images/generations"
#: 参考图字段名 —— **待核实**。
_REFERENCE_IMAGES_FIELD = "image_urls"


def build_image_payload(
    *,
    model: str,
    prompt: str,
    ref_images: Sequence[str] | None,
    size: str | None,
    n: int,
    seed: int | None,
) -> dict[str, Any]:
    """构造图像生成请求体。

    `ref_images` 是**URL 列表**(架构约定图片一律存对象存储、库里只存 URL)。
    """
    payload: dict[str, Any] = {"model": model, "prompt": prompt, "n": n}
    if size:
        payload["size"] = size
    if seed is not None:
        payload["seed"] = seed
    if ref_images:
        payload[_REFERENCE_IMAGES_FIELD] = list(ref_images)
    return payload


def parse_image_response(data: Any, *, provider: str) -> Images:
    """解析图像生成响应体。

    兼容两种常见回法:`data[].url`(返回 URL)与 `data[].b64_json`(返回 base64)。
    架构约定图片存对象存储,所以真实使用以 URL 为主;base64 一并支持,
    免得日后厂商默认回 base64 时整条链路不通。
    """
    items = _require(data, "data", provider)
    if not isinstance(items, list):
        raise InvalidProviderResponse(f"{provider} 图像响应的 data 不是数组")

    images: list[GeneratedImage] = []
    for item in items:
        if not isinstance(item, dict):
            raise InvalidProviderResponse(f"{provider} 图像响应的条目不是对象")
        url = item.get("url")
        b64 = item.get("b64_json")
        if url is None and b64 is None:
            # 条目既没 URL 也没 base64 —— 静默跳过会让"生成了 1 张却拿到 0 张"
            # 变成一个没有线索的谜,这里直接说清楚。
            raise InvalidProviderResponse(f"{provider} 图像响应的条目既无 url 也无 b64_json")
        images.append(GeneratedImage(url=url, b64=b64))

    return Images(
        images=tuple(images),
        usage=_parse_usage(data.get("usage")),
        model=str(data.get("model") or ""),
    )


# ----------------------------------------------------------------------
# 适配器
# ----------------------------------------------------------------------

class _OpenAICompatibleBase:
    """共用的出网与错误翻译。"""

    def __init__(
        self,
        *,
        provider: str,
        base_url: str,
        model: str,
        api_key: str,
        timeout_seconds: float,
        client: httpx.Client | None = None,
    ) -> None:
        # 缺配置就响,不静默降级 —— 否则"密钥没填"会表现得像"模型不行"。
        if not provider:
            raise GatewayConfigError("provider 未配置")
        if not base_url:
            raise GatewayConfigError(f"{provider} 缺少 base_url")
        if not model:
            raise GatewayConfigError(f"{provider} 缺少模型 ID —— 不猜,请显式配置")
        if not api_key:
            raise GatewayConfigError(f"{provider} 缺少 API Key(架构 §13.6:经环境变量注入)")

        self._provider = provider
        self._model = model
        self._timeout_seconds = timeout_seconds
        # 端点自己拼成绝对 URL,不依赖客户端的 base_url —— 否则外部注入一个
        # base_url 不同的客户端时,请求会**静默打到别的主机**上。
        self._base_url = base_url.rstrip("/")
        # 密钥只存在于这里,不进日志、不进异常消息
        self._headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }
        self._owns_client = client is None
        self._client = client or httpx.Client(timeout=httpx.Timeout(timeout_seconds))

    @property
    def provider(self) -> str:
        return self._provider

    @property
    def model(self) -> str:
        return self._model

    def close(self) -> None:
        """只关闭自己建的客户端 —— 外部注入的客户端归外部管。"""
        if self._owns_client:
            self._client.close()

    def __repr__(self) -> str:
        """只列可公开字段,杜绝把 headers 带进日志。"""
        return f"<{type(self).__name__} provider={self._provider} model={self._model}>"

    def _url(self, path: str) -> str:
        return f"{self._base_url}{path}"

    def _post(self, path: str, payload: dict[str, Any]) -> Any:
        try:
            response = self._client.post(self._url(path), json=payload, headers=self._headers)
        except httpx.TimeoutException as error:
            raise GatewayTimeout(
                f"{self._provider} 调用超时(>{self._timeout_seconds}s)"
            ) from error
        except httpx.HTTPError as error:
            # 只用类型名,不用 str(error) —— 它可能带上完整 URL 与请求头
            raise ProviderError(f"{self._provider} 连接失败:{type(error).__name__}") from error
        return self._decode(response)

    def _decode(self, response: httpx.Response) -> Any:
        if response.status_code >= 400:
            raise ProviderError(
                f"{self._provider} 返回 {response.status_code}:"
                f"{response.text[:_ERROR_SNIPPET_LIMIT]}",
                status_code=response.status_code,
            )
        try:
            return response.json()
        except ValueError as error:
            raise InvalidProviderResponse(f"{self._provider} 响应不是合法 JSON") from error

    def _stream_lines(self, path: str, payload: dict[str, Any]) -> Iterator[str]:
        try:
            with self._client.stream(
                "POST", self._url(path), json=payload, headers=self._headers
            ) as response:
                if response.status_code >= 400:
                    response.read()
                    raise ProviderError(
                        f"{self._provider} 返回 {response.status_code}:"
                        f"{response.text[:_ERROR_SNIPPET_LIMIT]}",
                        status_code=response.status_code,
                    )
                yield from response.iter_lines()
        except httpx.TimeoutException as error:
            raise GatewayTimeout(
                f"{self._provider} 流式调用超时(>{self._timeout_seconds}s)"
            ) from error
        except httpx.HTTPError as error:
            raise ProviderError(f"{self._provider} 连接失败:{type(error).__name__}") from error


class OpenAICompatibleLLM(_OpenAICompatibleBase):
    """OpenAI 兼容的对话/工具调用适配器(架构 §7.1 的 LLMGateway)。"""

    CHAT_PATH = "/chat/completions"

    def chat(
        self,
        messages: Sequence[Message],
        tools: Sequence[ToolDef] | None = None,
        stream: bool = False,
    ) -> Reply | Iterator[ReplyChunk]:
        payload = build_chat_payload(
            model=self._model, messages=messages, tools=tools, stream=stream
        )
        if stream:
            return iter_stream_chunks(self._stream_lines(self.CHAT_PATH, payload))
        return parse_chat_response(
            self._post(self.CHAT_PATH, payload), provider=self._provider
        )

    def chat_with_tools(
        self,
        messages: Sequence[Message],
        tool_defs: Sequence[ToolDef],
    ) -> ToolCallPlan:
        """要求模型做工具调用决策。

        模型**可能一个工具都不调**(它认为不需要)—— 那是合法结果,不是错误,
        故返回空的 `ToolCallPlan` 而非抛异常。是否改问法是调用方的策略。
        """
        reply = self.chat(messages, tools=tool_defs)
        assert isinstance(reply, Reply)  # stream=False 时必然如此
        return to_tool_call_plan(reply)


class OpenAICompatibleImageGen(_OpenAICompatibleBase):
    """OpenAI 兼容的图生图/文生图适配器(架构 §7.1 的 ImageGenGateway)。

    ⚠️ 请求与响应的字段形态**待与百炼文档核实**,见本节顶部的说明。
    """

    def generate(
        self,
        prompt: str,
        ref_images: Sequence[str] | None = None,
        size: str | None = None,
        n: int = 1,
        seed: int | None = None,
    ) -> Images:
        payload = build_image_payload(
            model=self._model,
            prompt=prompt,
            ref_images=ref_images,
            size=size,
            n=n,
            seed=seed,
        )
        return parse_image_response(
            self._post(_IMAGE_ENDPOINT, payload), provider=self._provider
        )
