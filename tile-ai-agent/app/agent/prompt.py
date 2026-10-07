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
#:
#: ⚠️ 2026-10-03 扩写。原来的两条只说了"贴近所选产品""与给定尺寸一致" ——
#: 两句都**太软,而且第二句当时根本兑现不了**(尺寸从来没写进提示词,
#: 模型无从知道)。实测反馈的两个症状是:**砖不按部位铺**(常常被画成一排样品图
#: 或拼贴)与**每次生成像不同的房间**。故补上"一个完整房间""不要拼贴"
#: "多角度必须是同一间房"这三条**可执行**的约束。
CONSTRAINTS: tuple[str, ...] = (
    "瓷砖的纹理与颜色贴近所选产品",
    "空间结构与上面给出的尺寸一致",
    "整张图是一个完整连续的室内空间,不要拼接、不要分屏、不要多个画面",
    "同一个房间在不同角度下必须完全一致",
)

#: 单视角的视角名 —— 与契约 `PlanImage.view` 的 `example: default` 一致。
DEFAULT_VIEW = "default"

#: 视角短语(有序):第 i 张图用哪句。**"多角度"= 同一张照片的不同机位** ——
#: 只换相机位置与朝向,空间、陈设与瓷砖必须完全不变(故多角度时另加
#: `MULTI_ANGLE_CLAUSE`,把这条不变量写进提示词)。
#:
#: 四个机位按"信息量"排序:第 1 张是能一眼看全空间的定妆照,往后依次强调
#: 空间关系、地面铺贴、墙面纹理。用户要的角度越多,拿到的信息越全。
#:
#: 上游只定了**命名**(契约 `PlanImage.view`,D-5),语义由本处决定
#: (2026-09-30,见 `openapi-verification.md` §33)。要加/改机位就改这一个表 —
#: 名字里的序号与 `views_for_angles` 生成的视角名一一对应。
#:
#: ⚠️ **2026-10-07:契约把 `angles` 上限锁为 1,当前只有 `DEFAULT_VIEW` 会被用到。**
#: 其余三个机位**保留、不删** —— 它们是"同一间房的不同机位"的现成资产,
#: 将来用**多机位底图**恢复多角度时可直接启用(恢复时要把 `MAX_ANGLES` 与
#: 契约的 `maximum` 一起改回去)。见 `openapi-verification.md` §74。
VIEW_PHRASES: Mapping[str, str] = {
    # ⚠️ 措辞必须与**白模的机位**对得上(2026-10-07,§77):白模是"相机在视线高度、
    # 略微低头 10°"画的 —— 低头是为了让**地面**在画面里占得够多(而地面正是贴地砖
    # 要看的那个面)。原先写"平视",与图**互相打架**,模型多半信文字,
    # 于是白模最有价值的那点可见度被抵消掉。
    DEFAULT_VIEW: "正面全景，镜头在视线高度、略微向下俯视",
    "angle1": "从房间一角斜向俯视，同时看到地面与两面墙",
    "angle2": "高位俯瞰，重点呈现地面铺贴",
    "angle3": "贴近墙面拍摄，重点呈现墙面瓷砖的纹理与光泽",
}

#: 要的视角超出上表时的兜底。不重复已有表述 —— 重复的机位只会得到一张
#: 近乎一样的图,那是在浪费一次生成(单图 ≤60s 且要计费)。
#:
#: ⚠️ 契约已封 `angles` 上限(`GenerateRequest.maximum: 1`,§74),
#: 所以**正常路径走不到这里**。留着它是纵深防御:本模块也会被内部调用方直接使用,
#: 不该因为对方没做校验就崩或默默越界。
ANGLE_FALLBACK_PHRASE = "换一个拍摄角度"

#: **契约允许的最大张数**(`GenerateRequest.angles.maximum`)。
#:
#: ⚠️ **2026-10-07 由 4 收窄为 1** —— 每个空间只出一张效果图。
#: 理由与追溯见 `openapi-verification.md` §74。
#:
#: ⚠️ **它不再等于 `VIEW_PHRASES` 的条目数**(那是 4)。这是**有意的**:
#: 机位表是"能力",上限是"当前产品决定"。改这个值时必须同步改三处契约
#: (`openapi-c.yaml`、`asyncapi-internal.yaml` 的 `maximum`,以及 Java 的 `@Max`),
#: 测试里有一条盯住 Python 侧与契约的一致性。
MAX_ANGLES = 1

