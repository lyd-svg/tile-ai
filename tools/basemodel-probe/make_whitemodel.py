"""
按户型尺寸生成"透视白模"图 —— 一次零依赖的实验。

目的
----
验证一个假设:**把房间的几何形状(尺寸 + 门窗位置)先按精确尺寸画出来喂给图像模型,
是不是比"只给几张瓷砖照片、让模型凭空想象整个房间"要准。**

现在的做法只给模型瓷砖照片,房间长什么样全靠它猜 —— 这是"每张像不同的房间"的根因。

为什么自己画、不装库
--------------------
本项目 Python 侧没有绘图依赖。为了**一次实验**去改 `requirements.txt` 不划算,
所以这里只用 numpy + Python 自带的 zlib 手写 PNG 编码(**零新依赖**)。

画的是什么
----------
一个**一点透视**的空房间:地面、天花板、后墙、左右墙,各自填不同灰度的白,
后墙上挖一个门洞、右墙上挖一个窗洞。没有颜色、没有纹理 —— 只有**形状**。

    "白模"只给模型"形状";"长相"(瓷砖与风格)由参考图和提示词决定。

用法
----
    python make_whitemodel.py
产物:`whitemodel.png`(与本文件同目录)
"""

from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path

import numpy as np

#: 产物写到**已被 gitignore 的报告目录**,不写在脚本旁边 ——
#: 否则跑一次就把生成的图塞进版本库了。
HERE = Path(__file__).resolve().parents[2] / "outputs" / "reports" / "basemodel-probe"
HERE.mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------- 房间尺寸(米)
# 取演示库里那条真实户型:卫生间 长 2.0 × 宽 1.8 × 层高 2.4
LENGTH = 2.0  # 进深(z 方向)
WIDTH = 1.8  # 面宽(x 方向)
HEIGHT = 2.4  # 层高(y 方向)

# 门窗(位置是**新增的** —— 现在契约里的 Opening 只有宽高、没有位置,
# 这正是"画不出精确户型图"的原因。这里先写死在脚本里做实验。)
DOOR = {"offset_x": -0.35, "w": 0.8, "h": 2.1}  # 后墙上,距中轴 -0.35m
WINDOW = {"offset_z": 0.9, "w": 1.0, "h": 1.2, "sill": 0.9}  # 右墙上

# ---------------------------------------------------------------- 相机与画布
W_PX, H_PX = 1024, 768
FOCAL = 820.0  # 焦距(像素),越大透视越弱
CAM_Y = 1.5  # 相机高度:真人举着手机的高度(米)
CAM_Z = -1.6  # 相机在房间前门外 1.6m —— 退远一点,免得透视太夸张
PITCH_DEG = 10.0  # **略微低头**。不低头的话地面被压成一条缝,而地面正是要贴砖的地方

# 白模的灰度(0-255)。近白,靠灰度差把几个面区分开。
SHADE = {
    "back": 238,
    "left": 196,
    "right": 214,
    "floor": 176,
    "ceiling": 248,
    "opening": 96,  # 门洞/窗洞:通向别处,所以是暗的
}


def project(x: float, y: float, z: float) -> tuple[float, float]:
    """世界坐标(米)→ 屏幕像素。一点透视 + 相机低头 PITCH_DEG 度。

    低头这一步不是装饰:相机水平时,地面会被压成画面底部一条窄缝 ——
    而地面正是"贴地砖"要看的那个面。真人拍室内照也是略微向下的。
    """
    dx = x
    dy = y - CAM_Y
    dz = z - CAM_Z

    a = math.radians(PITCH_DEG)
    # 绕 x 轴旋转世界(等价于相机低头)
    dy_rot = dy * math.cos(a) + dz * math.sin(a)
    dz_rot = -dy * math.sin(a) + dz * math.cos(a)

    sx = W_PX / 2.0 + FOCAL * dx / dz_rot
    sy = H_PX / 2.0 - FOCAL * dy_rot / dz_rot
    return sx, sy


def _signed_area(quad: np.ndarray) -> float:
    x = quad[:, 0]
    y = quad[:, 1]
    return 0.5 * float(np.sum(x * np.roll(y, -1) - np.roll(x, -1) * y))


def fill_quad(canvas: np.ndarray, pts: list[tuple[float, float]], grey: int) -> None:
    """把一个凸四边形填成指定灰度。纯 numpy 实现,不依赖任何绘图库。"""
    quad = np.array(pts, dtype=float)
    if _signed_area(quad) < 0:  # 统一成逆时针,方便用叉积判内外
        quad = quad[::-1]

    x0 = max(0, int(np.floor(quad[:, 0].min())))
    x1 = min(W_PX - 1, int(np.ceil(quad[:, 0].max())))
    y0 = max(0, int(np.floor(quad[:, 1].min())))
    y1 = min(H_PX - 1, int(np.ceil(quad[:, 1].max())))
    if x1 < x0 or y1 < y0:
        return

    yy, xx = np.mgrid[y0 : y1 + 1, x0 : x1 + 1]
    inside = np.ones(xx.shape, dtype=bool)
    n = len(quad)
    for i in range(n):
        ax, ay = quad[i]
        bx, by = quad[(i + 1) % n]
        inside &= (bx - ax) * (yy - ay) - (by - ay) * (xx - ax) >= 0

    canvas[y0 : y1 + 1, x0 : x1 + 1][inside] = grey


