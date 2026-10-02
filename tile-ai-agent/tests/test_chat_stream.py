"""T-1.6:对话咨询(契约 `POST /internal/agent/chat`,SSE)。

**纯单测**:模型/检索/Redis/Java 取数全部用替身,不出网、不花钱、不依赖中间件。

⚠️ **SSE 事件集不在任何门禁的覆盖范围内**(G1–G7 与契约比对都不看事件序列,
见 `openapi-verification.md` §40.4)。所以**本文件就是 D-7 那条裁决的唯一判据**
—— 帧的顺序、互斥、`[DONE]` 的有无,全靠这里的断言钉住。
"""

from __future__ import annotations

import json
from dataclasses import replace

from app.gateway.ports import Reply, ReplyChunk, Usage
from app.index.retrieval import RetrievedItem

from fastapi.testclient import TestClient

from app.api import internal as internal_api
from app.chat.pipeline import ChatDeps
from app.chat.session_store import RedisChatContextStore
from app.main import app

client = TestClient(app)
URL = "/internal/agent/chat"


# ----------------------------------------------------------------------
# 替身
# ----------------------------------------------------------------------

class FakeLLM:
    """可编程网关:流式产出 delta;非流式只回答意图分类。

    ⚠️ `chat` **必须是普通函数**(不能在里面直接写 `yield`)—— 一旦写成生成器函数,
    非流式分支也会返回生成器,与真实网关(`ResilientLLMGateway.chat` 非流式返回
    `Reply`、流式返回迭代器)的契约就不一致了,测试会"假装通过"或误判。
    所以流式单独放一个 `_stream()` 生成器方法。
    """

    def __init__(self, *, deltas=("雪瓷白", "很适合"), verdict="OK") -> None:
        self._deltas = deltas
        self._verdict = verdict
        self.stream_calls = 0
        self.classify_calls = 0

    def chat(self, messages, tools=None, stream=False):  # noqa: ARG002
        if stream:
            return self._stream()
        self.classify_calls += 1
        return Reply(content=self._verdict)

    def _stream(self):
        self.stream_calls += 1
        for delta in self._deltas:
            yield ReplyChunk(delta=delta)
        yield ReplyChunk(usage=Usage(prompt_tokens=3, completion_tokens=2), done=True)


class ExplodingLLM(FakeLLM):
    """分类调用就炸 —— 用来验证"分类器故障一律放行"。"""

    def chat(self, messages, tools=None, stream=False):  # noqa: ARG002
        if stream:
            return self._stream()
        self.classify_calls += 1
        raise RuntimeError("分类器连不上")


class FakeStore:
    def __init__(self) -> None:
        self.turns: list[tuple[str, str, str]] = []

    def load(self, session_id):
        return []

    def append(self, session_id, role, content):
        self.turns.append((session_id, role, content))


class _BrokenRedis:
    """连不上的 Redis:任何命令都抛 —— 用来验证降级路径。"""

    def lrange(self, *args, **kwargs):
        raise RuntimeError("redis down")

    def pipeline(self):
        raise RuntimeError("redis down")


class FakeEmbedding:
    def embed(self, texts):
        return [[0.0, 0.0, 0.0] for _ in texts]


class FakeJavaApi:
    """只认得 known 里的 SKU;其余返回 None(模拟"不存在/已下架")。"""

    def __init__(self, known: dict[str, str] | None = None) -> None:
        self._known = known if known is not None else {}
        self.calls: list[str] = []

    def get_product(self, sku, *, trace_id=""):  # noqa: ARG002
        self.calls.append(sku)
        if sku in self._known:
            return {"sku": sku, "name": self._known[sku]}
        return None


def an_item(sku: str = "TL-3012", name: str = "雪瓷白", price: float = 268.0) -> RetrievedItem:
    return RetrievedItem(
        id=sku,
        score=0.9,
        payload={"sku_code": sku, "name": name, "material": "陶瓷",
                 "spaces": ["bathroom"], "style_tags": ["现代"], "price": price},
    )


def wire(monkeypatch, *, items=None, gateway=None, java_api=None, store=None) -> ChatDeps:
    """把端点依赖换成替身,返回被装配的 deps(便于断言)。"""
    deps = ChatDeps(
        gateway=gateway or FakeLLM(),
        embedding=FakeEmbedding(),
        qdrant=object(),
        store=store or FakeStore(),
        java_api=java_api or FakeJavaApi({"TL-3012": "雪瓷白"}),
        trace_id="",
    )
    monkeypatch.setattr(internal_api, "_chat_deps", lambda trace_id: replace(deps, trace_id=trace_id))
    monkeypatch.setattr("app.chat.rag.retrieve", lambda *a, **k: list(items if items is not None else [an_item()]))
    return deps