#: 多角度时附加的不变量:多张图必须像"同一张照片的不同角度"。
#:
#: ⚠️ 2026-10-03 加强:原句"同一空间的同一组瓷砖,仅拍摄角度不同"只说了**是什么**,
#: 没把"哪些东西不许变"列出来。实测反馈"每张图像不同的房间",故逐项点名。
MULTI_ANGLE_CLAUSE = (
    "这是同一个房间、同一组瓷砖,本次只改变拍摄角度 —— "
    "房间形状与尺寸、陈设摆设、每块砖所处的部位与外观,都必须与其它角度完全一致"
)

#: 部位 → 参考图的绑定说明。**只在拿得到参考图编号时才拼**。
#:
#: 为什么需要它:我们一次发 N 张参考图,而提示词原先只按文字罗列各部位的砖 ——
#: **没有任何一句把"第几张图"和"哪个部位"连起来**。模型于是常常把砖画成一排
#: 样品/拼贴,或者把地面的砖画到墙上。这是"不按描述画在对应位置"的直接原因。
#:
#: 没有编号时**不拼** —— 写一句"以参考图为准"而参考图根本没有对应关系,
#: 是一句模型无法执行的指令。
REF_BINDING_CLAUSE = (
    "瓷砖必须严格按上面「部位 → 参考图 N」的对应关系铺贴到对应位置,"
    "不得互换、不得混用;不要把瓷砖画成样品图、贴图或拼贴"
)

#: **基准图形态**的绑定说明(2026-10-03,§64)。此时参考图只有 1 张 ——
#: 就是上一次生成的那张房间图,它已经**自带了砖的位置**,所以不再谈"第几张图对应哪个部位"。
#:
#: ⚠️ 两种形态**必须互斥**:基准图形态下若还留着「铺贴参考图 1 的瓷砖」,
#: 那句就成了假话(参考图 1 现在是整间房,不是地面砖)—— **不会报错,只会画得更乱**。
#: `assemble_prompt(base_image=True)` 负责把部位行里的编号与 `REF_BINDING_CLAUSE` 一起去掉。
BASE_IMAGE_CLAUSE = (
    "以参考图 1 为该空间的基准图 —— 房间形状与尺寸、陈设摆设、每块砖所处的部位与外观,"
    "都必须与它完全一致,本次只改变拍摄角度"
)

#: **白模说明**(2026-10-07,§77)。参考图 1 是按本房间尺寸画的三维白模。
#:
#: ⚠️ **这句必须写,而且实测证明不写会出错**:白模是一张没有材质的灰白结构图,
#: 不告诉模型它是什么,模型会把它当成"一张拍得很素的照片" ——
#: 实测**后墙上的那扇门被画成了一面镜子**(§77.2)。
#:
#: ⚠️ 它与 `REF_BINDING_CLAUSE` 是**配套**的:后者说"按『部位 → 参考图 N』铺贴",
#: 而白模占了第 1 位 —— 必须同时讲清"参考图 1 不是砖",否则模型可能把它当成
#: 「局部」部位的那块砖去贴。
WHITEMODEL_CLAUSE = (
    "参考图 1 是本房间的**三维白模**(没有材质的房间结构图),它只表达房间的形状 —— "
    "请严格保持它的墙 / 地面 / 天花位置与透视、门窗的数量与位置,"
    "只把表面的材质替换成真实瓷砖;不要把白模本身画成瓷砖、陈设或装饰"
)


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

    #: 产品名(如"思路雪影")。**只进提示词,不进契约** —— `to_contract()` 里没有它。
    #:
    #: 为什么要它:部位行原先只有材质属性,而**两款砖的属性可以完全一样**
    #: (同规格同材质同花色,只有名字不同)。那时两行文字一模一样,
    #: 模型没有任何依据把"哪一块"对应到"哪个部位" —— 2026-10-03 实测
    #: 就遇到过地面与墙面两行完全相同的情形。名字给模型一个可区分的记号。
    name: str = ""

    def to_contract(self) -> dict[str, Any]:
        """对外形状**保持不变**:`name` 是提示词专用的内部字段,不发出去。"""
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
            mapped.append(PartMaterial(
                part=part, sku=sku, phrases=map_material(data),
                name=_text(data.get("name")),
            ))

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
    """角度数 → 视角名列表(D-5,**2026-09-30 修订**)。

    **张数 = 角度数**:`angles = N` 出 N 张。第 1 张为 `default`,
    第 k 张(`k ≥ 2`)为 `angle{k-1}` —— 故 N 张的视角是
    `default` + `angle1`…`angle{N-1}`(N=1 时仅 `default`)。

    修订前契约写的是 `default` + `angle1`…`angleN`,照抄会出 **N+1** 张,
    与 `angles` 自身"生成角度数"的含义、PRD §5.4「默认 1 张 / 可选多角度」、
    架构 §11.2「默认 1 张 / 多角度 N 张」三处都对不上。
    裁决与追溯见 `openapi-verification.md` §32。
    """
    total = max(1, angles)
    return tuple(
        DEFAULT_VIEW if index == 1 else f"angle{index - 1}"
        for index in range(1, total + 1)
    )


