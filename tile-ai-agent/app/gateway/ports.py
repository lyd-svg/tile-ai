"""三类模型网关的抽象接口与数据类(架构 §7.1 / §7.2)。

架构 §7.1 原文给出的签名::

    LLMGateway
      ├─ chat(messages, tools?, stream?) -> Reply            # 对话/编排
      └─ chatWithTools(messages, toolDefs) -> ToolCallPlan   # 工具调用
    ImageGenGateway
      └─ generate(prompt, refImages?, size, n, seed?) -> Images  # 文生图/图生图
    EmbeddingGateway
      └─ embed(texts) -> vectors                             # 用于 Qdrant 检索

**两处对文档的刻意落法,记录在此以免被当成抄漏:**

1. 方法名用 **snake_case**(`chat_with_tools`),文档里的 `chatWithTools` /
   `refImages` / `toolDefs` 是跨语言伪码的 camelCase 记号。`chat` / `generate` /
   `embed` 三个词本身就与文档逐字一致。
2. `size` 与 `n` 文档未标 `?`,这里仍给了默认值 —— 默认值是**更宽松**的约束,
   不违反签名;调用方(T-1.10 / T-1.11)多数场景会省略它们。

网关层是**服务内部层,不是 HTTP 端点**:`/internal/*` 暴露的是智能体能力
(chat / parse-layout / …)而非模型能力。做成端点会凭空多一层对外面,
且违反不变量 3(Python 仅内网)。

依赖注入沿用本项目既有风格:`typing.Protocol` + 显式构造注入
(对照 `app/index/embedding.py` 的 `EmbeddingPort` 与 `IndexSyncConsumer`)。
**不引入 DI 容器** —— 本项目没有,也不需要一个。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Iterator, Protocol, Sequence


# ----------------------------------------------------------------------
# 数据类
# ----------------------------------------------------------------------

@dataclass(frozen=True)
class Usage:
    """一次调用的用量。成本计量(§7.2)与可观测的输入。"""

    prompt_tokens: int = 0
    completion_tokens: int = 0

    @property
    def total_tokens(self) -> int:
        return self.prompt_tokens + self.completion_tokens

    def __add__(self, other: "Usage") -> "Usage":
        return Usage(
            prompt_tokens=self.prompt_tokens + other.prompt_tokens,
            completion_tokens=self.completion_tokens + other.completion_tokens,
        )


@dataclass(frozen=True)
class Message:
    """一条对话消息。`role` 取 system / user / assistant / tool。"""

    role: str
    content: str
    #: role=tool 时回填,与发起该调用的 ToolCall.id 对应
    tool_call_id: str | None = None
    #: role=assistant 且带工具调用时,供应商返回的原始 tool_calls
    tool_calls: tuple["ToolCall", ...] = ()


@dataclass(frozen=True)
class ToolDef:
    """工具定义。`parameters` 是 JSON Schema 对象。"""

    name: str
    description: str
    parameters: dict[str, Any] = field(default_factory=dict)

    def to_openai(self) -> dict[str, Any]:
        """转成 OpenAI 兼容协议的 function 定义。"""
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            },
        }


@dataclass(frozen=True)
class ToolCall:
    """模型发起的一次工具调用。"""

    id: str
    name: str
    #: 供应商原样返回的 JSON 串。**保留原文不预先解析** ——
    #: 解析失败的责任在调用方(它才知道该工具的 schema),网关不该替它决定。
    arguments: str = ""


@dataclass(frozen=True)
class Reply:
    """`chat` 的返回值。"""

    content: str = ""
    tool_calls: tuple[ToolCall, ...] = ()
    usage: Usage = field(default_factory=Usage)
    model: str = ""


@dataclass(frozen=True)
class ToolCallPlan:
    """`chatWithTools` 的返回值。

    工具调用**可能为空**(模型认为不需要调工具),这不是错误 ——
    是否重试/改问法是调用方的策略。
    """

    tool_calls: tuple[ToolCall, ...] = ()
    usage: Usage = field(default_factory=Usage)
    model: str = ""


@dataclass(frozen=True)
class ReplyChunk:
    """流式响应的一帧(`stream=True` 时逐帧产出)。

    `usage` 只在最后一帧有值 —— OpenAI 兼容协议默认如此,
    故计费要认最后一帧,不能按帧累加(会重复计数)。
    """

    delta: str = ""
    tool_calls: tuple[ToolCall, ...] = ()
    usage: Usage | None = None
    model: str = ""
    done: bool = False


@dataclass(frozen=True)
class GeneratedImage:
    """一张生成图。URL 与 base64 二者其一。"""

    url: str | None = None
    b64: str | None = None

    def __post_init__(self) -> None:
        if (self.url is None) == (self.b64 is None):
            raise ValueError("生成图必须且只能有 url 或 b64 之一")


@dataclass(frozen=True)
class Images:
    """`generate` 的返回值。"""

    images: tuple[GeneratedImage, ...] = ()
    usage: Usage = field(default_factory=Usage)
    model: str = ""


# ----------------------------------------------------------------------
# 抽象接口
# ----------------------------------------------------------------------

class LLMGateway(Protocol):
    """对话/编排网关(架构 §7.1)。"""

    def chat(
        self,
        messages: Sequence[Message],
        tools: Sequence[ToolDef] | None = None,
        stream: bool = False,
    ) -> Reply | Iterator[ReplyChunk]:
        """对话。

        `stream=False` 返回 `Reply`;`stream=True` 返回 `ReplyChunk` 的迭代器
        —— 两种返回形态由调用方自己选的参数决定,不需要额外的方法名去区分。
        """
        ...

    def chat_with_tools(
        self,
        messages: Sequence[Message],
        tool_defs: Sequence[ToolDef],
    ) -> ToolCallPlan:
        """要求模型做工具调用决策。"""
        ...


class ImageGenGateway(Protocol):
    """图像生成网关(架构 §7.1)。"""

    def generate(
        self,
        prompt: str,
        ref_images: Sequence[str] | None = None,
        size: str | None = None,
        n: int = 1,
        seed: int | None = None,
    ) -> Images:
        """文生图 / 图生图。

        `ref_images` 是**参考图 URL 列表**,不是二进制 —— 架构约定图片一律存
        对象存储、库里只存 URL。

        ⚠️ **生产路径靠它**:架构 §15.3 定的形态是"同空间类型底图 + 各部位瓷砖
        切片",按一个空间最多 floor/wall/local 三部位计需要 **≥4 张参考图**。
        换图像模型时必须重核参考图上限(§15.1 / §15.3 都记了这条)。
        """
        ...


class EmbeddingGateway(Protocol):
    """嵌入网关(架构 §7.1)。

    与 `app/index/embedding.py` 的 `EmbeddingPort` **结构一致**(都提供
    `dimension` 与 `embed`)。`Protocol` 是结构化类型,故本协议的实现无需
    任何 import 即可直接喂给 `apply_event` / `IndexSyncConsumer`
    —— T-0.13 的代码一行不用改。该接缝由测试实际走通来证明,不靠类型注解自证。
    """

    @property
    def dimension(self) -> int:
        """向量维度。集合按它创建(`index_vector_size`)。"""
        ...

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        """把若干文本编码成向量,顺序与输入一致。"""
        ...
