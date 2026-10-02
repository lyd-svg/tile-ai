"""RAG 检索与 refs 生成(架构 §9.4)。

```
用户提问 → Embedding(query) → Qdrant topK 召回(product)
        → 拼接上下文(附引用) → LLM 生成回答 → refs 供"一键采纳"
```

**三条不能破的口径:**

1. **上下文块不含价格**。检索 payload 里**有** `price` 字段(§9.2),
   而回答不得报价(不变量 1)。所以 `context_block` **逐字段白名单**拼接,
   绝不整包塞进去 —— 漏一个字段是 bug,而漏一块价格是原则问题。
2. **只检索在售**。`build_filter` 已强制注入 `status=on_sale`(§9.3),
   这里不重复造过滤,直接复用它。
3. **refs 逐个校验,查不到就丢**。`sku_code` 只是检索索引里的一个字符串,
   不能证明产品真的存在/在售。必须回问 Java 的权威接口,取不到即丢弃
   (`java_api.get_product` 取不到返回 None,不抛)。这样"回答里出现的 SKU
   必然真实可查",是硬约束 4 的落点。
"""

from __future__ import annotations

import logging
from typing import Any, Iterable, Sequence

from app.index import collections
from app.index.retrieval import RetrievedItem, SearchFilter, search

logger = logging.getLogger(__name__)

#: 召回条数。小而精:条数越多,拼给模型的上下文越长、越贵,也越容易"串味"。
DEFAULT_TOP_K = 6
#: refs 上限。给用户"一键采纳"的候选,3 个够挑;多了反而是噪声。
DEFAULT_MAX_REFS = 3

#: 拼进上下文的字段 —— **白名单**,价格字段刻意不在其中。
_CONTEXT_FIELDS = ("sku_code", "name", "material", "surface", "spaces", "style_tags")

SYSTEM_PROMPT = (
    "你是瓷砖选购的搭配顾问。只回答瓷砖的基本信息、产品推荐与搭配方案。"
    "严格约束:"
    "① 绝不提供价格、费用或任何金额数字;若被问价,直接说明无法提供报价;"
    "② 只能从给定的候选产品里推荐,不得编造不存在的产品、型号或参数;"
    "③ 不承诺施工、售后、物流、下单支付等本服务不覆盖的事;"
    "④ 用简体中文,简洁分点作答。"
)

#: 部位关键词。推不出时默认 `wall` —— 见模块下方 `infer_part` 的说明。
_PART_KEYWORDS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("floor", ("地砖", "地面", "地板", "客厅地", "卧室地")),
    ("local", ("腰线", "花砖", "局部", "背景墙砖", "装饰砖")),
    ("wall", ("墙砖", "墙面", "卫生间墙", "厨房墙")),
)

DEFAULT_PART = "wall"


def retrieve(
    query: str,
    *,
    embedding: Any,
    qdrant: Any,
    top_k: int = DEFAULT_TOP_K,
    criteria: SearchFilter | None = None,
) -> list[RetrievedItem]:
    """把提问编码后检索产品集合。检索故障**不在这里兜** —— 由 pipeline 统一转 error 帧。"""
    vectors = embedding.embed([query])
    if not vectors:
        return []
    return search(
        qdrant,
        collections.PRODUCT.name,
        vectors[0],
        top_k=top_k,
        criteria=criteria or SearchFilter(),
    )


def context_block(items: Iterable[RetrievedItem]) -> str:
    """把召回结果拼成给模型看的上下文。**白名单字段,不含价格。**"""
    lines: list[str] = []
    for index, item in enumerate(items, start=1):
        payload = item.payload or {}
        fields = [f"{key}={payload[key]}" for key in _CONTEXT_FIELDS if payload.get(key)]
        if not fields:
            continue
        lines.append(f"[{index}] " + " ".join(fields))
    return "\n".join(lines)


def infer_part(query: str) -> str:
    """从提问里猜"部位"(契约 SkuRef.part ∈ floor/wall/local)。

    ⚠️ **这是启发式,不是权威** —— 检索 payload 里没有 part 字段(§9.2 的
    Payload 列不含它),所以只能按问句关键词猜。推不出时默认 `wall`。
    猜错会让"一键采纳"把砖放到错误部位 —— 这是一处已知缺口(见交付说明)。
    """
    text = query or ""
    for part, keywords in _PART_KEYWORDS:
        if any(keyword in text for keyword in keywords):
            return part
    return DEFAULT_PART


def build_refs(
    items: Sequence[RetrievedItem],
    *,
    part: str,
    java_api: Any,
    trace_id: str = "",
    max_refs: int = DEFAULT_MAX_REFS,
) -> list[dict[str, Any]]:
    """由检索候选生成 refs。**每个 SKU 都回问 Java 校验,查不到即丢。**

    `name` 取 Java 权威返回的名字(而不是检索 payload 里的),因为前者才是
    "用户点进去会看到的名字"。校验失败(SKU 不存在/已下架/取数超时)一律丢弃 ——
    宁可少给一个候选,也不能给一个点不开的。
    """
    refs: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in items:
        sku = str((item.payload or {}).get("sku_code") or "").strip()
        if not sku or sku in seen:
            continue
        seen.add(sku)

        product = java_api.get_product(sku, trace_id=trace_id)
        if not isinstance(product, dict):
            logger.info("refs 候选校验未通过,丢弃 sku=%s", sku)
            continue

        name = str(product.get("name") or (item.payload or {}).get("name") or "")
        refs.append({"sku": sku, "name": name, "part": part})
        if len(refs) >= max_refs:
            break
    return refs
