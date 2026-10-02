"""越界拦截 —— 问价必须拒答(不变量 1;PRD §5.2.3)。

回答范围**仅限**:瓷砖基本信息、产品推荐、搭配方案。其余一律 50001 拒答。

**两道闸门,顺序与分工是刻意的:**

1. `fast_out_of_scope` —— **确定性关键词**。命中即 50001,**不调模型**。
   为什么它排在前面:硬约束要求"问价 → 50001"必须**可靠**,而模型是概率性的。
   关键词闸门是这条硬约束的**保底**;模型只用来补它漏掉的隐晦说法。
2. `llm_out_of_scope` —— 让模型判断意图。**分类器故障/超时一律放行**:
   宁可漏拒一句问价,也不该因为分类器连不上就把正常咨询全部打成错误。
   漏拒的残余风险由 system prompt 兜(它同时禁止报价)。

**关键词只认整词,不认素面数字**:`750×1500` 是尺寸不是价格,
所以价格模式一律要求出现 "价/钱/费/贵/便宜/优惠" 这类**词**,
不靠"句子里有数字"来判断。
"""

from __future__ import annotations

import logging
import re
from typing import Any

logger = logging.getLogger(__name__)

#: 命中即拒答的**价格类**说法。
_PRICE_PATTERN = re.compile(
    r"多少钱|多钱|什么价|价格|价钱|报价|单价|总价|价位|"
    r"贵不贵|太贵|便宜|划算|折扣|优惠|促销|"
    r"费用|花费|收费|预算|每平|一平|元/平|元每平|元/㎡|元/平米"
)

#: 明确**不在**咨询范围的说法:效果图与报价单(不变量 2)、施工/售后等一期不做的话题。
_OTHER_OUT_OF_SCOPE_PATTERN = re.compile(
    r"效果图|出图|渲染图|报价单|施工|铺贴费|人工费|安装|售后|保修|退换|"
    r"物流|运费|下单|支付|付款|发票"
)

_LLM_VERDICT_PROMPT = (
    "你是客服意图分类器。只回一个词:"
    "若用户问题涉及**价格、费用、报价**或**施工、售后、物流、下单支付**等"
    "与瓷砖信息/推荐/搭配无关的话题,回 OUT;"
    "否则回 OK。不要解释,不要标点。"
)


def fast_out_of_scope(message: str) -> bool:
    """确定性闸门。命中 → 调用方应立即 50001,**不必调模型**。"""
    text = (message or "").strip()
    if not text:
        return False
    return bool(_PRICE_PATTERN.search(text) or _OTHER_OUT_OF_SCOPE_PATTERN.search(text))


def llm_out_of_scope(gateway: Any, message: str) -> bool:
    """模型闸门。**任何异常都返回 False(放行)**,不把故障升级成拒答。

    非流式调用:分类只需要一个词,流式没有意义。
    """
    from app.gateway.ports import Message  # 延迟 import:避免模块级循环依赖

    try:
        reply = gateway.chat(
            [
                Message(role="system", content=_LLM_VERDICT_PROMPT),
                Message(role="user", content=message),
            ],
            stream=False,
        )
    except Exception as error:  # noqa: BLE001 —— 分类器故障不阻断主流程
        logger.warning("意图分类失败,放行:%s", type(error).__name__)
        return False

    content = str(getattr(reply, "content", "") or "").strip().upper()
    return content.startswith("OUT")
