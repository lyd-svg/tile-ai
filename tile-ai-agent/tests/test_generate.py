"""T-1.11:生成编排与消费端(架构 §6.2、§6.4、§11.2)。

**纯单测**:取数、图像网关、回调全部注入替身,不出网、不花钱、不需要 MySQL/RabbitMQ。
真依赖的那部分(Java 侧的任务落库、幂等、补偿扫描)由 Java 的集成测试覆盖,
两边各自证明自己那一半。

对照任务卡的几条验收:
- 状态机序列 `pending(Java) → running → success/failed`
- 「照片确实交给了出图」—— 断言假网关**真的收到了**所选 SKU 的照片 URL
- 失败必须**先回调 failed 再抛出**(否则用户干等到补偿扫描接管)
- 进 DLQ 前也要先回调 failed
"""

from __future__ import annotations

import json
import logging

import pytest

from app.agent.prompt import FurnishConfig
from app.generate.consumer import MAX_RETRY, GenerateConsumer
from app.generate.orchestrator import (
    CODE_GENERATE_FAILED,
    CODE_TASK_NOT_FOUND,
    GeneratedRef,
    TaskGoneError,
    ref_images_of,
    run_generate_task,
)
from app.gateway.errors import ProviderError
from app.gateway.ports import GeneratedImage, Images

# ----------------------------------------------------------------------
# 替身
# ----------------------------------------------------------------------

#: 假 SKU 库。字段名照 Java 的对外 `Sku`(camelCase)—— 它才是真实来源。
#: `coverImage` 与 `images[].url` 就是**参考图**(§15.3),这也是 T-1.11 的验收点。
SKUS: dict[str, dict] = {
    "TL-2043": {
        "sku": "TL-2043",
        "material": "釉面砖",
        "surface": "柔光",
        "colorPattern": "浅灰细石纹",
        "spec": "750×1500mm",
        # 封面与 images[0] 刻意**同一个地址** —— 真实数据就是这样
        # (封面通常就是 default 那张),去重必须经得住它
        "coverImage": "https://oss.test/tiles/TL-2043-cover.jpg",
        "images": [
            {"url": "https://oss.test/tiles/TL-2043-cover.jpg", "angle": "default", "sort": 0},
            {"url": "https://oss.test/tiles/TL-2043-2.jpg", "angle": "angle1", "sort": 1},
        ],
    },
    "TL-3012": {
        "sku": "TL-3012",
        "material": "抛光砖",
        "surface": "亮光",
        "colorPattern": "米白大理石纹",
        "spec": "600×1200mm",
        "coverImage": "https://oss.test/tiles/TL-3012-cover.jpg",
        "images": [],
    },
}


class FakeSkuSource:
    """按 sku 取详情。缺的返回 None —— 模拟 Java 那边查不到(下架/不存在)。"""

    def __init__(self, skus: dict[str, dict] | None = None) -> None:
        self._skus = SKUS if skus is None else skus
        self.asked: list[str] = []

    def get(self, sku: str) -> dict | None:
        self.asked.append(sku)
        return self._skus.get(sku)


class FakeFurnishSource:
    def __init__(self, config: FurnishConfig | None = None) -> None:
        self._config = config if config is not None else FurnishConfig(
            prompt="浅灰布艺沙发，原木茶几", configured=True,
        )

    def get(self, space: str, style: str) -> FurnishConfig:
        return self._config


class FakeImageGateway:
    """记录每次调用收到了什么 —— "照片确实交给了出图"这条验收靠它证明。"""

    def __init__(self, *, images_per_call: int = 1, error: Exception | None = None) -> None:
        self.calls: list[dict] = []
        self._images_per_call = images_per_call
        self._error = error

    def generate(self, prompt, ref_images=None, size=None, n=1, seed=None) -> Images:
        self.calls.append({"prompt": prompt, "ref_images": list(ref_images or []), "n": n})
        if self._error is not None:
            raise self._error
        return Images(
            images=tuple(
                GeneratedImage(url=f"https://oss.test/out/{len(self.calls)}-{i}.png")
                for i in range(self._images_per_call)
            ),
            model="fake",
        )


class FakeCallback:
    """记录每一次回调体。`code` 非 0 可模拟"Java 没受理"。"""

    def __init__(self, *, code: int = 0) -> None:
        self.bodies: list[dict] = []
        self._code = code

    def send_callback(self, body, *, trace_id: str = ""):
        self.bodies.append(dict(body))
        return _Result(accepted=self._code == 0, code=self._code)

    @property
    def statuses(self) -> list[str]:
        return [body["status"] for body in self.bodies]


class _Result:
    def __init__(self, *, accepted: bool, code: int = 0, message: str = "") -> None:
        self.accepted = accepted
        self.code = code
        self.message = message


