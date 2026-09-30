"""自然语言 → 结构化户型(架构 §4.3 工具②、§5.3.2)。

契约:`openapi-internal.yaml` 的 `POST /internal/agent/parse-layout`。

## 三条设计要点

**1. 用工具调用拿结构化输出,而不是"让它吐 JSON 再解析"。**

提示词里写"请输出 JSON"然后正则/`json.loads` 去捞,是这类任务最常见的做法,
也是最脆的:模型多写一句解释、少一个引号,整条链路就断。
改用 **function calling** —— 把 `LayoutModel` 的 schema 作为工具参数,
由模型按 schema 填,拿到的一定是合法结构。

**2. 尺寸不齐就是失败,不返回半成品(§10.4 决策 2)。**

`LayoutSpace` 的 `type/length/width/height` 全是必填,而尺寸是**报价的输入** ——
缺了会让面积算错、报价悄悄偏掉。所以宁可返 10001 + 逐项原因,也不返回一个
"看起来成功"的半成品。提示用户补齐所需的"缺哪几项"就放在 `errors[]` 里。

**3. 本模块**不**派生 `areas`。**

`areas` 是服务端派生字段,而派生逻辑(架构 §8.3 的公式)**已经在 Java 侧实现**
(`LayoutAreaCalculator`)。在这里再实现一遍就会有两份,迟早不一致。
故本模块只负责"文本 → 尺寸与开口",派生留给 Java —— 无论走哪条路径,
派生都只发生在一个地方。

## 边界

- 只解析户型本身(**长/宽/高/门窗**),不碰瓷砖、不碰报价(不变量 1)。
- 空间类型取值须落在契约 `SpaceType` 的内置默认值内;模型映射不出时按缺失处理,
  由调用方提示用户改用下拉选择 —— 比让模型硬凑一个值更诚实。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Sequence

from app.gateway.ports import LLMGateway, Message, ToolDef

#: 契约 `SpaceType` 的内置默认值(openapi-common.yaml)。
#:
#: ⚠️ 这只是**内置默认值** —— 真实可选集由后台字典决定(D-14)。
#: 解析时用它做校验,是因为模型必须把"客厅"这类中文映射到一个**稳定的键**;
#: 字典里若新增了空间,应同步扩展这里(或改为从 Java 取字典)。
SPACE_TYPES: tuple[str, ...] = (
    "living_room", "bedroom", "kitchen", "bathroom",
    "dining_room", "study", "balcony", "other",
)

#: 开口类型(契约 `common.OpeningType`,枚举 `[door, window]`)。
#: **不要在这里加值** —— 架构 §8.3 的「结构化户型模型字段定义」表同样只列
#: `door` / `window`,多出来的值会让 Java 侧的 `LayoutModel` 校验拒收。
OPENING_TYPES: tuple[str, ...] = ("door", "window")

#: 模型必须返回的字段 —— 缺一即失败(见模块头第 2 条)。
REQUIRED_SPACE_FIELDS: tuple[str, ...] = ("type", "length", "width", "height")


@dataclass(frozen=True)
class LayoutParseError(Exception):
    """解析不出完整户型。`errors` 用契约的 `ErrorItem` 形状(`field` + `reason`)。"""

    errors: tuple[dict[str, str], ...]

    def __str__(self) -> str:  # pragma: no cover - 仅用于日志
        return "; ".join(f"{item['field']}: {item['reason']}" for item in self.errors)


#: 交给模型的工具 schema —— 即 `LayoutModel`,逐字段对齐契约。
_LAYOUT_TOOL = ToolDef(
    name="submit_layout",
    description="提交从用户描述中解析出的结构化户型数据。尺寸单位一律为**米**。",
    parameters={
        "type": "object",
        "required": ["spaces"],
        "properties": {
            "spaces": {
                "type": "array",
                "minItems": 1,
                "description": "户型中的各个空间。用户只提到一个房间时也只放一个。",
                "items": {
                    "type": "object",
                    "required": list(REQUIRED_SPACE_FIELDS),
                    "properties": {
                        "type": {
                            "type": "string",
                            "enum": list(SPACE_TYPES),
                            "description": "空间类型。客厅=living_room,卧室=bedroom,"
                                           "厨房=kitchen,卫生间=bathroom,餐厅=dining_room,"
                                           "书房=study,阳台=balcony,其余=other",
                        },
                        "name": {"type": "string", "description": "中文名,展示用,如「卫生间」"},
                        "length": {"type": "number", "description": "长(米)"},
                        "width": {"type": "number", "description": "宽(米)"},
                        "height": {"type": "number", "description": "层高(米);用户没说时按 2.7 估"},
                        "wallCount": {"type": "integer", "description": "墙面数;用户没说时省略"},
                        "openings": {
                            "type": "array",
                            "description": "门、窗等开口。用户没说时省略,不要编造",
                            "items": {
                                "type": "object",
                                "required": ["type", "w", "h"],
                                "properties": {
                                    "type": {"type": "string", "enum": list(OPENING_TYPES)},
                                    "w": {"type": "number", "description": "宽(米)"},
                                    "h": {"type": "number", "description": "高(米)"},
                                },
                            },
                        },
                    },
                },
            }
        },
    },
)

_SYSTEM_PROMPT = (
    "你是户型数据录入助手。把用户用自然语言描述的户型解析成结构化数据,"
    "调用 submit_layout 工具提交。\n"
    "规则:\n"
    "1. 尺寸单位一律换算成**米**(如 5m×4m → length=5, width=4;500cm → 5)。\n"
    "2. 用户没说层高时按 2.7 米填写。\n"
    "3. **不要编造门窗** —— 用户没提就省略 openings。\n"
    "4. 空间类型必须用给定的英文枚举值。\n"
    "5. 用户提到的每个房间都要列出来,不要只取第一个。\n"
    "6. 如果用户的描述里**完全没有尺寸信息**,不要猜,提交一个空 spaces 数组。"
)


def build_messages(text: str) -> list[Message]:
    """构造解析用的消息。"""
    return [
        Message(role="system", content=_SYSTEM_PROMPT),
        Message(role="user", content=text.strip()),
    ]


def validate_layout(layout: Any) -> list[dict[str, str]]:
    """校验解析结果,返回缺失/非法的逐项原因(契约 `ErrorItem` 形状)。

    空列表 = 通过。**校验放在模型之外**是刻意的:模型可能返回结构合法但语义不全的数据
    (比如少一个长宽),那由这里兜住 —— 不能指望提示词讲清楚了模型就不会漏。
    """
    errors: list[dict[str, str]] = []

    if not isinstance(layout, dict):
        return [{"field": "layout", "reason": "解析结果不是对象"}]

    spaces = layout.get("spaces")
    if not isinstance(spaces, list) or not spaces:
        return [{"field": "spaces", "reason": "没能从描述里识别出任何带尺寸的空间"}]

    for index, space in enumerate(spaces):
        prefix = f"spaces[{index}]"
        if not isinstance(space, dict):
            errors.append({"field": prefix, "reason": "不是对象"})
            continue

        space_type = space.get("type")
        if not space_type:
            errors.append({"field": f"{prefix}.type", "reason": "未识别出空间类型"})
        elif space_type not in SPACE_TYPES:
            errors.append({"field": f"{prefix}.type", "reason": f"未知的空间类型:{space_type}"})

        for field, label in (("length", "长"), ("width", "宽"), ("height", "层高")):
            value = space.get(field)
            if value is None:
                errors.append({"field": f"{prefix}.{field}", "reason": f"未给出{label}"})
            elif not isinstance(value, (int, float)) or value <= 0:
                errors.append({"field": f"{prefix}.{field}", "reason": f"{label}不是正数:{value}"})

        for opening_index, opening in enumerate(space.get("openings") or []):
            opening_prefix = f"{prefix}.openings[{opening_index}]"
            if not isinstance(opening, dict):
                errors.append({"field": opening_prefix, "reason": "不是对象"})
                continue
            if opening.get("type") not in OPENING_TYPES:
                errors.append({"field": f"{opening_prefix}.type",
                               "reason": f"未知的开口类型:{opening.get('type')}"})
            for field in ("w", "h"):
                if opening.get(field) is None:
                    errors.append({"field": f"{opening_prefix}.{field}", "reason": "未给出尺寸"})

    return errors


def parse_layout(gateway: LLMGateway, text: str) -> dict[str, Any]:
    """把自然语言解析成 `LayoutModel`(不含 `areas`,见模块头第 3 条)。

    :raises LayoutParseError: 解析不出完整户型,`errors` 为逐项原因
    """
    if not text or not text.strip():
        raise LayoutParseError(({"field": "text", "reason": "描述为空"},))

    plan = gateway.chat_with_tools(build_messages(text), [_LAYOUT_TOOL])

    if not plan.tool_calls:
        # 模型判断"这不是户型描述"(比如用户写的是"随便看看")
        raise LayoutParseError(
            ({"field": "text", "reason": "没能从这段描述里识别出户型信息"},)
        )

    call = plan.tool_calls[0]
    try:
        layout = json.loads(call.arguments or "{}")
    except ValueError:
        # 用了 function calling 还拿到非法 JSON,说明供应商没按协议来 ——
        # 这时**不能**退化成"尽力解析",直接当失败,免得半成品流进报价
        raise LayoutParseError(
            ({"field": "text", "reason": "解析结果格式不正确,请重试或改用表单录入"},)
        ) from None

    errors = validate_layout(layout)
    if errors:
        raise LayoutParseError(tuple(errors))

    return layout
