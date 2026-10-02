"""T-1.9:未选部位 AI 自动补齐(契约 `POST /internal/agent/complete-parts`)。

**纯单测**:模型用可编程替身、检索被打桩、Java 取数用假体,不出网、不花钱。

这一层要钉住的东西不多,但每一条都是产品规则或安全底线:
  - **只补地面/墙面,局部永不补**(PRD §5.2.4);
  - **用户选的一律不被覆盖**;
  - **不编造 SKU**:模型只能从候选里选,选了范围外的直接判降级;
  - **补不上就降级**(2026-10-02 拍板):不阻断生成,而不是报错卡住用户。
"""

from __future__ import annotations

import json

from app.agent.complete_parts import PARTS_TO_COMPLETE, complete_parts
from app.gateway.ports import Reply, ToolCall, ToolCallPlan, Usage
from app.index.retrieval import RetrievedItem

from fastapi.testclient import TestClient

from app.api import internal as internal_api
from app.main import app

client = TestClient(app)
URL = "/internal/agent/complete-parts"


# ----------------------------------------------------------------------
# 替身
# ----------------------------------------------------------------------

class FakeLLM:
    """可编程的假网关:让 `chat_with_tools` 返回指定的参数。"""

    def __init__(self, *, arguments: str | None = None, tool_calls=None) -> None:
        self._arguments = arguments
        self._tool_calls = tool_calls
        self.calls = 0

    def chat(self, messages, tools=None, stream=False):  # noqa: ARG002
        raise AssertionError("补齐应走 chat_with_tools,不该调 chat")

    def chat_with_tools(self, messages, tool_defs):  # noqa: ARG002
        self.calls += 1
        if self._arguments is None and not self._tool_calls:
            return ToolCallPlan(tool_calls=(), usage=Usage())
        arguments = self._arguments if self._arguments is not None else "{}"
        return ToolCallPlan(
            tool_calls=(ToolCall(id="c1", name="submit_completion", arguments=arguments),),
            usage=Usage(prompt_tokens=10, completion_tokens=5),
        )


class ExplodingLLM(FakeLLM):
    """模型调用直接炸 —— 模拟服务故障(应折 90001,而不是降级)。"""

    def chat_with_tools(self, messages, tool_defs):  # noqa: ARG002
        raise RuntimeError("gateway down")


class FakeJavaApi:
    def __init__(self, known: set[str] | None = None) -> None:
        self._known = known if known is not None else set()
        self.asked: list[str] = []

    def get_product(self, sku, *, trace_id=""):  # noqa: ARG002
        self.asked.append(sku)
        return {"sku": sku, "name": f"砖{sku}"} if sku in self._known else None


def an_item(sku: str, name: str = "雅灰石纹") -> RetrievedItem:
    return RetrievedItem(
        id=sku,
        score=0.9,
        payload={"sku_code": sku, "name": name, "material": "陶瓷", "surface": "柔光",
                 "spaces": ["bathroom"], "style_tags": ["modern"]},
    )


def run(monkeypatch, *, selected, llm=None, java_api=None, candidates=None, style="modern"):
    """跑一次补齐。`candidates=None` 表示"检索返回空"。"""
    monkeypatch.setattr(
        "app.agent.complete_parts.retrieve",
        lambda *args, **kwargs: list(candidates or []),
    )
    fake_llm = llm or FakeLLM(arguments="{}")
    return complete_parts(
        space="bathroom",
        style=style,
        selected=selected,
        llm=fake_llm,
        embedding=object(),
        qdrant=object(),
        java_api=java_api or FakeJavaApi({"A", "B"}),
        trace_id="t-1",
    ), fake_llm


# ----------------------------------------------------------------------
# 产品规则
# ----------------------------------------------------------------------

def test_local_is_never_completed():
    """**局部(腰线/花砖)是可选的,不强制补齐** —— 常量本身就是边界。"""
    assert "local" not in PARTS_TO_COMPLETE


