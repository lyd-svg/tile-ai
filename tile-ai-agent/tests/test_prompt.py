"""T-1.10:材质映射与场景提示词组装(架构 §7.3、§4.2)。

**纯单测**:取数接缝用假的 `SkuSource` / `FurnishSource`,不出网、不花钱。

对照任务卡的四条验证:
- 不同部位的砖**对号入座**(PRD §5.4)
- 陈设提示词**取自配置,不硬编码**
- 陈设**不计入报价**(PRD §10 边界)
- 契约枚举对齐(空间/风格/部位)
"""

from __future__ import annotations

import logging

import pytest

from app.agent.prompt import (
    CONSTRAINTS,
    DEFAULT_VIEW,
    PARTS,
    PART_LABELS,
    SPACE_LABELS,
    STYLE_LABELS,
    AssembledPrompts,
    FurnishConfig,
    MaterialPhrase,
    PartMaterial,
    assemble_prompt,
    build_prompts,
    map_material,
    map_parts,
    prepare_prompts,
    views_for_angles,
)

#: 假 SKU 库。字段名**照 Java 的对外 Sku**(camelCase),因为它才是真实来源。
SKUS: dict[str, dict] = {
    "TL-2043": {
        "sku": "TL-2043",
        "name": "雅灰石纹",
        "spec": "750×1500mm",
        "material": "釉面砖",
        "surface": "柔光",
        "colorPattern": "浅灰细石纹",
        "pricePerSqm": 268.0,
    },
    "TL-3012": {
        "sku": "TL-3012",
        "name": "米白大理石",
        "spec": "600×1200mm",
        "material": "抛光砖",
        "surface": "亮光",
        "colorPattern": "米白大理石纹",
        "pricePerSqm": 198.5,
    },
    "TL-9001": {
        "sku": "TL-9001",
        "name": "花砖",
        "spec": "300×300mm",
        "material": "仿古砖",
        "surface": "哑光",
        "colorPattern": "几何拼花",
        "pricePerSqm": 88.0,
    },
}


class FakeSkuSource:
    """按 sku 取详情。缺的返回 None —— 模拟 Java 那边查不到。"""

    def __init__(self, skus: dict[str, dict] | None = None) -> None:
        self._skus = SKUS if skus is None else skus
        self.asked: list[str] = []

    def get(self, sku: str):
        self.asked.append(sku)
        return self._skus.get(sku)


class FakeFurnishSource:
    """按「空间 × 风格」取陈设。没配的组合返回 `FurnishConfig.missing()`。"""

    def __init__(self, configs: dict[tuple[str, str], FurnishConfig] | None = None) -> None:
        self._configs = configs if configs is not None else {
            ("bathroom", "modern"): FurnishConfig(
                prompt="马桶、洗手台、淋浴房",
                cover_image="https://oss/furnish/bathroom-modern.jpg",
                configured=True,
            ),
        }

    def get(self, space: str, style: str) -> FurnishConfig:
        return self._configs.get((space, style), FurnishConfig.missing())


def a_selection(part: str, sku: str) -> dict:
    return {"part": part, "sku": sku}


# ----------------------------------------------------------------------
# 材质映射
# ----------------------------------------------------------------------

def test_sku_fields_map_to_phrase_in_contract_field_order():
    phrase = map_material(SKUS["TL-2043"])
    assert phrase == MaterialPhrase(
        material="釉面砖", surface="柔光", color_pattern="浅灰细石纹", spec="750×1500mm",
    )
    assert phrase.text == "釉面砖，柔光，浅灰细石纹，750×1500mm"


def test_phrase_uses_contract_camel_case_on_the_way_out():
    """对外 JSON 必须 camelCase —— 契约 `material-map` 的 `phrases.colorPattern`。"""
    assert map_material(SKUS["TL-2043"]).to_contract() == {
        "material": "釉面砖",
        "surface": "柔光",
        "colorPattern": "浅灰细石纹",
        "spec": "750×1500mm",
    }


