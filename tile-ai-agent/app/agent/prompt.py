"""材质映射与场景提示词组装(架构 §7.3、§4.2;PRD §5.4)。

契约面上对应两个端点:`POST /internal/agent/material-map`(Java → Python)与
`GET /internal/furnish/prompts`(Python → Java)。**但这两个端点 Java 都不调** ——
2026-09-30 裁决「场景组装在 Python 生成消费端内部完成」(`openapi-verification.md`
§30.2),故本模块是**服务内部模块**,由 T-1.11 的生成消费端直接调用,
契约里那两个端点保留标「预留」。

## 四条设计要点

**1. 严格照 §7.3 的两步走:先「材质映射」,再「场景组装」。**

§7.3 把提示词工程拆成「材质映射」与「场景组装」两步,组装公式是
`空间类型 + 风格 + 各部位材质 + 常规陈设提示词 + 视角`。本模块按这个顺序各出一层:
`map_material` / `map_parts` 是第一步,`assemble_prompt` 是第二步。
**不合成一个大函数** —— 两步各有各的断言(前者断言"这块砖的短语对不对",
后者断言"五个成分齐不齐"),合并了就只能整体比字符串。

**2. 确定性映射不交给模型(不变量 5)。**

SKU 的材质/光泽/花色/规格本来就是结构化字段,把它们"喂给模型让它写一句漂亮的
提示词"会把一个可复现的过程变成不可复现的 —— 同一块砖两次生成可能得到两种描述,
而且没法断言。这里全部是纯函数拼接。

**3. 陈设缺失只降级,不报错(架构 §13.3「降级不阻断主流程」)。**

`t_furnish_style` 是「空间 × 风格」矩阵,后台很可能配不全。未配置时跳过陈设段、
**记一条 WARN**、把 `furnish_configured=False` 透出去,生成照常进行。
陈设缺失是**画质问题**,不是功能问题 —— 不该让用户承担(理由同契约
`/internal/furnish/prompts` 的 x-note)。

**4. 不取数,只组装。**

SKU 详情与陈设配置分别经 `SkuSource` / `FurnishSource` 注入(Python → Java 的
HTTP 客户端及其错误映射属于编排层 T-1.11)。这样本模块**没有 I/O**,测试不需要
任何外部服务;也让"陈设不硬编码"成为结构事实 —— 模块里根本没有陈设字面量。

## 边界

- **不产出报价**,且**价格字段一律不进提示词**(PRD §10:陈设不计入报价)。
  提示词里出现价格既无意义,也是一处不必要的信息暴露面。测试有一条专门断言
  SKU 的 `pricePerSqm` 不会出现在任何提示词文本里。
- **不调图像网关、不产出效果图** —— 那是 T-1.11 的编排(不变量 2)。
- **不做内容审核** —— §7.3 的「内容安全」在生成前后各一次,由 T-1.11 接。
- 空间/风格的中文措辞是**内置默认值**(大实话:提示词得是中文,而契约里只有
  `living_room` 这类键)。后台字典(D-14)新增空间时这里查不到,**退化为键本身**
  而不报错 —— 新空间仍能出图,只是提示词里带一个英文键。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any, Mapping, Protocol, Sequence

logger = logging.getLogger(__name__)

#: 部位枚举(契约 `common.Part`,恒为三值;一期不含美缝)。
#: 顺序即**提示词里的出场顺序** —— 固定下来,输出才是确定的。
PARTS: tuple[str, ...] = ("floor", "wall", "local")

#: 部位的中文说法。
PART_LABELS: Mapping[str, str] = {"floor": "地面", "wall": "墙面", "local": "局部"}

#: `SpaceType` 内置默认值 → 中文。⚠️ 实时可选集以后台字典为准(D-14),
#: 这里只是**内置默认值**;字典新增空间时 `_label` 退化为键本身。
SPACE_LABELS: Mapping[str, str] = {
    "living_room": "客厅",
    "bedroom": "卧室",
    "kitchen": "厨房",
    "bathroom": "卫生间",
    "dining_room": "餐厅",
    "study": "书房",
    "balcony": "阳台",
    "other": "其他空间",
}

#: `StyleType` 内置默认值 → 中文。同上,以后台字典为准。
STYLE_LABELS: Mapping[str, str] = {
    "modern": "现代",
    "light_luxury": "轻奢",
    "cream": "奶油风",
    "nordic": "北欧",
}

#: 约束段(§7.3 后半)。§7.3 在这一项里还写了「要求生成 ≤60s/图」——
#: 那是对**模型调用**的时限要求(配置见 `gateway_image_timeout_seconds`),
#: 不是给模型看的句子,故不进提示词。
CONSTRAINTS: tuple[str, ...] = (
    "瓷砖的纹理与颜色贴近所选产品",
    "空间结构与给定户型尺寸一致",
)

#: 视角短语。**上游只定了命名,没定语义** —— 契约 `PlanImage.view` 仅约定
#: 「angles=1 时仅 `default`;angles=N 时 `default` + `angle1`…`angleN`」(D-5),
#: 没说明 angle1 与 angle2 在画面上差在哪。下列措辞是按惯例推断,**与契约里的
#: `x-todo` 同性质:是决策点,可随时改,改这一处即可。**
VIEW_PHRASES: Mapping[str, str] = {}

#: `angleN` 类视角的通用短语(见 `VIEW_PHRASES` 的说明)。
ANGLE_PHRASE = "从另一个角度拍摄同一空间"

#: 单视角的视角名 —— 与契约 `PlanImage.view` 的 `example: default` 一致。
DEFAULT_VIEW = "default"


# ----------------------------------------------------------------------
# 数据类
# ----------------------------------------------------------------------

@dataclass(frozen=True)
class MaterialPhrase:
    """一块砖的结构化材质短语(契约 `material-map` 响应的 `phrases`)。

    字段名用 snake_case(`color_pattern`),对外 camelCase 由 `to_contract()` 完成
    —— 命名约定见 CLAUDE.md「容易写错的约定」。
    """

    material: str = ""
    surface: str = ""
    color_pattern: str = ""
    spec: str = ""

    @property
    def text(self) -> str:
        """拼成中文短语。缺字段**跳过而不是填占位** —— 宁可少一句,不臆造。"""
        return "，".join(
            part for part in (self.material, self.surface, self.color_pattern, self.spec) if part
        )

    def to_contract(self) -> dict[str, str]:
        """转成契约里的 `phrases` 形状(对外 camelCase)。"""
        return {
            "material": self.material,
            "surface": self.surface,
            "colorPattern": self.color_pattern,
            "spec": self.spec,
        }


@dataclass(frozen=True)
class PartMaterial:
    """一个部位的选定砖及其材质短语 —— 契约 `material-map` 响应的 `parts[]`。"""

    part: str
    sku: str
    phrases: MaterialPhrase = MaterialPhrase()

    def to_contract(self) -> dict[str, Any]:
        return {"part": self.part, "sku": self.sku, "phrases": self.phrases.to_contract()}


@dataclass(frozen=True)
class FurnishConfig:
    """一个「空间 × 风格」的陈设配置(数据源:`t_furnish_style`,经 Java 取)。

    `prompt` **原样**拼进提示词,不做改写 —— 后台配的是什么就是什么。
    """

    prompt: str = ""
    cover_image: str | None = None
    configured: bool = False

    @classmethod
    def missing(cls) -> "FurnishConfig":
        """未配置时的降级值。"""
        return cls()


@dataclass(frozen=True)
class ViewPrompt:
    """一个视角(一张图)的完整提示词。"""

    view: str
    prompt: str


@dataclass(frozen=True)
class AssembledPrompts:
    """组装结果 —— T-1.11 消费端拿它去调图像网关。"""

    #: 每个视角一条,顺序与 `views_for_angles` 一致
    views: tuple[ViewPrompt, ...]
    #: 对号入座后的部位材质(已在提示词里)
    parts: tuple[PartMaterial, ...]
    #: 陈设是否已配置。false 时 T-1.11 应把 WARN 透到监控(§13.3)
    furnish_configured: bool
    #: 取不到详情的 SKU。非空说明图里这块砖没有材质描述 —— 需告警,不必失败
    missing_skus: tuple[str, ...] = ()


# ----------------------------------------------------------------------
# 取数接缝(实现见 T-1.11:`java_base_url` + `service_token` 已在 config 里)
# ----------------------------------------------------------------------

class SkuSource(Protocol):
    """按 SKU 取权威产品数据(`GET /internal/products/{sku}`)。取不到返回 None。"""

    def get(self, sku: str) -> Mapping[str, Any] | None:
        ...


class FurnishSource(Protocol):
    """按「空间 × 风格」取陈设提示词(`GET /internal/furnish/prompts`)。"""

    def get(self, space: str, style: str) -> FurnishConfig:
        ...


# ----------------------------------------------------------------------
# 第一步:材质映射
# ----------------------------------------------------------------------

def map_material(sku_data: Mapping[str, Any]) -> MaterialPhrase:
    """SKU 结构化字段 → 材质短语(§7.3「材质映射」)。

    只搬 `t_product` 的四个描述性字段(规格/材质/花色/光泽),**不碰价格**。
    缺字段留空串 —— 交由 `MaterialPhrase.text` 跳过,而不是编一个"高级灰"。
    """
    return MaterialPhrase(
        material=_text(sku_data.get("material")),
        surface=_text(sku_data.get("surface")),
        # Java 的 Sku 是对外 camelCase(`colorPattern`);下划线形式一并容忍,
        # 因为内部/DB 命名是 snake_case,两种来源都可能喂进来。
        color_pattern=_text(sku_data.get("colorPattern") or sku_data.get("color_pattern")),
        spec=_text(sku_data.get("spec")),
    )


def map_parts(
    selections: Sequence[Mapping[str, Any]],
    sku_source: SkuSource,
) -> tuple[PartMaterial, ...]:
    """部位选定(契约 `PartSelection`)→ 逐部位材质短语,**按部位对号入座**。

    PRD §5.4 要求"不同部位的砖要对号入座",故:
    - 输出**固定按 `PARTS` 排序**(地面 → 墙面 → 局部),与用户选择顺序无关;
      同一部位选多块砖时保留用户顺序(一个部位可多砖,§5.2.4)。
    - 未知部位值直接丢弃并告警 —— Java 侧已按枚举校验过,走到这里说明契约破了,
      与其把它拼进提示词,不如让它显形。
    - 取不到 SKU 详情的,**保留该部位但短语为空**并记 WARN(不静默丢部位:
      丢了图上那个位置就没砖,而用户以为自己选了)。
    """
    parts, _ = _map_parts(selections, sku_source)
    return tuple(parts)


def _map_parts(
    selections: Sequence[Mapping[str, Any]],
    sku_source: SkuSource,
) -> tuple[list[PartMaterial], list[str]]:
    """`map_parts` 的内部形态:额外把"取不到详情的 SKU"交回给调用方汇总。"""
    mapped: list[PartMaterial] = []
    missing: list[str] = []

    for part in PARTS:
        for selection in selections:
            if selection.get("part") != part:
                continue
            sku = _text(selection.get("sku"))
            if not sku:
                logger.warning("部位 %s 的选定缺少 sku,已跳过", part)
                continue
            data = sku_source.get(sku)
            if data is None:
                missing.append(sku)
                logger.warning("取不到 SKU %s 的详情:该部位将没有材质描述", sku)
                mapped.append(PartMaterial(part=part, sku=sku))
                continue
            mapped.append(PartMaterial(part=part, sku=sku, phrases=map_material(data)))

    known = {p for p in PARTS}
    for selection in selections:
        part = selection.get("part")
        if part not in known:
            logger.warning("未知部位 %r,已跳过(契约 Part 只有 %s)", part, "/".join(PARTS))

    return mapped, missing


# ----------------------------------------------------------------------
# 第二步:场景组装
# ----------------------------------------------------------------------

def views_for_angles(angles: int) -> tuple[str, ...]:
    """角度数 → 视角名列表(D-5)。

    契约 `PlanImage.view` 的约定逐字落地:`angles=1` 时**仅** `default`;
    `angles=N` 时 `default` + `angle1`…`angleN`。

    ⚠️ 照抄约定的后果是 **`angles=N` 出 N+1 张图**(`default` 之外还有 N 个),
    与"生成角度数 = N"读起来不一致。这是契约层面的口径问题,由 T-12 的
    契约 ↔ 实现双向 diff 提出,本模块**不擅自改** —— 改契约要走文档链四步。
    """
    if angles <= 1:
        return (DEFAULT_VIEW,)
    return (DEFAULT_VIEW,) + tuple(f"angle{index}" for index in range(1, angles + 1))


def assemble_prompt(
    *,
    space: str,
    style: str,
    parts: Sequence[PartMaterial],
    furnish: FurnishConfig | None = None,
    view: str = DEFAULT_VIEW,
) -> str:
    """按 §7.3 的公式组装一条提示词:

    `空间类型 + 风格 + 各部位材质 + 常规陈设提示词 + 视角` (+ 约束)

    陈设未配置时**跳过该段**(不填占位、不报错,§13.3)。
    """
    space_label = _label(SPACE_LABELS, space)
    style_label = _label(STYLE_LABELS, style)
    lines = [f"{space_label}空间的{style_label}风格装修效果图。"]

    for material in parts:
        label = PART_LABELS.get(material.part, material.part)
        if material.phrases.text:
            lines.append(f"{label}：铺贴{material.phrases.text}。")
        else:
            # 有部位、没描述 —— 说清楚缺什么,不假装描述过
            lines.append(f"{label}：铺贴产品 {material.sku}（材质信息缺失）。")

    if furnish is not None and furnish.configured and furnish.prompt.strip():
        lines.append(f"陈设：{furnish.prompt.strip()}。")

    phrase = _view_phrase(view)
    if phrase:
        lines.append(f"视角：{phrase}。")

    lines.append(f"{'；'.join(CONSTRAINTS)}。")
    return "\n".join(lines)


def build_prompts(
    *,
    space: str,
    style: str,
    parts: Sequence[PartMaterial],
    furnish: FurnishConfig | None = None,
    angles: int = 1,
    missing_skus: Sequence[str] = (),
) -> AssembledPrompts:
    """`assemble_prompt` 的多视角版本 —— 一个视角一条,其余成分完全相同。

    未配置陈设的 WARN **只在这里记一次**;若放进 `assemble_prompt`,
    N 个视角会刷 N 条同样的日志。
    """
    configured = bool(furnish is not None and furnish.configured)
    if not configured:
        logger.warning(
            "陈设未配置(space=%s, style=%s):本次生成的提示词不含陈设段。"
            "属画质问题、不影响出图;若持续出现请检查后台 t_furnish_style",
            space,
            style,
        )

    views = tuple(
        ViewPrompt(view=view, prompt=assemble_prompt(
            space=space, style=style, parts=parts, furnish=furnish, view=view,
        ))
        for view in views_for_angles(angles)
    )
    return AssembledPrompts(
        views=views,
        parts=tuple(parts),
        furnish_configured=configured,
        missing_skus=tuple(missing_skus),
    )


def prepare_prompts(
    *,
    space: str,
    style: str,
    selections: Sequence[Mapping[str, Any]],
    sku_source: SkuSource,
    furnish_source: FurnishSource | None = None,
    angles: int = 1,
) -> AssembledPrompts:
    """取数 + 映射 + 组装一条龙 —— T-1.11 消费端的入口。

    取陈设失败**不抛异常**:`furnish_source` 为 None 或它自己降级,都走
    `FurnishConfig.missing()`(§13.3)。
    """
    mapped, missing = _map_parts(selections, sku_source)
    furnish = FurnishConfig.missing()
    if furnish_source is not None:
        furnish = furnish_source.get(space, style)
    return build_prompts(
        space=space,
        style=style,
        parts=mapped,
        furnish=furnish,
        angles=angles,
        missing_skus=missing,
    )


# ----------------------------------------------------------------------
# 小工具
# ----------------------------------------------------------------------

def _view_phrase(view: str) -> str:
    """视角名 → 短语。见 `VIEW_PHRASES` 的说明(措辞是惯例推断)。"""
    if view in VIEW_PHRASES:
        return VIEW_PHRASES[view]
    if view.startswith("angle"):
        return ANGLE_PHRASE
    return ""


def _label(labels: Mapping[str, str], key: str) -> str:
    """内置默认值里有就用中文,**没有就退回键本身**(后台新增空间时不炸)。"""
    return labels.get(key, key)


def _text(value: Any) -> str:
    return str(value).strip() if value is not None else ""