def test_only_missing_floor_and_wall_are_filled(monkeypatch):
    picked, _ = run(
        monkeypatch,
        selected=[{"part": "local", "sku": "L1"}],
        llm=FakeLLM(arguments=json.dumps({"floor": "A", "wall": "B"})),
        candidates=[an_item("A"), an_item("B")],
    )

    parts = {item["part"]: item for item in picked}
    assert parts["floor"] == {"part": "floor", "sku": "A", "source": "ai"}
    assert parts["wall"] == {"part": "wall", "sku": "B", "source": "ai"}
    # 用户选的 local 原样保留,且**没有** local 的 ai 项
    assert parts["local"] == {"part": "local", "sku": "L1", "source": "user"}


def test_user_selection_is_kept_and_marked_user(monkeypatch):
    picked, _ = run(
        monkeypatch,
        selected=[{"part": "floor", "sku": "USER-FLOOR", "source": "ai"}],  # 外部传了 ai 也得改回 user
        llm=FakeLLM(arguments=json.dumps({"wall": "B"})),
        candidates=[an_item("B")],
    )

    floor = next(item for item in picked if item["part"] == "floor")
    assert floor == {"part": "floor", "sku": "USER-FLOOR", "source": "user"}


def test_nothing_missing_does_not_call_model(monkeypatch):
    picked, llm = run(
        monkeypatch,
        selected=[{"part": "floor", "sku": "F"}, {"part": "wall", "sku": "W"}],
    )

    assert llm.calls == 0, "两个部位都选了,不该再花一次模型调用"
    assert [item["sku"] for item in picked] == ["F", "W"]


# ----------------------------------------------------------------------
# 不编造
# ----------------------------------------------------------------------

def test_sku_outside_candidates_degrades(monkeypatch):
    """enum 已经限制了取值范围;模型硬给一个范围外的,**判降级**,不"尽力放行"。"""
    picked, _ = run(
        monkeypatch,
        selected=[{"part": "floor", "sku": "F"}],
        llm=FakeLLM(arguments=json.dumps({"wall": "NOT-IN-CANDIDATES"})),
        candidates=[an_item("A")],
    )

    assert [item["sku"] for item in picked] == ["F"], "不许放行候选之外的 SKU"


def test_missing_tool_call_degrades(monkeypatch):
    picked, _ = run(
        monkeypatch,
        selected=[{"part": "floor", "sku": "F"}],
        llm=FakeLLM(),  # 不调工具
        candidates=[an_item("A")],
    )

    assert [item["sku"] for item in picked] == ["F"]


def test_malformed_arguments_degrade(monkeypatch):
    picked, _ = run(
        monkeypatch,
        selected=[{"part": "floor", "sku": "F"}],
        llm=FakeLLM(arguments="{不是 json}"),
        candidates=[an_item("A")],
    )

    assert [item["sku"] for item in picked] == ["F"]


def test_authority_check_rejects_unknown_sku(monkeypatch):
    """索引里在售、权威库已下架的窗口 —— 回问 Java 取不到就不采用。"""
    picked, _ = run(
        monkeypatch,
        selected=[{"part": "floor", "sku": "F"}],
        llm=FakeLLM(arguments=json.dumps({"wall": "A"})),
        candidates=[an_item("A")],
        java_api=FakeJavaApi(known=set()),  # 一个都查不到
    )

    assert [item["sku"] for item in picked] == ["F"]


# ----------------------------------------------------------------------
# 降级:不阻断生成
# ----------------------------------------------------------------------

def test_no_candidates_degrades_without_calling_model(monkeypatch):
    picked, llm = run(
        monkeypatch,
        selected=[{"part": "floor", "sku": "F"}],
        candidates=[],  # 库里没有这个空间可用的在售砖
    )

    assert [item["sku"] for item in picked] == ["F"]
    assert llm.calls == 0, "没候选就不必调模型"


def test_partial_completion_keeps_what_worked(monkeypatch):
    """一个部位补上了、另一个没补上 —— **能帮就帮**,不要把已补的也丢掉。"""
    picked, _ = run(
        monkeypatch,
        selected=[{"part": "local", "sku": "L"}],
        llm=FakeLLM(arguments=json.dumps({"floor": "A", "wall": "ZZZ"})),
        candidates=[an_item("A")],
    )

    by_part = {item["part"]: item for item in picked}
    assert by_part["floor"]["source"] == "ai"
    assert "wall" not in by_part, "没补上的部位不该出现在结果里"


