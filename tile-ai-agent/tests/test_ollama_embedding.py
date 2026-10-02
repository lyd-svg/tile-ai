"""本地 Ollama 的嵌入适配器与工厂接线。

**纯单测**:用假的 HTTP 客户端,不需要 Ollama 在跑、不需要联网。

这一层最要紧的**不是"能不能调通"**(那是联调的事),而是**调不通时会不会响**:

  - 服务没起 → 必须抛一条**能照着做**的错误(告诉你去 `ollama serve`),
    而不是静默返回空向量 —— 那种失败最吓人:检索结果全乱,却看不出为什么;
  - 模型给的维度与配置对不上 → **当场报错**。放行的话,一堆维度错的向量会写进集合,
    检索结果是乱的,而且事后极难查。

这两条就是下面大部分用例在钉的东西。
"""

from __future__ import annotations

from types import SimpleNamespace

import httpx
import pytest

from app.gateway.errors import (
    GatewayConfigError,
    GatewayTimeout,
    InvalidProviderResponse,
    ProviderError,
)
from app.gateway.factory import build_embedding_gateway
from app.gateway.ollama_embedding import OllamaEmbedding
from app.index.embedding import DeterministicPlaceholderEmbedding


class FakeResponse:
    def __init__(self, *, status_code: int = 200, payload=None, text: str = "") -> None:
        self.status_code = status_code
        self._payload = payload
        self.text = text

    def json(self):
        if self._payload is None:
            raise ValueError("不是 JSON")
        return self._payload


class FakeClient:
    """记录每次调用;可以指定返回或抛错。"""

    def __init__(self, *, response=None, error: Exception | None = None) -> None:
        self._response = response
        self._error = error
        self.calls: list[tuple[str, dict]] = []

    def post(self, url, json=None):  # noqa: A002 —— 与 httpx 的签名保持一致
        self.calls.append((url, json))
        if self._error is not None:
            raise self._error
        return self._response


def build(**overrides) -> tuple[OllamaEmbedding, FakeClient]:
    payload = overrides.pop("payload", {"embeddings": [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]})
    client = FakeClient(response=FakeResponse(payload=payload))
    embedding = OllamaEmbedding(
        base_url="http://localhost:11434",
        model="qwen3-embedding:0.6b",
        dimension=3,
        client=client,
    )
    return embedding, client


# ----------------------------------------------------------------------
# 正常路径
# ----------------------------------------------------------------------

def test_embed_sends_batch_and_keeps_order():
    embedding, client = build()

    vectors = embedding.embed(["卫生间", "客厅"])

    assert vectors == [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]
    url, payload = client.calls[0]
    assert url == "http://localhost:11434/api/embed"
    assert payload == {"model": "qwen3-embedding:0.6b", "input": ["卫生间", "客厅"]}


def test_empty_input_does_not_call_the_service():
    embedding, client = build()

    assert embedding.embed([]) == []
    assert client.calls == [], "空输入不该白跑一趟"


def test_dimension_is_exposed():
    embedding, _ = build()

    assert embedding.dimension == 3


# ----------------------------------------------------------------------
# 失败必须响
# ----------------------------------------------------------------------

def test_service_not_running_gives_actionable_error():
    """**服务没起是最常见的一类** —— 错误里要直接说怎么起。"""
    client = FakeClient(error=httpx.ConnectError("connection refused"))
    embedding = OllamaEmbedding(base_url="http://localhost:11434",
                                model="qwen3-embedding:0.6b", dimension=3, client=client)

    with pytest.raises(ProviderError) as caught:
        embedding.embed(["x"])

    message = str(caught.value)
    assert "ollama serve" in message, "要告诉用户怎么做,而不只是报一个异常名"
    assert "localhost:11434" in message


def test_dimension_mismatch_is_rejected():
    """模型给 5 维、配置说 3 维 —— **当场报错**,不能写进集合。"""
    embedding, _ = build(payload={"embeddings": [[0.1, 0.2, 0.3, 0.4, 0.5]]})

    with pytest.raises(InvalidProviderResponse) as caught:
        embedding.embed(["x"])

    assert "index_vector_size" in str(caught.value)


