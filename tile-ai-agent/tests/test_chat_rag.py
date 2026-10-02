"""T-1.6:RAG 上下文与 refs 生成(架构 §9.4)。

两条口径在这里钉死:
  1. 拼给模型的上下文**不含价格**(不变量 1:不报价)。
  2. refs 里的 SKU **必须经 Java 权威校验**,查不到就丢(硬约束 4:不得编造)。
"""

from __future__ import annotations

from app.chat.rag import build_refs, context_block, infer_part
from app.index.retrieval import RetrievedItem


def an_item(**payload) -> RetrievedItem:
    base = {"sku_code": "TL-3012", "name": "雪瓷白", "material": "陶瓷",
            "surface": "柔光", "spaces": ["bathroom"], "style_tags": ["现代"]}
    base.update(payload)
    return RetrievedItem(id=str(base.get("sku_code")), score=0.9, payload=base)


# ----------------------------------------------------------------------
# 上下文块:白名单拼接,不含价格
# ----------------------------------------------------------------------

def test_context_block_excludes_price():
    """**价格绝不能进上下文** —— 漏一个字段是 bug,漏一块价格是原则问题。"""
    item = an_item(price=268.0, pricePerSqm=268.0)

    text = context_block([item])

    assert "268" not in text
    assert "price" not in text.lower()
    assert "雪瓷白" in text and "陶瓷" in text, "该有的信息还得在"


def test_context_block_lists_multiple_items():
    text = context_block([an_item(sku_code="A"), an_item(sku_code="B")])

    assert "[1]" in text and "[2]" in text


# ----------------------------------------------------------------------
# 部位推断
# ----------------------------------------------------------------------

def test_infer_part_floor():
    assert infer_part("客厅用什么地砖") == "floor"


def test_infer_part_wall():
    assert infer_part("卫生间配什么墙砖") == "wall"


def test_infer_part_local():
    assert infer_part("要不要加一圈腰线") == "local"


def test_infer_part_defaults_to_wall():
    """推不出时默认 wall —— 这是**已知的启发式缺口**,不是权威判断。"""
    assert infer_part("推荐一款砖") == "wall"


# ----------------------------------------------------------------------
# refs:逐个校验,查不到就丢
# ----------------------------------------------------------------------

class FakeJavaApi:
    def __init__(self, known: dict[str, str]) -> None:
        self._known = known
        self.calls: list[str] = []

    def get_product(self, sku, *, trace_id=""):
        self.calls.append(sku)
        name = self._known.get(sku)
        return {"sku": sku, "name": name} if name else None


def test_build_refs_drops_unverifiable_skus():
    java_api = FakeJavaApi({"A": "甲砖"})

    refs = build_refs(
        [an_item(sku_code="A"), an_item(sku_code="B")],
        part="wall", java_api=java_api,
    )

    assert [r["sku"] for r in refs] == ["A"]
    assert java_api.calls == ["A", "B"], "每个候选都要真的去问过 Java"


def test_build_refs_uses_java_name():
    refs = build_refs([an_item(sku_code="A", name="旧名")],
                      part="wall", java_api=FakeJavaApi({"A": "权威名"}))

    assert refs[0]["name"] == "权威名"


def test_build_refs_empty_when_nothing_resolves():
    refs = build_refs([an_item(sku_code="X")], part="wall", java_api=FakeJavaApi({}))

    assert refs == []


def test_build_refs_deduplicates_and_caps():
    java_api = FakeJavaApi({sku: f"砖{sku}" for sku in "ABCD"})

    refs = build_refs(
        [an_item(sku_code=s) for s in "AABBCCDD"],
        part="wall", java_api=java_api, max_refs=3,
    )

    assert [r["sku"] for r in refs] == ["A", "B", "C"], "去重 + 上限 3"
    assert java_api.calls.count("A") == 1, "重复的 SKU 只该问一次"


def test_build_refs_carries_part():
    refs = build_refs([an_item(sku_code="A")], part="floor",
                      java_api=FakeJavaApi({"A": "甲"}))

    assert refs[0]["part"] == "floor"