def test_service_failure_propagates(monkeypatch):
    """**服务故障不降级** —— 要抛上去折 90001,好让日志里看得见(与"补不上"分开)。"""
    monkeypatch.setattr("app.agent.complete_parts.retrieve",
                        lambda *args, **kwargs: [an_item("A")])

    try:
        complete_parts(space="bathroom", style="", selected=[{"part": "floor", "sku": "F"}],
                       llm=ExplodingLLM(), embedding=object(), qdrant=object(),
                       java_api=FakeJavaApi({"A"}), trace_id="t")
    except RuntimeError:
        return
    raise AssertionError("模型故障应当抛出,而不是被降级吞掉")


# ----------------------------------------------------------------------
# 端点层(契约 openapi-internal.yaml)
# ----------------------------------------------------------------------

def _wire(monkeypatch, *, candidates=None, llm=None, java_api=None):
    """把端点的依赖换成替身。

    ⚠️ `candidates` 用 `None` 表示"用默认候选",**不能**用 `or` 兜 ——
    空列表是**合法输入**(库里没有候选),用 `or` 会把 `[]` 悄悄换成默认值,
    于是"没候选"这条路径根本测不到。
    """
    deps = {
        "llm": llm or FakeLLM(arguments=json.dumps({"wall": "A"})),
        "embedding": object(),
        "qdrant": object(),
        "java_api": java_api or FakeJavaApi({"A"}),
    }
    items = [an_item("A")] if candidates is None else list(candidates)
    monkeypatch.setattr(internal_api, "_completion_deps", lambda: deps)
    monkeypatch.setattr("app.agent.complete_parts.retrieve", lambda *args, **kw: list(items))


def test_endpoint_returns_merged_parts(monkeypatch):
    _wire(monkeypatch)

    response = client.post(URL, json={"space": "bathroom", "style": "modern",
                                      "selectedParts": [{"part": "floor", "sku": "F"}]})

    assert response.status_code == 200, "业务结果统一 HTTP 200(§10.5)"
    body = response.json()
    assert body["code"] == 0
    parts = body["data"]["parts"]
    assert {item["part"]: item["source"] for item in parts} == {"floor": "user", "wall": "ai"}
    assert "traceId" in body


def test_endpoint_degrades_instead_of_failing(monkeypatch):
    """**补不上时照常返回**(而不是 40004)—— 这是"降级不阻断"的落点。"""
    _wire(monkeypatch, candidates=[])  # 没候选

    body = client.post(URL, json={"space": "bathroom", "selectedParts": [{"part": "floor", "sku": "F"}]}).json()

    assert body["code"] == 0, "补不上不该报错"
    assert body["data"]["parts"] == [{"part": "floor", "sku": "F", "source": "user"}]


def test_endpoint_blank_space_is_10001(monkeypatch):
    _wire(monkeypatch)

    body = client.post(URL, json={"space": "  ", "selectedParts": []}).json()

    assert body["code"] == 10001
    assert body["data"]["errors"], "要说清缺哪一项"


def test_endpoint_service_failure_is_90001(monkeypatch):
    _wire(monkeypatch, llm=ExplodingLLM())

    body = client.post(URL, json={"space": "bathroom", "selectedParts": [{"part": "floor", "sku": "F"}]}).json()

    assert body["code"] == 90001
    assert body["data"] is None


def test_endpoint_echoes_trace_id(monkeypatch):
    _wire(monkeypatch)

    body = client.post(URL, json={"space": "bathroom", "selectedParts": []},
                       headers={"X-Trace-Id": "trace-from-java"}).json()

    assert body["traceId"] == "trace-from-java"


def test_endpoint_enforces_service_token_when_configured(monkeypatch):
    class Stub:
        service_token = type("S", (), {"get_secret_value": staticmethod(lambda: "s3cret")})()

    monkeypatch.setattr(internal_api, "settings", Stub)

    assert client.post(URL, json={"space": "bathroom"}).status_code == 401
    assert client.post(URL, json={"space": "bathroom"},
                       headers={"X-Service-Token": "s3cret"}).status_code != 401
