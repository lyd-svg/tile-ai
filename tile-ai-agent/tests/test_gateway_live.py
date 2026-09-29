"""T-1.5 的真实厂商联调(**会真的出网、真的花钱**)。

与其余测试分开、且**默认不跑**:需显式设 `RUN_LIVE_LLM=1`。
门控方式与项目里 Java 侧 `-Ddb.verify=true` 的惯例一致 —— 需要外部依赖的验证
必须显式打开,不能让 `pytest` 顺手跑出去。

前置:
  1. `tile-ai-agent/.env` 里有 `MODEL_LLM_PROVIDER=deepseek` 与 `MODEL_LLM_API_KEY`;
  2. Redis 在线(工厂会连它做令牌桶)。

跑法::

    RUN_LIVE_LLM=1 python -m pytest tests/test_gateway_live.py -v

**这一层验的是"能不能真的跟厂商说上话"** —— 与 `test_gateway.py` 互补:
那边验行为契约(超时/重试/熔断/切换),用替身,不出网。
"""

from __future__ import annotations

import os
import time

import pytest

from app.core.config import Settings
from app.gateway.factory import build_image_gateway, build_llm_gateway
from app.gateway.ports import Message, Reply, ReplyChunk, ToolDef
from app.gateway.resilience import CostMeter

pytestmark = pytest.mark.skipif(
    os.environ.get("RUN_LIVE_LLM") != "1",
    reason="真实联调会出网并产生费用;需要时设 RUN_LIVE_LLM=1 显式启用",
)


@pytest.fixture(scope="module")
def settings() -> Settings:
    """读 .env(密钥在那里),并确认确实配了 provider/密钥。"""
    configured = Settings()
    if not configured.model_llm_provider or not configured.model_llm_api_key.get_secret_value():
        pytest.fail("未配置 MODEL_LLM_PROVIDER / MODEL_LLM_API_KEY —— 见 .env.example")
    return configured


@pytest.fixture(scope="module")
def meter(settings: Settings) -> CostMeter:
    """单价取自配置,不是空表 —— 空表会让"有单价却没算出成本"这种错误看不出来。"""
    print(f"\n  配置的单价表 = {settings.gateway_model_prices}")
    return CostMeter(settings.gateway_model_prices)


@pytest.fixture(scope="module")
def gateway(settings: Settings, meter: CostMeter):
    return build_llm_gateway(settings, meter=meter)


# ----------------------------------------------------------------------

def test_chat_reaches_the_real_provider(gateway):
    """一次真实对话:内容非空、用量有值、模型名回填。"""
    reply = gateway.chat([
        Message(role="system", content="你是瓷砖选购顾问,回答控制在 30 字以内。"),
        Message(role="user", content="卫生间适合铺什么颜色的砖?一句话。"),
    ])

    assert isinstance(reply, Reply)
    assert reply.content.strip(), "厂商返回了空内容"
    assert reply.usage.prompt_tokens > 0, "未取到 prompt_tokens —— 成本计量会失真"
    assert reply.usage.completion_tokens > 0
    assert reply.model, "响应未回填 model 字段"

    print(f"\n  provider 返回 model = {reply.model!r}")
    print(f"  usage = {reply.usage.prompt_tokens} in / {reply.usage.completion_tokens} out")
    print(f"  内容 = {reply.content.strip()[:60]}")


def test_configured_model_id_is_accepted(settings, gateway):
    """**验证预置的模型 ID 真的可用** —— 这是本次联调最想确认的一条。

    架构 §15.1 定的是 `deepseek-flash`;网上教程大量还在用已停用的
    `deepseek-chat` / `deepseek-reasoner` 或已退役的 `deepseek-v4-flash`。
    若预置 ID 是错的,这里会直接拿到 4xx。
    """
    reply = gateway.chat([Message(role="user", content="回复:ok")])
    assert reply.content.strip(), "模型 ID 可能不对:调用没报错但没内容"

    # 空 model 配置时应落到预置默认值,而不是把空串发出去
    expected = settings.model_llm_model or "deepseek-flash"
    print(f"\n  配置的模型 = {expected!r};厂商回显 = {reply.model!r}")


def test_streaming_yields_deltas_and_final_usage(gateway):
    """流式:逐帧 delta → 末帧带 usage(§7.1 的 stream 参数)。"""
    stream = gateway.chat(
        [Message(role="user", content="用 20 字以内说一句瓷砖保养建议。")],
        stream=True,
    )

    chunks = list(stream)  # type: ignore[arg-type]
    assert isinstance(chunks[0], ReplyChunk)

    text = "".join(chunk.delta for chunk in chunks)
    assert text.strip(), "流式没有产出任何内容"
    assert chunks[-1].done is True, "缺少 [DONE] 帧"

    with_usage = [c for c in chunks if c.usage is not None]
    assert with_usage, "末帧未带 usage —— 流式的成本计量会变成瞎账"
    print(f"\n  流式 {len(chunks)} 帧,其中带 usage 的 {len(with_usage)} 帧")