def payload_of(*, task_id: str = "g_test1", angles: int = 1, style: str = "modern",
               parts: list[dict] | None = None, trace_id: str = "t-1") -> dict:
    return {
        "taskId": task_id,
        "type": "image",
        "traceId": trace_id,
        "userId": 88110001,
        "space": "bathroom",
        "style": style,
        "angles": angles,
        "parts": parts if parts is not None else [
            {"part": "floor", "sku": "TL-2043", "area": 3.6, "source": "user"},
            {"part": "wall", "sku": "TL-3012", "area": 18.24, "source": "user"},
        ],
        "layout": {"spaces": []},
        "attempt": 1,
        "enqueuedAt": "2026-09-30T10:00:00",
    }


def run(payload: dict, *, gateway=None, callback=None, sku_source=None):
    gateway = gateway if gateway is not None else FakeImageGateway()
    callback = callback if callback is not None else FakeCallback()
    sku_source = sku_source if sku_source is not None else FakeSkuSource()
    outcome = run_generate_task(
        payload,
        sku_source=sku_source,
        furnish_source=FakeFurnishSource(),
        image_gateway=gateway,
        callback=callback,
    )
    return outcome, gateway, callback


# ======================================================================
# 编排:正常路径
# ======================================================================

def test_single_angle_reports_running_then_success() -> None:
    """1 个角度 = 1 张图。回调序列:`running(0)` → `success(100)`。"""
    outcome, gateway, callback = run(payload_of(angles=1))

    assert callback.statuses == ["running", "success"], callback.bodies
    assert callback.bodies[0]["progress"] == 0
    assert callback.bodies[0]["taskId"] == "g_test1"
    assert callback.bodies[1]["progress"] == 100
    assert len(gateway.calls) == 1
    assert [image.view for image in outcome.images] == ["default"]


def test_multi_angle_calls_gateway_once_per_view_and_reports_progress() -> None:
    """3 个角度 → 网关被调 3 次(不是一次 n=3),进度 33 / 66,最后一次直接 success。"""
    outcome, gateway, callback = run(payload_of(angles=3))

    assert [call["n"] for call in gateway.calls] == [1, 1, 1], \
        "逐视角循环 —— 一次 n=3 会把同一个提示词发三遍,违背「同一张照片的不同机位」"
    assert [image.view for image in outcome.images] == ["default", "angle1", "angle2"]
    assert callback.statuses == ["running", "running", "running", "success"]
    assert [body.get("progress") for body in callback.bodies] == [0, 33, 66, 100]


def test_each_view_gets_its_own_prompt() -> None:
    """视角不同 ⇒ 提示词不同。相同的话多角度就毫无意义。"""
    _, gateway, _ = run(payload_of(angles=3))

    prompts = [call["prompt"] for call in gateway.calls]
    assert len(set(prompts)) == 3, prompts


def test_only_default_view_is_cover() -> None:
    """封面只有 `default` 一张(D-5 的基准机位唯一,故封面确定)。"""
    _, _, callback = run(payload_of(angles=3))

    images = callback.bodies[-1]["imageUrls"]
    assert [image["isCover"] for image in images] == [True, False, False]
    assert sum(1 for image in images if image["isCover"]) == 1


def test_trace_id_rides_every_callback() -> None:
    """traceId 必须跟到底 —— 出了图要能找回是哪次请求(架构 §13.7)。"""
    _, _, callback = run(payload_of(trace_id="trace-abc"))

    assert all(body["traceId"] == "trace-abc" for body in callback.bodies)


def test_missing_style_degrades_to_empty_string() -> None:
    """`style` 在契约里可选。缺了就退化成键本身,不让整条链断掉。"""
    payload = payload_of()
    del payload["style"]

    _, gateway, _ = run(payload)

    assert gateway.calls, "缺 style 也必须照常出图"
    assert "现代" not in gateway.calls[0]["prompt"]


# ======================================================================
# 编排:照片确实是出图的输入
# ======================================================================

def test_reference_images_are_the_selected_products_photos() -> None:
    """**T-1.11 的核心验收**:交给图像网关的参考图 = 所选 SKU 的照片 URL。"""
    _, gateway, _ = run(payload_of(angles=2))

    assert gateway.calls[0]["ref_images"] == [
        "https://oss.test/tiles/TL-2043-cover.jpg",
        "https://oss.test/tiles/TL-2043-2.jpg",
        "https://oss.test/tiles/TL-3012-cover.jpg",
    ], "封面与 images 都算参考图,按部位顺序排列"