def posted(**body):
    return client.post(URL, json=body)


def frames_of(body: str) -> list[str]:
    """取出所有 `data:` 帧的载荷(`[DONE]` 保持字面量)。"""
    return [line[len("data: "):] for line in body.splitlines() if line.startswith("data: ")]


def payloads_of(body: str) -> list[dict]:
    return [json.loads(f) for f in frames_of(body) if f != "[DONE]"]


# ----------------------------------------------------------------------
# 正常结局:帧序列(D-7)
# ----------------------------------------------------------------------

def test_new_session_frame_sequence(monkeypatch):
    """`delta… → refs → [DONE]`,且 **Python 不发 sessionId 帧**(那是 Java 的职责)。"""
    wire(monkeypatch)

    response = client.post(URL, json={"sessionId": "s_1", "message": "卫生间配什么墙砖"})

    assert response.status_code == 200
    frames = frames_of(response.text)
    assert frames[-1] == "[DONE]", "正常结局必须以 [DONE] 收尾"
    assert not any("sessionId" in f for f in frames), "sessionId 首帧由 Java 下发,Python 不得发"

    kinds = [("delta" in p, "refs" in p) for p in payloads_of(response.text)]
    assert kinds[0] == (True, False), "第一帧应是 delta"
    assert kinds[-1] == (False, True), "refs 应是最后一帧数据"
    assert sum(1 for d, _ in kinds if d) == 2, "两帧 delta 对应两次输出"
    assert sum(1 for _, r_ in kinds if r_) == 1, "refs 至多一帧"


def test_delta_text_is_ordered(monkeypatch):
    wire(monkeypatch, gateway=FakeLLM(deltas=("雪", "瓷", "白")))

    body = posted(sessionId="s_1", message="推荐一款").text

    deltas = "".join(p["delta"] for p in payloads_of(body) if "delta" in p)
    assert deltas == "雪瓷白"


def test_refs_only_contain_real_skus(monkeypatch):
    """检索给出 A、B,但只有 A 能被 Java 查到 ⇒ refs 里**只应有 A**(不得编造)。"""
    java_api = FakeJavaApi({"TL-3012": "雪瓷白"})
    wire(monkeypatch, items=[an_item("TL-3012"), an_item("TL-9999", "不存在的砖")], java_api=java_api)

    body = posted(sessionId="s_1", message="推荐").text
    refs = next(p["refs"] for p in payloads_of(body) if "refs" in p)

    assert [r["sku"] for r in refs] == ["TL-3012"]
    assert java_api.calls == ["TL-3012", "TL-9999"], "每个候选都要真的去问过 Java"


def test_refs_is_empty_when_none_resolve(monkeypatch):
    """一个都查不到时给**空数组**,而不是编一个出来。"""
    wire(monkeypatch, items=[an_item("TL-9999")], java_api=FakeJavaApi({}))

    body = posted(sessionId="s_1", message="推荐").text
    refs = next(p["refs"] for p in payloads_of(body) if "refs" in p)

    assert refs == []


def test_refs_name_comes_from_java(monkeypatch):
    """refs 的 name 取 Java 权威值,不是检索 payload 里的旧名字。"""
    wire(monkeypatch, items=[an_item("TL-3012", name="旧名")],
         java_api=FakeJavaApi({"TL-3012": "权威新名"}))

    body = posted(sessionId="s_1", message="推荐").text
    refs = next(p["refs"] for p in payloads_of(body) if "refs" in p)

    assert refs[0]["name"] == "权威新名"


# ----------------------------------------------------------------------
# 问价 → 50001(不变量 1)
# ----------------------------------------------------------------------

def test_price_question_is_refused_with_50001(monkeypatch):
    """**不变量 1:问价一律拒答。** 关键词闸门命中 ⇒ 不调模型、无 delta、无 [DONE]。"""
    llm = FakeLLM()
    wire(monkeypatch, gateway=llm)

    body = posted(sessionId="s_1", message="这款砖多少钱一平?").text
    frames = frames_of(body)

    assert len(frames) == 1, "拒答只该有一帧"
    error = json.loads(frames[0])["error"]
    assert error["code"] == 50001
    assert llm.stream_calls == 0 and llm.classify_calls == 0, "命中关键词闸门就不该再调模型"


def test_effect_image_question_is_refused(monkeypatch):
    """问效果图同样越界(不变量 2:不产出效果图)。"""
    wire(monkeypatch)

    error = json.loads(frames_of(posted(sessionId="s_1", message="帮我出个效果图").text)[0])["error"]

    assert error["code"] == 50001


