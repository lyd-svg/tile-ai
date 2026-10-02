"""T-1.6:问价/越界拦截的关键词闸门。

这道闸门是"问价 → 50001"这条硬约束(不变量 1)的**保底** —— 它必须确定、可预测,
所以逐条钉住。模型闸门(补隐晦说法)只做补充,不在这里测。
"""

from __future__ import annotations

import pytest

from app.chat.scope import fast_out_of_scope


@pytest.mark.parametrize("message", [
    "这个砖多少钱",
    "多少钱一平",
    "有没有优惠",
    "帮我算下总价",
    "这个价格还能再谈谈吗",
    "报价单发我一份",
    "铺贴费用大概多少",
    "这个贵不贵",
    "太贵了吧",
])
def test_price_questions_are_refused(message):
    assert fast_out_of_scope(message) is True


@pytest.mark.parametrize("message", [
    "帮我出个效果图",
    "这个能上门安装吗",
    "售后保修怎么算",
    "包不包物流",
    "能直接下单支付吗",
])
def test_other_out_of_scope_topics_are_refused(message):
    """效果图/报价单(不变量 2)与施工售后等一期不做的话题,同样越界。"""
    assert fast_out_of_scope(message) is True


@pytest.mark.parametrize("message", [
    "卫生间浅灰砖配什么墙砖?",
    "客厅用什么地砖显大",
    "这款砖适合现代风格吗",
    "柔光砖和亮面砖有什么区别",
    "300×600 的砖贴在卫生间合适吗",
    "这款砖性价比高吗",  # 问的是"值不值",不是"多少钱" —— 不该被误拦
])
def test_normal_questions_are_not_refused(message):
    assert fast_out_of_scope(message) is False


@pytest.mark.parametrize("message", ["750×1500", "800*800", "600×1200 一片", "0.8 米"])
def test_bare_dimensions_are_not_mistaken_for_price(message):
    """**尺寸数字不是价格** —— 拦错会拒掉完全正常的规格问题。"""
    assert fast_out_of_scope(message) is False


def test_blank_message_is_not_out_of_scope():
    """空消息交给上层报 10001,不是"越界"。"""
    assert fast_out_of_scope("   ") is False
