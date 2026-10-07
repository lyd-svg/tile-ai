"""
第四轮:**去掉提示词里对瓷砖的文字描述**,只保留"照着参考图 2 画"。

假设(用户 2026-10-07 提出)
--------------------------
现在提示词里写着「奥斯银灰,瓷质仿古砖,易洁糖果釉,仿石,800×800mm」。
这段文字可能正在**和参考图打架** —— 模型看到"银灰"这个词,
就按自己心里那个"银灰"去画,而不去看那张照片到底长什么样。

做法:**保留"按参考图 2 铺贴"这条绑定**(那是让模型用图的关键),
**只删掉对砖的文字形容**,并明确要求"照着这张图的颜色与纹路"。

产物:E1_run1.png / E2_run2.png / E3_run3.png —— 用 measure.py 与原瓷砖图比对。
"""

from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parents[2] / "outputs" / "reports" / "basemodel-probe"
HERE.mkdir(parents=True, exist_ok=True)
#: ⚠️ 导入兄弟脚本用的是**脚本自己所在目录**,不是产物目录。
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from probe import TILE_URL, data_uri, load_key  # noqa: E402
from probe2 import ROOM, call, describe_room  # noqa: E402

RUNS = 3

# 与 PROMPT_V2 唯一的差别:**删掉对砖的文字形容**,改成"照着图看"。
PROMPT_E = f"""{ROOM['name']}空间的{ROOM['style']}风格装修效果图。

【房间的几何基准】参考图 1 是一张**没有材质的三维白模(房间结构图)**。
它精确表达了本房间的形状。请**严格保持它**的:墙 / 地面 / 天花的位置与透视、
门窗的数量与位置。**只把表面的材质替换成真实瓷砖** ——
不要改变房间形状,不要增加或减少门窗,不要把门窗画成别的物件。

【房间的实际尺寸】
{describe_room(ROOM)}

【材质】参考图 2 **就是**本次要铺的瓷砖。
**请严格照着这张图的颜色、明暗与纹路来画** —— 它是唯一的依据。
不要用你自己想象的其他瓷砖,也不要凭"某种风格常见的砖"去替代它。
地面与墙面都铺这一款,砖缝要与房间尺寸对得上。

【陈设】马桶、洗手台、淋浴房。
【视角】正面平视全景,镜头与视线同高。
【要求】真实照片质感,有自然光照与阴影。"""


def main() -> None:
    api_key = load_key()
    if not api_key:
        sys.exit("没读到密钥")

    white = data_uri(HERE / "whitemodel_edged.png")
    print(f"去掉砖的文字描述,连跑 {RUNS} 次(其余与上一轮完全相同)\n")

    for i in range(1, RUNS + 1):
        call(api_key, PROMPT_E, [white, TILE_URL], f"E{i}_nodesc")


if __name__ == "__main__":
    main()
