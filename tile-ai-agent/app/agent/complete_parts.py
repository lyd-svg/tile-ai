"""未选部位的 AI 补齐(T-1.9;PRD §5.2.4、架构 §11.2)。

用户**不必选全**部位:未选的**地面/墙面**在发起生成时由 AI 补上,**局部(腰线/花砖)
是可选的、不强制补齐**。契约端点:`POST /internal/agent/complete-parts`(Java → Python),
由 Java 在**受理生成时、落任务与投递队列之前**同步调用(架构 §11.2 时序)。

## 三条不可破的口径

1. **只补 `floor` / `wall`,`local` 永不补** —— `PARTS_TO_COMPLETE` 这个常量**就是边界**,
   不给调用方传参的余地(传了就能绕过产品规则)。
2. **只在售、只从候选里选** —— 候选来自 Qdrant 检索,过滤里由
   `retrieval.build_filter` **强制注入在售**(绕不过去);模型用的是 function calling,
   **每个部位的 `enum` 就是候选 SKU 列表**,选完还要**回问 Java 权威接口**再确认一次。
   enum 只是"应该挡住",不是保证 —— 校验必须在模型之外(与 `validate_layout` 同一哲学)。
3. **补不上就降级,不阻断生成** —— 这是 2026-10-02 拍板的:**用户不该因为"库里没有
   合适的砖"而被卡住**(PRD 的本意就是"不强制选全")。所以本层对**内容层面**的失败
   (没候选 / 模型没选 / 选的不在候选里 / 权威校验不过)**一律降级**:那些部位不补,
   照常返回其余部位。**只有真·服务故障**(网关/Qdrant/Java 抛异常)才往上抛,
   由端点折 90001 —— 它会被 Java 记成 ERROR 并同样降级,但**日志里看得见**。

   ⇒ **本端点不会返回 40004**。契约里声明了这个码、前端也备好了文案,但"降级"这个
   选择让它在本策略下不会出现(登记于 `openapi-verification.md` §49)。
"""

from __future__ import annotations

import json
import logging
from typing import Any, Sequence

from app.chat.rag import retrieve
from app.gateway.ports import Message, ToolDef
from app.index.retrieval import SearchFilter

logger = logging.getLogger(__name__)

#: 会自动补齐的部位。**`local` 不在这里,且不该被加进来** —— PRD §5.2.4。
PARTS_TO_COMPLETE: tuple[str, ...] = ("floor", "wall")

SOURCE_USER = "user"
SOURCE_AI = "ai"

#: 召回条数。比 AI 咨询的 6 条宽一些,因为这里按空间一筛之后候选往往本来就不多。
COMPLETE_TOP_K = 8

_PART_LABELS = {"floor": "地面", "wall": "墙面"}

_SYSTEM_PROMPT = (
    "你是瓷砖搭配助手。用户装修某个空间时只选了部分部位的砖,"
    "请为**缺的部位**各挑一块,调用 submit_completion 提交。\n"
    "规则:\n"
    "1. 每个部位只能从它给出的候选编号里选一个,**不要编造编号**。\n"
    "2. 地面与墙面通常选不同的砖(墙砖一般更浅、更亮),但候选有限时也可以相同。\n"
    "3. 只挑候选里确实合适的;**宁可少挑,也不要硬凑**。\n"
)


def _as_user(parts: Sequence[dict[str, Any]]) -> list[dict[str, Any]]:
    """用户选的部位统一标 `source=user`(不采信外部传来的 source)。"""
    return [{**dict(part), "source": SOURCE_USER} for part in parts]


def complete_parts(
    *,
    space: str,
    style: str = "",
    selected: Sequence[dict[str, Any]],
    llm: Any,
    embedding: Any,
    qdrant: Any,
    java_api: Any,
    trace_id: str = "",
) -> list[dict[str, Any]]:
    """补齐未选的 floor / wall,返回**合并后**的部位列表(用户项 + 补齐项)。

    补不上就降级:返回里不会有那些部位的 `source=ai` 项,但用户已选的一个都不少。
    """
    missing = [part for part in PARTS_TO_COMPLETE
               if part not in {str(item.get("part") or "") for item in selected}]
    if not missing:
        return _as_user(selected)

    candidates = _candidates(space, style, embedding=embedding, qdrant=qdrant)
    if not candidates:
        # 库里没有这个空间可用的在售砖 —— 数据缺口,不是用户的错,更不该卡住他
        logger.warning("补齐降级:空间 %s 找不到任何在售候选,本次不补齐", space)
        return _as_user(selected)

    picked = _pick(llm, space=space, style=style, missing=missing, candidates=candidates)

    completed: list[dict[str, Any]] = []
    for part in missing:
        sku = picked.get(part)
        if not sku:
            continue
        if not _verify(java_api, sku, trace_id):
            continue
        completed.append({"part": part, "sku": sku, "source": SOURCE_AI})

    if not completed:
        logger.warning("补齐降级:空间 %s 的 %s 都没能补上", space, "/".join(missing))

    # 顺序:用户选的保持原样在前,补齐项按部位顺序在后
    return _as_user(selected) + completed


