"""生成任务的编排(架构 §6.2、§11.2;T-1.11)。

消费端从 `queue.generate` 取到一条 `GenerateTaskPayload`,交给这里的
`run_generate_task` 跑完:取数 → 组装提示词 → 逐视角调图像网关 → 回调 Java。
它是**纯编排** —— 所有 I/O 都从参数注入(端口),所以测试不需要 MySQL、
不需要 RabbitMQ、不需要模型密钥,只要几个假对象。

## 顺序是有讲究的

```
回调 running(0)         ← 先报开工。用户点下生成后马上能看到"处理中"
逐视角:调网关 → 回调进度
回调 success(100)       ← 终局,带 imageUrls
```

**开工先回调、再调模型**(而不是反过来):`progress` 才有意义 —— 真模型下
最坏 4 张 × 60s = 240 秒,2 秒轮询要看见进度在走,而不是一个两分钟不动然后
突然跳满的条。附带好处是每次进度回调都会刷新 `t_generation_task.updated_at`
(MySQL `ON UPDATE`),**活着的任务**因此不会被 Java 的补偿扫描误判成卡住
—— 这是那边敢把阈值定成 360 秒的前提(§35.1)。

**进度只报"已完成几张"**:`⌊100 × 已完成 / 总数⌋`。不报"正在生成第 k 张" ——
契约里的 `progress` 是整数百分比,没有"第几张"的位置,加了就是自造字段。

## 失败怎么处理

任何异常都**先回调 `failed`(40001)再原样抛出**。抛出去是为了让消费端决定
重投还是进 DLQ;而回调是必要的 —— 不回调的话用户只能一直轮询,直到 Java 的
补偿扫描(最长 30 分钟)接管才知道失败。用户看到的是一个转不完的圈。

## 本期的一张图当几张用

§15.3 定的参考图是"同空间底图 + 各部位瓷砖切片",但**没有任何表存底图**(§35.7),
所以本期参考图只有各部位的瓷砖照片(1~3 张)。替身不看参考图,故本期看不出差别;
换回真模型时要补底图,那时才谈得上"空间结构一致"。
"""

from __future__ import annotations

import logging
import math
from dataclasses import dataclass
from typing import Any, Mapping, Protocol, Sequence

from app.agent.prompt import (
    DEFAULT_VIEW,
    FurnishSource,
    SkuSource,
    prepare_prompts,
)
from app.gateway.errors import (
    CircuitOpen,
    GatewayConfigError,
    GatewayTimeout,
    ProviderError,
    RateLimited,
)
from app.gateway.ports import ImageGenGateway

logger = logging.getLogger(__name__)

#: 契约 `common.ErrorCode` 的 40001 —— 生成失败。
#: Java 侧**原样记录**这个码,不翻译(§10.4 约定 1 管的是 Java *返回* 的码)。
CODE_GENERATE_FAILED = 40001
#: 契约 `common.ErrorCode` 的 40003 —— 任务不存在。回调拿到它意味着"别重试了"。
CODE_TASK_NOT_FOUND = 40003

#: 失败原因写进回调时的截断长度(Java 那一列是 1000)。
_ERROR_MESSAGE_LIMIT = 300


class CallbackPort(Protocol):
    """把一次回传交给 Java 的那条通路。

    返回 `CallbackResult`(`app/clients/java_api.py`)—— 需要知道**为什么**没收下:
    `40003` 表示任务已经不在 Java 那边了,继续出图纯属白烧模型调用。
    """

    def send_callback(self, body: Mapping[str, Any], *, trace_id: str = "") -> Any:
        ...


class TaskGoneError(RuntimeError):
    """Java 那边已经没有这个任务了(回调返回 40003)。

    **不该重试**:任务记录都不在了,再出一次图也无人认领。消费端收到它时应当
    直接 ACK 丢弃,而不是重投 —— 否则会拿一条注定失败的消息把队列堵满。
    """


@dataclass(frozen=True)
class GeneratedRef:
    """一张出图 —— 契约 `generate/callback` 的 `imageUrls[]` 项。"""

    url: str
    view: str
    #: 封面。D-5 定的基准机位是 `default`(唯一),故它当封面是确定的。
    is_cover: bool


