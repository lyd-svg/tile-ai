"""把 `queue.index.sync` 事件落到 Qdrant(架构 §6.2、§9.2)。

这是"Java 投递 → Python 更新 Qdrant"里的后一半。
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

from qdrant_client import QdrantClient
from qdrant_client.http import models as qmodels

from app.index import collections
from app.index.embedding import EmbeddingPort, embedding_text_for

# 由业务主键派生 Qdrant point id 的命名空间。
#
# **必须这样映射**:Qdrant 的 point id 只接受 **uint64 或 UUID**,
# 而我们的业务主键是 `sku_code`(如 `TL-2043`)、`combo_id`(如 `12`)——
# 直接用会被 Qdrant 拒绝。用 UUIDv5 从 `集合:业务主键` 派生,既是**确定性**的
# (同一个 SKU 永远映射到同一个点,upsert/delete 才对得上),
# 又不必额外维护一张映射表。
_POINT_NAMESPACE = uuid.UUID("6f1a1f2c-6f2e-4a1e-9d5a-2b8f7c3e0d11")


def point_id_for(collection: str, business_id: str) -> str:
    """业务主键 → Qdrant point id。确定性,可重算。"""
    return str(uuid.uuid5(_POINT_NAMESPACE, f"{collection}:{business_id}"))


@dataclass(frozen=True)
class ApplyResult:
    upserted: int = 0
    deleted: int = 0

    def __add__(self, other: "ApplyResult") -> "ApplyResult":
        return ApplyResult(self.upserted + other.upserted, self.deleted + other.deleted)


def apply_event(client: QdrantClient, embedding: EmbeddingPort, event: dict) -> ApplyResult:
    """应用一条 IndexSyncPayload。

    **幂等**:契约要求消费端以 `eventId` 去重,而这里更进一步 ——
    操作本身就是 upsert/delete 语义,**重复应用结果完全相同**。
    因此即使去重缓存丢失(进程重启),最坏也只是重复写一次,不会写坏索引。
    """
    collection = event.get("collection")
    spec = collections.BY_EVENT_NAME.get(collection)
    if spec is None:
        # 未知集合:静默忽略会让事件凭空消失,记下来但不阻断其它事件
        raise ValueError(f"未知的索引集合: {collection!r}(契约 enum: product / combo / kb)")

    result = ApplyResult()
    for item in event.get("items", []):
        business_id = item.get("id")
        action = item.get("action")
        if not business_id or not action:
            raise ValueError(f"索引项缺少 id 或 action: {item!r}")

        point_id = point_id_for(collection, str(business_id))
        if action == "delete":
            client.delete(
                collection_name=spec.name,
                points_selector=qmodels.PointIdsList(points=[point_id]),
            )
            result += ApplyResult(deleted=1)
            continue

        if action != "upsert":
            raise ValueError(f"未知的索引动作: {action!r}(契约 enum: upsert / delete)")

        payload = dict(item.get("payload") or {})
        vector = embedding.embed([embedding_text_for(collection, payload)])[0]
        client.upsert(
            collection_name=spec.name,
            points=[qmodels.PointStruct(id=point_id, vector=vector, payload=payload)],
        )
        result += ApplyResult(upserted=1)

    return result