def test_reference_images_are_deduplicated() -> None:
    """同一块砖铺两个部位 ⇒ 它的照片只出现一次。"""
    parts = [
        {"part": "floor", "sku": "TL-2043"},
        {"part": "wall", "sku": "TL-2043"},
    ]

    refs = ref_images_of(parts, FakeSkuSource())

    assert refs == (
        "https://oss.test/tiles/TL-2043-cover.jpg",
        "https://oss.test/tiles/TL-2043-2.jpg",
    ), "重复的参考图不会让模型更「看见」它,只是白花一次视觉 token"


def test_unknown_sku_is_skipped_but_generation_continues() -> None:
    """取不到的 SKU 不阻塞出图 —— 降级不阻断主流程(§13.3)。"""
    parts = [
        {"part": "floor", "sku": "TL-2043"},
        {"part": "wall", "sku": "TL-GONE"},
    ]

    outcome, gateway, callback = run(payload_of(parts=parts))

    assert outcome.missing_skus == ("TL-GONE",)
    assert gateway.calls, "缺一块砖的详情不该让整次生成失败"
    assert callback.statuses[-1] == "success"
    assert "TL-GONE" not in gateway.calls[0]["ref_images"]


def test_no_reference_images_still_generates(caplog) -> None:
    """一张参考图都没有时**仍然出图**(退化成文生图),但要留下告警。"""
    with caplog.at_level(logging.WARNING):
        outcome, gateway, callback = run(
            payload_of(parts=[{"part": "floor", "sku": "TL-GONE"}]),
        )

    assert outcome.ref_images == ()
    assert gateway.calls
    assert callback.statuses[-1] == "success"
    assert any("没有任何参考图" in record.message for record in caplog.records)


# ======================================================================
# 编排:失败路径
# ======================================================================

def test_gateway_error_reports_failed_then_reraises() -> None:
    """网关炸了 ⇒ **先回调 failed(40001)再抛出**,让用户立刻看到失败。

    只抛不回调的话,用户要一直轮询到 Java 的补偿扫描(最长 30 分钟)接管,
    期间看到的是一个转不完的圈。
    """
    gateway = FakeImageGateway(error=ProviderError("上游 502", status_code=502))
    callback = FakeCallback()

    with pytest.raises(ProviderError):
        run(payload_of(angles=2), gateway=gateway, callback=callback)

    assert callback.statuses == ["running", "failed"]
    failed = callback.bodies[-1]
    assert failed["error"]["code"] == CODE_GENERATE_FAILED
    assert "ProviderError" in failed["error"]["message"]


def test_gateway_returning_no_image_is_failure() -> None:
    """网关返回 0 张图 ⇒ 失败。往下走会送出一个「success 但没有图」的回调,
    Java 只能拒掉(10001),用户看到的是一次莫名其妙的失败。"""
    gateway = FakeImageGateway(images_per_call=0)
    callback = FakeCallback()

    with pytest.raises(RuntimeError):
        run(payload_of(), gateway=gateway, callback=callback)

    assert callback.statuses[-1] == "failed"


def test_callback_failure_does_not_mask_the_original_error() -> None:
    """回调自己出错时,**原始异常照常抛出** —— 否则日志里只剩「回调失败」,
    真正的原因(网关超时/限流)被吞了。"""
    class ExplodingCallback(FakeCallback):
        def send_callback(self, body, *, trace_id: str = ""):
            self.bodies.append(dict(body))
            raise RuntimeError("回调也炸了")

    gateway = FakeImageGateway(error=ProviderError("上游 502", status_code=502))

    with pytest.raises(ProviderError, match="502"):
        run(payload_of(), gateway=gateway, callback=ExplodingCallback())

    assert gateway.calls, "回调炸了不阻断生成 —— 开工回调炸掉就放弃,等于白扔一次已付费的生成"


def test_task_gone_aborts_before_burning_more_model_calls() -> None:
    """Java 返回 40003(任务不存在)⇒ **立刻停手**,不再继续出图。

    任务记录都不在了,再出一次图也无人认领。继续跑等于白烧 4 次模型调用。
    """
    gateway = FakeImageGateway()
    callback = FakeCallback(code=CODE_TASK_NOT_FOUND)

    with pytest.raises(TaskGoneError):
        run(payload_of(angles=4), gateway=gateway, callback=callback)

    assert gateway.calls == [], "开工回调就被拒了,一次模型调用都不该发出去"
    assert callback.statuses == ["running"], "任务已经不在,再回调 failed 也没有接收方"


def test_transient_callback_failure_does_not_abort_generation() -> None:
    """回调超时/连不上只记日志,**不该放弃一次已经付费的生成**。"""
    gateway = FakeImageGateway()
    callback = FakeCallback(code=90001)

    outcome, _, _ = run(payload_of(angles=2), gateway=gateway, callback=callback)

    assert len(gateway.calls) == 2
    assert len(outcome.images) == 2