@dataclass(frozen=True)
class GenerateOutcome:
    """一轮编排的结果。**只用于测试与日志** —— 真正的结果早已回调给 Java。"""

    task_id: str
    images: tuple[GeneratedRef, ...]
    #: **第 1 张图**用的参考图(= 所选瓷砖的照片 URL,去重后保序)。
    #: 多角度且启用基准图时,第 2 张起改用首张产出图,不在此列(§64)。
    ref_images: tuple[str, ...]
    missing_skus: tuple[str, ...]
    furnish_configured: bool


def run_generate_task(
    payload: Mapping[str, Any],
    *,
    sku_source: SkuSource,
    image_gateway: ImageGenGateway,
    callback: CallbackPort,
    furnish_source: FurnishSource | None = None,
    anchor_first_view: bool = True,
) -> GenerateOutcome:
    """跑完一条生成任务。

    `payload` 是契约 `GenerateTaskPayload` 的形状(Java 发来的原样 JSON)。

    `anchor_first_view`(2026-10-03,§64):多角度时**把第一张的产出图当作后面几张的
    参考图**。默认开(与 `settings.image_anchor_first_view` 一致),关掉即回退到
    "每次都发瓷砖切片、各画各的"。

    :raises TaskGoneError: Java 已无此任务(40003),不该重试
    :raises Exception: 其余失败,**已先回调 failed 再抛出**
    """
    task_id = str(payload.get("taskId") or "")
    trace_id = str(payload.get("traceId") or "")
    space = str(payload.get("space") or "")
    # `style` 在契约里是可选的(Java 的 GenerateRequest),缺省时用空串 ——
    # 提示词里的风格段会退化成键本身,而不是让整条链断掉(§35.4)。
    style = str(payload.get("style") or "")
    angles = int(payload.get("angles") or 1)
    parts = list(payload.get("parts") or [])

    if not task_id:
        # 没有 taskId 就无处回调、无法幂等,这条消息是坏的 —— 直接抛给消费端走重试/DLQ
        raise ValueError("生成任务缺少 taskId,无法处理")

    try:
        # ⚠️ 参考图必须先算:提示词里要写明"第几张参考图对应哪个部位",
        # 而那个编号就是**这些图在请求里的顺序**(2026-10-03,见 §61)。
        ref_images = _ref_images_of(parts, sku_source)
        prepared = prepare_prompts(
            space=space,
            style=style,
            selections=parts,
            sku_source=sku_source,
            furnish_source=furnish_source,
            angles=angles,
            ref_index=_ref_index_of(parts, sku_source, ref_images),
            room=_room_of(payload, space),
            anchor_first_view=anchor_first_view,
        )
        if not ref_images:
            # 不是错误:没有参考图也能出图(退化成文生图),只是"贴近所选产品"
            # 这条约束失去了依据。§15.3 的底图本期还没有承载表(§35.7),
            # 所以"一张参考图都没有"在生产上是个**要能被看见**的信号。
            logger.warning("本次生成没有任何参考图(taskId=%s):出图将只依据文字提示词", task_id)

        _notify(callback, task_id, trace_id, _running(0))

        images: list[GeneratedRef] = []
        total = len(prepared.views)
        #: 基准图 —— 第 1 张画出来之后才有值,第 2 个视角起改用它当参考图(§64)。
        #: 单张(angles=1)时全程为空,行为与改动前完全一致。
        anchor_images: list[str] = []
        if anchor_first_view and total > 1:
            logger.info(
                "多角度出图启用基准图:第 2 张起以首张产出图为参考图"
                "(taskId=%s, views=%d, 瓷砖切片=%d 张)",
                task_id, total, len(ref_images),
            )
        for index, view in enumerate(prepared.views):
            # 有基准图就只发基准图 —— 它已自带砖的位置,再塞切片既没用又会顶满 4 张配额。
            # 没有(第 1 张 / 关掉开关)就照旧发瓷砖切片。
            refs = list(anchor_images) if anchor_images else list(ref_images)
            generated = image_gateway.generate(
                prompt=view.prompt, ref_images=refs, n=1
            )
            if not generated.images:
                # 网关返回 0 张图 = 这次生成没产出。当成失败 —— 继续往下走的话
                # 会送出一个"success 但没有图"的回调,Java 侧只能拒掉(10001),
                # 用户看到的是一次莫名其妙的失败。
                raise RuntimeError(f"图像网关未返回任何图片(view={view.view})")

            url = generated.images[0].url or ""
            if not url:
                # 契约 `imageUrls[].url` 必填。base64 形态本期不支持
                # (§29:图片一律存 URL,库里只存 URL),故没有 URL 就是失败。
                raise RuntimeError(f"图像网关返回的图片没有 URL(view={view.view})")

            images.append(GeneratedRef(
                url=url, view=view.view, is_cover=view.view == DEFAULT_VIEW,
            ))
            if anchor_first_view and not anchor_images:
                # 第 1 张产出图即后面所有视角的基准图。**它已经过上面两道校验**
                # (有图、有 URL),所以基准图一定是个可用地址 —— 不需要再加判断。
                # ⚠️ 这一行让"四张必须同源"成立:第 2 张拿不到基准图的情形**不存在**,
                # 因为第 1 张失败时上面已经 raise,整单就此失败(用户 2026-10-03 拍板)。
                anchor_images = [url]

            done = index + 1
            if done < total:
                _notify(callback, task_id, trace_id, _running(_percent(done, total)))
            # 最后一张不单独报 running(100) —— 紧接着的 success 已经带了 100,
            # 再报一次只是让 Java 多写一行同样的数据。

        _notify(callback, task_id, trace_id, _succeeded(task_id, images, trace_id))
    except TaskGoneError:
        # 任务已经不在 Java 那边,再回调一次 failed 也没有接收方 —— 原样抛给消费端,
        # 由它识别成本任务的**终局**(不重投)。
        raise
    except Exception as error:
        _notify_failure(callback, task_id, trace_id, error)
        raise

    logger.info(
        "生成任务完成 taskId=%s images=%d refImages=%d missingSkus=%d furnishConfigured=%s",
        task_id, len(images), len(ref_images), len(prepared.missing_skus),
        prepared.furnish_configured,
    )
    return GenerateOutcome(
        task_id=task_id,
        images=tuple(images),
        ref_images=ref_images,
        missing_skus=prepared.missing_skus,
        furnish_configured=prepared.furnish_configured,
    )


