"""模型网关层(架构 §7)。

三层分工:

- `ports.py` —— 三类抽象接口与数据类。签名对齐 §7.1。业务代码只依赖这一层。
- `openai_compat.py` —— 具体怎么跟某个厂商说话(§7.2「适配器统一走 OpenAI 兼容协议」)。
- `resilience.py` / `resilient.py` —— 说不通时怎么办:超时、重试、限流、熔断、成本计量。
- `failover.py` —— 主力与备份之间的切换(§13.7 的 10% 阈值、§15.1 的双供应商)。
- `factory.py` —— 按配置把上面几层组装起来。**这是业务代码唯一该 import 的入口**。

**本层不对应任何 HTTP 端点**:`/internal/*` 暴露的是智能体能力,不是模型能力。
业务读写一律经 Java(不变量 3、4),本层不经手任何业务数据。

**Embedding 是插槽状态**:§7.1 的 `embed(texts) -> vectors` 已定义,
但真实厂商待 P0-2 定案,`build_embedding_gateway()` 默认返回 T-0.13 的确定性占位。
"""

from app.gateway.factory import (
    build_embedding_gateway,
    build_image_gateway,
    build_llm_gateway,
)
from app.gateway.ports import (
    EmbeddingGateway,
    GeneratedImage,
    ImageGenGateway,
    Images,
    LLMGateway,
    Message,
    Reply,
    ReplyChunk,
    ToolCall,
    ToolCallPlan,
    ToolDef,
    Usage,
)

__all__ = [
    "EmbeddingGateway",
    "GeneratedImage",
    "ImageGenGateway",
    "Images",
    "LLMGateway",
    "Message",
    "Reply",
    "ReplyChunk",
    "ToolCall",
    "ToolCallPlan",
    "ToolDef",
    "Usage",
    "build_embedding_gateway",
    "build_image_gateway",
    "build_llm_gateway",
]
