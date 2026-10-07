"""把 basemodel 模块的输出 dump 成一张 PNG,供肉眼检查。

用法(必须在 tile-ai-agent 目录下跑,或用 PYTHONPATH 指过去):
    .venv\\Scripts\\python.exe ..\\outputs\\reports\\basemodel-probe\\dump_basemodel.py
"""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT / "tile-ai-agent"))
#: 产物落到已被 gitignore 的报告目录(见同目录 README)。
OUT = PROJECT / "outputs" / "reports" / "basemodel-probe"
OUT.mkdir(parents=True, exist_ok=True)

from app.agent.basemodel import basemodel_data_uri, render_basemodel  # noqa: E402

ROOM = {
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

png = render_basemodel(ROOM)
assert png is not None, "白模没画出来"

out = OUT / "basemodel_module.png"
out.write_bytes(png)

uri = basemodel_data_uri(ROOM)
print(f"已生成:{out}")
print(f"  PNG   {len(png)} bytes")
print(f"  base64 长度 {len(uri)} 字符(发送时就是这个体积)")
print(f"  确定性:两次产出相同 = {render_basemodel(ROOM) == png}")