def _num(value: Any) -> str:
    """尺寸数值 → 提示词里的写法。整数不带小数点(`2` 而不是 `2.0`),非法/非正数返回空串。"""
    if value is None:
        return ""
    try:
        number = float(value)
    except (TypeError, ValueError):
        return ""
    if number <= 0:
        return ""
    return str(int(number)) if number == int(number) else str(number)


def _scene_line(room: Mapping[str, Any] | None) -> str:
    """场景锚点句 —— **把尺寸写出来**,这是"每次像不同房间"的主要解药。

    ⚠️ 原先只在约束里写"空间结构与给定户型尺寸一致",但**尺寸从来没进过提示词**
    —— 模型看不到 `layout`,那句约束等于没给。没有尺寸时退化成只说"一个房间",
    不编数字。
    """
    measured = [
        f"{label} {value} 米"
        for key, label, value in (
            ("length", "长约", _num(room.get("length") if room else None)),
            ("width", "宽", _num(room.get("width") if room else None)),
            ("height", "层高", _num(room.get("height") if room else None)),
        )
        if value
    ]
    where = "一个完整连续的室内空间"
    if measured:
        where = f"{where}（{'，'.join(measured)}）"
    return (
        f"场景：{where}；整张图只拍这一个房间，"
        "不要拼接、不要分屏、不要多个画面。"
    )


def _part_line(material: PartMaterial, ref_index: Mapping[str, int] | None) -> str:
    """一个部位一行。**拿到编号就写明"第几张参考图"** —— 这是部位绑定的关键。"""
    label = PART_LABELS.get(material.part, material.part)
    number = (ref_index or {}).get(material.part)
    head = f"铺贴参考图 {number} 的" if number else "铺贴"
    if material.phrases.text:
        # 产品名放在最前:它是**区分两块属性完全相同的砖**的唯一记号
        described = "，".join(
            text for text in (material.name, material.phrases.text) if text
        )
        tail = f"瓷砖（{described}）"
    else:
        # 有部位、没描述 —— 说清楚缺什么,不假装描述过
        tail = f"产品 {material.sku}（材质信息缺失）"
    return f"{label}：{head}{tail}。"


