"""T-1.7:自然语言 → 结构化户型(契约 `POST /internal/agent/parse-layout`)。

**纯单测**:LLM 网关用可编程的假体,不出网、不花钱。
真实模型的解析质量另有联调测试(`test_layout_parse_live.py`,默认跳过)。
"""

from __future__ import annotations

import json

import pytest

from app.agent.layout_parse import (
    REQUIRED_SPACE_FIELDS,
    SPACE_TYPES,
    LayoutParseError,
    build_messages,
    parse_layout,
    validate_layout,
)
from app.gateway.ports import Reply, ToolCall, ToolCallPlan, Usage


class FakeLLM:
    """可编程的假网关 —— 让它返回什么就返回什么。"""

    def __init__(self, *, arguments: str | None = None, tool_calls=None, calls: int = 0) -> None:
        self._arguments = arguments
        self._tool_calls = tool_calls
        self.calls = calls

    def chat(self, messages, tools=None, stream=False):  # noqa: ARG002
        raise AssertionError("解析应走 chat_with_tools,不该调 chat")

    def chat_with_tools(self, messages, tool_defs):  # noqa: ARG002
        self.calls += 1
        if self._arguments is None:
            return ToolCallPlan(tool_calls=self._tool_calls or (), usage=Usage())
        return ToolCallPlan(
            tool_calls=(ToolCall(id="c1", name="submit_layout", arguments=self._arguments),),
            usage=Usage(prompt_tokens=10, completion_tokens=5),
        )


def a_room(**overrides) -> dict:
    room = {"type": "bathroom", "name": "卫生间",
            "length": 2.0, "width": 1.8, "height": 2.7}
    room.update(overrides)
    return room


# ----------------------------------------------------------------------
# 校验:不返回半成品(架构 §10.4 决策 2)
# ----------------------------------------------------------------------

def test_complete_layout_passes():
    assert validate_layout({"spaces": [a_room()]}) == []


def test_missing_dimension_is_reported_field_by_field():
    """缺尺寸必须**逐项**报出来 —— 只说"参数不对"用户不知道补什么。"""
    errors = validate_layout({"spaces": [a_room(width=None)]})

    assert len(errors) == 1
    assert errors[0]["field"] == "spaces[0].width"
    assert "宽" in errors[0]["reason"]


def test_all_required_fields_are_checked():
    """逐个抽掉必填字段都该被抓住 —— 别漏掉某一个。"""
    for field in REQUIRED_SPACE_FIELDS:
        room = a_room()
        del room[field]
        errors = validate_layout({"spaces": [room]})
        assert any(e["field"] == f"spaces[0].{field}" for e in errors), \
            f"字段 {field} 缺失时没被发现"


def test_empty_spaces_is_a_failure_not_a_success():
    """空 spaces 是失败 —— 一个没有空间的户型,面积无从算起。"""
    errors = validate_layout({"spaces": []})
    assert errors and errors[0]["field"] == "spaces"


def test_non_positive_dimension_is_rejected():
    errors = validate_layout({"spaces": [a_room(length=0)]})
    assert any("不是正数" in e["reason"] for e in errors)


def test_unknown_space_type_is_rejected():
    """模型硬凑一个空间类型时挡住 —— 比放进报告里让报价算错好。"""
    errors = validate_layout({"spaces": [a_room(type="toilet")]})
    assert any("未知的空间类型" in e["reason"] for e in errors)


def test_all_builtin_space_types_are_accepted():
    for space_type in SPACE_TYPES:
        assert validate_layout({"spaces": [a_room(type=space_type)]}) == []


def test_opening_dimensions_are_checked():
    room = a_room(openings=[{"type": "door", "w": 0.8}])  # 缺 h
    errors = validate_layout({"spaces": [room]})
    assert any(e["field"] == "spaces[0].openings[0].h" for e in errors)

    room_bad = a_room(openings=[{"type": "hole", "w": 0.8, "h": 2.1}])
    errors_bad = validate_layout({"spaces": [room_bad]})
    assert any("未知的开口类型" in e["reason"] for e in errors_bad)


# ----------------------------------------------------------------------
# 解析:走工具调用,不"让它吐 JSON 再捞"
# ----------------------------------------------------------------------

def test_parse_returns_layout_from_tool_call():
    layout = {"spaces": [a_room(), a_room(type="living_room", name="客厅",
                                          length=5.0, width=4.0)]}
    gateway = FakeLLM(arguments=json.dumps(layout, ensure_ascii=False))

    result = parse_layout(gateway, "三室两厅,客厅 5m×4m,卫生间 2m×1.8m")

    assert len(result["spaces"]) == 2
    assert result["spaces"][1]["type"] == "living_room"
    assert gateway.calls == 1


def test_parse_reports_incomplete_layout_as_error():
    """模型只解析出一个房间且缺尺寸 → 报错,而不是把半成品交出去。"""
    gateway = FakeLLM(arguments=json.dumps({"spaces": [a_room(length=None)]}))

    with pytest.raises(LayoutParseError) as caught:
        parse_layout(gateway, "一个没有长宽的卫生间")

    assert caught.value.errors[0]["field"] == "spaces[0].length"


