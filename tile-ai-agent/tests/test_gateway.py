"""T-1.5:模型网关(架构 §7.1 / §7.2 / §13.6 / §13.7)。

**纯单测,不需要任何外部服务**:出网替身用 `httpx.MockTransport`,
时钟、sleep、抖动全部注入 —— 所以断超时与退避不必真等。

真实的 Redis 令牌桶与 Qdrant 接缝另见 `test_gateway_integration.py`。
"""

from __future__ import annotations

import itertools
import json
import logging

import httpx
import pytest

from app.core.config import Settings
from app.gateway.errors import (
    CircuitOpen,
    GatewayConfigError,
    GatewayTimeout,
    ProviderError,
    RateLimited,
    counts_toward_failure_rate,
)
from app.gateway.failover import FailoverLLMGateway
from app.gateway.factory import build_embedding_gateway, build_image_gateway, build_llm_gateway
from app.gateway.dashscope_image import build_image_payload, parse_image_response
from app.gateway.openai_compat import (
    OpenAICompatibleLLM,
    build_chat_payload,
    iter_stream_chunks,
    parse_chat_response,
)
from app.gateway.ports import Message, Reply, ToolCall, ToolCallPlan, ToolDef
from app.gateway.resilience import (
    CircuitBreaker,
    CostMeter,
    FailureRateTracker,
    RetryPolicy,
)

SENTINEL_KEY = "sk-SENTINEL-do-not-log-me-1234567890"


# ----------------------------------------------------------------------
# 替身与工具
# ----------------------------------------------------------------------

class AllowAllRedis:
    """令牌桶替身:永远放行。纯单测里不碰真 Redis。"""

    def eval(self, *_args, **_kwargs):
        return 1


class DenyAllRedis:
    """令牌桶替身:永远拒绝 —— 用来验证限流路径。"""

    def eval(self, *_args, **_kwargs):
        return 0


class CountingRedis:
    """记录 eval 调用的替身,用来断言"限流按供应商分开计数"。"""

    def __init__(self) -> None:
        self.keys: list[str] = []

    def eval(self, _script, _numkeys, key, *_args):
        self.keys.append(key)
        return 1


def settings(**overrides) -> Settings:
    """构造测试用配置。

    `_env_file=None` 是必须的 —— 否则本机若存在 .env,测试结果会随开发者的
    本地文件变化,同一份代码在两个人机器上结论不同。
    """
    base = {
        "model_llm_provider": "deepseek",
        "model_llm_api_key": SENTINEL_KEY,
        "model_image_provider": "dashscope",
        "model_image_api_key": "img-key",
        "gateway_model_prices": {},
    }
    base.update(overrides)
    return Settings(_env_file=None, **base)


def client_with(handler) -> httpx.Client:
    return httpx.Client(transport=httpx.MockTransport(handler), base_url="https://fake.test")


def reply_body(content: str = "好的", *, prompt_tokens: int = 10, completion_tokens: int = 5,
               tool_calls=None, model: str = "deepseek-flash") -> dict:
    message: dict = {"role": "assistant", "content": content}
    if tool_calls is not None:
        message["tool_calls"] = tool_calls
    return {
        "model": model,
        "choices": [{"index": 0, "message": message}],
        "usage": {
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": prompt_tokens + completion_tokens,
        },
    }


def image_body(*urls: str) -> dict:
    """DashScope 原生响应:`n>1` 时每张图各占一个 choice(实测确认)。

    ⚠️ 形状照**真实响应**写,不是照我以为的写 —— 原先这里是 `{"data": [...]}`,
    那是按 OpenAI 兼容**推断**的,实测后作废。
    """
    return {
        "output": {
            "choices": [
                {"finish_reason": "stop",
                 "message": {"role": "assistant", "content": [{"image": url, "type": "image"}]}}
                for url in urls
            ],
            "finished": True,
        },
        "usage": {"image_count": len(urls), "input_tokens": 29, "output_tokens": 2,
                  "size": "1024*1024", "total_tokens": 31},
    }


def recording_gateway(handler):
    """返回 (gateway, 已发送请求列表)。列表在断言里用来数"到底发了几次"。"""
    sent: list[httpx.Request] = []

    def wrapped(request: httpx.Request) -> httpx.Response:
        sent.append(request)
        return handler(request)

    return sent, client_with(wrapped)


