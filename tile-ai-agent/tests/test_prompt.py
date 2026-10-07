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
    ANGLE_FALLBACK_PHRASE,
    BASE_IMAGE_CLAUSE,
    CONSTRAINTS,
    DEFAULT_VIEW,
    MAX_ANGLES,
    MULTI_ANGLE_CLAUSE,
    PARTS,
    PART_LABELS,
    REF_BINDING_CLAUSE,
    SPACE_LABELS,
    STYLE_LABELS,
    VIEW_PHRASES,
    AssembledPrompts,
    FurnishConfig,
    MaterialPhrase,
    PartMaterial,
    assemble_prompt,
    build_prompts,
    map_material,
    map_parts,
    prepare_prompts,
    view_phrase,
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
    # ⚠️ 2026-10-03 起多两段:**场景锚点**(紧跟主句,是对"空间类型"的展开)
    # 与**收口句**(参考图绑定 + 约束)。成分与顺序不变,只是每一项说得更死。
    assert body[1].startswith("场景：一个完整连续的室内空间")
    # 部位行带上产品名 —— 它是"两款属性完全相同的砖"之间唯一的记号
    assert body[2] == "地面：铺贴瓷砖（雅灰石纹，釉面砖，柔光，浅灰细石纹，750×1500mm）。"
    assert body[3] == "墙面：铺贴瓷砖（米白大理石，抛光砖，亮光，米白大理石纹，600×1200mm）。"
    assert body[4] == "陈设：马桶、洗手台、淋浴房。"
    assert body[5].startswith("视角：")
    # 没有参考图编号 ⇒ 不写绑定句(见下一条用例),约束句直接收口
    assert body[6] == f"{'；'.join(CONSTRAINTS)}。"


# ----------------------------------------------------------------------
# 2026-10-03:部位绑定与场景锚点 —— 治"砖铺错部位"与"每次像不同房间"
# ----------------------------------------------------------------------

def test_reference_index_is_written_into_the_part_line():
    """**第几张参考图铺哪个部位,必须写进提示词。**

    这是"砖被画到错误部位"的直接解药:原先提示词里**没有任何一句**
    把"第几张参考图"和"哪个部位"连起来,模型只能猜。
    """
    parts = map_parts(
        [a_selection("floor", "TL-2043"), a_selection("wall", "TL-3012")],
        FakeSkuSource(),
    )
    prompt = assemble_prompt(
        space="bathroom", style="modern", parts=parts,
        ref_index={"floor": 1, "wall": 2},
    )

    assert "地面：铺贴参考图 1 的瓷砖（" in prompt
    assert "墙面：铺贴参考图 2 的瓷砖（" in prompt
    assert REF_BINDING_CLAUSE in prompt


def test_without_reference_index_no_binding_clause():
    """拿不到编号时**不写**绑定句 —— 一句无法执行的"以参考图为准"只会稀释指令。"""
    parts = map_parts([a_selection("floor", "TL-2043")], FakeSkuSource())

    prompt = assemble_prompt(space="bathroom", style="modern", parts=parts)

    assert "参考图" not in prompt
    assert REF_BINDING_CLAUSE not in prompt


def test_base_image_form_drops_reference_index_and_binding_clause():
    """基准图形态:**参考图只有一张房间图**,故不能再写「参考图 N = 某个部位」(§64)。

    ⚠️ 这是本次最危险的一处 —— 参考图清单变了而提示词没跟着变,**不会报错**,
    只会把一句假话喂给模型(参考图 1 现在是整间房,不是地面砖)。
    """
    parts = map_parts(
        [a_selection("floor", "TL-2043"), a_selection("wall", "TL-3012")],
        FakeSkuSource(),
    )
    prompt = assemble_prompt(
        space="bathroom", style="modern", parts=parts,
        ref_index={"floor": 1, "wall": 2},  # 传了也**必须被忽略**
        base_image=True,
    )

    assert "铺贴参考图 1 的" not in prompt
    assert "铺贴参考图 2 的" not in prompt
    assert REF_BINDING_CLAUSE not in prompt
    assert BASE_IMAGE_CLAUSE in prompt
    assert "地面：铺贴瓷砖（" in prompt, "部位行还在,只是不再报编号"


