"""T-1.11:Java 取数客户端、两个 Source 适配器、替身图像网关。

**纯单测**:HTTP 用 `httpx.MockTransport` 拦下来,一个字节都不出网。

对照任务卡的验收里能在这层证明的部分:
- 取数失败**降级不抛** —— 消费端是个长驻循环,一次网络抖动不该让它整批停顿
- 业务失败看响应体的 `code`,**不看 HTTP 状态码**(契约:T-13 只有 401/403 用真实状态码)
- 参考图来自 `Sku.coverImage` / `Sku.images[]`
"""

from __future__ import annotations

import json

import httpx
import pytest

from app.agent.prompt import DEFAULT_VIEW, VIEW_PHRASES, FurnishConfig
from app.agent.prompt_sources import HttpFurnishSource, HttpSkuSource
from app.clients.java_api import CODE_TASK_NOT_FOUND, JavaApiClient
from app.core.config import settings
from app.gateway.factory import build_image_gateway
from app.gateway.placeholder_image import PLACEHOLDER_PATH, PlaceholderImageGen, view_of_prompt

# ----------------------------------------------------------------------
# HTTP 替身
# ----------------------------------------------------------------------

def envelope(code: int = 0, data=None, message: str = "ok") -> httpx.Response:
    return httpx.Response(200, json={"code": code, "message": message, "data": data,
                                     "traceId": "t-1"})