def build_llm(**overrides):
    """构造网关;默认关掉真实等待与抖动,让重试类断言可复现。"""
    overrides.setdefault("sleep", lambda _seconds: None)
    overrides.setdefault("jitter_source", lambda: 0.0)
    overrides.setdefault("redis_client", AllowAllRedis())
    return build_llm_gateway(settings(**overrides.pop("settings_overrides", {})), **overrides)


# ----------------------------------------------------------------------
# §7.2 验收:配置切换可生效
# ----------------------------------------------------------------------

def test_provider_switch_changes_endpoint():
    """换 provider 真的换端点 —— 可插拔性的行为证据,不是类型注解自证。"""
    sent, client = recording_gateway(lambda _r: httpx.Response(200, json=reply_body()))
    build_llm_gateway(
        settings(model_llm_provider="deepseek"),
        client=client, redis_client=AllowAllRedis(), sleep=lambda _s: None,
    ).chat([Message(role="user", content="hi")])
    assert sent[-1].url.host == "api.deepseek.com"

    sent2, client2 = recording_gateway(lambda _r: httpx.Response(200, json=reply_body()))
    build_llm_gateway(
        settings(model_llm_provider="zhipu", model_llm_model="glm-any"),
        client=client2, redis_client=AllowAllRedis(), sleep=lambda _s: None,
    ).chat([Message(role="user", content="hi")])
    assert sent2[-1].url.host == "open.bigmodel.cn"


def test_unknown_provider_raises_instead_of_degrading():
    """拼错 provider 必须响 —— 静默返回一个"能用"的网关是最难查的故障。"""
    with pytest.raises(GatewayConfigError, match="未知"):
        build_llm(settings_overrides={"model_llm_provider": "deepsek"})


def test_rate_limiter_key_is_per_provider():
    """主力和备份是两份独立额度,不能共用一把桶。"""
    redis = CountingRedis()
    sent, client = recording_gateway(lambda _r: httpx.Response(200, json=reply_body()))

    gateway = build_llm_gateway(
        settings(model_llm_provider="deepseek", model_llm_backup_provider="zhipu",
                 model_llm_backup_api_key="k", model_llm_backup_model="glm-any"),
        client=client, redis_client=redis, sleep=lambda _s: None,
    )
    gateway.chat([Message(role="user", content="hi")])
    assert "gateway:ratelimit:deepseek" in redis.keys
    # 主力成功,不该动到备份那把桶
    assert "gateway:ratelimit:zhipu" not in redis.keys


# ----------------------------------------------------------------------
# §7.1 签名:chat / chatWithTools / generate
# ----------------------------------------------------------------------

def test_chat_maps_request_and_response():
    sent, client = recording_gateway(lambda _r: httpx.Response(200, json=reply_body("浅灰木纹")))
    gateway = build_llm(client=client)

    reply = gateway.chat([Message(role="user", content="推荐一款地砖")])

    assert isinstance(reply, Reply)
    assert reply.content == "浅灰木纹"
    assert reply.usage.prompt_tokens == 10
    assert reply.usage.completion_tokens == 5

    # 用 JSON 反解而不是字符串包含 —— httpx 会把非 ASCII 转义成 \uXXXX,
    # 直接搜中文会假失败
    body = json.loads(sent[-1].read().decode("utf-8"))
    assert body["model"] == "deepseek-flash"
    assert body["messages"] == [{"role": "user", "content": "推荐一款地砖"}]


def test_chat_with_tools_returns_plan():
    tool_calls = [{"id": "call_1", "type": "function",
                   "function": {"name": "search_products", "arguments": '{"space":"bathroom"}'}}]
    _, client = recording_gateway(lambda _r: httpx.Response(200, json=reply_body("", tool_calls=tool_calls)))
    plan = build_llm(client=client).chat_with_tools(
        [Message(role="user", content="卫生间想铺灰砖")],
        [ToolDef(name="search_products", description="检索产品库", parameters={"type": "object"})],
    )

    assert len(plan.tool_calls) == 1
    assert plan.tool_calls[0].name == "search_products"
    # 参数保留供应商原文,不在网关层解析 —— 该工具怎么解读是调用方的事
    assert plan.tool_calls[0].arguments == '{"space":"bathroom"}'


