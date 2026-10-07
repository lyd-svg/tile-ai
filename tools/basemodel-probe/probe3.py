"""
第三轮:**稳定性检验** —— 同样的输入跑 4 次,看是不是每次都是同一间房。

为什么这一轮最重要
------------------
用户的原话痛点是"大模型不稳定"。前两轮证明了**能不能听话**(门窗画对),
但**一次画对不等于每次都画对** —— 这才是决定这条路能不能交付的判据。

这一轮输入**完全不变**:同一张描边白模、同一块砖、同一段提示词。
唯一的变量是"模型的随机性"。

产物:D1_run1.png … D4_run4.png
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
from probe2 import PROMPT_V2, call  # noqa: E402

RUNS = 4


def main() -> None:
    api_key = load_key()
    if not api_key:
        sys.exit("没读到密钥")

    white = data_uri(HERE / "whitemodel_edged.png")
    print(f"同一张白模 + 同一块砖 + 同一段提示词,连跑 {RUNS} 次\n")

    for i in range(1, RUNS + 1):
        call(api_key, PROMPT_V2, [white, TILE_URL], f"D{i}_run{i}")


if __name__ == "__main__":
    main()
