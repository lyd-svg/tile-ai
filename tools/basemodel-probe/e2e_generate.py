"""真机端到端:走**完整的生产链路**发一次生成,验证白模确实进了请求。

链路:C 端 HTTP → Java 受理 → MQ → Python 编排(画白模 + 组提示词)→
      真·图像模型 → 回调 → Java 落方案 → 轮询拿到图。

与其他脚本的区别
----------------
`probe*.py` 是**绕过 Java 直接调模型**做的对照实验;这个是**真链路**,
用来验证"白模有没有真的接进生产路径"。

⚠️ 它会**建一个临时 C 端账号**(用户名见下)—— 这是走公开注册接口,没有直接写库。
   用完可以在后台删掉。

用法:先确保 Java(8080)与 Python(8000)在跑,然后
    .venv\\Scripts\\python.exe ..\\outputs\\reports\\basemodel-probe\\e2e_generate.py
"""

from __future__ import annotations

import sys
import time
from pathlib import Path

import httpx

BASE = "http://localhost:8080/api/v1"
USERNAME = "probe_basemodel"
PASSWORD = "probe-123456"
TIMEOUT_S = 300

# 一个**带门窗位置**的卫生间 —— 位置是这次架构调整新增的字段。
LAYOUT = {
    "spaces": [
        {
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
    ]
}

HERE = Path(__file__).resolve().parents[2] / "outputs" / "reports" / "basemodel-probe"
HERE.mkdir(parents=True, exist_ok=True)


def body_of(client: httpx.Client, task_id: str) -> dict:
    """查进度。

    ⚠️ **必须带上登录 Cookie** —— `GET /generate/{taskId}` 是要鉴权的。
    第一版这里用的是模块级 `httpx.get`(**不带 Cookie**),于是每次都拿到 401,
    `data` 是空的、状态永远是 `None`,最后只表现为"轮询超时",
    而真实情况是任务早就失败了。**一个把失败伪装成超时的写法。**
    """
    return client.get(f"{BASE}/generate/{task_id}", timeout=30.0).json()


def main() -> None:
    with httpx.Client(timeout=60.0) as client:
        # ---------------- 1. 建号 / 登录 ----------------
        reg = client.post(f"{BASE}/auth/register",
                          json={"username": USERNAME, "password": PASSWORD})
        print(f"[1] 注册 {USERNAME}: code={reg.json().get('code')} "
              f"({reg.json().get('message')})")

        login = client.post(f"{BASE}/auth/login",
                            json={"username": USERNAME, "password": PASSWORD})
        payload = login.json()
        if payload.get("code") != 0:
            sys.exit(f"登录失败:{payload}")
        print(f"[2] 登录成功,拿到 Cookie:{sorted(client.cookies.keys())}")

        # ---------------- 2. 提交生成 ----------------
        body = {
            "space": "bathroom",
            "style": "modern",
            "layout": LAYOUT,
            "parts": [
                {"part": "floor", "sku": "8FVA10782M"},
                {"part": "wall", "sku": "75-150FMB10753LPM"},
            ],
        }
        accepted = client.post(f"{BASE}/generate", json=body).json()
        if accepted.get("code") != 0:
            sys.exit(f"受理失败:{accepted}")
        task_id = accepted["data"]["taskId"]
        print(f"[3] 已受理 taskId={task_id},开始轮询…")

        # ---------------- 3. 轮询 ----------------
        deadline = time.time() + TIMEOUT_S
        last = None
        while time.time() < deadline:
            data = body_of(client, task_id).get("data") or {}
            status = data.get("status")
            if status != last:
                print(f"    {status} progress={data.get('progress')}")
                last = status
            if status in ("success", "failed"):
                print(f"\n[4] 终态:{status}")
                print(json_dump(data))
                if status == "success":
                    save_images(data.get("images") or [])
                return
            time.sleep(3)
        print("超时")


def json_dump(data: dict) -> str:
    import json
    return json.dumps({k: v for k, v in data.items() if k != "layout"},
                      ensure_ascii=False, indent=2)


def save_images(images: list[dict]) -> None:
    """把出的图抓下来,和"如果画了白模、它应该长什么样"并排放。"""
    for image in images:
        url = image.get("url") or ""
        try:
            got = httpx.get(url, timeout=60.0, follow_redirects=True)
        except Exception as error:  # noqa: BLE001
            print(f"    取图失败 {url[:60]}: {error}")
            continue
        name = f"e2e_{image.get('view', 'image')}.png"
        (HERE / name).write_bytes(got.content)
        print(f"    已存 {name}  ({len(got.content)} bytes)  {url[:80]}…")


if __name__ == "__main__":
    main()