def test_chat_with_tools_returning_none_is_not_an_error():
    """模型认为不需要调工具是合法结果,不是故障。"""
    _, client = recording_gateway(lambda _r: httpx.Response(200, json=reply_body("直接回答你就行")))
    plan = build_llm(client=client).chat_with_tools(
        [Message(role="user", content="你好")],
        [ToolDef(name="t", description="d")],
    )
    assert plan.tool_calls == ()


def test_image_generate_accepts_four_reference_images():
    """§15.3 硬约束:底图 + 地面/墙面/局部三张切片 = 至少 4 张参考图。

    这条防的是"换了个参考图上限装不下的模型" —— 那种模型装不下生产路径。
    **实测(2026-09-29)该模型上限 9 张,4 张确实被接受。**
    """
    sent, client = recording_gateway(
        lambda _r: httpx.Response(200, json=image_body("https://oss/e1.png"))
    )
    refs = [
        "https://oss/room-base.png",   # 同空间类型底图
        "https://oss/sku-floor.png",   # 地面
        "https://oss/sku-wall.png",    # 墙面
        "https://oss/sku-local.png",   # 局部
    ]
    images = build_image_gateway(settings(), client=client, redis_client=AllowAllRedis(),
                                 sleep=lambda _s: None).generate("卫生间 现代风", ref_images=refs)

    assert len(images.images) == 1
    body = json.loads(sent[-1].read().decode("utf-8"))

    # DashScope 原生形态:input.messages[0].content = [恰好 1 个 text] + [0..9 个 image]
    content = body["input"]["messages"][0]["content"]
    assert sum(1 for item in content if "text" in item) == 1, "content 里必须恰好一个 text 对象"
    sent_refs = [item["image"] for item in content if "image" in item]
    assert sent_refs == refs, f"四张参考图必须按序进 content:{sent_refs}"
    assert body["model"] == "wan2.7-image-pro"
    assert "parameters" in body


def test_image_payload_is_dashscope_native_not_openai():
    """形态是 DashScope 原生 —— **不是** OpenAI 兼容。

    这条钉住 2026-09-29 实测纠正的一个错误假设:架构 §7.2 原写"适配器统一走
    OpenAI 兼容协议",而图像实际走原生端点与嵌套 messages 结构。
    若有人"顺手"改回 `{prompt, image_urls}` 形态,这里会红。
    """
    payload = build_image_payload(model="m", prompt="p", ref_images=["https://oss/a.png"],
                                  size="1K", n=1)

    assert "input" in payload and "messages" in payload["input"]
    assert "prompt" not in payload, "DashScope 原生协议用的是 input.messages,不是顶层 prompt"
    assert "image_urls" not in payload, "参考图是 content 里的 {image} 对象,不是 image_urls 字段"


def test_image_payload_rejects_more_than_the_documented_limit():
    """超过上限时**早失败并说清楚**,而不是让服务端回一个含义模糊的 4xx。"""
    too_many = [f"https://oss/{i}.png" for i in range(10)]  # 文档上限 9
    with pytest.raises(GatewayConfigError, match="最多接受 9 张"):
        build_image_payload(model="m", prompt="p", ref_images=too_many, size=None, n=1)


def test_image_request_carries_size_and_n():
    payload = build_image_payload(model="m", prompt="p", ref_images=None,
                                  size="2K", n=2)
    assert payload["parameters"]["size"] == "2K"
    assert payload["parameters"]["n"] == 2


def test_parse_image_response_walks_every_choice():
    """`n>1` 时**每张图各占一个 choice** —— 只读 choices[0] 会少拿图却不报错。

    实测确认:usage 记了 image_count=2,而 choices 数组有两个元素。
    "付了钱没拿到东西"是最不该出现的一类错。
    """
    data = {
        "output": {"choices": [
            {"message": {"content": [{"image": "https://oss/1.png", "type": "image"}]}},
            {"message": {"content": [{"image": "https://oss/2.png", "type": "image"}]}},
        ], "finished": True},
        "usage": {"image_count": 2, "input_tokens": 52, "output_tokens": 4},
    }
    images = parse_image_response(data, provider="dashscope")

    assert [image.url for image in images.images] == ["https://oss/1.png", "https://oss/2.png"]
    # 用量取的是 input/output tokens(参考图会吃掉大量视觉 token)
    assert images.usage.prompt_tokens == 52


