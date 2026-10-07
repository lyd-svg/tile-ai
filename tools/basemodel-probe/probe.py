"""
一次实验:**把"按精确尺寸画出来的白模"喂给图像模型,看它能不能改善"每次像不同房间"。**

对比三组
--------
  A  只给瓷砖照片            —— 这就是**现在的做法**
  B  只给白模               —— 看模型能不能把"白房子"渲染成真房间
  C  白模 + 瓷砖照片         —— **要验证的方案**

三组用**同一段提示词**(B 组去掉提到瓷砖的那句),这样差异只来自参考图。

产物
----
  whitemodel.png        白模(由 make_whitemodel.py 生成)
  A_tile_only.png / B_whitemodel_only.png / C_whitemodel_tile.png
  compare.html          四张并排,浏览器打开看

⚠️ 会**把白模上传到外部模型服务**(硅基流动),并产生几分钱费用。
⚠️ 白模挂在本机 localhost,外部服务取不到 —— 所以这里用 base64 内嵌。
   本脚本同时验证"这个接口认不认 base64"。
"""

from __future__ import annotations

import base64
import json
import os
import sys
from pathlib import Path

import httpx

#: 产物写到**已被 gitignore 的报告目录**(见同目录 README 的说明)。
HERE = Path(__file__).resolve().parents[2] / "outputs" / "reports" / "basemodel-probe"
HERE.mkdir(parents=True, exist_ok=True)
ENV_FILE = HERE.parents[2] / "tile-ai-agent" / ".env"

BASE_URL = "https://api.siliconflow.cn"
PATH = "/v1/images/generations"
MODEL = "Qwen/Qwen-Image-Edit-2509"

# 一块真实瓷砖的产品图(演示库里的「奥斯银灰」,公网地址,模型取得到)
TILE_URL = "https://www.monalisa.com.cn/uploadfiles/2026/08/20260828160642476.jpg"

ROOM = "卫生间"
DIM = "长约 2 米,宽 1.8 米,层高 2.4 米"

PROMPT_TILE = (
    f"{ROOM}空间的现代风格装修效果图。\n"
    f"场景:一个完整连续的室内空间({DIM});整张图只拍这一个房间,不要拼接、不要分屏、不要多个画面。\n"
    "地面:铺贴参考图里的这款瓷砖(奥斯银灰,瓷质仿古砖,易洁糖果釉,仿石,800×800mm)。\n"
    "墙面:铺贴同一款瓷砖。\n"
    "陈设:马桶、洗手台、淋浴房。\n"
    "视角:正面平视全景。\n"
    "瓷砖必须铺贴到地面上,不要把瓷砖画成样品图、贴图或拼贴。"
)

PROMPT_BASE_FIRST = (
    f"{ROOM}空间的现代风格装修效果图。\n"
    f"**以参考图 1 为该空间的基准图** —— 房间形状与尺寸、门窗位置、墙面数量必须与它完全一致。\n"
    "地面:铺贴参考图 2 的这款瓷砖(奥斯银灰,瓷质仿古砖,易洁糖果釉,仿石,800×800mm)。\n"
    "墙面:铺贴同一款瓷砖。\n"
    "陈设:马桶、洗手台、淋浴房。\n"
    "视角:正面平视全景。\n"
    "瓷砖必须铺贴到地面上,不要把瓷砖画成样品图、贴图或拼贴。"
)

PROMPT_BASE_ONLY = (
    f"{ROOM}空间的现代风格装修效果图。\n"
    "以这张图为房间的基准 —— 房间形状与尺寸、门窗位置必须与它完全一致。\n"
    "请把它渲染成一间真实、有生活气息的卫生间:地面与墙面铺上瓷砖,放上马桶、洗手台、淋浴房。\n"
    "视角:正面平视全景。"
)


def load_key() -> str:
    """从 tile-ai-agent/.env 读密钥。**只读不打印。**"""
    if not ENV_FILE.exists():
        sys.exit(f"找不到 {ENV_FILE}")
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith("MODEL_IMAGE_API_KEY_SILICONFLOW="):
            return line.split("=", 1)[1].strip()
        if line.startswith("MODEL_IMAGE_API_KEY="):
            fallback = line.split("=", 1)[1].strip()
    return os.environ.get("MODEL_IMAGE_API_KEY_SILICONFLOW", "") or fallback


def data_uri(path: Path) -> str:
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


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
        print(f"[{tag}] ✗ HTTP {resp.status_code}: {resp.text[:400]}")
        return

    body = resp.json()
    items = body.get("images") or body.get("data") or []
    if not items:
        print(f"[{tag}] ✗ 没有图:{json.dumps(body)[:400]}")
        return

    url = items[0].get("url") or items[0].get("b64_json")
    if not url:
        print(f"[{tag}] ✗ 拿不到地址:{json.dumps(items[0])[:300]}")
        return

    print(f"[{tag}] ✓ 出图地址 {url[:90]}…")
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
    print(f"密钥已读取(长度 {len(api_key)}),模型 {MODEL}\n")

    white = data_uri(HERE / "whitemodel.png")
    print(f"白模已编码为 base64({len(white)} 字符)\n")

    call(api_key, PROMPT_TILE, [TILE_URL], "A_tile_only")
    call(api_key, PROMPT_BASE_ONLY, [white], "B_whitemodel_only")
    call(api_key, PROMPT_BASE_FIRST, [white, TILE_URL], "C_whitemodel_tile")


if __name__ == "__main__":
    main()
