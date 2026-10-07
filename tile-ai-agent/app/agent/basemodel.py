"""按户型数据画一张**透视"白模"** —— 出图时当第一张参考图用。

它解决什么问题
--------------
出效果图时,房间长什么样原先**全靠模型编** —— 同一个户型点两次得到两间不同的房。
把房间的几何**先按精确尺寸画出来喂给它**,几何就锁住了。实测(见
``openapi-verification.md`` §77):同一输入连跑 4 次,**房间形状、门的数量与位置、
窗的数量与位置全部一致**;而不给白模时房间是模型编的。

画的是什么
----------
一个**一点透视**的空房间:地面、天花板、后墙、左右墙各填不同灰度,再挖出门窗洞,
最后描一圈深色边 —— 描边是为了让模型一眼看出"**这是结构图,不是一张拍得很素的照片**"。
实测:不描边、且提示词里不说这是白模时,**模型会把后墙上的门画成一面镜子**。

它在本项目里的位置
------------------
与 ``prompt.py`` 同级、同性质:输入结构化数据、输出确定性产物、**不取数、不调外部服务**
(不变量 5:确定性映射不交给模型)。它**不碰网络**,也**不落盘** —— 输出字节,
由调用方转成 base64 data URI 直接当参考图发出去(实测该接口认 base64)。

⚠️ 为什么不用 Pillow / numpy
---------------------------
本服务**没有任何图像依赖**(``requirements.txt`` 里没有),而本仓库有一条成文规矩:
**直接依赖不该靠别人间接带进来**(httpx 与 PyYAML 都因此被提升为显式声明)。
所以这里用 **stdlib 的 zlib 手写 PNG + 自己写扫描线填色** —— 零新依赖。

⚠️ 目前只支持**矩形房间**
-----------------------
原型画的是固定的四壁矩形。户型里 ``wallCount`` 可以不等于 4,那时画出来的几何是**错的** ——
而白模是要去指挥模型的"权威",错的几何比没有更糟。故 ``wallCount`` 存在且 ≠ 4 时
**返回 ``None``**(调用方降级回纯瓷砖参考图)并记 WARN。
"""

from __future__ import annotations

import base64
import logging
import math
import struct
import zlib
from typing import Any, Mapping, Sequence

logger = logging.getLogger(__name__)

# ----------------------------------------------------------------------
# 画布与相机
# ----------------------------------------------------------------------

WIDTH_PX = 1024
HEIGHT_PX = 768

#: 相机在房间**前门外**这么远,朝后墙看。退远一点免得透视太夸张。
CAMERA_Z = -1.6
#: 相机高度(米):真人举着手机的高度。
CAMERA_Y = 1.5
#: **略微低头**。不低头的话地面会被压成画面底部一条窄缝 ——
#: 而地面正是"贴地砖"要看的那个面。真人拍室内照也是略微向下的。
CAMERA_PITCH_DEG = 10.0
#: 焦距(像素)的**兜底值** —— 正常路径由 `_focal_for()` 按房间尺寸算。
FOCAL = 820.0

#: **焦距由房间尺寸算出来,不是一个固定值。**
#:
#: ⚠️ 固定焦距是踩过的坑:房间一大(实测 **5×4 的客厅**),侧墙就被挤出画面,
#: **右墙上那扇窗只剩一半** —— 而白模是要去指挥模型的"权威",看到一半的窗
#: 等于只给了半个约束。
#:
#: 做法:让**后墙大约占画面宽度的 `BACK_WALL_SPAN`**,再夹进一个合理区间。
BACK_WALL_SPAN = 0.62
FOCAL_MIN = 380.0
FOCAL_MAX = 1500.0


