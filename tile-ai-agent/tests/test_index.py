"""T-0.13:向量索引的建立、写入与检索(架构 §9)。

**需要 Qdrant 在 127.0.0.1:6333**(`deploy/docker-compose.dev.yml`)。

这些测试直连本地 Qdrant,不用替身 —— 要验的恰恰是 Qdrant 的真实行为:
point id 的类型约束、payload 索引、过滤语义。替身给不了这些。
"""

from __future__ import annotations

import uuid

import pytest
from qdrant_client import QdrantClient
from qdrant_client.http import models as qmodels

from app.index import collections
from app.index.apply import apply_event, point_id_for
from app.index.embedding import DeterministicPlaceholderEmbedding, product_embedding_text
from app.index.retrieval import ON_SALE, SearchFilter, build_filter, search

QDRANT_URL = "http://127.0.0.1:6333"


@pytest.fixture(scope="module")
def qdrant() -> QdrantClient:
    return QdrantClient(url=QDRANT_URL)


@pytest.fixture(scope="module")
def embedding() -> DeterministicPlaceholderEmbedding:
    return DeterministicPlaceholderEmbedding(collections.DEFAULT_VECTOR_SIZE)


@pytest.fixture(scope="module", autouse=True)
def prepared(qdrant, embedding):
    """建好集合并清理本测试用过的点。"""
    collections.ensure_collections(qdrant, embedding.dimension)
    yield
    for business_id in (SKU, SKU_OFF_SALE, COMBO_ID):
        _delete_point(qdrant, collections.PRODUCT.name, business_id)
        _delete_point(qdrant, collections.COMBO.name, business_id)


SKU = "T13-PY-SKU"
SKU_OFF_SALE = "T13-PY-OFF"
COMBO_ID = "T13-PY-COMBO"


def _delete_point(qdrant: QdrantClient, collection: str, business_id: str) -> None:
    qdrant.delete(
        collection_name=collection,
        points_selector=qmodels.PointIdsList(points=[point_id_for(_event_name(collection), business_id)]),
    )


def _event_name(collection_name: str) -> str:
    for event_name, spec in collections.BY_EVENT_NAME.items():
        if spec.name == collection_name:
            return event_name
    raise ValueError(collection_name)


def _product_event(business_id: str, action: str, status: str = ON_SALE, price: float = 268.0) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "collection": "product",
        "traceId": "test-trace",
        "enqueuedAt": "2026-09-29T00:00:00",
        "items": [{
            "id": business_id,
            "action": action,
            "payload": {
                "sku_code": business_id,
                "name": "雅灰石纹",
                "material": "釉面砖",
                "surface": "柔光",
                "style_tags": ["modern"],
                "spaces": ["bathroom"],
                "price": price,
                "status": status,
                "updated_at": "2026-09-29T00:00:00",
            },
        }],
    }


def _query_vector(embedding) -> list[float]:
    return embedding.embed(["查询"])[0]


# ----------------------------------------------------------------------
# 集合
# ----------------------------------------------------------------------

def test_collections_are_created(qdrant, embedding):
    for spec in collections.ALL_COLLECTIONS:
        assert qdrant.collection_exists(spec.name), f"集合 {spec.name} 未建立"
        info = qdrant.get_collection(spec.name)
        assert info.config.params.vectors.size == embedding.dimension


def test_ensure_collections_is_idempotent(qdrant, embedding):
    """重复调用不报错、不丢数据 —— 集合初始化会在每次启动时执行。"""
    collections.ensure_collections(qdrant, embedding.dimension)
    collections.ensure_collections(qdrant, embedding.dimension)


def test_dimension_mismatch_is_rejected(qdrant, embedding):
    """维度不符要显式报错,而不是静默重建。

    维度不符意味着 Embedding 模型换了,那是需要重新嵌入全部数据的操作,
    不该由启动流程顺手做掉。
    """
    with pytest.raises(RuntimeError, match="维度"):
        collections.ensure_collections(qdrant, embedding.dimension + 1)


# ----------------------------------------------------------------------
# point id 映射
# ----------------------------------------------------------------------

def test_point_id_is_deterministic_and_collection_scoped():
    """业务主键不能直接当 point id(Qdrant 只收 uint64 / UUID),必须确定性映射。"""
    first = point_id_for("product", SKU)
    assert first == point_id_for("product", SKU), "同一业务主键必须映射到同一个点"
    # 同一编号在不同集合里应落到不同的点,否则产品与搭配会互相覆盖
    assert first != point_id_for("combo", SKU)
    # 能被解析成 UUID 才算合法 —— 否则 Qdrant 会拒绝
    uuid.UUID(first)


# ----------------------------------------------------------------------
# 写入与检索
# ----------------------------------------------------------------------

def test_upsert_makes_item_searchable(qdrant, embedding):
    result = apply_event(qdrant, embedding, _product_event(SKU, "upsert"))
    assert result.upserted == 1

    hits = search(qdrant, collections.PRODUCT.name, _query_vector(embedding), top_k=50)
    ids = {hit.payload.get("sku_code") for hit in hits}
    assert SKU in ids, "刚写入的 SKU 应能被检索到"
    assert SKU in ids


def test_delete_removes_item(qdrant, embedding):
    apply_event(qdrant, embedding, _product_event(SKU, "upsert"))
    result = apply_event(qdrant, embedding, _product_event(SKU, "delete"))
    assert result.deleted == 1

    hits = search(qdrant, collections.PRODUCT.name, _query_vector(embedding), top_k=50)
    assert SKU not in {hit.payload.get("sku_code") for hit in hits}, "删除后不应再被检索到"


