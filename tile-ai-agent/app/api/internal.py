"""`/internal/*` 路由(Java ↔ Python)。

契约:`openapi-internal.yaml`。本文件目前为**骨架**,端点自 T-1.5 起逐个落地。

鉴权:服务令牌 `X-Service-Token`
(`openapi-common.yaml#/components/securitySchemes/serviceToken`),
叠加内网 mTLS;对外由网关阻断(架构 §6.1)。
"""

from fastapi import APIRouter

router = APIRouter(prefix="/internal", tags=["internal"])

# 契约已冻结的 7 个端点,待实现(带 x-todo 者需先评审,勿自行定夺):
#   POST /internal/agent/chat            Java → Python  SSE      x-todo:事件集
#   POST /internal/agent/parse-layout    Java → Python           x-todo:入参/出参
#   POST /internal/agent/material-map    Java → Python           x-todo:响应结构
#   POST /internal/agent/complete-parts  Java → Python           x-todo:40004 归属
#   GET  /internal/products/{sku}        Python → Java           —
#   GET  /internal/furnish/prompts       Python → Java           x-todo:入参/返回
#   POST /internal/generate/callback     Python → Java           —
#
# 注意:契约中**不存在** /internal/quote/* —— 报价只归 Java(架构 §4.1 / A1)。