def _focal_for(width: float, length: float, height: float) -> float:
    """按房间尺寸定焦距。规则:**只往外拉,不往里推。**

    要同时满足两条"装得下":

    * **横向** —— 后墙别超出画面左右。后墙在深度 ``length - CAMERA_Z`` 处、
      半宽 ``width / 2``,投影半宽是 ``focal * (width/2) / 深度``。
    * **纵向** —— 后墙**整面**(从地面到天花)别超出画面上下。相机在 1.5 米高、
      略微低头,所以**天花是最先出画的那一头**。

    ⚠️ **取 ``min(默认, 两条装得下的上限)`` —— 这个 ``min`` 是踩过坑才有的。**
    第一版写成了"让后墙**正好**占 62%",于是**小房间被推近**:实测卫生间
    焦距从 820 涨到 1270,后墙糊满画面、**地面被挤没** —— 而地面正是贴地砖要看的那个面。
    ``min`` 之后,常见尺寸都落在默认焦距上(**取景与改动前逐像素相同**)。

    ⚠️ **纵向那条是后补的,因为撞见了:** 层高一旦超过约 2.6 米,**天花就出了画面**
    (实测 2×1.8×10 的房间画出来跟普通房间一模一样,看不出它很高)。
    常见层高 2.8 米也会中招 —— 模型就无从知道这房间到底多高。
    (缓解:提示词里**另有文字**写着层高,所以不是全丢。)

    ⚠️ 它**解决不了**的:宽房间**侧墙靠近镜头的那一端**必然出画。
    那是站在门口往里看的物理事实(真人拿手机拍也一样),不是 bug。
    """
    depth = max(length - CAMERA_Z, 1e-6)

    fits_h = WIDTH_PX * BACK_WALL_SPAN / 2.0 * depth / max(width / 2.0, 1e-6)

    # 后墙顶(离相机最远、最高的那个点)的纵向投影
    dy, dz = _rotate_pitch(height - CAMERA_Y, depth)
    fits_v = (HEIGHT_PX / 2.0) * dz / dy if dy > 1e-6 else float("inf")

    return max(FOCAL_MIN, min(FOCAL, fits_h, fits_v))

#: 各面的灰度(0-255)。近白,靠灰度差把几个面区分开。
SHADE = {
    "back": 238,
    "left": 196,
    "right": 214,
    "floor": 176,
    "ceiling": 248,
    "opening": 96,  # 门窗洞:通向别处,所以是暗的
    "edge": 70,  # 描边
}
FILL = 250  # 房间外的底色

#: 描边线宽(像素)。
EDGE_WIDTH = 3.5

#: 缺省窗台高(米)。门没有这个概念(恒 0);窗没给 ``sill`` 时用这个。
DEFAULT_SILL_M = 0.9

#: 支持的墙面。契约 ``OpeningWall`` 就是这三个 —— **没有 front**:
#: 相机在房间外,前墙在镜头背后,一点透视里永远看不到。
WALLS = ("back", "left", "right")


# ----------------------------------------------------------------------
# 绘制原语(纯 stdlib)
# ----------------------------------------------------------------------

def _rotate_pitch(dy: float, dz: float) -> tuple[float, float]:
    """绕 x 轴旋转 —— 等价于相机低头。"""
    a = math.radians(CAMERA_PITCH_DEG)
    return dy * math.cos(a) + dz * math.sin(a), -dy * math.sin(a) + dz * math.cos(a)


def _project(x: float, y: float, z: float, focal: float) -> tuple[float, float]:
    """世界坐标(米)→ 屏幕像素。一点透视 + 相机低头。"""
    dy, dz = _rotate_pitch(y - CAMERA_Y, z - CAMERA_Z)
    return WIDTH_PX / 2.0 + focal * x / dz, HEIGHT_PX / 2.0 - focal * dy / dz