def test_parse_image_response_rejects_body_without_images():
    """没有任何图片时报错并带上厂商给的原因 —— 否则"要 1 张却拿到 0 张"是无线索的谜。"""
    from app.gateway.errors import InvalidProviderResponse

    with pytest.raises(InvalidProviderResponse, match="没有任何图片"):
        parse_image_response({"output": {"choices": [], "code": "InvalidParameter"}},
                             provider="dashscope")


def test_stream_yields_deltas_then_done():
    sse = (
        'data: {"model":"deepseek-flash","choices":[{"delta":{"content":"浅"}}]}\n'
        "\n"
        'data: {"model":"deepseek-flash","choices":[{"delta":{"content":"灰"}}]}\n'
        "\n"
        ": keep-alive\n"
        "\n"
        'data: {"model":"deepseek-flash","choices":[],"usage":{"prompt_tokens":7,"completion_tokens":2}}\n'
        "\n"
        "data: [DONE]\n"
        "\n"
    )
    _, client = recording_gateway(lambda _r: httpx.Response(200, text=sse,
                                                            headers={"content-type": "text/event-stream"}))
    chunks = list(build_llm(client=client).chat([Message(role="user", content="hi")], stream=True))

    assert "".join(c.delta for c in chunks) == "浅灰"
    assert chunks[-1].done is True
    # usage 只认最后一帧 —— 按帧累加会重复计数
    assert chunks[-2].usage is not None
    assert chunks[-2].usage.prompt_tokens == 7


def test_stream_parsing_skips_unparseable_lines():
    """供应商会插心跳与注释行,不该为一行噪音炸掉整条流。"""
    chunks = list(iter_stream_chunks([
        "event: ping", 'data: {"choices":[{"delta":{"content":"a"}}]}', "data: 不是JSON",
        "data: [DONE]",
    ]))
    assert "".join(c.delta for c in chunks) == "a"
    assert chunks[-1].done


def test_build_chat_payload_requests_usage_when_streaming():
    """流式不显式要 usage,最后一帧就没有用量,成本计量会变成瞎账。"""
    payload = build_chat_payload(model="m", messages=[Message(role="user", content="x")], stream=True)
    assert payload["stream_options"] == {"include_usage": True}


# ----------------------------------------------------------------------
# §7.2 超时与重试
# ----------------------------------------------------------------------

def test_timeout_becomes_gateway_timeout_after_retries():
    def handler(_request):
        raise httpx.ReadTimeout("too slow")

    sent, client = recording_gateway(handler)
    gateway = build_llm(client=client, settings_overrides={"gateway_max_attempts": 3})

    with pytest.raises(GatewayTimeout):
        gateway.chat([Message(role="user", content="hi")])
    assert len(sent) == 3, "超时属瞬态,应重试到上限"


def test_too_many_requests_is_retried_and_then_succeeds():
    calls = itertools.count()

    def handler(_request):
        if next(calls) == 0:
            return httpx.Response(429, text="slow down")
        return httpx.Response(200, json=reply_body("恢复了"))

    sent, client = recording_gateway(handler)
    reply = build_llm(client=client).chat([Message(role="user", content="hi")])

    assert reply.content == "恢复了"
    assert len(sent) == 2


def test_client_error_is_not_retried():
    """4xx 是我们发错了,重试不会变对 —— 只该发一次。"""
    sent, client = recording_gateway(lambda _r: httpx.Response(400, text="bad request"))

    with pytest.raises(ProviderError) as caught:
        build_llm(client=client).chat([Message(role="user", content="hi")])

    assert caught.value.status_code == 400
    assert caught.value.is_transient() is False
    assert len(sent) == 1, "4xx 不该重试"


def test_retry_backoff_never_shortens():
    """抖动只向上加 —— 退避时长的下界是对下游的尊重,不能被"打散"破坏。"""
    policy = RetryPolicy(base_delay_seconds=1.0, max_delay_seconds=8.0, jitter_ratio=0.25)
    assert policy.delay_for(1, jitter=0.0) == 1.0
    assert policy.delay_for(1, jitter=1.0) == 1.25
    assert policy.delay_for(2, jitter=0.0) == 2.0
    assert policy.delay_for(10, jitter=0.0) == 8.0, "应被上限截断"


# ----------------------------------------------------------------------
# §7.2 熔断
# ----------------------------------------------------------------------