# ----------------------------------------------------------------------
# 参考图
# ----------------------------------------------------------------------

def ref_images_of(parts: Sequence[Mapping[str, Any]], sku_source: SkuSource) -> tuple[str, ...]:
    """部位选定 → 参考图 URL 列表(**每个部位一张**,去重、保序)。

    公开是因为它是"照片确实交给了出图"这条验收的落点 —— 测试直接断言它。
    """
    return _ref_images_of(parts, sku_source)


def representative_photo_of(sku_data: Mapping[str, Any]) -> str:
    """该 SKU 的**一张代表图**:封面优先,没有封面才退回相册第一张。

    "一张"是刻意的 —— 见 `_ref_images_of` 的说明。
    """
    cover = str(sku_data.get("coverImage") or "").strip()
    if cover:
        return cover
    photos = _photo_urls_of(sku_data)
    return photos[0] if photos else ""


def ref_index_of(parts: Sequence[Mapping[str, Any]], sku_source: SkuSource,
                 refs: Sequence[str]) -> dict[str, int]:
    """部位 → 该部位的砖在参考图里排**第几张**(1-based)。

    它决定提示词里那句"使用参考图 N 的瓷砖"写的是几 —— 也就是**部位与图片的对应关系**。
    公开是给测试断言用的(同 `ref_images_of` 的理由)。

    ⚠️ 同一块砖铺在多个部位时**共享同一个编号**:参考图按 URL 去重
    (见 `_ref_images_of`),所以"地面与墙面是同一款砖"在提示词里表现为两行同一个 N
    —— 这正是我们想让模型知道的事。
    """
    index: dict[str, int] = {}
    for selection in parts:
        part = str(selection.get("part") or "").strip()
        sku = str(selection.get("sku") or "").strip()
        if not part or not sku or part in index:
            continue
        data = sku_source.get(sku)
        if data is None:
            continue
        url = representative_photo_of(data)
        if url and url in refs:
            index[part] = list(refs).index(url) + 1
    return index


def _ref_index_of(parts: Sequence[Mapping[str, Any]], sku_source: SkuSource,
                  refs: Sequence[str]) -> dict[str, int]:
    """`ref_index_of` 的内部别名 —— 与 `_ref_images_of` 保持同一套命名习惯。"""
    return ref_index_of(parts, sku_source, refs)


def _room_of(payload: Mapping[str, Any], space: str) -> Mapping[str, Any] | None:
    """从消息体的户型快照里挑出**本次要拍的那个空间**。

    优先取 `type` 等于本次 `space` 的那间;挑不到就退回第一间(户型只有一间时
    两者本来一样)。都没有就返回 `None` —— 提示词随之**不写尺寸**,不编数字。
    """
    layout = payload.get("layout")
    spaces = layout.get("spaces") if isinstance(layout, Mapping) else None
    if not isinstance(spaces, list) or not spaces:
        return None
    for candidate in spaces:
        if isinstance(candidate, Mapping) and str(candidate.get("type") or "") == space:
            return candidate
    first = spaces[0]
    return first if isinstance(first, Mapping) else None


