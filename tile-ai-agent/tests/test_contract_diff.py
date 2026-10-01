"""契约 ↔ 实现比对(**Python 侧**,路线图 T-2.1)。

Java 侧那半在 `tile-ai-server` 的 `com.tileai.contract.ContractImplDiffTest` 里 ——
那侧已经比到**类型 / 必填 / 枚举值**(2026-10-01 收口,见 openapi-verification.md §40)。
**本侧刻意保持轻**:归属 Python 的端点只有 4 条、仅 1 条实装,请求体是 `text` + `params: dict`、
响应是 `dict[str, Any]` —— 再往里比对只会产出一堆「跳过」而不是信号。
两侧**口径一致**的部分:比**路径 + HTTP 方法 + 字段名**;「跳过 ≠ 通过」;
已知未开工必须带出处,没出处的缺失单独高亮;挂账过期判红。

**实现侧从哪来**:`app.openapi()` —— FastAPI 按真实路由与 pydantic 模型生成,
是"实现真实长什么样"的第一手材料,不需要起服务。

**本侧覆盖不到**(不许读成「已绑住」):类型 / 必填 / 状态码与业务错误码 · 状态机与幂等 ·
SSE 事件集(`/internal/agent/chat` 的 text/event-stream 完全不解析)·
跨文件 `$ref` 里更深的结构。SSE 那条尤其要记住:契约里那个端点的价值几乎全在事件集上。

**唯一在 Python 侧比枚举值的地方**:`test_opening_and_space_types_match_contract` ——
`app.openapi()` 里根本没有这两个常量,只能直接 import 模块常量比对。
它是 §31.2 那次真实分叉(实现多出 `bay_window`/`other`)**唯一**的兜底。

**为什么红/不红**:默认只出清单(报告落 `outputs/reports/contract-impl-diff-python.md`),
真正红的是"工具自己不可信"的两类 —— 锚点未命中、挂账已过期。
要把它变成硬门禁,设环境变量 `CONTRACT_DIFF_GATE=1`。
"""

from __future__ import annotations

import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

from app.agent import layout_parse
from app.main import app

REPO_ROOT = Path(__file__).resolve().parents[2]
CONTRACT_FILE = "openapi-internal.yaml"
COMMON_FILE = "openapi-common.yaml"
REPORT = REPO_ROOT / "outputs" / "reports" / "contract-impl-diff-python.md"
GAPS_FILE = Path(__file__).resolve().parent / "contract-impl-gaps.yaml"

METHODS = {"get", "post", "put", "patch", "delete"}


# ----------------------------------------------------------------------
# 挂账 / 豁免 / 锚点:从 contract-impl-gaps.yaml 读(键名与 Java 侧逐字一致)
# ----------------------------------------------------------------------

def _load_gaps() -> tuple[list[str], dict[str, str], dict[tuple[str, str], tuple[str, str]]]:
    """读清单并当场校验 —— 挂账不许裸挂、豁免不许没理由、锚点不许为空。

    读不到 / 不合格就抛。**绝不退化成"没有挂账"** —— 那会让全部未开工端点瞬间变成
    「意外缺失」,或者更糟:清单被清空时缺失会被静默当成正常。
    """
    if not GAPS_FILE.exists():
        raise AssertionError(
            f"找不到 {GAPS_FILE.name} —— 挂账清单不能缺,否则缺失的端点无从区分"
            "「未开工」与「出事了」")

    with GAPS_FILE.open(encoding="utf-8") as handle:
        doc = yaml.safe_load(handle) or {}

    canaries: list[str] = []
    for item in doc.get("canaries") or []:
        text = str(item).strip()
        if not re.match(r"(?i)(GET|POST|PUT|PATCH|DELETE)\s+/.*", text):
            raise AssertionError(f"锚点格式应为 \"METHOD /path\",实际是:{text}")
        canaries.append(text)
    if not canaries:
        raise AssertionError("canaries 为空 —— 没有锚点,路径拼接写错时会静默全绿")

    ignored: dict[str, str] = {}
    for item in doc.get("ignoredImplPaths") or []:
        path, reason = str(item.get("path", "")).strip(), str(item.get("reason", "")).strip()
        if not path or not reason:
            raise AssertionError(f"豁免项必须同时写 path 与 reason:{item}")
        ignored[path] = reason

    gaps: dict[tuple[str, str], tuple[str, str]] = {}
    for item in doc.get("knownGaps") or []:
        method = str(item.get("method", "")).strip().upper()
        path = str(item.get("path", "")).strip()
        source = str(item.get("source", "")).strip()
        note = str(item.get("note", "")).strip()
        if not method or not path.startswith("/"):
            raise AssertionError(f"挂账项缺 method/path:{item}")
        if not source:
            raise AssertionError(f"挂账项缺 source(出处),不许裸挂:{method} {path}")
        gaps[(method, path)] = (source, note)

    return canaries, ignored, gaps


