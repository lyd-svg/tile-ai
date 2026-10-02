"""本地 `sentence-transformers` 的嵌入适配器(架构 §7.1 的 EmbeddingGateway)。

**为什么是它**:嵌入模型选型(P0-2 残留)一直空着,而占位实现的向量**没有语义**
("浅灰"和"灰白"在它眼里毫无关系),检索能跑但挑不准。这里把开源模型
(`Qwen3-Embedding-0.6B`,Apache 2.0)下到本地目录、在进程内跑推理。

**与另一个适配器(`ollama_embedding.py`)的分工**:那个是"调本地 Ollama 服务",
这个"模型直接在 Python 进程里"。两者实现同一个 `EmbeddingPort`,按配置二选一:
能装 Ollama 就少一个 Python 依赖;装不上(比如安装包只在 GitHub、下不动)就用这个,
只多一个 `sentence-transformers`。**选哪个不影响别处** —— 工厂按 provider 分派。

## 三条不能省的细节

1. **延迟加载**。模型加载很慢(首次十几秒、吃几百 MB 内存),**不能在 import 时做** ——
   否则只是导入这个模块就会把服务启动拖住。所以 `_load()` 放到第一次 `embed()` 时,
   并且 `import sentence_transformers` 也在函数里(没装这个库的环境不该因为 import 失败)。
2. **向量要归一化**(`normalize_embeddings=True`)。集合用的是 **COSINE** 距离;
   归一化之后相似度就是点积,量纲一致,不会出现"长文本天然占优"。
3. **维度对不上要当场响**。静默写进集合的话,向量空间与集合定义错位,
   检索结果是乱的、而且看不出为什么。
"""

from __future__ import annotations

import logging
from typing import Any, Sequence

from app.gateway.errors import GatewayConfigError, InvalidProviderResponse

logger = logging.getLogger(__name__)


class LocalEmbedding:
    """进程内跑一个 sentence-transformers 模型。"""

    def __init__(
        self,
        *,
        model: str,
        dimension: int,
        device: str = "cpu",
        batch_size: int = 32,
    ) -> None:
        if not model:
            raise GatewayConfigError("local embedding 缺少模型 —— 填本地目录或模型名")
        if dimension <= 0:
            raise GatewayConfigError(f"向量维度必须为正,当前 {dimension}")

        self._model_name = model
        self._dimension = dimension
        self._device = device
        self._batch_size = batch_size
        #: 首次 embed 时才真正加载 —— 见模块头第 1 条
        self._model: Any = None

    @property
    def dimension(self) -> int:
        return self._dimension

    @property
    def model(self) -> str:
        return self._model_name

    def __repr__(self) -> str:
        return f"<LocalEmbedding model={self._model_name} device={self._device}>"

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        """把若干文本编码成向量,**顺序与输入一致**。"""
        items = [str(text) for text in texts]
        if not items:
            return []

        model = self._load()
        vectors = model.encode(
            items,
            batch_size=self._batch_size,
            # COSINE 距离下归一化是标准做法(见模块头第 2 条)
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )

        result = [[float(value) for value in vector] for vector in vectors]
        for vector in result:
            if len(vector) != self._dimension:
                # 见模块头第 3 条:宁可当场失败,也不要写出一堆维度错的向量
                raise InvalidProviderResponse(
                    f"模型 {self._model_name} 给出 {len(vector)} 维,而配置的 "
                    f"index_vector_size 是 {self._dimension} 维。两处必须一致:"
                    f"改配置,或换一个维度相符的模型(改完还要重建索引)。"
                )
        return result

    def _load(self) -> Any:
        if self._model is not None:
            return self._model

        try:
            from sentence_transformers import SentenceTransformer
        except ImportError as error:  # pragma: no cover - 取决于环境
            raise GatewayConfigError(
                "没装 sentence-transformers —— 本地嵌入模型需要它。装法:"
                "`pip install sentence-transformers`(国内可加 "
                "`-i https://pypi.tuna.tsinghua.edu.cn/simple`)"
            ) from error

        logger.info("加载本地嵌入模型 %s(首次较慢,之后就常驻内存)", self._model_name)
        self._model = SentenceTransformer(self._model_name, device=self._device)
        return self._model
