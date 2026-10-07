"""把**每一个空间类型**都画一遍白模,确认它不只是"卫生间能画"。

为什么要单独检查这个
--------------------
白模生成器原先是**只拿一个卫生间**验过的(§77)。但字典里有 5 个空间,
尺寸差得很远 —— 阳台又窄又长、客厅又大又方正。渲染器里有**写死的相机位置
与焦距**,换个尺寸会不会画歪、画出画面外、或者干脆返回 None,**没人验过**。

⚠️ 本脚本**不调图像模型、不花钱、不需要任何服务在跑** ——
   它只调 `app/agent/basemodel.py` 这个纯函数。

产物落在 `outputs/reports/basemodel-probe/spaces/`(已 gitignore)。
"""

from __future__ import annotations

import logging
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT / "tile-ai-agent"))

from app.agent.basemodel import basemodel_data_uri, render_basemodel  # noqa: E402

OUT = PROJECT / "outputs" / "reports" / "basemodel-probe" / "spaces"
OUT.mkdir(parents=True, exist_ok=True)

#: 取自后台字典 `t_dict`(dict_type=space)。尺寸按各空间的常见值给,**故意拉开差距**。
SPACES = [
    # 空间 key     中文     长    宽    层高   门窗(位置全给)
    ("living_room", "客厅", 5.0, 4.0, 2.8,
     [("door", 1.2, 2.1, "back", 2.5, None), ("window", 2.0, 1.5, "right", 2.0, 0.6)]),
    ("bedroom", "卧室", 3.6, 3.0, 2.7,
     [("door", 0.9, 2.1, "back", 0.8, None), ("window", 1.5, 1.5, "right", 1.6, 0.9)]),
    ("bathroom", "卫生间", 2.0, 1.8, 2.4,
     [("door", 0.8, 2.1, "back", 0.55, None), ("window", 1.0, 1.2, "right", 0.9, 0.9)]),
    ("kitchen", "厨房", 3.0, 2.0, 2.7,
     [("door", 0.9, 2.1, "back", 1.0, None)]),
    ("balcony", "阳台", 3.6, 1.4, 2.6,
     [("door", 1.6, 2.2, "back", 1.6, None), ("window", 1.6, 1.4, "left", 1.8, 0.9)]),
]


def room_of(key: str, name: str, length: float, width: float, height: float, raw) -> dict:
    return {
        "type": key, "name": name,
        "length": length, "width": width, "height": height, "wallCount": 4,
        "openings": [
            {"type": t, "w": w, "h": h, "wall": wall, "offset": off,
             **({} if sill is None else {"sill": sill})}
            for t, w, h, wall, off, sill in raw
        ],
    }


class _Catcher(logging.Handler):
    """收 WARN —— 门窗被跳过时渲染器会打一条,这里把它显式报出来。

    ⚠️ 加这个是因为踩过一次:厨房那个门我位置填得差了 5 厘米、超出墙了,
    被新加的检查跳掉 —— 图是画出来了,但**门窗没了**,
    而脚本当时只会说"✅ 画出来了",要看图才发现。
    """

    def __init__(self) -> None:
        super().__init__()
        self.messages: list[str] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.messages.append(record.getMessage())


def main() -> None:
    catcher = _Catcher()
    logging.getLogger("app.agent.basemodel").addHandler(catcher)
    logging.getLogger("app.agent.basemodel").setLevel(logging.WARNING)

    print(f"{'空间':<10} {'尺寸(长×宽×高)':<20} {'门窗':<4} {'画出来':<8} {'PNG 大小':<10} {'确定性':<6}")
    print("-" * 74)

    bad = 0
    for key, name, length, width, height, raw in SPACES:
        room = room_of(key, name, length, width, height, raw)
        catcher.messages.clear()
        png = render_basemodel(room)
        if png is None:
            print(f"{name:<10} {length}×{width}×{height:<10} {len(raw):<4} ✗ None")
            bad += 1
            continue

        same = render_basemodel(room) == png
        (OUT / f"{key}.png").write_bytes(png)
        uri = basemodel_data_uri(room) or ""
        # ⚠️ 数的是**警告条数**,不是"被跳过的门窗个数" —— 一次渲染会打多条
        # (逐个开口一条 + 一句汇总),而这里为了验确定性渲染了不止一次。
        # 想看**具体哪个洞被跳了**,看下面逐条打出来的消息。
        drop = f"  ⚠️ {len(catcher.messages)} 条警告" if catcher.messages else ""
        print(f"{name:<10} {f'{length}×{width}×{height}':<20} {len(raw):<4} ✅       "
              f"{len(png):>6} B  {'✅' if same else '✗'}    (base64 {len(uri)} 字符){drop}")
        for msg in catcher.messages:
            print(f"           ⚠️ {msg}")

    print("-" * 74)
    print(f"产物:{OUT}")
    print("画不出来 = 0" if bad == 0 else f"⚠️ 有 {bad} 个空间画不出来")


if __name__ == "__main__":
    main()
