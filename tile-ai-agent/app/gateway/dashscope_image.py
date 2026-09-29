"""通义万相图像适配器(**DashScope 原生协议**,非 OpenAI 兼容)。

## 为什么单独一个文件

架构 §7.2 原文写"适配器**统一走 OpenAI 兼容协议**"。**这一条对图像不成立** ——
2026-09-29 实测(4 次真实调用)确认,`wan2.7-image-pro` 走的是 DashScope 原生端点:

    错误(原先按 OpenAI 兼容推断)      正确(实测)
    POST {base}/images/generations      POST {base}/api/v1/services/aigc/multimodal-generation/generation
    {prompt, image_urls: [...]}         {input:{messages:[{role,content:[{text},{image}...]}]},
                                         parameters:{size,n}}
    data[].url                          output.choices[].message.content[].image

原 `openai_compat.py` 里那版图像适配器**从未与厂商核实过**(当时没有密钥,
已在文件头显著标注),本次实测后作废并拆出本文件。LLM 侧仍是 OpenAI 兼容,
留在原处。

## 实测记录(2026-09-29,1K)

| 场景 | 延迟 | usage |
| --- | --- | --- |
| 文生图 | 24.1s | input 29 / image_count 1 |
| **4 张参考图 + 提示词** | 10.6s | input **20050**(每张参考图约 5000 视觉 token) |
| 参考图用 **URL**(生产路径) | 10.5s | input 20023 |
| `n=2` | 27.3s | image_count 2 |

几条对设计有影响的实测事实:

1. **延迟远低于 PRD 的 ≤60s/图**(实测 10–28s),§7.3 / §13.1 的硬指标可达。
2. **参考图上限 9 张** —— §15.3 定的"底图 + 三部位切片(≥4 张)"**装得下**,
   生产路径成立。这是本次最想验证的一条假设。
3. **参考图按 URL 传即可**,不必 base64 —— 与架构"图片一律存 URL"一致。
   注意:无论 URL 还是 base64,**每张参考图都消耗约 5000 视觉 token**,
   所以参考图数量直接进成本,不能只按"每张图多少钱"算账。
4. **`n>1` 时每张图一个 `choices` 元素**,不是 content 数组里放多个 ——
   只读 `choices[0]` 会**少拿图却不报错**。解析必须遍历全部 choices。
5. **返回的是 OSS 临时 URL(24 小时过期)**,不是永久地址。
   架构要求图片存对象存储,故调用方(T-1.11)需及时转存 —— 本适配器只负责拿到 URL。
"""

from __future__ import annotations

from typing import Any, Sequence

from app.gateway.errors import GatewayConfigError, InvalidProviderResponse
from app.gateway.openai_compat import _OpenAICompatibleBase
from app.gateway.ports import GeneratedImage, Images, Usage

#: 同步端点。图像生成通常 1–2 分钟,同步调用在 1K 下实测 10–28s,
#: 故一期走同步;4K 或更长提示词如遇超时再考虑异步(需 X-DashScope-Async + 轮询)。
SYNC_PATH = "/api/v1/services/aigc/multimodal-generation/generation"

#: 参考图上限(实测文档值)。超过它服务端会拒 —— 前端/编排层据此收窄。
MAX_REFERENCE_IMAGES = 9


def build_image_payload(
    *,
    model: str,
    prompt: str,
    ref_images: Sequence[str] | None,
    size: str | None,
    n: int,
) -> dict[str, Any]:
    """构造 DashScope 原生的请求体。

    形态:`input.messages[].content[]` 里**恰好一个** `text` + 若干 `image` 对象。
    """
    refs = list(ref_images or [])
    if len(refs) > MAX_REFERENCE_IMAGES:
        # 早失败并说清楚,别让服务端返回一个含义模糊的 4xx
        raise GatewayConfigError(
            f"{model} 最多接受 {MAX_REFERENCE_IMAGES} 张参考图,当前给了 {len(refs)} 张"
        )

    content: list[dict[str, Any]] = [{"text": prompt}]
    content.extend({"image": url} for url in refs)

    # `size` 用 1K/2K/4K 档位或 "宽*高";不传则用服务端默认
    parameters: dict[str, Any] = {"n": n}
    if size:
        parameters["size"] = size

    return {
        "model": model,
        "input": {"messages": [{"role": "user", "content": content}]},
        "parameters": parameters,
    }


def parse_image_response(data: Any, *, provider: str) -> Images:
    """解析 DashScope 原生响应。

    ⚠️ **必须遍历全部 `choices`** —— `n>1` 时每张图各占一个 choice,
    只读 `choices[0]` 会静默少拿图(usage 里却按实际张数计费),
    是"付了钱没拿到东西"的那种错。
    """
    if not isinstance(data, dict):
        raise InvalidProviderResponse(f"{provider} 响应不是对象")

    output = data.get("output")
    if not isinstance(output, dict):
        raise InvalidProviderResponse(f"{provider} 响应缺少 output")

    images: list[GeneratedImage] = []
    for choice in output.get("choices") or []:
        message = (choice or {}).get("message") or {}
        for item in message.get("content") or []:
            if not isinstance(item, dict):
                continue
            url = item.get("image")
            if url:
                images.append(GeneratedImage(url=url))

    if not images:
        # 生成失败时厂商会在 output 里给 code/message;把它带出来便于排查
        detail = output.get("code") or output.get("message") or ""
        raise InvalidProviderResponse(f"{provider} 响应里没有任何图片 {detail}".strip())

    usage_raw = data.get("usage") or {}
    return Images(
        images=tuple(images),
        usage=Usage(prompt_tokens=int(usage_raw.get("input_tokens") or 0),
                    completion_tokens=int(usage_raw.get("output_tokens") or 0)),
        model=str(data.get("model") or ""),
    )


def image_count_of(data: Any) -> int:
    """usage.image_count —— 厂商按它计费,成本核对以它为准。"""
    return int(((data or {}).get("usage") or {}).get("image_count") or 0)


class DashScopeImageGen(_OpenAICompatibleBase):
    """通义万相图像生成/编辑(架构 §7.1 的 ImageGenGateway)。

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
        # `seed` 参数在万相的图像接口上没有对应字段(实测文档未列)。
        # 保留形参是为了满足 §7.1 的签名;不支持时**不发送**,而不是发一个
        # 服务端不认识的名字 —— 那会得到一个含义不明的报错。
        _ = seed

        payload = build_image_payload(
            model=self._model, prompt=prompt, ref_images=ref_images, size=size, n=n
        )
        return parse_image_response(self._post(SYNC_PATH, payload), provider=self._provider)
