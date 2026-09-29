"""向量检索(架构 §9.4)。

```
用户提问 → Embedding(query) → Qdrant topK 召回(product/combo/kb)
        → 可选 payload 过滤(空间/风格/价格区间/在售)
        → ...
```

**最要紧的一条**:下架内容必须在过滤中排除(§9.3 一致性:
"下架产品在检索过滤中被排除")。这不是可选的优化 —— 若检索能召回已下架的产品,
AI 咨询就会把它推荐给用户,而用户在线下根本买不到。
"""

from __future__ import annotations

from dataclasses import dataclass, field

from qdrant_client import QdrantClient
from qdrant_client.http import models as qmodels

from app.index import collections

# 有上下架状态语义的集合 —— 这些集合的检索**必须**带在售过滤。
_STATUS_FILTERED = frozenset({collections.PRODUCT.name, collections.COMBO.name})

ON_SALE = "on_sale"


@dataclass(frozen=True)
class SearchFilter:
    """检索过滤条件。全部可选,但 `status` 会被强制补上(见 `build_filter`)。"""

    space: str | None = None
    style: str | None = None
    price_min: float | None = None
    price_max: float | None = None
    material: str | None = None
    extra: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class RetrievedItem:
    id: str
    score: float
    payload: dict


def build_filter(collection_name: str, criteria: SearchFilter) -> qmodels.Filter | None:
    """把检索条件编译成 Qdrant 过滤。

    **在售过滤由本函数强制注入,调用方无法关掉** —— 让"排除下架"成为检索的
    默认行为,而不是每个调用点都要记得加的一个参数。忘了加不会报错,
    只会悄悄把下架产品推荐出去。
    """
    must: list[qmodels.Condition] = []

    if collection_name in _STATUS_FILTERED:
        must.append(qmodels.FieldCondition(
            key="status", match=qmodels.MatchValue(value=ON_SALE)))

    if criteria.space:
        # 产品用 spaces(数组)、搭配用 space(单值),两种形态都要覆盖
        must.append(_any_of_field(("spaces", "space"), criteria.space))
    if criteria.style:
        must.append(_any_of_field(("style_tags", "style"), criteria.style))
    if criteria.material:
        must.append(qmodels.FieldCondition(
            key="material", match=qmodels.MatchValue(value=criteria.material)))

    if criteria.price_min is not None or criteria.price_max is not None:
        must.append(qmodels.FieldCondition(
            key="price",
            range=qmodels.Range(gte=criteria.price_min, lte=criteria.price_max)))

    for key, value in criteria.extra.items():
        must.append(qmodels.FieldCondition(key=key, match=qmodels.MatchValue(value=value)))

    return qmodels.Filter(must=must) if must else None


def _any_of_field(candidate_keys: tuple[str, ...], value: str) -> qmodels.Condition:
    """同一个语义值在 product / combo 两个集合里字段名不同,这里任一命中即可。

    用 `should`(或)而不是要求两个字段都有 —— 产品有 `spaces`,搭配有 `space`,
    写死其中一个会让另一个集合的过滤永远为空。
    """
    return qmodels.Filter(should=[
        qmodels.FieldCondition(key=key, match=qmodels.MatchValue(value=value))
        for key in candidate_keys
    ])


def search(
    client: QdrantClient,
    collection_name: str,
    query_vector: list[float],
    top_k: int = 10,
    criteria: SearchFilter | None = None,
) -> list[RetrievedItem]:
    """在指定集合里检索。返回按相似度降序的结果。"""
    response = client.query_points(
        collection_name=collection_name,
        query=query_vector,
        limit=top_k,
        query_filter=build_filter(collection_name, criteria or SearchFilter()),
        with_payload=True,
    )
    return [
        RetrievedItem(id=str(point.id), score=point.score, payload=point.payload or {})
        for point in response.points
    ]