def test_tool_calling_returns_a_plan(gateway):
    """工具调用:用一个**路径 C 允许**的工具(产品库检索)。

    刻意不造"报价"工具 —— 架构不变量 1:对话智能体不接报价工具,
    用户问价一律 50001 拒答。测试里也不该出现这种工具的定义。
    """
    plan = gateway.chat_with_tools(
        [Message(role="user", content="卫生间想要浅灰色的砖,帮我找找有哪些。")],
        [
            ToolDef(
                name="search_products",
                description="在产品库里按空间与风格检索瓷砖",
                parameters={
                    "type": "object",
                    "properties": {
                        "space": {"type": "string", "description": "空间,如 bathroom"},
                        "style": {"type": "string", "description": "风格,如 modern"},
                    },
                    "required": ["space"],
                },
            )
        ],
    )

    assert plan.tool_calls, "给了工具却没产生任何工具调用(模型或提示词有问题)"
    call = plan.tool_calls[0]
    assert call.name == "search_products"
    # 参数保留供应商原文,由调用方解析 —— 这里只确认它是合法 JSON 串
    assert call.arguments.strip().startswith("{"), f"工具参数不是 JSON 对象:{call.arguments!r}"
    print(f"\n  工具调用 = {call.name}({call.arguments})")


def test_cost_meter_records_real_usage(meter: CostMeter):
    """成本计量在真实用量上留下了账。

    这条**依赖前面几个用例跑过** —— 它验的是"整轮联调累计下来的账目",
    而不是某一次调用。module 级 fixture 的 meter 会跨用例累加。
    """
    snapshot = meter.snapshot()
    assert snapshot, "成本计量没有任何记录"
    entry = snapshot.get("deepseek")
    assert entry is not None, f"未按 provider 记账:{snapshot}"
    assert entry["prompt_tokens"] > 0
    assert entry["cost"] > 0, "单价取自配置,配了就应算出正数成本"
    print(f"\n  联调累计 = {entry}")


# ----------------------------------------------------------------------
# 图像:通义万相(DashScope 原生协议)
# ----------------------------------------------------------------------

@pytest.fixture(scope="module")
def image_settings() -> Settings:
    configured = Settings()
    if not configured.model_image_provider or not configured.model_image_api_key.get_secret_value():
        pytest.fail("未配置 MODEL_IMAGE_PROVIDER / MODEL_IMAGE_API_KEY —— 见 .env.example")
    return configured


@pytest.fixture(scope="module")
def image_gateway(image_settings: Settings):
    return build_image_gateway(image_settings)


def test_image_generate_reaches_the_real_provider(image_gateway):
    """真实出图:返回图片 URL,且**延迟满足 PRD 的 ≤60s/图**。

    这条同时验两个硬指标:① 链路通 ② §7.3 / §13.1 的 60s 上限真的达得到。
    """
    started = time.monotonic()
    images = image_gateway.generate("浅灰色瓷砖铺设的现代卫生间,简洁干净", size="1K", n=1)
    elapsed = time.monotonic() - started

    assert images.images, "厂商没返回任何图片"
    assert images.images[0].url, "返回的不是 URL"
    # 返回的是 OSS 临时地址(24 小时过期)—— 架构要求转存对象存储,故调用方需及时取走
    assert images.images[0].url.startswith("http")
    assert images.usage.prompt_tokens > 0, "未取到 input_tokens —— 参考图的视觉 token 会进成本"

    print(f"\n  1K 文生图用时 {elapsed:.1f}s(PRD 上限 60s)")
    print(f"  usage = {images.usage.prompt_tokens} in / {images.usage.completion_tokens} out")

    assert elapsed < 60, f"单图超过 60s,违反 §7.3:{elapsed:.1f}s"


def test_image_generate_accepts_four_reference_images_live(image_gateway):
    """**§15.3 的核心假设**:底图 + 各部位切片(≥4 张)真的能进同一个请求。

    整个一期生产路径都押在这条上 —— 它若不成立,PRD 决策 #11 的
    "一个空间多块砖分配到三个部位"就得收窄。故必须有真实调用兜底。
    """
    # 先拿一张真实图片,再把它当参考图用满 4 份(生产中会是底图 + 三张瓷砖切片)
    base = image_gateway.generate("纯灰色瓷砖特写", size="1K", n=1)
    assert base.images, "取底图失败"
    ref = base.images[0].url

    started = time.monotonic()
    images = image_gateway.generate(
        "把这几种瓷砖铺进这个卫生间:地面深灰、墙面浅灰、局部花砖",
        ref_images=[ref, ref, ref, ref],  # 底图 + 地面/墙面/局部
        size="1K",
        n=1,
    )
    elapsed = time.monotonic() - started

    assert images.images, "带 4 张参考图时厂商没返回图片"
    print(f"\n  4 张参考图用时 {elapsed:.1f}s;input_tokens = {images.usage.prompt_tokens}")
    # 参考图会显著抬高 input_tokens(实测每张约 5000 视觉 token)——
    # 所以成本不能只按"每张图多少钱"算,参考图张数直接进账。
    assert images.usage.prompt_tokens > 1000, "参考图似乎没被计入用量"


def test_image_generate_n_two_returns_two_images(image_gateway):
    """`n=2` 必须真的拿到 2 张。

    实测确认多张图落在**多个 choices** 里;只读 choices[0] 会拿到 1 张而
    usage 记 2 张 —— 付了钱没拿到东西,且不报错。
    """
    images = image_gateway.generate("浅灰瓷砖卫生间,现代风", size="1K", n=2)

    assert len(images.images) == 2, f"n=2 只拿到 {len(images.images)} 张"
    print(f"\n  n=2 -> 拿到 {len(images.images)} 张")