def _fill_quad(gray: bytearray, quad: Sequence[tuple[float, float]], value: int) -> None:
    """把一个四边形填成指定灰度 —— **扫描线填充**(even-odd)。

    纯 Python。用扫描线而不是逐像素判内外,是因为前者每行只算几次边交点,
    后者要算满整张图(1024×768 × 每个面);跨度用切片赋值,是 C 级速度。
    """
    n = len(quad)
    if n < 3:
        return
    ys = [p[1] for p in quad]
    y0 = max(0, int(math.floor(min(ys))))
    y1 = min(HEIGHT_PX - 1, int(math.ceil(max(ys))))

    for y in range(y0, y1 + 1):
        yc = y + 0.5
        crossings: list[float] = []
        for i in range(n):
            ax, ay = quad[i]
            bx, by = quad[(i + 1) % n]
            if (ay <= yc < by) or (by <= yc < ay):
                crossings.append(ax + (yc - ay) / (by - ay) * (bx - ax))
        if len(crossings) < 2:
            continue
        crossings.sort()
        for i in range(0, len(crossings) - 1, 2):
            xa = max(0, int(math.ceil(crossings[i] - 0.5)))
            xb = min(WIDTH_PX - 1, int(math.floor(crossings[i + 1] - 0.5)))
            if xb >= xa:
                start = y * WIDTH_PX + xa
                gray[start : start + (xb - xa + 1)] = bytes([value]) * (xb - xa + 1)


def _draw_line(gray: bytearray, p1: tuple[float, float], p2: tuple[float, float],
               value: int, width: float = EDGE_WIDTH) -> None:
    """画一条有粗细的直线(用一个细长的四边形填出来)。"""
    (x1, y1), (x2, y2) = p1, p2
    dx, dy = x2 - x1, y2 - y1
    length = math.hypot(dx, dy)
    if length < 1e-6:
        return
    nx, ny = -dy / length * width / 2.0, dx / length * width / 2.0
    _fill_quad(
        gray,
        [(x1 + nx, y1 + ny), (x2 + nx, y2 + ny), (x2 - nx, y2 - ny), (x1 - nx, y1 - ny)],
        value,
    )


def _write_png_gray(gray: bytearray) -> bytes:
    """把 8 位灰度像素写成 PNG(color type 0)。只用 stdlib 的 zlib。"""
    raw = bytearray()
    for y in range(HEIGHT_PX):
        raw.append(0)  # 每行的 filter type:0 = None
        raw += gray[y * WIDTH_PX : (y + 1) * WIDTH_PX]

    def chunk(tag: bytes, data: bytes) -> bytes:
        body = tag + data
        return (
            struct.pack(">I", len(data))
            + body
            + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
        )

    out = b"\x89PNG\r\n\x1a\n"
    out += chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH_PX, HEIGHT_PX, 8, 0, 0, 0, 0))
    out += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    out += chunk(b"IEND", b"")
    return out


# ----------------------------------------------------------------------
# 门窗定位:把 (wall, offset, sill) 换成世界坐标
# ----------------------------------------------------------------------

