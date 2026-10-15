"""扫一遍 4 份契约,列出**所有数字字段**有没有上下限。

为什么要有这只脚本
------------------
2026-10-07 发现:户型尺寸(`length/width/height`)**三层都只挡了下限** ——
用户填个"长 = 一万米"能一路存进去,而面积按 长×宽 派生,
大到超过列宽时**直接落库报错**。

那是**一个一个发现的**。这只脚本把同一类问题**一次扫完**:
凡是 `type: number` / `type: integer` 的字段,都该问一句
"用户能填多大?填大了会怎样?"

怎么读结果
----------
* ★ = **没有任何上限**(用户能随便填大)—— 需要逐个判断要不要加上限;
* ⚠️ = **没有下限**(可能是漏了,也可能本来就允许 0/负数);
* ✅ = 两边都有。

⚠️ 本脚本只看**契约**。Java 的 `@Min/@Max/@DecimalMin/@DecimalMax` 与前端
   `<input min max>` 是**另外两层**,要人对着看 —— 但**契约是一切的源头**,
  契约没有的约束,后面两层迟早会漂掉(而契约↔实现的比对工具
  **只比字段名/类型/必填/枚举,取值约束抓不到**,见 openapi-verification.md §40)。

用法:
    tile-ai-agent\\.venv\\Scripts\\python.exe tools\\audit-numeric-bounds.py
"""

from __future__ import annotations

import sys
from pathlib import Path

import yaml

PROJECT = Path(__file__).resolve().parents[1]
CONTRACTS = [
    "openapi-common.yaml",
    "openapi-c.yaml",
    "openapi-admin.yaml",
    "openapi-internal.yaml",
]

NUMERIC = {"number", "integer"}


def walk(node, path="", out=None):
    """递归找 (properties 下的) 数字字段。"""
    if out is None:
        out = []
    if not isinstance(node, dict):
        return out

    for name, spec in (node.get("properties") or {}).items():
        if isinstance(spec, dict):
            t = spec.get("type")
            if t in NUMERIC:
                out.append((f"{path}.{name}" if path else name, t, spec))
            elif "allOf" in spec:
                # `allOf: [$ref 到枚举]` 这种是字符串枚举,跳过
                pass
        # 继续往下钻一级(对象 / 数组 items)
        if isinstance(spec, dict):
            if "properties" in spec:
                walk(spec, f"{path}.{name}" if path else name, out)
            items = spec.get("items")
            if isinstance(items, dict) and "properties" in items:
                walk(items, f"{path}.{name}[]", out)
    return out


def describe(key: str, kind: str, spec: dict) -> tuple[str, str]:
    lo, hi = spec.get("minimum"), spec.get("maximum")
    lo_s = f"{lo}" if lo is not None else "—"
    hi_s = f"{hi}" if hi is not None else "**无**"
    if hi is None:
        mark = "★"
    elif lo is None:
        mark = "⚠️"
    else:
        mark = "✅"
    return mark, f"{key:<46} {kind:<8} min={lo_s:<10} max={hi_s}"


def main() -> None:
    total_loose = 0
    for name in CONTRACTS:
        path = PROJECT / name
        if not path.exists():
            print(f"⚠️ 找不到 {name}")
            continue
        doc = yaml.safe_load(path.read_text(encoding="utf-8"))

        rows = []
        for section in ("components",):
            for _key, schema in ((doc.get(section) or {}).get("schemas") or {}).items():
                for props_owner in (schema,):
                    if "properties" in props_owner:
                        rows.extend(walk(props_owner, _key))

        print(f"\n{'=' * 76}\n{name}   数字字段 {len(rows)} 个\n{'=' * 76}")
        loose = 0
        for key, kind, spec in sorted(rows):
            mark, text = describe(key, kind, spec)
            if mark == "★":
                loose += 1
            print(f"  {mark} {text}")
        total_loose += loose
        print(f"  —— 没有任何上限的:{loose} 个")

    print(f"\n{'=' * 76}")
    print(f"合计:**{total_loose} 个数字字段没有任何上限**")
    print("⚠️ 契约没有上限 = 用户能在前端随便填 —— 要不要加、加多少,逐个判断。")


if __name__ == "__main__":
    sys.exit(main())
