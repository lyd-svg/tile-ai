"""白模生成器的单测。

它是**纯函数**:同一份房间数据 → 同一串字节,不取数、不调外部服务、不落盘。
所以这一组测试**不需要网络、不需要模型、不花一分钱**。

判据分三类:
  ① **确定性** —— 同输入两次,字节完全相同(这是"确定性"这个卖点的底线);
  ② **降级不阻断** —— 画不了就返回 None,绝不抛异常(架构 §13.3);
  ③ **PNG 合法** —— 产物真的是张图,而不是一段坏字节。
"""

from __future__ import annotations

import base64
import logging

from app.agent.basemodel import (
    DEFAULT_SILL_M,
    basemodel_data_uri,
    render_basemodel,
)

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


def a_room(**overrides):
    room = {
        "type": "bathroom",
        "name": "卫生间",
        "length": 2.0,
        "width": 1.8,
        "height": 2.4,
        "wallCount": 4,
        "openings": [
            {"type": "door", "w": 0.8, "h": 2.1, "wall": "back", "offset": 0.55},
            {"type": "window", "w": 1.0, "h": 1.2, "wall": "right", "offset": 0.9, "sill": 0.9},
        ],
    }
    room.update(overrides)
    return room


# ----------------------------------------------------------------------
# ① 确定性
# ----------------------------------------------------------------------

def test_same_room_renders_identical_bytes():
    """**同一个房间,画两次必须一模一样。**

    这是这个模块存在的理由 —— 用户的痛点就是"大模型不稳定"。如果连白模自己
    每次都不一样,拿它去锁模型的几何就成了笑话。
    """
    assert render_basemodel(a_room()) == render_basemodel(a_room())


def test_different_size_renders_different_image():
    """换个尺寸必须是另一张图 —— 否则"按尺寸画"就是假的。"""
    small = render_basemodel(a_room(length=2.0))
    big = render_basemodel(a_room(length=5.0))
    assert small != big


def test_door_position_changes_the_image():
    """门挪个位置,图就得跟着变 —— 否则位置字段等于没接进来。"""
    # ⚠️ 位置要选**整扇都落在墙内**的(墙 1.8 米、门 0.8 米 ⇒ 中心在 0.4~1.4 之间)。
    left = render_basemodel(a_room(openings=[{"type": "door", "w": 0.8, "h": 2.1,
                                              "wall": "back", "offset": 0.5}]))
    right = render_basemodel(a_room(openings=[{"type": "door", "w": 0.8, "h": 2.1,
                                               "wall": "back", "offset": 1.3}]))
    assert left != right


def test_opening_placed_outside_its_wall_is_skipped_and_warned(caplog):
    """**位置超出墙就不画,而且必须记一条 WARN。**

    用户是**自己填**「哪面墙 + 距墙角多少米」的 —— 随手填个"距墙角 5 米"、
    而那面墙只有 1.8 米,太正常了。

    实测(2026-10-07):不检查的话洞会画到房间外面去,**图上什么都看不见、
    日志里也不说** —— 就是"我明明填了门,图上却没有"。

    ⚠️ **契约层面挡不住**:`offset` 能写 `minimum: 0`,但上限取决于那面墙多长,
    而那是 schema 表达不了的。所以只能在这里挡。
    """
    no_door = render_basemodel(a_room(openings=[]))

    with caplog.at_level(logging.WARNING):
        far = render_basemodel(a_room(openings=[
            {"type": "door", "w": 0.8, "h": 2.1, "wall": "back", "offset": 5.0},
        ]))
    assert far == no_door, "整扇都在墙外的洞应当跳过"
    assert any("放不下" in record.message for record in caplog.records), "跳过必须留痕"

    # 一半在墙外的也要跳过 —— 那会画成一扇斜插在墙角上的门(门 0.8、位置 1.9、墙 1.8)
    half = render_basemodel(a_room(openings=[
        {"type": "door", "w": 0.8, "h": 2.1, "wall": "back", "offset": 1.9},
    ]))
    assert half == no_door

    # 而**刚好贴到墙角**是合法的,不能误伤(门 0.8、中心 1.4 ⇒ 占 1.0~1.8,墙正好 1.8)
    flush = render_basemodel(a_room(openings=[
        {"type": "door", "w": 0.8, "h": 2.1, "wall": "back", "offset": 1.4},
    ]))
    assert flush != no_door, "刚好贴到墙角是合法的,不该被跳过"


# ----------------------------------------------------------------------
# ② 降级不阻断
# ----------------------------------------------------------------------

def test_missing_room_returns_none():
    assert render_basemodel(None) is None
    assert render_basemodel({}) is None


def test_non_positive_size_returns_none():
    assert render_basemodel(a_room(width=0)) is None
    assert render_basemodel(a_room(height=-1)) is None


def test_non_rectangular_room_returns_none():
    """``wallCount`` ≠ 4 时**不画**。

    原型画的是固定矩形;异形房间画出来是**错的几何**,而白模是要去指挥模型的
    "权威" —— 错的比没有更糟。宁可退回纯瓷砖参考图。
    """
    assert render_basemodel(a_room(wallCount=6)) is None