def _opening_center(opening: Mapping[str, Any], half_w: float, length: float) -> Mapping[str, Any] | None:
    """算出一个开口的**中心点**在世界坐标里的位置。

    契约 ``OpeningWall`` 的「0 点」约定(与架构 §8.3 的表一致):

    ==========  ==========================  ==========
    wall        0 点从哪算起                 方向
    ==========  ==========================  ==========
    ``back``    ``x = -半宽``(画面左下角)    沿 ``+x``
    ``left``    ``z = 0``(离镜头最近的前角)   沿 ``+z``
    ``right``   ``z = 0``(离镜头最近的前角)   沿 ``+z``
    ==========  ==========================  ==========

    **拿不到位置就返回 ``None``** —— 调用方跳过这个洞(照画房间),不去猜。
    """
    wall = opening.get("wall")
    offset = opening.get("offset")
    if wall not in WALLS or offset is None:
        return None
    try:
        offset = float(offset)
    except (TypeError, ValueError):
        return None

    sill = opening.get("sill")
    if sill is None:
        # 门没有台高;窗有个常见的默认值。**放在一处**,别让调用方各猜各的。
        sill = 0.0 if str(opening.get("type")) == "door" else DEFAULT_SILL_M
    try:
        sill = float(sill)
    except (TypeError, ValueError):
        sill = 0.0

    try:
        w = float(opening["w"])
        h = float(opening["h"])
    except (KeyError, TypeError, ValueError):
        return None

    # ⚠️ **洞必须整扇落在墙里。** 用户是**自己填**「哪面墙 + 距墙角多少米」的,
    # 随手填个"距墙角 5 米"而墙只有 1.8 米,太正常了。
    #
    # 不检查会出两种难看的结果(都实测过):
    #   ① 洞**比墙还宽**(1.6 米的推拉门放在 1.4 米的墙上)—— 会从墙里伸到相邻墙面上;
    #   ② 洞**位置超出墙**(门 0.8 米、位置填 1.9,墙 1.8 米)—— 会画出一扇
    #      **一半在墙外**的门;填得更远则整扇飘在房间外、图上什么都看不见。
    #   而白模是拿去指挥模型的"权威",**画歪的比没有更糟**。
    #
    # ⚠️ **契约层面挡不住这件事**:`offset` 能写 `minimum: 0`,但它的上限取决于
    # 那面墙多长 —— 那是 schema 表达不了的。所以只能在这里挡。
    # ⇒ 跳过这一个洞、照画房间,并**记 WARN**(与"缺位置"走同一条降级路径)。
    #     ⚠️ 一定要记:否则就是"用户填了门、图上没有、日志里也不说"。
    wall_length = half_w * 2 if wall == "back" else length
    low, high = offset - w / 2.0, offset + w / 2.0
    if low < -1e-9 or high > wall_length + 1e-9:
        logger.warning(
            "白模:开口放不下,未画 —— %s 宽 %.2f 米、中心距墙角 %.2f 米,"
            "而这面墙只有 %.2f 米(需要 %.2f~%.2f 米都在墙内)",
            opening.get("type"), w, offset, wall_length, low, high,
        )
        return None

    if wall == "back":
        return {"axis": "back", "fixed": length, "center": -half_w + offset, "w": w, "sill": sill, "h": h}
    if wall == "left":
        return {"axis": "left", "fixed": -half_w, "center": offset, "w": w, "sill": sill, "h": h}
    return {"axis": "right", "fixed": half_w, "center": offset, "w": w, "sill": sill, "h": h}


def _opening_quad(placed: Mapping[str, Any], focal: float) -> list[tuple[float, float]]:
    """把一个已定位的开口转成屏幕上的四边形。"""
    a = placed["center"] - placed["w"] / 2.0
    b = placed["center"] + placed["w"] / 2.0
    s0, s1 = placed["sill"], placed["sill"] + placed["h"]
    if placed["axis"] == "back":
        z = placed["fixed"]
        return [_project(a, s0, z, focal), _project(b, s0, z, focal),
                _project(b, s1, z, focal), _project(a, s1, z, focal)]
    x = placed["fixed"]
    return [_project(x, s0, a, focal), _project(x, s0, b, focal),
            _project(x, s1, b, focal), _project(x, s1, a, focal)]


# ----------------------------------------------------------------------
# 对外入口
# ----------------------------------------------------------------------