def _ref_images_of(parts: Sequence[Mapping[str, Any]], sku_source: SkuSource) -> tuple[str, ...]:
    """每个部位**一张**代表图(封面优先),同一块砖只出现一次。

    <h2>⚠️ 为什么是"每部位一张",而不是把这个产品的相册都发过去</h2>
    <p>**这里改过一次,原因值得留着。** 原先是把 `coverImage` + `images[].url` **全部**
    当参考图发出去,于是:导入的真实产品每款有 5–6 张照片 ⇒ 选**一块砖**就发 6 张,
    选两块就发 **12 张**(实测日志逐字:`收到 12 张 —— 保留前 4 张,丢弃 8 张`)。
    后果有两个:

    <p>① **超时出不了图**。参考图越多、模型越慢。实测单张 28 秒,而 4 张就超过了
    `gateway_image_timeout_seconds`(默认 60 秒)⇒ 读超时 ⇒ 重试 ⇒ 熔断 ⇒
    任务判失败。**用户看到的是"选了砖却怎么也生成不出来"。**

    <p>② **偏离架构设计**。§15.3 定的形态是"同空间类型**底图 1 张** +
    **各部位瓷砖切片**最多 3 张 = 最多 4 张",即**每个部位一张切片** ——
    不是"每个部位把它整个相册发过去"。

    <p>⇒ 改成"每部位一张"之后:选 1 块砖发 1 张(28 秒,出得来图)、
    选 3 个部位发 3 张,加上底图正好是架构说的 4 张上限。

    <p>顺带省钱:每张参考图约 5000 视觉 token(见 `dashscope_image.py`),
    发 12 张等于白烧 6 万 token。
    """
    urls: list[str] = []
    seen: set[str] = set()
    for selection in parts:
        sku = str(selection.get("sku") or "").strip()
        if not sku:
            continue
        data = sku_source.get(sku)
        if data is None:
            continue
        # 同一块砖可能同时铺地面与墙面(`prompt.py` 的 `_map_parts` 已按部位展开),
        # 但它**只该出现一次** —— 重复的参考图不会让模型更"看见"它,只是白花一次视觉 token。
        url = representative_photo_of(data)
        if url and url not in seen:
            seen.add(url)
            urls.append(url)
    return tuple(urls)


def _photo_urls_of(sku_data: Mapping[str, Any]) -> list[str]:
    urls: list[str] = []
    cover = str(sku_data.get("coverImage") or "").strip()
    if cover:
        urls.append(cover)
    for image in sku_data.get("images") or []:
        if isinstance(image, Mapping):
            url = str(image.get("url") or "").strip()
        else:
            # 容忍"字符串数组"这种形状:`Sku.images` 契约上是对象数组,
            # 但照片列表在别处出现过纯字符串形态,多认一种不会出错。
            url = str(image or "").strip()
        if url:
            urls.append(url)
    return urls


# ----------------------------------------------------------------------
# 回调体
# ----------------------------------------------------------------------

def _running(progress: int) -> dict[str, Any]:
    return {"status": "running", "progress": progress}


def _succeeded(task_id: str, images: Sequence[GeneratedRef], trace_id: str) -> dict[str, Any]:
    return {
        "taskId": task_id,
        "status": "success",
        "progress": 100,
        "imageUrls": [
            {"url": image.url, "view": image.view, "isCover": image.is_cover}
            for image in images
        ],
        "traceId": trace_id,
    }


#: 出图失败时给**用户看**的话。
#:
#: 为什么需要这一层:契约里 `status=failed` 的 `error.message` 是**展示文案** ——
#: Java 侧 `GenerateService.failureMessage` 的注释把这一点写得很死("它是我们自己
#: 的 Python 编排器写的展示文案,**不是第三方 SDK 的原始异常**"),而这里原先直接
#: 回传 `f"{type(error).__name__}: {error}"`。**实现与那句注释不符**,用户看到的是
#: `ProviderError: HTTP 400 ...` 这种只有开发者看得懂的东西(2026-10-02 修)。
_USER_FACING: dict[type[BaseException], str] = {
    RateLimited: "AI 出图服务繁忙(请求过于集中),请稍后重试",
    CircuitOpen: "AI 出图服务当前不可用,请稍后重试",
    GatewayTimeout: "AI 出图服务响应超时,请稍后重试",
    GatewayConfigError: "出图服务尚未配置好,请联系店员处理",
}