def test_circuit_opens_and_stops_sending_requests():
    sent, client = recording_gateway(lambda _r: httpx.Response(500, text="boom"))
    gateway = build_llm(client=client, settings_overrides={
        "gateway_circuit_failure_threshold": 2,
        "gateway_max_attempts": 1,  # 一次调用 = 一次失败,便于数数
    })

    for _ in range(2):
        with pytest.raises(ProviderError):
            gateway.chat([Message(role="user", content="hi")])
    assert len(sent) == 2

    with pytest.raises(CircuitOpen):
        gateway.chat([Message(role="user", content="hi")])
    assert len(sent) == 2, "熔断打开后不该再发请求"


def test_circuit_half_opens_after_recovery_and_closes_on_success():
    now = [1000.0]
    state = {"fail": True}

    def handler(_request):
        if state["fail"]:
            return httpx.Response(500, text="boom")
        return httpx.Response(200, json=reply_body("好了"))

    sent, client = recording_gateway(handler)
    gateway = build_llm(
        client=client,
        clock=lambda: now[0],
        settings_overrides={"gateway_circuit_failure_threshold": 1,
                            "gateway_circuit_recovery_seconds": 30,
                            "gateway_max_attempts": 1},
    )

    with pytest.raises(ProviderError):
        gateway.chat([Message(role="user", content="hi")])
    with pytest.raises(CircuitOpen):
        gateway.chat([Message(role="user", content="hi")])

    now[0] += 31  # 冷却结束
    state["fail"] = False
    assert gateway.chat([Message(role="user", content="hi")]).content == "好了"
    assert len(sent) == 2, "半开时应放行一次探测"

    # 探测成功 → 回到 closed,后续调用照常
    assert gateway.chat([Message(role="user", content="hi")]).content == "好了"
    assert len(sent) == 3


def test_programming_error_releases_half_open_probe_slot():
    """半开名额必须归还 —— 泄漏会让熔断再也回不来,变成事实上的永久打开。"""
    breaker = CircuitBreaker(failure_threshold=1, recovery_seconds=0, half_open_probes=1,
                             clock=lambda: 0.0)
    breaker.record_failure()
    assert breaker.state == CircuitBreaker.HALF_OPEN

    breaker.before_call()          # 占用唯一名额
    with pytest.raises(CircuitOpen):
        breaker.before_call()      # 名额已满

    breaker.release()              # 归还(非下游原因的失败)
    breaker.before_call()          # 应能再次放行


def test_local_rate_limit_and_circuit_do_not_count_as_provider_failures():
    """限流与熔断是本服务的保护动作,不是供应商故障。

    若把它们算进失败率,一次限流就会顺带把主力供应商切走。
    """
    assert counts_toward_failure_rate(RateLimited("busy")) is False
    assert counts_toward_failure_rate(CircuitOpen("open")) is False
    assert counts_toward_failure_rate(ProviderError("500", status_code=500)) is True
    assert counts_toward_failure_rate(ProviderError("bad key", status_code=401)) is True
    assert counts_toward_failure_rate(ValueError("bug")) is False


def test_local_rate_limit_raises_without_retrying():
    sent, client = recording_gateway(lambda _r: httpx.Response(200, json=reply_body()))
    gateway = build_llm_gateway(settings(), client=client, redis_client=DenyAllRedis(),
                                sleep=lambda _s: None)

    with pytest.raises(RateLimited):
        gateway.chat([Message(role="user", content="hi")])
    assert len(sent) == 0, "被本地限流挡下就不该出网,也不该退避重试"


# ----------------------------------------------------------------------
# §13.7 失败率 > 10% 切换备份 / §15.1 双供应商
# ----------------------------------------------------------------------

class FakeLLM:
    """可编程的假供应商,用来把切换路径跑全而不碰网络。"""

    def __init__(self, name: str, *, broken: bool = False) -> None:
        self.name = name
        self.broken = broken
        self.calls = 0

    def chat(self, _messages, tools=None, stream=False):  # noqa: ARG002
        self.calls += 1
        if self.broken:
            raise ProviderError(f"{self.name} down", status_code=500)
        return Reply(content=f"来自 {self.name}")

    def chat_with_tools(self, _messages, _tool_defs):
        self.calls += 1
        if self.broken:
            raise ProviderError(f"{self.name} down", status_code=500)
        return ToolCallPlan(model=self.name)


