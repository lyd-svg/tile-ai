"""本地 Ollama 的嵌入适配器(架构 §7.1 的 EmbeddingGateway)。

**为什么是 Ollama**:嵌入模型选型(P0-2 残留)一直空着,而占位实现的向量
**没有语义**("浅灰"和"灰白"在它眼里毫无关系),检索能跑但挑不准。
Ollama 把"下载模型 + 本地跑推理 + 开一个 HTTP 接口"三件事一次解决,
对"就想把嵌入换成真的"这个需求,比引 torch 轻得多。

**模型**:`qwen3-embedding:0.6b`(Apache 2.0 开源权重,原生 **1024 维**)——
维度与 `index_vector_size` 的默认值正好一致。

⚠️ **它是个常驻服务**:跑之前要先 `ollama serve`(装完之后通常已随开机启动)。
调用方不要指望"没起也能用" —— 连不上时这里会抛一条**能照着做**的错误,
而不是静默返回空向量(那会让检索结果全乱,却看不出为什么)。
"""

from __future__ import annotations

from typing import Any, Sequence

import httpx

from app.gateway.errors import (
    GatewayConfigError,
    GatewayTimeout,
    InvalidProviderResponse,
    ProviderError,
)

#: Ollama 的嵌入端点。**不是** OpenAI 兼容的那个(`/v1/embeddings`)——
#: 原生接口的响应形状更直接(`embeddings` 就是数组的数组),不必再套一层 choices。
EMBED_PATH = "/api/embed"

#: 单次请求里最多几条文本。Ollama 没有硬上限,但一次塞太多会占住它的工作线程;
#: 重建索引时按这个批量走,进度也更可读。
DEFAULT_BATCH_SIZE = 32


class OllamaEmbedding:
    """把本地 Ollama 的 `/api/embed` 包成项目的 `EmbeddingPort`。

    与 `EmbeddingPort` 协议**结构一致**(`dimension` + `embed`)——
    `Protocol` 是结构化类型,所以 `apply_event` / `IndexSyncConsumer` 那边一行不用改,
    这个接缝由测试实际走通来证明,不靠类型注解自证。
    """

    def __init__(
        self,
        *,
        base_url: str,
        model: str,
        dimension: int,
        timeout_seconds: float = 15.0,
        client: httpx.Client | None = None,
    ) -> None:
        # 缺配置就响,不静默降级 —— 否则"服务没起"会表现得像"模型不行"。
        if not base_url:
            raise GatewayConfigError("ollama 缺少 base_url")
        if not model:
            raise GatewayConfigError("ollama 缺少模型 ID —— 不猜,请显式配置")
        if dimension <= 0:
            raise GatewayConfigError(f"向量维度必须为正,当前 {dimension}")

        self._model = model
        self._dimension = dimension
        self._timeout_seconds = timeout_seconds
        # 端点自己拼绝对 URL,不依赖客户端的 base_url —— 否则外部注入一个
        # base_url 不同的客户端时,请求会**静默打到别的主机**上。
        self._base_url = base_url.rstrip("/")
        self._owns_client = client is None
        self._client = client or httpx.Client(timeout=httpx.Timeout(timeout_seconds))

    @property
    def dimension(self) -> int:
        """向量维度。集合按它创建(`index_vector_size`)。"""
        return self._dimension

    @property
    def model(self) -> str:
        return self._model

    def close(self) -> None:
        """只关闭自己建的客户端 —— 外部注入的客户端归外部管。"""
        if self._owns_client:
            self._client.close()

    def __repr__(self) -> str:
        """只列可公开字段。"""
        return f"<OllamaEmbedding base_url={self._base_url} model={self._model}>"

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        """把若干文本编码成向量,**顺序与输入一致**。

        一次请求发一批,不逐条发 —— 逐条会把一次索引重建变成几百次往返。
        """
        items = [str(text) for text in texts]
        if not items:
            return []

        data = self._post(EMBED_PATH, {"model": self._model, "input": items})
        vectors = data.get("embeddings")
        if not isinstance(vectors, list) or len(vectors) != len(items):
            raise InvalidProviderResponse(
                f"ollama 返回的向量条数与请求不符:要 {len(items)} 条,"
                f"拿到 {len(vectors) if isinstance(vectors, list) else type(vectors).__name__}"
            )

        for vector in vectors:
            if not isinstance(vector, list) or not vector:
                raise InvalidProviderResponse("ollama 返回的向量不是非空数组")

        # 维度对不上必须**当场响**:静默写进集合的话,向量空间与集合定义错位,
        # 检索结果是乱的而且看不出为什么。这一层挡住它,比事后查索引便宜得多。
        actual = len(vectors[0])
        if actual != self._dimension:
            raise InvalidProviderResponse(
                f"模型 {self._model} 给出 {actual} 维,而配置的 index_vector_size 是 "
                f"{self._dimension} 维。两处必须一致:改配置,或换一个维度相符的模型"
                f"(改完还要重建索引)。"
            )
        return [list(vector) for vector in vectors]

    # ------------------------------------------------------------------

    def _post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        """发一个请求并把各类失败翻译成本项目的异常类型。"""
        try:
            response = self._client.post(self._url(path), json=payload)
        except httpx.TimeoutException as error:
            raise GatewayTimeout(
                f"ollama 调用超时(>{self._timeout_seconds}s)"
            ) from error
        except httpx.HTTPError as error:
            # 连不上是**最常见**的一类(服务没起)。给一句能直接照着做的提示 ——
            # 否则排查方向会跑到"模型是不是不行"上去。
            raise ProviderError(
                f"连不上本地 Ollama({self._base_url})—— 确认它已启动"
                f"(`ollama serve`;装完之后通常已随开机启动)。原因:{type(error).__name__}"
            ) from error

        if response.status_code >= 400:
            raise ProviderError(
                f"ollama 返回 {response.status_code}:{response.text[:200]}",
                status_code=response.status_code,
            )

        try:
            data = response.json()
        except ValueError as error:
            raise InvalidProviderResponse("ollama 返回的不是 JSON") from error
        if not isinstance(data, dict):
            raise InvalidProviderResponse("ollama 返回的顶层结构不是对象")
        return data

    def _url(self, path: str) -> str:
        return f"{self._base_url}{path}"
