"""硅基流动图像适配器:请求体构造与响应解析。

**形状照真实响应写,不是照文档写。** 2026-10-02 用真实密钥实测,官方文档里给的
参考图字段名是 `imageUpload`,照它写会直接得到
`{"code":20015,"message":"Missing required key: image"}` —— 正确字段是 `image`。
(同一个教训在 `test_gateway.image_body` 那边已经有过一次:"形状照真实响应写"。)
"""

from __future__ import annotations

import json

import httpx
import pytest

from app.gateway.errors import GatewayConfigError, InvalidProviderResponse, ProviderError
from app.gateway.siliconflow_image import (
    MAX_REFERENCE_IMAGES,
    SiliconFlowImageGen,
    build_image_payload,
    parse_image_response,
    ref_field_name,
)

MODEL = "Qwen/Qwen-Image-Edit-2509"

#: 实测响应:同时带 `images` 与 `data`,两者都是 [{"url": ...}]。
#: 另有 timings.inference(服务端自报耗时)与 seed 回显。
REF_A = "https://www.monalisa.com.cn/a.jpg"
REF_B = "https://www.monalisa.com.cn/b.jpg"


def ok_response(*urls: str) -> httpx.Response:
    return httpx.Response(200, json={
        "images": [{"url": url} for url in urls],
        "data": [{"url": url} for url in urls],
        "timings": {"inference": 28.103},
        "seed": 1940952541,
        "shared_id": "0",
    })


def gateway(handler):
    """返回 (已发送请求列表, 适配器)。"""
    sent: list[httpx.Request] = []

    def wrapped(request: httpx.Request) -> httpx.Response:
        sent.append(request)
        return handler(request)

    client = httpx.Client(transport=httpx.MockTransport(wrapped))
    return sent, SiliconFlowImageGen(
        provider="siliconflow",
        base_url="https://api.siliconflow.cn",
        model=MODEL,
        api_key="test-key",
        timeout_seconds=60.0,
        client=client,
    )


# ----------------------------------------------------------------------
# 参考图字段名 —— 这是最容易照文档写错的地方
# ----------------------------------------------------------------------

def test_first_ref_has_no_number_suffix() -> None:
    """第一张是 `image`,**没有数字后缀** —— 这是实测出来的,不是笔误。"""
    assert ref_field_name(0) == "image"
    assert ref_field_name(1) == "image2"
    assert ref_field_name(3) == "image4"


def test_payload_numbers_the_refs_as_parallel_fields() -> None:
    payload = build_image_payload(
        model=MODEL, prompt="铺这款砖", ref_images=[REF_A, REF_B], seed=None
    )

    assert payload["image"] == REF_A
    assert payload["image2"] == REF_B
    assert "imageUpload" not in payload, "文档写的字段名是错的,别再走回去"


def test_payload_omits_image_size() -> None:
    """该模型明确不接受 `image_size` —— 发了会被拒。"""
    payload = build_image_payload(model=MODEL, prompt="x", ref_images=[REF_A], seed=None)

    assert "image_size" not in payload
    assert "size" not in payload


def test_seed_only_sent_when_given() -> None:
    without = build_image_payload(model=MODEL, prompt="x", ref_images=None, seed=None)
    with_seed = build_image_payload(model=MODEL, prompt="x", ref_images=None, seed=42)

    assert "seed" not in without
    assert with_seed["seed"] == 42


def test_more_than_four_refs_are_trimmed_not_fatal() -> None:
    """超过上限**截断并告警,不报错**。

    出图编排层会把产品的"封面 + 所有图"都传下来(实测每款 5 张左右)——
    **产品多几张照片是正常情况**,为它让整次生成失败,是把一个可以降级的小事
    变成了故障。封面排在最前,所以丢掉的一定是后面那些备用角度图。
    """
    refs = [f"https://x/{index}.jpg" for index in range(6)]

    payload = build_image_payload(model=MODEL, prompt="x", ref_images=refs, seed=None)

    assert payload["image"] == refs[0], "封面必须留下"
    assert payload["image4"] == refs[MAX_REFERENCE_IMAGES - 1]
    assert "image5" not in payload


def test_no_refs_is_text_to_image() -> None:
    """不给参考图就是纯文生图 —— 同一个端点,**不是**另一条路径。"""
    payload = build_image_payload(model=MODEL, prompt="现代客厅", ref_images=None, seed=None)

    assert payload["prompt"] == "现代客厅"
    assert not [key for key in payload if key.startswith("image")]


# ----------------------------------------------------------------------
# 响应解析
# ----------------------------------------------------------------------

def test_parses_images_key() -> None:
    images = parse_image_response({"images": [{"url": "https://s3/a.png"}]}, provider="siliconflow")

    assert [image.url for image in images.images] == ["https://s3/a.png"]


def test_falls_back_to_data_key() -> None:
    """只认 `images` 的话,厂商哪天真删了它就会**静默出空图**。"""
    images = parse_image_response({"data": [{"url": "https://s3/b.png"}]}, provider="siliconflow")

    assert [image.url for image in images.images] == ["https://s3/b.png"]


def test_empty_response_raises_with_vendor_detail() -> None:
    with pytest.raises(InvalidProviderResponse) as error:
        parse_image_response({"code": 20015, "message": "Missing required key: image"},
                             provider="siliconflow")

    assert "Missing required key" in str(error.value), "厂商给的原因要带出来,便于排查"


def test_non_object_response_raises() -> None:
    with pytest.raises(InvalidProviderResponse):
        parse_image_response(["not", "an", "object"], provider="siliconflow")


# ----------------------------------------------------------------------
# generate 端到端(打桩)
# ----------------------------------------------------------------------

def test_generate_posts_to_the_documented_path_with_bearer_auth() -> None:
    sent, adapter = gateway(lambda _request: ok_response("https://s3/out.png"))

    images = adapter.generate("把地面换成这款砖", ref_images=[REF_A, REF_B])

    assert [image.url for image in images.images] == ["https://s3/out.png"]
    assert str(sent[0].url) == "https://api.siliconflow.cn/v1/images/generations"
    assert sent[0].headers["authorization"] == "Bearer test-key"

    body = json.loads(sent[0].content)
    assert body["model"] == MODEL
    assert body["image"] == REF_A
    assert body["image2"] == REF_B


def test_n_greater_than_one_fails_loudly() -> None:
    """该端点一次只出一张。**不能静默只给一张** —— 调用方以为要了 n 张却不报错,
    属于"付了钱没拿到东西"那类问题。"""
    _, adapter = gateway(lambda _request: ok_response("https://s3/out.png"))

    with pytest.raises(GatewayConfigError):
        adapter.generate("x", n=2)


def test_client_error_becomes_provider_error() -> None:
    _, adapter = gateway(lambda _request: httpx.Response(400, json={"message": "bad"}))

    with pytest.raises(ProviderError) as error:
        adapter.generate("x")

    assert error.value.status_code == 400


def test_response_carries_no_usage() -> None:
    """该端点**不回 usage** —— 成本只能按张数算。

    钉住这一条,是为了让后人看到 usage=0 时知道**不是自己解析漏了**。
    """
    _, adapter = gateway(lambda _request: ok_response("https://s3/out.png"))

    images = adapter.generate("x")

    assert images.usage.prompt_tokens == 0
    assert images.usage.completion_tokens == 0
