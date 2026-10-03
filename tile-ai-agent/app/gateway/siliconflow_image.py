"""硅基流动的图像生成 / 编辑适配器(架构 §7.1 的 ImageGenGateway)。

**2026-10-02 用真实密钥实测确认过**下列事实 —— 官方文档里写的字段名是错的,
照文档写会直接 400。结论都放在这里,免得下一个人再踩一遍:

1. **参考图的字段名是 `image`,不是 `imageUpload`。**
   按文档写的 `imageUpload` 会得到
   `{"code":20015,"message":"Missing required key: image"}`。
2. **多图是并列字段** `image` / `image2` / `image3` / `image4`……
   **实测 4 张通过**(架构 §15.1 / §15.3 要的正是"底图 1 + 切片最多 3 = 4 张",
   所以不需要收窄能力)。**5 张以上没测**,本适配器**保守地卡在 4**:
   超出的部分**截断并记 WARN**,不报错 —— 出图编排层会把产品的封面 + 所有图
   都传下来(实测每款 5 张左右),**产品多几张照片是正常情况**,
   为它让整次生成失败是把可降级的小事变成了故障。
3. **返回体同时带 `images` 和 `data`**,两者都是 `[{"url": ...}]`。
   解析**优先取 `images`,回退 `data`** —— 只认一个,厂商哪天真删了另一个就会静默出空。
4. **出图 URL 是带签名的临时地址,`X-Amz-Expires=86400` = 24 小时**
   —— 与通义万相同档,转存机制(T-1.11 / §53)照常适用。
   *(注:网上有说"1 小时"的,实测是 24 小时。)*
5. **一次编辑约 28 秒**(服务端在 `timings.inference` 里自报),
   与万相的 24 秒同量级 —— §13.1 的"单图 ≤60s"仍然成立。
6. **`Qwen-Image-Edit-2509` 不接受 `image_size` 字段**(文档明确),
   所以本适配器的 `size` 形参收到也不发。
"""

from __future__ import annotations

import logging
from typing import Any, Sequence

from app.gateway.errors import GatewayConfigError, InvalidProviderResponse
from app.gateway.openai_compat import _OpenAICompatibleBase
from app.gateway.ports import GeneratedImage, Images, Usage

logger = logging.getLogger(__name__)

#: 同步端点(OpenAI images 风格的路径,但**参数是硅基流动自己的扩展**)。
SYNC_PATH = "/v1/images/generations"

#: 参考图上限。**实测 4 张通过**;5 张以上未测,保守卡 4。
#: 恰好等于架构 §15.3 生产路径所需的张数(底图 1 + 切片最多 3)。
MAX_REFERENCE_IMAGES = 4

#: 参考图字段名。第 k 张(0-based)写成 `image` / `image2` / `image3` / `image4`。
#: ⚠️ 第一张**没有数字后缀**,这是实测出来的,不是笔误。
_REF_FIELD_PREFIX = "image"


def ref_field_name(index: int) -> str:
    """第 `index` 张参考图对应的请求字段名(0-based)。"""
    return _REF_FIELD_PREFIX if index == 0 else f"{_REF_FIELD_PREFIX}{index + 1}"


def build_image_payload(
    *,
    model: str,
    prompt: str,
    ref_images: Sequence[str] | None,
    seed: int | None,
) -> dict[str, Any]:
    """构造请求体。

    `image_size` **不发** —— 该模型明确不支持,发了会被拒。
    """
    refs = list(ref_images or [])
    if len(refs) > MAX_REFERENCE_IMAGES:
        # ⚠️ **截断,不是报错。** 出图编排层会把产品的"封面 + 所有图"都传下来
        # (实测导入的产品每款 5 张左右),**产品多几张照片是正常情况** ——
        # 为这个让整次生成失败,是把一个可以降级的小事变成了故障。
        # 保留前 N 张(封面排在最前,不会丢),并记一条 WARN 让"被丢了几张"看得见。
        logger.warning(
            "%s 最多接受 %d 张参考图,收到 %d 张 —— 保留前 %d 张,丢弃 %d 张",
            model, MAX_REFERENCE_IMAGES, len(refs), MAX_REFERENCE_IMAGES,
            len(refs) - MAX_REFERENCE_IMAGES,
        )
        refs = refs[:MAX_REFERENCE_IMAGES]

    payload: dict[str, Any] = {"model": model, "prompt": prompt}
    for index, url in enumerate(refs):
        payload[ref_field_name(index)] = url
    if seed is not None:
        payload["seed"] = seed
    return payload


def parse_image_response(data: Any, *, provider: str) -> Images:
    """解析响应。

    **`images` 优先、`data` 回退** —— 实测两者同时存在且内容一致,
    但只认其中一个,厂商删掉另一个时就会静默出空图。
    """
    if not isinstance(data, dict):
        raise InvalidProviderResponse(f"{provider} 响应不是对象")

    raw_images = data.get("images")
    if not isinstance(raw_images, list):
        raw_images = data.get("data")

    urls: list[str] = []
    for item in raw_images or []:
        if isinstance(item, dict) and item.get("url"):
            urls.append(str(item["url"]))

    if not urls:
        # 失败时厂商会在 body 里给 code/message,带出来便于排查
        detail = data.get("message") or data.get("code") or ""
        raise InvalidProviderResponse(f"{provider} 响应里没有任何图片 {detail}".strip())

    return Images(
        images=tuple(GeneratedImage(url=url) for url in urls),
        # ⚠️ 该端点**不回 usage**(实测响应里只有 images/data/timings/seed)。
        # 成本核对拿不到 token 数,只能按"张数 × 单价"算 —— 这一条记在这里,
        # 免得后人以为是自己解析漏了。
        usage=Usage(prompt_tokens=0, completion_tokens=0),
        model=str(data.get("model") or ""),
    )


class SiliconFlowImageGen(_OpenAICompatibleBase):
    """硅基流动的图像生成 / 编辑(架构 §7.1 的 ImageGenGateway)。

    复用基类的超时与错误翻译,只换端点与请求/响应映射。
    """

    def generate(
        self,
        prompt: str,
        ref_images: Sequence[str] | None = None,
        size: str | None = None,
        n: int = 1,
        seed: int | None = None,
    ) -> Images:
        # `size` 在该模型上**没有对应字段**(文档明确不支持 image_size)。
        # 保留形参是为了满足 §7.1 的签名;不支持时**不发**,
        # 而不是发一个服务端不认识的名字 —— 那会得到一个含义不明的报错。
        _ = size

        if n != 1:
            # 该端点的 batch_size 只对 Kolors 生效,这里一次只出一张。
            # **不能静默只返回一张** —— 调用方以为要了 n 张,少拿了却不报错,
            # 属于"付了钱没拿到东西"的那类问题(万相那边的 choices 解析有同类注释)。
            raise GatewayConfigError(
                f"{self._model} 一次只返回一张图,不支持 n={n}；请拆成多次调用"
            )

        payload = build_image_payload(
            model=self._model, prompt=prompt, ref_images=ref_images, seed=seed
        )
        return parse_image_response(self._post(SYNC_PATH, payload), provider=self._provider)