def test_model_declining_to_call_the_tool_is_a_failure():
    """用户写的不是户型描述时,模型不调工具 —— 这是失败,不是空结果。"""
    gateway = FakeLLM(tool_calls=())

    with pytest.raises(LayoutParseError, match="没能"):
        parse_layout(gateway, "随便看看")


def test_malformed_arguments_do_not_degrade_to_best_effort():
    """用了 function calling 还拿到非法 JSON → 直接失败,不"尽力解析"。

    半成品流进报价的代价,远大于让用户重试一次。
    """
    gateway = FakeLLM(arguments="{不是合法 JSON")

    with pytest.raises(LayoutParseError) as caught:
        parse_layout(gateway, "客厅 5m×4m")

    assert "格式不正确" in caught.value.errors[0]["reason"]


def test_blank_text_short_circuits_without_calling_the_model():
    gateway = FakeLLM(arguments="{}")

    with pytest.raises(LayoutParseError):
        parse_layout(gateway, "   ")

    assert gateway.calls == 0, "空描述不该浪费一次模型调用"


def test_prompt_tells_the_model_not_to_invent_openings():
    """提示词里必须明确"不编造门窗" —— 编出来的开口会直接改报价。"""
    system = build_messages("卫生间 2m×1.8m")[0].content
    assert "不要编造" in system
    assert "米" in system


# ----------------------------------------------------------------------
# 端点层(契约 openapi-internal.yaml)
# ----------------------------------------------------------------------

from fastapi.testclient import TestClient  # noqa: E402

from app.api import internal as internal_api  # noqa: E402
from app.main import app  # noqa: E402

client = TestClient(app)
URL = "/internal/agent/parse-layout"


def test_endpoint_returns_envelope_with_parsed_layout(monkeypatch):
    layout = {"spaces": [a_room()]}
    monkeypatch.setattr(internal_api, "build_llm_gateway",
                        lambda: FakeLLM(arguments=json.dumps(layout, ensure_ascii=False)))

    response = client.post(URL, json={"text": "卫生间 2m×1.8m"})

    assert response.status_code == 200, "业务结果统一 HTTP 200(§10.5)"
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["spaces"][0]["type"] == "bathroom"
    assert "traceId" in body


def test_endpoint_does_not_derive_areas(monkeypatch):
    """`areas` 留给 Java 派生 —— 在这里算就会出现两份实现。

    派生逻辑已在 Java 的 LayoutAreaCalculator(架构 §8.3 的公式),
    Python 再实现一遍迟早不一致。故断言响应里**没有** areas。
    """
    layout = {"spaces": [a_room()]}
    monkeypatch.setattr(internal_api, "build_llm_gateway",
                        lambda: FakeLLM(arguments=json.dumps(layout, ensure_ascii=False)))

    body = client.post(URL, json={"text": "卫生间 2m×1.8m"}).json()

    assert "areas" not in body["data"]["spaces"][0]


def test_endpoint_maps_parse_failure_to_10001_with_errors(monkeypatch):
    """解析不出完整户型 → **10001 + data.errors[]**,这是输入问题不是服务故障。"""
    monkeypatch.setattr(internal_api, "build_llm_gateway",
                        lambda: FakeLLM(arguments=json.dumps({"spaces": []})))

    body = client.post(URL, json={"text": "一个没有尺寸的客厅"}).json()

    assert body["code"] == 10001
    assert body["data"]["errors"], "必须给出逐项原因,否则用户不知道补什么"


def test_endpoint_blank_text_is_10001(monkeypatch):
    monkeypatch.setattr(internal_api, "build_llm_gateway", lambda: FakeLLM(arguments="{}"))

    body = client.post(URL, json={"text": "   "}).json()

    assert body["code"] == 10001


def test_gateway_failure_is_90001_not_10001(monkeypatch):
    """**模型网关不可用是服务故障,不能伪装成"你的描述有问题"。**

    这条很关键:两者对用户的含义完全不同 —— 前者该重试/找运维,后者该改描述。
    混在一起会让用户对着一个配置错误反复改自己的输入。
    """
    def boom():
        raise RuntimeError("MODEL_LLM_API_KEY 未配置")

    monkeypatch.setattr(internal_api, "build_llm_gateway", boom)

    body = client.post(URL, json={"text": "卫生间 2m×1.8m"}).json()

    assert body["code"] == 90001
    assert body["data"] is None


def test_endpoint_echoes_trace_id(monkeypatch):
    """透传 Java 的 traceId —— 跨进程串联同一条链路(§13.7)。"""
    monkeypatch.setattr(internal_api, "build_llm_gateway",
                        lambda: FakeLLM(arguments=json.dumps({"spaces": [a_room()]})))

    body = client.post(URL, json={"text": "卫生间 2m×1.8m"},
                       headers={"X-Trace-Id": "trace-from-java"}).json()

    assert body["traceId"] == "trace-from-java"


def test_endpoint_enforces_service_token_when_configured(monkeypatch):
    """配了服务令牌就得校验(契约 serviceToken);未配置时放行便于本地联调。"""
    class Stub:
        service_token = type("S", (), {"get_secret_value": staticmethod(lambda: "s3cret")})()

    monkeypatch.setattr(internal_api, "settings", Stub)

    assert client.post(URL, json={"text": "x"}).status_code == 401
    assert client.post(URL, json={"text": "x"},
                       headers={"X-Service-Token": "s3cret"}).status_code != 401
