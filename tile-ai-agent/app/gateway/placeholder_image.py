"""替身图像网关(`MODEL_IMAGE_PROVIDER=placeholder`,§29 阶段性收缩)。

本期唯一目标是**端到端跑通**:选砖 → 录户型 → 点生成 → 看到图。真实模型的出图
质量评估(纹理还原度、空间一致性、坏图率)已明确推到换回真模型之后,故这里返回
一张固定占位图。它是"假模型",不是"简化模型" —— **不模拟延迟、不模拟失败、
不假装看过参考图**。要测失败路径就注入一个自己会抛的假网关(编排测试正是这么做的)。

## 图为什么由 Java 提供

URL 是 `{base}/placeholder.svg?v={view}`,其中 `{base}` 默认就是 `java_base_url`
(那份 SVG 在 `tile-ai-server/src/main/resources/static/placeholder.svg`)。

理由是不变量 3:**Java 是唯一对外 API,前端只连 Java**。图若挂在 Python 上
(Python 只在内网),存进 `t_plan_image` 的就是一个前端够不到的地址 ——
本地开发能碰巧跑通,但 T-2.4 上线时会被 Nginx 拦掉,而且是一处**违反铁律**的实现。

## `?v=` 从哪来

`ImageGenGateway.generate` 的签名里**没有视角参数**(它是 §7.1 定的,不能改),
所以视角只能从提示词里读回来 —— 而提示词里确实带着它:`prompt.py` 的
`VIEW_PHRASES` 每个机位一句措辞,`assemble_prompt` 把它拼进了"视角："那一段。

这正是一个替身**模型**该做的事:读提示词,决定自己"拍到"的是哪个机位。
好处是 URL 里带的就是视角名(`?v=default` / `?v=angle1`),对得上 Java 侧的
封面规则(`view == default` 当封面),肉眼也能核。

## 张数

**恰好 `n` 张**,与 §30.3 一致。但正常路径**永远是 `n=1`**:编排出图是**逐视角循环**,
每个机位各调一次 —— 每个视角的提示词不同,一次 `n=N` 会把同一个提示词发 N 遍,
违背"同一张照片的不同机位"(D-5 / §33)。`n>1` 只在内部分支兜底,
那时同一视角的 n 张必须**地址不同**,否则 Java 存下去就是 N 条一模一样的记录。
"""

from __future__ import annotations

import hashlib
from typing import Sequence

from app.agent.prompt import VIEW_PHRASES
from app.core.config import settings
from app.gateway.ports import GeneratedImage, Images

#: 占位图的对外路径。Java 是唯一对外 API(不变量 3)。
#:
#: ⚠️ **不是** `/static/placeholder.svg`:Spring Boot 把 `classpath:/static/`
#: 映射到**根路径**,所以文件虽然在 `resources/static/` 下,对外地址没有 `static`
#: 这一段。按目录名拼 URL 会得到一个 404,而且是"图明明在那儿"的那种 404。
PLACEHOLDER_PATH = "/placeholder.svg"

#: 认不出视角时的 `?v=` 长度。取提示词摘要的前若干位 —— 不可读但**唯一且稳定**,
#: 好过让两个不同机位落到同一个地址上。
_DIGEST_LENGTH = 10


class PlaceholderImageGen:
    """固定占位图网关(架构 §7.1 的 `ImageGenGateway`)。"""

    def __init__(self, *, base_url: str | None = None) -> None:
        base = base_url if base_url else settings.placeholder_image_base_url
        # 留空即"跟 Java 同源" —— 这是默认值,也是唯一正确的部署形态
        self._base_url = (base or settings.java_base_url).rstrip("/")

    def generate(
        self,
        prompt: str,
        ref_images: Sequence[str] | None = None,
        size: str | None = None,
        n: int = 1,
        seed: int | None = None,
    ) -> Images:
        # 参考图**收下但不用** —— 替身不看照片。这一句不是装饰:
        # "照片确实交给了出图这一环"是 T-1.11 的验收项,由编排层的测试断言
        # (用一个会记录 ref_images 的假网关),而不是由替身自己证明。
        _ = (ref_images, size, seed)

        view = view_of_prompt(prompt)
        count = max(1, n)
        return Images(
            images=tuple(
                GeneratedImage(url=self._url(view, index, count)) for index in range(count)
            ),
            model="placeholder",
        )

    def _url(self, view: str, index: int, count: int) -> str:
        url = f"{self._base_url}{PLACEHOLDER_PATH}?v={view}"
        # 同一视角的多张(正常路径不会出现)必须地址不同,否则落库就是 N 条重复记录
        return url if count == 1 else f"{url}&i={index + 1}"


def view_of_prompt(prompt: str) -> str:
    """从提示词里认回视角名;认不出退化为提示词摘要。

    认不出**正常路径走不到**:`angles` 上限 4 等于 `VIEW_PHRASES` 的条目数
    (契约已封,`prompt.py` 有测试盯着这个等式)。留着兜底是纵深防御 ——
    真出现表外的 `angleN` 时,提示词里那句是 `ANGLE_FALLBACK_PHRASE`,
    与所有已知机位都不匹配,此时**必须**给一个不同的地址而不是都退成 `default`。
    """
    for view, phrase in VIEW_PHRASES.items():
        if phrase and phrase in prompt:
            return view
    return hashlib.sha256(prompt.encode("utf-8")).hexdigest()[:_DIGEST_LENGTH]