def draw_line(canvas: np.ndarray, p1: tuple[float, float], p2: tuple[float, float], grey: int, width: float = 3.5) -> None:
    """画一条有粗细的直线(用一个细长的四边形填出来)。"""
    x1, y1 = p1
    x2, y2 = p2
    dx, dy = x2 - x1, y2 - y1
    ln = math.hypot(dx, dy)
    if ln < 1e-6:
        return
    nx, ny = -dy / ln * width / 2.0, dx / ln * width / 2.0
    fill_quad(
        canvas,
        [(x1 + nx, y1 + ny), (x2 + nx, y2 + ny), (x2 - nx, y2 - ny), (x1 - nx, y1 - ny)],
        grey,
    )


def write_png(path: Path, rgb: np.ndarray) -> None:
    """把 HxWx3 uint8 写成 PNG。只用 zlib —— 不引入 Pillow。"""
    h, w, _ = rgb.shape
    raw = b"".join(b"\x00" + rgb[y].tobytes() for y in range(h))

    def chunk(tag: bytes, data: bytes) -> bytes:
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


def main() -> None:
    hw, hh = WIDTH / 2.0, HEIGHT

    canvas = np.full((H_PX, W_PX, 3), 250, dtype=np.uint8)  # 房间外的底色(近白)

    # 八个角:z=0 是门口(近),z=LENGTH 是后墙(远)
    def p(x, y, z):
        return project(x, y, z)

    # 后墙(先画,因为其它面会盖住它多余的边)
    fill_quad(canvas, [p(-hw, 0, LENGTH), p(hw, 0, LENGTH), p(hw, hh, LENGTH), p(-hw, hh, LENGTH)], SHADE["back"])
    # 天花板 / 地面
    fill_quad(canvas, [p(-hw, hh, 0), p(hw, hh, 0), p(hw, hh, LENGTH), p(-hw, hh, LENGTH)], SHADE["ceiling"])
    fill_quad(canvas, [p(-hw, 0, 0), p(hw, 0, 0), p(hw, 0, LENGTH), p(-hw, 0, LENGTH)], SHADE["floor"])
    # 左右墙
    fill_quad(canvas, [p(-hw, 0, 0), p(-hw, 0, LENGTH), p(-hw, hh, LENGTH), p(-hw, hh, 0)], SHADE["left"])
    fill_quad(canvas, [p(hw, 0, 0), p(hw, 0, LENGTH), p(hw, hh, LENGTH), p(hw, hh, 0)], SHADE["right"])

    # 后墙上的门洞(半开着,所以画成暗的)
    dx0, dx1 = DOOR["offset_x"] - DOOR["w"] / 2, DOOR["offset_x"] + DOOR["w"] / 2
    fill_quad(
        canvas,
        [p(dx0, 0, LENGTH), p(dx1, 0, LENGTH), p(dx1, DOOR["h"], LENGTH), p(dx0, DOOR["h"], LENGTH)],
        SHADE["opening"],
    )

    # 右墙上的窗洞
    z0, z1 = WINDOW["offset_z"] - WINDOW["w"] / 2, WINDOW["offset_z"] + WINDOW["w"] / 2
    fill_quad(
        canvas,
        [
            p(hw, WINDOW["sill"], z0),
            p(hw, WINDOW["sill"], z1),
            p(hw, WINDOW["sill"] + WINDOW["h"], z1),
            p(hw, WINDOW["sill"] + WINDOW["h"], z0),
        ],
        SHADE["opening"],
    )

    out = HERE / "whitemodel.png"
    write_png(out, canvas)
    print(f"已生成:{out}  (纯色块版)")

    # ---------------- 带描边的版本 ----------------
    # 为什么要加描边:纯灰块可能被模型当成"一张拍得很素的照片",于是按自己的理解
    # 发挥(实测:白模后墙上的门被它画成了镜子)。加上深色描边能让它一眼看出
    # **这是一张结构图,不是照片**。
    edged = canvas.copy()
    EDGE = 70

    corners = {
        (sx, sy, sz): p(sx * hw, sy * hh, sz * LENGTH)
        for sx in (-1, 1)
        for sy in (0, 1)
        for sz in (0, 1)
    }
    # 12 条棱:每一对只差一个坐标的角相连
    for a in corners:
        for b in corners:
            if sum(1 for i in range(3) if a[i] != b[i]) == 1 and a < b:
                draw_line(edged, corners[a], corners[b], EDGE)

    # 门洞与窗洞的轮廓
    for quad in (
        [p(dx0, 0, LENGTH), p(dx1, 0, LENGTH), p(dx1, DOOR["h"], LENGTH), p(dx0, DOOR["h"], LENGTH)],
        [
            p(hw, WINDOW["sill"], z0),
            p(hw, WINDOW["sill"], z1),
            p(hw, WINDOW["sill"] + WINDOW["h"], z1),
            p(hw, WINDOW["sill"] + WINDOW["h"], z0),
        ],
    ):
        for i in range(len(quad)):
            draw_line(edged, quad[i], quad[(i + 1) % len(quad)], EDGE)

    out2 = HERE / "whitemodel_edged.png"
    write_png(out2, edged)
    print(f"已生成:{out2}  (带描边版)")

    print(f"  房间:长 {LENGTH}m × 宽 {WIDTH}m × 层高 {HEIGHT}m")
    print(f"  门(后墙):宽 {DOOR['w']}m 高 {DOOR['h']}m,中心偏 {DOOR['offset_x']}m")
    print(f"  窗(右墙):宽 {WINDOW['w']}m 高 {WINDOW['h']}m,离地 {WINDOW['sill']}m")


if __name__ == "__main__":
    main()