def test_snake_case_source_is_accepted_too():
    """Java 侧是对外 camelCase,但 DB/内部命名是 snake_case,两种都要认。"""
    phrase = map_material({"material": "岩板", "color_pattern": "深灰岩纹"})
    assert phrase.color_pattern == "深灰岩纹"


def test_missing_sku_fields_are_skipped_not_invented():
    """缺字段就少一句,不编一个"高级灰"出来 —— 臆造的描述无法断言、也无法归因。"""
    phrase = map_material({"material": "岩板", "surface": None, "spec": ""})
    assert phrase == MaterialPhrase(material="岩板")
    assert phrase.text == "岩板"


def test_mapping_never_reads_price():
    """价格不进提示词:提示词里出现价格既无意义,也是不必要的暴露面。"""
    phrase = map_material(SKUS["TL-2043"])
    assert "268" not in phrase.text
    assert not hasattr(phrase, "price_per_sqm")


# ----------------------------------------------------------------------
# 对号入座(PRD §5.4 / 任务卡验证 ①)
# ----------------------------------------------------------------------

def test_each_part_gets_its_own_brick():
    """核心断言:地面那块砖的描述不会跑到墙面去。"""
    parts = map_parts(
        [a_selection("floor", "TL-2043"), a_selection("wall", "TL-3012")],
        FakeSkuSource(),
    )
    by_part = {p.part: p for p in parts}
    assert by_part["floor"].sku == "TL-2043"
    assert by_part["floor"].phrases.color_pattern == "浅灰细石纹"
    assert by_part["wall"].sku == "TL-3012"
    assert by_part["wall"].phrases.color_pattern == "米白大理石纹"


def test_output_order_is_fixed_regardless_of_input_order():
    """输出恒为 地面 → 墙面 → 局部,与用户勾选顺序无关(输出要确定)。"""
    shuffled = [
        a_selection("local", "TL-9001"),
        a_selection("wall", "TL-3012"),
        a_selection("floor", "TL-2043"),
    ]
    assert [p.part for p in map_parts(shuffled, FakeSkuSource())] == ["floor", "wall", "local"]


def test_same_part_may_hold_several_bricks_in_user_order():
    """一个部位可多砖(§5.2.4),同一部位内保留用户顺序。"""
    parts = map_parts(
        [a_selection("wall", "TL-3012"), a_selection("wall", "TL-2043")],
        FakeSkuSource(),
    )
    assert [(p.part, p.sku) for p in parts] == [("wall", "TL-3012"), ("wall", "TL-2043")]


def test_unknown_part_is_dropped_with_a_warning(caplog):
    """Java 已按枚举校验过;走到这里说明契约破了 —— 让它显形,别拼进提示词。"""
    with caplog.at_level(logging.WARNING):
        parts = map_parts([a_selection("ceiling", "TL-2043")], FakeSkuSource())
    assert parts == ()
    assert "未知部位" in caplog.text


def test_unknown_sku_keeps_the_part_but_says_so(caplog):
    """取不到详情**不静默丢部位** —— 丢了图上那个位置就没砖,用户却以为自己选了。"""
    source = FakeSkuSource({"TL-2043": SKUS["TL-2043"]})
    with caplog.at_level(logging.WARNING):
        parts = map_parts([a_selection("wall", "TL-404")], source)
    assert [(p.part, p.sku) for p in parts] == [("wall", "TL-404")]
    assert parts[0].phrases.text == ""
    assert "取不到 SKU TL-404" in caplog.text


def test_sku_is_looked_up_once_per_selection():
    source = FakeSkuSource()
    map_parts([a_selection("floor", "TL-2043"), a_selection("wall", "TL-3012")], source)
    assert source.asked == ["TL-2043", "TL-3012"]


