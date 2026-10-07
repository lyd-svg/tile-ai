"""嵌入端口(架构 §7.1 的 EmbeddingGateway)。

⚠️ **当前是占位实现,不是最终方案。**

真实的 Embedding 网关属 **T-1.5**,其厂商与模型由 **P0-2(模型 bake-off)** 决定;
架构 §15.1 建议优先用国产开源模型(如 BGE-M3)自部署。在那之前,这里提供一个
**确定性占位实现**,让索引同步的**链路**可以先跑通并验证。

**为什么占位实现必须确定性**(而不是随机):
"全量重建后可恢复出一致的索引"(T-0.13 判据)要求**同一份输入产出同一份向量**。
随机占位会让每次重建得到不同结果,那条判据就永远无法验证 ——
占位实现的价值在于让链路的**正确性**可被测试,而不是让检索**质量**达标。
"""

from __future__ import annotations

import hashlib
import math
from typing import Protocol, Sequence

from app.index.collections import DEFAULT_VECTOR_SIZE


class EmbeddingPort(Protocol):
    """嵌入端口。真实网关(T-1.5)实现同一协议即可无缝替换。"""

    @property
    def dimension(self) -> int:
        """向量维度。集合按它创建。"""
        ...

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        """把若干文本编码成向量,顺序与输入一致。"""
        ...


class DeterministicPlaceholderEmbedding:
    """确定性占位嵌入:同样的文本永远得到同样的向量。

    做法是用文本的 sha256 反复扩展出 `dimension` 个分量再归一化。
    它**没有任何语义能力** —— 语义相近的文本不会得到相近的向量,
    因此不能用于验证检索质量,只能用于验证索引同步链路。
    """

    def __init__(self, dimension: int = DEFAULT_VECTOR_SIZE) -> None:
        if dimension <= 0:
            raise ValueError("向量维度必须为正")
        self._dimension = dimension

    @property
    def dimension(self) -> int:
        return self._dimension

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        return [self._embed_one(text) for text in texts]

    def _embed_one(self, text: str) -> list[float]:
        components: list[float] = []
        counter = 0
        # 每 32 字节 sha256 摘要产出 32 个字节,用它们填充分量
        while len(components) < self._dimension:
            digest = hashlib.sha256(f"{counter}:{text}".encode("utf-8")).digest()
            components.extend((byte - 127.5) / 127.5 for byte in digest)
            counter += 1

        vector = components[: self._dimension]
        # 归一化:余弦距离下,归一化后内积即相似度;不归一化会让长文本天然占优
        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0:
            return vector
        return [value / norm for value in vector]


def product_embedding_text(payload: dict) -> str:
    """产品的嵌入文本(架构 §9.3):
    `名称 + 材质 + 规格 + 花色纹理 + 光泽 + 适用空间 + 风格标签`。

    ⚠️ **2026-10-07 修正**:此前 `spec` / `color_pattern` **不在 payload 里**
    (§9.2 的 Payload 列未包含它们),于是这两项**被从公式里省掉了** ——
    而它们恰恰是**用户描述瓷砖时最常用的词**("大理石纹""木纹""750×1500")。
    公式要求的东西搜不到,检索质量自然上不去。
    现已把两个字段补进 §9.2 的 Payload 列,这里跟着把公式**还原成完整的样子**。

    ⚠️ 顺序**照 §9.3 的公式写**(名称 → 材质 → 规格 → 花色 → 光泽 → 空间 → 风格),
    不要因为"读起来顺"就改 —— 公式是权威,且换顺序会让新旧向量对不上
    (改顺序同样要全量重建)。
    """
    parts = [
        payload.get("name"),
        payload.get("material"),
        payload.get("spec"),
        payload.get("color_pattern"),
        payload.get("surface"),
        *_as_list(payload.get("spaces")),
        *_as_list(payload.get("style_tags")),
    ]
    return " ".join(str(part) for part in parts if part)


def combo_embedding_text(payload: dict) -> str:
    """搭配的嵌入文本(架构 §9.3):`搭配名 + 空间 + 风格 + 所含产品摘要`。

    "所含产品摘要"当前**取不到** —— `queue.index.sync` 的 combo payload(§9.2)
    只有 combo_id / name / space / style / status,没有所含产品的信息。
    要让摘要进向量,得先在契约里给 combo payload 加字段(属上游变更),
    故这里只拼接可得的部分,并在文档中记录这处缺口。
    """
    parts = [payload.get("name"), payload.get("space"), payload.get("style")]
    return " ".join(str(part) for part in parts if part)


def _as_list(value: object) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [value]
    if isinstance(value, (list, tuple)):
        return [str(item) for item in value]
    return [str(value)]


def embedding_text_for(collection: str, payload: dict) -> str:
    """按集合类型选嵌入文本;未知集合用产品口径兜底(至少不会抛错丢事件)。"""
    if collection == "combo":
        return combo_embedding_text(payload)
    if collection == "kb":
        # 知识库按语义段落切片后入库(§9.3),其文本由切片过程给定,
        # 不走"字段拼接"这条路 —— 该集合的内容源尚未定义,先用标题兜底。
        return str(payload.get("title") or "")
    return product_embedding_text(payload)