def test_openings_without_position_are_skipped_not_fatal():
    """没有位置的开口被跳过,但**房间照画** —— 降级不阻断(架构 §13.3)。"""
    without = a_room(openings=[{"type": "door", "w": 0.8, "h": 2.1}])
    png = render_basemodel(without)
    assert png is not None and png.startswith(PNG_MAGIC)
    # 和"完全没有门窗"应当是同一张图 —— 因为那个洞确实没画上去
    assert png == render_basemodel(a_room(openings=[]))


def test_unknown_wall_is_skipped():
    """`front` 不在枚举里(相机在房间外,前墙永远看不到)—— 当作没位置处理。"""
    assert render_basemodel(a_room(openings=[{"type": "door", "w": 0.8, "h": 2.1,
                                              "wall": "front", "offset": 0.5}])) \
        == render_basemodel(a_room(openings=[]))


def test_opening_wider_than_its_wall_is_skipped():
    """**洞比墙还宽就画不上去。**

    实测踩到的:一个 1.6 米的推拉门放在 **1.4 米宽**的阳台墙上,渲染器**照样画**,
    洞从墙里伸到相邻墙面上去了。白模是拿去指挥模型的"权威" ——
    **画歪的比没有更糟**,所以宁可跳过这一个洞。
    """
    balcony = dict(
        type="balcony", length=3.6, width=1.4, height=2.6, wallCount=4,
    )
    with_door = render_basemodel({**balcony, "openings": [
        {"type": "door", "w": 1.6, "h": 2.2, "wall": "back", "offset": 1.8},
    ]})
    without = render_basemodel({**balcony, "openings": []})

    assert with_door is not None
    assert with_door == without, "超宽的洞应当被跳过,退化成'没有门窗'那张图"


def test_focal_never_zooms_in_past_the_default():
    """**焦距只往外拉,不往里推。**

    ⚠️ 这条钉的是一个**踩过的坑**:第一版按"让后墙正好占画面 62%"算焦距,
    结果**小房间被推近**(卫生间从 820 涨到 1270),后墙糊满画面、**地面被挤没** ——
    而地面正是贴地砖要看的那个面。

    取 `min(默认, 装得下的值)` 之后:常见尺寸全部落在默认焦距上,
    取景与改动前**逐像素相同**;只有特别大的房间才会被拉远。
    """
    from app.agent.basemodel import FOCAL, _focal_for

    assert _focal_for(1.8, 2.0, 2.4) == FOCAL, "卫生间(2.4 米层高)不该被推近"
    assert _focal_for(1.8, 2.0, 2.8) < FOCAL, "2.8 米层高要让天花进画面,得拉远"
    assert _focal_for(4.0, 5.0, 2.8) == FOCAL, "客厅(深 5 米)天花板进得来,不用拉"
    assert _focal_for(8.0, 3.0, 2.4) < FOCAL, "又宽又浅的房间要拉远"


def test_tall_room_shows_its_ceiling():
    """**层高高的房间必须看得见天花** —— 否则白模传递不出"这房间很高"。

    实测踩到的:2×1.8×10 的房间画出来**跟普通房间一模一样**(天花出了画面),
    模型根本无从知道它有多高。相机在 1.5 米、略微低头,所以**天花是最先出画的**。
    """
    tall = a_room(length=2.0, width=1.8, height=3.5, openings=[])
    normal = a_room(length=2.0, width=1.8, height=2.4, openings=[])

    assert render_basemodel(tall) != render_basemodel(normal), "层高不同,图必须不同"


def test_window_sill_defaults_when_omitted():
    """窗没给 `sill` 时按默认台高画 —— 与显式给同一个值的产物一致。"""
    omitted = a_room(openings=[{"type": "window", "w": 1.0, "h": 1.2,
                                "wall": "right", "offset": 0.9}])
    explicit = a_room(openings=[{"type": "window", "w": 1.0, "h": 1.2,
                                 "wall": "right", "offset": 0.9, "sill": DEFAULT_SILL_M}])
    assert render_basemodel(omitted) == render_basemodel(explicit)


# ----------------------------------------------------------------------
# ③ PNG 合法 + data URI
# ----------------------------------------------------------------------

def test_output_is_a_valid_png():
    png = render_basemodel(a_room())
    assert png.startswith(PNG_MAGIC)
    assert png.rstrip().endswith(b"IEND\xae\x42\x60\x82")
    # 一张 1024×768 的灰度图,压缩后不该超过 200KB —— 超过说明画崩了
    assert 1024 < len(png) < 200 * 1024


def test_data_uri_is_decodable_back_to_the_same_png():
    """data URI 能被解回原字节 —— 发出去的就是画出来的。"""
    png = render_basemodel(a_room())
    uri = basemodel_data_uri(a_room())
    assert uri is not None
    prefix = "data:image/png;base64,"
    assert uri.startswith(prefix)
    assert base64.b64decode(uri[len(prefix):]) == png


def test_data_uri_is_none_when_unrenderable():
    assert basemodel_data_uri(None) is None
    assert basemodel_data_uri(a_room(wallCount=3)) is None
