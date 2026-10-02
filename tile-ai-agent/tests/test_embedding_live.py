"""本地嵌入模型的**真实语义**验证。

默认跳过:要 1GB 权重在本地(`python tools/download_embedding.py` 下)。
跑它之前先确认模型已下好。

**这里验的不是"能不能调通",而是"换了它到底有没有用"** —— 也就是:
语义相近的两句话,向量是不是真的比不相关的更近。
换模型 / 改池化 / 升级 sentence-transformers 之后,**回归就靠这一条**。
"""

from __future__ import annotations

import math
from pathlib import Path

import pytest

from app.gateway.local_embedding import LocalEmbedding
from app.index.embedding import DeterministicPlaceholderEmbedding

MODEL_DIR = Path(__file__).resolve().parents[1] / "models" / "Qwen3-Embedding-0.6B"

pytestmark = pytest.mark.skipif(
    not MODEL_DIR.exists(),
    reason=f"本地嵌入模型未下载({MODEL_DIR})—— 先跑 python tools/download_embedding.py",
)

#: 三条探针:一句话"语义相近",一句"同领域但不相关"。
QUERY = "卫生间浅灰地砖配什么墙砖"
NEAR = "卫生间铺的浅灰色瓷砖,墙面怎么搭"
FAR = "厨房用的红色防滑地砖"

#: 相似度至少要拉开这么多,才算"分辨得出来"。噪声不至于有这么大。
MARGIN = 0.1


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb)


def test_model_gives_1024_dimensions():
    """维度必须与 `index_vector_size` 一致 —— 对不上集合就废了。"""
    adapter = LocalEmbedding(model=str(MODEL_DIR), dimension=1024)

    assert len(adapter.embed(["瓷砖"])[0]) == 1024


def test_semantically_close_texts_are_actually_closer():
    """**这才是换模型的目的**:语义近的,向量也该近。"""
    adapter = LocalEmbedding(model=str(MODEL_DIR), dimension=1024)

    query, near, far = adapter.embed([QUERY, NEAR, FAR])
    sim_near = cosine(query, near)
    sim_far = cosine(query, far)

    assert sim_near > sim_far + MARGIN, (
        f"语义相近的那句没有明显更近:近={sim_near:.3f} 远={sim_far:.3f} —— "
        f"模型/池化配置可能不对"
    )


def test_real_model_beats_placeholder_on_the_same_pair():
    """**负向对照**:同一对句子,真模型认得出"像",占位实现认不出。

    这条比"单独断言占位实现不像"稳:两侧用**同一组输入**,比的是"谁更能分辨",
    而不是拿一个随机结果去撞阈值。它同时说明**为什么值得换**:
    占位实现的向量是哈希出来的,语义相近根本不会更近 —— 检索能跑,排序等于随机。
    """
    real = LocalEmbedding(model=str(MODEL_DIR), dimension=1024)
    placeholder = DeterministicPlaceholderEmbedding(1024)

    real_sim = cosine(*real.embed([QUERY, NEAR]))
    placeholder_sim = cosine(*placeholder.embed([QUERY, NEAR]))

    assert real_sim > placeholder_sim + MARGIN, (
        f"真模型的相近度({real_sim:.3f})没有明显高于占位实现({placeholder_sim:.3f})—— "
        f"换模型这一步可能没真正生效"
    )