def _candidates(space: str, style: str, *, embedding: Any, qdrant: Any) -> list[dict[str, Any]]:
    """按空间/风格召回候选(**在售由 `build_filter` 强制**,这里绕不过去)。

    ⚠️ **两个部位的候选池是同一个**:产品索引的 payload 里**没有**"这块砖适合当地砖
    还是墙砖"的字段(§9.2 的 Payload 列),所以分不出"地砖候选 / 墙砖候选"。
    挑选由模型按部位语义决定(所以提示词里要它"墙砖一般更浅")。
    """
    query = " ".join(part for part in (space, style) if part) or "瓷砖"
    items = retrieve(
        query,
        embedding=embedding,
        qdrant=qdrant,
        top_k=COMPLETE_TOP_K,
        criteria=SearchFilter(space=space or None, style=style or None),
    )

    candidates: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in items:
        payload = item.payload or {}
        sku = str(payload.get("sku_code") or "").strip()
        if not sku or sku in seen:
            continue
        seen.add(sku)
        candidates.append({key: payload[key] for key in ("sku_code", "name", "material", "surface")
                           if payload.get(key)})
    return candidates


def _pick(
    llm: Any, *, space: str, style: str, missing: list[str], candidates: list[dict[str, Any]]
) -> dict[str, str]:
    """让模型在候选里挑。挑不出/挑得不合法就返回空(由调用方降级)。

    **模型抛异常不在这里兜** —— 那是服务故障,要让端点折 90001(日志里看得见)。
    """
    skus = [str(item["sku_code"]) for item in candidates]
    tool = _tool_for(missing, skus)

    plan = llm.chat_with_tools(_messages(space, style, missing, candidates), [tool])
    if not plan.tool_calls:
        logger.warning("补齐降级:模型没有提交任何选择(空间=%s)", space)
        return {}

    try:
        payload = json.loads(plan.tool_calls[0].arguments or "{}")
    except ValueError:
        logger.warning("补齐降级:模型提交的参数不是合法 JSON")
        return {}
    if not isinstance(payload, dict):
        return {}

    picked: dict[str, str] = {}
    for part in missing:
        sku = str(payload.get(part) or "").strip()
        if sku in skus:
            picked[part] = sku
        else:
            # enum 已经限制了取值范围,走到这里说明供应商没守 schema。
            # **不能"尽力而为"地放行** —— 放行就等于允许编造 SKU。
            logger.warning("补齐降级:模型给的 %s 不在候选里(%s)", part, sku or "空")
    return picked


def _verify(java_api: Any, sku: str, trace_id: str) -> bool:
    """回问 Java 权威接口确认这个 SKU **真的存在且在售**。

    挡的是"索引里还在售、权威库已经下架"的时间窗 —— Qdrant 是派生索引,不保证实时。
    """
    product = java_api.get_product(sku, trace_id=trace_id)
    if not isinstance(product, dict):
        logger.warning("补齐降级:候选 %s 过不了权威校验", sku)
        return False
    return True


def _tool_for(missing: list[str], skus: list[str]) -> ToolDef:
    """动态工具 schema:每个缺失部位一个 `enum`,取值范围就是候选编号。

    这是"不编造 SKU"的第一道闸 —— 模型**只能**从候选里选。
    """
    return ToolDef(
        name="submit_completion",
        description="为缺的部位各挑一块砖。只能从候选编号里选。",
        parameters={
            "type": "object",
            "required": list(missing),
            "properties": {
                part: {
                    "type": "string",
                    "enum": list(skus),
                    "description": f"为{_PART_LABELS.get(part, part)}选一个砖的编号",
                }
                for part in missing
            },
        },
    )


def _messages(
    space: str, style: str, missing: list[str], candidates: list[dict[str, Any]]
) -> list[Message]:
    labels = "、".join(_PART_LABELS.get(part, part) for part in missing)
    lines = [
        f"空间:{space or '未说明'}",
        f"风格:{style or '不限'}",
        f"需要补齐的部位:{labels}",
        "",
        "可选砖(编号 · 名称 · 材质 · 表面):",
    ]
    for item in candidates:
        fields = " · ".join(str(item.get(key) or "") for key in ("sku_code", "name", "material", "surface"))
        lines.append(f"- {fields}")
    return [
        Message(role="system", content=_SYSTEM_PROMPT),
        Message(role="user", content="\n".join(lines)),
    ]