def test_count_mismatch_is_rejected():
    embedding, _ = build(payload={"embeddings": [[0.1, 0.2, 0.3]]})

    with pytest.raises(InvalidProviderResponse):
        embedding.embed(["第一条", "第二条"])


def test_http_error_carries_status_code():
    client = FakeClient(response=FakeResponse(status_code=500, text="boom"))
    embedding = OllamaEmbedding(base_url="http://localhost:11434",
                                model="m", dimension=3, client=client)

    with pytest.raises(ProviderError) as caught:
        embedding.embed(["x"])

    assert caught.value.status_code == 500
    assert caught.value.is_transient() is True


def test_timeout_is_transient():
    client = FakeClient(error=httpx.TimeoutException("too slow"))
    embedding = OllamaEmbedding(base_url="http://localhost:11434",
                                model="m", dimension=3, client=client)

    with pytest.raises(GatewayTimeout) as caught:
        embedding.embed(["x"])

    assert caught.value.is_transient() is True


def test_non_json_response_is_rejected():
    client = FakeClient(response=FakeResponse(payload=None))
    embedding = OllamaEmbedding(base_url="http://localhost:11434",
                                model="m", dimension=3, client=client)

    with pytest.raises(InvalidProviderResponse):
        embedding.embed(["x"])


def test_missing_response_field_is_rejected():
    embedding, _ = build(payload={"model": "qwen3-embedding:0.6b"})

    with pytest.raises(InvalidProviderResponse):
        embedding.embed(["x"])


# ----------------------------------------------------------------------
# 配置
# ----------------------------------------------------------------------

@pytest.mark.parametrize("overrides", [
    {"base_url": ""},
    {"model": ""},
    {"dimension": 0},
])
def test_bad_config_raises(overrides):
    params = {"base_url": "http://localhost:11434", "model": "m", "dimension": 3, **overrides}

    with pytest.raises(GatewayConfigError):
        OllamaEmbedding(**params)


def test_repr_does_not_leak_anything_sensitive():
    embedding, _ = build()

    assert repr(embedding) == "<OllamaEmbedding base_url=http://localhost:11434 model=qwen3-embedding:0.6b>"


# ----------------------------------------------------------------------
# 工厂接线
# ----------------------------------------------------------------------

def settings(**overrides) -> SimpleNamespace:
    base = dict(
        model_embedding_provider="ollama",
        model_embedding_base_url="",
        model_embedding_model="",
        index_vector_size=1024,
        gateway_embedding_timeout_seconds=15.0,
    )
    base.update(overrides)
    return SimpleNamespace(**base)


def test_factory_builds_ollama_adapter_with_model_default():
    gateway = build_embedding_gateway(settings())

    assert isinstance(gateway, OllamaEmbedding)
    # 没配 model 就用预置的默认值
    assert gateway.model == "qwen3-embedding:0.6b"
    assert gateway.dimension == 1024


def test_factory_honours_explicit_model_and_base_url():
    gateway = build_embedding_gateway(settings(
        model_embedding_model="qwen3-embedding:4b",
        model_embedding_base_url="http://127.0.0.1:11434/",
    ))

    assert gateway.model == "qwen3-embedding:4b"
    assert gateway._base_url == "http://127.0.0.1:11434"  # noqa: SLF001 —— 断言末尾斜杠被去掉


@pytest.mark.parametrize("provider", ["", "placeholder"])
def test_empty_provider_still_falls_back_to_placeholder(provider):
    """留空 = 占位实现(旧行为不变)—— 换了真模型之后也不该把它删掉。"""
    gateway = build_embedding_gateway(settings(model_embedding_provider=provider))

    assert isinstance(gateway, DeterministicPlaceholderEmbedding)


def test_unknown_provider_is_rejected():
    """不认识就响,不静默退回占位 —— 否则"配错了"会表现得像"模型不行"。"""
    with pytest.raises(GatewayConfigError) as caught:
        build_embedding_gateway(settings(model_embedding_provider="openai"))

    assert "openai" in str(caught.value)
