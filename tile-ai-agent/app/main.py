"""Python 智能体服务入口(FastAPI)。

职责见架构 §4.2:Agent 编排、对话咨询(路径 C)、户型参数解析、材质映射、
陈设提示词组装、图像生成调用。

不可违反的边界(见 CLAUDE.md「架构不变量」):
  - 本服务**仅内网**,不直接对外;`/internal/*` 对外由网关阻断。
  - **不参与报价** —— 报价只归 Java(架构 §4.1,一致性评审 A1)。
  - 不持有业务权威数据,业务读写一律经 Java;除 Qdrant 向量索引外无数据副本。

契约:同步 `openapi-internal.yaml`,异步 `asyncapi-internal.yaml`。
"""

from fastapi import FastAPI

from app.api.internal import router as internal_router
from app.core.config import settings

app = FastAPI(
    title="瓷砖选购 AI — 智能体服务",
    version="0.1.0",
    description=(
        "Java 业务核的下游 AI 服务,仅内网可达。"
        "对外契约 `openapi-internal.yaml`,异步契约 `asyncapi-internal.yaml`。"
    ),
)

app.include_router(internal_router)


@app.get("/health", tags=["ops"], summary="健康检查")
async def health() -> dict[str, str]:
    """进程存活探针,供 Docker Compose healthcheck 使用(架构 §12.1 / T-2.4)。"""
    return {"status": "UP", "service": settings.service_name}
