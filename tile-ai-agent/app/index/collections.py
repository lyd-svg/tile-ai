"""Qdrant 集合定义与初始化(架构 §9.2)。

三个集合的划分、向量维度策略与 payload 过滤字段,全部取自架构 §9.2 的表格。

**Qdrant 是派生索引**(§9.1):权威数据在 MySQL,变更经 `queue.index.sync` 同步过来。
索引可随时全量重建,不参与业务事务 —— 因此集合初始化必须是**幂等**的,
重复调用不报错、不丢数据。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Final

from qdrant_client import QdrantClient
from qdrant_client.http import models as qmodels

# 向量维度。
#
# ⚠️ **待定**:架构 §9.2 写的是"由 Embedding 网关决定(如 1024)",
# 而模型选型(P0-2)尚未完成,真实维度未知。这里先按契约给的示例值 1024 建集合。
#
# 改这个值是**安全**的 —— Qdrant 是派生索引,维度变了只需重建集合并全量重放,
# 不涉及任何权威数据。这正是"索引可重建"这条设计的意义(§9.1、§15.2)。
DEFAULT_VECTOR_SIZE: Final[int] = 1024

# 距离度量。文本语义检索的标准选择;架构未规定,由实现按惯例确定。
DISTANCE: Final[qmodels.Distance] = qmodels.Distance.COSINE


@dataclass(frozen=True)
class CollectionSpec:
    """一个集合的定义。`payload_fields` 即架构 §9.2 的 Payload 列。"""

    name: str
    payload_fields: tuple[str, ...]
    # 需要建索引的 payload 字段 —— 只有"会被用来过滤"的字段才值得建索引,
    # 全建会拖慢写入且白占内存。
    indexed_fields: tuple[str, ...]


PRODUCT: Final[CollectionSpec] = CollectionSpec(
    name="product_index",
    payload_fields=("sku_code", "name", "material", "surface",
                    "style_tags", "spaces", "price", "status", "updated_at"),
    # 检索时按空间/风格/价格/在售过滤 —— §9.4 的"可选 payload 过滤"
    indexed_fields=("sku_code", "status", "material", "style_tags", "spaces", "price"),
)

COMBO: Final[CollectionSpec] = CollectionSpec(
    name="combo_index",
    payload_fields=("combo_id", "name", "space", "style", "status", "updated_at"),
    indexed_fields=("combo_id", "status", "space", "style"),
)

KB: Final[CollectionSpec] = CollectionSpec(
    name="kb_index",
    payload_fields=("doc_id", "category", "title", "source", "updated_at"),
    indexed_fields=("doc_id", "category"),
)

ALL_COLLECTIONS: Final[tuple[CollectionSpec, ...]] = (PRODUCT, COMBO, KB)

# 按事件里的 collection 取值(契约 enum: product / combo / kb)查集合定义
BY_EVENT_NAME: Final[dict[str, CollectionSpec]] = {
    "product": PRODUCT,
    "combo": COMBO,
    "kb": KB,
}

# payload 字段类型 —— 影响建索引时的选择。
# 建错类型不会报错,只会让过滤悄悄失效(或退回全表扫描),所以显式声明。
_FIELD_TYPES: Final[dict[str, qmodels.PayloadSchemaType]] = {
    "sku_code": qmodels.PayloadSchemaType.KEYWORD,
    "combo_id": qmodels.PayloadSchemaType.KEYWORD,
    "doc_id": qmodels.PayloadSchemaType.KEYWORD,
    "status": qmodels.PayloadSchemaType.KEYWORD,
    "material": qmodels.PayloadSchemaType.KEYWORD,
    "surface": qmodels.PayloadSchemaType.KEYWORD,
    "category": qmodels.PayloadSchemaType.KEYWORD,
    "source": qmodels.PayloadSchemaType.KEYWORD,
    "space": qmodels.PayloadSchemaType.KEYWORD,
    "style": qmodels.PayloadSchemaType.KEYWORD,
    "name": qmodels.PayloadSchemaType.TEXT,
    "title": qmodels.PayloadSchemaType.TEXT,
    "updated_at": qmodels.PayloadSchemaType.KEYWORD,
    # 多值字段:按"包含任一项"过滤
    "style_tags": qmodels.PayloadSchemaType.KEYWORD,
    "spaces": qmodels.PayloadSchemaType.KEYWORD,
    "price": qmodels.PayloadSchemaType.FLOAT,
}


def ensure_collections(client: QdrantClient, vector_size: int = DEFAULT_VECTOR_SIZE) -> None:
    """确保三个集合存在且维度正确。**幂等**。

    已存在且维度一致 → 不动(不重建,否则每次启动都会白白清空索引)。
    已存在但维度不一致 → 抛错而不是静默重建:维度不符意味着 Embedding 网关换了模型,
    那是一次需要显式决定的操作(要重新嵌入全部数据),不该由启动流程顺手做掉。
    """
    for spec in ALL_COLLECTIONS:
        if client.collection_exists(spec.name):
            _assert_dimension_matches(client, spec, vector_size)
            continue

        client.create_collection(
            collection_name=spec.name,
            vectors_config=qmodels.VectorParams(size=vector_size, distance=DISTANCE),
        )
        for field in spec.indexed_fields:
            client.create_payload_index(
                collection_name=spec.name,
                field_name=field,
                field_schema=_FIELD_TYPES.get(field, qmodels.PayloadSchemaType.KEYWORD),
            )


def _assert_dimension_matches(client: QdrantClient, spec: CollectionSpec, vector_size: int) -> None:
    info = client.get_collection(spec.name)
    params = info.config.params.vectors
    existing = params.size if isinstance(params, qmodels.VectorParams) else None
    if existing is not None and existing != vector_size:
        raise RuntimeError(
            f"集合 {spec.name} 的向量维度是 {existing},配置期望 {vector_size}。"
            "维度不符说明 Embedding 模型换了 —— 需要重建集合并重新嵌入全部数据,"
            "这应由显式操作完成,不在启动时静默处理。"
        )
