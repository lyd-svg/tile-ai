"""
第二round:改进提示词 + 改进白模,看能不能让模型**更严格地照着白模画**。

与第一轮的差别(用户 2026-10-07 选的路:观感优先,不用蒙版、不用代码贴图)
--------------------------------------------------------------------------
  ① **房间描述由程序生成**,不是手写 —— 这样"每次户型不同"也不用人管。
     房间数据是结构化的,拼成文字是纯模板活儿。
  ② **明说参考图 1 是"白模"** —— 第一轮没说,它可能把白模当成了一张拍得很素的照片。
  ③ 白模加**黑描边**,让它一眼看出这是结构图而不是照片。

本轮两组(与第一轮的 C 组并排比)
--------------------------------
  C1  旧白模(纯色块) + 新提示词   → 隔离出**提示词**改动的效果
  C2  新白模(带描边) + 新提示词   → **两条一起**的效果

产物:C1_oldmodel_newprompt.png / C2_edgedmodel_newprompt.png
"""

from __future__ import annotations

import base64
import json
import sys
from pathlib import Path

import httpx

HERE = Path(__file__).resolve().parents[2] / "outputs" / "reports" / "basemodel-probe"
HERE.mkdir(parents=True, exist_ok=True)
#: ⚠️ 导入兄弟脚本用的是**脚本自己所在目录**,不是产物目录 —— 两者 2026-10-07 起分开了。
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
from probe import BASE_URL, MODEL, PATH, TILE_URL, data_uri, load_key  # noqa: E402

# ============================================================
# 房间数据 —— **结构化的**。下面那段文字描述是**程序拼出来的**,不是手写的。
# 换一个户型,这段文字自动跟着变,零人工。
# ============================================================
ROOM = {
    "name": "卫生间",
    "style": "现代",
    "length": 2.0,  # 进深(米)
    "width": 1.8,  # 面宽(米)
    "height": 2.4,  # 层高(米)
    "openings": [
        {
            "wall": "后墙",
            "type": "门",
            "w": 0.8,
            "h": 2.1,
            # ⚠️ 位置现在是写死在脚本里的 —— 因为契约里的 Opening **只有宽高、没有位置**。
            #    要真正落地,这一处得先进数据模型。
            "position": "门洞中心位于地面上距左墙 0.55 米处",
        },
        {
            "wall": "右墙",
            "type": "窗",
            "w": 1.0,
            "h": 1.2,
            "sill": 0.9,
            "position": "窗洞中心距后墙 0.9 米",
            "note": "窗台离地 0.9 米",
        },
    ],
}


def describe_room(room: dict) -> str:
    """**把结构化房间数据拼成一段文字** —— 这就是"不用每次手写"的那一步。"""
    lines = [
        f"本房间长 {room['length']} 米、宽 {room['width']} 米、层高 {room['height']} 米;"
    ]
    for o in room["openings"]:
        one = f"{o['wall']}有一扇宽 {o['w']} 米、高 {o['h']} 米的{o['type']},{o['position']}"
        if o.get("note"):
            one += f",{o['note']}"
        lines.append(one + "。")
    return "\n".join(lines)


PROMPT_V2 = f"""{ROOM['name']}空间的{ROOM['style']}风格装修效果图。

【房间的几何基准】参考图 1 是一张**没有材质的三维白模(房间结构图)**。
它精确表达了本房间的形状。请**严格保持它**的:墙 / 地面 / 天花的位置与透视、
门窗的数量与位置。**只把表面的材质替换成真实瓷砖** ——
不要改变房间形状,不要增加或减少门窗,不要把门窗画成别的物件。

【房间的实际尺寸】
{describe_room(ROOM)}

【材质】参考图 2 是瓷砖(奥斯银灰,瓷质仿古砖,易洁糖果釉,仿石,800×800mm)。
地面与墙面都铺这一款,砖缝要与房间尺寸对得上。

【陈设】马桶、洗手台、淋浴房。
【视角】正面平视全景,镜头与视线同高。
【要求】真实照片质感,有自然光照与阴影。"""


def call(api_key: str, prompt: str, refs: list[str], tag: str) -> None:
    payload: dict = {"model": MODEL, "prompt": prompt}
    for i, ref in enumerate(refs):
        payload["image" if i == 0 else f"image{i + 1}"] = ref

    print(f"[{tag}] 参考图 {len(refs)} 张 → 调用中…", flush=True)
    with httpx.Client(timeout=600.0) as client:
        resp = client.post(
            BASE_URL + PATH,
            json=payload,
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        )
    if resp.status_code != 200:
        print(f"[{tag}] ✗ HTTP {resp.status_code}: {resp.text[:300]}")
        return

    body = resp.json()
    items = body.get("images") or body.get("data") or []
    url = (items[0].get("url") or items[0].get("b64_json")) if items else None
    if not url:
        print(f"[{tag}] ✗ 没有图:{json.dumps(body)[:300]}")
        return

    print(f"[{tag}] ✓")
    if url.startswith("http"):
        with httpx.Client(timeout=120.0) as client:
            img = client.get(url)
        (HERE / f"{tag}.png").write_bytes(img.content)
        (HERE / f"{tag}.url").write_text(url, encoding="utf-8")
        print(f"[{tag}]   已存 {tag}.png ({len(img.content)} bytes)")


def main() -> None:
    api_key = load_key()
    if not api_key:
        sys.exit("没读到密钥")

    print("=== 程序自动生成的房间描述 ===")
    print(describe_room(ROOM))
    print("=" * 34 + "\n")

    old_model = data_uri(HERE / "whitemodel.png")
    new_model = data_uri(HERE / "whitemodel_edged.png")

    call(api_key, PROMPT_V2, [old_model, TILE_URL], "C1_oldmodel_newprompt")
    call(api_key, PROMPT_V2, [new_model, TILE_URL], "C2_edgedmodel_newprompt")


if __name__ == "__main__":
    main()
