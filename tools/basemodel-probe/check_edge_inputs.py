"""拿**用户可能随手填出来的怪参数**去撞白模生成器,看它会不会画出误导人的东西。

为什么单独做这件事
------------------
`check_all_spaces.py` 用的是**我手挑的合理尺寸** —— 那只能证明"正常输入没问题"。
但录入户型是**用户自己填**的:他可能填出又窄又高、门窗位置离谱、一层楼那么高的
房间。白模是**拿去指挥图像模型的"权威"** —— 它画出误导人的几何,比画不出来更糟。

⚠️ 本脚本**不调模型、不花钱、不需要任何服务在跑**。

产物落在 `outputs/reports/basemodel-probe/edges/`(已 gitignore)。
"""

from __future__ import annotations

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT / "tile-ai-agent"))

from app.agent.basemodel import render_basemodel  # noqa: E402

OUT = PROJECT / "outputs" / "reports" / "basemodel-probe" / "edges"
OUT.mkdir(parents=True, exist_ok=True)


def room(length, width, height, openings=None, wall_count=4):
    r = {"type": "other", "length": length, "width": width, "height": height,
         "openings": openings or []}
    if wall_count is not None:
        r["wallCount"] = wall_count
    return r


def door(w=0.8, h=2.1, wall="back", offset=0.5):
    return {"type": "door", "w": w, "h": h, "wall": wall, "offset": offset}


CASES = [
    ("正常(对照)", room(2.0, 1.8, 2.4, [door()])),
    ("又窄又高 0.6×0.6×2.4", room(0.6, 0.6, 2.4, [door()])),
    ("又宽又浅 10×2×2.4", room(10.0, 2.0, 2.4, [door()])),
    ("又长又窄 20×1.2×2.4", room(20.0, 1.2, 2.4, [door()])),
    ("层高很矮 2×1.8×1.2", room(2.0, 1.8, 1.2, [door()])),
    ("层高很高 2×1.8×10", room(2.0, 1.8, 10.0, [door()])),
    ("⚠️ 门的位置超出墙(offset=5, 墙宽 1.8)", room(2.0, 1.8, 2.4, [door(offset=5.0)])),
    ("⚠️ 门的位置为负(offset=-1)", room(2.0, 1.8, 2.4, [door(offset=-1.0)])),
    ("门刚好贴左墙角(offset=0.4=w/2)", room(2.0, 1.8, 2.4, [door(offset=0.4)])),
    ("门刚好贴右墙角(offset=1.4)", room(2.0, 1.8, 2.4, [door(offset=1.4)])),
    ("门窗特别多(12 个)", room(3.0, 3.0, 2.7,
                          [door(w=0.6, offset=0.3 + i * 0.25, wall="back") for i in range(12)])),
    ("没填 wallCount", room(2.0, 1.8, 2.4, [door()], wall_count=None)),
    ("墙数 3(非矩形)", room(2.0, 1.8, 2.4, [door()], wall_count=3)),
    ("尺寸为 0", room(0.0, 1.8, 2.4, [door()])),
    ("尺寸为负", room(-2.0, 1.8, 2.4, [door()])),
    ("尺寸字段缺失", {"type": "other", "openings": []}),
]


def main() -> None:
    print(f"{'用例':<38} {'结果':<10} {'体积':<10}")
    print("-" * 62)
    for i, (name, r) in enumerate(CASES):
        png = render_basemodel(r)
        if png is None:
            print(f"{name:<38} 不画(None)  {'—':<10}")
            continue
        (OUT / f"{i:02d}_{name.replace('/', '_').replace(' ', '')[:24]}.png").write_bytes(png)
        print(f"{name:<38} 画出来了   {len(png):>6} B")
    print("-" * 62)
    print(f"产物:{OUT}")
    print("⚠️ **'画出来了'不等于'画对了'** —— 这一行人眼看,脚本判不了。")


if __name__ == "__main__":
    main()