#: 百炼"额度不足 / 欠费"走的是 400(错误码 Arrearage、QuotaExceeded 之类)。
#: 给用户的话**不点得太死** —— 参数错也是 400,具体原因在日志里。
_PROVIDER_400_MESSAGE = "AI 出图服务调用被拒(账号额度可能不足),请联系店员处理"


def _user_facing_message(error: BaseException) -> str:
    """把网关异常翻译成用户能看懂的一句话。

    只翻译**用户能理解、且可能据此行动**的几类;其余**保留技术串** ——
    宁可难看,也不要把排查线索抹掉(诊断细节另有日志)。
    """
    for kind, text in _USER_FACING.items():
        if isinstance(error, kind):
            return text

    if isinstance(error, ProviderError):
        status = error.status_code
        if status in (401, 403):
            return "出图服务的访问凭据无效,请联系店员处理"
        if status == 429:
            return "AI 出图服务繁忙(请求过于集中),请稍后重试"
        if status is not None and status >= 500:
            return "AI 出图服务暂时故障,请稍后重试"
        if status == 400:
            return _PROVIDER_400_MESSAGE

    return f"{type(error).__name__}: {error}"


def _failed(task_id: str, error: BaseException, trace_id: str) -> dict[str, Any]:
    return {
        "taskId": task_id,
        "status": "failed",
        "error": {
            "code": CODE_GENERATE_FAILED,
            "message": _user_facing_message(error)[:_ERROR_MESSAGE_LIMIT],
        },
        "traceId": trace_id,
    }


def _percent(done: int, total: int) -> int:
    """`⌊100 × done / total⌋`。用整除而不是 `round` —— 进度只能往前往上,
    四舍五入会让"3/4"算成 75 而"4/4"那段提前显示 100。"""
    return math.floor(100 * done / max(1, total))


def _notify(callback: CallbackPort, task_id: str, trace_id: str, body: dict[str, Any]) -> None:
    """回调一次,并把 `taskId` / `traceId` 补齐。

    两个字段由这里统一补,免得漏掉某一处:回调没有 `taskId`,Java 直接无法处理;
    没有 `traceId`,出了图就找不回是哪次请求(§13.7)。

    **回调自身抛异常只记日志,不影响生成**(`app/clients/java_api.py` 的实现本来
    就不抛,这里是纵深防御)。唯一能让生成停下的回调结果是 40003 —— 见
    `_raise_if_task_gone`。
    """
    body.setdefault("taskId", task_id)
    body.setdefault("traceId", trace_id)
    try:
        result = callback.send_callback(body, trace_id=trace_id)
    except Exception:  # noqa: BLE001
        logger.exception("回调抛异常(继续生成)taskId=%s status=%s", task_id, body.get("status"))
        return
    _raise_if_task_gone(task_id, result)


def _notify_failure(
    callback: CallbackPort, task_id: str, trace_id: str, error: BaseException
) -> None:
    """回调 failed。**这一步自己出错也不能盖掉原始异常** —— 否则日志里留下的是
    "回调失败",而真正的原因(网关超时/限流)被吞了。"""
    try:
        callback.send_callback(_failed(task_id, error, trace_id), trace_id=trace_id)
    except Exception:  # noqa: BLE001
        logger.exception("回调 failed 时又出错 taskId=%s(原始异常将照常抛出)", task_id)


def _raise_if_task_gone(task_id: str, result: Any) -> None:
    """回调返回 40003 ⇒ 任务已经不在 Java 那边,别再往下烧模型调用了。

    其余失败(超时、连不上、500)只记日志:图已经出来了,进度回调丢一两条
    不影响终局 —— 补偿扫描会兜住真正卡住的任务。**为一次网络抖动放弃一次
    已经付费的生成**才是更坏的选择。
    """
    if getattr(result, "accepted", False):
        return
    code = getattr(result, "code", 0)
    if code == CODE_TASK_NOT_FOUND:
        raise TaskGoneError(f"Java 已无此任务:{task_id}")
    logger.warning(
        "回调未被受理(将继续生成)taskId=%s code=%s message=%s",
        task_id, code, getattr(result, "message", ""),
    )
