#!/usr/bin/env python
"""Qdrant 全量重建工具(架构 §9.1、§15.2)。

    ⚠️ 这是一个**独立运维工具**,不属于任何服务的运行时。

架构 §6.2 把 Qdrant 的写权给了 Python 智能体,而权威数据在 MySQL、Python 不直连
MySQL(CLAUDE.md 不变量 4)。因此"从 MySQL 恢复出一致的索引"这件事,
两侧的服务都无法独立完成 —— 它需要一个同时能读两者的工具,也就是本脚本。
它不被任何服务加载,也**不应**被引入服务的调用链。

用法:
    python tools/rebuild_index.py                 # 清空并重建(默认)
    python tools/rebuild_index.py --keep          # 不清空,仅重放覆盖
    python tools/rebuild_index.py --dry-run       # 只统计,不写

环境变量(与 Java 侧同名,便于复用):
    DB_URL / DB_USERNAME / DB_PASSWORD   —— MySQL 连接
    QDRANT_URL                           —— Qdrant 地址,默认 http://localhost:6333
"""

from __future__ import annotations

import argparse
import os
import sys
from dataclasses import dataclass
from pathlib import Path

# 复用智能体侧的集合定义与嵌入实现 —— 重建出来的向量必须与增量同步的一致,
# 各写一套迟早会漂移。
_AGENT_DIR = Path(__file__).resolve().parent.parent / "tile-ai-agent"
sys.path.insert(0, str(_AGENT_DIR))

import pymysql  # noqa: E402
from qdrant_client import QdrantClient  # noqa: E402

from app.index import collections  # noqa: E402
from app.index.apply import point_id_for  # noqa: E402
from app.index.embedding import DeterministicPlaceholderEmbedding  # noqa: E402
from app.index.embedding import embedding_text_for  # noqa: E402


@dataclass
class RebuildStats:
    products: int = 0
    combos: int = 0

    def total(self) -> int:
        return self.products + self.combos