# ----------------------------------------------------------------------
# 场景组装(§7.3 公式)
# ----------------------------------------------------------------------

def test_assembly_follows_the_section_7_3_formula():
    """空间类型 + 风格 + 各部位材质 + 陈设 + 视角,顺序不能乱。"""
    parts = map_parts(
        [a_selection("floor", "TL-2043"), a_selection("wall", "TL-3012")],
        FakeSkuSource(),
    )
    prompt = assemble_prompt(
        space="bathroom", style="modern", parts=parts,
        furnish=FakeFurnishSource().get("bathroom", "modern"),
        view=DEFAULT_VIEW,
    )
    body = prompt.splitlines()
    assert body[0] == "卫生间空间的现代风格装修效果图。"
    assert body[1].startswith("地面：铺贴釉面砖，柔光，浅灰细石纹，750×1500mm。")
    assert body[2].startswith("墙面：铺贴抛光砖，亮光，米白大理石纹，600×1200mm。")
    assert body[3] == "陈设：马桶、洗手台、淋浴房。"
    assert body[4] == f"{'；'.join(CONSTRAINTS)}。"


def test_furnish_text_is_used_verbatim():
    """陈设**原样**拼进去,不改写 —— 后台配什么就是什么。"""
    configured = FurnishConfig(prompt="一张三人沙发、圆形茶几", configured=True)
    prompt = assemble_prompt(
        space="living_room", style="nordic", parts=(), furnish=configured,
    )
    assert "一张三人沙发、圆形茶几" in prompt


def test_space_and_style_come_from_the_dictionary_not_hardcoded():
    """空间/风格走后台字典(D-14);内置表里没有的键**退化为键本身**,不炸。"""
    prompt = assemble_prompt(space="attic", style="wabi_sabi", parts=())
    assert "attic空间的wabi_sabi风格装修效果图。" in prompt


def test_builtin_labels_cover_the_contract_enums():
    """内置默认值必须与契约枚举一致(openapi-common.yaml 的 SpaceType / StyleType)。"""
    assert set(SPACE_LABELS) == {
        "living_room", "bedroom", "kitchen", "bathroom",
        "dining_room", "study", "balcony", "other",
    }
    assert set(STYLE_LABELS) == {"modern", "light_luxury", "cream", "nordic"}
    assert set(PART_LABELS) == set(PARTS)


# ----------------------------------------------------------------------
# 陈设缺失降级(架构 §13.3)
# ----------------------------------------------------------------------

def test_missing_furnish_degrades_instead_of_failing(caplog):
    """未配置 → 跳过陈设段 + WARN + furnishConfigured=false,生成照常。"""
    with caplog.at_level(logging.WARNING):
        result = build_prompts(space="study", style="modern", parts=(), furnish=None)
    assert result.furnish_configured is False
    assert "陈设：" not in result.views[0].prompt
    assert "陈设未配置" in caplog.text


def test_unconfigured_furnish_is_treated_as_missing():
    """`configured=false` 即视为未配置,哪怕它带了文本 —— 显式而不是靠空串判断。"""
    result = build_prompts(
        space="study", style="modern", parts=(),
        furnish=FurnishConfig(prompt="不该出现", configured=False),
    )
    assert result.furnish_configured is False
    assert "不该出现" not in result.views[0].prompt


def test_furnish_warning_is_logged_once_not_once_per_view(caplog):
    """N 个视角不该刷 N 条同样的 WARN。"""
    with caplog.at_level(logging.WARNING):
        build_prompts(space="study", style="modern", parts=(), furnish=None, angles=3)
    assert caplog.text.count("陈设未配置") == 1


def test_missing_furnish_source_degrades_too():
    """`furnish_source=None`(比如后台接口都还没上线)同样只降级。"""
    result = prepare_prompts(
        space="study", style="modern",
        selections=[a_selection("floor", "TL-2043")],
        sku_source=FakeSkuSource(),
        furnish_source=None,
    )
    assert result.furnish_configured is False