def make_failover(*, primary_broken: bool = False, min_samples: int = 5,
                  threshold: float = 0.10, probe_seconds: float = 60.0, now=None):
    clock = now if now is not None else (lambda: 0.0)
    tracker = FailureRateTracker(window=20)
    primary = FakeLLM("primary", broken=primary_broken)
    backup = FakeLLM("backup")
    gateway = FailoverLLMGateway(
        primary=primary, backup=backup,
        primary_name="primary", backup_name="backup",
        tracker=tracker, threshold=threshold, min_samples=min_samples,
        probe_seconds=probe_seconds, clock=clock,
    )
    return gateway, primary, backup, tracker


def test_failure_rate_above_threshold_switches_to_backup():
    gateway, primary, backup, tracker = make_failover()

    # 5 次里 1 次失败 = 20% > 10%
    for ok in (True, True, True, True, False):
        tracker.record("primary", ok=ok)

    reply = gateway.chat([Message(role="user", content="hi")])
    assert reply.content == "来自 backup"
    assert gateway.current_provider == "backup"
    assert primary.calls == 0, "已判定切换后不该再打主力"


def test_single_failure_does_not_switch_when_samples_are_too_few():
    """样本不足不下判定 —— 否则首次调用失败(1/1 = 100%)就把主力切走。"""
    gateway, primary, _backup, tracker = make_failover()
    tracker.record("primary", ok=False)

    assert gateway.chat([Message(role="user", content="hi")]).content == "来自 primary"
    assert gateway.current_provider == "primary"


def test_probe_returns_to_primary_after_recovery():
    now = [0.0]
    gateway, primary, _backup, tracker = make_failover(probe_seconds=60, now=lambda: now[0])
    for ok in (True, True, True, True, False):
        tracker.record("primary", ok=ok)

    gateway.chat([Message(role="user", content="hi")])
    assert gateway.current_provider == "backup"

    # 主力恢复,冷却到点
    primary.broken = False
    now[0] += 61

    assert gateway.chat([Message(role="user", content="hi")]).content == "来自 primary"
    assert gateway.current_provider == "primary"
    # 旧失败样本必须清掉,否则会把主力立刻按回备份位
    assert tracker.failure_rate("primary") == 0.0


def test_failed_probe_still_answers_from_backup():
    """探测失败不能让用户吃错误 —— 这一次请求改由备份答完。"""
    now = [0.0]
    # 主力确实还坏着 —— 否则探测会成功,就测不到"探测失败"这条分支了
    gateway, primary, backup, tracker = make_failover(
        primary_broken=True, probe_seconds=60, now=lambda: now[0])
    for ok in (True, True, True, True, False):
        tracker.record("primary", ok=ok)
    gateway.chat([Message(role="user", content="hi")])

    now[0] += 61  # 到点,放一个真请求去试探主力
    reply = gateway.chat([Message(role="user", content="hi")])

    assert reply.content == "来自 backup", "探测失败时该由备份兜住"
    assert gateway.current_provider == "backup"
    assert primary.calls == 1, "应确实试探过主力一次"


# ----------------------------------------------------------------------
# §7.2 成本计量
# ----------------------------------------------------------------------

def test_cost_meter_uses_configured_prices():
    from app.core.config import ModelPrice

    _, client = recording_gateway(lambda _r: httpx.Response(
        200, json=reply_body(prompt_tokens=1_000_000, completion_tokens=1_000_000)))
    gateway = build_llm_gateway(
        settings(gateway_model_prices={
            "deepseek": ModelPrice(input_per_million=1.0, output_per_million=2.0),
        }),
        client=client, redis_client=AllowAllRedis(), sleep=lambda _s: None,
    )
    gateway.chat([Message(role="user", content="hi")])

    meter = gateway._meter  # noqa: SLF001 —— 断言记账结果,不测私有实现
    assert meter.total("deepseek") == pytest.approx(3.0)
    snapshot = meter.snapshot()["deepseek"]
    assert snapshot["prompt_tokens"] == 1_000_000
    assert snapshot["completion_tokens"] == 1_000_000


def test_cost_meter_without_price_records_zero_cost_not_a_guess():
    meter = CostMeter({})
    assert meter.record_tokens("unknown-vendor", 1000, 1000) == 0.0
    # 用量仍要记 —— 只是价钱未知,不能把用量也丢掉
    assert meter.snapshot()["unknown-vendor"]["prompt_tokens"] == 1000