def mysql_connection() -> pymysql.connections.Connection:
    """按 Java 侧同名环境变量连 MySQL。

    默认值与 Java 的 application.yml 一致,便于本地直接跑。
    """
    url = os.getenv("DB_URL", "jdbc:mysql://127.0.0.1:3306/tile_ai")
    # jdbc:mysql://host:port/db?params → 拆出 host / port / db
    without_scheme = url.replace("jdbc:mysql://", "")
    host_port, _, rest = without_scheme.partition("/")
    host, _, port = host_port.partition(":")
    database = rest.split("?")[0]

    return pymysql.connect(
        host=host or "127.0.0.1",
        port=int(port or "3306"),
        user=os.getenv("DB_USERNAME", "root"),
        password=os.getenv("DB_PASSWORD", ""),
        database=database or "tile_ai",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def fetch_products(connection: pymysql.connections.Connection) -> list[dict]:
    """读全部 SKU。

    **含下架产品**:索引里要保留它们并带上 `status=off_sale`,
    由检索侧的过滤把它们排除(架构 §9.3)。若在重建时就丢掉下架产品,
    它们一旦重新上架就得等下一次全量重建才能被搜到。
    """
    with connection.cursor() as cursor:
        cursor.execute(
            "SELECT sku_code, name, material, surface, applicable_spaces, style_tags,"
            "       price_per_sqm, status, updated_at"
            "  FROM t_product"
        )
        return list(cursor.fetchall())


def fetch_combos(connection: pymysql.connections.Connection) -> list[dict]:
    with connection.cursor() as cursor:
        cursor.execute(
            "SELECT id, name, space, style, status, updated_at FROM t_recommend_combo"
        )
        return list(cursor.fetchall())


def product_payload(row: dict) -> dict:
    """DB 行 → 契约 §9.2 的 payload(字段名 snake_case,与增量事件一致)。"""
    return {
        "sku_code": row["sku_code"],
        "name": row["name"],
        "material": row["material"],
        "surface": row["surface"],
        "style_tags": _loads(row["style_tags"]),
        "spaces": _loads(row["applicable_spaces"]),
        "price": float(row["price_per_sqm"]) if row["price_per_sqm"] is not None else None,
        "status": row["status"],
        "updated_at": str(row["updated_at"]),
    }


def combo_payload(row: dict) -> dict:
    return {
        "combo_id": str(row["id"]),
        "name": row["name"],
        "space": row["space"],
        "style": row["style"],
        "status": row["status"],
        "updated_at": str(row["updated_at"]),
    }


def _loads(value: object) -> list[str] | None:
    """MySQL 的 JSON 列经 pymysql 取回时已是 Python 对象;字符串则解析一次。"""
    if value is None:
        return None
    if isinstance(value, (list, tuple)):
        return [str(item) for item in value]
    import json

    try:
        parsed = json.loads(value)
    except (TypeError, ValueError):
        return None
    return parsed if isinstance(parsed, list) else None


def rebuild(drop: bool, dry_run: bool) -> RebuildStats:
    stats = RebuildStats()
    connection = mysql_connection()
    qdrant = QdrantClient(url=os.getenv("QDRANT_URL", "http://localhost:6333"))
    embedding = DeterministicPlaceholderEmbedding(size())

    try:
        products = fetch_products(connection)
        combos = fetch_combos(connection)
        stats.products = len(products)
        stats.combos = len(combos)

        print(f"MySQL 读出:产品 {stats.products} 条,搭配 {stats.combos} 条")
        if dry_run:
            print("--dry-run:不做任何写入")
            return stats

        if drop:
            # 清空而非覆盖:只重放现存记录无法清除"MySQL 已删但索引还留着"的残留点,
            # 而那正是全量重建要解决的问题。
            for spec in collections.ALL_COLLECTIONS:
                if qdrant.collection_exists(spec.name):
                    qdrant.delete_collection(spec.name)
                    print(f"已删除集合 {spec.name}")
        collections.ensure_collections(qdrant, embedding.dimension)
        print("集合已就绪:", ", ".join(spec.name for spec in collections.ALL_COLLECTIONS))

        _upsert(qdrant, embedding, collections.PRODUCT.name, products, product_payload)
        _upsert(qdrant, embedding, collections.COMBO.name, combos, combo_payload)
        return stats
    finally:
        connection.close()


def _upsert(qdrant: QdrantClient, embedding, collection: str,
            rows: list[dict], to_payload) -> None:
    from qdrant_client.http import models as qmodels

    if not rows:
        print(f"{collection}:无数据,跳过")
        return

    batch_size = 100
    for start in range(0, len(rows), batch_size):
        chunk = rows[start:start + batch_size]
        payloads = [to_payload(row) for row in chunk]
        texts = [embedding_text_for(_event_collection(collection), payload) for payload in payloads]
        vectors = embedding.embed(texts)

        points = [
            qmodels.PointStruct(
                id=point_id_for(_event_collection(collection), _business_id(collection, payload)),
                vector=vector,
                payload=payload,
            )
            for payload, vector in zip(payloads, vectors)
        ]
        qdrant.upsert(collection_name=collection, points=points)

    print(f"{collection}:已写入 {len(rows)} 条")


def _event_collection(collection_name: str) -> str:
    """集合名 → 契约里的 collection 取值(product / combo / kb)。"""
    for event_name, spec in collections.BY_EVENT_NAME.items():
        if spec.name == collection_name:
            return event_name
    raise ValueError(f"未知集合: {collection_name}")


def _business_id(collection_name: str, payload: dict) -> str:
    return payload["combo_id"] if collection_name == collections.COMBO.name else payload["sku_code"]


def size() -> int:
    return int(os.getenv("INDEX_VECTOR_SIZE", str(collections.DEFAULT_VECTOR_SIZE)))


def main() -> int:
    parser = argparse.ArgumentParser(description="Qdrant 全量重建(架构 §15.2 兜底手段)")
    parser.add_argument("--keep", action="store_true",
                        help="不清空集合,仅重放覆盖(默认会先删除集合以清除残留)")
    parser.add_argument("--dry-run", action="store_true", help="只统计,不写")
    args = parser.parse_args()

    stats = rebuild(drop=not args.keep, dry_run=args.dry_run)
    print(f"完成:产品 {stats.products} 条,搭配 {stats.combos} 条,共 {stats.total()} 条已重建")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