def test_llm_verdict_out_refuses(monkeypatch):
    """关键词没命中、但模型判越界 ⇒ 仍 50001。"""
    wire(monkeypatch, gateway=FakeLLM(verdict="OUT"))

    error = json.loads(frames_of(posted(sessionId="s_1", message="这个能用在淋浴房吗").text)[0])["error"]

    assert error["code"] == 50001


def test_classifier_failure_passes_through(monkeypatch):
    """**分类器故障一律放行** —— 不能因为分类器连不上就把正常咨询打成错误。"""
    wire(monkeypatch, gateway=ExplodingLLM())

    frames = frames_of(posted(sessionId="s_1", message="卫生间配什么墙砖").text)

    assert frames[-1] == "[DONE]", "分类器挂了也该正常答完"


# ----------------------------------------------------------------------
# 降级与边界
# ----------------------------------------------------------------------

def test_redis_failure_does_not_break_the_stream(monkeypatch):
    """短期上下文读写失败 ⇒ 降级,不影响回答(§13.3)。

    这里用**真实的** `RedisChatContextStore` + 一个坏掉的 Redis 客户端,
    走的就是生产里的降级分支 —— 而不是让替身"违约抛异常"(那测的是替身,不是代码)。
    """
    wire(monkeypatch, store=RedisChatContextStore(_BrokenRedis()))

    frames = frames_of(posted(sessionId="s_1", message="推荐一款墙砖").text)

    assert frames[-1] == "[DONE]"


def test_empty_model_answer_gets_a_fallback(monkeypatch):
    """**模型一个字都没吐** → 给一句人话兜底,而不是让前端看到空白。

    这一档既不是越界(50001)也不是故障(90001):问答本身是成功的,只是内容为空。
    兜底文案必须**作为 delta 帧发出** —— Java 靠累积 delta 落库,前端靠 delta 渲染;
    只在内部替换会让三者对不上。
    """
    store = FakeStore()
    wire(monkeypatch, gateway=FakeLLM(deltas=()), store=store)

    body = posted(sessionId="s_1", message="推荐一款墙砖").text
    frames = frames_of(body)

    deltas = [p["delta"] for p in payloads_of(body) if "delta" in p]
    assert deltas, "空回答必须补一帧 delta,否则前端是空白的"
    assert all(delta.strip() for delta in deltas), "兜底文案不能是空串"
    assert frames[-1] == "[DONE]", "兜底之后仍按正常结局收尾(这不是错误)"
    assert not any("error" in frame for frame in frames), "空回答不该被当成错误"

    assistant = [content for _, role, content in store.turns if role == "assistant"]
    assert assistant and assistant[0].strip(), "兜底文案也要写进短期上下文"


def test_empty_message_is_10001_frame(monkeypatch):
    """空消息 → **error 10001 帧**,而不是 HTTP 422(信封形状要一致)。"""
    wire(monkeypatch)

    response = client.post(URL, json={"sessionId": "s_1", "message": "   "})

    assert response.status_code == 200
    error = json.loads(frames_of(response.text)[0])["error"]
    assert error["code"] == 10001


def test_error_frame_echoes_trace_id(monkeypatch):
    wire(monkeypatch)

    body = client.post(URL, json={"sessionId": "s_1", "message": "多少钱"},
                       headers={"X-Trace-Id": "trace-from-java"}).text
    error = json.loads(frames_of(body)[0])["error"]

    assert error["traceId"] == "trace-from-java"


def test_chat_is_persisted_to_short_term_context(monkeypatch):
    """一段问答结束后,user 与 assistant 都应写进短期上下文。"""
    store = FakeStore()
    wire(monkeypatch, store=store)

    posted(sessionId="s_1", message="推荐一款墙砖")

    assert [(role, content) for _, role, content in store.turns] == [
        ("user", "推荐一款墙砖"),
        ("assistant", "雪瓷白很适合"),
    ]


def test_endpoint_enforces_service_token_when_configured(monkeypatch):
    """配了服务令牌就得校验(契约 serviceToken);未配置时放行便于本地联调。

    依赖同样换成替身:凭证放行后会真的走到编排里,若用真实 `_chat_deps`
    就会去连本机 `.env` 配的**真实模型**(出网、花钱、还可能卡住)。
    """
    wire(monkeypatch)

    class Stub:
        service_token = type("S", (), {"get_secret_value": staticmethod(lambda: "s3cret")})()

    monkeypatch.setattr(internal_api, "settings", Stub)

    assert client.post(URL, json={"message": "x"}).status_code == 401
    assert client.post(URL, json={"message": "x"},
                       headers={"X-Service-Token": "s3cret"}).status_code != 401