# ----------------------------------------------------------------------
# 视角(D-5)
# ----------------------------------------------------------------------

def test_single_angle_yields_exactly_one_default_view():
    assert views_for_angles(1) == (DEFAULT_VIEW,)
    assert views_for_angles(0) == (DEFAULT_VIEW,)


@pytest.mark.parametrize("angles", [1, 2, 3, 5])
def test_view_count_equals_angle_count(angles):
    """**张数 = 角度数**(D-5 2026-09-30 修订)。

    修订前契约写作 `default` + `angle1`…`angleN`,照抄会出 N+1 张 ——
    与 `angles` 自身"生成角度数"的含义对不上。这条断言就是那个口径的锚。
    """
    views = views_for_angles(angles)
    assert len(views) == angles
    assert views[0] == DEFAULT_VIEW


def test_multi_angle_names_angle1_through_angle_n_minus_1():
    """`default` 占掉第一张,故 N 张的最后一个视角是 `angle{N-1}`。"""
    assert views_for_angles(2) == ("default", "angle1")
    assert views_for_angles(3) == ("default", "angle1", "angle2")


def test_angle_views_get_a_distinct_hint_but_share_the_scene():
    """多视角的**场景部分完全相同**,只有视角短语不同 —— 否则就不是同一个空间了。"""
    parts = map_parts([a_selection("floor", "TL-2043")], FakeSkuSource())
    result = build_prompts(space="kitchen", style="modern", parts=parts, angles=2)

    assert [v.view for v in result.views] == ["default", "angle1"]
    scene = [line for line in result.views[0].prompt.splitlines() if not line.startswith("视角：")]
    for view in result.views[1:]:
        assert "视角：" in view.prompt
        assert [line for line in view.prompt.splitlines() if not line.startswith("视角：")] == scene


# ----------------------------------------------------------------------
# 边界(PRD §10)
# ----------------------------------------------------------------------

def test_no_price_ever_reaches_a_prompt():
    """陈设不计入报价,而报价字段也不该出现在提示词里(PRD §10 边界)。"""
    result = prepare_prompts(
        space="bathroom", style="modern",
        selections=[a_selection("floor", "TL-2043"), a_selection("wall", "TL-3012")],
        sku_source=FakeSkuSource(),
        furnish_source=FakeFurnishSource(),
    )
    for view in result.views:
        for price in ("268", "198.5", "88"):
            assert price not in view.prompt
        assert "元" not in view.prompt


def test_result_carries_no_amount_field():
    """结构性保证:结果里根本没有金额字段可填 —— 陈设不进报价。"""
    result = prepare_prompts(
        space="bathroom", style="modern",
        selections=[a_selection("floor", "TL-2043")],
        sku_source=FakeSkuSource(),
    )
    assert isinstance(result, AssembledPrompts)
    fields = set(vars(result))
    assert not {f for f in fields if "amount" in f or "price" in f or "fee" in f}


def test_prepare_reports_skus_it_could_not_resolve():
    """取不到详情的 SKU 汇总交回编排层(供 WARN/监控),而不是悄悄吞掉。"""
    result = prepare_prompts(
        space="bathroom", style="modern",
        selections=[a_selection("floor", "TL-2043"), a_selection("wall", "TL-404")],
        sku_source=FakeSkuSource(),
    )
    assert result.missing_skus == ("TL-404",)


@pytest.mark.parametrize("part", PARTS)
def test_every_part_label_renders(part):
    """三个部位都要能出现在提示词里(枚举恒为 floor/wall/local,一期不含美缝)。"""
    parts = tuple(PartMaterial(part=p, sku="TL-2043", phrases=map_material(SKUS["TL-2043"]))
                  for p in (part,))
    assert f"{PART_LABELS[part]}：铺贴" in assemble_prompt(
        space="kitchen", style="modern", parts=parts,
    )