def test_reapplying_event_is_idempotent(qdrant, embedding):
    """重复应用同一事件结果相同 —— 消费端可能重复收到消息。"""
    event = _product_event(SKU, "upsert")
    apply_event(qdrant, embedding, event)
    apply_event(qdrant, embedding, event)

    points, _ = qdrant.scroll(
        collection_name=collections.PRODUCT.name,
        scroll_filter=qmodels.Filter(must=[qmodels.FieldCondition(
            key="sku_code", match=qmodels.MatchValue(value=SKU))]),
        limit=10,
    )
    assert len(points) == 1, "重复消费不应产生重复的点"


# ----------------------------------------------------------------------
# 判据:下架产品在检索过滤中被排除(§9.3)
# ----------------------------------------------------------------------

def test_off_sale_product_is_excluded_from_search(qdrant, embedding):
    apply_event(qdrant, embedding, _product_event(SKU_OFF_SALE, "upsert", status="off_sale"))

    hits = search(qdrant, collections.PRODUCT.name, _query_vector(embedding), top_k=100)
    ids = {hit.payload.get("sku_code") for hit in hits}
    assert SKU_OFF_SALE not in ids, (
        "下架产品必须被排除 —— 否则 AI 咨询会把它推荐给用户,而用户在线下买不到"
    )


def test_on_sale_product_is_included(qdrant, embedding):
    """正向对照:证明上一条的排除不是因为过滤把所有东西都挡掉了。"""
    apply_event(qdrant, embedding, _product_event(SKU, "upsert", status=ON_SALE))

    hits = search(qdrant, collections.PRODUCT.name, _query_vector(embedding), top_k=100)
    assert SKU in {hit.payload.get("sku_code") for hit in hits}


def test_status_filter_cannot_be_disabled(qdrant):
    """在售过滤由 build_filter 强制注入,调用方关不掉。"""
    built = build_filter(collections.PRODUCT.name, SearchFilter())
    assert built is not None, "即使不传任何条件,也必须带上在售过滤"
    keys = [condition.key for condition in built.must]
    assert "status" in keys

    # kb_index 没有上下架语义,不应被强加该过滤
    assert build_filter(collections.KB.name, SearchFilter()) is None


def test_filter_by_space_and_price(qdrant, embedding):
    apply_event(qdrant, embedding, _product_event(SKU, "upsert", price=268.0))

    hits = search(qdrant, collections.PRODUCT.name, _query_vector(embedding), top_k=50,
                  criteria=SearchFilter(space="bathroom", price_max=500))
    assert SKU in {hit.payload.get("sku_code") for hit in hits}

    misses = search(qdrant, collections.PRODUCT.name, _query_vector(embedding), top_k=50,
                    criteria=SearchFilter(price_min=1000))
    assert SKU not in {hit.payload.get("sku_code") for hit in misses}, "价格区间外应被过滤"


# ----------------------------------------------------------------------
# 嵌入的确定性
# ----------------------------------------------------------------------

def test_embedding_is_deterministic(embedding):
    """同一文本必须得到同一向量 —— 否则"全量重建可恢复一致索引"无从验证。"""
    first = embedding.embed(["雅灰石纹 釉面砖"])
    second = embedding.embed(["雅灰石纹 釉面砖"])
    assert first == second


def test_embedding_dimension_and_normalization(embedding):
    vector = embedding.embed(["x"])[0]
    assert len(vector) == collections.DEFAULT_VECTOR_SIZE

    norm = sum(value * value for value in vector) ** 0.5
    assert abs(norm - 1.0) < 1e-6, "向量应已归一化 —— 否则长文本会天然占优"


def test_product_embedding_text_follows_the_architecture_formula():
    """**产品的嵌入文本必须照架构 §9.3 的公式拼。**

    `名称 + 材质 + 规格 + 花色纹理 + 光泽 + 适用空间 + 风格标签`

    ⚠️ **2026-10-07 之前,「规格」与「花色纹理」根本没进去** ——
    因为 §9.2 的 Payload 列没给这两个字段,实现拿不到,只能把它们从公式里省掉。
    而这两样恰恰是**用户描述瓷砖时最常用的词**("大理石纹""木纹""750×1500")——
    **公式要求的东西搜不到,检索质量自然上不去。**
    """
    text = product_embedding_text({
        "name": "菲特米黄",
        "material": "瓷质抛釉砖",
        "spec": "750×1500mm",
        "color_pattern": "米黄大理石纹",
        "surface": "高透亮面",
        "spaces": ["living_room"],
        "style_tags": ["cream"],
    })

    for piece in ("菲特米黄", "瓷质抛釉砖", "750×1500mm", "米黄大理石纹", "高透亮面",
                  "living_room", "cream"):
        assert piece in text, f"{piece} 必须进嵌入文本"

    # 顺序**照公式** —— 换顺序会让新旧向量落在不同的位置,同样要全量重建才能对齐
    assert text == "菲特米黄 瓷质抛釉砖 750×1500mm 米黄大理石纹 高透亮面 living_room cream"


def test_product_embedding_text_skips_missing_fields():
    """字段缺失**跳过而不是填占位** —— 宁可少一句,不臆造。"""
    assert product_embedding_text({"name": "丝缎"}) == "丝缎"


def test_unknown_collection_is_rejected(qdrant, embedding):
    with pytest.raises(ValueError, match="未知的索引集合"):
        apply_event(qdrant, embedding, {"collection": "nope", "items": []})