def render_basemodel(room: Mapping[str, Any] | None) -> bytes | None:
    """按一个空间的数据画白模,**返回 PNG 字节**;画不了就返回 ``None``。

    返回 ``None`` 的两种情况(**都是降级,不是错误**):

    * 没有房间数据 / 尺寸不合法 —— 无从下笔;
    * ``wallCount`` 存在且 ≠ 4 —— 本函数只支持矩形房间,画异形会得到**错的几何**,
      而错的白模会去指挥模型,比没有更糟。

    :param room: 消息体里的那个空间(``length`` / ``width`` / ``height`` / ``openings``)
    """
    if not isinstance(room, Mapping):
        return None

    try:
        length = float(room["length"])
        width = float(room["width"])
        height = float(room["height"])
    except (KeyError, TypeError, ValueError):
        logger.warning("白模:房间尺寸不全,跳过(%s)", dict(room) if isinstance(room, Mapping) else room)
        return None
    if min(length, width, height) <= 0:
        logger.warning("白模:房间尺寸非正(长=%s 宽=%s 高=%s),跳过", length, width, height)
        return None

    wall_count = room.get("wallCount")
    if wall_count is not None and int(wall_count) != 4:
        logger.warning(
            "白模:本函数只支持矩形房间(四面墙),当前 wallCount=%s —— 跳过白模,"
            "本次退回纯瓷砖参考图(架构 §8.3)",
            wall_count,
        )
        return None

    half_w = width / 2.0
    gray = bytearray([FILL]) * (WIDTH_PX * HEIGHT_PX)
    focal = _focal_for(width, length, height)

    def pt(x: float, y: float, z: float) -> tuple[float, float]:
        return _project(x, y, z, focal)

    # 五个面(后墙先画,其它面会盖住它多余的边)
    _fill_quad(gray, [pt(-half_w, 0, length), pt(half_w, 0, length),
                      pt(half_w, height, length), pt(-half_w, height, length)], SHADE["back"])
    _fill_quad(gray, [pt(-half_w, height, 0), pt(half_w, height, 0),
                      pt(half_w, height, length), pt(-half_w, height, length)], SHADE["ceiling"])
    _fill_quad(gray, [pt(-half_w, 0, 0), pt(half_w, 0, 0),
                      pt(half_w, 0, length), pt(-half_w, 0, length)], SHADE["floor"])
    _fill_quad(gray, [pt(-half_w, 0, 0), pt(-half_w, 0, length),
                      pt(-half_w, height, length), pt(-half_w, height, 0)], SHADE["left"])
    _fill_quad(gray, [pt(half_w, 0, 0), pt(half_w, 0, length),
                      pt(half_w, height, length), pt(half_w, height, 0)], SHADE["right"])

    # 门窗洞
    skipped = 0
    for opening in room.get("openings") or []:
        if not isinstance(opening, Mapping):
            skipped += 1
            continue
        placed = _opening_center(opening, half_w, length)
        if placed is None:
            skipped += 1
            continue
        _fill_quad(gray, _opening_quad(placed, focal), SHADE["opening"])

    if skipped:
        # 降级不阻断(架构 §13.3):房间照画,只是少了这个洞。
        logger.warning("白模:有 %d 个开口没画(缺位置,或宽超过它所在的墙)", skipped)

    # 描边 —— 让模型看出"这是结构图",不是一张拍得很素的照片
    corners = {
        (sx, sy, sz): pt(sx * half_w, sy * height, sz * length)
        for sx in (-1, 1)
        for sy in (0, 1)
        for sz in (0, 1)
    }
    for a in corners:
        for b in corners:
            if sum(1 for i in range(3) if a[i] != b[i]) == 1 and a < b:
                _draw_line(gray, corners[a], corners[b], SHADE["edge"])

    for opening in room.get("openings") or []:
        if not isinstance(opening, Mapping):
            continue
        placed = _opening_center(opening, half_w, length)
        if placed is None:
            continue
        quad = _opening_quad(placed, focal)
        for i in range(len(quad)):
            _draw_line(gray, quad[i], quad[(i + 1) % len(quad)], SHADE["edge"])

    return _write_png_gray(gray)


def basemodel_data_uri(room: Mapping[str, Any] | None) -> str | None:
    """画白模并转成 **base64 data URI** —— 直接就能当参考图发出去。

    为什么是 data URI 而不是 URL:白模是**现画在本机内存里**的,没有对外托管;
    而参考图是要发给**外部**图像模型的。实测(§77.6)该接口**认 base64**,
    于是整条上传/托管/建表的链路都可以省掉。
    """
    png = render_basemodel(room)
    if png is None:
        return None
    return "data:image/png;base64," + base64.b64encode(png).decode("ascii")