# ----------------------------------------------------------------------
# 读取:契约侧
# ----------------------------------------------------------------------

def _read(name: str) -> dict[str, Any]:
    with (REPO_ROOT / name).open(encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def _split_ref(ref: str, current: str) -> tuple[str, str]:
    file_part, _, pointer = ref.partition("#")
    return (file_part.replace("./", "") or current), pointer


def _walk(docs: dict[str, dict], name: str, pointer: str, ref: str) -> Any:
    node: Any = docs[name]
    for segment in pointer.split("/"):
        if not segment:
            continue
        if not isinstance(node, dict):
            raise AssertionError(f"悬空引用 {ref}:{pointer} 在 {name} 里走到了非 mapping")
        node = node.get(segment.replace("~1", "/").replace("~0", "~"))
    if node is None:
        raise AssertionError(f"解析不到的引用 {ref} —— 已按基准文件 {name} 去找 {pointer}")
    return node


def _resolve(node: Any, current: str, docs: dict[str, dict], depth: int = 0) -> Any:
    """把 $ref 就地解析掉。

    `$ref` 是相对**它所在的那份文件**,所以每进一层都要换基准文件 ——
    c/internal 里引用 common 的 schema,而 common 里又写着 `#/...`,那个 `#` 指的是 common。
    """
    if depth > 20:
        return node
    if isinstance(node, list):
        return [_resolve(item, current, docs, depth) for item in node]
    if not isinstance(node, dict):
        return node
    ref = node.get("$ref")
    if isinstance(ref, str):
        target_file, pointer = _split_ref(ref, current)
        resolved = _resolve(_walk(docs, target_file, pointer, ref), target_file, docs, depth + 1)
        if not isinstance(resolved, dict):
            return resolved
        out = dict(resolved)
        for key, value in node.items():
            if key != "$ref":
                out[key] = _resolve(value, current, docs, depth)
        return out
    return {key: _resolve(value, current, docs, depth) for key, value in node.items()}


def _merge_into(out: dict[str, Any], member: dict[str, Any]) -> None:
    for key, value in member.items():
        if key == "properties" and isinstance(value, dict):
            out["properties"] = {**(out.get("properties") or {}), **value}
        elif key == "required" and isinstance(value, list):
            out["required"] = list(dict.fromkeys([*(out.get("required") or []), *value]))
        else:
            out[key] = value


def _flatten(node: Any) -> dict[str, Any]:
    """摊平 allOf:成员按顺序叠加(靠后的覆盖靠前的),节点自身的键最后叠加。"""
    if not isinstance(node, dict):
        return {}
    out: dict[str, Any] = {}
    for member in node.get("allOf") or []:
        _merge_into(out, _flatten(member))
    _merge_into(out, {key: value for key, value in node.items() if key != "allOf"})
    return out


def _json_schema(holder: dict[str, Any] | None) -> Any:
    if not isinstance(holder, dict):
        return None
    return ((holder.get("content") or {}).get("application/json") or {}).get("schema")


def _contract_ops() -> dict[tuple[str, str], dict[str, Any]]:
    """契约里 `x-direction` 指向 Python 的操作 —— 归属由契约自证,不手写清单。"""
    docs = {CONTRACT_FILE: _read(CONTRACT_FILE), COMMON_FILE: _read(COMMON_FILE)}
    ops: dict[tuple[str, str], dict[str, Any]] = {}
    for path, item in (docs[CONTRACT_FILE].get("paths") or {}).items():
        for method, op in item.items():
            if method.lower() not in METHODS:
                continue
            direction = str(op.get("x-direction", ""))
            if not direction.endswith("Python"):
                continue
            schema = _flatten(_resolve(_json_schema(op.get("requestBody")), CONTRACT_FILE, docs))
            props = schema.get("properties")
            data = _data_schema(op, CONTRACT_FILE, docs)
            ops[(method.upper(), _normalize(path))] = {
                "path": path,
                "summary": op.get("summary", ""),
                "request": set(props) if isinstance(props, dict) else None,
                "response": data,
            }
    return ops


def _data_schema(op: dict[str, Any], current: str, docs: dict[str, dict]) -> set[str] | None:
    """响应 `data` 的顶层字段名;契约没声明则为 None(无从比)。"""
    ok = ((op.get("responses") or {}).get("200") or {})
    schema = _json_schema(ok)
    if schema is None:
        return set()
    props = _flatten(_resolve(schema, current, docs)).get("properties") or {}
    if "data" not in props:
        return None
    data = _flatten(_resolve(props["data"], current, docs))
    data_props = data.get("properties")
    return set(data_props) if isinstance(data_props, dict) else None


# ----------------------------------------------------------------------
# 读取:实现侧(不经 HTTP,直接问 FastAPI 要它生成的 OpenAPI)
# ----------------------------------------------------------------------

@dataclass
class Impl:
    method: str
    path: str
    request: set[str] | None
    response: set[str] | None

    @property
    def key(self) -> tuple[str, str]:
        return self.method, _normalize(self.path)

    @property
    def brief(self) -> str:
        return f"{self.method:<6} {self.path}"


def _impl_fields(schema: Any, schemas: dict[str, Any]) -> set[str] | None:
    if not isinstance(schema, dict):
        return None
    ref = schema.get("$ref")
    if isinstance(ref, str):
        schema = schemas.get(ref.rsplit("/", 1)[-1]) or {}
    props = schema.get("properties")
    return set(props) if isinstance(props, dict) else None


def _impl_endpoints() -> list[Impl]:
    spec = app.openapi()
    schemas = (spec.get("components") or {}).get("schemas") or {}
    out: list[Impl] = []
    for path, item in (spec.get("paths") or {}).items():
        for method, op in item.items():
            if method.lower() not in METHODS:
                continue
            body = _json_schema(op.get("requestBody"))
            ok = ((op.get("responses") or {}).get("200") or {})
            out.append(Impl(
                method.upper(),
                path,
                _impl_fields(body, schemas) if body is not None else set(),
                _impl_fields(_json_schema(ok), schemas),
            ))
    return out


def _normalize(path: str) -> str:
    path = re.sub(r"\{[^}]*\}", "{}", path.strip())
    path = re.sub(r"/{2,}", "/", path)
    if len(path) > 1 and path.endswith("/"):
        path = path[:-1]
    return path if path.startswith("/") else "/" + path


# 清单里的路径统一走 _normalize,好与比对时构造的键逐字对得上。
# (定义在这里而不是文件头的 import 处,是因为 _load_gaps 之外还要用 _normalize。)
_CANARIES, _IGNORED, _GAPS = _load_gaps()
CANARIES: list[str] = _CANARIES
IGNORED_IMPL_PATHS: dict[str, str] = {_normalize(p): r for p, r in _IGNORED.items()}
KNOWN_GAPS: dict[tuple[str, str], tuple[str, str]] = {
    (m, _normalize(p)): v for (m, p), v in _GAPS.items()
}



# ----------------------------------------------------------------------
# 比对
# ----------------------------------------------------------------------

@dataclass
class Result:
    contract_count: int
    impl_count: int
    matched: int = 0
    unexpected_missing: list[tuple[str, str, str]] = field(default_factory=list)
    known_missing: list[tuple[str, str, str, str]] = field(default_factory=list)
    extra_impl: list[Impl] = field(default_factory=list)
    ignored_impl: list[tuple[Impl, str]] = field(default_factory=list)
    skipped: list[str] = field(default_factory=list)
    field_diffs: list[str] = field(default_factory=list)
    stale_gaps: list[tuple[str, str, str]] = field(default_factory=list)
    broken_canaries: list[str] = field(default_factory=list)

    @property
    def has_findings(self) -> bool:
        return bool(self.unexpected_missing or self.extra_impl
                    or self.field_diffs or self.stale_gaps or self.broken_canaries)


def _compare_fields(method: str, path: str, contract: set[str] | None,
                    impl: set[str] | None, where: str,
                    out: list[str], skipped: list[str]) -> None:
    """比顶层字段名。**任一侧没给出具名字段就记「无法比对」** —— 跳过 ≠ 通过。"""
    if contract is None or impl is None:
        if contract is None and impl is None:
            skipped.append(f"{method:<6} {path} · {where} · 两侧都没给出具名字段(无从比)")
        else:
            missing_side = "契约" if contract is None else "实现"
            given_side = "实现" if contract is None else "契约"
            skipped.append(f"{method:<6} {path} · {where}"
                           f" · {missing_side}侧未给出具名字段({given_side}侧有)—— 未比对")
        return
    contract_only = sorted(contract - impl)
    impl_only = sorted(impl - contract)
    if contract_only or impl_only:
        text = f"{method:<6} {path} · {where}"
        if contract_only:
            text += f" | 契约有实现无: {contract_only}"
        if impl_only:
            text += f" | 实现有契约无: {impl_only}"
        out.append(text)


def _diff(contract: dict[tuple[str, str], dict[str, Any]], impl: list[Impl]) -> Result:
    assert contract, "契约侧解析出 0 条端点 —— 空集合不许进入比较"
    assert impl, "实现侧读到 0 条路由 —— 空集合不许进入比较"

    result = Result(contract_count=len(contract), impl_count=len(impl))
    impl_by_key: dict[tuple[str, str], Impl] = {}
    for endpoint in impl:
        impl_by_key.setdefault(endpoint.key, endpoint)

    for key, op in contract.items():
        method, path = key
        twin = impl_by_key.get(key)
        if twin is None:
            gap = KNOWN_GAPS.get(key)
            if gap is None:
                result.unexpected_missing.append((method, op["path"], op["summary"]))
            else:
                result.known_missing.append((method, op["path"], gap[0], gap[1]))
            continue
        result.matched += 1
        _compare_fields(method, op["path"], op["request"], twin.request, "请求体",
                        result.field_diffs, result.skipped)
        _compare_fields(method, op["path"], op["response"], twin.response, "响应 data",
                        result.field_diffs, result.skipped)

    for endpoint in impl:
        reason = IGNORED_IMPL_PATHS.get(_normalize(endpoint.path))
        if reason is not None:
            result.ignored_impl.append((endpoint, reason))
            continue
        if endpoint.key not in contract:
            result.extra_impl.append(endpoint)

    for key, gap in KNOWN_GAPS.items():
        if key in impl_by_key:
            result.stale_gaps.append((key[0], key[1], gap[0]))

    for canary in CANARIES:
        method, _, path = canary.partition(" ")
        if (method, _normalize(path)) not in contract:
            result.broken_canaries.append(f"{canary} —— 契约侧没有这条路径")
        elif (method, _normalize(path)) not in impl_by_key:
            result.broken_canaries.append(f"{canary} —— 实现侧没读到这条路由")

    # 两侧计不上账就当场抛:不许有"凭空消失"的端点
    assert result.matched + len(result.unexpected_missing) + len(result.known_missing) \
        == result.contract_count, "契约侧计不上账"
    assert result.matched + len(result.extra_impl) + len(result.ignored_impl) \
        == result.impl_count, "实现侧计不上账"
    return result


def _render(result: Result) -> str:
    lines = [
        "# 契约 ↔ 实现比对清单(Python 侧,T-2.1)",
        "",
        "> 由 `tests/test_contract_diff.py` 自动生成,请勿手改。",
        "> 比对范围:**路径 + HTTP 方法 + 字段名**;不比类型 / 必填 / 状态码 / 幂等。",
        "> 例外:`OPENING_TYPES` / `SPACE_TYPES` 两个模块常量与契约 enum **严格相等**,"
        "由 `test_opening_and_space_types_match_contract` 钉住(§31.2 的唯一兜底)。",
        "",
        "## 页眉计数",
        "",
        "| 项 | 数 |",
        "| --- | ---: |",
        f"| 契约端点(x-direction → Python) | {result.contract_count} |",
        f"| 实现路由(app.openapi) | {result.impl_count} |",
        f"| 两边匹配上并比过 | {result.matched} |",
        f"| 契约有实现无 · **意外缺失** | {len(result.unexpected_missing)} |",
        f"| 契约有实现无 · 已挂账(已知未开工) | {len(result.known_missing)} |",
        f"| 实现有契约无 · 多余路由 | {len(result.extra_impl)} |",
        f"| 实现侧被豁免 | {len(result.ignored_impl)} |",
        f"| 字段不一致 | {len(result.field_diffs)} |",
        f"| 挂账已过期 | {len(result.stale_gaps)} |",
        f"| 锚点未命中 | {len(result.broken_canaries)} |",
        "",
        "**本骨架覆盖不到**:类型 / 必填 / 枚举值 · 状态码与业务错误码 · 幂等与状态机 ·",
        "`text/event-stream` 的 SSE 事件集(/internal/agent/chat 的价值几乎全在这里)·",
        "嵌套深度与 `Map` 型响应。",
        "",
        "## 需要人看的",
        "",
    ]
    for title, items in (
        ("锚点未命中(比对本身可能没跑对)", result.broken_canaries),
        ("契约有、实现无 —— 意外缺失(没有挂账出处)",
         [f"{m:<6} {p} · {s}" for m, p, s in result.unexpected_missing]),
        ("实现有、契约无 —— 多余路由", [e.brief for e in result.extra_impl]),
        ("字段不一致", result.field_diffs),
        ("挂账已过期", [f"{m} {p} · 出处 {src} —— 已实现,挂账该删了"
                        for m, p, src in result.stale_gaps]),
    ):
        lines.append(f"### {title}")
        lines.append("")
        lines.extend([f"- {item}" for item in items] or ["(无)"])
        lines.append("")

    lines += ["## 已知未开工(挂了账,带出处)", ""]
    if result.known_missing:
        lines += ["| 方法 | 路径 | 出处 | 说明 |", "| --- | --- | --- | --- |"]
        lines += [f"| {m} | `{p}` | {src} | {note} |"
                  for m, p, src, note in result.known_missing]
    else:
        lines.append("(无)")
    lines += ["", "## 无法比对(跳过 ≠ 通过)", ""]
    lines += [f"- {item}" for item in result.skipped] or ["(无)"]
    lines += ["", "## 实现侧被豁免", ""]
    lines += [f"- {e.brief} · {reason}" for e, reason in result.ignored_impl] or ["(无)"]
    lines.append("")
    return "\n".join(lines)


# ----------------------------------------------------------------------
# 测试
# ----------------------------------------------------------------------

def test_contract_diff_python_side():
    """出清单、落盘,并对"工具自己可信"的那两类当场判定。"""
    contract = _contract_ops()
    impl = _impl_endpoints()
    result = _diff(contract, impl)
    report = _render(result)

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(report, encoding="utf-8")
    print(report)

    assert len(contract) >= 4, "契约里 x-direction 指向 Python 的端点应至少有 4 条"
    assert not result.broken_canaries, "锚点未命中 —— 比对本身可能没跑对"
    assert not result.stale_gaps, f"挂账清单已过期,请删掉:{result.stale_gaps}"

    if os.environ.get("CONTRACT_DIFF_GATE"):
        assert not result.has_findings, f"契约与实现有分叉(明细见 {REPORT})"


def test_python_service_exposes_no_public_route():
    """Python 仅内网:除 /health 外,所有路由都必须在 /internal 下。

    这条属于架构不变量 3(Java 是唯一对外 API),比字段名更值得钉住 ——
    一个不小心加上的公开路由,不会让任何别的检查变红。
    """
    offenders = [
        endpoint.brief
        for endpoint in _impl_endpoints()
        if not endpoint.path.startswith("/internal/")
        and _normalize(endpoint.path) not in IGNORED_IMPL_PATHS
    ]
    assert not offenders, f"Python 服务出现了契约外的非 /internal 路由:{offenders}"


def test_opening_and_space_types_match_contract():
    """§31.2 的唯一兜底:模块常量必须与契约 enum **严格相等**。

    为什么不能用 OpenAPI 反射做这件事:这两个常量只以模块常量的形式活在
    `app/agent/layout_parse.py` 里,`app.openapi()` 生成的 schema 里根本没有它们
    (请求体是 `text` + `params: dict`,响应是 `dict[str, Any]`)。所以只能直接 import 常量比。

    §31.2 记的真实事故:实现侧多出 `bay_window` / `other`,契约只有 `[door, window]`,
    **当时没有任何门禁会因此变红**。这条断言就是那块补丁 —— 它不比对"包含",
    只比对**相等**:多了少了都红。
    """
    common = _read(COMMON_FILE)
    contract_opening = common["components"]["schemas"]["OpeningType"]["enum"]
    contract_space = common["components"]["schemas"]["SpaceType"]["enum"]

    assert list(layout_parse.OPENING_TYPES) == list(contract_opening), (
        "layout_parse.OPENING_TYPES 与契约 common.OpeningType.enum 不一致:"
        f"实现={list(layout_parse.OPENING_TYPES)} 契约={list(contract_opening)};"
        "多出来的值会被 Java 侧 LayoutModel 校验拒收")

    assert list(layout_parse.SPACE_TYPES) == list(contract_space), (
        "layout_parse.SPACE_TYPES 与契约 common.SpaceType.enum(内置默认值)不一致:"
        f"实现={list(layout_parse.SPACE_TYPES)} 契约={list(contract_space)}")
