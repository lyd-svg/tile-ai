"""本地嵌入适配器(`LocalEmbedding`)与工厂接线。

**纯单测**:不加载真模型(那要 1GB 权重、十几秒),用替身走通逻辑。
**真实语义**由 `test_embedding_live.py` 验(那条要模型已下载,默认跳过)。

这一层最要紧的三件事:
  1. **import 不该拖住启动** —— 不许在模块/构造阶段就 `import torch`;
  2. **维度对不上当场响** —— 静默写进集合的话,检索结果是乱的且看不出为什么;
  3. **工厂按 provider 分派** —— `local` / `ollama` / 空(占位)三条路各自走对。
"""

from __future__ import annotations

import sys
from types import SimpleNamespace

import pytest

from app.gateway.errors import GatewayConfigError, InvalidProviderResponse
from app.gateway.factory import build_embedding_gateway
from app.gateway.local_embedding import LocalEmbedding
from app.index.embedding import DeterministicPlaceholderEmbedding


class FakeModel:
    """替身:按输入条数返回固定长度的向量,并记住收到过什么。"""

    def __init__(self, dimension: int = 3) -> None:
        self.dimension = dimension
        self.calls: list[dict] = []

    def encode(self, texts, **kwargs):
        self.calls.append({"texts": list(texts), **kwargs})
        return [[float(i)] * self.dimension for i, _ in enumerate(texts)]


def build(dimension: int = 3, model_dimension: int = 3):
    adapter = LocalEmbedding(model="/tmp/fake-model", dimension=dimension)
    fake = FakeModel(model_dimension)
    adapter._model = fake  # noqa: SLF001 —— 直接塞替身,跳过真实的模型加载
    return adapter, fake


# ----------------------------------------------------------------------
# 正常路径
# ----------------------------------------------------------------------

def test_embed_normalizes_and_keeps_order():
    adapter, fake = build()

    vectors = adapter.embed(["卫生间", "客厅"])

    assert vectors == [[0.0, 0.0, 0.0], [1.0, 1.0, 1.0]]
    assert fake.calls[0]["texts"] == ["卫生间", "客厅"]
    assert fake.calls[0]["normalize_embeddings"] is True, (
        "集合用的是 COSINE 距离 —— 不归一化会出现'长文本天然占优'"
    )


def test_empty_input_does_not_touch_the_model():
    adapter, fake = build()

    assert adapter.embed([]) == []
    assert fake.calls == [], "空输入不该去加载模型(第一次加载要十几秒)"


def test_dimension_is_exposed():
    adapter, _ = build(dimension=1024)

    assert adapter.dimension == 1024


# ----------------------------------------------------------------------
# 失败必须响
# ----------------------------------------------------------------------

def test_model_dimension_mismatch_is_rejected():
    """模型给 5 维、配置说 3 维 ⇒ 当场报错,不写进集合。"""
    adapter, _ = build(dimension=3, model_dimension=5)

    with pytest.raises(InvalidProviderResponse) as caught:
        adapter.embed(["x"])

    assert "index_vector_size" in str(caught.value)


@pytest.mark.parametrize("overrides", [
    {"model": ""},
    {"dimension": 0},
])
def test_bad_config_raises(overrides):
    params = {"model": "/tmp/m", "dimension": 3, **overrides}

    with pytest.raises(GatewayConfigError):
        LocalEmbedding(**params)


def test_importing_the_module_does_not_pull_torch():
    """**模块本身不许 import torch** —— 那会把服务启动拖住十几秒。

    **另起一个进程**来验:在本进程里要么 reload、要么删 `sys.modules` 里的 torch,
    两种都会把模块/类的身份弄乱(后者会让同一进程里其它用例的 `isinstance` 假失败)。
    子进程是干净的解释器,验的正是"新起一个服务时会不会被拖住"。
    """
    import subprocess

    code = (
        "import sys, app.gateway.local_embedding; "
        "heavy = [n for n in sys.modules if n.startswith(('torch', 'sentence_transformers'))]; "
        "print(','.join(heavy))"
    )
    result = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True, check=True)

    assert result.stdout.strip() == "", (
        f"导入适配器就把这些重家伙拉进来了:{result.stdout.strip()} —— 启动会被拖住"
    )


def test_repr_is_plain():
    adapter, _ = build()

    assert repr(adapter) == "<LocalEmbedding model=/tmp/fake-model device=cpu>"


# ----------------------------------------------------------------------
# 工厂接线
# ----------------------------------------------------------------------

def settings(**overrides) -> SimpleNamespace:
    base = dict(
        model_embedding_provider="local",
        model_embedding_base_url="",
        model_embedding_model="/tmp/fake-model",
        index_vector_size=1024,
        embedding_device="cpu",
        gateway_embedding_timeout_seconds=15.0,
    )
    base.update(overrides)
    return SimpleNamespace(**base)


def test_factory_builds_local_adapter():
    gateway = build_embedding_gateway(settings())

    assert isinstance(gateway, LocalEmbedding)
    assert gateway.dimension == 1024
    assert gateway.model == "/tmp/fake-model"


def test_factory_default_model_points_inside_the_repo():
    """模型没配时用默认路径 —— 而且它**必须与本工具的运行目录无关**。

    (相对 CWD 的路径会踩"从哪个目录跑就读哪个目录"的坑,重建工具那条已经踩过一次。)
    """
    gateway = build_embedding_gateway(settings(model_embedding_model=""))

    assert isinstance(gateway, LocalEmbedding)
    assert gateway.model.endswith("models\\Qwen3-Embedding-0.6B") or gateway.model.endswith(
        "models/Qwen3-Embedding-0.6B"
    )


@pytest.mark.parametrize("provider", ["", "placeholder"])
def test_empty_provider_still_falls_back_to_placeholder(provider):
    gateway = build_embedding_gateway(settings(model_embedding_provider=provider))

    assert isinstance(gateway, DeterministicPlaceholderEmbedding)


def test_unknown_provider_is_rejected():
    with pytest.raises(GatewayConfigError, match="未知的 Embedding provider"):
        build_embedding_gateway(settings(model_embedding_provider="dashscope"))