def test_base_image_form_keeps_material_and_product_name():
    """丢的只是**编号**,不是"铺的是什么砖" —— 产品名与材质短语必须原样保留。"""
    parts = map_parts([a_selection("floor", "TL-2043")], FakeSkuSource())

    prompt = assemble_prompt(
        space="bathroom", style="modern", parts=parts, base_image=True,
    )

    assert "雅灰石纹" in prompt, "产品名是区分两块同款属性砖的唯一记号,不能丢"
    assert "釉面砖" in prompt


def test_anchor_first_view_gives_only_the_first_view_the_slice_form():
    """★ 只有第 1 张走切片形态,第 2 张起走基准图形态。"""
    parts = map_parts(
        [a_selection("floor", "TL-2043"), a_selection("wall", "TL-3012")],
        FakeSkuSource(),
    )
    result = build_prompts(
        space="bathroom", style="modern", parts=parts, angles=3,
        ref_index={"floor": 1, "wall": 2}, anchor_first_view=True,
    )

    first, *later = [view.prompt for view in result.views]
    assert "铺贴参考图 1 的" in first
    assert "基准图" not in first, "第 1 张是基准图的来源,此时还没有基准图可依"
    assert later, "angles=3 应当有后续视角"
    for text in later:
        assert "铺贴参考图" not in text
        assert "基准图" in text


def test_anchor_first_view_is_a_noop_for_a_single_angle():
    """angles=1 时 `anchor_first_view` 必须**什么都不改变**(回归)。"""
    parts = map_parts([a_selection("floor", "TL-2043")], FakeSkuSource())
    kwargs = dict(
        space="bathroom", style="modern", parts=parts, angles=1,
        ref_index={"floor": 1},
    )

    on = build_prompts(**kwargs, anchor_first_view=True).views[0].prompt
    off = build_prompts(**kwargs, anchor_first_view=False).views[0].prompt

    assert on == off


def test_room_dimensions_are_spelled_out():
    """尺寸必须**写出来**:模型看不到 `layout`,只写"与给定尺寸一致"等于没给。"""
    prompt = assemble_prompt(
        space="bathroom", style="modern", parts=(),
        room={"type": "bathroom", "length": 2, "width": 1.8, "height": 2.4},
    )

    assert "长约 2 米，宽 1.8 米，层高 2.4 米" in prompt


def test_missing_room_does_not_invent_numbers():
    """拿不到户型 ⇒ 只说"一个房间",**不编尺寸**。"""
    prompt = assemble_prompt(space="bathroom", style="modern", parts=())

    scene = prompt.splitlines()[1]
    assert scene.startswith("场景：一个完整连续的室内空间；")
    assert "米" not in scene


def test_identical_tiles_are_distinguished_by_name():
    """两款**属性完全相同**的砖,靠产品名区分。

    2026-10-03 实测踩到过:地面与墙面两行的材质短语一模一样,
    提示词层面根本没有信息能说明"哪块砖铺哪里"。
    """
    same = {
        "material": "釉面砖", "surface": "柔光",
        "colorPattern": "浅灰细石纹", "spec": "750×1500mm",
    }
    skus = {
        "TL-A": {"sku": "TL-A", "name": "思路雪影", **same},
        "TL-B": {"sku": "TL-B", "name": "白雪", **same},
    }
    parts = map_parts(
        [a_selection("floor", "TL-A"), a_selection("wall", "TL-B")],
        FakeSkuSource(skus),
    )
    prompt = assemble_prompt(space="bathroom", style="modern", parts=parts)
    floor_line, wall_line = prompt.splitlines()[2], prompt.splitlines()[3]

    assert "思路雪影" in floor_line and "白雪" in wall_line
    assert floor_line != wall_line, "两行必须不同 —— 否则模型无从区分"