def test_image_cost_counts_actual_returned_images():
    """按实际返回张数计价,不是请求的 n —— 少给一张就少算一张的钱。"""
    meter = CostMeter({"dashscope": type("P", (), {
        "input_per_million": 0.0, "output_per_million": 0.0, "per_image": 0.2})()})
    meter.record_images("dashscope", 3)
    assert meter.total("dashscope") == pytest.approx(0.6)


# ----------------------------------------------------------------------
# §13.6 密钥:不落代码、不落库、不进日志
# ----------------------------------------------------------------------

def test_secrets_never_appear_in_logs_or_repr(caplog):
    caplog.set_level(logging.DEBUG)

    def handler(_request):
        return httpx.Response(500, text="internal error")  # 触发告警级日志与异常路径

    client = client_with(handler)
    gateway = build_llm(client=client,
                        settings_overrides={"gateway_max_attempts": 2,
                                            "gateway_circuit_failure_threshold": 1})

    with pytest.raises(ProviderError) as caught:
        gateway.chat([Message(role="user", content="hi")])

    haystack = "\n".join([
        caplog.text,
        repr(gateway),
        str(caught.value),
        repr(caught.value),
    ])
    assert SENTINEL_KEY not in haystack, "密钥泄漏到日志或异常消息里了"

    # 顺带确认告警日志确实产生了 —— 否则这条断言可能只是因为什么都没记
    assert "模型调用失败" in caplog.text


def test_settings_repr_hides_secrets():
    """SecretStr 让脱敏成为结构性保证,不靠"记得别打印"这种纪律。"""
    text = repr(settings())
    assert SENTINEL_KEY not in text
    assert "**********" in text


def test_missing_api_key_raises_config_error():
    with pytest.raises(GatewayConfigError, match="API Key"):
        build_llm(settings_overrides={"model_llm_api_key": ""})


def test_missing_model_id_raises_config_error():
    """智谱的模型 ID 文档未定 —— 不填就该报错,不能替它猜一个。"""
    with pytest.raises(GatewayConfigError, match="模型 ID"):
        build_llm(settings_overrides={"model_llm_provider": "zhipu",
                                      "model_llm_model": ""})


def test_empty_provider_raises_config_error():
    with pytest.raises(GatewayConfigError, match="MODEL_LLM_PROVIDER"):
        build_llm(settings_overrides={"model_llm_provider": ""})


# ----------------------------------------------------------------------
# Embedding 插槽(模型未定,默认占位)
# ----------------------------------------------------------------------

def test_embedding_gateway_defaults_to_deterministic_placeholder():
    gateway = build_embedding_gateway(settings(index_vector_size=1024))
    assert gateway.dimension == 1024
    # 确定性是 T-0.13 判据的前提("全量重建可恢复一致索引")
    assert gateway.embed(["雅灰石纹"]) == gateway.embed(["雅灰石纹"])


def test_unknown_embedding_provider_raises_rather_than_silently_placeholder():
    with pytest.raises(GatewayConfigError, match="尚未实现"):
        build_embedding_gateway(settings(model_embedding_provider="bge-m3"))


# ----------------------------------------------------------------------
# 纯函数:解析边界
# ----------------------------------------------------------------------

def test_parse_chat_response_rejects_malformed_body():
    from app.gateway.errors import InvalidProviderResponse

    with pytest.raises(InvalidProviderResponse):
        parse_chat_response({"choices": []}, provider="deepseek")
    with pytest.raises(InvalidProviderResponse):
        parse_chat_response({}, provider="deepseek")


def test_adapter_repr_excludes_headers():
    adapter = OpenAICompatibleLLM(provider="deepseek", base_url="https://x.test",
                                  model="deepseek-flash", api_key=SENTINEL_KEY,
                                  timeout_seconds=1.0)
    try:
        assert SENTINEL_KEY not in repr(adapter)
        assert "Authorization" not in repr(adapter)
    finally:
        adapter.close()


def test_tool_def_serialises_to_openai_shape():
    tool = ToolDef(name="search", description="检索", parameters={"type": "object"})
    assert tool.to_openai()["function"]["name"] == "search"
    assert tool.to_openai()["type"] == "function"


def test_tool_call_arguments_are_kept_verbatim():
    call = ToolCall(id="c1", name="f", arguments='{"a": 1}')
    assert call.arguments == '{"a": 1}'