def assemble_prompt(
    *,
    space: str,
    style: str,
    parts: Sequence[PartMaterial],
    furnish: FurnishConfig | None = None,
    view: str = DEFAULT_VIEW,
    multi_angle: bool = False,
    ref_index: Mapping[str, int] | None = None,
    room: Mapping[str, Any] | None = None,
    base_image: bool = False,
    whitemodel: bool = False,
) -> str:
    """按 §7.3 的公式组装一条提示词:

    `空间类型 + 风格 + 各部位材质 + 常规陈设提示词 + 视角` (+ 约束)

    陈设未配置时**跳过该段**(不填占位、不报错,§13.3)。

    `multi_angle=True`(本次请求出多张)时,视角段后附 `MULTI_ANGLE_CLAUSE`
    —— 多张图要像"同一张照片的不同角度",这条不变量得让模型看见。
    单张时不附(没有"不同的角度"可比)。

    `ref_index`(部位 → 第几张参考图,**1-based**)与 `room`(户型里的那个空间)
    都是可选增强,**不改变 §7.3 的成分与顺序**:

    - `ref_index` 补上"第几张图对应哪个部位" —— 没有它,模型得自己猜;
    - `room` 补上尺寸 —— 没有它,约束里那句"与尺寸一致"无从兑现。

    两者都拿不到时,输出退化成扩写前的形状(只是措辞更强)。

    `base_image=True` 走**基准图形态**(2026-10-03,§64):此时参考图**只有一张**
    —— 上一次生成的那张房间图(它已自带砖的位置)。故**不谈「第几张图对应哪个部位」**:
    部位行去掉「铺贴参考图 N 的」前缀,绑定句换成 `BASE_IMAGE_CLAUSE`。
    与 `ref_index` **互斥**,`base_image` 优先 —— 两者同时给,编号会被忽略。
    """
    space_label = _label(SPACE_LABELS, space)
    style_label = _label(STYLE_LABELS, style)
    lines = [f"{space_label}空间的{style_label}风格装修效果图。"]
    # 场景锚点紧跟在主句之后 —— 它是对"空间类型"这一项的展开,不是新成分
    lines.append(_scene_line(room))

    for material in parts:
        # 基准图形态**不报编号** —— 那张参考图是整间房,不是某个部位的砖。
        # 报了就是把一句假话交给模型(且不会报错),见 BASE_IMAGE_CLAUSE。
        lines.append(_part_line(material, None if base_image else ref_index))

    if furnish is not None and furnish.configured and furnish.prompt.strip():
        lines.append(f"陈设：{furnish.prompt.strip()}。")

    phrase = view_phrase(view)
    if phrase:
        lines.append(f"视角：{phrase}。")
    if multi_angle:
        # **单独一行**,不塞进视角句的括号里 —— 它约束的是整张图(房间、陈设、
        # 每块砖的位置),不是机位。贴着"视角"写,容易被读成只对机位生效。
        lines.append(f"多角度一致性：{MULTI_ANGLE_CLAUSE}。")

    # 绑定句与约束句收口放在最后:前面已经把"哪个部位、第几张图、什么材质"交代完,
    # 这里压成一句可执行的禁令。没有参考图编号时不写绑定句 —— 见 REF_BINDING_CLAUSE。
    # ⚠️ 两种形态**互斥**:基准图形态下参考图 1 是整间房,再写"参考图 N → 部位"就是假话。
    if base_image:
        lines.append(f"{BASE_IMAGE_CLAUSE}。")
    elif ref_index:
        lines.append(f"{REF_BINDING_CLAUSE}。")
    # 白模说明**紧跟绑定句** —— 两句说的是同一件事(哪张图是什么),拆远了容易只读一半。
    # ⚠️ 只在 `base_image` 形态之外写:那个形态下参考图 1 是上一张产出图,不是白模。
    if whitemodel and not base_image:
        lines.append(f"{WHITEMODEL_CLAUSE}。")
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
    ref_index: Mapping[str, int] | None = None,
    room: Mapping[str, Any] | None = None,
    anchor_first_view: bool = False,
    whitemodel: bool = False,
) -> AssembledPrompts:
    """`assemble_prompt` 的多视角版本 —— 一个视角一条,其余成分完全相同。

    未配置陈设的 WARN **只在这里记一次**;若放进 `assemble_prompt`,
    N 个视角会刷 N 条同样的日志。

    `ref_index` / `room` 原样透传(见 `assemble_prompt` 的说明)。

    `anchor_first_view=True`(2026-10-03,§64):**只有第一个视角用切片形态**,
    其余视角改用**基准图形态** —— 调用方(编排层)会拿第一张的产出图当它们的参考图。
    `angles=1` 时两者等价(没有"其余视角"),故本参数对单张是空操作。
    """
    configured = bool(furnish is not None and furnish.configured)
    if not configured:
        logger.warning(
            "陈设未配置(space=%s, style=%s):本次生成的提示词不含陈设段。"
            "属画质问题、不影响出图;若持续出现请检查后台 t_furnish_style",
            space,
            style,
        )

    view_names = views_for_angles(angles)
    multi_angle = len(view_names) > 1

    views = tuple(
        ViewPrompt(view=view, prompt=assemble_prompt(
            space=space, style=style, parts=parts, furnish=furnish, view=view,
            multi_angle=multi_angle, ref_index=ref_index, room=room,
            # 第 1 张是基准图的**来源**,故仍按切片形态出图;第 2 张起才有基准图可依。
            base_image=anchor_first_view and index > 0,
            whitemodel=whitemodel,
        ))
        for index, view in enumerate(view_names)
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
    ref_index: Mapping[str, int] | None = None,
    room: Mapping[str, Any] | None = None,
    anchor_first_view: bool = False,
    whitemodel: bool = False,
) -> AssembledPrompts:
    """取数 + 映射 + 组装一条龙 —— T-1.11 消费端的入口。

    取陈设失败**不抛异常**:`furnish_source` 为 None 或它自己降级,都走
    `FurnishConfig.missing()`(§13.3)。

    `ref_index` / `room` 由调用方(编排层)算好传进来 —— 本模块**不取数**这条边界
    不破:参考图顺序只有编排层知道,户型尺寸在消息体里。
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
        ref_index=ref_index,
        room=room,
        whitemodel=whitemodel,
        anchor_first_view=anchor_first_view,
    )


# ----------------------------------------------------------------------
# 小工具
# ----------------------------------------------------------------------

def view_phrase(view: str) -> str:
    """视角名 → 机位措辞。表里没收录的 `angleN` 走兜底,不臆造新机位。"""
    if view in VIEW_PHRASES:
        return VIEW_PHRASES[view]
    if view.startswith("angle"):
        return ANGLE_FALLBACK_PHRASE
    return ""


def _label(labels: Mapping[str, str], key: str) -> str:
    """内置默认值里有就用中文,**没有就退回键本身**(后台新增空间时不炸)。"""
    return labels.get(key, key)


def _text(value: Any) -> str:
    return str(value).strip() if value is not None else ""
