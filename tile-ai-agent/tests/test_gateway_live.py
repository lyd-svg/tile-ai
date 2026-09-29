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

import pytest

from app.core.config import Settings
from app.gateway.factory import build_llm_gateway
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