class Recorder:
    """把请求记下来,便于断言"带了哪些头 / 打到哪个路径"。"""

    def __init__(self, handler) -> None:
        self.requests: list[httpx.Request] = []
        self._handler = handler

    def __call__(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        return self._handler(request)

    @property
    def paths(self) -> list[str]:
        return [request.url.path for request in self.requests]


def client_with(handler, **kwargs) -> JavaApiClient:
    transport = httpx.MockTransport(Recorder(handler) if not isinstance(handler, Recorder) else handler)
    return JavaApiClient(
        base_url="http://java.test",
        service_token="tok",
        client=httpx.Client(transport=transport),
        **kwargs,
    )


# ======================================================================
# 取数
# ======================================================================

def test_get_product_passes_token_and_trace_id() -> None:
    recorder = Recorder(lambda request: envelope(data={"sku": "TL-1", "coverImage": "u"}))
    client = client_with(recorder)

    data = client.get_product("TL-1", trace_id="trace-abc")

    assert data == {"sku": "TL-1", "coverImage": "u"}
    assert recorder.paths == ["/internal/products/TL-1"], "契约路径不带 /api/v1 前缀"
    assert recorder.requests[0].headers["X-Service-Token"] == "tok"
    assert recorder.requests[0].headers["X-Trace-Id"] == "trace-abc"


def test_business_failure_is_read_from_the_body_not_the_http_status() -> None:
    """契约:业务失败也是 HTTP 200。按状态码判成败会把「SKU 已下架」当成成功,
    然后 `data` 是 null,下游拿到一个空壳。"""
    client = client_with(lambda request: envelope(code=30001, data=None, message="SKU 不存在"))

    assert client.get_product("TL-GONE") is None


def test_real_401_is_also_swallowed_into_none() -> None:
    """鉴权失败同样只返回 None —— 本层的契约是"不抛进消费循环"。"""
    client = client_with(
        lambda request: httpx.Response(401, json={"code": 10002, "message": "未认证", "data": None})
    )

    assert client.get_product("TL-1") is None


def test_network_error_does_not_raise() -> None:
    def boom(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    assert client_with(boom).get_product("TL-1") is None


def test_non_json_body_does_not_raise() -> None:
    """网关挡在前面时返回的是 HTML 错误页,不是信封。"""
    client = client_with(lambda request: httpx.Response(502, text="<html>Bad Gateway</html>"))

    assert client.get_product("TL-1") is None


def test_empty_sku_is_not_requested_at_all() -> None:
    recorder = Recorder(lambda request: envelope(data={}))

    assert client_with(recorder).get_product("") is None
    assert recorder.requests == [], "空 sku 问不出东西,不必浪费一次往返"


def test_furnish_prompts_passes_space_and_style() -> None:
    recorder = Recorder(lambda request: envelope(data={"prompt": "p", "furnishConfigured": True}))
    client = client_with(recorder)

    data = client.get_furnish_prompts("bathroom", "modern")

    assert data["prompt"] == "p"
    assert recorder.paths == ["/internal/furnish/prompts"]
    assert recorder.requests[0].url.params["space"] == "bathroom"
    assert recorder.requests[0].url.params["style"] == "modern"


def test_no_token_configured_means_no_header() -> None:
    """令牌留空时不发这个头:Java 侧对空令牌是「放行 + 告警」,
    带一个空字符串反而会走到「令牌无效」分支上去。"""
    recorder = Recorder(lambda request: envelope(data={}))
    client = JavaApiClient(
        base_url="http://java.test", service_token="",
        client=httpx.Client(transport=httpx.MockTransport(recorder)),
    )

    client.get_product("TL-1")

    assert "X-Service-Token" not in recorder.requests[0].headers


# ======================================================================
# 回传
# ======================================================================

def test_callback_accepted() -> None:
    client = client_with(lambda request: envelope())

    result = client.send_callback({"taskId": "g_1", "status": "success"})

    assert result.accepted is True
    assert result.code == 0


def test_callback_task_not_found_is_distinguishable() -> None:
    """40003 与"没送达"必须能分开:前者是**终局**(别重试),后者是瞬态。"""
    client = client_with(lambda request: envelope(code=CODE_TASK_NOT_FOUND, message="任务不存在"))

    result = client.send_callback({"taskId": "g_1", "status": "running"})

    assert result.accepted is False
    assert result.code == CODE_TASK_NOT_FOUND


def test_callback_network_error_is_transient() -> None:
    def boom(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    result = client_with(boom).send_callback({"taskId": "g_1", "status": "running"})

    assert result.accepted is False
    assert result.code == 0, "code=0 表示「不是任务不存在」,调用方据此继续而不是放弃"


def test_callback_posts_json_body_verbatim() -> None:
    recorder = Recorder(lambda request: envelope())
    body = {"taskId": "g_1", "status": "success", "progress": 100,
            "imageUrls": [{"url": "u", "view": "default", "isCover": True}]}

    client_with(recorder).send_callback(body, trace_id="trace-abc")

    sent = json.loads(recorder.requests[0].content)
    assert sent == body
    assert recorder.paths == ["/internal/generate/callback"]


# ======================================================================
# Source 适配器:按任务缓存
# ======================================================================

def test_sku_source_asks_java_only_once_per_sku() -> None:
    """一块砖铺两个部位时只请求一次 Java。"""
    recorder = Recorder(lambda request: envelope(data={"sku": "TL-1"}))
    source = HttpSkuSource(client_with(recorder))

    source.get("TL-1")
    source.get("TL-1")
    source.get("TL-1")

    assert len(recorder.requests) == 1


def test_sku_source_caches_the_missing_answer_too() -> None:
    """**取不到的也要缓存** —— 否则一块已下架的砖会被每个引用它的部位各问一次,
    而答案每次都一样是「没有」。"""
    recorder = Recorder(lambda request: envelope(code=30001, data=None))
    source = HttpSkuSource(client_with(recorder))

    assert source.get("TL-GONE") is None
    assert source.get("TL-GONE") is None

    assert len(recorder.requests) == 1


def test_furnish_source_reads_configured_prompt() -> None:
    source = HttpFurnishSource(client_with(lambda request: envelope(data={
        "prompt": "浅灰布艺沙发", "coverImage": "https://oss.test/f.jpg", "furnishConfigured": True,
    })))

    config = source.get("bathroom", "modern")

    assert config == FurnishConfig(prompt="浅灰布艺沙发", cover_image="https://oss.test/f.jpg",
                                   configured=True)


def test_furnish_source_degrades_when_not_configured() -> None:
    source = HttpFurnishSource(client_with(lambda request: envelope(data={
        "prompt": "", "coverImage": None, "furnishConfigured": False,
    })))

    config = source.get("bathroom", "modern")

    assert config.configured is False
    assert config.prompt == ""


def test_furnish_source_degrades_when_java_is_unreachable() -> None:
    """取不到陈设只是**画质问题**,不是功能问题(§13.3)。"""
    def boom(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    config = HttpFurnishSource(client_with(boom)).get("bathroom", "modern")

    assert config.configured is False


def test_furnish_source_caches_per_space_and_style_pair() -> None:
    """缓存键必须是 `(space, style)`:按 space 做键会把 A 风格的陈设用到 B 风格上。"""
    recorder = Recorder(lambda request: envelope(data={"prompt": "p", "furnishConfigured": True}))
    source = HttpFurnishSource(client_with(recorder))

    source.get("bathroom", "modern")
    source.get("bathroom", "modern")
    source.get("bathroom", "nordic")

    assert len(recorder.requests) == 2


# ======================================================================
# 替身图像网关
# ======================================================================

def test_placeholder_returns_exactly_n_images() -> None:
    """§30.3:替身返回的张数 = 请求的 n。"""
    gateway = PlaceholderImageGen(base_url="http://java.test/")

    assert len(gateway.generate("p", n=1).images) == 1
    assert len(gateway.generate("p", n=3).images) == 3


def test_placeholder_urls_point_at_java() -> None:
    """图必须由 **Java** 提供 —— 前端只连 Java(不变量 3)。

    ⚠️ 提示词用 ``VIEW_PHRASES`` **拼出来**,不写死字面量 ——
    2026-10-07 就因为这里写死了旧措辞("正面平视全景…"),而 `VIEW_PHRASES` 的
    措辞随白模机位改成了"略微向下俯视"(§77),两条用例当场红。
    引用常量之后,改措辞不会再漂。
    """
    gateway = PlaceholderImageGen(base_url="http://java.test/")

    url = gateway.generate(f"视角：{VIEW_PHRASES[DEFAULT_VIEW]}。").images[0].url

    assert url == f"http://java.test{PLACEHOLDER_PATH}?v=default"


def test_placeholder_defaults_to_java_base_url() -> None:
    """留空即「跟 Java 同源」—— 这是默认值,也是唯一正确的部署形态。"""
    url = PlaceholderImageGen().generate("任意提示词").images[0].url

    assert url.startswith(settings.java_base_url.rstrip("/") + PLACEHOLDER_PATH)


def test_placeholder_carries_the_view_in_the_url() -> None:
    """`?v=` 就是视角名,与 Java 的封面规则(`view == default`)对得上,肉眼也能核。"""
    gateway = PlaceholderImageGen(base_url="http://java.test")
    # 同样引用常量,不写死 —— 见上一条用例的说明。
    prompts = {
        "default": VIEW_PHRASES[DEFAULT_VIEW],
        "angle1": VIEW_PHRASES["angle1"],
    }

    urls = [gateway.generate(phrase).images[0].url for phrase in prompts.values()]

    assert urls[0].endswith("?v=default")
    assert urls[1].endswith("?v=angle1")


def test_placeholder_distinct_urls_within_one_view() -> None:
    """同一视角的 n 张必须地址不同,否则 Java 落库就是 N 条一模一样的记录。"""
    urls = [image.url for image in PlaceholderImageGen(base_url="http://j").generate("p", n=3).images]

    assert len(set(urls)) == 3


def test_unknown_view_falls_back_to_a_distinct_digest() -> None:
    """表外的机位(正常路径走不到)也不能都退成 default —— 那会让两个机位
    指向同一张图。"""
    a = view_of_prompt("视角：换一个拍摄角度。")
    b = view_of_prompt("视角：换一个拍摄角度。再加一句不同的话。")

    assert a != b
    assert a != "default"


def test_factory_returns_placeholder_when_configured() -> None:
    class FakeSettings:
        model_image_provider = "placeholder"
        java_base_url = "http://java.test"

    gateway = build_image_gateway(FakeSettings())

    assert isinstance(gateway, PlaceholderImageGen)


def test_factory_still_rejects_unknown_provider() -> None:
    """未知 provider 一律抛错,不静默降级 —— 把 dashscope 拼错却拿到一个
    「能用的」网关,是最难查的一类故障。"""
    from app.gateway.errors import GatewayConfigError

    class FakeSettings:
        model_image_provider = "dashskope"

    with pytest.raises(GatewayConfigError):
        build_image_gateway(FakeSettings())