def test_missing_task_id_is_rejected() -> None:
    """没有 taskId 就无处回调、无法幂等 —— 这条消息是坏的。"""
    with pytest.raises(ValueError, match="taskId"):
        run({"status": "x"})


# ======================================================================
# 消费端
# ======================================================================

class FakeProperties:
    def __init__(self, headers: dict | None = None, content_type: str = "application/json") -> None:
        self.headers = headers or {}
        self.content_type = content_type


class FakeMethod:
    def __init__(self, tag: int = 1) -> None:
        self.delivery_tag = tag


class FakeChannel:
    """记录 ACK / NACK / 重投。"""

    def __init__(self) -> None:
        self.acked: list[int] = []
        self.nacked: list[tuple[int, bool]] = []
        self.published: list[dict] = []

    def basic_ack(self, delivery_tag: int) -> None:
        self.acked.append(delivery_tag)

    def basic_nack(self, delivery_tag: int, requeue: bool) -> None:
        self.nacked.append((delivery_tag, requeue))

    def basic_publish(self, **kwargs) -> None:
        self.published.append(kwargs)


def consumer_with(*, api, gateway=None) -> GenerateConsumer:
    return GenerateConsumer(
        api_client=api,
        image_gateway=gateway if gateway is not None else FakeImageGateway(),
        sku_source_factory=lambda client, trace_id="": FakeSkuSource(),
        furnish_source_factory=lambda client, trace_id="": FakeFurnishSource(),
    )


def test_consumer_acks_after_success() -> None:
    api = FakeCallback()
    channel = FakeChannel()

    consumer_with(api=api)._on_message(
        channel, FakeMethod(tag=7), FakeProperties(), json.dumps(payload_of()).encode(),
    )

    assert channel.acked == [7]
    assert channel.nacked == []
    assert channel.published == []
    assert api.statuses[-1] == "success"


def test_consumer_republishes_until_the_retry_limit() -> None:
    """失败重投时带计数,且**不 ACK 原消息的语义要正确**:
    重投 + ACK 原消息,否则 requeue 会立刻再投一次,形成没有退避的热循环。"""
    api = FakeCallback()
    channel = FakeChannel()
    body = json.dumps(payload_of()).encode()

    consumer_with(api=api, gateway=FakeImageGateway(error=RuntimeError("炸")))._on_message(
        channel, FakeMethod(tag=1), FakeProperties(headers={"x-generate-retry": 0}), body,
    )

    assert len(channel.published) == 1
    assert channel.published[0]["properties"].headers["x-generate-retry"] == 1
    assert channel.acked == [1], "重投之后必须 ACK 原消息"
    assert channel.nacked == []


def test_consumer_dead_letters_after_retry_limit_and_notifies_java() -> None:
    """到上限 ⇒ **先回调 failed 再 nack**,用户不必干等到补偿扫描接管。"""
    api = FakeCallback()
    channel = FakeChannel()

    consumer_with(api=api, gateway=FakeImageGateway(error=RuntimeError("炸")))._on_message(
        channel, FakeMethod(tag=3),
        FakeProperties(headers={"x-generate-retry": MAX_RETRY}),
        json.dumps(payload_of()).encode(),
    )

    assert channel.nacked == [(3, False)], "requeue=False ⇒ 交给 DLX 转死信队列"
    assert channel.published == []
    assert api.statuses[-1] == "failed"
    assert api.bodies[-1]["error"]["code"] == CODE_GENERATE_FAILED


def test_consumer_drops_message_when_task_is_gone() -> None:
    """40003 ⇒ 直接 ACK 丢弃。重投只会拿一条注定失败的消息把队列堵满。"""
    api = FakeCallback(code=CODE_TASK_NOT_FOUND)
    channel = FakeChannel()

    consumer_with(api=api)._on_message(
        channel, FakeMethod(tag=9), FakeProperties(), json.dumps(payload_of()).encode(),
    )

    assert channel.acked == [9]
    assert channel.nacked == []
    assert channel.published == [], "不重投"


def test_dead_letter_notification_survives_unparsable_body() -> None:
    """消息体本身坏了就没有 taskId 可回传 —— 但**消息该进死信队列还是得进**。"""
    api = FakeCallback()
    channel = FakeChannel()

    consumer_with(api=api, gateway=FakeImageGateway(error=RuntimeError("炸")))._on_message(
        channel, FakeMethod(tag=5),
        FakeProperties(headers={"x-generate-retry": MAX_RETRY}),
        b"{ not json",
    )

    assert channel.nacked == [(5, False)]
    assert api.bodies == []