def test_product_name_is_not_exposed_to_the_contract():
    """`name` 只进提示词,**不进 material-map 的契约响应**(不给契约加字段)。"""
    parts = map_parts([a_selection("floor", "TL-2043")], FakeSkuSource())

    assert parts[0].name == "雅灰石纹"
    assert "name" not in parts[0].to_contract()


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
#
# ⚠️ **2026-10-07**:契约已把 `angles` 上限锁为 1(§74),所以下面以
# `angles=2/3` 直接调 `build_prompts` / `views_for_angles` 的用例,
# **验的是模块能力,不是可达路径** —— 契约层面进不来了。
# 这些能力**刻意保留**(多机位底图恢复多角度时要用),故用例一并保留。
# "上限确实是 1"这件事由上面那两条用例钉住。
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
# 视角语义:同一张照片的不同机位(2026-09-30 定,见 §33)
# ----------------------------------------------------------------------

def test_every_named_view_has_its_own_camera_position():
    """机位表里每个机位各不相同 —— 重复的机位只会得到一张近乎一样的图,白花一次生成。

    ⚠️ 遍历的是**机位表本身**,不是 `views_for_angles(MAX_ANGLES)` ——
    上限 2026-10-07 已锁为 1(§74),拿它当表长会只看到 `default` 一项。
    """
    names = tuple(VIEW_PHRASES)
    assert len(names) == 4, "四个机位是保留资产,见下一条用例"
    phrases = [view_phrase(name) for name in names]
    assert len(set(phrases)) == len(names)
    assert all(phrases)


def test_angles_ceiling_is_locked_to_one_and_the_camera_table_is_kept():
    """**上限锁为 1,但机位表保留。** 两者不再相等,这是有意的。

    2026-10-07 用户拍板:每个空间只出一张效果图(理由见 `openapi-verification.md` §74)。
    机位表不删 —— 它是"同一间房的不同机位"的现成资产,将来用**多机位底图**
    恢复多角度时可直接启用。

    这条断言同时钉住两件事:
    ① 上限确实是 1(与契约三处 `maximum` 一致);
    ② 机位表**没有被顺手删掉** —— 删了就是把回头路堵死,而且看不出代价。
    """
    assert MAX_ANGLES == 1
    assert views_for_angles(MAX_ANGLES) == (DEFAULT_VIEW,)
    assert DEFAULT_VIEW in VIEW_PHRASES
    assert len(VIEW_PHRASES) > MAX_ANGLES, "机位表刻意比上限长:它是能力,上限是产品决定"


def test_camera_positions_are_ordered_from_overview_to_detail():
    """第 1 张是"一眼看全"的定妆照,往后依次强调空间关系、地面、墙面。

    顺序有业务含义:用户只要 1 张时拿到的必须是最有信息量的那张,
    而不是随机一个机位。
    """
    first = view_phrase(DEFAULT_VIEW)
    assert "全景" in first
    assert "俯瞰" in view_phrase("angle2")
    assert "墙面" in view_phrase("angle3")


def test_multi_angle_tells_the_model_the_tiles_must_not_change():
    """多张图得是"同一张照片的不同角度" —— 这条不变量要写进提示词,
    否则模型很可能每张都重新布置一次房间,"多角度"就退化成"同风格的几张图"。"""
    parts = map_parts([a_selection("floor", "TL-2043")], FakeSkuSource())
    multi = build_prompts(space="kitchen", style="modern", parts=parts, angles=2)
    single = build_prompts(space="kitchen", style="modern", parts=parts, angles=1)

    assert all(MULTI_ANGLE_CLAUSE in view.prompt for view in multi.views)
    # 单张没有"不同的角度"可比,不该出现这句
    assert MULTI_ANGLE_CLAUSE not in single.views[0].prompt


def test_angles_beyond_the_defined_positions_fall_back_not_crash():
    """要的机位超出已定表 → 兜底措辞,不臆造新机位、也不报错。"""
    result = build_prompts(
        space="kitchen", style="modern",
        parts=map_parts([a_selection("floor", "TL-2043")], FakeSkuSource()),
        angles=5,
    )
    assert result.views[-1].view == "angle4"
    assert ANGLE_FALLBACK_PHRASE in result.views[-1].prompt


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
