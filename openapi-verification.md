# 接口契约验证表(契约 ↔ 上游)

> 对象:`openapi-common.yaml` / `openapi-c.yaml` / `openapi-admin.yaml` / `openapi-internal.yaml`
> 上游:`tile-ai-prd.md`(v0.6)、`tile-ai-architecture.md`(v1.2)
> 生成日期:2026-09-29
> 结论三态:✓符合 / ⚠️待确认 / ✗冲突

---

## 1. 契约 ↔ 上游 逐条追溯表

### 1.1 C 端(openapi-c.yaml)

| 契约条目 | 出处 | 结论 |
| --- | --- | --- |
| POST /auth/sms/send | 架构 §10.2 | ✓ |
| POST /auth/login/sms(needSetPassword + tempToken) | 架构 §10.2、§11.1 | ✓ |
| POST /auth/register/set-password(临时令牌) | 架构 §10.2、§11.1 | ✓ |
| POST /auth/login/password | 架构 §10.2 | ✓ |
| POST /auth/password | 架构 §10.2 | ✓ |
| POST /auth/logout | 架构 §10.2 | ✓ |
| POST /auth/refresh | 架构 §10.1(提及 refresh_token、10003) | ⚠️ 接口清单未列,路径/方法为推断 |
| GET /products(筛选/分页) | 架构 §10.2、PRD §5.2.1 | ✓(query 参数名为推断 ⚠️) |
| GET /products/{sku} | 架构 §10.2、PRD §5.2.1 | ✓ |
| GET /combos | 架构 §10.2、PRD §5.2.2 | ✓ |
| GET /combos/{id} | 架构 §10.2 | ✓ |
| POST /agent/chat(SSE,仅建议+refs) | 架构 §10.2、§10.6③、PRD §5.2.3 | ✓(SSE 事件集为推断 ⚠️) |
| GET /agent/chat/sessions | 架构 §10.2 | ✓ |
| GET /agent/chat/sessions/{id} | 架构 §10.2 | ✓ |
| GET /layout/templates | 架构 §10.2、PRD §5.3.1 | ✓ |
| POST /layout/parse | 架构 §10.2、PRD §5.3.2 | ⚠️ 入参/出参结构未定义,为推断 |
| POST /generate(部分部位 + 自动补齐) | 架构 §10.2、§10.6①、PRD §5.2.4/§5.4 | ✓(planId 为推断 ⚠️) |
| GET /generate/{taskId}(progress/状态机) | 架构 §10.2、§10.6②、§6.3 | ✓ |
| POST /quote/calc(按部位) | 架构 §10.2、§10.6⑤、PRD §5.5 | ✓ |
| GET /plans | 架构 §10.2、PRD §5.6 | ✓(卡片字段由 §5.6 映射 ⚠️) |
| GET /plans/{id} | 架构 §10.2、§11.4 | ✓ |
| PATCH /plans/{id}(重命名) | 架构 §10.2、PRD §5.6 | ✓ |
| DELETE /plans/{id}(逻辑删除) | 架构 §10.2、PRD §5.6 | ✓ |
| POST /plans/{id}/copy | 架构 §10.2、§11.4 | ✓ |
| POST /plans/{id}/share | 架构 §10.2、PRD §5.6 | ✓(返回结构为推断 ⚠️) |
| POST /plans/{id}/recalc(无请求体) | 架构 §15.7 | ✓ |
| GET /share/{token} | 架构 §10.2、PRD §5.6 | ✓ |
| GET /store | 架构 §10.2、PRD §5.6/§7.6 | ✓ |

### 1.2 B 端(openapi-admin.yaml)

| 契约条目 | 出处 | 结论 |
| --- | --- | --- |
| POST /auth/login | 架构 §4.4("均需后台会话") | ⚠️ 接口清单未列,为推断 |
| /products(CRUD + 上下架) | 架构 §10.3、PRD §5.7 | ✓(方法/子路径展开为推断 ⚠️) |
| POST /products/import、GET /products/import/{batchId} | 架构 §8.7 | ✓(字段结构为推断 ⚠️) |
| /combos(CRUD + 关联 SKU/部位) | 架构 §10.3、PRD §5.7 | ✓(展开为推断 ⚠️) |
| /templates(CRUD) | 架构 §10.3、PRD §5.7 | ✓(展开为推断 ⚠️) |
| /furnish(空间×风格矩阵) | 架构 §10.3、§8.5 | ✓(展开为推断 ⚠️) |
| /quote-params | 架构 §10.3、§8.5 | ✓(展开为推断 ⚠️) |
| /store | 架构 §10.3、§8.5 | ✓(展开为推断 ⚠️) |
| /accounts(账号权限) | 架构 §10.3、§4.4 | ✓(展开为推断 ⚠️) |
| /logs(操作日志,只读) | 架构 §10.3、§4.4、§8.1 | ✓(展开为推断 ⚠️) |
| 权限点 product:write / combo:delete / quote-param:write / account:manage | 架构 §4.4 | ✓ |

### 1.3 内部(openapi-internal.yaml)

| 契约条目 | 出处 | 结论 |
| --- | --- | --- |
| POST /internal/agent/chat | 架构 §10.4、§6.1 | ✓ |
| POST /internal/agent/parse-layout | 架构 §10.4、§4.3② | ✓ |
| POST /internal/agent/material-map | 架构 §10.4、§4.3③ | ✓ |
| POST /internal/agent/complete-parts | 架构 §10.4、§4.2、§11.2 | ✓ |
| GET /internal/products/{sku} | 架构 §10.4、§6.1 | ✓ |
| GET /internal/furnish/prompts | 架构 §10.4、§6.1 | ✓ |
| POST /internal/generate/callback | 架构 §10.4、§10.6④ | ✓ |
| (无 /internal/quote/calc) | 架构 §4.1、§6.1(一致性评审 A1) | ✓ |
| 鉴权 X-Service-Token | 架构 §10.4、§6.1 | ✓ |

### 1.4 横切一致性核对(§10.1、§10.5)

| 检查项 | 契约落点 | 结论 |
| --- | --- | --- |
| 统一响应体 {code,message,data,traceId} | common.ApiResponse,全接口引用 | ✓ |
| 状态码策略:业务 HTTP 200;仅 401/403 真实状态 | common.responses.Unauthorized/Forbidden | ✓ |
| 分页 page/size → data{list,total,page,size} | common.PageData + PageParam/SizeParam | ✓ |
| 鉴权 Set-Cookie、不用 Authorization;tempToken 例外 | common.securitySchemes(cookieAuth/tempToken) | ✓ |
| 错误码表(含 40004) | common.ErrorCode enum | ✓ |
| 部位枚举 floor/wall/local | common.Part(固定三值,无美缝) | ✓ |
| 任务状态机 pending→running→success/failed | common.TaskStatus | ✓ |
| 报价公式(开口扣减、同砖合并、辅料) | common.QuoteItem/QuoteResult、§8.8 | ✓(数值示例自洽,见 §3) |

---

## 2. §11 时序图逐条核对

| 时序 | 消息 | 契约对应 | 结论 |
| --- | --- | --- | --- |
| §11.1 | POST /auth/sms/send | /auth/sms/send | ✓ |
| §11.1 | POST /auth/login/sms | /auth/login/sms | ✓ |
| §11.1 | needSetPassword + 临时令牌 | LoginSmsData | ✓ |
| §11.1 | POST /auth/register/set-password | /auth/register/set-password | ✓ |
| §11.1 | Set-Cookie(access+refresh) | cookieAuth | ✓ |
| §11.2 | POST /generate {layout, parts[]可部分, space, style, angles} | GenerateRequest | ✓ |
| §11.2 | /internal/agent/complete-parts(AI 补齐) | /internal/agent/complete-parts | ✓ |
| §11.2 | 补齐后的 parts(标注 ai) | PartSelection.source=ai | ⚠️ 字段名为推断 |
| §11.2 | 落 t_generation_task(pending) | 内部,无 REST 契约 | — |
| §11.2 | 投递 queue.generate | MQ,无 REST 契约 | ⚠️ 见 §4-13 |
| §11.2 | taskId → 前端 | GenerateAccepted.taskId | ✓ |
| §11.2 | POST /internal/generate/callback {taskId,status,imageUrls,progress} | /internal/generate/callback | ✓ |
| §11.2 | GET /generate/{taskId} 轮询 progress/status | TaskProgress | ✓ |
| §11.3 | POST /agent/chat(SSE){sessionId, message} | /agent/chat | ✓ |
| §11.3 | 流式 token + refs | SSE delta / refs | ✓(事件集为推断) |
| §11.3 | 一键采纳 → 跳转「户型录入」 | refs(SkuRef)+ 前端跳转 | ✓ |
| §11.4 | GET /plans/{id} | /plans/{id} | ✓ |
| §11.4 | POST /generate(planId=原 id,覆盖) | GenerateRequest.planId | ⚠️ planId 为推断 |
| §11.4 | POST /plans/{id}/copy → 新 planId | /plans/{id}/copy | ✓ |

**时序核对结论**:所有 REST 消息均有契约落点;未覆盖的仅 **MQ 事件**(queue.generate / queue.index.sync / DLQ)——它们不属于 HTTP 契约,见待确认项 D-13。

---

## 3. 抽取发现的"文档内部"问题(非契约缺陷,供上游修订)

1. **§10.2 接口清单遗漏"刷新令牌"接口**:§10.1 定义了 refresh_token(14 天)与错误码 10003,但清单未列。契约已按 `POST /auth/refresh` 补齐并标 ⚠️。
2. **§10.2 遗漏后台登录接口**:§4.4 要求"后台会话",但 C/B 端清单均无后台登录。
3. **§10.6⑤ 报价示例算术自洽**(已核对):floor 3.6×1.06×268=1022.69;wall 有效面积 13.9−(0.8×2.1+1.0×1.2)=11.02,11.02×1.06×68=794.32;材料合计 1817.01;辅料 (3.6+11.02)×20=292.40;总价 2109.41。**契约 schema 与该示例一致**。✓
4. **损耗率数值口径**:§5.5 写"默认 5%~8%",§8.8/§10.6 的 rulesSnapshot 用 `lossRate: 0.06`(小数)。契约按小数(0.06 表示 6%),需上游统一口径。
5. **`/plans/{id}/recalc` 仅在 §15.7 出现**,未进 §10.2 清单(清单标注为"节选",故非冲突,但建议补入)。

---

## 4. 待确认清单(上游未定义/推断项)

| # | 项 | 现状 | 契约中的处理 |
| --- | --- | --- | --- |
| D-1 | 刷新令牌接口 | §10.1 有令牌无接口 | 补 `POST /auth/refresh`,标 ⚠️ |
| D-2 | 户型结构化模型完整字段 | ✅ 已定:架构 §8.3 直写「结构化户型模型字段定义」表(原 SDD §3.10 已并入) | LayoutModel 对齐该表,x-todo 已移除 |
| D-3 | `/generate` 的 planId | §10.6① 无,§11.4 有 | 补 planId,标 x-todo |
| D-4 | 补齐项"来源"字段名/枚举 | §11.2 仅说"标注 ai" | common.PartSource(user/ai),标 x-todo |
| D-5 | angles 语义与视图命名 | §10.6① angles:1;§8.4 view=default/angle1… | angles(int)+view(string),标隐式约定 |
| D-6 | `/layout/parse` 入参/出参 | §10.2 仅一句 | 依 common.LayoutModel 推断 |
| D-7 | `/agent/chat` SSE 事件全集、sessionId 生成时机 | §10.6③ 仅 delta/refs/[DONE] | 按三类事件建模,标 x-todo |
| D-8 | `/products` 查询参数名 | §10.2 仅"花色/材质/规格/风格/价格" | 推断 keyword/material/spec/style/space/priceMin/priceMax |
| D-9 | `/plans` 列表卡片字段 | §5.6 有业务描述 | 映射为 PlanSummary |
| D-10 | B 端 CRUD 方法/子路径 | §10.3 仅资源前缀 | 按完整 RESTful 展开 |
| D-11 | `data.errors[]` 元素结构 | §10.5 仅提及 | common.ErrorItem({field,reason,rejectedValue}) |
| D-12 | 后台登录接口 | §4.4 需后台会话 | 补 `POST /admin/auth/login`,标 ⚠️ |
| D-13 | **MQ 事件契约**(queue.generate / queue.index.sync / DLQ) | §6.2、§6.4 描述 MQ 消息但无结构 | **不在 OpenAPI 范围**;建议另出 AsyncAPI 或消息规范文档 |
| D-14 | SpaceType/StyleType 是否封闭枚举 | ✅ 已定:后台字典可配置(架构 §8.5 `t_dict`、§10.1、§10.2、§10.3) | enum 标注为**内置默认值**;新增 `GET /dicts`(C)、`/admin/dicts`(B)、common.Dict |
| D-15 | 损耗率口径(0.06 还是 6%) | §5.5 与 §8.8 表述不一 | 统一按小数 0.06 |
| D-16 | 批量导入"模板下载"接口 | §8.7 提"模板下载" | 缺路径,未建 |
| D-17 | 分享 token 失效/访问范围控制接口 | §13.5 提"支持失效" | 缺接口,未建 |
| D-18 | 401 时的响应体 | §10.1 真实 401 + §10.5 业务码 10002 并存 | 约定 401 响应体仍为 ApiResponse(code=10002) |
| D-19 | 报价参数 aux_unit_price 单位/量纲 | §8.5 仅 param_value | 未标注单位,建议后台增加单位字段 |

---

## 5. 契约测试可校验项清单(供 CI)

> 建议实现方式:① Spectral/redocly 做静态规则;② FastAPI(自动 OpenAPI)与 springdoc 导出 schema 与本契约做 diff;③ 对 §10.6 示例做 schema 校验。

> **2026-10-01 补注(上面这句建议不改)**:第 ② 条**没有按"导出 OpenAPI"来做** ——
> springdoc 未被采用,T-12 改为读实现侧的**路由表 + 反射内省**(Java)与
> `app.openapi()` + 模块常量断言(Python)。**"与契约做 diff"这件事本身没变**,
> 下面的 T-12 一行 (「实现导出的 OpenAPI 与本契约结构 diff」) 请按此口径理解。
> 完整裁决见 **§40**。

| # | 校验项 | 期望 |
| --- | --- | --- |
| T-1 | 所有响应体为 ApiResponse(code/message/traceId 必填) | 通过 |
| T-2 | 所有业务错误码 ∈ common.ErrorCode enum | 通过 |
| T-3 | 分页响应 data 含 list/total/page/size | 通过 |
| T-4 | 需登录接口带 `security: cookieAuth`;公开接口 `security: []` | 通过 |
| T-5 | 内部接口带 `security: serviceToken` | 通过 |
| T-6 | c/admin 契约**不出现** `/internal/*` 路径 | 通过 |
| T-7 | 内部契约**不出现** `/internal/quote/*`(A1) | 通过 |
| T-8 | 对外 schema **不出现** `layout_enc`(密文字段);仅出现解密后 `layout` | 通过 |
| T-9 | Part enum 恒为 [floor, wall, local] | 通过 |
| T-10 | TaskStatus 恒为 [pending, running, success, failed] | 通过 |
| T-11 | §10.6 的三段示例(生成请求/回调/报价)可被对应 schema 校验 | 通过 |
| T-12 | 实现导出的 OpenAPI 与本契约结构 diff(C、internal 双向) | 一致 |
| T-13 | 仅 401/403 用真实 HTTP 状态,其余业务结果 HTTP 200 | 通过 |
| T-14 | 所有 `$ref` 可解析(无悬空引用) | 通过 |
| T-15 | 空间/风格**不硬编码**:前端提交的 space/style 必须 ∈ `GET /dicts` 返回集;契约内置 enum 为其子集(默认可运行) | 通过 |

---

## 6. 未覆盖范围说明

- **MQ 异步消息**(queue.generate / queue.index.sync / DLQ):§6.2、§6.4 定义了可靠性机制,但消息体结构上游未定义,不在 OpenAPI 契约内(D-13)。
- **Redis 会话上下文结构**(§4.2):内部实现细节,不属对外契约。
- **模型网关抽象接口**(§7.1):Python 内部接口,不属 Java↔Python 契约。

---

## 7. 裁决结论(2026-09-29)

> 对 §4 待确认清单的逐条裁决。归属:【契约已定】= 契约内已定论;【上游改】= 需回改 PRD/架构;【待 SDD】= 待 SDD 产出回填;【另文档】= 不属本契约范围。

| # | 裁决 | 契约落地 | 归属 |
| --- | --- | --- | --- |
| D-1 | 采纳 `POST /auth/refresh`(无 body) | 已有 | ✅ 已回改 §10.2 |
| D-2 | 采纳推断集,并以架构 §8.3 字段表为准 | LayoutModel(对齐 §8.3,移除 SDD §3.10 引用) | ✅ 已回改 §8.3/§13.5 |
| D-3 | 采纳 `planId`(可选) | GenerateRequest.planId | 契约已定 |
| D-4 | 采纳 `source ∈ {user, ai}` | common.PartSource | 契约已定 |
| D-5 | 采纳 angles/view 映射 | common.PlanImage.view + GenerateRequest.angles | 契约已定 |
| D-6 | 采纳;解析失败用 10001 | /layout/parse x-todo | 契约已定 |
| D-7 | 采纳事件集;sessionId 首帧下发 | c + internal chat | ✅ 已回改 §10.6③ |
| D-8 | 采纳 + 补 `colorPattern` | /products 请求参数 | 契约已定 |
| D-9 | 采纳 PlanSummary 映射 | common.PlanSummary | 契约已定 |
| D-10 | 采纳完整 RESTful | openapi-admin.yaml | 契约已定 |
| D-11 | 采纳 ErrorItem | common.ErrorItem | 契约已定 |
| D-12 | 采纳 `POST /admin/auth/login` | openapi-admin.yaml | ✅ 已回改 §10.3 |
| D-13 | 不在 OpenAPI 范围 | — | 另文档(AsyncAPI,待产出) |
| D-14 | 保留枚举并标注"内置默认值,以后台字典为准";新增字典接口 | SpaceType/StyleType 说明 + `GET /dicts`(C)/`/admin/dicts`(B)/common.Dict | ✅ 已回改 §8.5/§10.1/§10.2/§10.3 + PRD §5.7 |
| D-15 | 统一小数口径(0.06=6%) | QuoteParam 说明 | ✅ 已回改 §5.5/§11#7 |
| D-16 | 采纳 `GET /admin/products/import/template` | openapi-admin.yaml | ✅ 已回改 §10.3 |
| D-17 | 采纳 `DELETE /plans/{id}/share` | openapi-c.yaml | ✅ 已回改 §10.2 |
| D-18 | 采纳:401 返回 ApiResponse(10002/10003) | common.responses.Unauthorized | 契约已定 |
| D-19 | 采纳量纲:元/㎡ + 小数 | common.QuoteParam | 契约已定 |

### 7.1 上游文档回改记录(2026-09-29 已执行)

| 文档 | 位置 | 修改 | 状态 |
| --- | --- | --- | --- |
| 架构 | §8.3 | 直写「结构化户型模型字段定义」表,去掉对 SDD §3.10 的引用 | ✅ |
| 架构 | §13.5 | `详见 SDD §3.10` → 说明作用于 `t_layout/t_plan.layout_enc`,对外传明文,见 §8.3 | ✅ |
| 架构 | §8.5 | 新增 `t_dict`(数据字典,后台可配置)表 | ✅ |
| 架构 | §10.1 | 新增空间/风格枚举"由后台字典维护、不硬编码"说明 | ✅ |
| 架构 | §10.2 | 补 `POST /auth/refresh`、`DELETE /plans/{id}/share`、`POST /plans/{id}/recalc`、`GET /dicts` | ✅ |
| 架构 | §10.3 | 补后台登录、批量导入(模板下载/进度)、`/admin/dicts` | ✅ |
| 架构 | §10.6③ | 补首个事件 `data: {"sessionId":"..."}` | ✅ |
| PRD | §5.5 | 损耗率口径统一为小数(默认 6%,5%~8%,如 `0.06`) | ✅ |
| PRD | §11 决策表 #7 | 损耗率口径同步 | ✅ |
| PRD | §5.7 | 新增"空间 / 风格字典配置"后台能力 | ✅ |

### 7.2 仍挂起(不阻塞契约使用)

- **D-13**:MQ 消息规范(queue.generate / queue.index.sync / DLQ)无上游可依,建议另出 AsyncAPI 或消息规范文档跟踪。
- 其余 D 项均已裁决并落地;契约内 `x-todo` 仅保留 **D-3/D-4/D-5/D-6/D-7/D-8/D-11** 等"依惯例推断"标注,待实现阶段双向校验(T-12)确认。

---

## 8. 静态校验结果(2026-09-29)

> 本节为对 §5 校验清单(T-1~T-15)的**实际执行记录**。所有结论均来自工具输出或脚本断言,不含人工目测。
> **§8.1~§8.7 = 修复前基线**:4 份契约均未通过 OpenAPI 结构校验,根因是 **16 处 YAML 语法级缺陷**(详见 8.4 F-1)。
> **§8.8 = 修复执行与回归结果**:F-1~F-4 已经确认后全部修复,**4 份契约现已通过结构校验**。

### 8.1 环境与工具

| 项 | 值 | 备注 |
| --- | --- | --- |
| OS | Windows 11 (10.0.26200) | ⚠️ 非 Linux;见下方说明 |
| Node.js | v24.14.1 | |
| npm | 11.11.0 | |
| Python | 3.11.9 | |
| PyYAML | 6.0.3 | 用于契约断言 |
| jsonschema | 4.26.0 | 用于 T-11 示例校验 |
| @redocly/cli | **2.55.0** | 内置 `recommended` 规则集 |
| @stoplight/spectral-cli | **6.16.3** | ruleset = `spectral:oas` |
| @apidevtools/swagger-parser | **13.1.0** | `$ref` 解析 + OAS 结构校验 |

**环境说明**:任务描述中的"本机隔离 Linux 环境"**未启用**。本机存在 WSL Ubuntu 发行版,但项目文件位于 `D:\`,且所需工具全部为 Node/Python 生态、在 Windows 下可原生运行,故直接使用 Windows 原生工具链,未走 WSL。npm 全局目录已按约定创建于 `outputs/.npm-global`(原目录不存在,为本节新建成);依赖实际安装在 `outputs/tools/node_modules`(本地、可丢弃),未污染全局环境。

### 8.2 执行命令

```powershell
# 依赖安装(本地)
npm install --no-audit --no-fund @redocly/cli @stoplight/spectral-cli `
            @apidevtools/swagger-parser @stoplight/spectral-rulesets

# ① YAML 解析 + $ref 解析 + OpenAPI 3.0 结构校验
node outputs/scripts/validate-refs.js

# ② redocly 结构 + 规则 lint(逐份)
redocly lint <file>.yaml --format=json

# ③ spectral lint(逐份;spectral:oas 别名需本地 ruleset 文件转发)
spectral lint <file>.yaml --ruleset outputs/tools/.spectral.yaml --format json

# ④ T-1~T-15 程序化断言
python outputs/scripts/check_t.py

# ⑤ 缺陷行号定位与解析还原
python outputs/scripts/show_lines.py
```

原始输出留档:`outputs/reports/`(redocly-*.json / spectral-*.json / swagger-parser.txt / t-results.json)。

### 8.3 T-1~T-15 逐条结果

| # | 校验项 | 结论 | 证据 |
| --- | --- | --- | --- |
| T-1 | 所有响应体为 ApiResponse(code/message/traceId 必填) | ✅ 通过 | `ApiResponse.required = ['code','message','traceId']`。JSON 响应体中非 ApiResponse 的接口 **0 个**;另有 4 处**设计例外**:2 处 SSE(`c POST /agent/chat`、`internal POST /internal/agent/chat`,流式无法包 ApiResponse)、2 处二进制下载(`admin GET /products/import/template` 的 xlsx / csv) |
| T-2 | 所有业务错误码 ∈ common.ErrorCode enum | ✅ 通过 | 正文出现的 18 个 5 位码全部 ∈ enum(0/10001~10005/20001~20005/30001/30002/40001~40004/50001/90001),越界 **0** |
| T-3 | 分页响应 data 含 list/total/page/size | ✅ 通过 | `PageData.required = ['list','page','size','total']`;引用 PageData 的分页响应 **10 个**,全部经 `allOf` 覆写 `list` 元素类型 |
| T-4 | 需登录接口 cookieAuth;公开接口 `security: []` | ✅ 通过 | 对照架构 §10.2 鉴权列逐一核对 **30 个**接口,不符 **0**、未登记 **0**。(但 scheme **定义**不在本文件内 → 见 F-4) |
| T-5 | 内部接口带 `security: serviceToken` | ❌ **失败** | `internal.yaml` 根级 `security: [{serviceToken: []}]` 声明正确、7 个接口全部继承;但 **`serviceToken` 未在 internal.yaml 内定义**(仅存在于 common.yaml)→ redocly `security-defined` 报错。见 **F-4** |
| T-6 | c/admin 不出现 `/internal/*` 路径 | ✅ 通过 | paths 键中 `/internal/*` **0 个**。⚠️ 但 `openapi-c.yaml:459` 描述文本中字面出现 `/internal/agent/parse-layout`,基于 grep 的 CI 规则会误报,建议加白名单 |
| T-7 | 内部契约不出现 `/internal/quote/*` | ✅ 通过 | paths 中 **0 个**;仅 `internal.yaml:15` 描述中提及"**无** /internal/quote/calc"以说明 A1 裁决 |
| T-8 | 对外 schema 不出现 `layout_enc` | ✅ 通过 | `layout_enc` 出现 **0 次**;明文 `layout` 出现 4 处(`PlanDetail`、`UnitTemplate`、`GenerateRequest`、`UnitTemplateUpsert`),符合"密文仅限静态存储" |
| T-9 | Part enum 恒为 [floor, wall, local] | ✅ 通过 | `common.Part.enum = ['floor','wall','local']`,无其它定义点 |
| T-10 | TaskStatus 恒为 [pending, running, success, failed] | ✅ 通过 | `common.TaskStatus.enum` 一致;`ImportStatus.status` 复用同值集,未另立枚举 |
| T-11 | §10.6 示例可被对应 schema 校验 | ✅ 通过 | 5 个用例全部通过:`GenerateRequest` / `QuoteCalcRequest` / `TaskProgress` / `QuoteResult` / `internal callback.requestBody` |
| T-12 | 实现导出的 OpenAPI 与本契约 diff | ⚠️ **不可执行** | 仓库内**无任何实现代码**,也无 springdoc / FastAPI 导出的 OpenAPI 产物 → 缺被比对方,无法判定。**属前置条件未满足,非契约缺陷**;待阶段 0 产出实现后执行 |
| T-13 | 仅 401/403 用真实 HTTP 状态 | ✅ 通过 | 全量响应码分布 `{200: 73, 401: 13}`,越界 **0**。`403` 在 `common.responses.Forbidden` 中已定义但当前无接口引用 |
| T-14 | 所有 `$ref` 可解析(无悬空引用) | ✅ 通过 | 扫出 **242** 个 `$ref`,悬空 **0**(含跨文件 `./openapi-common.yaml#/...`) |
| T-15 | space/style 不硬编码;enum ⊆ `/dicts` 返回集 | ✅ 通过(静态部分) | `SpaceType` 8 值、`StyleType` 4 值;`GET /dicts`(C)与 `/admin/dicts`(B)均在;`Dict.type.enum = ['space','style']`;硬编码内联 enum **0 处**。⚠️ "内置 enum ⊆ 运行时返回集"需**运行时数据**方可判定,静态不可证,本节只验证其静态前置条件 |

**汇总:通过 12 · 失败 1(T-5)· 不可执行 1(T-12)**。
其中 **T-15 为"静态部分通过"**(运行时子集关系不可静态证);**T-4 虽通过,但因 F-4 需连带修复**(scheme 定义缺失)。

> **2026-10-01 补注(上表 T-12 单元格刻意不改,保留当日原貌)**:T-12 已**不可执行 → 通过**。
> 实现侧不再依赖 springdoc,改为读 Spring 路由表 + Jackson 内省(Java)与 `app.openapi()`
> + 两条模块常量断言(Python);判据由 `outputs/scripts/check_t.py` 读
> `outputs/reports/contract-impl-diff.json` 得出。**完整记录见 §40**,本表当时的"无实现代码"
> 是 2026-09-29 的事实,继续有效于那一日。

### 8.4 失败项明细与修复建议(**待确认,尚未改动任何 YAML**)

#### F-1 ⛔ 阻断:16 处 YAML 流式映射"逗号撕裂"(3 个工具一致确认)

**根因**:YAML **流式映射**(`{ ... }` 单行写法)中,未加引号的标量里的 `,` 被 YAML 当作**键值分隔符**。因此

```yaml
page: { type: integer, description: 当前页,从 1 开始 }
```

实际被解析为 3 个键:`type`、`description='当前页'`、**`从 1 开始`(值为 null 的伪键)**。后果有三:① schema 出现非法附加属性 → OpenAPI 结构校验失败;② **description 被截断**,文档语义丢失;③ `example` 等仍幸存,故**单看 YAML 不会报错,极易漏过**。

**证据**:redocly `struct` 规则、spectral `oas3-schema` 规则、swagger-parser OAS 校验三方**独立报出完全相同的 16 处**。swagger-parser 因经 `$ref` 级联放大,共报 72 条 "additional properties" 违规(16 个真实异物键 × 引用放大)。

| # | 文件:行 | 规则 | 原始报错 | 修复建议 |
| --- | --- | --- | --- | --- |
| 1 | `openapi-common.yaml:103` | struct / oas3-schema | `Property '从 1 开始' is not expected here.` | 给描述加引号:`description: "当前页,从 1 开始"` |
| 2 | `openapi-common.yaml:239` | 同上 | `Property '唯一)' is not expected here.` | `description: "产品编号(sku_code,唯一)"` |
| 3 | `openapi-common.yaml:241` | 同上 | `Property '如 750×1500mm' is not expected here.` | `description: "规格,如 750×1500mm"` |
| 4 | `openapi-common.yaml:276` | 同上 | `Property '前端可传可不传' is not expected here.` | `description: "该部位面积(㎡);可由服务端依户型计算,前端可传可不传"` |
| 5 | `openapi-common.yaml:300` | 同上 | `Property '如 0.06' is not expected here.` | `description: "损耗率,如 0.06"` |
| 6 | `openapi-common.yaml:455` | 同上 | `Property '例:80-100㎡' is not expected here.` | `description: "面积区间,例:80-100㎡"` |
| 7 | `openapi-common.yaml:456` | 同上 | `Property '例:三室两厅' is not expected here.` | `description: "户型结构,例:三室两厅"` |
| 8 | `openapi-common.yaml:538` | 同上 | `Property '如 living_room' is not expected here.` | `description: "存储键(对外提交值),如 living_room"` |
| 9 | `openapi-common.yaml:539` | 同上 | `Property '如 客厅' is not expected here.` | `description: "展示名,如 客厅"` |
| 10 | `openapi-common.yaml:540` | 同上 | `Property '升序' is not expected here.` | `description: "排序,升序"` |
| 11 | `openapi-admin.yaml:819` | 同上 | `Property '业务主键)' is not expected here.` | `description: "产品编号(唯一,业务主键)"` |
| 12 | `openapi-admin.yaml:895` | 同上 | `Property '如 living_room' is not expected here.` | `description: "存储键(对外提交值),如 living_room"` |
| 13 | `openapi-admin.yaml:896` | 同上 | `Property '如 客厅' is not expected here.` | `description: "展示名,如 客厅"` |
| 14 | `openapi-admin.yaml:897` | 同上 | `Property '升序' is not expected here.` | `description: "排序,升序"` |
| 15 | `openapi-internal.yaml:63` | 同上 | `Property '用于鉴权与留痕)' is not expected here.` | `description: "已登录用户(由 Java 透传,用于鉴权与留痕)"` |
| 16 | `openapi-internal.yaml:288` | 同上 | `Property '回调幂等键' is not expected here.` | `description: "对外唯一任务号,回调幂等键"` |

> **修复方式说明**:统一给受影响的**流式映射标量值加双引号**。**不改动任何描述文字内容,不改动类型/枚举/必填等语义**,仅消除 YAML 解析歧义。
> **建议同时做防复发**:① 全仓统一改**块式(block)写法**,弃用 `{ }` 流式映射;或 ② 在 CI 中加入本节的 `check_t.py` 同类断言 + redocly lint,阻断合入。

#### F-2 ⛔ `openapi-internal.yaml:87` — Media Type Object 不允许 `description`

```yaml
            application/json:
              description: 备用:非流式聚合返回(若 Java 侧选择非 SSE 调用)   # ← 非法
              schema: { $ref: './openapi-common.yaml#/components/schemas/ApiResponse' }
```

原始报错(redocly + spectral 一致):`Property 'description' is not expected here.`
**依据**:OpenAPI 3.0 的 Media Type Object 仅允许 `schema` / `example` / `examples` / `encoding` 四个字段,无 `description`。

**修复建议(二选一,涉及措辞,请裁决)**:
- **方案 A(推荐)**:删除该行,把语义上移到**响应级** `description`(该响应已有 description,追加一句即可)。
- **方案 B**:删除该行,把说明并入 `schema` 内的描述。
- 该行文字纯为注释性说明,不影响任何接口语义。

#### F-3 ⛔ `openapi-common.yaml:124` — `nullable` 未伴随 `type`

```yaml
        rejectedValue:
          description: 被拒绝的原始值(可选)
          nullable: true          # ← 无 type 兄弟字段
```

原始报错(redocly):`[nullable-type-sibling] The 'type' field must be defined when the 'nullable' field is used.`
**修复建议(推荐方案 A)**:直接**删除第 124 行 `nullable: true`**。理由:该字段未声明 `type`,JSON Schema 语义下**本已允许任意值(含 null)**,`nullable` 在此既非法又无意义 —— 删除是**语义等价**的,不改变任何校验行为。
- 方案 B:补 `type: string`,但这会**收窄**字段可接受范围(把原始被拒值限制为字符串),属语义变更,**不建议**。

#### F-4 ⚠️ securitySchemes 未在 c / admin / internal 内本地声明

**现象**(redocly `security-defined`):

| 文件 | 位置 | 报错 |
| --- | --- | --- |
| `openapi-c.yaml` | 16 处(104、160、184、333、366、397、496、524、578、609、630、655、673、700、721、741 行) | 15 处 `There is no 'cookieAuth' security scheme defined.`;1 处 `tempToken`(L104) |
| `openapi-admin.yaml` | 44 行(根级 security) | `There is no 'cookieAuth' security scheme defined.` |
| `openapi-internal.yaml` | 30 行(根级 security) | `There is no 'serviceToken' security scheme defined.` |

**根因**:三个方案(`cookieAuth` / `tempToken` / `serviceToken`)**只定义在 `openapi-common.yaml`**,而 `security` / `securitySchemes` **无法跨文档 `$ref`**(这与 `schemas` 不同)。因此**每份文件单独解析时 security 悬空**。
**性质**:这是"common 作为共享组件库"设计的**固有结构性缺口**,不是笔误。它直接导致 **T-5 失败**。

**修复建议(三选一,请裁决)**:
- **方案 A(推荐,改动最小、收益最直接)**:在 `openapi-c.yaml` / `openapi-admin.yaml` / `openapi-internal.yaml` 各自的 `components` 下**补声明 `securitySchemes`**(内容与 common 一致,加注"与 common 保持同步")。三份文件即可**独立通过校验**,代价是 3 处重复定义。
- **方案 B(不改契约)**:维持现状,在 lint 阶段改用**先 bundle 再 lint**(`redocly bundle` 产出解析后的单文档),并在 redocly 配置中把 `security-defined` 降级为 warning。契约文件保持单一真源,代价是 CI 链路多一步、且单文件不可独立校验。
- **方案 C**:把 `securitySchemes` 的归属重新划分(如每份文件自持自用),改动面最大,需回改架构 §10.1。

> 该项**不影响运行期行为**(只要实现侧按 common 统一装配即可),但**影响契约的可独立校验性与可交付性**(例如把 `openapi-internal.yaml` 单独交给 Python 团队时会缺鉴权定义)。**倾向方案 A**。

#### F-5 ⚠️ T-12 不可执行

仓库内**无实现代码**,亦无 springdoc / FastAPI 导出的 OpenAPI 产物,缺少比对对象。**非契约缺陷**,待阶段 0 实现落地后执行双向 diff。

> **2026-10-01 补注(上文不改)**:F-5 已闭合。T-12 于本日转为真判据(PASS=15 / FAIL=0 / SKIP=0),
> 但**实现侧不导出 OpenAPI** —— 比对改为读实现侧的反射内省与路由表,口径见 **§40**。
> 故"无 springdoc / FastAPI 导出产物"这一条**至今仍然成立,且不再是阻塞**。

### 8.5 非缺陷项(校验器规则差异,建议以配置消音)

| 报错 | 涉及文件 | 判定 |
| --- | --- | --- |
| `no-empty-servers` / `oas3-api-servers` | `openapi-common.yaml` | **假阳**。该文件为**纯组件库**(`paths: {}`,供 `$ref` 引用),按设计无需 `servers`。建议在 redocly 配置中对该文件关闭该规则 |
| `no-unused-components` / `oas3-unused-component`(27 条) | `openapi-common.yaml` | **假阳**。组件被**其它 3 份文件**引用,校验器只看单文件。建议关闭该规则或改用 bundle 后校验 |
| `operation-operationId` / `operation-4xx-response` | c / admin / internal | **风格建议**,非 §5 要求;是否补齐取决于是否要由契约生成 SDK。当前契约以"可读 + 可校验"为目标,可暂不补 |
| `info-license` / `info-contact` | 全部 4 份 | **风格建议**,非缺陷。如契约拟对外发布再补 |
| swagger-parser 报 72 条 `additional properties` | 全部 4 份 | **级联计数**,真实缺陷仅 **16 个异物键**(见 F-1);因快照被多处 `$ref` 引用而重复报出,不可按 72 计数 |

### 8.6 复现方式

```powershell
# 一次性复现本节全部结论
node  outputs/scripts/validate-refs.js     # 结构校验(4 份均 FAIL)
python outputs/scripts/check_t.py          # T-1~T-15(12 通过 / 1 失败 / 1 不可执行)
python outputs/scripts/show_lines.py       # 16 处缺陷的原文与解析还原
# 报告产物:outputs/reports/
```

### 8.7 本节结论

1. **4 份契约当前均无法通过 OpenAPI 3.0 结构校验**,根因是 **F-1 的 16 处 YAML 逗号撕裂** —— 这是**语法级缺陷,必须修复**方可进入实现阶段。
2. F-1 的危害不止于"校验红",更在于**描述文本被静默截断**:YAML 本身不报错,人工阅读 YAML 也看不出问题(单行 `{ }` 里的逗号极易被当作普通标点),只有跑工具或看解析后的 AST 才会暴露。**§5 的 T-1~T-15 清单一项目前都检测不到这类缺陷**,建议把"解析后 AST 的键合法性断言"补入 §5 作为 **T-16**。
3. F-2 / F-3 为**单点结构错误**,修复明确且语义等价。
4. F-4 是**结构性设计取舍**,需上游裁决后才能改;当前不影响运行期,只影响单文件可校验性。
5. §5 中 T-1~T-15 的预期结论均为"通过",但**实际由外部工具复跑时,结构校验层面 4 份全部不通过** —— 说明 §5 的清单偏向"语义一致性",**缺少"语法/结构合法性"这一层**。建议在 §5 增补结构校验项(见第 2 条)。

### 8.8 修复执行与回归结果(2026-09-29)

> §8.4 的 F-1~F-4 经确认后已全部落地。**所有修复均为结构性/语法性修复,不改动接口语义**;F-1 的 16 处仅给标量值加引号,**描述文字逐字未变**(见下方校验证据)。

#### 8.8.1 已执行的改动

| 缺陷 | 处理 | 涉及文件 | 改动数 |
| --- | --- | --- | --- |
| **F-1** 逗号撕裂 | 给流式映射中的标量值**加双引号**(`description: "当前页,从 1 开始"`) | common / admin / internal | **16 处** |
| **F-2** Media Type 层 `description` 非法 | 删除该行,说明上移至**响应级 description**("application/json 为备用载体:非流式聚合返回…") | internal (`agent/chat` 200) | 1 处 |
| **F-3** `nullable` 无 `type` 兄弟字段 | **删除 `nullable: true`**,并在描述中补注"未声明 type,允许任意值(含 null)" | common (`ErrorItem.rejectedValue`) | 1 处 |
| **F-4** securitySchemes 未本地声明 | 在三份文件的 `components` 下**补声明** `securitySchemes`(内容与 common 一致,已加"与 common 保持同步"注释) | c(`cookieAuth`+`tempToken`)、admin(`cookieAuth`)、internal(`serviceToken`) | 3 处 |

> **F-1 未采用"改为块式写法"**:按裁决采用最小改动的加引号方案,避免大面积重排导致 diff 噪音过大、评审困难。**防复发建议见 8.8.4**。

#### 8.8.2 回归结果(修复后重跑)

| 校验器 | 修复前 | 修复后 |
| --- | --- | --- |
| swagger-parser(结构 + `$ref`) | 4/4 **FAIL**(common 64 / c 254 / admin 194 / internal 68 条违规) | **4/4 PASS** ✅ |
| redocly(结构 + 规则) | 4/4 有 error(合计 58 条) | **3/4 = 0 error**;common 仅剩 1 条**已知假阳** `no-empty-servers` |
| spectral(`spectral:oas`) | error:common 10 / admin 4 / internal 3 | **4/4 = 0 error** ✅ |
| 逗号撕裂残留扫描 | 16 处伪键 | **0 处** ✅ |
| 伪键全局扫描(4 份 × 全 AST) | 16 处非 ASCII 键 | **0 处** ✅ |

swagger-parser 修复后输出:

```
FILE: openapi-common.yaml    [PASS] validate   paths=0   schemas=38
FILE: openapi-c.yaml         [PASS] validate   paths=27  schemas=6
FILE: openapi-admin.yaml     [PASS] validate   paths=20  schemas=10
FILE: openapi-internal.yaml  [PASS] validate   paths=7   schemas=0
OVERALL: 全部 PASS
```

#### 8.8.3 语义未变动的验证证据

F-1 最隐蔽的危害是**描述被静默截断**。修复后逐字段核对,16 处描述的完整文本均已恢复,且所在节点**无任何伪键残留**:

```
描述完整且无伪键: 16 / 16        RESULT: ALL OK
  common :: PageData.page      description = '当前页,从 1 开始'
  common :: Sku.sku            description = '产品编号(sku_code,唯一)'
  common :: Sku.spec           description = '规格,如 750×1500mm'
  common :: PartSelection.area description = '该部位面积(㎡);可由服务端依户型计算,前端可传可不传'
  common :: QuoteItem.lossRate description = '损耗率,如 0.06'
  common :: UnitTemplate.*     description = '面积区间,例:80-100㎡' / '户型结构,例:三室两厅'
  common :: Dict.key/label/sort  description = '存储键(对外提交值),如 living_room' / '展示名,如 客厅' / '排序,升序'
  admin  :: ProductUpsert.sku  description = '产品编号(唯一,业务主键)'
  admin  :: DictUpsert.*       description = 同 Dict
  internal :: chat.userId      description = '已登录用户(由 Java 透传,用于鉴权与留痕)'
  internal :: callback.taskId  description = '对外唯一任务号,回调幂等键'

全局扫描(4 份契约全部 AST):
  openapi-common.yaml  : 0 处非 ASCII 键
  openapi-c.yaml       : 0 处非 ASCII 键
  openapi-admin.yaml   : 0 处非 ASCII 键
  openapi-internal.yaml: 0 处非 ASCII 键
```

复现:`python outputs/scripts/verify_descriptions.py`

#### 8.8.4 T-1~T-15 修复后终态

| # | 修复前 | 修复后 |
| --- | --- | --- |
| T-1 | ✅ | ✅ |
| T-2 | ✅ | ✅ |
| T-3 | ✅ | ✅ |
| T-4 | ✅ | ✅ |
| **T-5** | ❌ | ✅ **(F-4 修复)** |
| T-6 | ✅ | ✅ |
| T-7 | ✅ | ✅ |
| T-8 | ✅ | ✅ |
| T-9 | ✅ | ✅ |
| T-10 | ✅ | ✅ |
| T-11 | ✅ | ✅ |
| **T-12** | ⚠️不可执行 | ⚠️**仍不可执行**(无实现代码,非契约缺陷) |
| T-13 | ✅ | ✅ |
| T-14 | ✅ | ✅ |
| T-15 | ✅(静态) | ✅(静态) |

**终态:通过 14 · 不可执行 1(T-12) · 失败 0。**

> **2026-10-01 补注(上表 T-12 单元格刻意不改)**:T-12 已由"仍不可执行"转为**通过** ——
> 实现侧比对落地,`check_t.py` 报 **PASS=15 / FAIL=0 / SKIP=0**(T-12 曾是唯一的那个 SKIP)。
> 口径与裁决见 **§40**。

#### 8.8.5 修复后仍挂起的事项

| # | 事项 | 性质 | 建议 |
| --- | --- | --- | --- |
| R-1 | `openapi-common.yaml` 的 redocly `no-empty-servers` | **假阳**(纯组件库,`paths: {}`,按设计无需 servers) | 在 redocly 配置中对该文件关闭该规则;或忽略 |
| R-2 | `openapi-common.yaml` 27 条 `no-unused-components` | **假阳**(组件被其它 3 份文件引用,校验器只看单文件) | 关闭该规则,或改为 bundle 后校验 |
| R-3 | `operation-operationId` / `operation-4xx-response` / `info-license` / `info-contact` 等 warning | **风格建议**,非 §5 要求 | 若后续要由契约生成 SDK,再统一补齐 |
| R-4 | T-12 双向 diff | **前置条件未满足**(无实现) | 阶段 0 产出 Java/Python 骨架后执行 |
| R-5 | T-15 的"enum ⊆ `/dicts` 实际返回集" | 需**运行时数据** | 纳入接口契约测试(启动后拉取 `/dicts` 对比) |
| R-6 | F-4 的三处 `securitySchemes` **重复定义** | 本次裁决接受的代价 | 如需消除重复,可改用 bundle 方案(§8.4 F-4 方案 B);**改动 common 里的 scheme 时须同步改 3 处** |
| R-7 | **建议新增 T-16:AST 键合法性断言** | §5 现有 15 项**均检测不到 F-1 这类缺陷** | 断言"解析后所有 mapping 的键必须匹配 OpenAPI 关键字白名单",纳入 CI |

#### 8.8.6 复现全部结论

```powershell
# 结构校验(期望 4/4 PASS)
node  outputs/scripts/validate-refs.js

# T-1~T-15 断言(期望 14 PASS / 1 不可执行)
python outputs/scripts/check_t.py

# 描述完整性与伪键扫描(期望 16/16 OK,0 伪键)
python outputs/scripts/verify_descriptions.py

# lint(期望 redocly 仅 common 剩 1 条已知假阳;spectral 全 0)
powershell -File outputs/scripts/run-lint.ps1
```

留档:`outputs/reports/`(修复前的 redocly-*.json 已被修复后结果覆盖,如需修复前基线请查阅本节 §8.4 的表格记录)。

---

## 9. D-13 关闭:MQ 消息契约(AsyncAPI)

### 9.1 背景

§4 待确认项 **D-13** 与 §7.2 挂起项均记录:

> MQ 消息规范(queue.generate / queue.index.sync / DLQ)无上游可依,
> 建议另出 AsyncAPI 或消息规范文档跟踪。

架构 §6.2 / §6.4 把**可靠性机制**写得很细(Outbox 模式、手动 ACK、指数退避重试、
死信队列、以 taskId 幂等、补偿扫描),但**未定义消息体结构**;§15.2 亦把
"定义 `index.sync` 事件结构"列入**阶段 0「先定」清单**。

这是本项目中**唯一"输入缺失"的硬缺口** —— 其余待办(docs 缺口 G-2/G-3/G-6 等)
均可在别处找到答案,而 MQ 消息体无任何权威来源。

### 9.2 产出

新增契约 **`asyncapi-internal.yaml`**(AsyncAPI **3.1.0**),与 4 份 OpenAPI 契约同级。

| 通道 | 地址 | 方向 | 依据 |
| --- | --- | --- | --- |
| 生成任务派发 | `queue.generate` | Java → Python | 架构 §6.2、§11.2 |
| 向量索引同步 | `queue.index.sync` | Java → Python | 架构 §6.2、§9、§15.2 |
| 生成任务死信 | `queue.generate.dlq` | 代理 → 人工/脚本 | 架构 §6.4 |
| 索引同步死信 | `queue.index.sync.dlq` | 代理 → 人工/脚本 | 架构 §6.4 |

消息体:`GenerateTaskPayload`、`IndexSyncPayload`、`DlqPayload`。

**结果回传不经 MQ**:Python 完成出图后走 **HTTP 回调**
`POST /internal/generate/callback`(已在 `openapi-internal.yaml` 中定义),
故本契约只覆盖**单向派发**。

### 9.3 关键设计决策

1. **复用 `openapi-common.yaml` 的 schema,不重复定义**。
   `Part` / `TaskType` / `SpaceType` / `StyleType` / `LayoutModel` / `PartSelection` /
   `ErrorCode` 均通过跨文件 `$ref` 引用(如
   `./openapi-common.yaml#/components/schemas/Part`)。
   **已实测验证** `@asyncapi/parser` 3.6.3 可正确解析并取回目标 schema ——
   这避免了重演 **F-4** 式"同一枚举存在多份真源"的问题。

2. **方向标注**。本契约描述**两个服务之间的共享契约**,而非单一应用的接口。
   沿用 `openapi-internal.yaml` 的约定,每个 operation 标注
   `x-producer` / `x-consumer`。

3. **消息体与协议无关**。契约编制时 §15.9 尚未裁决 RabbitMQ 与 Redis Stream(P0-3),
   故 `servers` 与 `channel.bindings` 按 AMQP 书写,**但消息体不受选型影响** ——
   改选协议时只需调整这两处。
   > **后续(2026-09-29)**:P0-3① 已裁决一期采用 **RabbitMQ**(见 §9.5 R-8、
   > 架构 §15.9),上述 AMQP 写法据此成为**定稿**,`servers.broker` 的 `x-todo` 已移除。
   > 本条的历史表述保留原貌。

4. **DLQ 粒度**:架构仅提"死信队列(DLQ)"未指明粒度,本契约按**每源队列一个 DLQ**
   (AMQP 惯例)拆分,已标 `x-todo` 待确认。

5. **可靠性要求写入契约描述**:手动 ACK、幂等去重键、Outbox 投递、指数退避等
   要求与消息体**同等重要**,故在 `info.description` 与各 operation 描述中明确。

### 9.4 校验(G7 门禁)

新增校验脚本 `outputs/scripts/validate-asyncapi.js`,接入 `ci.sh` 作为 **G7** 门禁。
分三层:

| 层 | 内容 | 为什么需要 |
| --- | --- | --- |
| ① 结构合法性 | AsyncAPI 3.x 规范校验 | 基础 |
| ② 跨文件 `$ref` 解析 | 确认能取回 `openapi-common.yaml` 的真实 schema | 防止"解析通过但引用没落地" |
| ③ **语义断言** | 幂等键必填、方向标注齐全、队列齐备、DLQ 枚举覆盖、`parts.minItems=1` | **文档合法 ≠ 契约完整** |

**第 ③ 层经负向验证证明是必要的**:

| 注入的缺陷 | ① 结构 | ③ 语义 | 退出码 |
| --- | --- | --- | --- |
| 从 `GenerateTaskPayload.required` 移除 `taskId` | ✓ **仍然合法** | ✗ 捕获 | 1 |
| `asyncapi` 版本改为 `9.9.9` | ✗ 捕获 | — | 1 |

第一行是关键:删掉幂等键后,文档**依然通过结构校验** —— 若只有前两层,
这个会让 Java 与 Python 的幂等实现失去契约依据,而 CI 仍显示绿色。

#### 9.4.1 新增契约暴露的覆盖盲区(已修复)

引入 `asyncapi-internal.yaml` 后做回归验证时发现:**该文件被原有的检测能力完全漏掉**。

向 AsyncAPI 契约注入一处逗号撕裂(`description: 单价,元/㎡`)后,门禁反应如下:

| 门禁 | 修复前 | 原因 |
| --- | --- | --- |
| **G4 逗号撕裂** | ❌ **扫不到** | 文件清单是**硬编码的 4 份 OpenAPI**,不含 AsyncAPI |
| **G5 描述完整性** | ❌ **扫不到** | 同上 |
| **G7 AsyncAPI** | ❌ **显示"结构合法"通过** | AsyncAPI 解析器**不校验 schema 对象的键白名单** —— 这与 redocly 的 `struct` 规则不同,异物键不会被拦 |

**即:同一类曾导致 16 处缺陷的漏洞,在新契约中当时完全不可见。**

修复:

1. `detect_corruption.py` / `verify_descriptions.py` 的文件清单**由硬编码改为 `glob`**
   (`openapi-*.yaml` + `asyncapi-*.yaml`)。今后新增任何契约文件自动纳入,
   同类问题不会重演。
2. `.githooks/pre-commit` 的触发条件同步扩展为 `^(openapi|asyncapi)-.*\.yaml$`。

修复后重测:同一注入被 G4 与 G5 捕获,退出码 1。回滚后 7 个门禁全部通过。

> **本次修复自身也暴露了一个假阳**:检测器以"键含非 ASCII 字符"为撕裂特征,
> 而新增的 `x-not-covered` 段误用了中文键(`项:` / `说明:`)。
> 该写法同时**违反了本仓库"对外键一律英文"的既有约定**(参见 `x-todo` /
> `x-source` / `x-direction`),已改回 `item` / `note`。
> 检测器的启发式保持不变 —— 它在此前抓出了全部 16 处真实缺陷,代价可接受。

### 9.5 仍未闭合

| # | 事项 | 性质 |
| --- | --- | --- |
| ~~R-8~~ | ~~**P0-3:MQ 选型**(RabbitMQ vs Redis Stream)~~ | ✅ **已闭合(2026-09-29)**:一期采用 **RabbitMQ**。已回填架构 §3 技术选型表与 §15.9,并移除 `asyncapi-internal.yaml` `servers.broker` 的 `x-todo`。**消息体未变**,本次改动不涉及任何 schema 或 channel。 |
| R-9 | DLQ 粒度(每队列一个 vs 全局一个) | 推断项,已标 `x-todo` 待确认 |
| R-10 | 对话消息异步落库(架构 §6.2 称"**可选**") | 未纳入契约;若实现阶段采用,需补 `queue.chat.persist` |
| R-11 | 二期进度回传的 MQ 路径(架构 §6.3) | 一期经 HTTP 回调即可,二期再补 |

> **未回改架构文档**:架构 §10.4 列出了接口契约清单但未提及 AsyncAPI。
> 本契约属**新增**而非变更既有接口,且架构 §7(D-13)本就建议另出 AsyncAPI。
> 是否在架构 §10.4 补一行指引,留待上游决定。

---

## 10. 上游补充:CSRF/SameSite 与 t_import_batch(2026-09-29)

实现 T-0.1 ~ T-0.4 的过程中暴露两处**上游未定义**,按
"PRD → 架构 → 契约 → 本文件"的顺序补充完毕。
**两项均未改变任何既有接口语义,未增删任何字段。**

### 10.1 两处缺口

| # | 缺口 | 暴露于 | 处置 |
| --- | --- | --- | --- |
| A | Cookie 鉴权的 **CSRF / SameSite** 策略未定义 —— 架构 §13.5 原只写到"鉴权 JWT、密码 BCrypt、隐私数据加密、HTTPS",未涉及 CSRF | T-0.4 | 补入架构 §13.5 |
| B | **`t_import_batch` 表未定义**(缺口 **G-2**)—— 契约有 `GET /admin/products/import/{batchId}`,架构 §8 却无承载表 | T-0.2 / T-0.7 | 补入架构 §8.7 |

### 10.2 架构改动(v1.2 → v1.3)

| 位置 | 改动 |
| --- | --- |
| 文档头 | 版本 → **v1.3**,新增"修订记录"表 |
| §13.5 | 新增 CSRF 防护条目:**`SameSite=Lax`** + 前后端同源部署(§12.1);一期不引入 CSRF token;并写明该方案**唯一的失效条件**是前端改为跨站部署 |
| §8.7 | 新增 **`t_import_batch`** 表定义(12 字段),声明其与 `ImportStatus` 一一对应 |
| §3 / §15.9 | (同日早先)**P0-3①** 裁决 MQ 选型为 RabbitMQ,见 §9.5 R-8 |

> 三者均为**新增说明**,不修改任何既有条目。

### 10.3 契约改动(4 处)

| 文件 | 位置 | 改动 |
| --- | --- | --- |
| `openapi-common.yaml` | `securitySchemes.cookieAuth` | description 增补 `HttpOnly` / `SameSite=Lax` / `Secure` 说明 |
| `openapi-c.yaml` | `securitySchemes.cookieAuth` | **同步**(F-4:`security` 无法跨文档 `$ref`,各文件须自声明) |
| `openapi-admin.yaml` | `securitySchemes.cookieAuth` | **同步** |
| `openapi-admin.yaml` | `schemas.ImportStatus` | description 指向架构 §8.7 的 `t_import_batch`;**移除 `x-todo`**(其前提"字段结构未定义"已不成立) |

> `openapi-internal.yaml` **未同步** —— 其 `securitySchemes` 只声明 `serviceToken`,
> 不含 `cookieAuth`,不受 F-4 影响。

**`x-todo` 计数**:24 → 23(P0-3① 移除 `servers.broker`)→ **22**(本节移除 `ImportStatus`)。
剩余 22 处均未被本轮改动触及。

### 10.4 契约校验

```bash
bash outputs/scripts/ci.sh
```

**7 门禁全部 PASS,退出码 0**。G1 结构 4/4、G2 redocly **0 error**、G3 spectral **0 error**、
G4 逗号撕裂 0 可疑键、G5 描述完整性 16/16、G6 T-1~T-15(PASS 14 / SKIP 1,T-12)、G7 AsyncAPI。

> 核对 G3 时的注意点:其日志中"error"一词出现在汇总行 `0 errors` 与告警文案里,
> 直接 `grep error` 会误判为失败。判据是 **ci.sh 的退出码**,不是关键词。

### 10.5 实现侧回归

新增迁移 `V7__import_batch.sql`,并在 `SchemaMigrationTest` 期望 Schema 中加入该表。

> ⚠️ **V1–V6 一个字都没有改动。** Flyway 为每个迁移记录校验和,回改已应用的迁移会导致
> `checksum mismatch`、应用无法启动。新表一律**以新版本号追加**。

实测:

```
Migrating schema `tile_ai` to version "7 - import batch"
Successfully applied 1 migration to schema `tile_ai`, now at version v7
[T-0.2] 库=tile_ai  起始为空库=false  本次执行迁移数=1  成功=true
```

**无校验和错误**,V7 在既有 V1–V6 之上干净叠加。

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true"
```

**56 个测试,0 失败 0 错误 0 跳过。** `SchemaMigrationTest` 已断言到 §8.7
(21 → **22 张表**),`ErrorCodeContractTest`、`ApiResponseContractTest`、
四项安全测试全绿 —— **已完成的 T-0.1 ~ T-0.4 无一处被破坏**。

### 10.6 仍未闭合

- **CSRF 方案的失效条件**已写入架构 §13.5:前端若改**跨站**部署,`SameSite` 必须放宽,
  CSRF 防护随之失效,届时须补 CSRF token 机制(涉及契约新增端点,需另走一轮上游变更)。
- `t_import_batch` 的**实现**(模板下载、上传、逐行校验、错误报告、批次查询)属 **T-0.7**;
  本轮只补了**定义与建表**。

---

## 11. RBAC 权限归属澄清(2026-09-29)

做 T-0.5(RBAC 与后台登录)时发现权限归属存在**三方互相矛盾**,无法据此实现。
本节记录裁决与随之而来的上游改动。

### 11.1 冲突:`quote-param:write` 归谁

| 来源 | 原文 | 含义 |
| --- | --- | --- |
| 架构 §4.4(权威顺序高于契约) | operator「…可查看操作日志;**不可管理账号与报价参数**」 | operator **不可** |
| 契约 `openapi-admin.yaml` | 「权限点 quote-param:write(仅 **super_admin/operator**,editor 不可)」 | operator **可** |
| `tile-ai-tasks.md` T-0.5 验证判据 | 「operator 调 PUT /admin/quote-params 应**成功**(§4.4 允许)」 | operator **可**,但**引用错了 §4.4 原文** |

**根因**:§4.4 只有散文式描述,**没有「权限点 → 角色」的显式对照表**。契约逐接口标注了
"需要哪个权限点",却没有(也不该有)"哪个角色有该权限"—— 后者本应只在 §4.4 定义。
两处都没写死,于是各自推断,推反了。

### 11.2 裁决(用户 2026-09-29 裁定)

| 项 | 结论 | 理由 |
| --- | --- | --- |
| `quote-param:read` / `quote-param:write` | **仅 super_admin**(operator 连读都不行) | 直接决定**所有**报价金额,与"账号管理"同属高影响面配置;§4.4 把两者并列即此意 |
| `dict:read` | 三角色均可 | 字典是运营日常维护项(§5.7) |
| `dict:write` | super_admin + operator | 同上;editor 只读 —— 字典是跨模块引用的全局枚举 |
| `product:import` | **仅 operator** | upsert 模式能**成批覆盖**既有 SKU,影响面远大于单条编辑 |

### 11.3 上游改动

| 文件 | 改动 |
| --- | --- |
| `tile-ai-architecture.md` | **v1.3 → v1.4**;§4.4 新增 **22 行「权限点 → 角色」对照表**,并注明它是**唯一权威来源**;角色描述补上"字典" |
| `openapi-admin.yaml` | `quote-param:write` 收窄为「**仅 super_admin**」,并说明 read 同样仅 super_admin |
| `tile-ai-tasks.md` | T-0.5 验证判据重写为逐条对照新表,并注明原判据引用有误 |

> 契约只改了这**一处**;其余 21 个权限点的标注未动。§4.4 的改动是**新增表格 + 补充描述**,
> 不修改任何既有条目。

### 11.4 校验

```bash
bash outputs/scripts/ci.sh      # 7 门禁全部 PASS,退出码 0
```

### 11.5 实现侧

新增 RBAC 机制(`com.tileai.admin.rbac` 等)与后台登录接口
(`POST /api/v1/admin/auth/login`),并写了**把代码绑回文档**的测试:

| 测试 | 绑定对象 | 需外部依赖 |
| --- | --- | --- |
| `PermissionContractTest` | `Permission` 枚举 ↔ 契约里所有"权限点 xxx"标注 | 否 |
| `AdminRoleMatrixTest` | `AdminRole` 权限集合 ↔ **解析架构 §4.4 的 Markdown 表格**逐格比对 | 否 |
| `AdminRbacIntegrationTest` | 真实 HTTP 结果 ↔ §4.4 矩阵 | MySQL |
| `AdminAuthIntegrationTest` | 登录接口行为(下发 Cookie、令牌不进响应体、失败统一 20005) | MySQL |

后两者不做成"没库就 SKIP";用 `-Ddb.verify=true` 显式启用。

实测:

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true"
```

**86 个测试,0 失败 0 错误 0 跳过。**

### 11.6 过程中发现的一处回归(已修复)

引入 `AdminPermissionInterceptor`(`@Component`)后,`ApiResponseContractTest`
**11 个测试全部 ERROR** —— `@WebMvcTest` 的类型过滤器**包含 HandlerInterceptor**,
把该拦截器拉进了切片上下文,而它依赖访问数据库的 `AdminAccountService`,切片里没有数据源。

> **教训**:切片测试(`@WebMvcTest`)会连带拉起 Web 层的拦截器/过滤器。
> 新增这类组件时,既有的切片测试可能集体失效 —— 它们报的是"上下文起不来",
> 而不是"断言失败",容易被误读成环境问题。
>
> 修法:在该切片里对 `AdminAccountService` 打桩(`@MockitoBean`),
> 因为它不在 `/api/v1/admin/**` 下,拦截器实际不执行。

### 11.7 仍未闭合

- editor 是否应拥有**只读**的户型模板 / 陈设 / 门店权限(§4.4 未授予,当前实现为无)。
  若运营侧有实际诉求,需回改 §4.4。
- 操作日志采用"**成功后才记、记失败不回滚业务**"的取舍(见 `AdminAuditAspect` 类注释)。
  严格一致需把审计写进与业务**同一事务**,属 T-2.x 加固范围。

---

## 12. T-0.6 产品库 CRUD:一处判据降级说明(2026-09-29)

### 12.1 T-0.6 的判据与实际的差距

任务清单 T-0.6 的验证判据第一条是:

> 实现**导出的 OpenAPI** 与 `openapi-admin.yaml` 对应片段结构一致

这需要 **T-12** 的导出-比对基础设施(Java 侧用 springdoc 导出 `/v3/api-docs`,
再与契约做双向结构 diff)。而任务清单自己把 T-12 排在 **T-2.1**,依赖条件是
"各实现模块基本就位" —— 此刻产品库之外的模块尚未开工。

**本轮的处理**:不实现 OpenAPI 导出,改为**字段级契约绑定测试**
(`ProductContractTest`):用 Jackson 内省取 DTO 的<b>实际 JSON 字段名</b>,
与契约 schema 的 `properties` 逐字段比对。

| | 覆盖 | 不覆盖 |
| --- | --- | --- |
| 字段级绑定(已做) | 字段名拼错、漏字段、多字段、必填项缺失 | HTTP 方法 / 路径 / 状态码 / 响应包装层 |
| 完整导出 diff(T-2.1) | 上述全部 | — |

> **这是一处判据降级,不是等价替代。** 记录在此,以免日后误以为 T-0.6 的
> "契约一致性"已被完整验证。T-2.1 落地时应对产品库这几个接口补做完整 diff。

### 12.2 契约留白:DELETE 的 30002 何时返回

契约给 `DELETE /admin/products/{id}` 列了失败码 `30001、30002`,但<b>没说什么状态会不允许删除</b>。

采用的口径:**上架中的 SKU 不可删除,须先下架**。理由:§10.5 对 30002 的定义是
"**状态**不允许该操作",举例是"对同款砖重复上下架" —— 是状态类冲突。

至于"被 `t_recommend_combo_item` 引用的 SKU 不可删除",**本轮未实现**:
搭配模块是 T-0.8,顺序上 T-0.6 在前,此刻库中不可能存在引用。
届时 T-0.8 应补上该校验,否则会留下悬挂引用。

### 12.3 校验

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true"
```

**109 个测试,0 失败 0 错误 0 跳过**(T-0.5 时为 86)。

新增两个测试类:

| 测试 | 覆盖 | 需要外部依赖 |
| --- | --- | --- |
| `ProductContractTest`(6 条) | DTO 字段 ↔ 契约 schema(含 `images[]` 嵌套、PageData 必填项) | 否 |
| `AdminProductCrudIntegrationTest`(17 条) | 增删改查全流程、分页、筛选、图片替换、30001/30002 | MySQL |

其中**分页那条**针对一个不报错的坑:`MybatisPlusConfig` 若漏了分页插件,
`selectPage` 会把全表当一页返回 —— 不抛异常,只是 `size` 形同虚设。
测试造 5 条、请求 `size=2`,断言"只返回 2 条但 total=5"。

### 12.4 写测试时踩到的两个坑(供后来者参考)

1. **`ObjectNode.put(String, BigDecimal)` 会丢小数位** —— Jackson 的 `JsonNodeFactory`
   默认对 BigDecimal 做 `stripTrailingZeros`,`268.00` 写入 JSON 后变成 `268.0`。
   断言金额应比**数值**(`compareTo == 0`)而不是比字符串。契约对金额只声明
   `type: number`,并未规定小数位数。
2. **契约 schema 的字段在 `properties` 之下** —— schema 顶层的键是
   `type / description / required / properties`。直接在顶层取字段名会得到 null,
   报错也会指向别处。

---

## 13. T-0.7 产品库批量导入(2026-09-29)

### 13.1 实现范围与其依赖的降级

T-0.7 的依赖写作 `T-0.6、P0-4(产品库数据盘点)`。**P0-4 尚未完成**(需真实的现有产品库数据),
故本轮按约定只做**机制**:模板下载、上传、逐行校验、错误报告、批次查询、两种导入模式、
异步处理 —— 用**自造样本**验证。**"真实数据能否导入"仍待 P0-4**。

### 13.2 一个值得记下的缺陷:MyBatis-Plus 的 typeHandler 不作用于 UpdateWrapper

导入功能首次跑通时,7 个集成测试全部卡在 15 秒超时。根因:

```
java.io.NotSerializableException: com.tileai.product.imports.ImportRowError
```

`t_import_batch.errors` 是 JSON 列,靠 `@TableField(typeHandler = JacksonTypeHandler.class)`
做 `List<ImportRowError>` ↔ JSON 的转换。**该 typeHandler 只在"实体字段"参与 SQL 时生效**;
用 `LambdaUpdateWrapper.set(...)` 传值时会绕过它,把 `List` 原样交给 JDBC 驱动。

**真正致命的是它的次生效应**:失败路径上同样要写 `errors`,所以**连"把批次标记为
failed"都失败了**,异常在异步线程里抛出后无人接管,批次永远停在 `running`,
调用方无限轮询、无从判断发生了什么。

两条修复:
1. 状态更新一律走**实体更新**(`updateById`),让 typeHandler 生效;
2. 失败路径用 `safeUpdateBatch` 包一层 —— 更新状态本身再失败时至少留日志,
   不让异步线程被抛死。

> **通用教训**:MyBatis-Plus 的 `typeHandler` 与 `UpdateWrapper`/`LambdaUpdateWrapper`
> 的 `.set()` **不兼容**。凡是有自定义 typeHandler 的列(JSON、加密字段等),
> 更新时都要走实体。

### 13.3 三处能力边界(已知且有意)

| # | 边界 | 说明 |
| --- | --- | --- |
| A | **`errorReportUrl` 恒为 null** | 错误报告文件需上传对象存储,而**对象存储选型是 P0-4**。当前错误明细由 `errors[]` 直接返回,完整报告待 P0-4 后补。 |
| B | **字典为空时跳过 space/style 校验** | 字典由 T-0.11 维护,上线初期可能为空。空字典上做严格校验会导致**全行失败**,而运营此时还没有地方去配字典。字典一旦配好,校验立即生效。 |
| C | **material / surface 不做枚举校验** | §8.7 提到校验"材质/风格/光泽",但 `t_dict` 目前只定义 `space` / `style` 两类,**材质与光泽没有权威取值来源**。硬编码一套枚举会违反 D-14(不硬编码),故暂不校验。若需要,应先在架构 §8.5 明确 `t_dict` 的材质/光泽类型。 |

### 13.4 校验

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true"
```

**133 个测试,0 失败 0 错误 0 跳过**(T-0.6 时为 109)。

新增两个测试类:

| 测试 | 覆盖 | 需要外部依赖 |
| --- | --- | --- |
| `SpreadsheetReaderTest`(14 条) | 模板往返、GBK/UTF-8 BOM 编码、全角表头、行号保持、引号字段、格式按内容判定 | 否 |
| `AdminProductImportIntegrationTest`(10 条) | 错误行不写入 + **行号准确**、修正后重传、upsert 不重复、insert_only 拒绝覆盖、重复导入幂等、模板往返导入 | MySQL |

对应 T-0.7 的三条判据均有直接断言:

- 「错误行不写入、错误报告行号准确、修正后重传可成功」→ `badRowsAreReportedWithAccurateLineNumbers`
  (断言报错行号为 3 与 5,即文件真实行号)+ `fixedFileCanBeReimported`;
- 「以 sku_code 为业务主键的 upsert 不产生重复数据」→ `upsertUpdatesInsteadOfDuplicating`
  (断言 id 不变,证明是更新而非删了重建);
- 「同一文件重复导入结果幂等」→ `repeatedImportIsIdempotent`。

### 13.5 写测试时的一个反复踩到的坑

测试里**手写逗号分隔的 CSV 行** —— 逗号数错一个,整行列即错位,
症状是"某字段莫名等于 268.00",看着像解析器有 bug。**连踩两次后改为按
`ImportColumn` 枚举拼行**,对齐由代码保证。后续涉及 CSV 的测试都应沿用该写法。

---

## 14. T-0.8 推荐搭配维护(2026-09-29)

### 14.1 关闭 T-0.6 遗留的判据

T-0.6 曾把"删除被搭配引用的 SKU"这一条**主动推迟**——理由是该轮搭配模块尚未建立,
库中不可能存在引用,顺序上安全(见 §12.2)。T-0.8 建立起搭配模块后,该检查已补上:

```
DELETE /api/v1/admin/products/{id}
  ├─ 不存在              → 30001
  ├─ 处于 on_sale        → 30002(须先下架)
  └─ 被搭配引用          → 30002(须先从搭配中移除)
```

**为什么硬删必须挡住**:`t_recommend_combo_item` 存的是 `product_id`,
硬删会留下指向不存在产品的悬挂引用 —— 前台展示时只能显示空白,属静默的数据损坏。

**为什么历史方案不受此限**:`t_plan_part` 里的 `sku_code` / `product_name` /
`price_per_sqm` 是**快照**(§8.4),历史方案本就该保留当时的瓷砖信息,
与产品是否还在售无关。所以方案引用**不**阻止删除 SKU。

### 14.2 两处映射差异

| 库内 | 契约对外 | 处理 |
| --- | --- | --- |
| `t_recommend_combo_item.product_id` | `ComboItem.sku` | 写入时按编号解析、读取时反查编号 |
| 该表**没有 name 列** | `ComboItem.name`(冗余展示名) | 读取时从 `t_product` 带出 |

对照表里没有 `name` 字段 —— 契约也把它标为"可选"。若日后需要展示名脱离产品库独立维护,
要先回改架构 §8.2 加列。

### 14.3 校验

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true"
```

**151 个测试,0 失败 0 错误 0 跳过**(T-0.7 时为 133)。

| 测试 | 覆盖 | 需要外部依赖 |
| --- | --- | --- |
| `ComboContractTest`(6 条) | 四个 schema 的字段绑定 + **T-9 部位三值**(从 `@Pattern` 注解取正则与 `Part` 枚举比对)+ `items.minItems=1` | 否 |
| `AdminComboIntegrationTest`(12 条) | 增删改查、三种部位、非法部位、空 items、引用不存在的 SKU、列表过滤与分页、级联删除、**被引用 SKU 不可删** | MySQL |

两条判据均有直接断言:

- 「items 含 sku + part,Part 恒为三值」→ `createThenReadBackWithItems` +
  `allThreePartsAreAccepted` + `invalidPartIsRejected`;
- 「删除被引用的 SKU 行为符合预期」→ `referencedSkuCannotBeDeleted`
  (断言返回 30002、数据未被删、解除引用后可删)。

---

## 15. T-0.9 户型模板维护(2026-09-29)

### 15.1 闭合上游空白:`areas` 的推导公式

实施前发现:**`areas`(各部位毛面积)的推导公式在上游是空白**。
架构只定义了「有效面积 = 毛面积 − 开口投影」(§8.8),**没写毛面积本身怎么来**;
而 §8.3 的示例又与最常见的公式对不上 ——

| 量 | 公式 | 值 | §8.3 示例 |
| --- | --- | --- | --- |
| `floor` | 长×宽 = 2.0×1.8 | 3.6 | **3.6** ✓ 吻合 |
| `wall` | 周长×层高 = 2×(2.0+1.8)×2.7 | 20.52 | **13.9** ✗ 不符 |

`floor` 用的是标准公式,`wall` 却推不出唯一解。该口径会**直接改变报价金额**,
故请示后裁定为:

```
floor = length × width
wall  = 2 × (length + width) × height      # 周长 × 层高
local = 0
```

已写入架构 **§8.3(v1.5)**。要点:

- **只补不覆盖** —— 调用方显式给出 `areas` 时原样采用,公式不参与;
- **`wallCount` 不参与公式** —— 它是独立的记录字段,不改变"周长×层高"的口径;
- **算的是毛面积** —— 扣开口得到有效面积属 §8.8 的报价环节;
- 面积保留 **4 位小数**,与 §8.4 的 `DECIMAL(10,4)` 一致 —— 面积是乘法的输入,
  提前四舍五入到 2 位会在"面积 × 单价"里被放大成金额误差。

### 15.2 关闭契约的 `x-todo`

契约 `LayoutSpace.areas` 上挂着一个 `x-todo`:

> `areas 究竟是录入入参还是服务端计算结果,§8.3 未说明`

15.1 的裁决正好回答了它,该 `x-todo` 已移除,description 改写为明确口径。
**`x-todo` 计数 22 → 21。**

### 15.3 契约补两个筛选参数

PRD §5.3.1 明确要求「按 **房间数 + 面积区间 + 户型结构** 筛选模板」,
而契约 `GET /admin/templates` 只声明了 `roomCount`。按权威顺序(PRD > 契约),
已补上 `areaRange` 与 `structure` 两个可选参数,并注明精确匹配。

### 15.4 修正一处自己埋的默认值缺陷(迁移 V8)

契约里 `UnitTemplate.status` 与 `FurnishStyleUpsert.status` 都引用 `ProductStatus`
(`on_sale` / `off_sale`),而 V3 / V5 建表时架构尚未规定取值、当时按惯例写了
`enabled`。列类型本来就能容纳两种取值,但**默认值不对**:任何一条没显式给 status 的
INSERT,数据库都会写入一个契约里不存在的状态值,**而且不报错**。

新增迁移 `V8__align_status_defaults.sql` 把两列的默认值与注释对齐为 `off_sale`。
⚠️ 未回头修改 V3 / V5 —— 已应用的迁移改动会导致校验和失配。

### 15.5 一条只有真实 schema 校验才能发现的缺陷

T-0.9 的判据要求「存入的 layout **用 LayoutModel schema 校验通过**」——
与其他任务不同,这是**字面意义**的 JSON Schema 校验。因此引入了
`com.networknt:json-schema-validator`(**仅测试作用域**),
把契约里的 `LayoutModel`(连同其引用的 `LayoutSpace` / `Opening` / `LayoutAreas` / 枚举)
抽出、内联 `$ref` 后,校验**从数据库读回的那份真实 JSON**。

它当场抓出一个缺陷:

```
$.spaces[0].wallCount: 找不到 null,期望 integer
$.spaces[1].openings:  找不到 null,期望 array
```

可选字段为 null 时,Jackson 默认序列化成 **显式 `null`**,而契约把它们声明为
`integer` / `array` 且**未标注 `nullable`** —— 显式 null 被判为类型不符。
加 `@JsonInclude(NON_NULL)` 后改为**整个省略**,校验通过。

> **这正是"字段名比对"与"真实 schema 校验"的差别所在。**
> 前四轮用的字段集合比对**不可能发现它** —— 字段名一个不差,
> 差别在"值为 null 时该省略还是该输出 null"。
> T-0.6 / T-0.8 的同类判据用的是字段比对(见 §12.1 的降级说明),
> 若日后统一改用真实 schema 校验,很可能还会翻出同类问题。

### 15.6 校验

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true"
```

**181 个测试,0 失败 0 错误 0 跳过**(T-0.8 时为 151)。

| 测试 | 覆盖 | 需要外部依赖 |
| --- | --- | --- |
| `LayoutAreaCalculatorTest`(10 条) | 公式逐项、wallCount 不参与、开口不减毛面积、**只补不覆盖**、4 位小数 | 否 |
| `LayoutModelSchemaTest`(7 条) | **用契约 schema 校验实际产物** + 4 条负向对照(缺必填/空数组/非法枚举/类型不符),证明校验不是空转 | 否 |
| `AdminTemplateIntegrationTest`(13 条) | 面积推导与保留、**校验从库里读回的真实 JSON**、嵌套校验的错误路径、三个筛选、明文边界(T-8)、增删改 | MySQL |

> 负向对照是刻意加的:一个永远通过的 schema 校验测试毫无价值。
> 缺 `length`、空 `spaces`、非法开口类型、类型不符四例都必须**被拦下**。

---

## 16. T-0.10 / T-0.11 / T-0.12 并行完成(2026-09-29)

### 16.1 为什么可以并行

三个任务**只依赖已完成的 T-0.3**,且改的是完全不同的表与模块,互不重叠:

| 任务 | 表 | 模块 | 规模 |
| --- | --- | --- | --- |
| T-0.10 陈设风格 | `t_furnish_style` | `com.tileai.furnish` | S |
| T-0.11 报价参数 + 字典 | `t_quote_param` / `t_dict` | `com.tileai.quote` / `com.tileai.admin` | S |
| T-0.12 门店 | `t_store` | `com.tileai.store` | S |

**未采用子代理并行**,而是同轮实现、统一验证:三者共用同一套约定(错误码口径、
测试模式、文档先行),子代理要么需逐个重度交接、要么会风格分歧;而各自有独立测试类,
一次 `mvn test` 仍能逐任务定位失败。

**T-0.13 不能并行**(也未开工):它需要 Qdrant 与 RabbitMQ 两个容器,目前都没起。

### 16.2 一处契约与其余列表接口不同的地方

`/furnish`、`/quote-params`、`/dicts` 三个列表接口的 `data` 都是
**`{ list: [...] }` 而<b>不带</b> `total/page/size`** —— 与 `/products`、`/combos`、
`/templates` 的分页结构不同。契约并未声明 `page`/`size` 参数。

因此新增了 `ListData<T>` 与 `PageData<T>` 并存,**没有**复用后者把分页字段填 0 ——
那样前端无法区分"真的只有 0 条"与"这个接口不分页"。契约校验表 T-3 对本轮三个接口不适用。

### 16.3 两条判据的落实方式

**T-0.11:量纲(D-19 / D-15)** —— 量纲校验挂在参数键枚举上
(`QuoteParamKey.validate`),因为三种键的量纲不同:辅料单价是**元/㎡**,
两个损耗率是**小数**。拦的是这类错误:把 6% 写成 `6` 而非 `0.06`。
数据库收得下、接口返回成功、页面看起来正常,**只有所有报价金额悄悄偏 100 倍** ——
对账时才发现且极难定位。故写入侧直接拒绝,并在错误信息里写明正确写法。

**T-0.11:字典删除的引用检查** —— 跨 4 张表统计引用:产品(JSON 数组,用
`JSON_CONTAINS`)、搭配、陈设、方案。之所以用只读 SQL 而不是各模块的 Mapper:
那几张表的实体此项目前还不存在(T-1.x 才建),为一次引用检查把它们提前造出来
会把模块边界变成摆设。各来源计数**一并列出**而非碰到第一个就返回 ——
让运营一次就知道要去哪些地方解除引用。

**T-0.12:「PUT 整体更新而非插入」的理解** —— 落实为**无论 PUT 多少次,库里始终恰好一行**,
而不是"库里没有就不让配"(表里没有种子数据,那样首次配置无从进行)。
由 `repeatedPutNeverCreatesSecondRow` 断言。

### 16.4 校验

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true"
```

**215 个测试,0 失败 0 错误 0 跳过**(T-0.9 时为 181,本轮 +34)。

| 测试 | 覆盖 | 需要外部依赖 |
| --- | --- | --- |
| `QuoteParamKeyTest`(7 条) | D-19/D-15 量纲:小数合法、百分数写法被拒、1 与负数被拒 | 否 |
| `AdminFurnishIntegrationTest`(7 条) | **upsert 不产生重复组合**、不同风格互不覆盖、非分页结构、status 处理 | MySQL |
| `AdminQuoteParamIntegrationTest`(8 条) | 元/㎡ 与小数原值读回、**填 6 被拒**、同键更新而非新增、非分页 | MySQL |
| `AdminDictIntegrationTest`(7 条) | 增删改查、含 disabled 项、键重复 30002、**被产品引用不可删 30002**、解除后可删 | MySQL |
| `AdminStoreIntegrationTest`(5 条) | 未配置 30001、**多次 PUT 始终一行**、可选字段省略而非 null、缺必填 10001 | MySQL |

### 16.5 写测试时又踩到的同一个坑

`AdminDictIntegrationTest` 里多个用例共用同一个探针键,而 JUnit **不保证执行顺序** ——
先跑的把它建出来,后跑的再建就撞 30002,报错还指向"创建失败",看着像实现有 bug。
加 `@BeforeEach` 重置探针状态后解决。

> 这已是**第三次**同类问题(T-0.6 的 SKU、T-0.8 的 SKU、本次的字典键)。
> 规律很明确:**只要多个用例共用同一份可变前置数据,就必须显式重置**,
> 不能依赖执行顺序。后续涉及共享数据的测试都应照此处理。

---

## 17. T-0.13 向量索引建立与同步(2026-09-29)

### 17.1 一处必须先裁决的架构缺口

架构 §6.2 把 Qdrant 的写权给了 Python(「Java 投递 `queue.index.sync` →
**Python 更新 Qdrant**」),§9.1 说 Qdrant 是派生索引,§15.2 要求「全量重建脚本
阶段 0 就绪」。但**现状下任何单独一侧都做不完这件事**:

- Python **不直连 MySQL**(CLAUDE.md 不变量 4),拿不到权威数据 → 重建不了;
- Java 不碰 Qdrant(§6.2 把写权给了 Python)→ 清空不了;
- 契约里**既无"重建/清空"语义的消息,也无让 Python 拉全量数据的内部接口**。

**裁决(用户 2026-09-29)**:全量重建做成**独立运维工具**,直连 MySQL 与 Qdrant。
它不属于任何服务的运行时,也不应被引入服务的调用链 —— "脚本"本就是运维产物,
架构 §15.2 说的也是"脚本"而非"接口"。落在 `tools/rebuild_index.py`。

### 17.2 一个会在运行时才炸的约束:Qdrant 的 point id

Qdrant 的 point id **只接受 uint64 或 UUID**,而我们的业务主键是 `sku_code`
(如 `TL-2043`)、`combo_id`(如 `12`)—— 直接用会被拒绝。

解法:`uuid5(命名空间, "集合:业务主键")` 做**确定性映射**。
确定性是关键 —— 同一个 SKU 永远映射到同一个点,upsert/delete 才对得上;
若用随机 UUID,删除就找不到写入时那个点,索引会持续堆积孤儿。

### 17.3 一个自己埋的、只有跑起来才会暴露的缺陷

T-0.13 的 Java 侧一加进 `application.yml`,**所有 `@SpringBootTest`
上下文全部启动失败**(含纯 Web 切片)。根因:

```
org.yaml.snakeyaml.constructor.DuplicateKeyException: while constructing a mapping
```

我在文件末尾新增了一个顶层 `tile:` 块放 outbox 配置,而 T-0.4 时**已经有一个
`tile:`**(鉴权配置)。YAML 不允许重复键,整份配置解析失败。

> 这次是**硬失败**而不是静默覆盖 —— 重复键若被后者覆盖,会是更难查的问题
> (outbox 配置生效、auth 配置被吃掉,或反之)。教训:往已有配置文件里加顶层块前,
> 先确认该键不存在。

### 17.4 校验

**Java 侧**(`mvn test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true`):

**225 个测试,0 失败 0 错误 0 跳过**(T-0.12 时为 215)。

| 测试 | 覆盖 | 需要外部依赖 |
| --- | --- | --- |
| `OutboxDispatcherTest`(4 条) | 指数退避、上限、单调性、极大重试次数不溢出 | 否 |
| `IndexSyncIntegrationTest`(6 条) | 业务变更 → outbox(同事务)→ 发布到 **RabbitMQ** 队列;上下架/改价后 payload 同步;删除产生 delete 事件;消息体与落库记录逐字段一致 | MySQL + RabbitMQ |

**Python 侧**(`pytest tests`):**14 个测试全过**。

| 覆盖 | 说明 |
| --- | --- |
| 集合建立与幂等 | 维度不符**显式报错**而非静默重建 |
| point id 确定性映射 | 同键同点、跨集合不冲突、可解析为 UUID |
| upsert / delete / 重复消费 | 重复应用结果相同(消费端可能重复收到消息) |
| **下架产品被检索排除** | 判据直接断言;另有正向对照证明过滤没把东西全挡掉 |
| 在售过滤不可关闭 | `build_filter` 强制注入,调用方关不掉 |
| 嵌入确定性 | 同文本同向量 —— 否则"重建后可恢复一致索引"无从验证 |

**全量重建工具**(实测):

```
MySQL 读出:产品 2 条,搭配 0 条
已删除集合 product_index / combo_index / kb_index
product_index:已写入 2 条
完成:产品 2 条,搭配 0 条
```

重建后校验:索引含**两条**(含下架那条,payload 带 `status`),
检索**只命中在售那条**;再跑一次重建,结果完全一致(幂等 + 确定性嵌入)。

### 17.5 三处占位与缺口(已知、待闭合)

| # | 项 | 说明 |
| --- | --- | --- |
| A | **嵌入是确定性占位实现** | 真实 Embedding 网关属 **T-1.5**,厂商由 **P0-2** 决定(§15.1 建议自部署 BGE-M3)。占位实现**无语义能力**,只够验证链路,不能验检索质量。 |
| B | **向量维度暂用 1024** | §9.2 写"由 Embedding 网关决定(如 1024)"。改它只需重建集合并全量重放 —— 索引可重建,安全。 |
| C | **combo 的嵌入文本缺"所含产品摘要"** | §9.3 要求搭配嵌入文本含"所含产品摘要",但 §9.2 的 combo payload 只有 `combo_id/name/space/style/status`,**取不到**所含产品。要补得先在契约里给 combo payload 加字段(上游变更)。当前只拼接可得部分。 |

### 17.6 写测试时第四次踩到同一个坑

`IndexSyncIntegrationTest` 多个用例共用同一个探针 SKU,而 JUnit 不保证执行顺序。
加 `@BeforeEach` 重置后解决。**同一类问题在本项目已出现四次**,规律无例外:
**共用可变前置数据就必须显式重置。**

### 17.7 一处只有"真的从队列取消息"才能发现的假阳

最初的事件断言只筛「collection=product 且 action=upsert」,结果被**其它测试类
留下的消息**命中(T-0.6/T-0.7 的用例同样会触发索引同步,而 Python 消费者
在测试环境未运行,消息无人消费、一直留在队列里)。

修法:筛选时**锁定本测试的 SKU**。若只断言"outbox 行变成 sent",这类假阳
完全不会被发现 —— 它连队列都不用碰。

---

## 18. T-1.8 报价引擎(2026-09-29)

> 阶段 1 的其余任务都卡在 **P0-1(短信报备)** 与 **P0-2(模型选型)** 上;
> T-1.8 是任务清单点名"全项目最适合先写测试的一个" —— 公式、边界、示例数值全部定死,
> 且无任何 AI 依赖,故先做。

### 18.1 一处必须先裁决的缺口:公式里那个「损耗率」取自哪里

| 来源 | 说法 |
| --- | --- |
| §8.8 公式 | `材料费 = 有效面积 × (1 + 损耗率) × 单价` —— **单个**损耗率 |
| §10.6⑤ 示例 | `rulesSnapshot: {auxUnitPrice: 20.00, lossRate: 0.06}` —— 也是**单个** |
| §5.5 / §5.7 | 「损耗率**默认值**(默认 6%,范围 5%~8%)」—— 一个**值** + 一个**范围** |
| 契约 `QuoteParam.paramKey` | `aux_unit_price / loss_rate_min / loss_rate_max` —— **没有那个"当前值"** |

`loss_rate_min` / `loss_rate_max` 是一对**区间**,从中推不出示例里那个 `0.06`。
而损耗率乘在**每一个**报价金额上,猜错不会有任何报错。

**裁决(用户 2026-09-29)**:**新增第 4 个参数键 `loss_rate`** 作为当前采用的损耗率,
`loss_rate_min/max` 降为**允许范围**(只用于校验与提示,不参与计算)。

已按文档先行落地:架构 §8.5 加键 + §8.8 新增「参数来源」表(**v1.5 → v1.6**);
契约 `QuoteParam.paramKey` 与 `QuoteParamUpsert.paramKey` 两个 enum 同步加值。

### 18.2 另外三处口径(§8.8 未写明,由示例与约束推出)

| # | 口径 | 依据 |
| --- | --- | --- |
| A | **地面开口扣减 = 0** | §8.8 写「地面开口 = 门洞面积(**占地面部分**)」,而"占地面部分"取决于**墙体厚度** —— 户型模型(§8.3)**没有墙厚字段**。这与 §10.6⑤ 示例一致:示例地面存在门洞,而 `floor.grossArea = effectiveArea = 3.6`,未做任何扣减。已写入架构 §8.8 |
| B | **多 SKU 部位的开口按毛面积占比分摊** | §8.8 说扣减是**部位级**的、计价是**按 SKU** 的,却没写一个部位用多款砖时怎么分。按面积占比是唯一不偏袒某款砖的分法,且保证「各分项有效面积之和 == 部位有效面积」—— 由 `differentSkusInSamePartArePricedSeparately` 断言 |
| C | **合计累加"已四舍五入的分项"** | 先加总分再取整会让「明细逐条相加」与「合计」差一分钱。由 `itemSumEqualsMaterialTotal` 断言 |

### 18.3 校验

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true" "-Drabbit.verify=true"
```

**238 个测试,0 失败 0 错误 0 跳过**(T-0.13 时为 225,本轮 +13)。

`QuoteEngineRegressionTest`(13 条,需 MySQL)逐项复现 §10.6⑤:

```
请求:floor TL-2043 @3.6 / wall TL-3012 @13.9,墙面有门 0.8×2.1 + 窗 1.0×1.2
响应:materialTotal 1817.01 / auxTotal 292.40 / total 2109.41
      floor gross 3.6 → effective 3.6 → 3.6×1.06×268  = 1022.69
      wall  gross 13.9 → effective 11.02 → 11.02×1.06×68 = 794.32
      rulesSnapshot {auxUnitPrice 20.00, lossRate 0.06}
```

覆盖:示例逐项吻合、开口只作用于墙面、同部位同砖合并、多 SKU 分摊、明细之和 == 合计、
参数缺失报 30001 并指明缺哪个、编号不存在报 10001、缺面积报 10001、
**改 `loss_rate_min/max` 不影响金额**(证明它们确实不参与计算)。

### 18.4 一处只有"先写测试"才会暴露的写法问题

`QuoteService` 被刻意写成**纯函数式**:除读参数与读单价外不做任何 I/O,也不依赖请求上下文。
这样它才能被示例数值直接钉住,也才能被 T-1.15(按当前价重算)原样复用 ——
**重算之所以安全,正是因为公式只有这一份**。
若公式散在生成流程与预览接口两处,§15.7 的"按当前价重算"就会与初次报价算出不同结果。

---

## 19. 登录方式变更:手机号 + 短信验证码 → 用户名 + 密码(2026-09-29)

### 19.1 起因

P0-1(短信通道报备)实测调研的结论推翻了原方案的可行性:

- 国内短信需**企业资质**并通过**运营商签名实名制报备**;
- 资质审核约 2 个工作日、**运营商实名报备 5–10 个工作日且明确不承诺时效**、
  签名与模板另需 1–2 天 —— 合计最坏 **2–3 周**;
- 更关键的是,**个人认证账号的"自用资质"无法通过签名实名制报备** ——
  个人开发者基本无法接入国内短信(需借用企业资质或升级企业认证)。

对单店一期而言这是一道实质性的上线门槛,且卡在**关键路径**上
(P0-1 → T-1.1 登录 → 一切需登录的功能)。故改为一期即可落地的账号密码方案。

### 19.2 改动链(**严格按 PRD → 架构 → 契约 → 实现 顺序**)

| 层 | 文档 / 文件 | 版本 | 改动 |
| --- | --- | --- | --- |
| **需求** | `tile-ai-prd.md` | v0.6 → **v0.7** | §4 流程图、§5.1 全节、§6 页面表、§7.7、§10 边界、§11 决策 #10、§12 迭代规划;新增修订记录 |
| **架构** | `tile-ai-architecture.md` | v1.6 → **v1.7** | §2/§3 Redis 用途、§4.1 auth 职责、§8.1(`t_user.phone`→`username`,删 `t_sms_code`)、§10.1(取消 `tempToken` 例外)、§10.2(4 接口→2)、§10.5(移除 2 码/改义 1 码)、§11.1 时序图、§13.6、§13.7、**§15.6 整节标记为"已取消"但保留决策记录**、§15.8 |
| **契约** | `openapi-c.yaml` | — | 删 `/auth/sms/send`、`/auth/login/sms`;`/auth/register/set-password` + `/auth/login/password` → `/auth/register` + `/auth/login`;删 `LoginSmsData`、`tempToken` scheme |
| **契约** | `openapi-common.yaml` | — | 删 `tempToken` scheme;`UserInfo.phone` → `username`;`ErrorCode` 19 → **17** 码 |
| **任务** | `tile-ai-tasks.md` | — | **P0-1 标记取消**、T-0.4 去掉 tempToken、T-1.1 改写、附 A 关键路径更新 |
| **实现** | `tile-ai-server` | — | 迁移 **V9**;`ErrorCode`、`TokenType`、`AuthPrincipal`、`JwtService`、`JwtProperties`、`JwtAuthenticationFilter`、`SecurityConfig`、`CurrentPrincipal`;删除 `TempTokenStore` / `RedisTempTokenStore` |
| **工具** | `outputs/scripts/check_t.py` | — | T-4 的鉴权矩阵快照同步(它是架构 §10.2 的写死副本) |
| **约定** | `CLAUDE.md` | — | 鉴权行、新增「登录标识」行(**明令不得再引入手机号/短信/验证码**)、待办 |

**未改动的部分(刻意)**:`openapi-admin.yaml`(后台登录本就是用户名+密码)、
`openapi-internal.yaml`、`asyncapi-internal.yaml`、
以及全部业务模块(产品库/搭配/户型/陈设/报价/门店/索引)。**Python 侧一行未动。**

### 19.3 两个设计选择

**账号标识用 `username` 而非"保留手机号但不发验证码"**:后者会让用户可填任意手机号,
而库里看起来像是已验证的 —— 那是个隐性的数据假象。用户名是诚实的。

**登录失败一律 20005**:不区分「用户名不存在」与「密码错误」。区分开等于对外提供一个
账号枚举接口 —— 攻击者可借此批量探测哪些用户名已注册。这条已写入契约 description
与 T-1.1 的验证判据。

### 19.4 校验

**契约侧**:`bash outputs/scripts/ci.sh` —— **7 门禁全部 PASS**。
`x-todo` 21 → **20**(`LoginSmsData` 的 x-todo 随该 schema 一并删除)。

> 过程中 T-2 与 T-4 各失败一次,都指向上游未同步之处:
> - **T-2** 失败是因为我把被删的 `20001`/`20002` 写在了契约的变更说明里 ——
>   校验器扫描契约文本中的 5 位码。**契约该定义"现在是什么",不该兼做变更日志**;
>   变更经过属于架构与本节。已改写为不含数字的表述。
> - **T-4** 失败是因为它是架构 §10.2 鉴权矩阵的**写死副本**,契约改了它不会自己跟着变。
>   已同步。

**实现侧**:

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true" "-Drabbit.verify=true"
```

**233 个测试,0 失败 0 错误 0 跳过**。

迁移实测:

```
Migrating schema `tile_ai` to version "9 - switch login to username"
Successfully applied 1 migration to schema `tile_ai`, now at version v9
```

**受影响的 5 个测试类均已同步**:`SchemaMigrationTest`(t_user 字段、删 t_sms_code、
唯一约束改为 username、迁移版本 1..9)、`ErrorCodeContractTest`(19→17)、
`JwtServiceTest`(去掉 TEMP 类型与 tempTtl)、`RedisTokenStoreTest`(删除 tempToken 用例)、
`SecurityFilterContractTest`(三个 tempToken 用例替换为"Authorization 头不被接受")。

### 19.5 影响

- **P0-1(短信通道报备)取消** —— 全项目最长的外部等待项消失。
- **T-1.1(注册登录)解阻** —— 不再依赖任何外部资源,可立即开工。
- **关键路径缩短**:仍在路径上的外部前置只剩 **P0-2(模型选型)**。
- **代价**:用户需自己记住用户名与密码,**没有短信找回密码**。一期接受。
  若日后要做找回密码,应独立设计(如后台重置),**不要因此把短信通道接回来** ——
  那会把本节列出的全部成本重新引入。这一条已写入架构 §15.6 与 CLAUDE.md。

---

## 20. T-1.1 C 端注册登录(2026-09-29)

> **v0.7 改造后 T-1.1 即刻解阻** —— 不再依赖任何外部资源(原依赖 P0-1 短信通道)。

### 20.1 实现

`com.tileai.auth` 模块(此前只有 `package-info.java`):

| 类 | 职责 |
| --- | --- |
| `entity/User` + `mapper/UserMapper` | `t_user`;登录标识 `username`(V9 迁移后) |
| `AuthService` | 注册(唯一性校验)、登录、改密;密码 BCrypt |
| `AuthController` | `/auth/register`、`/login`、`/password`、`/logout`、`/refresh` |
| `dto/AuthRequests` | 三个请求体 |
| `dto/UserView` | 契约 `UserInfo` 的对外视图(白名单,不含 `passwordHash`) |

复用 T-0.4 已有的 `JwtService` / `AuthSessionService` / `AuthCookies` / `CurrentPrincipal` ——
**鉴权基础设施一行未改**,T-1.1 只是把端点接上去。

### 20.2 两处刻意的防御

**① 密码长度报 20004,不是 10001。** 契约给注册/改密声明的密码格式错误码是 **20004**,
而 `@Size` 校验失败会走「参数校验失败 10001」。两者语义不同 ——
10001 是"请求本身不合法",20004 是"这个字段内容不合业务规则"。
因此长度校验放在服务层抛 20004,DTO 上只用 `@NotBlank` 拦住"压根没传"。

**② 登录失败一律 20005,并做时序对齐。** 不区分「用户名不存在」「密码错误」「账号已停用」;
且用户名不存在时**也跑一次 BCrypt**。前者防的是从响应体枚举账号,
后者防的是从**响应时间**枚举 —— 同一个问题的两种表现。

### 20.3 一条被测试纠正的错误前提

`logoutIsIdempotent` 最初写成"不带任何 Cookie 调登出也应返回 200",实测得到 **401**。

查契约后确认**实现是对的**:`/auth/logout` 标的是 `security: cookieAuth`,**它本来就需要登录态**。
"登出总能成功"的准确含义是"**带登录态但 refresh 令牌缺失或无效时不该失败**"(如重复登出),
而不是"无需登录也能登出"。测试已按此改正,并**补了一条 `logoutRequiresLogin`** 把
"未登录调登出 → 401"这个契约行为也钉住。

### 20.4 校验

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true" "-Drabbit.verify=true"
```

**249 个测试,0 失败 0 错误 0 跳过**(T-1.8 时为 238,本轮 +16 —— 新增 `AuthIntegrationTest`)。

`AuthIntegrationTest`(16 条,需 MySQL + Redis)逐条对应 T-1.1 的三条判据:

| 判据 | 覆盖 |
| --- | --- |
| 契约 T-4 鉴权矩阵 | 未登录调改密/登出 → 401;公开接口免鉴权(契约侧由 ci.sh 的 T-4 保证) |
| **登录失败不区分原因** | `unknownUsernameIsIndistinguishable` 断言两种失败的**响应体除 traceId 外完全一致**;`disabledUserIsIndistinguishable` 断言停用账号同样是 20005 |
| 错误码覆盖 | 20003 用户名重复 / 20004 密码长度(短与长各一)/ 20005 密码错误 |

另覆盖完整链路:注册即登录 → Cookie 能访问受保护接口 → 改密后新密码可登录、旧密码失效 →
登出后 refresh 立即失效 → 刷新下发新 access。以及**令牌不进响应体**、
**refresh Cookie 的 Path 收窄至 `/api/v1/auth`** 两项契约硬约束。

---

## 21. T-1.2 / T-1.3 / T-1.4 / T-1.14 C 端四个只读接口(2026-09-29)

四个任务互不重叠(产品库 / 搭配 / 字典 / 门店各一张表、一个模块),
且都只依赖已完成的 T-0.6 / T-0.8 / T-0.11 / T-0.12 —— 与 §16 同理,
**同轮实现、统一验证**,未拆子代理。

### 21.1 发现并修复一处契约缺陷:C 端 `/dicts` 的响应不可满足

T-1.4 的判据要求「契约校验表 T-15 的**运行时部分**」。逐字读契约时发现
`GET /api/v1/dicts` 与其余接口**不同构**:

```yaml
# openapi-c.yaml(修复前)
responses:
  '200':
    content:
      application/json:
        schema:
          allOf:
            - $ref: './openapi-common.yaml#/components/schemas/ApiResponse'
            - type: object
              properties:
                data:
                  type: array          # ← 裸数组
```

而 `ApiResponse.data` 声明为 **`type: object`**。两者经 `allOf` 组合后
**同时成立**——不存在任何既满足 `array` 又满足 `object` 的 JSON 值。
也就是说:**这个接口没有任何合法响应形态**,契约层面直接死锁。

修复为 `{ list: [...] }`,与后台 `GET /admin/dicts` **同构**:

```yaml
data:
  type: object
  properties:
    list: { type: array, items: { $ref: '.../components/schemas/Dict' } }
  required: [list]
```

> 该缺陷**G1–G7 全绿时就已经存在** —— 单独一份 YAML 展开 `allOf` 也看不出矛盾,
> 只有把两份文件合起来判断「有没有能同时满足两边的值」才暴露。

### 21.2 新增 T-1 断言(契约自身的一致性)

`outputs/scripts/check_t.py` 的 T-1 原只校验 `data` 是否存在,现已增加:
**每个接口为 `data` 声明的类型,必须与 `ApiResponse.data.type` 兼容** ——
即 `object` 兼容一切(可加 `properties` 收窄),而 `array` / `string` 等与之矛盾者一律 FAIL。

**已做负向验证**:把 `data: { type: object, properties: { list } }` 改回
`data: { type: array }` → `ci.sh` **exit 1**;恢复 → **exit 0**。
一个永远通过的断言没有价值(同 §15.5 的负向对照)。

### 21.3 三处刻意与后台列表不同(不是疏漏)

| 差异 | 后台 `/admin/products` | C 端 `/products` | 理由 |
| --- | --- | --- | --- |
| 详情路径参数 | `{id}`(主键) | **`{sku}`**(编号) | 前台"选砖"流程里流传的是编号,不是库内主键 |
| 可见范围 | 全部(含下架) | **仅 `on_sale`** | 下架砖不该出现在浏览与搜索里;**详情与列表口径统一** —— 下架 SKU 的详情按"不存在"处理(30001),否则用户能拿着买不到的砖往下走 |
| 筛选维度 | 名称/材质/规格/价格 | **多出 `style` / `space`** | 走字典键,对应前台"按风格挑""按房间挑" |

搭配列表同理:仅返回 `on_sale`(后台要能管理下架项)。

### 21.4 一处必须用 `JSON_CONTAINS` 而非 `LIKE` 的筛选

`applicable_spaces` / `style_tags` 是 JSON 数组列。若用 `LIKE '%modern%'`,
**`modern` 会命中 `modern_lux`** —— 且不会报任何错,只是搜索结果悄悄多出无关砖。
改用 `JSON_CONTAINS`,由 `arrayFilterIsExactNotSubstring` 断言
(插入一条 `style_tags: ["modern_lux"]` 的砖,按 `modern` 筛**不得**命中)。

候选值经 `ObjectMapper.writeValueAsString` 转成 JSON 字面量后再拼入 `JSON_CONTAINS`,
避免手工拼引号。

### 21.5 校验

**契约侧**:`bash outputs/scripts/ci.sh` —— **7 门禁全部 PASS**(T-1 的加固见 §21.2)。

**实现侧**:

```bash
mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" "-Dredis.verify=true" "-Drabbit.verify=true"
```

**263 个测试,0 失败 0 错误 0 跳过**(T-1.1 时为 249,本轮 +14)。

| 测试 | 覆盖 | 需要外部依赖 |
| --- | --- | --- |
| `CEndBrowseIntegrationTest`(14 条) | 四个接口的全部判据(见下表) | MySQL |

四个任务各自的判据与断言:

| 任务 | 判据 | 断言 |
| --- | --- | --- |
| **T-1.2** | 分页 T-3 成立 | `productListIsPaginated` 断言 `list/total/page/size` 四字段齐备 |
| | 八参数与 **D-8 裁决**一致 | `allEightFiltersWork` 逐个筛选正向命中,**并对每个维度各带一条负向对照**;`priceMin` 在阈值两侧各测一次 |
| | 下架 SKU 不出现、详情 30001 | `offSaleProductIsHidden` |
| | 免登录(PRD §5.1) | 全部请求**不带任何 Cookie** —— 测试本身即验证,另有 `productListIsPublic` |
| **T-1.3** | 结构与 `Combo` schema 一致 | `comboListReturnsOnSaleOnly` 逐字段核对 |
| | **items 的 `part` 恒为三值(T-9)** | `comboItemPartsAreThreeValued` |
| | 仅返回上架搭配 | 同上 + `comboDetailRespectsStatus` |
| **T-1.4** | 仅 `enabled`、按 `sort` 升序 | `dictReturnsEnabledSortedBySort` |
| | **T-15 的运行时部分** | 接口返回集即"前端可提交的 space/style 全集",静态不可证(契约侧 T-15 由 ci.sh 保证) |
| **T-1.14** | 与 `Store` schema 一致、未配置 30001 | `storeFieldsAndUnconfigured`(断言是 30001 而**不是**空对象 —— 空对象会让前端画出一张没内容的卡片) |
| | **PRD §10 边界:仅展示,无下单/支付** | `storeHasNoCheckoutEntry` 扫响应体,`order/pay/cart/checkout` 均不得出现 |

### 21.6 又一次"共享可变数据"——第五次

`CEndBrowseIntegrationTest` 一开始就写成 `@BeforeEach` 全量重置探针数据
(产品 3 条 / 搭配 2 条 / 字典 2 条),`@AfterAll` 再清一次。
§12.2、§16.5、§17.6 已记过四次,**未再重犯**。

另有一处细节:`cleanUp` 必须**先删搭配项再删产品** —— 反过来会留下
指向已删产品的悬挂 `product_id`(正是 §14.1 硬删要挡的那个状态)。

---

## 22. 模型选型落定(2026-09-29)

关闭缺口 **G-4**。记录性质为**产品决策**,不是评测结论。

### 22.1 背景:决策顺序与原设计相反

P0-2「模型选型 Bake-off」的原文是**不先验拍板** —— 架构 §15.1 建议
"用真实样本横向评测后再定"。本次改为**先下选型决策**,理由:

- 三类网关(§7.1)已抽象、候选协议收敛到 **OpenAI 兼容**,厂商选择退化为配置项;
- 评测的**真实价值**不在"选谁",而在**定质量验收基线**(坏图率阈值、五维量化分数)——
  这部分不以"选厂商"为前提。

**故 §15.1 与新决策不矛盾,而是分工**:厂商已定,基线待测。

### 22.2 产出(文档链改动)

| 文档 | 版本 | 改动 |
| --- | --- | --- |
| `tile-ai-prd.md` | v0.7 → **v0.8** | §8 关键选型建议补已选厂商;§11 决策 #4/#5 细化 |
| `tile-ai-architecture.md` | v1.7 → **v1.8** | §15.1 由"建议"改为"决策";§15.3 分期作废;§7.2 标注已选 |
| `tile-ai-tasks.md` | v0.1 | P0-2 增进度块(明确标注"只有前半完成");附 C 关 G-4、新增 G-7 |

**契约未改动** —— 本次不涉及任何接口、字段或枚举,4 份 OpenAPI 与
`asyncapi-internal.yaml` **一字未动**,`openapi-common.yaml` 无需同步。

### 22.3 三处关键判断

**1. 图像模型的硬约束是"参考图张数",不是画质。**

生产路径是"同空间类型底图 + 各部位瓷砖切片"的条件生成。按一个空间最多
floor / wall / local 三部位计(PRD 决策 #11)=**最多 4 张参考图**:

| 模型 | 参考图上限 | 结论 |
| --- | --- | --- |
| `wan2.5-i2i-preview` | 3 张 | ❌ **装不下**,不可用 |
| `wan2.7-image-pro` | 9 张 | ✅ 不构成瓶颈,已选 |

这条**反向约束产品能力**:换图像模型时必须重核上限,低于 4 就要收窄
"多砖多部位"。**这是本次唯一"技术约束倒逼产品设计"的点**,已写入
PRD §8 与架构 §15.3。

**2. 模型 ID 会过期,配置里不要照抄教程。**

`deepseek-flash` 是当前正式 ID(V4.1 Flash,2026-09-10)。同日
`deepseek-v4-flash` 退役(仍路由,已非正式名);`deepseek-chat` /
`deepseek-reasoner` 已于 **2026-07-24 停用**。网上教程与脚手架大量仍用停用名,
实现 T-1.5 时极易照抄出错 —— 已写入架构 §15.1 的警示。

**3. "选型落定"不等于 §15.3 的"达标"没了。**

§15.3 原文写"并行验证垫图方案,**达标后替换**",而"达标"依赖**坏图率阈值**,
阈值只能由真实样本测出。故 P0-2 的评测脚本与评分表**仍需产出**,
只是验收目标从"选出厂商"变为"定出基线",且**必须按条件生成模式测** ——
纯文生图下的排名与生产路径无关。

### 22.4 未闭合:合规是硬前置,且当前无任务承载

**这是本节最需要回看的一条。**

面向公众提供生成式 AI 服务在国内走**三轨制**,不可互相替代:

| 事项 | 办理对象 | 周期 |
| --- | --- | --- |
| 算法备案 | 对外提供的生成合成类算法服务,**不论模型来源** | 2–4 个月 |
| 大模型登记 | 仅调用已备案模型、未做改造的应用方 | 1–3 个月 |
| 大模型备案 | 自研或深度微调的模型底座 | 6–8 个月(本项目不需要) |

三点直接命中本项目:

1. **"调用已备案模型的 API 就不用办"是误区。** 2026-03 已有执法案例:企业
   通过 API 调用已备案大模型对外提供服务,未做应用登记与安全评估,被约谈并
   责令下线。2026-04 起"应备未备 / 应登未登"列入专项行动重点。
2. **对输出做二次处理可能触发"升级"。** 架构 §7.3 要求"出图与问答前后接内容
   审核";内容过滤/重组**有可能被认定为对模型的实质性修改**,从而使**登记升级
   为备案**(周期 1–3 个月 → **6–8 个月**)。**这条应在 T-2.2 开工前就与属地
   网信部门确认口径**。
3. **合规反向约束选型。** 登记须报"调用哪个已备案模型",故候选须先确认在
   已备案清单内 —— 这是**选型的前置筛选条件**,不是五维打分里的软性一维。

另有《人工智能生成合成内容标识办法》要求的**生成内容标识**义务
(效果图需显式标识 AI 生成)。

**现状**:PRD §9 仅一句"内容安全(生成图、AI 问答合规)";架构 §13.9
「兼容性与合规」只写浏览器兼容与报价免责;**任务清单中没有任何任务承载
备案、登记、内容标识**。已记入架构 §15.1 合规前置、任务清单附 C 缺口 **G-7**,
建议新增 **P0-5**。

> **与 P0-1 的类比值得警惕**:P0-1(短信报备)因 **2–3 周**外部周期而被直接砍掉
> 登录方案。备案是 **1–4 个月**,且**砍不掉**(总不能让用户不能问 AI)——
> 现在启动才来得及。

### 22.5 校验

**契约侧**:本次未改动任何契约文件,但按 CLAUDE.md 的"改动后必跑"惯例执行:

```
bash outputs/scripts/ci.sh   →   EXIT=0
G1-结构校验 PASS   G2-redocly PASS   G3-spectral PASS   G4-逗号撕裂 PASS
G5-描述完整性 PASS   G6-T1-T15 PASS   G7-AsyncAPI PASS
T-1~T-15:PASS=14  FAIL=0  SKIP=1(T-12 仍因无实现侧导出而不可执行)
```

**实现侧**:未改动代码,未重跑 263 个 Java 测试。

---

## 23. T-1.5 Python 智能体服务与模型网关(2026-09-29)

### 23.1 为什么这次不碰契约

T-1.5 的产物**不对应任何 HTTP 端点**。架构 §7.1 把三类网关画在 Python 服务
内部;`openapi-internal.yaml` 的 7 个端点全是**智能体能力**(chat / parse-layout /
material-map / complete-parts)与 Python→Java 回查/回传。

因此:

- 4 份 OpenAPI 与 `asyncapi-internal.yaml` **一字未动**,`openapi-common.yaml`
  无需同步,本文件的追溯表(§1–§5)也无新增条目。
- **`x-todo` 一个都没解**:`/internal/agent/*` 的 4 处 `x-todo` 属 T-1.6/1.7/1.9/1.10,
  按 CLAUDE.md「决策点,先问别自己定」,本次不碰。契约里**不存在**
  `/internal/quote/*` 这一条也未受任何影响(不变量 1)。

把网关做成端点会凭空多一层对外面,且违反不变量 3(Python 仅内网)。

### 23.2 产出

新增包 `tile-ai-agent/app/gateway/`:

| 文件 | 职责 |
| --- | --- |
| `ports.py` | 三类 Protocol + `Reply`/`ToolCallPlan`/`Images`/`Message`/`ToolDef`/`ToolCall`/`Usage`/`ReplyChunk` |
| `errors.py` | 异常层次 + `counts_toward_failure_rate()`(重试口径与失败率口径**刻意不同**) |
| `resilience.py` | `RetryPolicy` / `CircuitBreaker` / `FailureRateTracker` / `RedisTokenBucket` / `CostMeter` / `CallGuard` |
| `openai_compat.py` | LLM(chat / chatWithTools / SSE 流式)与图像(多参考图)适配器;请求构造与响应解析为纯函数 |
| `resilient.py` | 把韧性层套到网关上(LLM / Image 两个包装) |
| `failover.py` | `FailoverLLMGateway` —— §13.7 的 10% 阈值切换与半开恢复 |
| `factory.py` | 按配置组装(§7.2 的"唯一开关"),厂商预置表 |

配置侧:`app/core/config.py` 补 backup/base_url/model/redis/韧性旋钮字段,
**密钥字段改 `SecretStr`**;`requirements.txt` 补 `httpx` 与 `redis`
(前者原已随 `qdrant-client` 间接装入,现提升为显式声明)。

### 23.3 三处值得回看的判断

**1. `is_transient()`(要不要重试)与 `counts_toward_failure_rate()`(算不算故障)
是两个口径,不是一回事。**

一度想当然地写成"只把瞬态故障计入失败率"。那样 401 密钥失效、响应格式错乱
都不会被算作故障 —— 而它们恰恰说明**这个供应商当前不可用**,正是该切备份的
情形,只是不该重试而已。故最终口径:**除本服务自己的保护动作(限流、熔断)外,
所有网关层故障都计入失败率**。

限流与熔断明确排除,因为它们不是供应商故障;熔断尤其不能计入 ——
它本身就是失败率推导出的结论,再计回去就成了自我印证的循环,一旦打开
就再也掉不下来。

**2. 半开探测走真实请求,失败时由备份兜住。**

切到备份后每隔 `probe_seconds` 放**一个真实请求**去试探主力:成功即切回,
失败则继续用备份。比"定时发 ping"省一次调用,也不用为 ping 编造假对话。

但有个必须处理的副作用:**不能让用户为我们的探测买单**。故探测失败时,
这一次请求改由备份答完 —— 由 `test_failed_probe_still_answers_from_backup` 断言。

**3. 三个真实缺陷由测试暴露,不是靠 review 看出来的。**

| 缺陷 | 症状 | 后果 |
| --- | --- | --- |
| 半开探测名额不归还 | 非下游原因失败时不计入熔断,名额只减不增 | 名额耗尽后**熔断再也回不来**,变成事实上的永久打开。已加 `release()` |
| 熔断中途打开会顶掉原始错误 | 一次调用内重试,第 2 次尝试被熔断挡下,抛出的是 `CircuitOpen` | 调用方看到"熔断"而非"为什么失败",**线索丢失**。已改为重抛原始错误 |
| 端点依赖注入客户端的 base_url | 外部注入 client 时,请求**静默打到别的主机** | 最危险的一类:不报错、只是连错厂商。已改为适配器自己拼绝对 URL |

第三条尤其值得记:它不会让任何测试变红(替身照样回 200),只有断言
**请求真的打到哪台主机**时才暴露。

### 23.4 §7.1 签名的两处刻意落法

| 文档写法 | 实现 | 理由 |
| --- | --- | --- |
| `chatWithTools`、`refImages`、`toolDefs` | snake_case(`chat_with_tools` / `ref_images` / `tool_defs`) | 文档是跨语言伪码的 camelCase 记号。`chat` / `generate` / `embed` 三个词**逐字一致** |
| `size`、`n` 未标 `?` | 给了默认值 | 默认值是**更宽松**的约束,不违反签名且更可用 |

**这两处不是抄漏,是刻意的**,记在此以便评审时对照。

### 23.5 校验

**测试**:`tile-ai-agent` 下 `.venv/Scripts/python.exe -m pytest -q` ——
**58 passed, 5 skipped**(T-0.13 的 14 条 + 本次新增 44 条;5 条真实联调默认跳过)。

| 文件 | 条数 | 需要外部服务 |
| --- | --- | --- |
| `tests/test_gateway.py` | 39 | **无**(`httpx.MockTransport` + 注入时钟/sleep/抖动) |
| `tests/test_gateway_integration.py` | 5 | Redis 6379 与 Qdrant 6333 |
| `tests/test_gateway_live.py` | 5 | **真实厂商 API**(默认跳过,见 §23.6) |

分两个文件的理由:韧性逻辑(重试、熔断、切换)用替身 + 注入时钟就能测全,
不必真等 30 秒冷却;**但令牌桶的"跨实例共享额度"与 Embedding 插槽的接缝
替身证明不了** —— 前者要的正是 Redis 服务端的原子性与它自己的时钟,
后者要的正是"工厂造的网关能否真的被 `apply_event` 接受并写进 Qdrant"。

关键断言与判据的对应:

| 判据 | 断言 |
| --- | --- |
| §7.2 配置切换可生效(卡片验收项) | 换 provider 后**请求真的打到不同主机**(`api.deepseek.com` / `open.bigmodel.cn`);拼错 provider 抛 `GatewayConfigError` 而非静默降级 |
| §7.2 超时 | 超时经重试后抛 `GatewayTimeout`,且确实发满 3 次 |
| §7.2 重试 | 429 重试后成功(2 次请求);**4xx 只发 1 次**(重试不会变对);退避**只向上抖**不缩短 |
| §7.2 熔断 | 达阈值后**不再发请求**(请求计数不增);冷却后半开放行一次,成功即归 closed |
| §7.2 限流 | 本地限流挡下时**零出网、零退避**;令牌桶跨实例共享同一额度;令牌随时间回填;时间戳取自 Redis 的 `TIME` 而非客户端 |
| §7.2 成本计量 | 单价取自配置(百万 token 计费);**按实际返回张数**计图费;无单价时记 0 成本但**仍记用量** |
| **§13.7 失败率 > 10% 切备份** | 5 次中 1 次失败(20%)→ 切备份且**不再打主力** |
| §13.7 的反面(样本不足) | 1 次失败(100%)但样本 < 5 → **不切**,避免一次抖动换一次切换 |
| §15.1 双供应商恢复 | 主力恢复且到冷却点 → 探测成功切回,且**旧失败样本被清空**(否则立刻被按回备份位) |
| §15.3 参考图硬约束 | 图像请求体里**四张参考图一张不少**(底图 + 三部位切片)—— 防"换了个上限只有 3 张的模型" |
| §13.6 密钥 | 哨兵密钥不出现在**日志、异常消息、`repr`** 三处;且同时断言告警日志**确实产生过**(否则那条断言可能只是因为什么都没记) |
| §7.1 流式 | 帧序为 delta… → 末帧带 usage → `[DONE]`;**usage 只认最后一帧**(按帧累加会重复计数);无法解析的行跳过而非炸掉整条流 |

**回归保护**:`app/index/**` 与 `tests/test_index.py` **零改动**(`git status` 已核);
旧 14 条断言全过。

**契约侧**:契约未改动,仍按惯例跑 `bash outputs/scripts/ci.sh` → **EXIT=0**,
7 门禁全 PASS。契约校验表无新增条目。

**Java 侧**:本次不涉及 Java,**263 个测试未重跑**。

### 23.6 真实联调(2026-09-29,DeepSeek)

**原记录的"没有任何真实厂商调用被验证"已被推翻 —— LLM 侧已与厂商真实联调通过。**

新增 `tests/test_gateway_live.py`(5 条),**默认跳过**,需显式设
`RUN_LIVE_LLM=1` 才跑 —— 门控方式与 Java 侧 `-Ddb.verify=true` 的惯例一致:
需要外部依赖的验证必须显式打开,不能让 `pytest` 顺手跑出去(会出网、会花钱)。
密钥只在 `tile-ai-agent/.env`(已 gitignore),**不进代码、不进测试文件**。

```
RUN_LIVE_LLM=1 python -m pytest tests/test_gateway_live.py -v
→ 5 passed
```

| 断言 | 实测结果 |
| --- | --- |
| `chat` 真实出网并返回内容 | usage 52 in / 184 out;内容「建议选浅色系,如白色、米色或浅灰,显亮又耐看。」 |
| **预置模型 ID 被厂商接受** | 厂商回显 **`deepseek-flash`** —— 架构 §15.1 定的 ID 正确 |
| 流式 | 31 帧,末帧带 usage |
| 工具调用 | `search_products({"space": "bathroom"})` |
| 成本计量 | 单价表从 `.env` 的 JSON 正确解析为 `ModelPrice(input=1.0, output=2.0)`;累计 456 in / 835 out → **¥0.0021** |

> **模型 ID 这一条是本次联调最想确认的**:架构 §15.1 定的是 `deepseek-flash`,
> 而网上教程大量仍在用已停用的 `deepseek-chat` / `deepseek-reasoner`
> 或已退役的 `deepseek-v4-flash`。实测厂商接受并回显该 ID。
>
> 成本量级也印证了 §22 的判断:**LLM 成本可忽略**(单轮约 ¥0.002),
> 图像才是成本大头。选型不该为省这几厘钱而降档。

**联调中发现并修掉的一处自身问题**:live 测试最初用了空的 `CostMeter()`,
于是"有 token 却算出 0 成本"。这不是网关的问题(无单价时记 0 成本是**有意设计**,
已有单测钉住),而是**测试搭错了**:单价在配置里,替身却建了张空表。
已改为从 `settings.gateway_model_prices` 取。

#### 23.6.1 仍未验证(不得当作已交付)

1. **`wan2.7-image-pro` 的请求/响应字段形态仍未与百炼文档核实** —— 无厂商文档与密钥。
   已在 `openai_compat.py` 顶部显著标注。它被隔离在两个纯函数里,形态确认后只改那里;
   **在此之前不能声称图像链路可用**。
2. **智谱 GLM 的模型 ID 未定**,预置表里留空 —— 配置不填就报错,不猜。
   故 §13.7 的**主力→备份切换只经过替身验证,未做过跨厂商真实演练**。
3. **Embedding 真实适配器未落** —— 模型选型属 P0-2 残留,默认仍是确定性占位。
4. 流式响应的 `tool_calls` 分片拼装未实现,留给 T-1.6。
5. 网关未接入 FastAPI 启动流程(T-1.6 才有调用方);Prometheus 导出属 T-2.3。

### 23.7 一处架构层面的偏离,需要评审确认

**网关做成同步而非异步。** 理由:本项目既有代码全是同步的
(`pika.BlockingConnection`、`QdrantClient`、显式构造注入),且
`EmbeddingPort.embed` 是同步的 —— 若改成 async,`EmbeddingGateway` 就再也不能
直接喂给 `apply_event`,**T-0.13 的接缝会断**。

代价:T-1.6 的 SSE 端点需用 `run_in_threadpool` 调这些同步方法,流式部分
返回同步迭代器由它逐帧转 SSE。**这个取舍建议在 T-1.6 开工时复核一次。**

---

## 24. 补后台会话接口:logout / me,并关闭后台登录的 x-todo(2026-09-29)

### 24.1 背景

`openapi-admin.yaml` 的 `/auth/login` 长期挂着一条 `x-todo`:

> `§10.3/§10.2 未列出后台登录接口;由 §4.4'均需后台会话'推断,路径与字段待确认`

而前端接入后台时发现两件事必须补接口:

1. **契约里没有后台登出接口**,前端 `logout()` 只能清本地状态,服务端 Cookie 要等自然过期;
2. **契约里没有"取当前账号"接口**,令牌在 HttpOnly Cookie 里读不到,前端只能把
   `AdminUserView` 缓存在 sessionStorage 里 —— 那是把**展示状态当成凭证来源**:
   账号被停用或降权后,界面会继续按旧角色渲染。

故本次一并处理:补 `POST /auth/logout`、`GET /auth/me`,并**裁决关闭那条 x-todo**
(现有 `/auth/login` 的路径与字段即为正式契约 —— 它已按此形态实测工作)。

### 24.2 产出(文档链改动)

| 文档 | 版本 | 改动 |
| --- | --- | --- |
| `tile-ai-prd.md` | v0.8 → **v0.9** | §7「后台管理端」补入**后台登录与退出** |
| `tile-ai-architecture.md` | v1.8 → **v1.9** | §10.3 由一句话展开为**三个会话接口**,写明不需权限点、登出口径、以及为何需要 `me` |
| `openapi-admin.yaml` | — | 新增 `/auth/logout`、`/auth/me`;**移除** `/auth/login` 的 `x-todo` |
| 实现 | — | `AdminAuthController` +2 接口;`AdminAccountService.findEnabledById`;`AuthCookies.refreshTokenOf`(从 C 端控制器抽出来共用) |

**鉴权口径**:三个会话接口**都不需要权限点** —— 它们是"我是谁"的接口,不是"我能做什么"。
登出**沿用 C 端口径**(需要登录态,但 refresh 缺失/无效时不报错),
理由见 §20.3 的既有裁决;做成"无需登录"等于对外提供一个人人可调的清 Cookie 接口。

`GET /auth/me` 对**已停用账号返回 10002** 而非 30001:对调用方这是"会话失效、要重新登录",
与"资源不存在"是两回事 —— 前者清态跳登录页,后者只需提示。

### 24.3 ⚠️ 实测发现两个**既有缺口**(不是本次改动引入的)

用真实 HTTP + Cookie 容器(.NET `CookieContainer`,与浏览器同规则)实测复现。

**缺口 A:C 端与 B 端的 Cookie 同名同路径,同一浏览器会互相覆盖。**

| 步骤 | 观察 |
| --- | --- |
| 同会话登录 C 端 | `access_token`(Path=/)写入 |
| 同一会话再登录后台 | `access_token` **被覆盖**为 ADMIN 令牌 |
| 再调 C 端受保护接口 | **403** —— C 端用户被踢下线 |

根因:两端共用 `access_token` / `refresh_token` 两个名字与路径,
而 JWT 里带 `styp`(ADMIN/USER)。覆盖后 C 端请求带着 ADMIN 令牌,
`hasAuthority("ROLE_USER")` 不放行。反向同理。

**缺口 B:后台的 `refresh_token` Cookie 被浏览器直接丢弃。**

| 观察 | 结果 |
| --- | --- |
| 登录响应下发的 `Set-Cookie` | 两条都在(`access_token` + `refresh_token`) |
| 浏览器 Cookie 容器里实际存下的 | **只有 `access_token`** |

根因:`refresh_token` 的 `Path=/api/v1/auth`(契约要求收窄),而**后台登录在
`/api/v1/admin/auth/login`** —— Cookie 的 Path **不覆盖发起请求的路径**,
浏览器按规范拒绝存储。后果:**后台会话根本没有 refresh 令牌**,2 小时后
access 过期就只能重新登录;后台登出也就无从"拉黑 refresh"。

> 这一条也解释了为什么 §24.4 的 `adminLogoutDoesNotRevokeRefreshToken` 会通过 ——
> 表面是"登出没拉黑",实际是"压根没有令牌可拉黑"。

**两者共同根因**:会话模型没有区分 C 端与 B 端的主体。
修复需要**给后台独立的 Cookie 名与路径**(如 `admin_access_token` Path=/api/v1/admin、
`admin_refresh_token` Path=/api/v1/admin/auth),而 Cookie 名与路径写在
`openapi-common.yaml#/components/securitySchemes/cookieAuth` 里 ——
**属契约变更,影响面含 C 端,故本次不动,先记录**。缺口编号 **G-9**。

**这两条已在 §25 修复。**

### 24.4 校验

**契约侧**:`bash outputs/scripts/ci.sh` → **EXIT=0**,7 门禁全 PASS
(T-4 鉴权矩阵含新增的两个 `security: cookieAuth` 接口)。

**Java 侧**:`mvn test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true`

```
Tests run: 269, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

263 → 269,本轮 +6,全部落在 `AdminAuthIntegrationTest`:

| 断言 | 判据 |
| --- | --- |
| `/me` 带会话返回账号(含 role,不含 passwordHash) | §10.3 |
| `/me` 未登录 → **真实 401** | 契约 T-4 |
| `/me` 对**签发后被停用**的账号 → **10002** | §4.4「权限判断读当下事实」 |
| 登出清两条 Cookie(`Max-Age=0`) | §10.3 |
| 登出未登录 → 401 | 与 C 端同口径(§20.3) |
| `adminLogoutDoesNotRevokeRefreshToken` | 把缺口 B **钉住**(§25 修复后已改写为验证修复) |

C 端 `AuthIntegrationTest` 全过 —— 证明把 `refreshTokenOf` 从 C 端控制器
**抽到 `AuthCookies` 共用**没有破坏既有功能。抽取的理由:两端都要读它,
各写一份就会有两份实现需要同步(本项目在 `securitySchemes` 重复定义上已吃过一次亏,F-4)。

**前端侧**:`npm test` → **66 passed / 6 文件**;`npm run build`(含 `vue-tsc`)通过。

**行为变更(前端)**:身份不再来自 `sessionStorage` 缓存,改为**每次页面加载问一次
`/auth/me`**;路由守卫改为异步确认会话;登出改为**先请求服务端再跳转**,
且服务端失败也照样清本地(不能把用户卡在登录态里)。

### 24.5 未闭合

1. **缺口 A / B 未修**(需契约变更,见 §24.3)。建议随 G-9 一并处理。
2. **`/auth/me` 的响应体是否该带权限点列表**:目前只带 `role`,前端据架构 §4.4
   的对照表自行推导权限。若日后角色-权限表要支持后台热配置,`me` 就该直接返回
   权限点集合。**当前不必要**(表是静态的且已冻结在架构里)。
3. **无"刷新后台会话"的能力**(缺口 B 的直接后果)。

> §24.5 的三条**均已由 §25 解决或覆盖**。


---

## 25. 两端会话分离:修复缺口 G-9(2026-09-29)

### 25.1 背景

§24.3 实测发现两个既有缺口,共同根因是**会话模型没有区分 C 端与 B 端主体**:

| 缺口 | 实测现象 | 影响 |
| --- | --- | --- |
| **A** | 同一浏览器先登 C 端再登后台,`access_token` 被覆盖 → 再调 C 端受保护接口得 **403** | 用户表现为"莫名掉线" |
| **B** | 后台登录下发的 `refresh_token`(`Path=/api/v1/auth`)**被浏览器直接丢弃** —— 它不覆盖登录路径 `/api/v1/admin/auth/login` | 后台没有刷新令牌,2 小时后强制重登;后台登出也无从"拉黑 refresh" |

本次按完整文档链修复:**PRD → 架构 → 契约 → 实现 → 校验**。

### 25.2 产出

| 文档 | 版本 | 改动 |
| --- | --- | --- |
| `tile-ai-prd.md` | — | **未改**。会话 Cookie 的名字与路径属技术设计,PRD 层面无对应描述;PRD §7「后台管理端」已在 v0.9 补过登录与退出 |
| `tile-ai-architecture.md` | v1.9 → **v2.0** | §10.1 由"一套 Cookie"改为 **C/B 两端各一套**(名字与路径都分开),含实测依据;§10.3 补 `POST /admin/auth/refresh` |
| `openapi-common.yaml` | — | 新增 `adminCookieAuth` scheme;**保留** `cookieAuth`(C 端) |
| `openapi-admin.yaml` | — | `cookieAuth` → `adminCookieAuth`(含文档级默认与两处显式声明);新增 `/auth/refresh` |

**两端 Cookie 的最终形态**:

| 主体 | access(2h) | refresh(14d) |
| --- | --- | --- |
| C 端业主 | `access_token`,Path=`/` | `refresh_token`,Path=`/api/v1/auth` |
| B 端后台 | `admin_access_token`,Path=`/api/v1/admin` | `admin_refresh_token`,Path=`/api/v1/admin/auth` |

两条设计要点:

1. **后台 refresh 的 Path 必须覆盖登录端点自身**。浏览器只在路径匹配时才存储/发送
   Cookie —— 这正是缺口 B 的成因(`/api/v1/auth` 覆盖不到 `/api/v1/admin/auth/login`)。
   **这一条有专门的测试锁住**(`adminRefreshPathCoversLoginEndpoint`)。
2. **令牌的 `styp` 是第二道防线,但不能只靠它**。`hasAuthority("ROLE_USER"/"ROLE_ADMIN")`
   能阻止越权,却阻止不了两端互相**覆盖** —— 那是 Cookie 名字与路径的职责。

**实现侧**:`JwtProperties` 增后台四项配置;`AuthCookies` 全部方法改为**按主体**出票
(写死一个名字正是两端覆盖的成因);`JwtAuthenticationFilter` **按请求路径**选读哪条
access Cookie;`AuthSessionService.refreshAccess` 增**主体类型校验**(第四道);
`AdminAuthController` 增 `refresh`。

> **顺带加固**:`refreshAccess` 原先只校验"签名 + 类型 + 黑名单"三道。两端分离后,
> 把 C 端刷新令牌塞进后台 Cookie 名里就能换到一个写在 `admin_access_token` 下的令牌 ——
> 签名与类型都对,只有主体不符。虽然最终仍会被 `ROLE_ADMIN` 挡下,但那不该是唯一防线。
> 故新增第**四**道主体校验,并有测试锁住。

### 25.3 校验

**契约侧**:`bash outputs/scripts/ci.sh` → **EXIT=0**,7 门禁全 PASS。
(T-4 只校验 C 端文档,故新增 `adminCookieAuth` 不影响它。)

**Java 侧**:`mvn test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true`

```
Tests run: 277, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

269 → 277,本轮 +8:

| 断言 | 锁住的判据 |
| --- | --- |
| `adminCookiesUseDistinctNames` | 后台两票用**完全不同**的名字 |
| `adminRefreshPathCoversLoginEndpoint` | 后台 refresh 的 Path **覆盖登录端点**(缺口 B 的根因) |
| `userAndAdminCookiesNeverCollide` | 四张 Cookie 的(名字, 路径)**两两不同** —— 这是"可并存"的充要条件 |
| `adminLogoutRevokesRefreshToken` | 由"钉住缺口"改为**验证修复**:登出后令牌确实被拉黑 |
| `refreshIssuesNewAccessToken` | 后台刷新链路端到端(且**不轮换** refresh) |
| `refreshWithoutCookieIsUnauthenticated` | 缺 Cookie → 10002 |
| `refreshTokenOfAnotherSubjectIsRejected` / `userRefreshTokenCannotRefreshAdminSession` | 主体类型不符必须拒绝 |
| `adminLogoutDoesNotClearUserCookies` | 后台登出**不动** C 端会话 |

**⚠️ 连带影响(必须记录)**:两端 Cookie 改名后,**所有后台集成测试的取票辅助方法都要改** ——
不改会集体 401。实测首次重跑即 **100 条失败**,全部是同一原因。已逐一改为
`admin_access_token`。**C 端测试保持不变**(它们本就用 `access_token`)。
`SecurityFilterContractTest` 里三条"业主令牌访问后台接口"的用例也做了调整:
令牌需放进**后台**的 Cookie 名里才能被过滤器读到、进而被 `ROLE_ADMIN` 拒成 403;
若仍放 `access_token`,后台路径根本不读它,得到的是 401 —— 那样测的就不再是
"ID 空间隔离",而是"这条 Cookie 没被读"。

**前端侧**:`npm test` → **73 passed / 6 文件**(+7);`npm run build`(含 `vue-tsc`)通过。

新增**令牌自动续期**:`http` 层在 **401** 或 **10003** 时调对应端的 refresh 端点并
**重放原请求一次**。三个必须处理的点:

- **403 不刷新** —— 那是"没权限",续期解决不了,只会白打一次请求;
- **刷新端点自身不触发刷新** —— 否则失败时无限递归;
- **只重试一次** —— 否则"刷新成功但原请求仍 401"会死循环。

> 没有这一步,缺口 B 只修了一半:后台确实拿得到 refresh 令牌了,却没有任何代码去用它。

**真实 HTTP 复测**(起真实 Java + MySQL,用 .NET `CookieContainer`,与浏览器同规则):

| 断言 | 结果 |
| --- | --- |
| 后台登录下发 `admin_access_token` / `admin_refresh_token`,Path 分别为 `/api/v1/admin`、`/api/v1/admin/auth` | PASS |
| **refresh 令牌这次被浏览器存下来了**(修复前被丢弃) | PASS |
| 调 `/admin/auth/*` 带上 `admin_refresh_token`;**调 `/admin/products` 不带**(收窄生效) | PASS |
| `/me` 返回当前账号;`/auth/refresh` 换到新的 access | PASS |
| **登出后该 refresh 立即失效** | PASS |
| **同浏览器先登 C 端再登后台:C 端 `access_token` 未被覆盖** | PASS |
| **登录后台后 C 端仍可用(修复前是 403 掉线)** | PASS |
| 同会话里后台接口同时可用 | PASS |

四张 Cookie 的实际归属(实测输出):

```
C 端 /auth/*         -> refresh_token, access_token
C 端 /products       -> access_token
后台 /admin/auth/*   -> admin_refresh_token, admin_access_token, access_token
后台 /admin/products -> admin_access_token, access_token
```

> `access_token`(Path=`/`)会随所有请求发出,后台路径下也在 —— 这是 Path=`/` 的固有含义。
> 但**名字不同**(`admin_access_token` 才是后台那条),过滤器按路径取,故不会串。
> 若要连这点暴露面也去掉,得把 C 端 access 的 Path 也收窄 —— 但那会改变 C 端的
> 既有契约行为,收益有限,本次不做。

### 25.4 过程中踩到的坑(记录以免重犯)

1. **改 `openapi-common.yaml` 的 securitySchemes 时把 `serviceToken:` 那行删了** ——
   它的正文挂到了新 scheme 下、键重复,被 G1/G2/G3/G7 当场拦下(`duplicated mapping key`)。
   这正是 CLAUDE.md 陷阱 2 说的"4 份文件里重复定义、改一处要同步另一处"的地带。
2. **`t_admin_user` 里的残留探针行**(上一轮跑挂在中途没清)导致主键冲突 ——
   该项目第 **6** 次遇到"共享可变前置数据"(前五次见 §12.2 / §16.5 / §17.6 / §21.6)。
   处置:清掉残留行后重跑。
3. **测试取票辅助方法 `loginCookies()` 会再登录一次** —— 于是"登出"拉黑的是第二次签发的
   令牌,而断言验的是第一次的,结论假阴性。已抽出 `cookiesOf(MvcResult)`,
   强制用**同一次**登录响应里的 Cookie。

### 25.5 未闭合

1. **C 端 access 的 Path 仍为 `/`** —— 它会随后台请求一起发出(见 §25.3 末注)。
   收敛它需改 C 端契约行为,收益有限,未做。
2. **前端只做了"静默续期一次"**,没有做"续期失败后统一跳登录页"的全局处理。
   当前是各调用点自己 catch —— 后台侧由路由守卫的 `ensureSession()` 兜底,
   C 端页面尚未实现,待 F-1.x 落地时统一处理。
3. **`admin_refresh_token` 的 14 天有效期没有做滑动续期** —— 与 C 端同口径
   (契约明确"只刷访问令牌,不轮换 refresh"),不是遗漏。

---

## 27. 内部契约 x-todo 评审:关闭 5 处决策点(2026-09-29)

### 27.1 为什么必须先把它们关掉

T-1.6 / T-1.7 / T-1.9 / T-1.10 这四张卡**全部从一条未评审的 `x-todo` 开始**:

| 任务 | 卡在哪条 |
| --- | --- |
| T-1.6 AI 咨询 | `/internal/agent/chat` —— 除冻结的四类事件外**是否还有 error** |
| T-1.7 户型录入 | `/internal/agent/parse-layout` —— 入参/出参结构、`text` 与 `params` 优先级、失败如何表达 |
| T-1.9 部位补齐 | `/internal/agent/complete-parts` —— `40004` 是内部码还是对外码 |
| T-1.10 材质映射 | `/internal/agent/material-map` 响应结构与粒度;`/internal/furnish/prompts` 入参与返回 |

CLAUDE.md 把 `x-todo` 定义为**决策点而非自由发挥的空间** —— 所以先评审、再开工,
否则每张卡开工时都要停下来问一次。

### 27.2 五条裁决

| # | 决策点 | 裁决 |
| --- | --- | --- |
| 1 | 对话流的错误表达 | **加 `error` 事件,与 `[DONE]` 互斥**(error 必为最后一帧) |
| 2 | 户型解析 | **`params` 优先于 `text`**;出参就是 `LayoutModel`;**失败 10001 + `data.errors[]`,不返回半成品** |
| 3 | 材质映射粒度 | 返回**结构化片段** `phrases{material,surface,colorPattern,spec}`,去掉整段 `prompt`;**该端点是否真被调用留待 T-1.11 判定** |
| 4 | `40004` 归属 | **内部与对外同一套码**,Java 原样透传;**但只透传已定义业务码,未定义的折 90001** |
| 5 | 陈设缺失 | **降级不阻断**:空陈设继续出图 + WARN 日志 + `furnishConfigured: false` |

### 27.3 两处在复核时被推翻的原建议(值得回看)

**① 陈设缺失原拟返 30001 —— 与 §13.3 冲突。**

初版建议"未配置就报 30001",理由是"别静默出空图"。复核时发现:

- 架构 §13.3 明写「**降级不阻断主流程**」,而陈设缺失**是画质问题不是功能问题**;
- 字典里空间 × 风格有 **20 个组合**,后台很难配全。若未配置就失败,用户选中没配的组合时
  **直接看不到效果图**,而他会以为是自己操作错了 —— 让用户承担了本该后台承担的疏漏。

改为降级,并把"后台漏配"暴露给**监控与后台**(WARN + `furnishConfigured`)。

**② 材质映射的"该端点是否被调用"不该现在定。**

初版只谈响应粒度。复核时意识到:`material-map` 是 Java→Python 的端点,
而**"场景组装"若就在生成消费端(Python 内部),Java 根本不需要绕这一圈** ——
那这个端点就是死的。它取决于 T-1.11 的实现位置,**我没有依据现在判断**。
故:定响应形状 + 标注「预留」,把判定留到 T-1.11。

> 教训:五条建议在同一轮里给出,复看时两条有问题。其中 ① 是我引用了 §7.3 的半句
> ("结构化")却漏掉了另一条(§13.3"降级不阻断")—— **同一次评审值得做第二遍**。

### 27.4 顺带清掉的陈旧标记

这些 x-todo 所指的问题**早已裁决或实现**,只是标记没摘,留着会让后来人以为这里还没定:

| 位置 | 依据 |
| --- | --- |
| `openapi-c.yaml /auth/refresh` | 已实现且 `AuthIntegrationTest` 覆盖 |
| `openapi-c.yaml /products` 八参数 | **D-8 裁决**;Java `allEightFiltersWork` 覆盖 |
| `openapi-common.yaml PartSource` / `PartSelection.source` | **D-4 裁决**(`source ∈ {user, ai}`) |
| `openapi-common.yaml ErrorItem` | **决策 2 依赖它** —— Java 已实现并有断言锁定。不清理则决策 2 落不了地 |

`openapi-internal.yaml` 现在 **0 个 `x-todo`**。

**仍保留的 5 条**(对应任务未做,留着是对的):`/generate` 的 `planId`(T-1.11)、
`/plans/{id}/share` 返回结构(T-1.13)、以及 admin 侧两条导入模板相关的(其中一条还带着
"建议同时覆盖搭配/户型模板/陈设/报价参数的模板"这个未决建议)。

### 27.5 文档链

| 文档 | 改动 |
| --- | --- |
| `tile-ai-prd.md` | **未改**。本轮全是接口内部形态与降级规则,PRD 层面无对应描述 |
| `tile-ai-architecture.md` v2.1 → **v2.2** | §10.4 增**内部接口四条约定** |
| `openapi-internal.yaml` | 逐个端点写入裁决;**5 条 x-todo 全部清除** |
| `openapi-c.yaml` | `/agent/chat` 补 `error` 事件;`/layout/parse` 补口径;清 2 条陈旧标记 |
| `openapi-common.yaml` | `ErrorItem` / `PartSource` / `PartSelection.source` 清标记 |

### 27.6 校验

**契约侧**:`bash outputs/scripts/ci.sh` → **EXIT=0**,7 门禁全 PASS。

**过程中修掉一处自己造的缺陷**:给 `/internal/agent/chat` 补 `error` 示例时,
误加了**第二个 `responses:` 键** —— 被 G1 当场拦下(`duplicated mapping key`)。
与 §25.4 记的 `serviceToken:` 那次是同一类错误(重复顶层/次级键)。

**实现侧**:本轮只改契约与文档,**未改动任何代码**,故未重跑 Java / Python / 前端测试。
下一步 T-1.7 开工时按新契约实现。

---

## 28. T-1.7 户型录入(2026-09-29)

### 28.1 产出

| 侧 | 文件 | 内容 |
| --- | --- | --- |
| **Python** | `app/agent/layout_parse.py` | 自然语言 → `LayoutModel`;**用 function calling 拿结构化输出**;校验不齐即失败 |
| | `app/api/internal.py` | 实装 `POST /internal/agent/parse-layout`(此前是骨架注释) |
| **Java** | `common/agent/AgentProperties` | Python 服务地址/令牌/超时 |
| | `common/agent/AgentClient` | **Java 侧第一个出网口**:服务令牌 + traceId 透传 + 错误码白名单透传 |
| | `layout/LayoutParseService` | 两条路径:params 就地处理、text 交 Python |
| | `layout/api/CLayoutController` | `GET /layout/templates`(仅上架)、`POST /layout/parse` |
| | `layout/LayoutTemplateService.pageOnSale` | C 端模板列表(只返回上架) |

**两处公共类的加法改动**(为透传 `data.errors[]` 所需,均向后兼容):
`BusinessException` 增可选 `data`;`ErrorCode` 增 `of(int)` 按码查找;
`GlobalExceptionHandler.handleBusiness` 把 `data` 带进响应体。

### 28.2 三个设计决定

**① 用 function calling,不"让它吐 JSON 再解析"。**

提示词里写"请输出 JSON"然后去捞,是这类任务最常见的做法,也最脆 ——
模型多写一句解释就整条断。改用工具调用:把 `LayoutModel` 的 schema 作为工具参数,
模型按 schema 填,拿到的一定是合法结构。

**② `params` 在 Java 就地处理,不绕 Python。**

结构化表单不需要模型。走模型等于花一次调用换一次"可能解析错",还多一个故障点。
校验用 `LayoutSpace` 上**已有的** jakarta 注解(控制器 `@Valid`),失败自动产出
10001 + 字段明细 —— 与自然语言路径的失败形状一致。

**③ 面积派生只在 Java 做一次。**

`areas` 是服务端派生字段,而派生公式的实现**已经在 `LayoutAreaCalculator`**。
故 Python **刻意不派生**(`tests/test_layout_parse.py::test_endpoint_does_not_derive_areas`
明确断言响应里没有 `areas`),两条路径最终都经过 Java 那一个实现 ——
避免两份公式迟早不一致。

### 28.3 校验

**Python 离线**:`pytest -q` 全绿。`test_layout_parse.py` **21 条**覆盖校验、解析、端点三层:

| 断言 | 判据 |
| --- | --- |
| 逐个抽掉必填字段都被抓住 | 不返回半成品 |
| 空 `spaces`、非正数、未知空间类型、开口缺尺寸 | 同上 |
| 模型不调工具 / 参数非法 JSON → 失败 | 不"尽力解析" |
| 端点:`params` 有值时**不调模型** | 见设计② |
| **端点响应里没有 `areas`** | 见设计③ |
| **网关不可用 → 90001,不是 10001** | 服务故障不能伪装成"你的描述有问题" |
| traceId 透传 | §13.7 |

**Python 真实联调**(`RUN_LIVE_LLM=1`):**3 条全过**

```
"卫生间 2米×1.8米,层高 2.7 米"
  → {'type': 'bathroom', 'length': 2, 'width': 1.8, 'height': 2.7}
"三室两厅,客厅 5m×4m,主卧 4m×3.5m,次卧 3m×3m" → 识别出 3 个空间
"我想要一个大一点的客厅,采光好一点" → **正确拒绝**(没尺寸不编造)
```

**Java**:`CLayoutIntegrationTest` **11 条**(`AgentClient` 为替身);全量 **288 passed**
(277 → +11)。

**真实跨进程联调**(起 Python + Java,走真实 DeepSeek):

| 场景 | 结果 |
| --- | --- |
| 自然语言 → 结构化 | ✅ `code=0`,**1275ms**;bathroom 2.0×1.8×2.7 |
| Java 派生面积 | ✅ floor=3.6、wall=20.52 |
| 多房间 | ✅ 3 个空间(客厅 + 主卧 + 次卧) |
| **无尺寸描述** | ✅ **10001 + `data.errors[]`**(不编造尺寸) |
| `params` 路径 | ✅ 不走 Python,面积照常派生 |
| `params` 缺尺寸 | ✅ 10001 |
| 模板列表 | ✅ **只返回上架**(建了 1 在售 + 1 下架,只看到 1 条) |

### 28.4 顺带修掉一处**测试脆弱性**(第七次"共享可变数据")

全量回归时 `CEndBrowseIntegrationTest` 的 3 条搭配用例挂了 ——
**但代码没问题,是我灌的演示数据撞的**。

它断言 `assertEquals(1, list.size(), "只应返回上架的那一条")` ——
**隐含假设"整张表只有本测试的数据"**。库里一旦有真实运营数据(或任何人灌过演示数据),
断言就崩,而实现完全正确。同一测试里还有 `list.get(0)` 取"第一条",
第一条是别人的数据时,它验的就不是自己要验的东西了。

**已改为只对自己造的数据下结论**(按 id 找自己那条、判它在不在,不判全表条数)。

> 这是本项目**第七次**遇到"共享可变前置数据"(§12.2 / §16.5 / §17.6 / §21.6 记过六次)。
> 前六次的教训都是"**必须显式重置**",但这一条重置救不了 ——
> **你没法把共享库重置成"只有我的数据"**。
> 真正的规矩是:🎯 **断言只针对自己造的数据,不对全表状态下结论。**

### 28.5 未闭合

1. **前端户型录入页(F-1.5)未做** —— 后端接口已通,页面属另一张卡。
2. **空间类型取值未校验字典** —— Python 按契约 `SpaceType` 的**内置默认值**校验;
   D-14 说真实可选集由字典决定,若后台加了新空间(如 `storage`),解析会判为"未知类型"。
   当前字典与内置值一致(5 个 vs 8 个默认值),**尚未暴露**;等字典扩充时需一并处理。
3. **未做超时/降级的实测** —— `AgentProperties.timeout=30s`,Python 不可用时 Java 折 90001
   (有单测),但**没有真的把 Python 停掉试一次**。
4. **模板与产品的 C 端筛选项未做联动**(如"按面积区间"目前要用户自己填)。

---

## 26. 图像模型实测:纠正一个从未验证过的协议假设(2026-09-29)

### 26.1 为什么这次实测非做不可

§23.6 只验了 **LLM**;而"把瓷砖铺进你家的效果图"靠的是**图像模型** —— 它才是产品本身。
当时图像适配器是按"OpenAI 兼容 + `image_urls` 字段"**推断**写的,文件头也如实标了
"尚未与厂商核实"。这次拿到百炼密钥后实测,**推断是错的**。

### 26.2 实测结果(4 次真实调用)

| | 原推断(错) | **实测(对)** |
| --- | --- | --- |
| 端点 | `{base}/images/generations` | `{base}/api/v1/services/aigc/multimodal-generation/generation` |
| 请求体 | `{prompt, image_urls:[…]}` | `{model, input:{messages:[{role,content:[{text},{image}…]}]}, parameters:{size,n}}` |
| 响应 | `data[].url` | `output.choices[].message.content[].image` |
| 协议 | OpenAI 兼容 | **DashScope 原生** |

> 架构 §7.2 原文写"适配器**统一走 OpenAI 兼容协议**"。**该结论对图像不成立**,
> 已在 v2.1 修正,并把教训写进 §7.2 —— 把"协议统一"当成既定事实写进文档,
> 差点让一个从未验证过的假设直接进生产。**"文档里写了"不等于"验过了"。**

### 26.3 顺带测出的六条事实(都影响后续设计)

| 事实 | 影响 |
| --- | --- |
| **参考图上限 9 张**,4 张实传被接受 | ✅ **§15.3 的生产路径成立** —— 这是本次最想验证的假设,它若不成立,PRD 决策 #11 的"多砖多部位"就要收窄 |
| 1K 文生图 **24.4s**、4 图 **10.6s**、`n=2` **27.3s** | ✅ §7.3 / §13.1 的 **≤60s 可达**(搜索到的"1–2 分钟"是通用/最坏口径) |
| 参考图**用 URL 即可**,不必 base64 | ✅ 与"图片一律存 URL"的架构约定一致 |
| **每张参考图约 5000 视觉 token**(4 张 ≈ 2 万 input) | ⚠️ 成本**不能只按"每张图多少钱"算**;参考图张数直接进账 |
| 出图是 **OSS 临时 URL,24 小时过期** | ⚠️ T-1.11 须及时转存对象存储 |
| `n>1` 时**每张图各占一个 `choices` 元素** | ⚠️ 只读 `choices[0]` 会**少拿图却不报错**(usage 仍按实际张数计费)—— "付了钱没拿到东西" |

最后一条是实测才发现的:第一次探针 `n=2` 返回了 `usage.image_count=2`,
但 `content` 里只解析出 1 张 —— 因为多出来的那张在 `choices[1]` 里。

### 26.4 产出

| 文件 | 改动 |
| --- | --- |
| `app/gateway/dashscope_image.py` | **新增**。DashScope 原生适配器;请求构造与响应解析为纯函数;参考图超限**早失败并说清楚** |
| `app/gateway/openai_compat.py` | **移除**那版未核实的图像实现(LLM 侧保留,它确实是 OpenAI 兼容) |
| `app/gateway/factory.py` | 图像预置 base_url 由 `/compatible-mode/v1` 改为 `https://dashscope.aliyuncs.com` |
| `tests/test_gateway.py` | 图像断言全部按**真实响应形状**重写;新增"形态是原生而非 OpenAI 兼容"的钉子 |
| `tests/test_gateway_live.py` | **新增 3 条图像真实联调**(出图 + ≤60s、4 张参考图、`n=2` 拿满 2 张) |
| 架构 §7.2 / §15.1 | 协议纠正 + 实测结果表;修订记录 v2.1 |

### 26.5 校验

**离线测试**:`pytest -q` → **61 passed, 8 skipped**(新增 4 条,改写 3 条)。

**真实联调**:`RUN_LIVE_LLM=1 pytest tests/test_gateway_live.py` → **8 passed**(87s)

```
1K 文生图用时 24.4s(PRD 上限 60s)
usage = 29 in / 2 out
4 张参考图用时 10.6s;input_tokens = 20061
n=2 -> 拿到 2 张
```

**契约门禁**:`bash outputs/scripts/ci.sh` → **EXIT=0**,7 门禁全 PASS(契约未改动)。

**Java / 前端**:本次不涉及,未重跑。

### 26.6 未闭合

1. **异步模式未验**:文档说 4K 或长提示词可能超时,届时需 `X-DashScope-Async: enable`
   + 轮询 `/api/v1/tasks/{task_id}`。一期按同步实现(1K/2K 实测都在 28s 内),
   **真要上 4K 时必须复验**。
2. **出图质量未评估**:本次只验"能不能出图",没验"出得像不像" ——
   纹理/颜色还原度、空间结构一致性、坏图率都属 §15.1 的 P0-2 残留,
   **需要真实产品样本**(依赖 P0-4 数据盘点)。
3. **`seed` 参数未使用**:万相图像接口未见该字段,适配器保留形参以满足 §7.1 签名
   但**不发送**。故"同输入出图方差"目前**不可控**,只能靠交互兜(PRD 已允许可选多角度)。
4. **成本计量仍按"每张图"计**:参考图带来的视觉 token 未计入。
   已记录,待 T-2.3 做可观测时一并处理。

---

## 30. T-1.11 开工前的三处口径(2026-09-30)

T-1.11 生成编排是**全项目最硬的一块**。开工前先把三处会影响接口与实现位置的口径定下来,
避免做到一半回头改。

### 30.1 三处裁决

| # | 决策点 | 裁决 |
| --- | --- | --- |
| 1 | 场景 / 提示词组装放在哪 | **放 Python**(生成消费端内部) |
| 2 | 替身图出几张 | **跟随请求的 `angles`** |
| 3 | `/generate` 的 `planId` | **保留并立即实现**(upsert) |

### 30.2 ① `material-map` 端点:确认为「当前无调用方」

§27.3 ② 留下的悬念在此关闭。既然场景组装在 Python 生成消费端内部完成,
**Java 就没有理由绕一圈去问材质短语** —— 端点 1 是死的。

**契约不改**:端点保留并标「预留」。理由是"当前无调用方"不等于"永远不该有" ——
若日后 Java 在**受理时**就要校验材质可用性(如选中的 SKU 缺材质短语,想在受理阶段
就拒掉而不是等到出图失败),它仍是自然落点。

> 这条**只关闭"要不要调它"**,不关闭"它的响应结构对不对" —— 后者已在 §27 裁决。

**影响面**:T-1.12 之后的实现不依赖它;`openapi-internal.yaml` 无需改动。

### 30.3 ② 替身图张数 = 请求的角度数

§29 已定"图像用替身",此处补齐**替身的行为口径**:替身**不是返回固定一张图**,
而要**按 `angles` 决定张数**。

理由:若替身恒定返回一张,"多角度"这条路径在替身下**永远走不到** —— 等换成真模型时
才是它第一次被验证,等于把风险推迟到最后一刻。替身的价值正是"让链路其余部分先跑通",
那就得让张数这个变量也真实存在。

### 30.4 ③ `planId`:关闭 x-todo,并定下 T-1.11 / T-1.12 的分界

`openapi-c.yaml` 的 `GenerateRequest.planId` 原挂一条 x-todo:

> §10.6① 示例无 planId,但 §11.4 时序显示覆盖原方案时携带 planId;此处按需补充,待确认字段名

**裁决:保留该字段并立即实现。**

| 请求 | 行为 |
| --- | --- |
| 带 `planId` | 生成完成后**覆盖**该方案(upsert) |
| 不带 | **新建**方案,新 id 经 `TaskProgress.planId` 返回 |

**依据**:架构 §11.2 时序图已写明"保存效果图 + 触发方案自动保存(**upsert**)"。
upsert 天然需要一个"要更新哪一条"的标识 —— 不留这个字段,upsert 就无从实现。
若不实现而总是新建,用户改一版再生成就会累积出多份近乎重复的方案。

**分界(重要,争议点在此)**:

| 归属 | 内容 |
| --- | --- |
| **T-1.11 本条任务** | 受理时的 `planId` 落库;**生成完成后的自动保存 / 覆盖** |
| **T-1.12** | 方案的列表 / 详情 / 重命名 / 删除 / 另存为;`/plans/{id}/copy` 也由它提供 |

即:另存为这条分支由 T-1.12 先 `copy` 出新 id,T-1.11 只消费这个新 id。

### 30.5 校验

**契约侧**:`bash outputs/scripts/ci.sh` → **EXIT=0**,7 门禁全 PASS
(T-1~T-15:PASS=14 / FAIL=0 / SKIP=1,SKIP 仍是 T-12 无实现侧导出)。

`openapi-c.yaml` 的 `x-todo` 由 **2 条减为 1 条** —— 余下的是
`/plans/{id}/share` 的返回结构,属 T-1.13,不在本轮范围。

**实现侧**:本轮只改契约与文档,**未改动代码**,故未重跑 Java / Python / 前端测试。

---

## 29. 阶段性收缩:先「完全跑通」,图像暂用替身(2026-09-30)

### 29.1 决策

**在整条主链路完全跑通之前,不接真实图像模型。**

| 项 | 决定 |
| --- | --- |
| 图像生成 | 改用**替身实现**(返回固定占位图),链路照跑 |
| 出图质量评估 | **不做** —— 纹理还原度 / 空间结构一致性 / 坏图率一概不评 |
| 4K / 异步 / `seed` | **不测**(原 §26.6 的三条未闭合项整体推迟) |
| 真实模型接入与质量验收 | 推迟到链路跑通之后单独安排 |
| 本期目标 | 选砖 → 录户型 → 点生成 → **看到图**,端到端走通 |

**备案(§22.4)同步不启动** —— 它只挡"上线",不挡"跑通";用户明确当前只要求跑通。

### 29.2 原因

**百炼账户欠费(`Arrearage`),真实图像生成当前不可用。这是账户问题,不是代码问题**,
不该由它卡住主链路开发。模型能力本身已验证存在(协议、参考图上限、耗时均实测过,§26),
缺的只是账户额度。

> 这不是"图像不做了",而是**把图像从关键路径上摘下来**。

### 29.3 为什么这样做不欠债(可逆性)

替身与真实适配器是**同一接口的两个实现**,由配置 `MODEL_IMAGE_PROVIDER` 选择 ——
与 T-0.13 的 **Embedding 占位**是同一模式:`build_embedding_gateway` 在 provider
为空 / `placeholder` 时返回确定性占位(`app/gateway/factory.py`)。

**换回真实模型只改配置,不动调用方代码。** 这是"暂用替身"能成立的前提 ——
它不制造分叉的实现路径,只留一个待填充的插槽。

替身本身**随 T-1.11 落地**(目前网关还没有调用方,T-1.5 的产物尚未接入 FastAPI)。

### 29.4 影响

- **§23.6 / §26 的图像真实联调结论继续有效** —— 协议形态、参考图上限、24h 临时 URL、
  `n>1` 的 `choices` 分布,这些都是**实测事实,仍然是真的**,只是**不再是当前实现路径**。
  后来人不要因为"现在用替身"就以为那些结论被推翻。
- **T-1.11 生成编排按替身开发** —— 状态机、Outbox、幂等、补偿扫描这些**编排逻辑与
  图像真假无关**,照常实现与验收。
- **"图像 URL 24h 过期 / 对象存储未选型"不再阻塞 T-1.11 的跑通目标** —— 替身不产生
  外部临时 URL。但 **T-1.12 方案管理要保存封面时仍会碰它**,届时须先定存储。
- **上线前的图像质量验收(路线图 CP-1)仍待办**,与本决策无关。

### 29.5 恢复条件

链路完全跑通后,按序恢复:① 充值或更换图像供应商 → ② 接上真实图像适配器
(代码已存在,§26)→ ③ 用真实产品样本做出图质量验收(依赖 P0-4)。

---

## 31. T-1.10 材质映射与陈设提示词组装(2026-09-30)

### 31.1 落地与校验

`app/agent/prompt.py`(Python 内部模块,无 I/O)+ `tests/test_prompt.py` 28 条。
**契约零改动** —— 本任务不改任何 YAML,故不触及文档链四步。

与 §30 的关系:§30.1 裁决 ①「场景/提示词组装放 Python」在此落地;
`material-map` 端点仍为「预留、无调用方」(§30.2),本模块走内部函数而非 HTTP。

对照任务卡的四条验证:

| 任务卡验证项 | 怎么验的 |
| --- | --- |
| 不同部位的砖**对号入座** | 断言“地面那块砖的短语不会出现在墙面行”;输出恒按 地面→墙面→局部 排序 |
| 陈设**取自配置、不硬编码** | 模块内**无陈设字面量**;注入假配置断言原文逐字出现在提示词里 |
| 陈设**不计入报价** | 三条断言:价格字段不进短语、价格数字不出现、结果结构里没有金额字段 |
| 契约枚举对齐 | 断言内置空间/风格/部位标签的键集 == `common` 的 `SpaceType` / `StyleType` / `Part` |

未验证项(留待 T-1.11):Python → Java 的真实 HTTP 取数(P0 阶段 Java 侧
`/internal/products/{sku}`、`/internal/furnish/prompts` 尚未实装),故本模块的
取数接缝只有假体测试。

### 31.2 发现并修掉一处分叉:开口类型(契约 ↔ 实现)

`app/agent/layout_parse.py` 的 `OPENING_TYPES` 原为
`("door","window","bay_window","other")`,但:

- 契约 `openapi-common.yaml#/components/schemas/OpeningType` 枚举为 **`[door, window]`**;
- 架构 §8.3「结构化户型模型字段定义」表的 `openings[].type` 同样只列 **`door` / `window`**。

多出的两个值**只存在于实现里**,且被用进了给模型的工具 schema(enums)与本地校验,
于是产生一条静默的失败路径:模型照 enum 填 `bay_window` → Python 校验通过 →
Java 侧 `LayoutModel` 校验拒收。**已收敛到契约枚举两值。**

这条记录的意义不在这一行代码,而在于:**它是 T-12 要防的那类分叉,而它已经发生过一次**
—— 且发生于实现期,不是契约期。T-12(契约 ↔ 实现双向 diff)仍未落地。

### 31.3 发现一处口径冲突(挂起待裁):`angles` 到底出几张图

> **已于同日裁决 → 见 §32**(选 B:`angles` = 生成张数)。以下保留当时的登记原貌。

两处文字对不上,**未擅自择一**:

- **契约** `common.PlanImage.view`(D-5 已采纳)写的是:「`angles=1` 时仅 `default`;
  `angles=N` 时 `default` + `angle1`…`angleN`」。**逐字执行 ⇒ `angles=N` 出 N+1 张图。**
- **§30.3** 写的是:「替身图张数 = **请求的角度数**」⇒ `angles=N` 出 N 张图。

`app/agent/prompt.py` 的 `views_for_angles` 目前**逐字落地契约**(N+1),
因为契约在上游、§30.3 在本文件的记录层。但两者必须择一,否则 T-1.11 按
`angles` 出图时会与提示词条数不一致。

两个候选口径:

| 口径 | 含义 | 代价 |
| --- | --- | --- |
| A(维持契约字面) | `angles` = "额外角度数",总数 = N+1 | `angles` 的含义与名字不符,且默认 1 时总数为 1、非默认时又变成 N+1,不自洽 |
| B(改契约) | `angles` = 出图张数;`angles=N` 时视角为 `default` + `angle1`…`angle{N-1}` | **要改契约**(`PlanImage.view` 的约定 + 可能连带 `GenerateRequest.angles` 的描述),须走文档链四步 |

**倾向 B**:`GenerateRequest.angles` 的描述是「生成角度数。默认 1(单张)」,
B 与之自洽,而 A 让"角度数"与"张数"永远差一。但 B 改契约,**不是实现层能定的**,
故此处只登记,待裁决后再动 —— 改动点仅 `views_for_angles` 一处(若选 B,
还需同步 `common.PlanImage.view` 的约定文字)。

> 另:视角的**语义**(`angle1` 与 `angle2` 在画面上差在哪)上游从未定义,
> 契约只定了命名。`prompt.VIEW_PHRASES` / `ANGLE_PHRASE` 是惯例推断的**单一常量**,
> 与 `x-todo` 同性质。

---

## 32. 裁决:`angles` 是「生成张数」(D-5 修订,2026-09-30)

### 32.1 问题

§31.3 登记的口径冲突。契约 `common.PlanImage.view` 的命名约定写作
「`angles=1` 时仅 `default`;`angles=N` 时 `default` + `angle1`…`angleN`」,
照字面执行 ⇒ **`angles=N` 出 N+1 张图**,而 `angles` 自己的描述是
「生成角度数。默认 1(单张)」—— 名字与张数永远差一。

### 32.2 裁决

**`angles` = 生成张数。** `angles = N` 出 **N** 张:

| 第几张 | 视角名 |
| --- | --- |
| 1 | `default` |
| k(2 ≤ k ≤ N) | `angle{k-1}` |

故 N 张的视角为 `default` + `angle1`…`angle{N-1}`;N=1 时仅 `default`。

### 32.3 这不是"契约 vs 实现"的选择题

关键事实:**上游两处本来就说 N 张**,分叉只在契约这一层。

| 上游 | 原文 |
| --- | --- |
| PRD §5.4 | 「**默认每个空间生成 1 张**;用户**可选"多角度"**,生成多张不同视角的效果图」 |
| PRD §11 决策 #9 | 「单空间**默认 1 张**,**可选多角度**」 |
| 架构 §11.2 时序 | 「`P->>M: 生成(默认 1 张 / 多角度 N 张)`」 |

三处都是"张数 = 角度数"。**契约的命名约定是唯一说 N+1 的地方** ——
所以本次是**把契约改回它自己的上游**,而不是让实现去迁就一处写错的约定。
若反过来让实现跟随契约(N+1),等于让一处孤立的契约笔误反过来推翻 PRD 与架构。

### 32.4 改动清单(文档链四步)

| 步 | 文件 | 改动 |
| --- | --- | --- |
| ① | `tile-ai-prd.md` §5.4 | 补一句「**张数即所选角度数**(不是 N+1),视角命名见架构 §8.4」;§11 决策 #9 同步 |
| ② | `tile-ai-architecture.md` §8.4 | `t_plan_image.view` 的命名约定改写为 `default` + `angle1`…`angle{N-1}`,并注明修订缘由 |
| ③ | `openapi-common.yaml` | `PlanImage.view` 的描述改写(契约里唯一的 N+1 出处) |
| ③ | `openapi-c.yaml` | `GenerateRequest.angles` 的说明改为「**生成张数**…**张数 = angles,不是 angles+1**」 |
| ③ | `openapi-internal.yaml` | `generate/callback` 的 `imageUrls[].view` 改为引用 `common.PlanImage.view` 的约定(原为 `default / angle1 …`) |
| ③ | `asyncapi-internal.yaml` | `GenerateTaskPayload.angles` 的说明同上 |
| ④ | 本文件 | 本节 |

### 32.5 影响面

- **实现**:`app/agent/prompt.py` 的 `views_for_angles` 与其测试同步更新,
  并新增"**张数 == 角度数**"的参数化断言(`angles ∈ {1,2,3,5}`)—— 把口径钉在测试里,
  下一处实现不会再自行解释一遍。
- **T-1.11**:§30.3 已定「替身图张数跟随请求的 `angles`」,**无需改动** ——
  两处口径现在才真正自洽(这正是 §30.3 当时与契约打架的地方)。
- **前端**:结果页按 N 张渲染,不按 N+1。
- **数据**:`view` 是字符串,无 schema 变更、无迁移。当前尚未上线,
  **无历史数据**需要回溯重命名。
- **门禁**:改动 4 份契约 YAML,须跑 `outputs/scripts/ci.sh`(7 门禁全过)。

### 32.6 未覆盖

视角的**语义**(`angle1` 与 `angle2` 在画面上差在哪)上游仍未定义 ——
契约只定了命名。当时实现里只有一句惯例推断的兜底措辞,**仍是待定项**。
→ **已于同日定下,见 §33。**

---

## 33. 视角语义:同一张照片的不同机位(2026-09-30)

### 33.1 裁决

§31.3 末 / §32.6 挂的最后一个口子在此关闭。用户口径:

> 画面的差异**应该是同一张照片的不同角度** —— 机位由实现方定。

即:`angles > 1` 不改变场景(空间、风格、陈设、瓷砖全都不变),**只换相机**。
这条不是可选的实现自由 —— 若每张图各自重新布置房间,"多角度"就退化成
"同风格的几张图",用户拿到的不是同一个方案的多个视角,而是几个不同的方案,
与 PRD §5.4「生成多张**不同视角的效果图**」不符。

### 33.2 四个机位(按信息量排序)

| 视角名 | 机位 |
| --- | --- |
| `default` | 正面平视全景,镜头与视线同高 —— 一眼看全空间的定妆照 |
| `angle1` | 从房间一角斜向俯视,同时看到地面与两面墙 |
| `angle2` | 高位俯瞰,重点呈现地面铺贴 |
| `angle3` | 贴近墙面拍摄,重点呈现墙面瓷砖的纹理与光泽 |

**顺序有业务含义**:用户只要 1 张时(默认),拿到的必须是最有信息量的那张,
而不是随机一个机位 —— 所以 `default` 是全景而非某个特写。

表外的机位(`angle4` 及以上)走兜底措辞 `"换一个拍摄角度"`,**不臆造新机位**,
也**不重复**已有措辞(重复的机位只会得到一张近乎一样的图,白花一次 60s + 一次计费)。

### 33.3 不变量写进提示词

多角度时,每条提示词的视角段附加:

> （同一空间的同一组瓷砖,仅拍摄角度不同）

单张时**不附**(没有"不同的角度"可比)。这条不变量必须显式给模型 ——
图像模型对"换个角度"的自然理解是"重画一张",而不是"同一张挪个机位"。

### 33.4 落点与改动

| 步 | 文件 | 改动 |
| --- | --- | --- |
| ② | `tile-ai-architecture.md` §7.3 | 补"视角 = 同一场景的不同机位"+ 四个机位 |
| ④ | 本文件 | 本节 |
| — | `app/agent/prompt.py` | `VIEW_PHRASES` / `ANGLE_FALLBACK_PHRASE` / `MULTI_ANGLE_CLAUSE`;`view_phrase` 提升为公开函数 |
| ④ | `tile-ai-tasks.md` | T-1.10 进度块同步 |

**契约无需改动** —— 契约只定命名(D-5),语义本就不在其中,
故本次**不涉及 openapi-\*.yaml**,门禁不受影响。

### 33.5 未覆盖 / 待决

- ~~**`angles` 无上界**(契约只有 `minimum: 1`,没有 `maximum`)~~
  **已于同日裁决,见 §34。**
- 机位措辞是**中文自然语言**,未做图像模型侧的效果验证(本阶段用替身,§29)
  —— 真实出图时"斜向俯视"能否稳定复现,待恢复真实模型后一并验收。

---

## 34. 裁决:`angles` 上限 = 4(2026-09-30)

### 34.1 问题

§33.5 登记:契约 `GenerateRequest.angles` 只有 `minimum: 1`,**没有 `maximum`**。
`angles` 是**乘性成本** —— 每个角度 = 一次真实出图(单张 ≤60s + 一次计费)。
无上界意味着客户端的笔误(或滥用)能让一次请求排出任意多张图,
把生成队列堆满、把额度烧掉。这类"参数本身没有恶意,但代价随参数线性放大"的输入,
在契约层收口比在下游拦更可靠 —— 下游各有各的拦截,迟早漏一处。

### 34.2 裁决

**`angles` 上限 = 4**,与已定义的机位数一致(§33.2)。

| 口径 | 值 | 理由 |
| --- | --- | --- |
| `minimum` | 1 | 原有;至少要一张 |
| `maximum` | **4** | = 已定义机位数;再多也只能重复机位或走兜底措辞,拿不到新信息 |

**为什么是 4 而不是更大的数**:上限不是拍脑袋定的容量,而是**"有多少个不重复的机位"**。
机位表(§33.2)有四个,所以四个是"还能给出新东西"的上界;第五张图无论怎么写,
要么重复已有表述(几乎一样的图),要么退到兜底措辞(含糊)。让用户为一个必然重复的
结果多等 60 秒、多付一次费,不是"更灵活",是把成本转嫁给他。

**校验失败返 `10001`**(参数校验失败),HTTP 仍 200 —— 走既有约定,不新增错误码。

### 34.3 改动清单(文档链四步)

| 步 | 文件 | 改动 |
| --- | --- | --- |
| ① | `tile-ai-prd.md` §5.4 / §11 #9 | 「最多 4 个角度」及其理由 |
| ② | `tile-ai-architecture.md` §7.3 | `angles` 上限 = 4,并注明**上限由机位数派生**:改机位数须同步改上限 |
| ③ | `openapi-c.yaml` | `GenerateRequest.angles` 加 `maximum: 4` |
| ③ | `asyncapi-internal.yaml` | `GenerateTaskPayload.angles` 加 `maximum: 4`(MQ 载荷与对外同口径) |
| ④ | 本文件 | 本节 |

### 34.4 实现侧的对应物:上限与机位表必须同步

`app/agent/prompt.py` 新增 `MAX_ANGLES = 4`,并有一条测试钉死:

```
assert MAX_ANGLES == len(VIEW_PHRASES)
```

理由:这两个数**必须相等** —— 上限的定义就是"有多少个已定义机位"。分开写死会漂:

- 加了机位、忘了改契约 `maximum` → 用户被挡在"其实已经支持"的角度之外;
- 改了契约、忘了加机位 → 多出来的那张图只能是重复机位或兜底措辞。

契约里没有"机位数"这个概念可引用,所以这个等式只能由测试来守。

**契约封顶后,`views_for_angles` 的兜底分支(`ANGLE_FALLBACK_PHRASE`)在正常路径上
走不到了** —— 保留为**纵深防御**:本模块也会被内部调用方直接使用,
不该因为对方没做校验就崩或默默越界。

### 34.5 未覆盖

- 上限是**常量**而非配置项。若日后要按套餐区分(免费 1 张 / 会员 4 张),
  需要把它挪进配置 —— 那属于新的产品决策,不在本次范围。
- 前端尚未实装角度选择器(F-1.x),故**没有实现侧的双重校验**;
  目前校验只在契约与 Java 侧(T-1.11 落地时)。

---

## 35. T-1.11 生成编排:实现前先落定的口径与阈值(2026-09-30)

T-1.11 是「点生成 → 看到图」这条异步管道。架构 §6.4 与 `asyncapi-internal.yaml`
把机制说清楚了(Outbox、状态机、幂等、补偿扫描),但**没有给一个数字**:
§6.4 只说「长时间停留」,AsyncAPI 只说「超过阈值」。同时核对代码时发现四处
文档文字与实现对不上。

**35.1–35.8 写于动手之前**,不是事后追认 —— 阈值先于实现存在,才不会反过来由实现定义。
**35.9 起是实现期间补记的**:方案稿没预料到的兜底(35.9)、实现与方案稿的一处偏差(35.10)、
以及本任务的明确边界(35.11)。

### 35.1 阈值表(上游全部只给了形容词)

| 参数 | 值 | 依据 |
| --- | --- | --- |
| `pending` 卡住阈值 | **60 秒** | 投递器每 2s 扫一次、退避 2/4/8…300s。**若该任务还有 `pending` 的发件箱行,就不算卡住** —— 说明投递器还在处理它,重投只会重复。一旦那行 `sent` 却没下文、或已 `failed`,静默 60s 就是真卡了 |
| `running` 卡住阈值 | **360 秒** | 最坏真实耗时 = 4 张 × 60s(§7.3 单图时限)= 240s,留 1.5 倍余量。**活着的任务扫不到**:每次进度回调都刷新 `updated_at`(见 35.2) |
| 重投上限 | `attempt < 3` | 每次重投都可能等于 4 次真实模型调用;到 3 次判 `failed`(40001) |
| 硬截止 | `created_at` 超 **30 分钟** | 对 `running` 分支的兜底 —— 任务若一直"活着"地卡着,阈值永远到不了 |
| 补偿扫描间隔 | **60 秒** | 它是安全网不是主路径;§13.7 的告警窗口是 5 分钟,60s 落在窗口之内 |
| Python 消费重试上限 | **2**(含首次共 3 次) | 与索引消费端的 5 不同:一次处理可能含 4 次真实生成调用,重试代价是索引同步的数倍;且网关层 `CallGuard` 自己已重试 3 次,消费端再叠 5 次等于三重放大。**长周期重投归 Java 补偿扫描**,不在消费端硬扛 |

> 表里只有「Python 消费重试上限」是 Python 侧常量,其余都在 Java 侧
> `GenerateProperties`(`tile.generate.*`),可配置。

### 35.2 `running` 由 Python 回调驱动,投递成功**不**翻状态

四个状态没有争议,有争议的是**谁在什么时候翻**。实现定为:

```
POST /generate        → pending, progress=0, 同事务落 t_outbox
投递器发出消息         → 仍为 pending     ← 故意不动
Python 开始处理        → 回调 running, progress=0
每出一张图             → 回调 running, progress=⌊100×已完成/总数⌋
全部成功              → 回调 success, progress=100, imageUrls[]
中途失败              → 回调 failed(40001)
```

**为什么不在「投递成功」时翻 `running`**:投递成功只意味着消息**进了队列**,
不等于 Python 开始干活。那时就翻,用户在队列积压时会看到一个不动的进度条。
让 Python 在真正开工时报 `running`,`progress` 才有意义。

**顺带定下一件事**:每次进度回调都写一次 `updated_at`。这正是 35.1 里
`running` 阈值敢定 360s 的前提 —— 「还活着」与「卡死了」的区别,就是
`updated_at` 有没有在被刷新。

### 35.3 失败任务的进度查询返 40001 **且带 `data`**

`openapi-c.yaml` 给 `GET /generate/{taskId}` 列了 40001 `GENERATE_FAILED`,
但 `TaskProgressView` 的 `status` 又是必填的。二者只能这样调和:

- 任务 `failed` ⇒ HTTP 200 + `code=40001` + **`data` 照常给出**(含 `taskId`、
  `status=failed`、`progress`、`error`)。

若失败时只返错误码不带 `data`,前端就拿不到 `status` 与失败原因 ——
契约里写的 40001 也就永远不可达。**这是对契约的补全而非偏离**:
`openapi-c.yaml` 该处本就未写明响应体有无,按上面读。

> 40003 `TASK_NOT_FOUND` 同理:taskId 不存在 / 不属于当前用户 ⇒ 200 + 40003。

### 35.4 `style`:请求里可选,消息里必填

`openapi-c.yaml` 的 `GenerateRequest.required = [space, layout, parts]` ——
**`style` 不在其中**;而 `asyncapi-internal.yaml` 的 `GenerateTaskPayload.required`
**含 `style`**。两边不一致,实现取:

- 请求未给 `style` ⇒ 落库与投递的 payload 里 `style = ""`(空串),提示词组装时
  `STYLE_LABELS` 查不到 ⇒ **退化为键本身**(即不加风格修饰),不报错、不阻断 ——
  与 T-1.10「内置默认值缺席就退化为键」同一口径。

即:**消息体的必填由 Java 保证,产品上的可选由 Java 补齐**。这样 Python 消费端
不必为「style 可能缺失」多写一条分支。

### 35.5 `attempt` 只有补偿扫描能递增,投递器不会

`GenerateTaskPayload.attempt` 的说明写的是「由 Java 投递器递增(架构 §6.4 指数退避)」,
但**投递器做不到这件事**:`OutboxDispatcher` 的设计原则是
**把库里存的 payload 原样发出,不在投递时重新构造**(见 `OutboxMessage` 的注释:
避免"落库时的数据"与"投递时的数据"不一致)。它只递增自己那张表的 `retry_count`,
不碰 `t_generation_task.payload`。

后果要说清楚:**MQ 层的重传与业务层的重投是两回事**。

| 谁 | 计数在哪 | 触发条件 |
| --- | --- | --- |
| `OutboxDispatcher` | `t_outbox.retry_count` | 投递到 MQ 失败(连接断、被拒绝) |
| `GenerateCompensationScanner` | payload 里的 `attempt` | 投递成功但任务卡住(没人消费 / 消费后失联) |

所以消息体里的 `attempt` 第一次总是 1,只有补偿扫描重投时才是 2、3。
契约那句说明按此订正在案(**契约 YAML 不动**,见 35.9)。

### 35.6 `layout` 加密:`package-info.java` 的注释与事实不符

`common/layout/package-info.java` 声称静态加密「落地见 T-0.9 / T-1.7」,
实际 T-1.7 只做了模板 CRUD 与解析,**Java 侧根本没有加密实现**。

T-1.11 存方案必须写 `t_plan.layout_enc`,故本任务补
`common/crypto/AesGcmCipher`(AES-256-GCM),密钥经环境变量注入
(**不落代码、不落库**,口径见架构 §13.5 字段加密),同时订正那段注释。

**这不是契约变更** —— `layout_enc` 的对外口径(存密文、传明文、
对外 schema 不得出现 `layout_enc`)早在 CLAUDE.md 里定死,只是实现迟迟没来。

### 35.7 §15.3 的「底图」没有承载表

架构 §15.3 定的生产路径是「多参考图条件生成」:参考图 = **1 张同空间类型底图**
+ 各部位瓷砖切片,合计最多 4 张。

核对下来:**瓷砖照片有表**(`t_product_image`)、**底图没有**。任何表、任何字段
都不存"卫生间底图"。故本期参考图**只放瓷砖照片**(1~3 张),底图缺位。

影响面有限:§29 本期图像走替身,替身不看参考图,有没有底图这次看不出差别。
但要说清 —— **换回真模型的第一件事就是要底图**,那时得先定它存哪
(新增 `t_space_base_image`?还是复用 `t_furnish_style.cover_image`?)。留作待定。

### 35.8 多空间户型:哪个空间的面积进报价,目前无规则

`LayoutModel` 允许一个户型含多个空间,而 `t_plan_quote` 是一行、
`t_plan.total_amount` 是一个数。**一个多空间户型用哪些面积算报价,上游没有规则。**

本期**不报价**(报价快照归 T-1.15),歧义随之不暴露。T-1.15 必须先把这条定了
再动手 —— 否则同一份户型在「按客厅算」和「按全屋算」下会给出两个金额。

**但面积快照这一刻就得取一个空间**(`t_plan_part.gross_area` 要落库),所以
T-1.11 先把「取哪个空间」定了 —— 它只决定**快照**,不决定报价取哪些空间:

| 顺序 | 规则 | 为什么 |
| --- | --- | --- |
| 1 | 户型只有一个空间 | 直接用它。用户录了个卫生间却把空间选成"其他"这类不一致,不值得让方案保存失败 |
| 2 | 取 `type` 与请求 `space` 相同的**第一个**空间 | 正常路径 |
| 3 | 都没有 ⇒ 取第一个 + WARN,**不报错** | 面积定不准是**报价**问题,而出图已经成功、模型调用已计费;为此把方案整个丢掉是最坏的选择 |

部位面积另有优先级:`parts[].area` 显式给了就用它(用户可能在录户型时人工核过),
否则取派生值。**局部部位派生出 `null` 而不是 `0`** —— `LayoutAreaCalculator` 对它的
定义就是"按实际铺贴、无默认值",那个 `0` 的含义是"推不出来"。存 `0` 会被后来人读成
"这块砖用了 0 ㎡",而 `null` 说的是"没定":报价遇到 `null` 会停下来要,遇到 `0` 直接
乘出 0 元。

### 35.9 方案自动保存的两条兜底

**① 覆盖目标在出图期间消失 ⇒ 退回新建,不报错。**
用户从点生成到出图完成之间把那个方案删了,完全可能。此时图已出好、模型调用也计过费,
因为"没地方存"把它丢掉是最坏的选择;而户型、风格、部位都在任务快照里,新建一个方案
什么都不缺。**代价:用户原来自起的名字随方案一起没了,退回默认名**(`卫生间 · 现代`)。
受理阶段(`assertOverwritable`)仍会为"不属于本人的 planId"返 30001 —— 那时还没花钱,
拒绝得越早越好;只有"受理时还在、出图期间没了"才走这条兜底。

**② 封面 = `view == 'default'`,且只有一张。**
D-5 修订后 `default` 是唯一确定的基准机位(§32 / §33),所以"哪张当封面"不需要
额外规则 —— 它由视角名直接决定。Python 侧按此填 `isCover`,Java 侧原样落库,
不自作主张挑一张。

### 35.10 替身图的 URL 路径:不是 `/static/...`

方案稿里写的是 `{java_base_url}/static/placeholder.svg`,**实现时改掉了**:

```
{java_base_url}/placeholder.svg?v={view}
```

因为 Spring Boot 把 `classpath:/static/` 映射到**根路径** —— 文件放在
`src/main/resources/static/placeholder.svg`,对外地址就没有 `static` 这一段
(本项目也未配置 `spring.mvc.static-path-pattern`)。

实测(启动本地实例,2026-09-30):

| 请求 | 结果 |
| --- | --- |
| `GET /placeholder.svg` | **200**,`Content-Type: image/svg+xml` —— 就是那张图 |
| `GET /static/placeholder.svg` | 不是图,而是 `{code:90001,"系统内部错误"}` |

按目录名拼 URL 的后果要说准:**这里不会得到一个 HTTP 404**,缺静态资源被
`GlobalExceptionHandler` 收成了业务错误 90001、HTTP 仍是 200(业务错误码统一 200 的
约定)。这比 404 更难查 —— "图明明在那儿"却报系统内部错误。

另外 `?v={view}` 里的视角名**不是新参数**,是替身从提示词里读回来的:
`ImageGenGateway.generate` 的签名没有视角参数(§7.1 定死,不能改),
而 `prompt.py` 的 `VIEW_PHRASES` 每个机位一句措辞、已拼进提示词。
替身"读提示词决定自己拍到哪个机位"正是一个替身**模型**该做的事,好处是 URL 里带的就是
视角名(`?v=default` / `?v=angle1`),与 Java 的封面规则对得上、肉眼也能核。

> 图**必须由 Java 提供**:前端只连 Java(不变量 3)。挂在 Python 上本地能跑通,
> 但存进 `t_plan_image` 的就是前端够不到的地址,T-2.4 上线会被网关拦掉。

### 35.11 未覆盖 / 不在本次范围

- **契约 YAML 不动**。35.4 / 35.5 记的是契约文字与实现的口径差,
  本任务不改 `asyncapi-internal.yaml` —— 按文档链,改消息体字段说明要走
  PRD → 架构 → 契约 → 本节记录四步,不能顺手改。**这两条留作待评审**。
- **`attempt` 与 MQ 的 `x-death` 计数不联动** —— 前者是业务层语义、后者是 broker 层,
  刻意不合并(合并会让"重投"与"重传"互相伪造对方的计数)。
- **索引消费端(`queue.index.sync`)仍不随服务启动**。本次只把**生成**消费端接进了
  `app/main.py` 的 `lifespan`(后台 daemon 线程)。索引消费端从 T-0.13 起就只被测试
  直接驱动 —— 原因不是"忘了",而是「**由谁托管它**」是个部署决定(一期最终由 T-2.4
  的编排决定),不该在这一轮顺手定下来。它是已知缺口,不是遗漏。
- **缩略图 / 图像转存对象存储**:§29.4 已推到 T-1.12。
- **SSE 进度推送**:一期 2s 轮询,二期再做(§6.3)。
- **DLQ 自动消费方**:一期没有,DLQ 消息数 > 0 告警即可(§13.7),
  与 §9.5 的 R-9(DLQ 粒度)呼应。

---

## 36. 契约 ↔ 实现比对骨架(T-2.1 提前落地,2026-09-30)

### 36.1 它补的是哪一段空白

G1–G7 全部是**契约自身**的校验 —— 契约自己自不自洽。**没有任何一个门禁会量
"实现和契约对不对得上"**:`check_t.py` 里承担这件事的 T-12 至今是 `skip=True` 的占位,
6 个 `*ContractTest` 只手工绑住了产品库 / 搭配 / 错误码等字段层面的一小块,路径与方法无人过问。

代价已经兑现过一次:§31.2 记的那次真实分叉(Python 侧 `OPENING_TYPES` 多出
`bay_window` / `other`,契约 `OpeningType` 只有 `[door, window]`),当时**没有任何检查会因此变红**。

**为什么现在做,而不是压到上线前**:前端是契约的第一个真实消费者。等前端写了一半
才发现契约与实现对不上,返工最贵。路线图原本也写着「建议 M2 结束时就做」,本次提前到
「每做完一组接口就量一次」。

### 36.2 归属规则:由契约自证,不维护手写清单

`openapi-internal.yaml` 的每条操作上都标着 `x-direction`(`Java → Python` / `Python → Java`)。
所以"这条接口该谁实现"**契约自己就说清楚了**,归属直接读契约 —— 手写映射表会随 DTO 增长,
而且**不会因为分叉而变红**,那正是要避免的东西。

| 契约 | 归属 | 判定依据 |
| --- | --- | --- |
| `openapi-c.yaml` | Java | `servers.url = /api/v1` |
| `openapi-admin.yaml` | Java | `servers.url = /api/v1/admin` |
| `openapi-internal.yaml` | 逐条 | 读该操作的 `x-direction` |

路径拼接按 `servers[].url` 的 path 部分做,**不做 URI 解析**(端口里的 `{var}` 会让 URI 解析抛异常)。
`{id}` 与 `{sku}` 视为同一条路径 —— 路径变量名不在比对范围内,这是**有意**的取舍。

### 36.3 比对范围与口径

**比**:路径 · HTTP 方法 · 字段名(请求体属性、响应 `data` 的属性)。深度 2,数组元素算同一层。
**不比**:类型 / 必填 / 格式 / 枚举值 · 状态码与业务错误码 · query / path / header 参数名 ·
幂等与状态机 · `Map` 与 `additionalProperties` · 运行期改写(序列化 inclusion、`@ControllerAdvice`)。

一条贯穿全报告的原则:**跳过 ≠ 通过**。"无法比对"与"字段不一致"平级渲染、显式列出条数与原因 ——
这样新冒出来的盲区一眼可见,而不是被"没报错"掩盖。

### 36.4 挂账与豁免:豁免必须看得见

第一遍清单必然列出 `/plans/**`、`/agent/chat/**` 等尚未开工的路径。若不区分,
清单会变成一片噪声,久而久之没人看(典型的门禁疲劳)。所以分三档:

| 档 | 条件 | 处理 |
| --- | --- | --- |
| 已挂账 | 在挂账清单里,**且带出处**(任务号 / 裁决节号) | 单列成表,不算异常 |
| **意外缺失** | 契约有、实现无,**且查不到出处** | 高亮 —— 这才是要人看的 |
| **挂账已过期** | 挂账清单里列了,这次实现却已经匹配上 | **判红** —— 清单在漂移的信号 |

豁免(实现侧有、契约内无)同样写在一个文件里并**每条附理由**:Java 的 `/error`
(Spring 内置错误页)、Python 的 `/health`(架构 §12.1);仅存在于测试源码的探针控制器由
实现侧自动识别并归组。**豁免要看得见,新冒出来的路由才会刺眼。**

### 36.5 它抓不到什么(不得读成「已绑住」)

1. **枚举值漂移** —— 恰恰是 §31.2 那次真实分叉的形态。本骨架只比字段名,
   `enum` 一个成员都不看,**这类分叉 100% 漏掉**。
2. 类型 / 必填 / 格式 / 约束(`type`、`required`、`minLength`…)全部不比。
3. 状态码与业务错误码(HTTP 200 的约定、401/403、30001/40003…)完全不在范围内。
4. **SSE 事件集完全不解析** —— `/internal/agent/chat` 的价值几乎全在事件集上,
   那个端点在本骨架里等于没绑。
5. 嵌套深度上限 2;`Map` / `additionalProperties` 无法枚举,一律记"跳过"。
6. 运行期改写(`@ControllerAdvice`、`NON_NULL` inclusion)看不见 —— 两条技术路线都看不见。

> **2026-10-01 补注(上面 6 条不改,记的是本骨架当日的边界)**:第 1、2、5 条已被 **§40** 收窄 ——
> 现在**比**枚举取值、类型、必填(仅请求体),嵌套深度由 2 抬到 5。
> 第 3、4、6 条**仍然成立**(状态码 / 业务错误码、SSE 事件集、运行期改写依旧看不到);
> 第 5 条只剩"`Map` 与 `additionalProperties` 无法枚举"这半句成立。

### 36.6 为什么门禁落在测试里,而不是加进 `ci.sh`

**CI 只有 Node + Python,没有 JDK**,Java 侧跑不起来。若为了迁就 CI 而只留 Python 侧,
覆盖的恰好是最小的那半边(4 条 vs 70 条)。

因此做成两个测试内的开关:

- `-Dcontract.diff.gate=true`(Java)/ `CONTRACT_DIFF_GATE=1`(Python)—— 打开后
  "意外缺失 / 多余路由 / 字段不一致"判红;**默认关**。
- **默认关是刻意的**:第一遍是体检,不是治疗。
- 但有**两类无论如何都判红**,与开关无关:「锚点未命中」与「挂账已过期」——
  它们说明**工具自己不可信**,必须当场拦住,否则"没报错"毫无意义。

一条命令跑两侧:`bash outputs/scripts/contract-diff.sh`(清单落 `outputs/reports/`)。
**此脚本不进 `ci.sh`**,理由同上。

### 36.7 工具怎么保证自己不是在假绿

一个"永远返回无差异"的比较器,在真实代码恰好干净时,输出与正确的比较器**完全一致**。
所以除了正向跑通,还钉了四层:

1. **负向对照**(不启 Spring 的比较器自测):缺路由 / 多路由 / 缺字段 / 多字段 /
   数组元素缺字段 / 嵌套对象缺字段 / 挂账过期 / 路径变量改名**不算差异** /
   `Map` 记「跳过」而**不是**「通过」。合成输入才能把"永远绿"与"正确"分开。
2. **空集合直接抛**:任一侧解析出 0 条端点,当场抛异常,不许进入比较。
3. **锚点(canary)**:Java 侧 8 条、Python 侧 1 条"必然两侧都在"的端点,
   任一条没匹配上就判红 —— 防的是路径拼接写错导致"清单是空的,看起来全对"。
4. **提取器自检**:断言从两侧取到的 `ApiResponse` 字段集都是 `{code, message, data, traceId}`;
   否则"两侧都空 → 字段一致"就是最隐蔽的假绿。

### 36.8 第一遍清单:两处真实分叉(已处置:改实现,不改契约)

| 侧 | 位置 | 现象 | 处置 |
| --- | --- | --- | --- |
| Java | `POST /api/v1/quote/calc` 请求体 `parts[]` | 契约(共享 `PartSelection`)声明了 `source`,`QuoteCalcRequest.PartSelection` 没有 | **改实现**:加上该字段(可选,`@Pattern user\|ai`) |
| Python | `POST /internal/agent/parse-layout` 请求体 | 契约声明 `params` 优先于 `text`,实现只接受 `text` | **改实现**:实装 `params` 分支,校验复用同一个 `validate_layout` |

**决定(2026-09-30):改实现、不改契约。** 契约在文档链上处于实现之上(见本文件开头),
按契约改实现是唯一不打乱归属的方向;而且这两处都不是"业务口径有争议",只是
**"契约声明了的东西实现对不上"** —— 前者是收不下,后者是没实装。

**两处的共同点值得记住**:它们都**不会让任何现有测试变红**。G1–G7 只看契约自身,
字段级绑定测试当时只覆盖了产品库与搭配 —— 这类分叉只有逐字段量才看得见。
这正是 §36.1 说的那段空白。

两处的口径要写清,否则以后会被人当成"漏了"或"多做了":

- **Java 那处:`source` 在 `/quote/calc` 里只收不用。** 契约的 `QuoteItem` 没有 `source`,
  预览也不落库,所以它不参与计算、不回显。收下它是为了让照契约传参的客户端不被
  **静默丢字段**(Jackson 默认忽略未知属性,不报错、只是少东西)。真正持久化 `source`
  的是 `generate` 那一路(T-1.11)。
- **Python 那处:实装后该分支暂无生产调用方。** T-1.7 已决定结构化表单由 Java 就地
  校验与派生、不绕模型;实装 `params` 只是为了与契约对齐。它**同样不派生 `areas`** ——
  派生只在 Java 发生一次,这条没有因为多了一条入口而放宽。

另有一处报告已披露但不算分叉的盲区:admin 的 `POST /combos`、`/products`、`/templates`
响应契约声明了类型化对象,实现返回 `Map`,本骨架记「跳过」而非「通过」——
这也是真实盲区,不粉饰成"一致"。

### 36.9 未覆盖 / 不在本次范围

- **`check_t.py` 的 T-12 不从 SKIP 翻成真判据** —— 理由见 36.6(CI 无 JDK)。
- **不引入 springdoc** 导出真 OpenAPI。它多给的恰好是被排除的类型 / 必填 / 枚举三项,
  却要付出生产依赖 + 每个 `@SpringBootTest` 上下文受影响的代价;且现有
  `SecurityConfig` 兜底是 `permitAll`,加了它等于**公网可拉 `/v3/api-docs`**。
  真正的 T-12(导出真 OpenAPI + 全结构 diff)仍未落地。

> **2026-10-01 补注(上面两条不改)**:这两条**已被 §40 覆盖** ——
> ① T-12 已翻成真判据(`check_t.py` 读 `contract-impl-diff.json`);
> ② **仍然不引 springdoc**,但类型 / 必填 / 枚举三项**已通过反射内省拿到**,
> "不引 springdoc"这个理由(生产依赖 + `permitAll` 兜底会公网暴露 `/v3/api-docs`)因此不被动摇,
> 反而成了本次方案的前提。
- **开关默认关 —— 已定,不是待办(2026-09-30 用户裁决)。** 常态就是"**改接口后跑一次、
  人看清单**",不自动判红:打开开关等于让任何一次字段漂移都阻断构建,与当前
  "先把功能跑通"的节奏冲突。开关(`GATE=1` / `-Dcontract.diff.gate=true`)作为
  **手动诊断手段**保留 —— 想确认"这两侧到底还有没有分叉"时打开它,红了就是有。
  代价要认清:自动化程度低,靠人记得跑;**这是刻意选的代价,不是遗漏**。

> **2026-10-01 补注(上面这条不改)**:此裁决**已被推翻**(见 §40)。变更范围要分清 ——
> **测试内的开关(`-Dcontract.diff.gate` / `CONTRACT_DIFF_GATE`)仍默认关**,
> 免得本地 `mvn test`(364 条基线)被新分叉打断;
> 但 **`contract-diff.sh` 的 `GATE` 默认由 0 改 1**,且 **CI 必拦**。
> 净效应:**CI 必拦 / 本地比对脚本默认判红 / 本地全量测试不打断**。

---

## 37. 暂停开发时的开口项登记(2026-09-30)

> **开发暂停。** 本节把**仍未闭合**的开口项汇总到一处,免得恢复时只记得最近那件事。
>
> **本节只登记与指向,不产生新决策。** 每条的权威记录仍在它指向的那一节;
> 本节若与那些节冲突,**以那些节为准**。同理:绿色基线的数字只写在路线图
> 「暂停点」一节,本节不复制 —— 同一个数字写两处,迟早分叉。

### 37.1 契约 ↔ 实现对账的能力边界(T-12 相关)

| # | 开口项 | 影响 | 权威记录 | 闭合条件 |
| --- | --- | --- | --- | --- |
| 1 | **真正的 T-12 未落地** —— 不导出实现侧 OpenAPI、不做全结构 diff | 类型 / 必填 / 枚举值 / 格式一律不比 | §36.9;任务卡 T-2.1 进度块 ③⑤ | ✅ **已闭合(2026-10-01,§40)**:T-12 转「通过」。**口径改了** —— 不是"导出真 OpenAPI",而是读路由表 + 反射内省;类型 / 必填 / 枚举已比,**格式仍不比** |
| 2 | **枚举值漂移 100% 漏掉** | §31.2 那次真实分叉正是这个形态,而**任何门禁都不会变红** | §36.5、§31.2 | ✅ **已闭合(2026-10-01,§40)**:两侧都能拦住(Java 侧经 `@Pattern` 与 Java `enum`;Python 侧经模块常量严格相等断言) |
| 3 | admin `POST /combos`、`/products`、`/templates` 响应实现返回 `Map` | 比对只能记「**跳过**」,不是「通过」 | §36.8 末 | ❌ **仍未闭合**:消息体是 `Map`,反射得不出结构。要么改实现返回类型化对象,要么继续记 Skip(报告已显式计数) |
| 4 | **挂账清单要人工维护** | 新接口未开工就得挂账,否则算「意外缺失」;挂账过期**无条件判红** | §36.4;两侧各一份 `contract-impl-gaps.yaml` | 仍要人工维护(性质未变)。**2026-10-01 起多了一层**:字段级挂账(`fieldGaps`)也已登记,过期照样判红 |
| 5 | **比对默认不判红,靠人跑** | 没人跑 = 没量过 | §36.9、`CLAUDE.md` §三 | ✅ **已闭合(2026-10-01,§40)**:`contract-diff.sh` 默认 `GATE=1`,CI 必拦;仅测试内的开关仍默认关 |

### 37.2 契约已冻结、实现侧无人对账的接口

| 接口 / 任务 | 状态 | 权威记录 |
| --- | --- | --- |
| `POST /agent/chat`(SSE)与两条 sessions;未选部位 AI 补齐(T-1.9,含 40004) | **未开工** | §27(5 处 x-todo 已关闭);§28.5 |
| `/plans/**`(列表/详情/重命名/删除/另存、`/plans/{id}/copy`,T-1.12)、`/plans/{id}/recalc`(T-1.15)、`/share/{token}`(T-1.13) | **未开工** | 任务卡 T-1.12 / T-1.13 / T-1.15 |
| 前端 F-1.5 户型录入 / F-1.7 生成结果(**暂停后的第一件事**) | **未做** | 路线图「暂停点」一节;任务卡附 C |
| 前端 F-1.6 / F-1.8~F-1.10、B 端 F-0.4~F-0.9 | **未做** | 同上 |

⚠️ **这一栏要紧在**:前端是契约的第一个真实消费者 —— 上面这些接口
**在页面真的调起来之前,都还没被人按契约用过一遍**。契约与实现对不上,
最容易就是在第一次真调用时暴露(§36.1 说的那段空白正是这个意思)。

### 37.3 各任务落地时留下的未闭合项(分散记录,按任务查)

这些**不是同一类东西** —— 有的是"明确推迟"、有的是"真欠着一次验证"、
有的是"需要业务方给数"。恢复开发时**按任务卡逐条看,别一概当阻塞**:

| 归属 | 节 |
| --- | --- |
| 阶段 0(脚手架 / MQ / 产品库 / 导入) | §9.5 · §10.6 · §11.7 |
| 合规 | §22.4(备案是硬前置,**只挡上线**) |
| 后端会话与前端 Cookie | §24.5 · §25.5 |
| 智能体网关 / 灰度 | §26.6 · §28.5 |
| 视角语义 | §33.5 · §34.5 |
| 生成编排 | §35.11 |
| 契约 ↔ 实现对账 | §36.9 |

> 路线图「未解决的问题」一节(§4)是**同一批事情按影响面重排的视图** ——
> 想按"挡不挡上线 / 挡不挡开发"看,读那里;想按"是哪个任务的"看,读本表。

---

## 38. 恢复开发当日:一条测试缺陷(2026-10-01)

> 恢复开发时按路线图「暂停点」的规矩复核基线,六项里 **五项逐字一致**,
> Java 一项挂了一条。本节记录这条的根因与处置 —— **不是产品缺陷**。

### 38.1 现象

`IndexSyncIntegrationTest.payloadIsPersistedNotRebuiltAtDispatch`
(「消息体在落库时就确定,投递时原样发出」)稳定失败:30 秒内收不到 `eventId`
对得上的队列消息。全量 **361 条里就挂这 1 条**(单独重跑该测试类,同样复现)。

### 38.2 根因:把「行数」当成了「id 下限」

该用例取的基线是 `outboxMapper.selectCount(null)` —— **行数**,而不是最大 id;
而它随后拿这个基线做的是 `id > baseline ORDER BY id DESC LIMIT 1`。

`t_outbox` 跨用例、跨轮次**只增不删**,行数与最大 id 会越差越多
(实测 **行数 1266 / 最大 id 1396**,差的 130 个号被「插入后回滚」消耗掉)。
于是「id 大于行数」**立刻**命中一批历史行,`awaitOutboxSentAfter` 不等本次的
新记录就返回了**上一次用例**的旧记录;那条旧记录的消息早被前序用例
(`awaitProductEvent` 会丢弃不匹配的消息)从队列取走 —— 等待必然超时。

**排除的支路**:曾把 `queue.index.sync` 的 90 条积压消息清空后重跑,**仍然失败**,
故与队列积压无关。(顺带记一条独立事实:该队列**当前无消费者** —— Python 侧索引
同步消费端未落地,消息只进不出。)

### 38.3 为什么暂停时是「全绿」

那条测试文件最后一次改动是 **2026-09-29**(早于 2026-09-30 的基线),git 工作区干净
—— **没有人改坏它**。它是**被数据积累顶出水面的**:当时 `t_outbox` 还没攒到这个程度。

### 38.4 处置

- **改测试,不改产品**:基线改为按最大 id 取(`latestOutboxId()`),并就地留注释说明缘由。
- 复核:Java 全量重跑 **361 passed / 0 failed / 0 skipped**,与暂停点基线一致。
- 提交:`9387253`(生产代码零改动,改动仅此一个测试文件)。

### 38.5 教训:与 §28.4 同族

§28.4 那条「**断言只针对自己造的数据,不对全表状态下结论**」,这里换了个形态又出现一次:
**基线的取值也要只针对自己造的数据** —— 拿全表行数当 id 水位,等于让历史数据参与判定,
而历史数据的量是时间函数,今天对、明天错。

⚠️ **值得再查一遍的形态**:凡「先取一个水位/基线,再断言新记录」的用例,都要看它取的是
**id** 还是 **count**。本次全仓库排查,**只有这一处**把 count 当 id 用
(其余 `selectCount(null)` 都是正常的「前后条数比较」)。

---

## 39. 补 C 端会话接口 `GET /auth/me`(2026-10-01)

> `x-source`:PRD §5.1(会话恢复)、架构 §10.2(C 端接口表)。
> 这是 M1 收口(选砖 → 录户型 → 点生成 → 看到图)开工时的**唯一一处契约改动** ——
> 两个页面本身用的都是已有接口,故只有本节触发文档链。

### 39.1 背景:C 端缺一个「我是谁」的接口

§24 / §25 已为**后台**补过同名接口(缺口 G-9 那一批)。C 端的理由**逐字相同**:

令牌在 HttpOnly Cookie 里(架构 §10.1 的 D1 方案),前端**读不到**,
于是**页面刷新后无法自行知道"当前是谁"**。不补这个接口,前端只有两条路:

| 走法 | 后果 |
| --- | --- |
| 什么都不存 | 刷新即掉登录态。生成页带 `meta.requiresAuth`,守卫会**反复把人踢回登录页** —— M1 的"看到图"就走不通 |
| 把账号缓存在浏览器端 | 违反**前端零存储**(架构 §10.1 / 开发规范 §1.4),且等于把**展示状态当成凭证来源**:账号被停用后界面仍按旧状态渲染 |

补上之后,每次页面加载都以**服务端当下的事实**为准。

### 39.2 产出(文档链改动,严格 ①→④)

| 顺序 | 文档 / 代码 | 改动 |
| --- | --- | --- |
| ① | `tile-ai-prd.md` | v0.9 → **v0.10**:§5.1 增「会话恢复」一条 |
| ② | `tile-ai-architecture.md` | v2.2 → **v2.3**:§10.2 表增一行 `GET /auth/me`(需登录),并补一段「为何 C 端也需要它」 |
| ③ | `openapi-c.yaml` | auth 分组新增 `/auth/me`(GET)。**镜像 `openapi-admin.yaml` 的写法**:`security: [cookieAuth]`、200 → `data: UserInfo`、401 → common 的 `Unauthorized` |
| ④ | `outputs/scripts/check_t.py` | `EXPECT_AUTH` 增 `('/auth/me','get'):'login'` |
| ④ | 本文档 | 本节 |
| — | 实现(Java) | `AuthController#me`、`AuthService#findEnabledById`、`SecurityConfig` 的 `PROTECTED_C_PATHS` |
| — | 实现(前端) | `api/c/auth.ts#fetchMe`、`stores/userAuth.ts#ensureSession`(刷新页面恢复会话) |

**没有新增 `securityScheme`** —— `cookieAuth` 已在 `openapi-c.yaml` 声明。
本节的 description 用**块式写法**(`: |`)而非流式映射,专门绕开 §2.3-1 的逗号撕裂陷阱。

### 39.3 鉴权口径:与后台逐字对齐

| 口径 | 取值 | 依据 |
| --- | --- | --- |
| 是否需要登录态 | **要** | §10.2 表的「需登录」列 |
| 是否需要权限点 | **不要** | "我是谁"的接口,不是"我能做什么"(§24.2 已为后台写明) |
| 账号停用 / 不存在 | **10002**(会话失效),不是 30001 | §24 既有裁决;停用是"要重新登录",不是"资源不存在" |
| 响应体 | 白名单 `UserView`,**绝不含 `passwordHash`** | 断言直接查 body 文本 |

⚠️ `AuthService#findEnabledById` 的**失败关闭**是必须的:令牌签发后账号可能被停用,
而令牌在最长 2 小时内仍然有效 —— `JwtAuthenticationFilter` 只解令牌**不查库**,
所以"账号还是不是启用状态"这件事**只有这里能挡**。

### 39.4 ⚠️ 一个静默陷阱:漏加 `PROTECTED_C_PATHS` 不会报错

`SecurityConfig` 里,未列入 `PROTECTED_C_PATHS` 的 `/api/v1/**` 会**落到
`anyRequest().permitAll()`** —— 于是 `/auth/me` **匿名也能调**,且功能"照样能用":
登录用户看到自己的账号,未登录用户收到的是一个空壳或 500,**没有任何一处会红**。

本次**专门加了一条测试钉它**:`meRequiresLogin` 断言未带 Cookie 时返回**真实 401**。
> 这与 §24 为后台写 `meRequiresLogin` 的动机相同 —— 这条断言的真正守卫对象
> 不是接口本身,而是**它有没有被登记进鉴权清单**。

### 39.5 校验

**契约侧**:`bash outputs/scripts/ci.sh` → **7/7 PASS**。
T-4 通过(它**反查**「契约里有、期望表没有」的路径 —— 只改架构表而忘了 `check_t.py`,
这一步必红);T-13 的 401 清单由 13 → **14** 条。

**Java 侧**:`mvn -s … test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true`

```
Tests run: 364, Failures: 0, Errors: 0, Skipped: 0
```

361 → 364(本轮 +3,全在 `AuthIntegrationTest`):

| 断言 | 判据 |
| --- | --- |
| `/me` 带会话返回账号(含 username,**不含 `passwordHash`**) | §5.1 / §10.2 |
| `/me` 未登录 → **真实 401** | §10.2「需登录」+ §39.4 |
| `/me` 对**签发后被停用**的账号 → **10002** | §4.4「权限判断读当下事实」 |

**前端侧**:`npm test` → **142 passed / 13 文件**;`npm run build`(含 `vue-tsc`)通过。

**Python 侧**:`pytest -q` → **174 passed / 11 skipped**(未受本改动影响,复核未误伤)。

**契约 ↔ 实现比对**(`contract-diff.sh`,**出清单不判红,靠人看**):

| 项 | 数 |
| --- | ---: |
| 契约有实现无 · **意外缺失** | **0** |
| 实现有契约无 · 多余路由 | **0** |
| 字段不一致 | **0** |
| 需要人看的 | **无** |

「无法比对(跳过 ≠ 通过)」共 **26** 条,**已逐条人看**:3 条形态无法对位
(后台三个写接口的 `data` 键不可枚举)、2 条非 JSON(导入模板下载)、21 条测试源码探针
—— **全是既有类别,无本次新增**;新增的 `/auth/me` **落在"比过"的那 53 条里**,
不在任何一条「无法比对」中。

### 39.6 未闭合 / 顺带发现

1. **文档头部版本行常年未随修订记录回填**(既有问题,非本次引入):
   架构头写 `v1.7` 而修订记录已到 `v2.2`;头部「上游文档」与正文引用仍写 **PRD v0.6**。
   本次把架构头**一并改为 v2.3**(否则本轮又会新增一处"头 ≠ 记录"),
   但**上游引用那两处未动** —— 不确定是有意钉住某个基线还是漏更新,**留待确认后处理**。
2. **`/auth/me` 是否该带更多字段**(如 `role` / 权限点):C 端目前没有角色概念,
   只返回 `UserInfo`。若日后 C 端出现差异化能力,再议 —— 与 §24.5② 后台那条同族。

---

## 40. 契约 ↔ 实现双向 diff 收口:比对扩维 + CI 必拦(2026-10-01)

> **本节产生两条新裁决,并且这两条明确覆盖旧记录**:
> ① **仍然不引 springdoc**,但把比对维度从"路径 / 方法 / 字段名"扩到
> **类型 / 必填(仅请求体)/ 枚举取值** —— 覆盖 §36.9 第 1、2 条;
> ② 门禁由 **"默认关、靠人跑"改为"CI 必拦"** —— 覆盖 §36.9 第 3 条与
> 2026-09-30 的「默认不判红是常态」裁决。
> **旧记录一律保留原貌**(§36.9 三条、§37.1 #1/#2/#5、§8.3 / §8.8.4 的 T-12 单元格均未回改),
> 各条之下已追加带日期的补注指向本节。这是本文件「历史只追加」的规矩。

### 40.1 为什么现在做:§31.2 已经兑现过一次

任务是 T-2.1,判据是 §5 的 **T-12**。开工前 T-12 一直是 `skip=True` 的占位 ——
也就是说,**"契约和实现对不对得上"这件事,整整一期没有任何门禁在看**。
代价不是假设,已经发生过:

> **§31.2**:Python 的 `OPENING_TYPES` 多出 `bay_window` / `other`,契约只有 `[door, window]`。
> 两侧测试都绿、7 个门禁全 PASS —— 因为 G1–G7 只校验**契约自身**。

§36 立起的骨架把这类分叉从"完全看不见"推进到"看得见一半",但 §36.5 自己写着:
枚举值漂移「**100% 漏掉**」。本次就是把这个"一半"补完,并让它**真的拦住**。

### 40.2 两条裁决

| # | 裁决 | 覆盖了谁 |
| --- | --- | --- |
| ① | **不引 springdoc**。实现侧改为读 **Spring 路由表 + Jackson 反射内省**;比对范围扩到**类型 / 必填(仅请求体)/ 枚举取值** | §36.9 第 1 条(「类型/必填/枚举三项被排除」)、第 2 条(「不引 springdoc」**强化**,理由不变) |
| ② | **门禁:CI 必拦**;`contract-diff.sh` 的 `GATE` 默认由 **0 → 1** | §36.9 第 3 条、§37.1 #5、2026-09-30「默认关是常态」 |

**① 的理由**:不引 springdoc 的原始理由(生产依赖 + `SecurityConfig` 兜底是 `permitAll`,
加了它等于公网可拉 `/v3/api-docs`)**依然成立**,所以不引;而它"多给的恰好是类型 / 必填 / 枚举"
这件事,**反射内省同样拿得到** —— 于是"不引"不再是"不比"的代价。

**② 为什么推翻"默认关"**:那条裁决的场景是"先把功能跑通,别让字段漂移阻断构建"。
本次收口的判断是:**跑通阶段已经过去**(M1 已收口),而"没人跑 = 没量过"这件事
恰恰是 T-12 挂了一期的原因。所以:
- **CI 必拦**;
- **本地 `contract-diff.sh` 默认判红**(该脚本的语义本就是"量一次");
- **本地全量 `mvn test` 仍不打断** —— 测试内的开关(`-Dcontract.diff.gate` /
  `CONTRACT_DIFF_GATE`)**保持默认关**,364 条基线不被新分叉打断。

### 40.3 实现侧怎么读(不导出 OpenAPI)

| 侧 | 读法 | 为什么 |
| --- | --- | --- |
| Java | `RequestMappingHandlerMapping` 取真实路由;**自己用 Jackson 内省 DTO** | 不引 springdoc;而且反射拿到的正是"实现真实长什么样" |
| Python | `app.openapi()` + **直接 import 模块常量** | F3:Python 侧的开口/空间取值**不在 `app.openapi()` 里** |

**三条勘探结论决定了方案**(不是拍脑袋选的):

- **F1.本仓库的 Java "枚举"几乎不是 `enum`,而是 `String` + `@Pattern(regexp="a|b|c")`**
  —— 全仓 16 处,逐一核对全是简单交替且与契约 enum 逐一相等
  (`door|window`、`floor|wall|local`、`user|ai`、`on_sale|off_sale`、
  `enabled|disabled`、`space|style`、`pending|running|success|failed`、
  `aux_unit_price|loss_rate|loss_rate_min|loss_rate_max`)。
  ⇒ **只做 `Class.isEnum()` 会漏掉本仓库全部真实 DTO。** 故枚举来源扩成三条:
  Java `enum` 常量(经 `ObjectMapper` 序列化取值,尊重 `@JsonValue`/命名策略)、
  `@Pattern` 简单交替、**推不出就记 Skip**(字典驱动的空间/风格属这一类)。
- **F2.契约 `OpeningType` 的 Java 落点是 `GenerateRequest.layout.spaces[].openings[].type`,深度 5。**
  原骨架 `MAX_DEPTH = 2` 根本比不到 —— 不抬深度就堵不住 §31.2 的 Java 侧镜像。**本次抬到 5。**
- **F3.Python 的 `OPENING_TYPES` 只在 `app/agent/layout_parse.py` 的模块常量里。**
  ⇒ Python 侧唯一能拦住 §31.2 的办法是**直接 import 常量比对**(见 40.6)。

**不做大小写归一** —— `bay_window` vs `BayWindow` 就是真分叉,压平会把真信号洗掉。

### 40.4 比什么 / 不比什么

| 维度 | 比吗 | 口径 |
| --- | --- | --- |
| 路径 / 方法 / 字段名 | ✅(原有) | 双向 |
| **类型** | ✅ **新增** | 两侧都已知才比;JSON Schema 里 `integer ⊂ number`,**契约 number 收实现 integer 不算差异**,反向才报 |
| **必填** | ✅ **新增** | **仅请求体侧** —— Java 的响应没有"必填"概念;实现侧读 `@NotNull`/`@NotBlank`/`@NotEmpty`/`@JsonProperty(required=true)`(`@Valid`/`@Size`/`@Pattern` **不算**,它们不拒绝 null) |
| **枚举取值** | ✅ **新增** | 对称差非空即报;契约有而实现推不出 → **记 Skip**(既非通过也非红) |
| 嵌套深度 | ✅ 2 → **5** | 深度截断的叶子**单独计数**(见 40.5) |
| format 与取值约束 / 状态码与业务错误码 / query·path·header 参数名 / 幂等与状态机 / `Map` 与 `additionalProperties` / 运行期改写 / SSE 事件集 | ❌ **不比** | 报告页眉与本节都写明 —— **不许读成「已绑住」** |

### 40.5 ⚠️ 一处必须单列的口径:「没判过」不是「比过」也不是「一致」

本次把叶子分成**两个数**,不许合成一个:

| 计数 | 含义 |
| --- | --- |
| **比到底的叶子(类型两侧都能判定)** | 真的判过 |
| **未对位的叶子 · 没判过**(深度截断 / 键不可枚举 / 一侧推不出类型) | **没判** —— 单独计数,不判红 |

首跑:**比过 461 / 没判过 12**。

这条口径是有前车之鉴的:早先只有一个计数器,标签写着"嵌套层未对位的叶子",
但类型维度落地后它把**所有**叶子都算了进去(改前只在"没往下递归"时累加)。
于是页眉里一个几百的数字顶着"未对位"的名字 —— 既吓人,又把真正没对上的那 12 个淹掉。
**那正是"跳过 ≠ 通过"最容易被读错的位置**,所以拆开,并加了负向对照钉住它。

「无法比对」同理,在报告里**按原因分组、逐组计数**(首跑共 84 条):
枚举取值推不出(实现侧)**58** · 形态无法对位(`Map`,三个后台写接口)**3** ·
非 JSON 响应(导入模板下载)**2** · 探针(仅测试源码)**21**。

### 40.6 字段级挂账:带出处、可见、过期判红

`contract-impl-gaps.yaml` 新增顶层节 `fieldGaps`(两侧各一份,键名一致)。
三段规则:

| 档 | 处理 |
| --- | --- |
| **命中**(端点匹配上、该维度真的产生了这个差异) | 折叠进「已知字段级差异(已挂账,带出处)」表 —— **可见,不红** |
| **过期**(端点匹配上、该维度这次比过、却**没**产生差异) | **判红**,与整条挂账过期同级 |
| **未生效**(端点缺失 / 维度不可推断) | 单列「本次未生效」—— **不红** |

三段必须分清,否则"端点还没开工 → 挂账被误判过期 → 天天红"。首跑 **命中 4 · 过期 0 · 未生效 0**。

**首轮登记的 4 条**(全部是"**契约松、实现严**"):

| 端点 | 字段 | 后果 |
| --- | --- | --- |
| `POST /api/v1/quote/calc` | `parts[].area` | 契约标可选(理由写着「可由服务端依户型计算」),但 `/quote/calc` 请求里**没有户型** —— 缺失时当 0 会**静默报出偏低价** |
| `POST /api/v1/quote/calc` | `openings[].part` | `part` 为空时 `QuoteService` **静默跳过扣减**(面积偏大、报价偏高) |
| `POST`、`PUT /api/v1/admin/products` | `images[].url` | 契约在内联 `items` 上没写 `required`;实现要求非空,否则会往 `t_product_image` 落一行**无地址的图** |

**处置口径沿用 §36.8:改实现、不改契约。** 但这 4 条的"正确修法"恰恰是**把契约收紧**
(内联 `items` 漏了 `required`)—— 属契约改动,本次**一个字不动**,先登记为可见挂账。
**它们没有被消除,别忘了还欠着。**

### 40.7 怎么证明它不是橡皮图章(注入 → 看红 → 撤回)

一个"永远返回无差异"的比较器,在真实代码恰好干净时与正确的比较器输出**完全一致**。
所以逐条注入、逐条看红、逐条撤回:

| 注入 | 期望 | 实测 |
| --- | --- | --- |
| `Opening.java` 的 `@Pattern` 加 `bay_window` | 出现 ENUM 不一致(**复现 §31.2**) | ✅ 报 **7** 条 ENUM 不一致,退出码 1 |
| `QuoteCalcRequest` 某必填字段去掉 `@NotNull` | 出现 REQUIRED 不一致,**且挂账条目变 stale → 红** | ✅ 报 **1** 条 `staleFieldGap`,退出码 1 |
| 契约某字段 `number` → `string` | 出现 TYPE 不一致 | ✅ 报 **5** 条 TYPE 不一致,退出码 1 |
| Python `layout_parse.OPENING_TYPES` 加一个成员 | 新断言 **FAIL** | ✅ `test_opening_and_space_types_match_contract` FAIL |

**全部还原后回到 0。** 另有一层"提取器没读空"的负向对照:`ImplReaderShapeTest`
(不启 Spring,直接按 Java 类型算形态),钉住 `@Pattern` 读法、Java `enum` 常量、
`@NotBlank` 确实被读到、深度 5 可达、超深被截断而不静默 —— 只跑比较器的话,
**提取器静默失灵时报告照样全绿**。

### 40.8 门禁怎么接:不新增 G8,T-12 计入 G6

**路线 a(采纳)**:CI 在 `ci.sh` **之前**生成比对产物,G6 / `check_t.py` 的 T-12 读**本轮新产物**判定。
**路线 b(不采纳)**:做成第 8 个门禁 G8 —— 会让 `ci.sh` 在无 JDK 机器上永久 FAIL 或自我 SKIP,
与「`ci.sh` 平台无关、7 门禁」冲突,退出码语义也与 G6 重复。**所以「7 个门禁」不变。**

`check_t.py` 的 T-12:

- `CONTRACT_DIFF_REQUIRED=1`(**CI**)时**绝不 SKIP**:文件缺失 / 不可解析 /
  `runId ≠ $CONTRACT_DIFF_RUN_ID` / `gitSha ≠ $GITHUB_SHA` → **FAIL**;`hasFindings=true` → FAIL;否则 PASS。
- 非 required(本地):缺失或不可验证 → **SKIP 并写明原因**;在 → 照判。
- **为什么缺失必须 FAIL 而不是 SKIP**:**SKIP 正是 T-12 挂了整整一期的原因** ——
  生产者坏了的时候,SKIP 会让门禁**静默地自我关闭**,这是最坏的绿色。

`.github/workflows/contract-lint.yml` 相应改动:加 `actions/setup-java`(temurin 17 / `cache: maven`);
加一步生成产物(**`continue-on-error: true`**,让 `ci.sh` 成为唯一裁判);
`timeout-minutes: 10 → 30`;触发路径**补 `tile-ai-server/**` 与 `tile-ai-agent/**`**
—— 在此之前**只改 `.java` 根本不触发本工作流**(白名单只有 yaml/yml/md/outputs),
不补这一条,"自动对账"就是纸面上的。`tile-ai-web/**` **刻意不加**(本工作流不跑前端测试)。

### 40.9 校验结果

**契约侧**:`bash outputs/scripts/ci.sh` → **7/7 PASS**(G1 结构 / G2 redocly / G3 spectral /
G4 逗号撕裂 / G5 描述完整性 / G6 T-1~T-15 / G7 AsyncAPI)。
G6 报 **PASS=15 · FAIL=0 · SKIP=0** —— T-12 曾是**唯一的那个 SKIP**。

**负向**:删掉 `contract-impl-diff.json` 再以 `CONTRACT_DIFF_REQUIRED=1` 跑 → G6 **FAIL**
(T-12 FAIL)、退出码 1,证明"产物缺失 = FAIL 而非 SKIP、不是假绿"。

**比对本身**:`bash outputs/scripts/contract-diff.sh`(默认 `GATE=1`)→ 退出码 **0**,
两侧清单齐备。Java 侧 `Tests run: 42, Failures: 0`;Python 侧 3 passed。

| 项 | 数 |
| --- | ---: |
| 契约端点(Java 归属 / Python 归属) | 71 / 4 |
| 实现路由 / 两边匹配上并比过 | 77 / 53 |
| 契约有实现无 · 意外缺失 | **0** |
| 实现有契约无 · 多余路由 | **0** |
| 字段名不一致 | **0** |
| 取值不一致 · 类型 / 枚举值 / 必填 | **0 / 0 / 0** |
| 字段级差异 · 已挂账(带出处) | 4 |
| 挂账已过期(整条 / 字段级) | 0 / 0 |
| 锚点未命中 | 0 |
| 比到底的叶子 / 未对位的叶子(没判过) | 461 / 12 |
| 无法比对(跳过 ≠ 通过) | 84(见 40.5 分组) |

**Java 全量**:`mvn -s … test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true`

```
Tests run: 389, Failures: 0, Errors: 0, Skipped: 0
```

364 → **389**(+25:比较器负向对照 13 + 提取器自检 12)。**0 skipped** —— 没有"跳过"被当成"通过"。

**Python 全量**:`pytest -q` → **175 passed / 11 skipped**(174 → +1,新增的常量断言)。

### 40.10 仍未覆盖(不许读成「已绑住」)

1. **format 与取值约束**(`minLength` / `minimum` / `pattern` 的语义)一律不比。
2. **状态码与业务错误码**(HTTP 200 约定、401/403、30001/40003…)不在范围内。
3. **query / path / header 参数名**不比 —— 只比请求体与响应体。
4. **SSE 事件集完全不解析**:`/internal/agent/chat` 的价值几乎全在事件集上,
   **那个端点至今等于没绑**。
5. **`Map` 与 `additionalProperties`** 无法枚举 —— admin 的三个写接口响应仍记 Skip。
6. **运行期改写**(`@ControllerAdvice`、`NON_NULL` inclusion)看不见。
7. **深度 > 5** 的嵌套被截断(本项目最深处是 `layout.openings[].type` = 5,刚好覆盖)。
8. `fieldGaps` 里的 4 条**是真实差异,不是通过** —— 它们只是被登记成可见挂账。

---

## 41. T-1.12 方案管理:五条接口的口径与三处实现改动(2026-10-01)

任务 T-1.12(「我的方案」)**契约一个字没动** —— 五条接口
(`GET /plans`、`GET /plans/{id}`、`PATCH /plans/{id}`、`DELETE /plans/{id}`、
`POST /plans/{id}/copy`)本来就在契约里,只是实现侧没有。本次把它们补齐,
并了结三件一直欠着的事。

> **本节含一条明确覆盖旧记录的裁决**:`PlanPart.source` 的存废(41.1)。
> **旧记录一律保留原貌** —— `GenerateCallbackService` 那条注释的原文与
> T-1.11 卡片里的相关判断都未回改,各条之下已追加带日期的补注指向本节。

### 41.1 `PlanPart.source` 落库 —— **推翻** T-1.11 的旧判断

**旧貌(保留,不回改)**:

```
/** 部位列表 → 方案部位(去掉 {@code source}:那是生成口径,不是方案口径)。 */
```

出处在 `GenerateCallbackService.toPlanParts`;T-1.11 的进度块与实现当时都按这个判断走,
**`source` 被有意丢弃**。

**为什么推翻**:契约 `openapi-common.yaml#/components/schemas/PlanPart`
自始就有 `source`(取值 `user` / `ai`,见 `PartSource`),`required` 里虽然没有它,
但它是**方案详情要返回的字段**;PRD §5.2.4 也明确「补齐结果随方案自动保存」。
更直接的证据是 §11.2:前端要靠它区分「**这块砖是用户自己选的,还是 AI 补的**」——
丢了它,详情接口永远说不出这件事。所以它是**方案口径**,不是生成口径,
当初那句判断把归属搞反了。

**怎么发现的**:不是靠人读出来的,是 **T-2.1 的契约↔实现比对量出来的**(§36 / §40)。
契约里有、实现里没有的字段,正是那个工具的用途。

**处置**(沿用 §36.8:**分叉一律改实现、不动契约**):

| 动作 | 位置 |
| --- | --- |
| 新迁移 `V10__plan_part_source.sql`,追加 `source VARCHAR(10) NULL`(列序在最后) | `db/migration/` |
| 请求侧归一:省略即 `user` | `PartSelection.withDefaultSource()` |
| 回调用它落库,并**重写那句已经说反了的注释** | `GenerateCallbackService.toPlanParts` |
| 自动保存与 `/copy` 深拷都带上这一列 | `PlanAutoSaveService` / `PlanService.copy` |
| 架构 §8.4 的 `t_plan_part` 字段清单补上 `source` | `tile-ai-architecture.md` |

> 这三处**必须同列序**押住:`SchemaMigrationTest` 是按**列序**逐列比对的,
> 所以 `V10` 的 `AFTER material_fee`、架构 §8.4 的清单、测试里的期望列序,
> 差一格就红。

### 41.2 报价快照:生成时就落库

原本留给 T-1.15。**本次只补「生成时存一份」**;「按当前价格重算」与
`quote_expired` 标记仍归 T-1.15(那张卡因此缩小)。

| 项 | 口径 |
| --- | --- |
| 落点 | `plan/PlanQuoteSnapshotService`;在 `PlanAutoSaveService.upsert` 的**同一个事务**里编排 —— 要么都有报价、要么都没有 |
| 写哪张表 | `t_plan_quote`(存汇总)+ 回填 `t_plan_part` 的费用列(`price_per_sqm` / `area` / `loss_rate` / `material_fee`)+ `t_plan.total_amount` |
| 唯一性 | **先删后插** —— V4 的 `t_plan_quote` **没有 `plan_id` 唯一键**,靠这个保证一个方案至多一行 |
| `rules_snapshot` | 直接序列化**算钱时用的那一份**参数,不重新查表 —— 否则会出现「快照里的参数」与「算钱用的参数」不一致 |

**红线:「算不出」就整单不落,绝不把"没算出来"写成 0。**

任一条成立即触发(`build` 返回 `Optional.empty()` + 一条 WARN):

- 部位行为空;
- **某行 `gross_area` 为 `null`**;
- SKU 在 `t_product` 里查不到;
- `t_quote_param` 缺参数;
- 报价引擎抛异常(走 `tryCalculate`,见下)。

`gross_area` 为 null 是**常事**:`PlanAutoSaveService.grossAreaOf` 对 `local` 部位和
「匹配不到空间」返回 null —— 语义是「**没定**」,不是 0。把它当 0 会静默算出一个偏低价。
**只算「能算的行」、总额留空也不行**:卡片上没有任何字段能说明这个总额是残缺的。
**整单不落是唯一不会骗人的选择。**

> ⚠️ **一处 Spring 陷阱,已避开**:`QuoteService.calculate` 上挂着
> `@Transactional(readOnly = true)`。从外层事务里调它、再用 `try/catch` 吞掉异常,
> Spring 会把**共享事务标成 rollback-only**,外层提交时抛 `UnexpectedRollbackException`
> —— 症状是「出图成功了、方案却没保存」,现场极难查。
> 故拆成三个:`calculateCore`(私有、无注解,原主体)/ `calculate`(带注解,一行委托)
> / `tryCalculate`(**不标任何事务注解**,返回 `Optional`,异常吞掉 + WARN)。
> `/quote/calc` 的行为**一字未变**。「算不出」那三条用例同时也是这个陷阱的对照。

**对 T-1.11 的一处有意收紧**:`t_plan_part` 按 **`(part, sku)` 合并后**再写行。
V4 的 DDL 注释本来就写着 `gross_area` 是「同部位同砖合并后」的值,而 T-1.11 没有合并。
不合并的话 `Σ material_fee ≠ material_total`,而 T-1.15 的重算要从 `t_plan_part`
反推请求 —— **两侧口径必须一致**,所以这次收紧。

**多空间方案会落进「保守不报价」分支** —— §35.8 把多空间报价归属留给了 T-1.15。
**这是刻意的保守,不是缺陷**,别当成 bug 来修。

> ⚠️ **上面这段与代码不符,已订正 —— 见 §42.5。**(原句保留不改,只追加指向:
> 当时确实以为会落进保守分支,实测下来 `resolveSpace` 总会返回一个空间,
> 没有任何多空间短路,T-1.11/T-1.12 期间多空间方案一直是**有**报价的。)

### 41.3 `/copy`(另存为)的语义

契约只说了路径与响应形状(`{planId}`),语义由本次定(**默认名 = 原名 + `(副本)`**):

| 表 | 做法与取舍 |
| --- | --- |
| `t_plan` | 新插一行。`name` = 原名 + `(副本)`;`layout_enc` **直接拷字节**(同密钥 → 同密文,不泄漏超出「这两个布局相同」的信息,而那本来就是用户自己做的操作;重新加密反而引入「源密文坏了就拷不了」的失败面);`share_token` **置 null**(唯一键会撞,且一个分享链接不能指向两个方案);`created_at` / `updated_at` 交给 DB 默认值 —— 副本是个**新**方案,有自己的创建时间 |
| `t_plan_part` / `t_plan_image` / `t_plan_quote` | 逐行**深拷**,连费用列、`is_cover`、`source` 一起 —— 否则副本会「有总额没明细」 |
| `t_generation_task` / `t_outbox` | **不拷** —— 任务与出图是原方案的历史 |

**不改原方案任何一个字节** —— 用逐列指纹比对钉住(含 `updated_at`:
用户在列表里看到的时间与排序位置,不该因为「我另存了一份」而变化)。

### 41.4 归属:`30001` 三合一;密文坏掉走 `90001`

**「不存在」/「不是本人的」/「已逻辑删除」一律 `30001`,且措辞完全相同。**
详情、改名、删除、另存为**四条路径**都断言了这一点 —— 区分开来等于送出一个
**可以枚举别人方案 id 的探针**。删除了的方案也不"复活":再删一次仍是 `30001`,
用户看到的事实就是「这个方案没了」。

**但密文解不开不是 `30001`。** `GET /plans/{id}` 里 `layout_enc` 解密/反序列化失败
走 **`90001` + ERROR 日志**:方案**确实在**,坏的是系统 ——
返 `30001` 会让用户以为方案丢了,从而去做一些不该做的操作。

**删除是纯逻辑删除**:不删子表、**不碰 `share_token`**。分享出去的链接要能解释成
「这个方案已被删除」,误删也还有恢复的余地。

### 41.5 对象存储:**再次顺延**

封面(决策①)本期只做到「**记住哪张是封面**」—— 取 `t_plan_image` 里
`is_cover = 1` 那行的 `url`(现在指向替身占位图),**没有真正存过一张图**。

于是 §4.1 P0-4 / §4.2 里「对象存储选型卡 T-1.12」的说法**再次顺延**:

```
~~卡 T-1.11~~ → ~~卡 T-1.12~~ → 改卡「接真实图像模型时」
```

**已经连续顺延两次。** 接回真实模型时必须先定 —— 那才是第一次真正需要存图的地方。
这是决策①的**已知代价**,不是遗漏。

### 41.6 ⚠️ 本次留下的新开口:`materialFee` 契约必填,却没算价时会省略

契约 `common.PlanPart.required = [part, sku, materialFee]`;
而「算不出」时实现把 `materialFee` 留成 `null`,VO 又带 `@JsonInclude(NON_NULL)`
—— **这个字段就不出现了**。

也就是说:**契约说它必有,实现在某些方案上不会给。**

- **不是缺陷**:那时确实没有价可报,发一个 `0` 才是骗人(见 41.2 的红线)。
- **但这是一处真实的不一致**,而且**比对工具抓不到** —— 它只对**请求体**判必填
  (§40.4),响应侧的必填不在范围内。所以它**不能**登记进 `fieldGaps`
  (那里 `required` 只能配 `request`)。
- **正确终局是收紧契约**(把 `materialFee` 从 `required` 里去掉,或改成 nullable 并在
  描述里写明「未报价时缺省」),但那要动 `openapi-common.yaml` + 架构,
  **不在本轮范围** —— 本次一个字没动契约。
- **所以在这里以可见方式登记**,别让它悄悄漂着。T-1.15 做重算时会再碰这个字段。

### 41.7 校验结果(2026-10-01)

**契约侧**:`bash outputs/scripts/ci.sh` → **7/7 PASS**
(G1 结构 / G2 redocly / G3 spectral / G4 逗号撕裂 / G5 描述完整性 / G6 T-1~T-15 / G7 AsyncAPI)。

**契约 ↔ 实现**:`bash outputs/scripts/contract-diff.sh`(默认 `GATE=1`)→ 退出码 **0**,
两侧干净。Java 侧 `Tests run: 42, Failures: 0`;Python 侧 3 passed。

| 项 | §40(基线) | 本次 |
| --- | ---: | ---: |
| 实现路由 / 两边匹配上并比过 | 77 / 53 | **82 / 58**(+5 = 本次五条) |
| 契约有实现无 · 意外缺失 | 0 | **0** |
| 实现有契约无 · 多余路由 | 0 | **0** |
| 字段名 / 类型 / 枚举值 / 必填 不一致 | 0 / 0 / 0 / 0 | **0 / 0 / 0 / 0** |
| 挂账已过期(整条 / 字段级) | 0 / 0 | **0 / 0** |
| 锚点未命中 | 0 | **0** |
| 比到底的叶子 / 未对位的叶子(没判过) | 461 / 12 | **530 / 12** |
| 无法比对(跳过 ≠ 通过) | 84 | **94** |

「已知未开工」由 17 条降到 **12 条** —— 本次这五条从表里消失了
(`/plans/{id}/recalc` 的 T-1.15 与 `share` 三条的 T-1.13 **仍留着**,那三条确实没开工)。

> ⚠️ **「未对位的叶子 12」「无法比对 94」这两个数必须有人看** —— 它们**没判过**,
> 不是通过。明细见报告页眉与 §40.5 / §40.10。

**Java 全量**:`mvn -s … test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true`

```
Tests run: 419, Failures: 0, Errors: 0, Skipped: 0
```

389 → **419**(+30:`PlanContractTest` 13 条 + `PlanManagementIntegrationTest` 17 条)。
**0 skipped** —— 没有"跳过"被当成"通过"。

**移植性注意(踩过一次)**:全项目 **21 个集成测试都带 `spring.flyway.enabled=false`**(统一约定),
所以**没有任何测试会替你把库建起来**。新环境、或新增了 `V*.sql` 之后,
**先跑一次 `SchemaMigrationTest`** 把迁移补上,否则会看到一堆 `90001`,
而真正的原因只是 `Unknown column 'source'`。本次就是这样,已同步写进路线图 §5.3。

### 41.8 仍未做(不许读成「已绑住」)

1. **封面没有真正存图** —— 只记住了哪张是封面(41.5)。
2. **多空间方案不报价**(保守分支,归 T-1.15,§35.8)。
   → 见 §42.5:该断言**不成立**,实测多空间方案一直有报价。
3. **`POST /plans/{id}/recalc` 未做** —— T-1.15;那条**无请求体**的不变量(§2.1 不变量 6)
   仍未实现。
   → 见 §42:**已完成**(T-1.15),不变量 6 两侧都钉住了。
4. **`materialFee` 的必填不一致**(41.6)—— 已登记,**未消除**。
   → 见 §42.7:重算路径**同样**会省略它,不一致面没扩大也没缩小。
5. **重新编辑的两条分支**只验了后端语义(覆盖 / 另存);**页面上还没有入口**
   (F-1.8~F-1.10 未做)。

---

## 42. T-1.15 报价时效与重算:标记「已过期」+ `POST /plans/{id}/recalc`(2026-10-01)

§41.2 把报价快照落了库,但**没有一个字**说这份快照什么时候会过时。本任务补上两半:

| 半 | 做什么 | 为什么非做不可 |
| --- | --- | --- |
| A | **改价时把受影响的方案标成「报价已过期」** | 标记只让「旧」这件事**可见**。不做这一半,用户看到的仍是一个**看起来权威、实际错误的数字** —— 那比没有报价更糟 |
| B | `POST /plans/{id}/recalc`(按**当前**价重算) | 让用户有路可回。A 只负责报警,B 负责解决 |

**契约 / PRD 一个字没动** —— 这是个纯实现任务(路径、方法、响应形状 §41 时就已经写进
`openapi-c.yaml` 了)。新增的是实现与 `V11` 迁移。

### 42.1 「调价」到底指什么:**上游只写了"产品调价",口径由本次定宽**(用户决策)

上游原话是「产品调价」(PRD §12 / T-1.15 卡片)。**只有瓷砖单价算不算调价** ——
`t_quote_param` 里的**全局报价参数**(辅料单价 `aux_unit_price`、损耗率 `loss_rate`)
动了,已生成的报价**一样全部过期**,因为它们是全店共用的系数。

**用户 2026-10-01 拍板:两者都算(更准)。**

| 触发源 | 判定 | 落点 |
| --- | --- | --- |
| 后台改瓷砖单价 | 单价**真变了**才标 | `ProductService.update` |
| 后台批量导入改单价 | 同上(仅覆盖已存在的行) | `ProductImportRowWriter.apply` |
| 后台改全局报价参数 | `aux_unit_price` / `loss_rate` 真变了才标 | `QuoteParamService.upsert` |

**代价是明确接受的**:后台把损耗率从 6% 调成 7%,会让**全店所有方案**一起变成「已过期」。
这不是误伤 —— 那些报价**确实**全部按旧损耗率算的,标出来是**事实**。
反过来说,如果只标瓷砖单价,就会留下一批「按旧损耗率算、却显示报价有效」的方案,
而那正是这个功能要消灭的东西。

**两处收窄,防止标记变成噪音:**

1. **必须先比旧值**(`PlanQuoteExpiryMarker.priceChanged`,用 `compareTo`)。
   `BigDecimal.equals` 比的是**小数位**:`100.00` 与 `100.0` 判为不等 —— 后台"保存一下"
   (值没改,只是重新提交)会让全店方案集体报过期,而**没有任何现有测试会红**。
   用 `compareTo` 后,`100.00 vs 100.0` 判为没变。
2. **不是所有报价参数都算。** 白名单写在 `QuoteParamKey.affectsQuote()`
   (`AUX_UNIT_PRICE` / `LOSS_RATE` 两个)。**不能**用「是不是单价类参数」来推导 ——
   `loss_rate_min` / `loss_rate_max` 也是合法区间参数,但它们只用于**校验**
   (`min ≤ loss_rate ≤ max`),不参与算钱。把它们算进去的后果是:
   把允许范围从 5%~8% 调成 4%~9% 会**全店报过期**,而一个数都没变。

### 42.2 ⚠️ 标记**不得**抬 `updated_at`(本轮最隐蔽的一条)

`t_plan.updated_at` 带 `ON UPDATE CURRENT_TIMESTAMP`,而「我的方案」列表**按
`updated_at desc` 排序**。若不处理,一次全局参数调价的连锁反应是:

```
改一次损耗率 → 所有方案被 UPDATE → MySQL 自动抬 updated_at
            → 每个用户的方案列表顺序被全量打乱(且时间显示成"刚刚")
```

**现有测试全绿,页面也不报错** —— 用户只会觉得"我的方案顺序怎么变了"。
所以 `PlanQuoteExpiryMarker` 的两条 UPDATE 都显式带上:

```sql
updated_at = updated_at      -- 显式把自动更新列赋成它自己的当前值 ⇒ 抑制自动更新
```

(MySQL 语义:显式赋值会覆盖 `ON UPDATE CURRENT_TIMESTAMP` 的自动行为。)
集成测试逐条断言 **标记前后 `updated_at` 完全相等**,并且**先把时间回拨 60 秒**
再断言 —— `DATETIME` 只到秒,不回拨的话断言会恒真(秒都没跨过去)。
`total_amount` 同样不动:标记说的是"这个数**过期了**",不是"这个数**变了**"。

**反过来,重算成功时是 `updated_at = NOW()`** —— 用户真的点了一下、数真的变了,
列表顺序该抬。同一张表上两种相反的处置,别混。

### 42.3 标记必须与改价**同事务**,不接受"循环后批量补标"

批量导入每行走一个 `REQUIRES_NEW` 事务。把标记挪到导入循环之后"统一标一次"
能省掉若干条 UPDATE,但代价是:

```
第 3 行改价成功 → 进程中断 → 前两行的方案已按新价、却显示「报价有效」
```

一批**看起来最新、实际按旧价算**的报价 —— 正是本功能存在的意义所在。
故标记**留在行写入的同一个事务里**,接受"每改一行一条 UPDATE"。
(同样理由:批量导入只对**已存在**的行判价 —— 新增 SKU 没有"旧价",
不存在任何按旧价算过的方案,不该标。)

### 42.4 重算失败一律「什么都不改」:`90001`

`PlanRecalcService.recalc` 的**所有读与校验都在任何写之前** —— 算不出就整单不落,
**一个字节都不写**(§41.2 那条红线的沿袭)。

| 情况 | 返回 | 为什么不是 30001 |
| --- | --- | --- |
| 方案不存在 / 别人的 / 已逻辑删除 | **30001** | §41.4:措辞完全相同,不送可枚举他人 id 的探针 |
| 未登录 | **401** | 真实状态码 |
| 密文坏 / 无户型快照 / 无部位行 / 某行 `gross_area` 为 null / SKU 查不到 / 报价参数未配 | **90001** | §41.4 的先例:**方案在,坏的是系统** |

**为什么这六种归一类**:它们的共同点是**用户无法自修**。返 30001 会让用户以为
方案丢了,从而去做一些不该做的操作(重建一个、或者以为系统把它删了)。
失因只进 WARN 日志,消息不泄内部细节。

**为什么"什么都不改"是硬要求**:半途改一半会留下「新的 `total_amount` 配旧的
`t_plan_quote` 明细行」—— 一个自己跟自己都对不上的方案。那比"重算失败、维持原样"糟得多。
**没有新增错误码** —— `ErrorCode` 枚举被契约逐值钉住(`ErrorCodeContractTest`)。

### 42.5 ⚠️ 事实订正:多空间方案**并不**落进「保守不报价」分支(推翻 §41.2 末段)

§41.2 末段写着「多空间方案会落进『保守不报价』分支」。**这是错的。**
`PlanAutoSaveService.resolveSpace`(现 `PlanSpaces.resolve`)的四条分支里,
**没有一条**会因为"有多个空间"而放弃报价 —— 最后一条是
「都不命中 ⇒ 取第一个 + WARN,**不报错**」。户型里只要有**一个**空间就一定能定出面积,
`buildDetailed` 的五个失败条件里也不含空间数量。

**实测证据(可重跑)**:`PlanRecalcIntegrationTest` 用**多空间户型**生成一次 →
**不改任何价** → 直接重算 → 断言两次总额**逐分相等**。若生成时真的没报价,
第一次就没有总额可比,这条用例根本走不通。

原句保留在 §41.2 未改(过程记录只追加),此处按事实订正。
**§41.8 第 2 条同步标注**:多空间是否**应该**只算某一个空间,仍是产品问题
(§35.8 列的四条规则只决定**取哪个空间**,不决定**该不该**按全屋算),
但「当前实现不报价」这句话不成立。

### 42.6 口径一致怎么证明:只读存储值 + 静态调用**同一个**方法

「生成时的价」与「重算后的价」必须同口径。**不靠"看起来调的是同一个方法"**,靠三条:

| 防线 | 做法 |
| --- | --- |
| 面积不重推 | 重算**读** `t_plan_part.gross_area`(**存储值**),不重新推导 |
| 定空间不复制 | 抽 `PlanSpaces.resolve`,**静态调用**。生成与重算调的是同一个方法,不是两份拷贝 |
| 合并规则不复制 | `PlanAutoSaveService.mergeParts` 改包级 `static`,重算同样**静态调用** |
| 可执行证明 | 多空间生成 → 不调价 → 直接重算 → **逐分相等**(见 42.5) |

防线 2、3 是同一类隐患:复制一份出来,**两边各自的测试都可能全绿**,
只在某个多空间 / `local` 部位的边界上分叉 —— 那是最难查的一种 bug。

顺带:重算**必须是「算了两次就不会不一样」**。故 `PlanQuoteSnapshotService`
新增 `buildDetailed` 返回 `SnapshotBundle(snapshot, quote)`,把
**落库用的整理结果**与**返回给前端的原始报价**绑在一个 record 里 ——
结构上不允许出现"重算返回的数 ≠ 存下来的数"。

### 42.7 `materialFee` 那处必填不一致(§41.6)**仍在**,面没扩大

重算路径与生成路径共用 `buildDetailed`,所以「算不出 ⇒ `materialFee` 为 null ⇒
`@JsonInclude(NON_NULL)` 把它省掉」在重算响应里**同样**成立。
契约 `common.PlanPart.required = [part, sku, materialFee]` 仍然写着它必有。
**没扩大也没缩小**,§41.6 的处置(可见登记、等收紧契约)不变。

### 42.8 校验结果(2026-10-01)

**契约侧**:`bash outputs/scripts/ci.sh` → **7/7 PASS**,退出码 0。

**契约 ↔ 实现**:`bash outputs/scripts/contract-diff.sh`(先跑,默认 `GATE=1`)
→ 退出码 **0**,两侧干净。Java 侧 `Tests run: 42, Failures: 0`;Python 侧 3 passed。

| 项 | §41(基线) | 本次 |
| --- | ---: | ---: |
| 契约端点(Java 归属) / 实现路由 | — / 82 | **71 / 83** |
| 两边匹配上并比过 | 58 | **59** |
| 契约有实现无 · **意外缺失** | 0 | **0** |
| 实现有契约无 · 多余路由 | 0 | **0** |
| 字段名 / 类型 / 枚举值 / 必填 不一致 | 0 / 0 / 0 / 0 | **0 / 0 / 0 / 0** |
| 挂账已过期(整条 / 字段级) | 0 / 0 | **0 / 0** |
| 锚点未命中 | 0 | **0** |
| 比到底的叶子 / **未对位的叶子(没判过)** | 530 / 12 | **542 / 12** |
| 无法比对(跳过 ≠ 通过) | 94 | **95** |

「已知未开工」由 12 条降到 **11 条** —— `/plans/{id}/recalc` 那条
(`source: T-1.15`)从 `contract-impl-gaps.yaml` 里删掉了。**不删会被判「挂账已过期」直接红**。
`share` 三条(`T-1.13`)仍在表里,那三条确实没开工。

> ⚠️ 「**未对位的叶子 12**」「**无法比对 95**」这两个数**必须有人看** —— 它们**没判过**,
> 不是通过。明细见报告页眉与 §40.5 / §40.10。1 条新增来自 recalc 被纳入比对。

**Java 全量**:`mvn -s … test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true`

```
Tests run: 452, Failures: 0, Errors: 0, Skipped: 0
```

419 → **452**(+33:`PlanQuoteExpiryIntegrationTest` 10 + `PlanRecalcIntegrationTest` 12
+ `PlanRecalcContractTest` 4 + `PlanSpacesTest` 5 + `PlanContractTest` 2)。
**0 skipped** —— 没有"跳过"被当成"通过"。既有 419 条**零改动**:
本轮只碰了两个测试文件,`SchemaMigrationTest` 是**追加一个版本号** `"11"`,
`PlanContractTest` 是**纯新增 + 把一个私有辅助方法泛化**(原断言原样保留)。

**`V11` 迁移**:只加两个索引,`SchemaMigrationTest -Ddb.verify=true` 通过
(「Successfully applied 1 migration …, now at version v11」;第二次「No migration necessary」)。
⚠️ 提醒仍在:全项目 21 个集成测试都带 `spring.flyway.enabled=false`,
**新增 `V*.sql` 后必须先跑一次 `SchemaMigrationTest`**,否则会看到一堆 `90001`,
而真因只是 `Unknown column`(§41.7 踩过一次,本次又先踩了同样的顺序)。

### 42.9 仍未做(不许读成「已绑住」)

1. **前端没有「已过期 → 点此重算」的入口** —— 后端闭环通了(`quoteExpired` 会置位、
   `recalc` 会清位),但「我的方案」页还没有把这两件事接起来(F-1.8~F-1.10 未做)。
   **用户目前看不到"过期",也点不到"重算"** —— 这是本轮最该记住的一条。
2. **「已过期」没有对外的读出口变化** —— `quoteExpired` 本来就已在
   `PlanSummary` / `PlanDetail` 响应里(§41),本次只是让它**真的会被置位**。
3. **`materialFee` 的必填不一致**(42.7 / §41.6)—— 已登记,**未消除**。
4. **多空间"该按哪个空间算钱"仍是产品问题**(42.5 / §35.8)——
   本次只保证两侧口径**一致**,没回答"合不合理"。
5. **`recalc` 不写 `t_outbox`、不重出图** —— 重算只动钱,不动图(图与价是两件事)。


---

## 43. T-1.13 分享:三条接口的口径、令牌与隐私面(2026-10-01)

**一句话**:方案不再只能自己在本机看。用户点一次「生成分享」拿到一条链接,
**拿到链接的任何人 —— 不需要账号 —— 就能看到那一份方案的只读快照**;
点「废除」,链接当场失效。

```
POST   /api/v1/plans/{id}/share    生成分享链接(需登录,限本人方案)
DELETE /api/v1/plans/{id}/share    废除分享链接(需登录,限本人方案)
GET    /api/v1/share/{token}       公开只读分享页(**不需登录**)
```

**本次没有动契约 YAML,一个字都没动** —— 三条接口、`ShareResult`、
公开页的 `security: []` 早就在 `openapi-c.yaml` 里写好了。本卡是把契约**兑现成实现**。

### 43.1 三条决定来自用户拍板(2026-10-01)

这三处都是**上游没说、必须选一个**的地方,用户逐条定的:

| # | 决定 | 直接后果 |
| --- | --- | --- |
| ① | **二维码由前端画** | 后端只回 `shareUrl`;契约里那个带 `x-todo` 的 `qrCodeUrl` **永远不填**(§43.2) |
| ② | **本轮只做后端** | F-1.8 / F-1.9 / F-1.10 三个页面留到下一轮 —— **用户目前仍然点不到「分享」**(§43.12) |
| ③ | **只做「废除即失效」** | 没有有效期、不加 `share_expires_at` 列、**不新增 `V12`**;对外「已废除」与「从未存在」完全一样(§43.4) |

### 43.2 `qrCodeUrl` 恒空 —— 一个 `x-todo` 字段的落地方式

契约 `ShareResult` 有三个字段:`shareToken` / `shareUrl` / `qrCodeUrl`。
其中 `qrCodeUrl` 带 **`x-todo`** —— 意思是**上游从没定义过它的返回结构**
(是一张图片的地址?一段 base64?还是一个"生成二维码"的接口地址?)。
按 CLAUDE.md §1.3 第 1 条的规矩,这种字段**先问,别自己定**。

问过之后用户选了「前端画二维码」:前端用任意二维码库把 `shareUrl` 画成图就行,
后端根本不需要知道"二维码"这回事。所以:

- VO(`ShareResultView`)用 `@JsonInclude(NON_NULL)`,`qrCodeUrl` 恒为 `null`
  ⇒ 序列化后**整个键消失**,而不是发一个 `null` 出去;
- 契约里这三个字段**都没写 `required`**,所以"不出现"是**合法**的,不构成违约;
- 有一条测试钉住这件事(`PlanShareContractTest.qrCodeUrlIsOmitted`),
  它断言的不仅是"没有 `qrCodeUrl`",还包括"**只有这两个字段**" ——
  免得以后有人顺手往里塞东西。

**这是一处已知的不一致面**,与 §41.6 的 `materialFee` 同类:**契约里写着、实现里没有**。
登记在此;等契约收紧 `required` 或明确删掉 `qrCodeUrl` 时一并消除。

### 43.3 令牌:256 bit、Base64URL,刻意不用 UUID

一次分享 = 给一个方案配一个令牌,落在 `t_plan.share_token`(V4 建的唯一键
`uk_plan_share_token`,`VARCHAR(64)`)。生成方式是:

```java
byte[] raw = new byte[32];                                   // 32 字节 = 256 bit
RANDOM.nextBytes(raw);                                       // SecureRandom,static final
Base64.getUrlEncoder().withoutPadding().encodeToString(raw); // → 43 个字符
```

四条理由,逐条:

1. **为什么是 256 bit** —— 架构 §13.5 要求「≥128 bit」。这个令牌是
   **不登录就能换到一份用户完整方案**的全部凭据,是这条链路上**唯一**的机密。
   把穷举的代价从"不可行"变成"绝对不可行",成本只有几十个字节。
2. **刻意不用 `UUID.randomUUID()`** —— 项目里别处拿 UUID 当 traceId、批次号,那没问题,
   因为**那些不是安全凭据**。UUID v4 只有 **122 个随机位**(其余 6 位是固定的版本号与变体位),
   **不满足 ≥128 bit**。分享令牌是凭据,不能沿用那个习惯。
3. **为什么是 Base64URL 而不是标准 Base64** —— 令牌要进 **URL 路径**(`/share/{token}`)。
   标准 Base64 的 `+` `/` `=` 在 URL 里会被转义,某些网关/场景下还会被**截断** ——
   而截断之后的令牌**不报错,只是打不开**,极难排查。
   URL-safe 字母表只有 `A-Za-z0-9-_`,43 个字符,`VARCHAR(64)` 放得下。
4. **万一撞唯一键** —— 概率约 2⁻²⁵⁶,数据库会抛 `DuplicateKeyException` → `90001`。
   **不重试是刻意的**:能撞上说明随机源出了问题,悄悄重试会把一个真实的
   基础设施故障掩盖成"一切正常"。

`SecureRandom` 声明成 `static final`:它的构造(找熵源、自播种)比取一次样贵得多,
每次分享都新建一个等于把可忽略的开销变成明显开销。它本身是线程安全的。

### 43.4 「取不到」的四种情形一律同码同措辞 —— 这是**探针**问题

公开页 `GET /share/{token}` 有四种"取不到"的情形:

1. 令牌**从来不存在**(随手编一个);
2. 令牌格式合法但**没匹配上**;
3. 分享已被**废除**(`share_token` 被置空了);
4. 方案被**逻辑删除**(`is_deleted = 1`)。

**四种一律 `30001` + 固定文案「方案不存在」,不区分。** 理由与 §41.4 完全一样,
只是探针的目标从"方案 id"换成了"令牌":

> 一旦区分开来 —— 比如"已废除"回 30002、"不存在"回 30001 —— 就等于对外提供了一个
> **「这个令牌曾经有效过」的判据**。攻击者可以拿它判断自己猜中的令牌是不是真的存在过,
> 把枚举从"盲猜"变成"有反馈的搜索"。而这个接口**不需要登录**:
> 它是全项目唯一一个匿名可达的读接口,也是最该保守的一个。

实现上这四种走的是**同一个常量**(`PlanOwnership.NOT_FOUND_MESSAGE`),
不是四处各写一遍文案 —— 文案一旦分叉不会有任何测试红。

**顺带说清一个边界**:令牌为空/空白时**显式拦掉、不进查询**。但这一行
**今天不是必需的** —— 见下。

#### 43.4.1 ⚠️ 事实订正:我为那行守卫写下的理由**曾经是错的**(故障注入才发现)

实现时我在代码注释里写过一句话,大意是:

> 「这一步不是防御性编程,是**必需**的:MyBatis-Plus 的 `eq` 在**值为 null 时静默跳过条件**
> (项目里 `PlanOwnership` 的注释明确写了这个行为)。少了这一步,`share_token = ?`
> 会整个消失,查询退化成『取第一条方案』—— **任何人凭一个空令牌就能看到别人的方案**。」

**按"不放过一个未经证实的信念"的规矩,我没有把它当成一句顺手的注释,而是做了一次故障注入**:
把那个空白检查**整个删掉**(先删 `token != null &&`,再直接改成 `if (false)`),
然后跑那条件为这个理由而写的用例。

**结果:两次都通过。**

也就是说 —— **我写下的是一个错误的信念**:

- MyBatis-Plus 的 `eq(列, null)` **并不会跳过条件**,它会如实拼出 `share_token = NULL`;
- 而 `= NULL` 在 SQL 里**永远不成立**(不是"取第一条",是一条都取不到);空串 `''` 同理。

⇒ 少了那个检查,结果**仍然是 30001**。更麻烦的是:**我那条测试是恒真的假绿** ——
它会绿,但**绿的原因和我以为的原因不是同一个**。这类测试比没有测试更危险:
它让人以为某个风险已经被守住了。

**处置(两件事都做了)**:

1. **改写代码注释**,如实写清:这一行**今天不是必需的**;留着是因为
   "不去依赖 `eq` 对 null / 空串的拼接行为" —— 那是 MyBatis-Plus 的实现细节、
   不是我们的契约,换版本或有人改成 `eq(StringUtils.isNotBlank(token), …)`
   的写法就可能变;而这里一旦退化成"取第一条未删除的方案",就是
   **一个不报错的越权读取**,代价与一行判断完全不成比例;
2. **改写测试**:`@DisplayName` 改成 **`blankTokenIs30001`**,并写明
   「这是**契约断言**(空令牌必须回 30001),**不是守卫测试** ——
   即使把服务里那行检查删掉,这条用例**也会通过**」。用例本身保留,因为
   契约要求的就是**外部行为**。

**还留了一处没改,如实记下**:`PlanOwnership.ownedUpdate` 的 Javadoc 里,
那句"`eq` 会静默跳过 null"的说法**仍在**。本次**没有顺手改它** ——
那是另一个类、另一个上下文,顺手改会把本轮的改动面从"分享"扩大到一个
被多处依赖的归属判定类上。**已报给用户,待单独处置**(§43.12 第 3 条)。

> **这条是本卡最有价值的产出**。它说明:光有测试不够,还得证明
> **测试会因为什么而红** —— 一个自己写下的、听起来很合理的理由,可能从来没人验证过。

### 43.5 重复点「生成分享」:返回**同一条**链接,不重新生成

用户重复点「生成分享」是个很自然的动作(没看到反馈、或者想再复制一次链接)。
如果每次都生成新令牌,**家人手里那条已经发出去的链接会当场作废**,
而用户完全不知道自己做错了什么。

所以 `share()` 的第一步就是:**已经有令牌就直接返回它** —— 天然幂等,点几次都是同一个链接。

**用户「双击」则是两个并发请求**,两个都会查到"还没有令牌",然后各写各的,
后写的把先写的覆盖掉(而先写的那条**可能已经进了剪贴板**)。防法是把这次写变成
一次**比较并交换**:

```java
planMapper.update(null, ownership.ownedUpdate(userId, planId)
        .isNull(Plan::getShareToken)          // ← 只有"当前还没有令牌"才写得进去
        .set(Plan::getShareToken, token)
        .setSql("updated_at = updated_at"));  // 见 §43.6
```

`affected == 0` 说明**别人先写成了** ⇒ **重读那一行,返回"赢家"写下的令牌**,
而不是我们刚生成的那个。两次响应拿到同一个令牌 —— 有测试用 **8 个线程 + 起跑门**
(`CountDownLatch`)把它钉住。

### 43.6 `updated_at` 压住不抬(与 §42.2 同一条禁忌)

分享**不在方案卡片上显示任何东西**,用户并没有"改过这个方案"。
而 `t_plan.updated_at` 带 `ON UPDATE CURRENT_TIMESTAMP` —— `share_token` 一变,
**MySQL 会自己抬这个时间戳,写不写 Java 代码都一样**。

抬它的后果是**无理由打乱列表顺序**(「我的方案」是 `order by updated_at desc, id desc`)。
所以分享与废除都显式写上:

```java
.setSql("updated_at = updated_at")   // 自己赋给自己 = 值没变 ⇒ ON UPDATE 不触发
```

有测试(`sharingDoesNotTouchUpdatedAt`)把 `updated_at` 回拨 60 秒后做分享与废除,
断言**与基线逐字相等**。

### 43.7 `share()` **故意**不带 `@Transactional`

这不是漏了。上面那个"输了就重读赢家"依赖读到的**是别人刚提交的新值**;
而 MySQL 默认的 **REPEATABLE READ** 下,一个事务的读视图在**第一次普通 SELECT
时就固定了**:

```
[如果 share() 在事务里]
  第一次 requireOwned  → 普通 SELECT,读视图就此固定
  另一个请求提交了令牌
  CAS 失败 → 重读 → 读视图里仍然是 NULL → 报 30001
  ⇒ 用户双击,第二次拿到「方案不存在」
```

这正是 §43.5 要避免的效果。而这里**没有跨语句的不变量要靠事务来守**:
每条语句自身原子(CAS 就是原子的),所以**放开自动提交反而正确**。

`revoke()` 同理不带事务。`readShared()` 带 `@Transactional(readOnly = true)` ——
它只读,事务是为了拿一个一致快照,没有上面这个问题。

### 43.8 隐私面:分享页发的是一整份 `PlanDetail`

**这条必须显式写下来,因为它是一个真实的暴露面,而且契约就是这么定的。**

拿到链接的任何人(**不需要账号**)可以看到:

- 方案的**明文户型**(每个空间的长宽、开口)——
  注意:库里存的是密文 `layout_enc`,但这个接口发的是**解密后的明文**;
  契约校验表 T-8 要求 `layout_enc` **不得出现在任何对外响应里**,分享页不例外;
- **完整报价**(总价、逐部位明细、报价规则快照);
- **全部效果图**;
- **令牌本身**(`shareToken` 也在响应里)。

这是**契约写定的,不是实现的选择** —— 分享页的 `data` 就是
`./openapi-common.yaml#/components/schemas/PlanDetail`,与「我的方案」详情接口
**同一个 schema**。契约这么定有它的道理(分享出去的就是那份方案;
另造一个"瘦 schema"会让两边各自漂移),但**后果必须有人知道**。

**实现上怎么保证两边不分叉**:抽出了一个 `PlanDetailAssembler`,
`PlanService.detail()` 与 `PlanShareService.readShared()` **静态调用同一个**
`assemble(plan)`。有一条测试做的是**整段 JSON 逐字相等**的断言
(`sharedPageIsIdenticalToOwnerDetail`),不是挑几个字段比。

> 为什么值得单抽一个组件:`.copy` 那轮已经为"两处口径必须同源"付过一次学费(§42.6)。
> 分享页和详情页要是各抄一份组装逻辑,迟早会在某个字段上分叉,
> 而**两边各自的测试都可能全绿**。

### 43.9 架构 §13.5 的「访问范围控制」:**上游没展开,本次不补**

架构 §13.5 讲分享时有一句「访问范围控制」。**这句话在架构文档里没有展开** ——
没说"范围"是什么(只能看不能改?某些字段不可见?限时限次?),也没有任何字段或接口定义它。

按 CLAUDE.md §1.3 第 1 条的规矩,本次**没有自作主张补**任何"范围"语义。
用户选择的是**只做「废除即失效」**:

- 不做有效期(无 `share_expires_at` 列,无 `V12`);
- 不做访问密码、不做次数限制、不做水印;
- **唯一的失效手段就是废除**,而废除后"已过期"与"从未存在"对外**完全一样**(§43.4)。

**登记为未解决项**:那句话要么由上游展开、要么由契约明确删除,不能一直悬着。

### 43.10 ⚠️ 本次登记的两处**既有**缺陷(不在本卡范围,但必须写下来)

这两条都是分享功能**暴露出来**的上游问题 —— **都不是本卡引入的,也都**没修**。

#### (1) 效果图 URL 是 **24 小时临时地址**,没有任何转存

架构 §15.1 的实测结论里(表格「出图地址」一行)写着:

> OSS **临时 URL(24 小时过期)** —— ⚠️ 调用方(T-1.11)须及时转存对象存储

**T-1.11 没有做转存**(§41.5 把对象存储又顺延了一次)。后果是:

> **分享链接本身不过期,但一天之后接收方看到的是空图。**
> 而且失败方式是"图裂",不是报错 —— 用户不会知道是为什么。

自己的方案页也有同样的问题,只是"自己当天看的概率高",所以不明显;
**分享把这个问题放大了**:链接是发给别人的,别人很可能隔天才打开。

⇒ 登记为**未解决项**。修法是补转存模块(T-1.11 的欠账);
或让分享链接带有效期 —— **但用户已明确选择不做有效期,所以只剩转存这一条路**。

#### (2) `share_token` 列**大小写不敏感**

`V4__plan_part_and_quote.sql` 建表时用的是**表默认排序规则**:

```sql
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
```

`_ai_ci` = accent-insensitive + **case-insensitive**。于是:

- `share_token = 'AbC…'` 与 `'abc…'` 在 SQL 里**相等** —— 令牌比较是大小写不敏感的;
- 而 Base64URL 字母表里 `A` 与 `a` 是**两个不同的字符** ——
  也就是说**令牌的有效熵从 256 bit 降到约 213 bit**。

**这不是一个可被利用的漏洞**(213 bit 仍然物理上不可穷举),但**作为密钥不够干净**:
凭据的比较应当**逐字节精确**。修法是把该列改成 `COLLATE utf8mb4_bin`,
用**新版本迁移**(已应用的 `V4` **绝不回改**)。

**有一条测试故意钉住这个现状**(`tokenComparisonIsCaseInsensitiveToday`):
它断言"今天大小写不敏感",**等有人把列改成 `utf8mb4_bin` 时这条用例会变红** ——
红色是在提醒"债务还清了,把这条测试和这条登记一起删掉",**不是"改坏了"**。
(写这条测试时先踩了一次 90001:用 `insertPlan` 造了一个没有户型快照的裸方案,
`assemble` 读不出密文就报了 90001 —— 改用生成的方案再单改 `share_token` 列才对。)

### 43.11 校验结果(2026-10-01)

**契约侧**:`bash outputs/scripts/ci.sh` → **7/7 PASS**,退出码 0。

**契约 ↔ 实现**:`bash outputs/scripts/contract-diff.sh`(先跑,默认 `GATE=1`)
→ 退出码 **0**,两侧干净。Java 侧 `Tests run: 42, Failures: 0`;Python 侧 3 passed。

| 项 | §42(基线) | 本次 |
| --- | ---: | ---: |
| 契约端点(Java 归属) / 实现路由 | 71 / 83 | **71 / 86** |
| 两边匹配上并比过 | 59 | **62** |
| 契约有实现无 · **意外缺失** | 0 | **0** |
| 实现有契约无 · 多余路由 | 0 | **0** |
| 字段名 / 类型 / 枚举值 / 必填 不一致 | 0 / 0 / 0 / 0 | **0 / 0 / 0 / 0** |
| 挂账已过期(整条 / 字段级) | 0 / 0 | **0 / 0** |
| 字段级挂账 · 本次未生效 | 0 | **0** |
| 锚点未命中 | 0 | **0** |
| 比到底的叶子 / **未对位的叶子(没判过)** | 542 / 12 | **591 / 12** |
| 无法比对(跳过 ≠ 通过) | 95 | **101** |

「已知未开工」由 11 条降到 **8 条** —— 三条 `share`(`source: T-1.13`)
从 `contract-impl-gaps.yaml` 里删掉了。**不删会被判「挂账已过期」直接红**。
剩下的 8 条 = 后台账号 5 条(PRD §10.3 未展开字段)+ AI 咨询 3 条(T-1.6)。

> ⚠️ 「**未对位的叶子 12**」「**无法比对 101**」这两个数**必须有人看** ——
> 它们**没判过**,不是通过。明细见报告页眉与 §40.5 / §40.10;
> 新增的 6 条来自 share 三条被纳入比对(枚举取值在实现侧推不出,多为走后台字典的字段)。
>
> ⚠️ 另有一处**比对工具结构性看不到**的东西,靠反射测试补:契约里这两条写接口
> **没有 `requestBody`**,但比对工具只能比"契约**声明过**的字段" ——
> 实现凭空加一个 `@RequestBody` 它**抓不到**。`PlanShareContractTest` 用反射断言
> 方法上不存在 `@RequestBody`、且只有唯一一个 `@PathVariable`。
> 这与 §42 的 `recalc` 是同一条思路。

**Java 全量**:`mvn -s … test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true`

```
Tests run: 481, Failures: 0, Errors: 0, Skipped: 0
```

452 → **481**(+29:`PlanShareContractTest` 7 + `PlanShareIntegrationTest` 17
+ `PlanContractTest` 5)。**0 skipped** —— 没有"跳过"被当成"通过"。

**既有 452 条零改动**(`git diff --numstat` 佐证,改动面**只有** `com.tileai.plan` 一个包
+ `application.yml` + 测试的挂账清单):

| 文件 | +/− | 性质 |
| --- | --- | --- |
| `PlanService.java` | +14 / −187 | 组装逻辑**搬**到 `PlanDetailAssembler`(净减) |
| `PlanRecalcService.java` | +4 / −4 | 只把 `PlanService` 换成 `PlanDetailAssembler` |
| `PlanOwnership.java` | +11 / −2 | 新增 `NOT_FOUND_MESSAGE` 常量,替掉两处字面量 |
| `PlanContractTest.java` | **+85 / −0** | **纯新增**,既有 15 条一个字没动 |
| `PlanManagementIntegrationTest.java` | +8 / −1 | 改一个方法名 + 加两条断言 |
| `contract-impl-gaps.yaml` | +0 / −12 | 删三条 `T-1.13` 挂账 |
| `application.yml` | +9 / −0 | 新增 `tile.share.base-url` |

其余模块(quote / product / agent / layout / common / admin)**零改动**。

**权限矩阵(本卡最容易悄悄改坏的地方)**:

| 接口 | 带 Cookie | 不带 Cookie |
| --- | --- | --- |
| `POST /plans/{id}/share` | 200 | **401** |
| `DELETE /plans/{id}/share` | 200 | **401** |
| `GET /share/{token}` | 200 | **200**(公开) |

两张靠的是**同一个** `SecurityConfig` 清单:`/api/v1/plans/**` 已在受保护前缀里
(注释原文就写着"含分享、重算"),`/api/v1/share/**` 落在末尾的
`anyRequest().permitAll()`。**这次是对的,但属于"巧合般的正确"** ——
所以两侧都用测试**真的调了一次**钉住。

**本次不新增迁移**:`V11` 仍是当前版本,`SchemaMigrationTest` 无需改 ——
`readShared` 的 `share_token = ?` 走 `uk_plan_share_token` 唯一键
(一笔最多匹配一行),`is_deleted` 只是回表后过滤;`share` / `revoke` 走主键。

### 43.12 仍未做(不许读成「已绑住」)

1. **前端没有「分享」入口** —— 三条接口都通了,但**用户点不到**。
   `shareUrl` 已经能正确拼出来(测试断言过),可是没有任何页面显示它,
   也没有"复制链接""画二维码"的按钮。F-1.10 由「阻塞」变「可开工」,
   但**页面上仍然是零**(与 §42.9 第 1 条同类)。
2. **`qrCodeUrl` 恒空**是登记的已知不一致面(§43.2)—— 已登记,**未消除**。
3. **`PlanOwnership` 里那句关于 `eq` 的错误注释仍在**(§43.4.1)——
   已报给用户,**本次没改**。
4. **效果图 24 小时过期**(§43.10-1)—— T-1.11 的转存欠账,**未修**。
5. **`share_token` 大小写不敏感**(§43.10-2)—— 已用测试钉住,**未修**。
6. **架构 §13.5「访问范围控制」未定义、未实现**(§43.9)—— 已登记,**未展开**。
7. **`SHARE_BASE_URL` 上线前必须改** —— `application.yml` 里默认
   `http://localhost:5173`。**这是个绝对地址,而且是发给别人的** ——
   上线忘了配,用户分享出去的链接指向 `localhost`,**自己用本机试还试不出来**。
8. **分享是"快照"还是"实时"没有定义** —— 本次实现是**实时**:分享页读的是
   **当前**方案状态,方案改名或重算,**别人打开链接看到的就是新的**。
   契约没说哪种,这里是按"实时"做的(还能直接复用
   `assembler.assemble`)。用户若要"发出一份就不会变"的快照语义,
   那是一次**独立的产品决策 + 契约改动**,不是实现能自己定的。
9. **`materialFee` 的必填不一致**(§41.6 / §42.7)—— 仍在,**面没扩大**。

---

## 44. F-1.9 我的方案 + F-1.8 报价展示与重算:前端落地与三处诚实边界(2026-10-01)

**本轮不写一行 Java、不动一个字契约** —— 后端六条接口(T-1.12 五条 + T-1.15 的
`recalc`)早已落地且已验收,缺的只是**界面**。做完之后 C 端第一次形成闭环:
**选砖 → 录户型 → 生成 → 我的方案 → 打开方案 → 改名 / 另存为 / 按当前价重算**。

### 44.1 范围由用户拍板(2026-10-01)

| 问题 | 用户选择 | 后果 |
| --- | --- | --- |
| 这一轮做到哪一步 | **列表 + 详情 + 管理 + 报价重算** | F-1.9 主体 + F-1.8 全部 |
| PRD 里的「重新编辑」这次做吗 | **这次不做** | 生成页**不回填草稿**;F-1.9 只做到"看与管理",没做到"改一改再出图" |
| 分享按钮 / 公开分享页 | **本轮不接**(F-1.10) | §43.12-1 那条"用户点不到分享"**依然成立** |

### 44.2 结构:列表只读,管理动作全部只在详情页

- `/plans`(`MyPlansView.vue`)—— **只读导航**:卡片 + 分页,整张卡片是链接。
- `/plans/:id`(`PlanDetailView.vue`)—— 效果图 / 户型 / 部位 / 报价 +
  **改名 / 另存为 / 删除 / 重算四个动作全在这里**。

**为什么不也往列表页放一份:** 同一个动作有两处实现,就要有两处测试,而且**迟早各自漂**
(§42.6 已经为"口径必须同源"付过一次学费)。详情页是"我打开了一份方案,现在想对它做点什么"
的自然位置。

列表页的骨架**照抄 `ProductListView.vue`**(卡片网格 + 分页器 + 四分支模板),
不另立一套取值/加载/错误态写法 —— C 端已经有一套了。

### 44.3 三处"改错了不报错,只悄悄出错"的边界(本轮的核心)

这三条都不是新问题,但**都是第一次被前端消费** —— 消费方式决定了它们是"显示成 0"
还是"显示成 —",而**两者都不会让任何测试变红**。

#### 44.3.1 `PlanPart.materialFee`:契约标必填,实现会**整个省略**

`PlanPartView` 带 `@JsonInclude(NON_NULL)`(`plan/dto/PlanPartView.java:28`)。
报价算不出来时该值为 null ⇒ **JSON 里根本没有这个键**。契约把 `materialFee` 标
`required` 是照"报价一定算得出来"的理想情况写的(§41.6 / §42.7 已登记)。

⇒ 前端 `materialFee?: number`,渲染一律 `?? '—'`。**不是 `?? 0`** ——
那是把"没算出来"伪装成一个真实的数字,而 ¥0 看起来像一张真报价单。

⚠️ **测试里必须用 `delete` 把字段从 mock 上拿掉,不能塞 `null`** ——
实现永远不发 `null`,塞 `null` 测的是不存在的情形。

#### 44.3.2 `totalAmount` / `quote` 可能整个没有 = **"这一次没算出价",不是 0**

`PlanSummaryView.totalAmount` 的注释原文:「**null 表示"这一次没算出价",不是 0**」。

⇒ 列表卡片显示「**未出报价**」,详情页显示「这份方案没有报价(生成时没能算出价格)」,
并且**重算按钮仍然可点** —— 那正是它存在的意义:把"当时算不出"变成"现在能算"。

**一条专门的断言钉死它**:

```ts
expect(wrapper.text()).not.toContain('NaN')   // 整页文本,不只是价格那一格
```

只要有人在任何一处写了 `totalAmount ?? 0` 或直接插值,这条就会红。
用"整页不含 NaN"而不是"这一格等于什么"是有意的:前者抓的是**这一类错**,
后者只抓你想到的那一处。

#### 44.3.3 改名的 200 上限:**契约没声明,门禁也看不见**(登记,不是修复)

上限只在后端 `PlanNames.NAME_MAX = 200` 里,由 `PlanService.rename` 按
**码点**(`codePointCount`)判,超了报 **10001**。
而契约 `PATCH /plans/{id}` 的 `name` **只有 `type: string`,没有 `maxLength`**。

⚠️ `contract-diff.sh` 比的是**路径 / 方法 / 字段名 / 类型 / 必填 / 枚举取值** ——
**约束维度它看不到**(§40 已列边界)。所以这道分歧**没有任何门禁会拦**。

前端的处置:

1. 客户端**按码点**先挡一道:`[...name].length > 200`(不是 `name.length`)。
   `'😀'` 在 UTF-16 里是 2 个单元、在 MySQL/后端眼里是 1 个码点 ——
   **150 个 emoji 是完全合法的名字**,而 `name.length` 会数成 300 并**错拒**它。
   有一条用例把这件事钉住(150 个 emoji 必须**发得出去**)。
2. **故意不加 HTML 的 `maxlength`** —— 它数的也是 UTF-16 单元,
   和后端用的是**两把尺子**。加了就会出现"浏览器拦住 100 个 emoji、后端认为完全合法"。
3. 服务端仍会回 10001 ⇒ 用既有的 `validationErrorsOf(error)` 把 `data.errors[]`
   逐条渲染出来,**不吞**。
4. 在 `tests/contract.spec.ts` 里**登记**这条分歧:

   ```ts
   expect(name?.maxLength, '契约补上了 maxLength —— 请把客户端校验改成读契约后再改这条')
     .toBeUndefined()
   ```

   哪天有人给契约补上 `maxLength`,这条会红。

**这是登记,不是修复** —— 补 `maxLength` 要走上游文档链(PRD → 架构 → 契约),
不在本轮范围(CLAUDE.md:改动必须自上而下)。

### 44.4 重算后**就地刷新**,不重新拉详情

契约写明 `POST /plans/{id}/recalc` 的 `data` **就是**重算后的完整 `QuoteResult`
(`CPlanRecalcController.recalc` 返回 `ApiResponse<QuoteResultView>`)。

⇒ 拿到就 `plan = { ...plan, quote: result, totalAmount: result.total, quoteExpired: false }`。
**不再 `getPlan` 一轮** —— 那是白打的请求,而且中间那一拍会闪一下旧金额。
有一条用例断言 `getPlan` **只被调用过一次**。

`totalAmount` 一并改掉,是因为后端同一次 `UPDATE` 里也把它覆盖成了新总额
(`PlanRecalcService:121`)、`quote_expired` 置回 false —— 前端照做,
免得页面上"总额"与"明细合计"两个数打架。

### 44.5 新增的契约断言(`tests/contract.spec.ts`,**+73/−0 纯追加**)

| 断言 | 为什么 |
| --- | --- |
| 六条接口的 `security` 都是 `[{ cookieAuth: [] }]` | 掉一条 = 多一个匿名入口;30001 的三合一防的就是"凭 id 猜别人的方案" |
| `POST /plans/{id}/recalc` **没有 `requestBody`** | 架构不变量 6。前端 `recalcPlan` 不带 body 是这条的另一半,两边都要钉 |
| `PlanSummary.required` == `[id, name, createdAt]` | 前端把 `totalAmount` / `coverImage` / `parts` 都当**可选**渲染 |
| `PlanPart` / `QuoteResult` 的 required 集 | 同 `types.ts` 里的可选/必填划分一致 |
| `PATCH /plans/{id}` 的 `name` **没有 `maxLength`** | §44.3.3 的登记 |

### 44.6 一个前端侧的发现:命名路由未注册时 `RouterLink` 会抛

生成页成功态原本只打了一行 `方案编号 {{ planId }}` 纯文本,本轮加了一个
`RouterLink` 指向方案详情。

**`to="{ name: 'plan-detail' }"` 会炸** —— `RouterLink` 解析命名路由时要求目标路由
**已注册**;而既有 `tests/generate.spec.ts` 的 memory router 只注册了
`/generate` / `/products` / `/layout`(既有测试**不许改**)。实测:6 条用例全红,
报 `instance.update is not a function`。

⇒ 改用**字符串路径** `:to="`/plans/${planId}`"` —— 它只渲染一个普通 `href`,
对"这条路由存不存在"不敏感。既有 `tests/generate.spec.ts` **零改动**、12 条全绿。

(其余新增的 `<RouterLink :to="{ name: 'plan-detail' }">` 在 `MyPlansView` 里,
而新增的 `tests/plans.spec.ts` **注册了**这条路由 —— 两处口径不同是有意的,
差别就在"目标路由在不在测试的 router 里"。)

### 44.7 回归安全:改动面

`git status` / `git diff --numstat`(2026-10-01):

```
 M tile-ai-web/src/api/types.ts                     +125 /  −0   纯追加
 M tile-ai-web/src/layouts/SiteLayout.vue             +1 /  −0   导航加一条
 M tile-ai-web/src/router/index.ts                   +15 /  −0   两条路由
 M tile-ai-web/src/views/site/GenerateView.vue       +18 /  −1   只改了那一行编号文本
 M tile-ai-web/tests/contract.spec.ts                +73 /  −0   纯追加
?? tile-ai-web/src/api/c/plans.ts                (新)
?? tile-ai-web/src/views/site/MyPlansView.vue    (新)
?? tile-ai-web/src/views/site/PlanDetailView.vue (新)
?? tile-ai-web/tests/plans.spec.ts               (新)
?? tile-ai-web/tests/planDetail.spec.ts          (新)
```

- **`tile-ai-server/` 与四份契约 YAML、PRD:`git status` 里一个字没有**;
- 既有 13 个 spec、142 条用例**零改动** —— 唯一被动到的既有 spec 是
  `contract.spec.ts`,且为 **+73 / −0 纯追加**;
- `GenerateView.vue` 的 **−1** 就是被替换掉的那一行 `<dt>方案编号</dt><dd>{{ planId }}</dd>`。

### 44.8 校验结果(2026-10-01)

| 项 | 结果 |
| --- | --- |
| 前端全量 | **180 passed / 15 文件**(基线 **142 / 13**,`87d1e62` 实测) |
| 新增 | `tests/plans.spec.ts` 13 条、`tests/planDetail.spec.ts` 20 条、`contract.spec.ts` +5 条 |
| 类型与构建 | `npm run build`(= `vue-tsc --noEmit && vite build`)**通过** |
| 后端 | 未跑(未改动;本轮不涉及 `ci.sh`) |

⚠️ **没有做人工浏览器验证** —— 与 M1 收口时不同,本轮的页面行为全部由组件测试钉住,
**没有在真实浏览器里点过一遍**(尤其没有连着真后端跑)。
这与 §29 的阶段性收缩**无关**,是本轮自己的欠账,记在 §44.9-1。

### 44.9 仍未做(不许读成「已绑住」)

1. **没有真人在浏览器里点过这两个页面**(§44.8)——
   组件测试证明的是"逻辑对",不证明"连着真后端跑得通"(Vite 代理 / Cookie / 真实数据)。
2. **F-1.10 分享页仍然没有** —— §43.12-1 那条"用户点不到分享"**依然成立**,
   本轮**明确不接**(用户拍板)。方案详情页里**没有**分享按钮。
3. **「重新编辑」未做** —— 生成页不回填草稿;用户想改一改再出图,只能重新走一遍。
4. **改名上限的契约缺口未修**(§44.3.3)—— 已登记(一条会红的断言),**未修**。
5. **`materialFee` 的必填不一致仍在**(§41.6 / §42.7 / §44.3.1)——
   前端按**可选**消费,分歧**未被消除**,只是被诚实地显示成了 `—`。
6. **列表页没有搜索 / 筛选 / 排序** —— 契约 `/plans` 只有 `page` / `size` 两个参数,
   前端不多传(CLAUDE.md:契约优先),所以这不是遗漏,是契约如此。
   方案多了之后要筛选,得**先改契约**。
7. **`SHARE_BASE_URL` 那一类"上线前必须改"的配置**(§43.12-7)与本轮无关,仍未处理。

---

## 45. F-1.10 分享页:公开只读页 + 详情页分享入口(2026-10-01)

**一句话**:§43 把分享做成了"后端的三个接口,用户点不到";这一节把它变成
"**用户能点、别人能开**"。做完之后一期 C 端该有的页面齐了(M2 闭合),
**唯一还在等的是 F-1.6(AI 咨询),它等的是后端 T-1.6**。

同 §43:**不写一行 Java、不动一个字契约** —— 三条接口、`ShareResult`、
公开页的 `security: []` 早在 `openapi-c.yaml` 里。这一轮是把**契约兑现成界面**。

### 45.1 用户拍板的两条(2026-10-01)

| # | 决定 | 直接后果 |
| --- | --- | --- |
| ① | **分享页独立成页,渲染代码各写一份** | 不新建 `src/components/`。代价:部位表 / 报价表 / 视角名 / 字典降级这几块**与详情页重复约 150 行** —— 这是**刻意的**,见 §45.2 |
| ② | **这一轮不画二维码** | 画它要引入**本项目第一个第三方 npm 依赖**(前端至今零依赖:手写 fetch、没有 axios、没有 UI 库)。「二维码由前端画」这个决定(§43.1-①)说的是**由谁画**,不是**这轮就画** —— 所以它不因此失效,只是留到以后 |

②还顺带挡住了一个诱惑:后端 `ShareResult.qrCodeUrl` 恒为空(§43.2),
"既然空着不如前端顺手填一个" —— 填了就变成前端在**伪造一个后端字段**,
而那个字段的语义上游从未定义过(`x-todo`)。**留空才是对的。**

### 45.2 公开页的形态:不套布局、不碰登录态

```
/share/:token   →  views/site/ShareView.vue    顶层路由,meta.public
/plans/:id      →  PlanDetailView.vue 的「分享」区  生成 / 复制 / 废除
```

**为什么是顶层路由、不挂在 `SiteLayout` 下**:看这一页的人(**家人 / 设计师 /
门店店员**)没有账号。挂进去就会给他们看一排「我的方案」「生成」的导航,
而点进去只会被守卫弹到登录页 —— **他们没有登录页可去**。

`meta.public`(而不是"恰好没写 `requiresAuth`")是**显式声明**,与 `/admin/login`
同一口径:守卫里第一句 `if (to.meta.public) return true` 直接放行,
不碰 `useUserAuthStore`、不碰 `/auth/me`。**匿名访客不该在打开一页时先被问一次会话。**

**四条必须守住的边界**(都是"改错了不报错,只悄悄出错"):

1. **一个管理动作都不能有** —— 没有改名 / 另存为 / 删除,也**没有重算**。
   重算要登录,而这里根本没有"谁"在操作。有一条用例直接断言**整页 0 个按钮**,
   并逐个断言正文里不出现「改名 / 另存为 / 删除 / 重算 / 保存」——
   按钮没了但文字还在的"半个入口"同样是误导。
   ⚠️ **2026-10-03 口径更新**:多角度缩略图改成可点之后,这一页**合法地**有了按钮
   (`正面` / `角度 N`)。断言由"0 个按钮"改成**允许清单**(只允许切换视角的按钮),
   约束本身不变。见 **§59.3**。
2. **取不到一律按后端那句 30001 文案显示,不猜。** 后端把
   「令牌不存在 / 已废除 / 方案已删除」**四合一**(§43.4),防的就是
   **一个能枚举令牌的探针**。前端若自作聪明地写"链接已失效,请联系分享者",
   就等于把这个探针又造了一遍 —— 用户看不出差别,但**错误文案本身就是信息**。
   所以照抄后端那句,只在**旁边**补一句"该找谁"(那句话不区分原因,只说明下一步)。
3. `materialFee` 缺失显示 `—`、`quote` 缺失显示「这份方案没有报价」、
   整页文本不许出现 `NaN` —— 与详情页**同一口径**(§44.3)。
   这条在这一页尤其重要:看的人**没有上下文**,一个 `¥NaN` 比在详情页里更伤。
4. **`quoteExpired` 仍然要提示**,但**去掉那句"点「按当前价重算」"** ——
   那是个他按不了的按钮。留一句"这是生成当时的价"就够,那是事实。

**关于那 150 行重复**:项目没有 `src/components/`,抽一层就等于定下
"以后所有页面都该考虑这个",而那是一次**架构决策**,不该顺手塞进一张分享页的卡里。
更重要的是这两页**本来就该长得不一样**(公开页有"只读"说明、没有管理区、
错误态不同、`quoteExpired` 的文案都不一样)—— 真正会分叉的是**取值口径**
(`?? '—'`、`?? '报价'` 这些),而那些**已经被两边的测试各钉了一遍**。

### 45.3 ⚠️ 三处形状必须一致的路径(不一致的后果是**静默回首页**)

同一条分享链接上有三个独立的"形状":

| 在哪 | 是什么 | 谁定义 |
| --- | --- | --- |
| 后端 | `SHARE_PATH = "/share/"`,链接 = `baseUrl + SHARE_PATH + token` | `PlanShareService` |
| 前端 | 路由 `/share/:token` | `src/router/index.ts` |
| 部署 | `tile.share.base-url`(默认 `http://localhost:5173`) | `application.yml` |

**任意一处不一致,后端发出去的链接在浏览器里都会落到最后那条兜底路由
`/:pathMatch(.*)*` → 静默重定向回首页。** 没有 404、没有报错、没有日志 ——
用户只会说"你这链接打开是你们首页"。

能自动化的部分已经钉了:`tests/contract.spec.ts` 里有一条断言
**契约的路径就是 `/share/{token}`、参数名就是 `token`** —— 契约是这三者里
唯一能被测试读到的那个,所以拿它当锚点。剩下两处(前端路由、部署域名)靠人,
已写进路线图 §6 的「步骤 7d」:**退出登录,用生成的链接打开一次**。

### 45.4 新增的契约断言(`tests/contract.spec.ts`,**+65/−0 纯追加**)

| 断言 | 为什么 |
| --- | --- |
| `POST` / `DELETE /plans/{id}/share` 的 `security` 都是 `[{ cookieAuth: [] }]` | 掉一条 = 任何人凭 id 就能把**别人的**方案分享出去,或把别人已发出的链接废掉 |
| **`GET /share/{token}` 的 `security` 是 `[]`(空数组)** | 这是全项目唯一匿名可达的读接口。⚠️ 必须区分"**空数组**"与"**没写**" —— 后者在 OpenAPI 里会**继承全局 `security`**,语义完全相反 |
| 公开页路径 = `/share/{token}`、path 参数名 = `token` | §45.3 那个锚点 |
| **`ShareResult.required` 是 `undefined`**(且字段恰好是这三个) | 登记 §43.2 那处不一致面:契约三字段全可选、实现里 `qrCodeUrl` 恒不出现。前端据此**三个都写成可选**并在渲染前逐个判 |

### 45.5 `shareUrl` 用服务端的,`location.origin` 只是兜底

详情页要显示一条链接,但**只有两条来源**:

- **刚点过「生成分享链接」** ⇒ 后端回体里有 `shareUrl`,**用它**(它是权威值);
- **刷新页面后 / 之前就分享过** ⇒ `PlanDetail` 里**只有 `shareToken`**,没有链接
  (链接是 `POST` 现拼的)⇒ 只能拿 `location.origin + "/share/" + token` 现拼。

同源部署下这两者**应当**相等(前端的源就是后端配的那个 base URL),但**不等时
以服务端为准** —— 因为那条链接是**发给别人**的,而后端那个配置项
(`tile.share.base-url`,默认 `http://localhost:5173`)**上线前必须改**
(§43.12-7)。它配错了不会报错,只会**静默产出一条指向 localhost 的链接**,
而分享者自己在浏览器里试还试不出来。

前端的 `location.origin` 兜底**不是**这个问题的解 —— 只是"刷新后至少还能给出
一条能用的链接"的补救。**权威值仍然是后端那个配置。**

### 45.6 一个前端侧的发现:Vue 的 `:value` 绑定设的是 **DOM 属性**,不是 HTML 属性

写测试时踩到的:分享链接显示在一个 `<input readonly :value="shareLink">` 里,
最初用 `wrapper.find('.share-link').attributes('value')` 去读 —— 拿到的是
**`undefined`**。

原因:Vue 对 `<input>` 上的 `value` 绑定走的是 `el.value = …`(**设属性/property**),
不是 `setAttribute`。`attributes()` 读的是**属性节点**,所以读不到。

⇒ 断言要读 DOM 属性:`(wrapper.find('.share-link').element as HTMLInputElement).value`。
测试里专门抽了一个 `shareLinkValue(wrapper)` 帮手并在注释里写明这一点 ——
**这个坑的坏处不是"读不到"(那会立刻红),而是**万一以后有人改成
`attributes('value')` 去断言一个**空字符串**是否包含某段文本,就会变成一条
**永远为假、却看起来在测东西**的用例。

### 45.7 `Referrer-Policy`:分享页 URL 里带着令牌

后端 `CShareController` 的类注释里留了一句:「分享页自己的 `Referrer-Policy`
该由前端的那个页面来设 —— 那是 F-1.10 的事」。这一轮把它办了。

**问题**:分享链接是 `/share/{token}`,**令牌在 URL 路径里**,而令牌是"不登录
就能看到别人一整份方案"的**全部凭据**。分享页又要加载**跨域**的效果图
(对象存储),浏览器默认会在图片请求上带 `Referer`。

**实情要说准**:现代浏览器的默认策略是 `strict-origin-when-cross-origin`,
**跨域只发源、不发路径** —— 所以**今天跨域图片那条路上令牌其实没漏**。

**但仍然写死 `no-referrer`**,理由是这条依赖太隐晦:哪天图片改成同源代理、
或有人把策略调宽,令牌就会**静默**出现在第三方的访问日志里,**没有任何测试会红**。

**放在 `index.html`(全局)而不是在分享页动态插**,是刻意的选择:

- 动态插 meta 要**赶在子资源请求之前**,还得在**离开路由时清掉** ——
  漏了任意一步,就是"某个页面的策略悄悄不对",而且同样不会有测试红;
- 而本站**没有任何依赖 Referer 的东西**(API 同源,图片是公开读地址),
  全站关掉**没有代价**。

**登记一处**:**分享页没有 `noindex`。** 如果拿到链接的人把链接发到公开的地方,
理论上可能被搜索引擎收录(SPA 的未链接路由,现实概率低,但**没有任何东西挡着**)。
没做是因为加 `noindex` 要么全站生效(会伤正常页面的 SEO)、要么同样要动态插 head
(与上面同一个坑),而这一轮不值得为它引入 head 管理。**未解决项。**

### 45.8 校验结果(2026-10-01)

| 项 | 结果 |
| --- | --- |
| 前端全量 | **206 passed / 16 文件**(基线 **180 / 15**,`f72b235` 实测) |
| 新增 | `tests/share.spec.ts` 13 条、`tests/planDetail.spec.ts` +9 条、`contract.spec.ts` +4 条 |
| 类型与构建 | `npm run build`(= `vue-tsc --noEmit && vite build`)**通过**;产物里有独立的 `ShareView-*.js` chunk(路由确实是懒加载的) |
| 后端 | 未跑(未改动;本轮不涉及 `ci.sh`,连接触契约的两个文件都没碰) |

改动面(`git status` / `git diff --numstat`):

```
 M tile-ai-web/index.html                        +18 /  −0   Referrer-Policy
 M tile-ai-web/src/api/c/plans.ts                +77 /  −2   加三条接口 + ShareResult;−2 是文件头一段改写
 M tile-ai-web/src/router/index.ts               +18 /  −0   顶层 /share/:token
 M tile-ai-web/src/views/site/PlanDetailView.vue +148 / −1   加「分享」区;−1 是被替换的 import 行
 M tile-ai-web/tests/contract.spec.ts            +65 /  −0   纯追加
 M tile-ai-web/tests/planDetail.spec.ts          +142 / −0   纯追加
?? tile-ai-web/src/views/site/ShareView.vue      (新)
?? tile-ai-web/tests/share.spec.ts               (新)
```

**既有 15 个 spec、180 条零改动** —— 被动到的两个既有 spec
(`contract.spec.ts` / `planDetail.spec.ts`)**都是纯追加**(−0)。
**`tile-ai-server/`、四份 OpenAPI、AsyncAPI、PRD:`git status` 里一个字没有。**

---

## 46. T-1.6 AI 咨询:双端落地、SSE 事件集与三处设计取定(2026-10-02)

M3 第一张卡。**双端从零**(Java 三条 C 端接口 + Python 的 `/internal/agent/chat`
此前都只是 `knownGaps`),契约 YAML **一个字没动**。

### 46.1 交付面

| 侧 | 新增 |
| --- | --- |
| Python | `app/chat/{sse,scope,session_store,rag,pipeline}.py`、`app/core/clients.py`;`internal.py` 加 `POST /internal/agent/chat`(SSE) |
| Java | `com.tileai.agent`(实体×2 / Mapper×2 / DTO×5 / `ChatSessionService` / `AgentChatOrchestrator` / `AgentChatController` / `AgentChatConfig`)、`com.tileai.common.agent` 的 `ChatEvent` / `SseEventParser` / `AgentChatStreamClient` / `HttpAgentChatStreamClient`;`AgentProperties.chatTimeout`、`application.yml`、`WebMvcConfig.configureAsyncSupport` |

**不需要新迁移**:`V6` 的 `t_chat_session` / `t_chat_message` 逐列覆盖契约字段
(唯一缺"按最近活动置顶",契约未要求,本期接受)。

### 46.2 SSE 事件集:**没有门禁能拦,测试是唯一判据**

G1–G7 只校验契约自身;契约比对明确**不比 SSE 事件集**
(§40.4 表末行;§36.5 第 4 条也早写了"`/internal/agent/chat` 的价值几乎全在事件集上,
那个端点在本骨架里等于没绑")。本轮实测印证:两份清单里 `POST /api/v1/agent/chat`
与 `POST /internal/agent/chat` 的响应都落进**「无法比对」**一栏。

⇒ 契约里那几条硬要求(D-7 的帧顺序、`error` 与 `[DONE]` 互斥、refs 至多一帧)
**只能由本轮新增的测试钉住**:Python `tests/test_chat_stream.py`(帧序列、50001、
refs 真实性、降级)、Java `AgentChatIntegrationTest`(代理后的帧序列、落库、
中途失败不落 assistant)、`SseEventParserTest`(解析规则)。
**这是一条结构性缺口,不是本轮遗漏** —— 记录的用意是下次改事件集时知道该改哪里。

### 46.3 Java 侧第一个 SSE:为什么是 `HttpClient` + `SseEmitter`

- **不引 WebFlux**。本项目是 Spring MVC(servlet)栈,为了一个流式客户端把整套
  WebFlux 拖进来,会让"到底哪套在跑"变得含糊。JDK 自带的
  `java.net.http.HttpClient`(`BodyHandlers.ofLines()`)按行产出,**零新依赖**,
  天然就是 SSE 的消费形态。
- **现有 `AgentClient` 不能复用**:它是同步 `RestClient`,会把整段响应缓冲完再返回
  —— 那不是流式,是"憋完再说"。故新增 `AgentChatStreamClient` 作为**可打桩的接缝**。
- **`WebMvcConfig` 必须加 `configureAsyncSupport`**。原先没有 —— Servlet 异步默认超时
  (Tomcat 约 30s)会把长回答**静默截断**。这是"不配就必坏"的一项,已与
  `SseEmitter` 共用 `tile.agent.chat-timeout`。
- **鉴权/追踪的 ThreadLocal 不跨线程**:`SecurityContextHolder` 与 `TraceContext`
  都是 ThreadLocal,所以在请求线程取好 `userId` / `traceId` 再作参数传给编排线程,
  编排器内不得再调 `CurrentPrincipal`。

### 46.4 会话权威在 Java,sessionId 由 Java 下发

契约 `ChatSession.id` 是 **string**,而 `t_chat_session.id` 是自增 `BIGINT` ——
本轮取"`String.valueOf(id)`"这个映射(与 `/plans/{id}` 暴露数字 id 的既有做法一致),
于是 **Java 是会话权威**:新建时先落 `t_chat_session`,把该字符串既作 C 端 id、
又作传给 Python 的 `sessionId`;**首帧 `sessionId` 由 Java 下发**。

因此 **Python 不发 sessionId 帧**(内部契约正文的正常序列本就是
`delta → refs → [DONE]`);Java 若收到 `{"sessionId":…}` **一律吞掉**,
保证 C 端"恰好一帧"(有专门用例钉住)。

**归属判定刻意不区分**"不存在 / 不属于本人 / 不是数字",三者一律 30001 且同一措辞
—— 分开就变成一个可枚举他人会话 id 的探针(与 `PlanOwnership` 同一口径)。

### 46.5 refs 为什么不交给模型

硬约束是"**不得编造 SKU**"。若 refs 由模型输出,**无法证明**它没编造。
所以 refs 走确定性代码:检索候选逐个回问 `JavaApiClient.get_product`(即
`/internal/products/{sku}`),**取不到即丢弃**,`name` 取 Java 权威值。
测试里有两条反例:只查得到一个就只给一个;一个都查不到就给**空数组**而不是凑一个。

### 46.6 问价拦截:两道闸门,顺序即分工

1. **确定性关键词先跑**(`app/chat/scope.py`):命中即 50001,**不调模型**。
   硬约束要求"问价 → 50001"必须**可靠**,而模型是概率性的 —— 关键词是保底。
   关键词只认"价/钱/费/贵/便宜/优惠"这类**词**,不认素面数字,所以
   `750×1500` 这类**尺寸**不会被误拦(有用例)。
2. **模型意图分类后跑**:补隐晦越界;**分类器故障/超时一律放行**(宁可漏拒一句,
   也不该因分类器连不上把正常咨询全打成错误)。

另外**上下文块逐字段白名单拼接**,`price` 字段绝不进入(有用例断言拼出的文本
里既没有价格数字也没有 `price` 字样)。

⚠️ **残余风险如实登记**:模型仍可能在正文里吐出一个数字,而流已开始无法撤回。

### 46.7 挂账清理是本任务的**隐性必做**

`/agent/chat`、两条 sessions、`/internal/agent/chat` 四条 `knownGaps` 一实现就会命中
⇒ 触发"**挂账已过期**"**无条件判红**。本轮已删(Java 3 条、Python 1 条),
并把 `POST /api/v1/agent/chat`、`GET /api/v1/agent/chat/sessions`、
`POST /internal/agent/chat` 加进两侧 `canaries`(锚点)。

### 46.8 与既定倾向的两处**推翻**(开工时复核的结果)

1. **`run_in_threadpool` 逐帧转 SSE → 否决**。httpx 的流式迭代器必须**在同一线程**
   创建与消费,逐帧往线程池丢会在 worker 之间飘;且每 token 一次线程池往返。
   改为"**单线程 producer + `asyncio.Queue` 桥接**"。
2. **网关接进 `lifespan` → 改为惰性单例**。网关内含熔断器/失败率/连接池,必须有
   跨请求生命周期(否则熔断永远打不开);但**不硬启动** —— 配置缺失时 `/health` 仍要活,
   与 `_start_generate_consumer` 同口径。

顺带:**§23.7 的复核结论 = 保持同步网关**,只把它放进专用后台线程,不 async 化。

### 46.9 校验结果(2026-10-02)

| 项 | 结果 |
| --- | --- |
| Python 全量 | **237 passed / 11 skipped**(基线 **175 / 11**) |
| Python 新增 | `test_chat_stream.py` 15、`test_chat_scope.py` 22、`test_chat_session_store.py` 8、`test_chat_rag.py` 13、`test_clients.py` 2(死锁回归);`test_gateway.py` +1(推理内容不外泄) |
| Java 全量 | **501 passed / 0 skipped**(基线 **481**) |
| Java 新增 | `AgentChatIntegrationTest` 12、`SseEventParserTest` 8 |
| 契约 ↔ 实现比对 | **两侧干净**;四条挂账已清、无"挂账已过期" |
| 契约门禁 | **7/7 PASS**(`contract-diff.sh` 先跑,再 `ci.sh`) |
| 契约 YAML | **一个字没动** |

### 46.10 未闭合(逐条标它挡不挡下一步)

1. **检索质量无意义** —— embedding 仍是确定性占位(sha256,无语义)。⇒ 本轮只能声明
   "管路通、帧与落库正确、refs 有真实性保证",**不能声称"推荐准确/搭配合理"**。
   要谈质量须先定 Embedding 模型并全量重建索引(P0-2)。**不挡**继续开发。
2. **未做真实跨进程联调** —— 两侧各自验证(Java 侧把 `AgentChatStreamClient` 打桩),
   **尚未在一个进程里跑通"Java → Python → 真模型"**。**挡**:它是下一步第一件事。
3. **refs 的 `part` 是启发式**(payload 无该字段,按问句关键词猜,推不出默认 `wall`)。
   猜错会误导"一键采纳"。**待产品决策**。
4. **会话列表不按最近活动置顶**(表无 `updated_at`)。要改需加列(V12)。契约未要求。
5. **思考模式**:价目表显示 `deepseek-flash` 默认开思考模式。网关只取 `delta.content`,
   **推理内容不会漏给用户**(离线用例钉住),代价是更慢更贵;**未做**真实开关对比。
6. **单价表订正**:`.env.example` 的 `GATEWAY_MODEL_PRICES` 由"输入 1 / 输出 2"
   改为**输入 2 / 输出 8(取高峰档)**,并注明 `CostMeter` 目前**不分峰谷、不分缓存命中**,
   故是**偏高估计**。精确记账需要给 `CostMeter` 加分档能力(**尚未做**)。

### 46.11 「模型答不上来」的兜底:不发明错误码(2026-10-02 追加)

**问题**:核对实现时发现一个空洞 —— 若模型**一个字都没吐**(空回答),
原实现会照常发 `refs` → `[DONE]`,`t_chat_message` 里那条 assistant 正文是**空的**,
前端于是显示**一段空白**:既没有错误码,也没有任何提示。

**为什么不给它一个码**。这一档与已有两档都不同:

| 情形 | 码 | 对用户的含义 |
| --- | --- | --- |
| 问价 / 施工售后 / 效果图(越界) | **50001** | "这问题**不归我管**" —— 该换问法 |
| 模型超时 / 连不上 / 熔断(故障) | **90001** | "**我这边坏了**" —— 该重试 |
| 模型没答上来但服务正常 | **本轮新增第三档** | "我问得不清楚" —— 该说得更具体 |

用 90001 会让用户白等一次重试;用 50001 等于骗他说"这问题不归我管"。
而"服务正常、只是内容为空"**并不是错误** —— 这次问答在协议上是**成功**的。

⇒ **不新增错误码**,而是把它当**正文**处理:Python 侧在生成结束后判空,
若为空则补一句固定文案,**作为一帧 `delta` 发出**,再照常 `refs` → `[DONE]`。

**为什么必须发成 `delta` 而不是只在内部替换**:Java 靠**累积 delta** 决定落库正文,
前端靠 **delta** 渲染,Redis 上下文也存这份文本 —— 只在内部替换会让
"用户看到的 / 库里存的 / 下一轮模型记得的"三者对不上,前端依旧空白。

**契约零改动**,这是刻意的:事件集、字段、错误码一个都没变,只是"同样的帧里写什么"。
若改成新增码,则要走 PRD → 架构 → 契约 YAML → 本记录四层链条,还要过门禁、
前端多一个分支 —— 把"内容问题"升级成"协议变更",成本不对等。

**测试**:`tests/test_chat_stream.py::test_empty_model_answer_gets_a_fallback`
(断言有一帧非空 `delta`、以 `[DONE]` 收尾、**不出现 error 帧**、且写进了上下文)。
**文档**:同步在任务卡 T-1.6 的进度块里记了一笔。

---

## 47. T-1.6 真实跨进程冒烟:三个只有真环境才暴露的缺陷(2026-10-02)

第一次把 Java 与 Python **同时真起起来**、经浏览器同款的 HTTP 路径跑一问一答。
链路是通的(见 §47.4),但过程中抓出**三个缺陷 + 一处既存数据问题** ——
**三个都不在两侧的单元/集成测试覆盖范围内**,这正是本次冒烟的价值。

> 一句话总结:**MockMvc 不跑真 HTTP,也不跑 Servlet 的异步回派** ——
> 凡是"真连接 / 真线程 / 真容器行为"上的错,只有真环境能看见。

### 47.1 缺陷一:Java 的 HTTP 客户端默认要用 HTTP/2,Python 不认

- **现象**:对话永远失败;Java 日志 `调用智能体对话接口返回非 200 status=400`,
  Python 日志 `Unsupported upgrade request` / `Invalid HTTP request received`。
- **根因**:`java.net.http.HttpClient` 默认版本是 HTTP/2。在**明文 http://** 上,
  它会先发一个 `Upgrade: h2c` 的升级请求;uvicorn(FastAPI 默认服务器)不支持 h2c,
  直接回 **400**。
- **修法**:构造客户端时显式 `.version(HttpClient.Version.HTTP_1_1)`。
- **测试为什么没抓到**:Java 侧集成测试把 `AgentChatStreamClient` 整个换成了替身
  (本来就该如此 —— 测试不该依赖真 Python),所以**从来没有真发过一次 HTTP**。

### 47.2 缺陷二:Spring Security 在 ASYNC 回派上二次鉴权,把 SSE 收尾拦掉了

- **现象**:流结束后 Java 抛 `AuthorizationDeniedException: Access Denied`,
  并伴随 `Unable to handle the Spring Security Exception because the response is already committed`。
- **根因**:`SseEmitter` 结束时 Servlet 会做一次 **ASYNC 回派**,再走一遍过滤链。而
  - 自定义的 `JwtAuthenticationFilter` 继承 `OncePerRequestFilter`,它**默认跳过 ASYNC 回派**
    → 回派时无人认证;
  - 但 Spring Security 6 的鉴权过滤器**默认会在 ASYNC 回派上再判一次** → 拒。
  两边默认值正好相反,合起来就是一个必炸的组合。
- **修法**:`authorizeHttpRequests(...).shouldFilterAllDispatcherTypes(false)` ——
  只对 REQUEST / FORWARD 判权。ASYNC 回派不会重新执行 controller,**不放宽任何权限**。
- **测试为什么没抓到**:MockMvc 的异步测试走的是另一套回派路径,没有真的经过容器。
  (换句话说:**这是本项目引入 SSE 才会有的坑**,而 T-1.6 正是第一个。)

### 47.3 缺陷三:Python 共享客户端的锁不可重入,把请求静默锁死

- **现象**:整个对话请求**永久挂起**,**日志里一个字都没有**;curl 直打 Python 也超时。
- **根因**:`app/core/clients.py` 里 `get_llm_gateway()` **持锁**期间调用
  `get_redis_client()`,而后者要拿**同一把** `threading.Lock` —— 自己把自己锁死。
- **修法**:改用 `threading.RLock()`,并把取 redis 的动作挪到**进锁之前**(两道保险)。
- **测试为什么没抓到**:单元测试把 `_chat_deps` 整段换成了替身,`clients` 这条路径
  **从未被真正走过**。已补 `tests/test_clients.py`:用"另起线程 + `join(timeout)`"
  把死锁变成一次**失败**而不是一次静默挂起(方法本身值得复用)。

### 47.4 既存数据问题:演示商品的中文是**双重编码**(不在 T-1.6 范围)

- **现象**:`refs` 里的商品名是乱码(`éç°ç³çº¹`),而模型生成的正文中文正常。
- **定位**:直接查库,`hex(name) = C3A9C29B...` —— **库里存的就已经是乱的**。
  库的字符集变量全是 `utf8mb4`,所以**不是 MySQL 配置问题,是当初灌数据那一步的
  客户端编码错了**(UTF-8 字节被当拉丁文解码后又存成 UTF-8)。
- **影响面**:不止 AI 咨询 —— **公开商品接口 `/api/v1/products` 返回的同样是乱码**,
  也就是说这个库里的中文商品文案**整体是坏的**。Java/Python 两侧都是忠实读、忠实返。
- **为什么现在才发现**:`M2` 的人工验收关注的是"链路通不通",没逐字核对商品名;
  而 AI 咨询把**商品名直接摆到用户眼前**(`refs` 要"一键采纳"),才让它刺眼。
- **处置**:**未修**。这是**数据**问题(演示数据从哪来、怎么修,属用户决定),
  不擅自动库。修法有两条:重新按正确编码灌一遍,或就地转换
  (`CONVERT(BINARY(CONVERT(col USING latin1)) USING utf8mb4)` —— 需先确认**哪些行是坏的**)。

### 47.5 冒烟通过的部分

| 项 | 结果 |
| --- | --- |
| 向量库 | 全量重建:**产品 12 + 搭配 3** 条入 `product_index` / `combo_index` |
| 首帧 | `data:{"sessionId":"23"}` —— **仅一次**,由 Java 下发 |
| 流式 | 150+ 帧 `delta`,**中文正确**(模型真在逐字产出) |
| refs | 一帧 `refs`,内含 3 个**经权威接口校验过**的 SKU |
| 收尾 | `data:[DONE]` 为最后一帧 |
| **问价拦截** | 「这款砖多少钱一平」→ **单帧 `error 50001`**,无 `delta`、无 `[DONE]` |
| 真实模型 | DeepSeek `deepseek-flash` 联调成功(非流式 1.6s、流式 1.6s / 194 帧) |

### 47.6 顺带记两条

1. **`Settings(env_file=".env")` 是相对当前目录的**。从仓库根目录启动 Python,
   读不到 `tile-ai-agent/.env` ⇒ 模型配置**全部为空**,对话会以 90001 失败,
   而报错完全不提"配置没加载"。启动目录不能错(排查时踩到,已记)。
2. **响应头 `Content-Type: text/event-stream` 未带 `charset`**。浏览器 `EventSource`
   强制按 UTF-8 解码,所以**前端不受影响**;但其它客户端(如 .NET 的
   `HttpWebRequest`)会按拉丁文解出乱码 —— 排查时被它误导过一次。是否补
   `;charset=utf-8` 属实现细节,未改。
3. **跑 Java 全量测试时,必须先把本地的 Python 服务停掉。** Python 的生成消费端
   会**抢走** `queue.generate` 的消息,`GenerateIntegrationTest` 于是等不到自己发的那条,
   两条用例各自**卡满 30 秒超时**后失败 —— 看起来像"代码坏了",其实是被抢了消息。
   (实测踩到一次:501 → 2 failed;停掉 Python 再跑即 501 passed。)反过来,
   `queue.index.sync` 积压着 60+ 条也没有消费者 —— 那是**刻意**的(§35.11),
   所以**索引只能靠 `tools/rebuild_index.py` 手动重建**,增量同步当前不生效。

---

## 48. F-1.6 AI 咨询页:前端落地与三处取舍(2026-10-02)

C 端一期**最后一个页面**。**纯前端**:契约 YAML 一个字没动,后端一行没改。

### 48.1 交付面

| 动作 | 文件 |
| --- | --- |
| 流式客户端(新) | `src/api/c/chat.ts`(`streamChat` 异步生成器 + 两条会话接口) |
| 帧解析(新) | `src/api/sse.ts`(纯函数,单独可测"半帧") |
| 页面(新) | `src/views/site/ConsultView.vue` |
| 富文本渲染(新) | `src/utils/richText.ts`(全项目**唯一**一处 `v-html`,见 §48.8) |
| 类型与契约常量(追加) | `src/api/types.ts`(`SkuRef` / `ChatSession` / `ChatMessage` + 四个常量) |
| 复用全站刷新(纯追加导出) | `src/api/http.ts` 导出 `tryRefreshSession` |
| 三处入口 | `router/index.ts`(`consult`,**`requiresAuth`**)、`SiteLayout.vue`(导航)、`HomeView.vue`(路径 C 卡片由「开发中」转「可用」) |

### 48.2 为什么不能用 `EventSource`

契约里 `/agent/chat` 是 **POST + JSON body + Cookie 鉴权**,而 `EventSource`
**只支持 GET、不能带请求体**。⇒ 只能 `fetch` + `response.body.getReader()` +
`TextDecoder` **手工切帧**。

两处必须做对:
1. **跨块半帧要缓冲** —— 网络块边界与帧边界无关,最后一段不完整的留在 buffer 里;
2. **多字节字符也会被劈开** —— 一个汉字占 3 个 UTF-8 字节,`decode(value, {stream:true})`
   才能接住。测试里有一条**故意把汉字从字节中间切开**的用例钉住它。

额外收益:自持 `TextDecoder('utf-8')`,不受 §47.3「响应头无 charset」影响。

### 48.3 两类"没答成"必须分开(本页最容易被改坏的一条)

| 情形 | 来源 | 页面表现 |
| --- | --- | --- |
| **拒答**(问价 → 50001) | 后端的 `error` 帧 | 照抄后端 `message`,**提示态**;**不报"失去联系"、不给重试** |
| **连接断了** | fetch 失败 / 流在终态前结束 / 校验不过 | 才显示「与服务器失去联系,请重试」,半截文本标「(不完整)」 |

理由:重试同一个问题不会有不同的答案;把"这事我不答"显示成"服务器坏了",
会让用户白重试,也会让运维被无谓地叫起来。契约里这两种结局**互斥**
(失败帧之后不会再有 `[DONE]`),而 `streamChat` 把"静默断流"也变成了**可判定**的中断。

### 48.4 一键采纳:**采纳 ≠ 跳走**,且**绝不 `clear()`**

- 采纳 = `selectionDraft.setPart(part, sku, {name?})` —— **同部位替换、其余保留**(合并);
- **不调用 `draft.clear()`**:那会把用户已录的**户型**一起清掉。有专门用例钉住
  (预置户型 + 地面砖,采纳墙面砖后断言**两者都还在**);
- **不在采纳时自动跳转** —— refs 常有 2~3 条,每点一下就弹走没法核对。
  跳转做成页面级按钮「带着这些砖去录户型」(`router.push({name:'layout'})`,不带参数,
  数据都在 store 里)。PRD 说的"点击搭配方案可直接跳到户型录入页"由它满足。

### 48.5 演示库商品名乱码:**前端不兜、不修**

`refs` 卡片要显示 `name`,而演示库里 `name` 是**双重编码的乱码**(§47.4,**数据问题**)。
前端**不做任何"修复"** —— 修了就会与产品库页显示不一致,而且是猜测。
做法是**始终把 `sku` 显出来当主标识**,`name` 有就显示、缺失或乱码也不留空白
(`SkuRef.name` 在契约里本就是**可选**)。观感问题源自数据,不源自本页。

⚠️ 另登记一处**消不掉**的既存缺口:`refs[].part` 是后端按问句关键词**猜的**(§46.10.3),
猜错会把砖铺到错的部位,而采纳是**替换**语义、会静默覆盖。前端能做的只有
**把部位写在卡片上**、采纳后立刻更新摘要、**不自动跳走**,给用户核对的机会。

### 48.6 校验结果(2026-10-02)

| 项 | 结果 |
| --- | --- |
| 前端全量 | **264 passed / 20 文件**(基线 **206 / 16**) |
| 新增 spec | `sse.spec.ts` 9、`chat.spec.ts` 14、`consult.spec.ts` 20、`richText.spec.ts` 11;`contract.spec.ts` **纯追加 4** |
| 类型与构建 | `npm run build`(`vue-tsc --noEmit && vite build`)**通过**;产物里有独立的 `ConsultView-*.js` chunk(路由确实懒加载) |
| 契约 / 后端 | **一个字没动**(前端改动不触及契约) |
| **真机冒烟(经 Vite 代理)** | 登录 → 提问:**332 帧**,以 `refs` + `[DONE]` 收尾 |
| **代理是否缓冲** | **实测不缓冲**:响应头 16ms、**第一个数据块 23ms**、整条流 28.6s 读完(867 次读)⇒ 开发环境**逐字出字成立** |

⚠️ **生产 Nginx 必须对 `/api/v1/agent/chat` 关 `proxy_buffering`** —— 否则逐字会变成
"憋完再说"。那是 T-2.4 部署的事,前端修不了,在此登记。

### 48.7 回答里的 Markdown 标记:渲染,但**只认三条规则**

**问题**:模型是按 Markdown 回的(`**首选墙砖:**云灰大理石`、行首 `- ` 的列表),
而页面按纯文本渲染 ⇒ 那些 `**` **原样显示**出来,很难看。

**做法**(用户 2026-10-02 拍板:在前端渲染):新增 `src/utils/richText.ts`,
把 `**粗体**` → `<strong>`、`` `代码` `` → `<code>`、行首 `- `/`* ` → `• `,
其余 Markdown(标题、表格、链接……)**一律按纯文本显示**。
**每多支持一条规则,就多一处可以被模型内容穿透的缝。**

**这是全项目唯一一处 `v-html`,所以顺序不可颠倒**:内容来自**大模型**,是不可信输入,
完全可能吐出 `<script>` 或 `<img onerror=…>`。因此**先整体转义、再插入自己造的标签** ——
进入 `v-html` 的字符串里,**任何 HTML 都不是原文带来的**。有专门的用例钉住
(`<script>` 与 `**<img onerror=…>**` 都不得变成元素)。

两个实现细节,都属于"错了不报错、只是不对"的那类:
1. **列表用 `•` 字符而不是 `<ul><li>`** —— 气泡是 `white-space: pre-wrap` 逐行显示的,
   插块级元素会在列表前后多出空行,反而更乱;
2. **样式必须写 `:deep()`** —— `v-html` 注入的元素**拿不到 scoped 属性**,
   直接写 `.text strong` 不匹配,而且不会报错。

### 48.8 浏览器里的人工验收(**2026-10-02 通过**,用户执行)

用户按 §48.6 列的四点走了一遍:**逐字出字**、问价显示 **50001 的后端文案**、
采纳后**摘要更新**、点跳转**进到「户型录入」页** —— 全部正常。
⇒ **F-1.6 到此是人验过的,不再只是"组件测试 + 构建 + 经代理的 HTTP 链路"。**

⚠️ 签的是这一页**跑通了、看着也顺**;**不代表**"推荐准不准" —— 那取决于 embedding
(仍是占位,§46.10),与本页无关。

另注:`sessionId` 是**内存态**,刷新即丢(与 `selectionDraft` 同规矩,前端零存储)。

---

## 49. T-1.9 未选部位 AI 补齐:架构定位订正、降级决策与三处取定(2026-10-02)

PRD §5.2.4 的承诺——"用户**不必选全**,未选的地面/墙面由 AI 补齐"——本轮落地。
**契约 YAML 一个字没动。**

### 49.1 订正:挂账里那句"Java 不调此端点"是错的

Python 门禁挂账里原本写着:

```
path: /internal/agent/complete-parts
source: "T-1.9(实现落在生成消费端内部;端点无调用方,未经评审)"
note: "补齐走生成流程内部,Java 不调此端点"
```

**这与架构 §11.2 的时序正相反**,而且它自己就标着"未经评审"。两条独立证据都指向
"Java 会调它":① 架构 §11.2 的时序图里 `J->>P: /internal/agent/complete-parts` 明文在列,
且发生在**落 `t_generation_task` 与投递 `queue.generate` 之前**;②
`openapi-internal.yaml:229-276` 有完整的请求/响应/失败码定义。

⇒ **以架构与契约为准**。那条挂账已删(实现注册了路由,不删会被判"挂账已过期"而无条件红),
并加了 canary。**旧记录不回改**,订正记在这里。

> 教训与 §30.2 那次同源:`material-map` **有**独立裁决支持"端点保留为预留",
> 而 `complete-parts` **没有** —— 把"同款处理"当成"已有裁决"来用,就会写出这条错的挂账。
> **一句"未经评审"不该被当成"已评审的结论"继续引用。**

### 49.2 决策:补不上就**降级**,40004 因此不再发出

**用户 2026-10-02 拍板**:AI 补不上合适的砖时,**不阻断生成** —— 按用户实际选的砖照常出图,
并在结果里如实说明哪一面没砖。理由:PRD 的本意就是"不强制选全",而当前向量库只有十几件产品、
向量模型还是占位替身,"补不上"很容易发生 —— **卡住用户等于把数据缺口变成用户的错**。
这与"陈设缺失降级不阻断"(§27.2 决策 5)是同一套取舍。

**连带后果,必须写清**:契约里声明了 **40004**、前端 `errors.ts` 也备好了文案,
但**本策略下这个码不会出现** —— 同一条内容失败(没候选、模型没选、选了候选外的、
权威校验不过)一律降级为"不补"。**不是漏实现,是决策的直接结果。**
真·服务故障(网关/Qdrant/Java 抛异常)折 **90001**,并由 Java 记 ERROR 后**同样降级**。

⚠️ **代价是"失败静默"**:只有 WARN/ERROR 日志。若补齐长期不工作,除了翻日志没人会发现
(与陈设缺失同一取舍)。登记在案。

### 49.3 三处实现取定

1. **只补 `floor` / `wall`,`local` 永不补** —— `PARTS_TO_COMPLETE` 这个常量就是边界,
   不给调用方传参的余地(传了就能绕过产品规则);Java 侧合并时也会丢弃越界的补齐项。
2. **不编造 SKU**:候选来自 Qdrant 检索(过滤里由 `build_filter` **强制注入在售**);
   用 function calling,**每个缺失部位的 `enum` 就是候选编号列表**;选完**再回问一次**
   `/internal/products/{sku}` 权威校验(挡"索引里还在售、权威库已下架"的时间窗)。
   enum 只是"应该挡住",不是保证 —— 校验必须在模型之外(与 `validate_layout` 同一哲学)。
3. **合并"用户优先"**:用户的部位一律保留并标 `user`,只接受 Python **补**的那几项;
   同部位冲突用户赢;补齐项的 `area` 置空,由服务端依户型派生(全项目只在 Java 派生一次)。

⚠️ **两个部位的候选池是同一个** —— 产品索引的 payload 里**没有**"这块砖适合当地砖还是墙砖"
的字段(§9.2 的 Payload 列),所以分不出"地砖候选/墙砖候选";由模型按部位语义挑选。

### 49.4 顺带修掉一处"安静失效"的陷阱

`GenerateService.accept` 原本整个方法是 `@Transactional`。补齐是**一次网络调用**
(检索 + 模型 + 最多两次回问,最坏几十秒),留在事务里会**一直占着数据库连接**。
本轮改成:补齐在**事务外**跑,只有"插任务 + 插发件箱"那一段进事务
(`TransactionTemplate`)—— 不拆成两个 bean、也不靠"自己调自己"的 `@Transactional`
(那个注解在 self-invocation 下**根本不生效**,是个安静的坑)。

### 49.5 两处契约措辞与实现的差(**登记,不改契约**)

1. 响应描述写的是"合并后的**完整**部位列表",而**降级时返回的可能与输入相同**(少的部位就是没补上)。
2. 请求体校验失败(缺 `space`)返回 **10001**,而契约没有为这个失败面枚举码。

两处都按"契约是照理想情况写的"处理,与 §41 的 `materialFee` 同类:**先登记,不扩契约**
(改契约要走 PRD → 架构 → 契约 → 本记录四层链条)。

### 49.6 校验结果(2026-10-02)

| 项 | 结果 |
| --- | --- |
| Python 全量 | **254 passed / 11 skipped**(基线 **237 / 11**;新增 `test_complete_parts.py` 17) |
| Java 全量(真 MySQL + Redis + RabbitMQ) | **517 passed / 0 skipped**(基线 **501**;纯函数 10 + 集成 6) |
| 前端全量 | **267 passed / 20 文件**(基线 **264**;`generate.spec.ts` +3) |
| 类型与构建 | `npm run build` 通过 |
| 契约 ↔ 实现比对 | 两侧干净;`complete-parts` 已匹配、**挂账已过期 0** |
| 契约门禁 | **7/7 PASS** |
| 契约 YAML | **一个字没动** |

**真机冒烟(三条,逐级加深):**

1. **Python 端点直打**(Java 没起):`code=0`,**降级**返回原样 —— 日志显示
   `候选 TL-1006 过不了权威校验`。原因很实在:**权威校验要回问 Java,而那时 Java 没起**。
   ⇒ 补齐**依赖 Java 在跑**(真流程里 Java 本就是发起方,所以正常)。
2. **Java 起来后直打 Python**:`floor TL-1001(user)` + **`wall TL-1006(ai)`** —— **补齐成功**。
3. **完整链路**:`POST /generate` 只带地砖 → `success` → `planId=847`;
   方案部位为 `floor TL-1001(user)` + **`wall TL-1006(source=ai)`** ——
   **AI 补的砖真的落到了方案里**。

### 49.7 未闭合

1. **"AI 补的砖会进报价"只在机制上成立,没端到端观测到。** 冒烟那次方案的报价是**空的** ——
   查库发现 `t_quote_param` 里只配了损耗率、**没有 `aux_unit_price`(辅料单价)**,
   报价引擎因而算不出整单。这是**既存的"参数没配就整单不落"**(§41 同类),**与 T-1.9 无关**;
   AI 部位与用户部位走的是**同一条计价路径**(`QuoteService` 不区分 `source`)。
   **未去补那条配置**(那会动开发库的配置)。
2. **补齐挑的砖没有质量可言** —— embedding 仍是占位,只能保证"范围对(强制空间/风格/在售)
   + 不编造"。本次冒烟正好印证:候选池小,模型挑的是库里恰好匹配空间的那几件。
3. **失败静默**(见 49.2):只有日志,没有面向运维的信号。
4. **补齐的耗时**:Java 侧 `tile.agent.timeout` 默认 30s,而这一步是"检索 + 模型 + 最多两次回问"。
   本次冒烟未测 P95;若将来撞超时,**降级**会让用户看不到补齐(而不是报错)—— 更隐蔽。

---

## 50. F-0.7 ~ F-0.9 后台三个页面:矩阵、"固定四行"与"未配置"的处置(2026-10-02)

M3 只剩 F-0.4~F-0.9 六个后台页面。用户 2026-10-02 拍板**分两批**,
本轮做**三个小的(S)**:陈设(F-0.7)、报价参数 + 字典(F-0.8,两个菜单入口 → 两个页面)、门店(F-0.9)。
**纯前端** —— 契约 YAML 与后端代码一个字没动;菜单、权限点、路由名原本就已就位。

### 50.1 交付面

| 页面 | 文件 |
| --- | --- |
| F-0.7 陈设(**真矩阵**) | `src/views/admin/AdminFurnishView.vue` |
| F-0.8 报价参数(**固定四行**) | `src/views/admin/AdminQuoteParamsView.vue` |
| F-0.8 数据字典 | `src/views/admin/AdminDictsView.vue` |
| F-0.9 门店 | `src/views/admin/AdminStoreView.vue` |
| API 模块(4 个新文件) | `src/api/admin/{furnish,quoteParams,dicts,store}.ts` |
| 路由 | `src/router/index.ts` 四条占位路由换真页面、删 `meta.task`(`name` 不变) |

⚠️ `AdminPlaceholderView.vue` **没删** —— 还有 5 处在用(导入 / 搭配 / 模板 / 账号 / 日志)。

### 50.2 三处设计取定

**① 陈设做成真矩阵,而不是"列表换皮"。**
卡片要的是"空间 × 风格**矩阵**"且"**矩阵里不出现重复组合**"。矩阵里**每个格子本身就是一个
确定的 `(space, style)`**,新增/编辑靠"点哪一格"决定 —— **组合不是手敲出来的**,
"不重复"于是成了**结构上的**结果,而不是靠校验去拦(表单里 space/style 是静态文本,有测试钉住)。
轴取 **字典项 ∪ 已有配置里出现过的 key**:字典项被删后遗留的配置标「未在字典中」留在轴上,
**不静默丢数据**。字典与配置**分开兜底**(`Promise.allSettled`):字典拉不到不该让已有配置不可见。

**② 报价参数做成"固定四行",不是"按服务端返回渲染列表"。**
服务端可能只返回两条(甚至零条),按返回渲染会让**缺的那个键整行消失**。
本轮冒烟正好证明这不是假设 —— 见 50.3。四行里显示单位(「元/㎡」)与口径说明(「0.06 表示 6%」),
损耗率**按小数录入**(服务端**拒绝**百分数写法,前端自己换算等于把量纲错误藏进一层转换里),
旁边只读提示「≈ 6%」;**缺失显示「未配置」,绝不填 0**;页顶在辅料单价缺失时给一条告警。

**③ `GET /store` 的 `30001` 是"还没配置",不是错误。**
把它当故障(红字 + 禁用表单)会让**新环境永远配不上第一家店**。所以留空表单 + 一句"填好保存即可"。
有专门用例钉住这一条。

### 50.3 真机冒烟:两次才配齐 —— 正好印证了"固定四行"

用 `admin/Admin@2026` 登录后台,走报价参数接口:

1. 先配 **`aux_unit_price = 20`** → 发一次生成 → **报价仍然是空的**;
2. 查参数才发现,**`loss_rate`(当前采用的损耗率)也没配**;
3. 配上 `loss_rate = 0.06` 再发一次 → **算出来了**:

   ```
   总价 7801.17 = 材料合计 7364.37 + 辅料合计 436.80
     地面 TL-1001  有效面积 3.6    材料费 1022.69   (3.6×1.06×268,与架构 §10.6⑤ 的示例逐分吻合)
     墙面 TL-1006  有效面积 18.24  材料费 6341.68   ← AI 补齐的那块砖
   ```

**两点结论**:
- **只缺一项时,报价是"整单不出"而不是"少算一项"** —— 所以界面上"哪个键没配"必须看得见。
  这正是"固定四行"存在的理由;按返回渲染的话用户根本不知道自己还差一项。
- **顺带补上了 T-1.9 那条"未观测到"**(§49.7 第 1 条):**AI 补的墙面砖确实进了报价** ——
  材料费 6341.68 就在总价里。T-1.9 的闭环到此完整。

⚠️ **副作用如实记录**:这次冒烟**改了开发库的配置** —— `t_quote_param` 新增了
`aux_unit_price=20` 与 `loss_rate=0.06`。那正是这一页要做的事,但它**是数据变更**,不是代码变更。

### 50.4 校验结果(2026-10-02)

| 项 | 结果 |
| --- | --- |
| 前端全量 | **318 passed / 24 文件**(基线 **267 / 20**;新增 4 个 spec 共 51 条) |
| 类型与构建 | `npm run type-check`、`npm run build` 均通过 |
| 契约 / 后端 | **一个字没动** |
| 未触碰 | `adminMenu.ts`、`permissions.ts`、`contract.spec.ts` 均未改 |

四个新 spec 各钉了三类:**对 `openapi-admin.yaml` 的契约断言**(query 参数解 `$ref`、
请求体必填与字段集)、**权限门控**(逐角色按钮可见性)、**行为与诚实边界**
(缺失 ≠ 0、无 `NaN`/`undefined`、删除二次确认、30002/30001 原样透出)。
陈设额外钉"**同组合重复数据只画一格**""**模块里不存在 create 函数**""**表单里没有 space/style 输入控件**";
报价参数额外钉"**契约无 POST/DELETE**""**四个键恒在**""**不显示 0%**"。

### 50.5 未闭合

1. **列表接口没有 `sort` 参数** —— 四页都**不提供排序控件、不在前端重排**(重排等于第二份实现)。
   字典页只**回显** `sort` 字段、不据此排序。陈设的轴序取字典自身顺序(那是"排轴",不是"排 list")。
2. **浏览器里还没人工点过这四个页面**(与 F-1.6 当初一样,得用户走一遍)。
3. **M3 还剩三个后台页面**:批量导入(F-0.4)、搭配(F-0.5)、户型模板(F-0.6);
   另有**本来就没做**的账号与操作日志两个占位页(§10.3 未展开字段)。
4. **F-0.4 有一个阻断级前置**(下一批要先解决):`spring.servlet.multipart` **未配置**,
   框架默认只收 **1MB**,而后端代码允许 20000 行 —— 真实文件会**直接 500**。
   另外错误报告的 `errorReportUrl` 后端**永远为空**(对象存储未选型),导出只能前端自己做。

---

## 51. F-0.4 ~ F-0.6 后台最后三页:上传上限、静默数据损坏与"派生字段不进表单"(2026-10-02)

M3 的后台六个页面到此**全部完成**。本批三个:**批量导入(F-0.4)、推荐搭配(F-0.5)、户型模板(F-0.6)**。
**契约 YAML 一个字没动**;后端**只动了一处配置 + 一个异常分支**(见 51.1)。

### 51.1 阻断级前置:上传大小没配,而且报的是"系统内部错误"

**问题**:`application.yml` 里**完全没有 `spring.servlet.multipart`** ⇒ Spring Boot 默认
**单文件 1MB**,而导入代码允许 **20000 行**(`SpreadsheetReader.MAX_ROWS`)。一份上万行的
CSV 轻松超过 1MB ⇒ 抛 `MaxUploadSizeExceededException` ⇒ 落到全局兜底 ⇒
**HTTP 200 + `90001`「系统内部错误」** —— 用户看到的是"服务挂了",而真实原因是"文件太大",
于是反复重传同一个文件。

**修法**(本轮仅有的两处后端改动):
1. `application.yml` 补 `spring.servlet.multipart`:`max-file-size: 20MB`、
   `max-request-size: 25MB`、`file-size-threshold: 1MB`(超过 1MB 落临时文件,不驻留堆);
2. `GlobalExceptionHandler` 增 `MaxUploadSizeExceededException` → **10001** +
   「上传文件超过大小上限(单个文件 20MB),请拆分后重试」。

**真机实测**:一份 **2.10MB / 20000 行**的 CSV —— **HTTP 200 受理,终态 success,
成功 20000 / 失败 0**。⇒ 上限确实生效,而且代码承诺的"两万行"是真的能跑完。
(改配置前,这份文件必然失败。)

### 51.2 F-0.4:上传通道、轮询与"导出只能前端做"

- **上传通道加在 `http.ts` 里**(`RequestOptions.formData` + 导出 `upload<T>`),
  **不另写一个 fetch**:401 单飞续期、`ApiResponse` 解包、业务错与网络错的区分,
  是每条出网路径都必须有的;**另写一份迟早与主路径漂移**。
  ⚠️ multipart 时**不能设 `Content-Type`**(浏览器要自己带 boundary)—— 有测试钉住。
- **轮询**照 `GenerateView` 的**递归 `setTimeout` + 卸载清理**;终态或 30001 即停表;
  网络类失败计数封顶(10 次)。有测试钉"success 之后不再发请求""卸载即停"。
- **`errorReportUrl` 实测确认为 `null`**(后端注释:对象存储未选型)⇒ 页面**不画那个下载链接**
  (画了就是死链),改为前端拿 `errors[]` 生成 CSV。生成时**带 UTF-8 BOM**,
  否则 Excel 打开中文乱码 —— 而运营是要在 Excel 里改完再传的。
- **模板下载用普通 `<a href>`**:后端按 `Accept` 头协商格式并带 `Content-Disposition: attachment`,
  同源导航自带 Cookie ⇒ 零 JS 直接下 xlsx。(CSV 需要请求头,`<a>` 做不到,本轮不做。)
- **导入是异步的**,离开页面停的只是轮询、**批次照跑** —— 界面上如实说明;
  整页刷新后**找不回批次**(没有"批次列表"接口),不假装能恢复。

### 51.3 F-0.5:一个静默数据损坏点 —— `note`/`sort`

`PUT /combos/{id}` 是 **items 整体替换**,而契约的 `ComboItem` 上还有可选的 `note`(备注)与 `sort`。
**前端的 `ComboItem` 类型此前漏了这两个字段** ⇒ 编辑一次就会**把备注静默清空**,
没有任何报错、也没有任何测试会红。

**修法**:`src/api/types.ts` 补上 `note?` / `sort?`;编辑页先 `getCombo` 拿全量、逐字段回填,
**行里已有的 `sort` 原样带回**(新增行不带,交给服务端)。有测试专门钉"带 note 的组合提交后 note 仍在"。

其余两处口径:
- **`name` 不提交** —— 它只是冗余展示名,库里不存(后端读取时从产品表带出);
- **部位恒三值 `floor/wall/local`(契约 T-9),不走字典** —— 字典只有空间/风格。

⚠️ **SKU 没有"按编号查"的后台接口**,只有"按关键词搜产品"(LIKE 名称/编号)。
⇒ 做法是"搜索 → 点选",同时保留手输;写错的兜底在服务端(`resolveProducts` 抛 10001,
**不会写脏数据**),但**用户要提交后才知道写错**。

### 51.4 F-0.6:`areas` **绝不进表单**

`spaces[].areas`(地面/墙面/局部面积)是**服务端按长宽高派生**的(架构 §8.3),读详情时后端会回填。
⇒ 表单里没有它的输入项,`buildLayout()` **逐字段重建、显式剔除 `areas`**
(不能整对象展开,否则就是把派生值当用户输入送回去)。页面上**只读展示**它。
有测试专门"加载一个带 areas 的模板 → 断言提交体里没有 areas"。

### 51.5 校验结果(2026-10-02)

| 项 | 结果 |
| --- | --- |
| 前端全量 | **356 passed / 27 文件**(基线 **318 / 24**;新增 3 个 spec 共 35 条 + `http.spec.ts` +3) |
| 类型与构建 | `npm run type-check`、`npm run build` 均通过 |
| **Java 全量**(真 MySQL + Redis + RabbitMQ) | **517 passed / 0 skipped** |
| 契约 YAML | **一个字没动** |

**真机冒烟(批量导入)**:
1. 上传一份**含一个故意错误行**(单价写成 `abc`)的 CSV → 终态 `success`,
   **总数 3 / 成功 2 / 失败 1**,错误行报**行 3** —— 与文件里那一行**逐行对上**(表头算第 1 行);
2. **同一个文件再传一次(upsert)** → 仍然 success,而库里那两条 SKU **还是 2 行**,**没有产生重复**;
3. 上传 **2.10MB / 20000 行** → 受理并**全部成功**(见 51.1);
4. 顺带确认 `errorReportUrl` 返回 `null`(印证 51.2 的处置)。

> 冒烟造出的 20002 行测试产品**已清理干净**(删的是本次自己造的 `T19-*`)。
> 上一批 F-0.8 冒烟改的报价参数(`aux_unit_price` / `loss_rate`)**保留** —— 那是配置,
> 而且正是让报价算得出来的前提。

⚠️ 中途 Java 全量曾 Fail 1 条(`acceptedMessageReachesQueue`,卡满 30 秒超时):
原因是**上一轮冒烟留下的 Python 服务在抢 `queue.generate` 的消息**(§47.6 记过同一条)。
停掉 Python 再跑即 **517 / 0**。

### 51.6 未闭合

1. **浏览器里还没人工点过这三个页面**(F-0.4/0.5/0.6 都是组件测试 + 真机接口冒烟)。
2. **超限请求的表现未用 >20MB 的文件实测** —— 理论上会返回可读的 10001,
   但 Tomcat 的 `maxSwallowSize` 有可能让连接先被重置(那就成了网络错误)。**未验证。**
3. **`errorReportUrl` 恒为 null** —— 契约有这个字段却永远拿不到值,与 `PlanPart.materialFee` 同类,
   **登记在案**,不画死链。
4. **列表都没有 `sort` 参数** ⇒ 三个页面都不提供排序控件、不在前端重排。
5. **后台仍剩两个占位页**:账号管理(`/admin/accounts`)与操作日志(`/admin/logs`)——
   它们属 F-0.2 的遗留,PRD §10.3 未展开字段,`contract-impl-gaps.yaml` 里挂着账。

---

## 52. Embedding 选型落地:Qwen3-Embedding 本地跑,以及一个被量出来的数据陷阱(2026-10-02)

**这是 P0-2(模型选型)的第一块落地。** 此前 Embedding 一直空着,检索用的是**确定性占位实现**
—— 向量是哈希出来的,**没有语义**:语义相近的两句话不会得到相近的向量,检索能跑但排序等于随机。

**契约 YAML 一个字没动**(没有接口变更);**PRD 也没动**(需求没变:仍是"基于产品库真实数据检索")。
需要改的是**架构**——选型从"仍未定"变成"已定"(§15.1)。

### 52.1 决策与两条实现路径

| | |
| --- | --- |
| **模型** | **`Qwen3-Embedding-0.6B`**(阿里,**Apache 2.0 开源权重**),**原生 1024 维** |
| **跑法** | **本地进程内**(`sentence-transformers` + CPU),不依赖外部服务 |
| **与架构原建议的差异** | 原写"如 **BGE-M3**" —— 方向一致(国产开源 / CPU 自部署),**具体模型换了**;理由见架构 §15.1 |

**为什么不是 Ollama**:一开始按"下载/运维更省"选了 Ollama,但**它的安装包只在 GitHub 分发** ——
实测该网络下**每秒约 1KB**(1.5GB 的包),基本下不动。于是改用 `sentence-transformers`:
**PyPI 走清华镜像、模型走 `hf-mirror.com`(实测 3 MB/s、约 6 分钟下完)**,两条路都绕开 GitHub。
两个适配器**都留着**(`local_embedding.py` / `ollama_embedding.py`),按 `MODEL_EMBEDDING_PROVIDER` 二选一。

### 52.2 验证:语义**真的**生效了

新增 `tests/test_embedding_live.py`(模型已下载才跑,默认跳过),三条:
维度 1024;**语义相近的两句明显比不相关的更近**;**同一组输入上真模型明显强过占位实现**(负向对照)。

真机检索实测:

```
【问】客厅想要现代风格的大理石纹地砖  → 分数 0.38~0.48,top-4 全部落在 living_room   ✅
【问】卫生间浅灰地砖配什么墙砖        → 分数只有 0.27~0.30,卫生间产品没排上来       ⚠️
```

第二条**不是模型不行** —— 查下去发现是**数据**在拖后腿(见 52.3)。

### 52.3 意外收获:那批乱码数据**在拖检索质量**,而且能量化

演示库的中文商品名是**双重编码的乱码**(§47.4 早就登记过,当时判断是"数据问题、前端不兜")。
但文档向量正是用**商品名 + 材质 + 表面 + 空间 + 风格**算出来的 ⇒ **模型拿到的是乱码**。

同一句提问,分别与"乱码文本"和"修好后的文本"算相似度(库里有 5 个卫生间可用的砖):

| SKU | 乱码文本 | **修好后** | 修好的名字 |
| --- | --- | --- | --- |
| TL-1007 | 0.339 | **0.497** | 奶白柔光 / 釉面砖 |
| TL-1009 | 0.350 | **0.471** | 墨玉黑金 / 抛光砖 |
| TL-1010 | 0.356 | **0.433** | 素白茶纹 / 岩板 |
| TL-1011 | 0.382 | **0.500** | 暖砂米黄 / 釉面砖 |
| **TL-1006** | 0.357 | **0.558** | **云灰大理石 / 釉面砖** ← 修好后排第一 |

**每一条都涨 0.10~0.20**;而且修好后居首的「云灰大理石」,正是"浅灰地砖配什么墙砖"该配的东西。

⇒ 结论:**乱码不只是"看着难看",它在实打实地降低检索质量**。修法是一行
`text.encode('latin-1').decode('utf-8')`(典型的"UTF-8 字节被当拉丁文存了")。
**本轮未改库**(那是数据处置,等用户决定);把这条量出来的证据记在这里。

### 52.4 校验结果(2026-10-02)

| 项 | 结果 |
| --- | --- |
| Python 全量 | **289 passed / 11 skipped**(基线 **254 / 11**;新增 `test_ollama_embedding.py` 19 + `test_local_embedding.py` 13,`test_embedding_live.py` 3 条**已实跑通过**) |
| 索引重建 | 用**真模型**重算 15 条(日志里可见 `LocalEmbedding ... Qwen3-Embedding-0.6B`) |
| 契约 YAML / PRD | **一个字没动**(本次是选型落地,不是接口变更) |
| 架构文档 | §15.1 由"仍未定"改为**已定**,并写明与 BGE-M3 建议的差异与理由 |

**顺带修掉一个真会出错的地方**:`tools/rebuild_index.py` 此前**写死占位实现** ——
而线上用真模型,两边会**在同一个集合里却不是同一个向量空间**,检索结果全乱。
现在它**走同一个工厂**,并显式指向 `tile-ai-agent/.env`(`Settings` 的 `env_file` 是**相对当前
工作目录**的,而这个工具通常从仓库根跑 —— 不显式指定就会静默退回占位)。

### 52.5 未闭合

1. **演示数据的中文仍是乱码**(52.3 已量化它的代价)。**未改库**,等决定。
2. **既有的 C 端会话 / 方案数据没有随索引一起"重算"** —— 索引重建只影响检索,不影响已存数据。
3. **只验了"语义相近的更近"**,没做检索**准确率**的系统评测(需要一份带标注的查询集,属 P0-2 的后续)。
4. **模型是 CPU 推理**,首次加载约 2 秒、十几条数据编码不到 1 秒;规模变大后(数千 SKU)需要复测。

⚠️ ~~**没有做人工浏览器验证。**~~ ✅ **已在同日补上,见 §45.10** ——
与 §44.8 同一条欠账,而且这一轮**更该做**:分享这条链路上有三处形状必须一致(§45.3),
而**只有一条能被测试读到**。组件测试证明的是"页面逻辑对",
**不证明"后端生成的链接,浏览器打开接得住"**。

### 45.9 仍未做(不许读成「已绑住」)

1. ~~**没有真人在浏览器里点过分享链路**~~ ✅ **已验收(2026-10-01,§45.10)** ——
   用户按八步在真实浏览器里走通,含"退出登录后用那条链接打开"这一趟,
   即 §45.3 那三处形状的**唯一**验收方式。
2. **二维码未画**(§45.1-②)—— 用户拍板本轮不做。`ShareResult.qrCodeUrl`
   **仍然恒空**(§43.2 那处不一致面**仍在**)。
3. **分享页没有 `noindex`**(§45.7)—— 已登记,**未做**。
4. **`SHARE_BASE_URL` 上线前必须改**(§43.12-7)—— **仍未处理**,默认值仍指向
   `localhost`。前端用 `location.origin` 兜了一层,但**权威值仍是后端那个配置**(§45.5)。
5. **「分享出去的是快照还是实时」契约没定义**(§43.12-8)—— 仍是**实时**。
6. **效果图 24 小时过期**(§43.10-1)—— 分享页把这个既有问题**放大了**:
   链接是发给别人的,别人很可能隔天才打开,那时看到的是**图裂**(不报错)。**未修**。
7. **`share_token` 大小写不敏感**(§43.10-2)—— 未修。
8. **`materialFee` 的必填不一致仍在**(§41.6 / §42.7 / §44.3.1 / §45.2-3)——
   前端按**可选**消费,分歧**未被消除**,只是被诚实地显示成了 `—`。
   分享页也一样。
9. **F-1.6(AI 咨询)仍等后端 T-1.6** —— 一期 C 端页面里**只剩这一个**。
   它不在 M2 的关键路径上(M2 到此闭合),属于 M3。

### 45.10 人工浏览器验收(2026-10-01,**通过**,用户执行)

**这是 §45.3「三处形状必须一致」唯一的验收方式** —— 组件测试能读到契约,
读不到"后端拼出来的那条链接,浏览器真的接得住"。上一节第 1 条到此关闭,
**M2「闭环可演示」现在是人验过的,不再只是"代码侧闭合"。**

**当时的环境(留档,供复现):**

| 组件 | 状态 |
| --- | --- |
| MySQL | 原生服务 `MySQL80`(3306),迁移到 **V11**,库里有 12 款砖 |
| Redis / Qdrant / RabbitMQ | `docker compose -f deploy/docker-compose.dev.yml up -d`(MySQL 不在 compose 里,§5.6) |
| Java(8080) | `mvn spring-boot:run -Dspring-boot.run.profiles=local`,**4 秒起好** |
| Python(8000) | `uvicorn app.main:app --port 8000`;启动日志确认 `MODEL_IMAGE_PROVIDER=placeholder`,**生成消费端已在后台线程启动**(`main.py` lifespan) |
| 前端(5173) | `npm run dev` |

**用户走的八步**:选砖 → 录户型 → 生成 → 点「方案编号」进详情 → 从「我的方案」再打开 →
改名 / 按当前价重算 → 生成分享链接 + 复制 → **退出登录,用那条链接打开**。

⚠️ **两条只在本机成立的前提,恢复开发时最容易忘:**

1. **必须用 `localhost:5173`,不能用 `127.0.0.1:5173`** —— Vite 只绑 IPv6
   (实测 `127.0.0.1` 连不上、`localhost` 可以)。这不是本轮引入的,但分享链接是
   `SHARE_BASE_URL` 拼出来的,**配成 `127.0.0.1` 会打不开**,而且同样是静默的。
2. **效果图是替身占位图**,不是真效果图 —— 本轮阶段收缩(CLAUDE.md / §29)。
   所以这次验收签的是**"跑通了"**,`CP-1` 里"出图质量"那一半**仍然没签**(§6)。

**顺带查清一件"看着像问题、其实不是"的事**:`queue.index.sync` 积压 **61 条**且
**没有消费者**。一度怀疑是漏启动,查 `app/main.py` 的 lifespan 注释后确认是**刻意的** ——
索引消费端从 T-0.13 起就**只被测试直接驱动**,"由谁托管它"是个**部署决定**,
留给上线的编排卡(T-2.4),理由记在 §35.11。**不需要修,也不该顺手在这里定下来。**

> 记这一条是因为它**长得太像 bug 了**:一个队列有积压、有声明、唯独没有消费者。
> 下次有人（包括我）看到 61 这个数,先读 `main.py:41` 那段注释,别直接开修。


## 53. 效果图转存落地:存在哪、谁来存、以及一条"下载必须封顶"的硬约束(2026-10-02)

**触发**:用户决定接回真实图像模型。而路线图 §4 早就登记着一条前置 ——
架构 §15.1 实测结论写着"出图地址是 **OSS 临时 URL(24 小时过期)**,调用方须及时转存",
但 **T-1.11 没做转存**(对象存储选型连续顺延两次,T-1.12 也只做到"记住哪张是封面",
**从未真正存过一张图**)。该遗留条目当时的处理方式就是
"**改卡「接真实图像模型时」——换回真模型时必须先定**"。

⇒ 本次到点。**不做转存的话,今晚出的图明天打开全是裂图**,分享页尤其难看
(链接不过期,接收方打开是空图,且失败方式是"图裂"而非报错)。

### 53.1 三项决策

**① 转存放在 Java 侧,不放 Python。**

架构原文写的是"调用方(T-1.11)须及时转存"。T-1.11 这张卡**跨两侧**,所以这句话
并不指定实现落在哪一端。选 Java 侧的理由:

- Python 侧转存就得新增一个 Java 内部接口(如 `/internal/images`)来接收图片,
  那要连带改 **`openapi-internal.yaml` + 权威链文档 + 门禁**;
- Java 侧做则**不改任何契约** —— 图片 URL 本来就是 Java 当不透明字符串透传的;
- 不变量 3 说 **Java 是唯一对外 API**,公共 URL 空间本就归它;
- 收口只有一处:`GenerateCallbackService#handleSuccess`。

**② 存储形态:本机磁盘 + 抽一层接口。**

用户选择(见架构 §15.5 改写)。落地是 "`ImageTransloader` + 本机目录 +
Spring 静态映射",**不引入云 OSS、不自建 MinIO**;换实现时业务侧无感。

**③ URL 存绝对地址,不存相对路径。**

用户实际跑的是 `npm run dev`,前端在 **5173**,而 `vite.config.ts` 的 dev 代理
**只转发 `/api`** ⇒ 相对路径 `/media/x.png` 会打到 Vite 而 404。
(相对路径 + 给 Vite 加一条代理也能work,但那把"能显示"绑在了 dev 配置上。)

⚠️ **`tile.storage.public-base-url` 指 Java API 的地址,不是前端地址** ——
`tile.share.base-url` 默认是 `http://localhost:5173`(前端页面),两者语义相反,
抄错会得到 `http://localhost:5173/media/...`,dev 下同样 404。已在两处配置注释里点明。

### 53.2 硬约束:下载必须有时间上界(这一条比功能本身重要)

**先把一个想当然的错判排除掉**:回调超时**不会**让 Python 重跑模型。
`orchestrator._notify` 的注释写死了"回调自身抛异常只记日志,**不影响生成**",
唯一能叫停生成的回调结果是 `40003`。所以超时本身基本只是**日志噪音**
(Java 那边会照常跑完并提交)。

**真正的风险链是另一条**:

```
下载长时间挂着 → Java 迟迟提交不了 → 任务一直停在 running
  → 补偿扫描(GenerateCompensationScanner,running-stuck-after=360s)判定"卡住"
  → 重投 → Python 真的重跑一次真实模型 → 白烧额度
```

⇒ 两条措施,缺一不可:

1. **转存设总预算**(`tile.storage.overall-budget`,默认 **8 秒**),超预算即放弃
   剩余图、保留临时地址。**这是唯一能封死上面那条链的东西。**
2. 该预算**必须小于 Python 的 `java_callback_timeout_seconds`**(当前 **10 秒**)。
   两处配置各写了一句交叉注释 —— 它们之间**没有编译期约束**,后人调一侧时必须知道另一侧。

**另一条同源的约束:转存失败绝不抛。** 抛出 ⇒ 回调 500 ⇒ 事务回滚 ⇒ 任务退回可重试
⇒ 落进上面同一条重投链。所以任何一张图出问题都只**降级为"保留原始临时 URL"**
—— 那与加这个功能之前的行为完全一致,24 小时内仍然看得见。

### 53.3 另外三处取定

- **转存必须在 `claimSuccess` 之前。** 那个闸门在 SQL 里是单向的
  (`WHERE status <> 'success'`),**没有对应的"撤回"**。若先抢闸门、提交、再转存、
  再另起事务存方案,一旦存方案失败就得回退一个已提交的 success —— 会引入新的状态机
  分支。放在前面,则**抢闸门 / 存方案 / 回填 planId 仍在同一事务**里原子完成,
  "任务说 success ⟺ 方案真的存在"这条不变量完整保留。
  代价是重复的 success 回调会多下载一次 —— 可接受(Python 不重发;补偿重投时任务
  本就被重新生成过;落盘内容寻址,重复写幂等;数据库侧仍被闸门挡住)。
- **文件名按内容哈希,不用 `planId`/`view`。** 两个原因:① `view` 由 Python 传来、
  契约只限长度 50,**可以含 `../`**,进了路径就是目录穿越;② 内容寻址让同一张图
  无论被回调几次都落到**同一个文件**,天然幂等。副作用是覆盖方案会留下孤儿文件
  (`replaceImages` 先删库行、不删文件),已登记为技术债。
- **事务切分照 `GenerateService.accept()` 的既有范式**:`handle()` 去掉
  `@Transactional`,网络 + 磁盘 I/O 放事务外,要原子的那段由 `TransactionTemplate` 包住。
  ⚠️ 不能"自己调自己"—— 同类内调用 `@Transactional` 方法**不走代理、注解静默失效**
  (`GenerateService#persist` 的 Javadoc 已把这个陷阱写死)。

### 53.4 新增的攻击面:SSRF

在此之前 Java 只是把 URL **当字符串存起来**,存什么都不出事;现在它会**主动去 GET**
—— 于是"能不能指向内网"从一个数据问题变成了一个指令问题。

`common/storage/UrlPolicy`(纯函数,可单测)做三档判定:`OWN`(自有域,跳过转存)、
`FETCH`、`REJECT`。拒绝项:非 http(s) 协议、环回 / 私网 / 链路本地(含云元数据
`169.254.169.254`)/ 运营商级 NAT、IP 的非点分十进制写法(`2130706433`、`0x7f000001`)、
带 `userinfo` 的地址。自有域用 **origin 比较**而非字符串前缀(后者会被
`http://localhost:8080.evil.com` 绕过)。

**另外必须禁重定向**(`HttpClient.Redirect.NEVER`)—— `UrlPolicy` 只判了第一跳,
一个公网地址 302 到 `169.254.169.254` 就能把上面全部判定绕开。收到 3xx 直接降级。

**残余风险(诚实标注)**:不做 DNS 解析后再判,因此挡不住 **DNS rebinding**。
彻底解法是把校验过的 IP 钉死、直连 IP 带 Host 头。该 URL 来源是内网服务、内容来自
已签约的模型厂商,判定当前不值得上这套复杂度 —— 但这一条写在代码注释里,不假装不存在。

### 53.5 实现

| 文件 | 作用 |
| --- | --- |
| `common/storage/StorageProperties` | `tile.storage.*`(开关 / 目录 / 对外前缀 / 自有域 / 三层超时 / 体积上限) |
| `common/storage/UrlPolicy` | 判定纯函数(53.4) |
| `common/storage/ImageTransloader` | 下载 + 魔数校验 + 内容寻址落盘 + 降级;失败只 WARN |
| `common/storage/StorageConfig` | 装配两个 bean,启动时打一次落盘绝对路径 |
| `common/web/WebMvcConfig#addResourceHandlers` | `{media-path}/**` → 磁盘目录(该方法一直是空的) |
| `common/security/SecurityConfig` | 显式 `{media-path}/**` 放行 —— **分享页匿名要读** |
| `generate/GenerateCallbackService` | `handle` 去 `@Transactional`;成功分支先转存再进事务 |

**尺寸校验按实际读取字节计数,不信任 `Content-Length`**;并按**魔数**定扩展名
(png/jpeg/webp),防"200 + 一页 HTML 错误页"被当图存下来。

### 53.6 验收

- **不消耗额度**:图片由绑在 `127.0.0.1` 的桩服务提供,直接 POST
  `/internal/generate/callback` 即可跑完整条链路。
- `ImageTransloaderTest`(14 条):落盘与地址改写、幂等、非图片 Content-Type、
  魔数不符、超体积、**302 不跟随**、连不上、500、空响应体、一张坏不影响其它张、
  自有域不下载、开关关掉不下载。
- `UrlPolicyTest`(35 条):自有域 origin 比较、公网放行、内网/云元数据/走私写法/非 http 协议
  /userinfo 全部拒绝。
   - ⚠️ **其中一条是真 bug 的回归**:四段的主机名**不是 IP**。首版把
     `dashscope-result.oss-cn-beijing.aliyuncs.com` 这类四段域名误判成"可疑 IP"而拒掉,
     而 OSS 域名**恰好几乎都是四段** —— 会静默拦死全部正常图源。已钉住。
- `ImageTransloadIntegrationTest`(5 条,需 MySQL):落盘 + **`t_plan_image.url` 与
  `t_generation_task.result_ref` 都被换掉** + **匿名 GET `/media/**` 拿到字节** +
  重复回调幂等 + 抓不到时保留原地址且任务仍成功 + 非 http(s) 被拒后降级。
- 回归:Java 全量 + 前端 + `contract-diff.sh` + `ci.sh` 七个门禁(见 §53.7)。

### 53.7 契约:不需要改,并说明为什么

- `openapi-internal.yaml` 的 `imageUrls[].url` 只是 `type: string`,无 `format` / `pattern` /
  `maxLength`;换成更短的本地绝对地址仍是合法值。
- `/media/**` 由 `ResourceHandlerRegistry` 注册,**不进 `RequestMappingHandlerMapping`**,
  契约↔实现比对器(只遍历 `getHandlerMethods()`)看不到它,不会被判成"未登记端点"。
  `placeholder.svg` 是同类先例。
- **无 DB 迁移**:`t_plan_image.url VARCHAR(500)` 够用,本地地址反而更短。
  (若有人提议加 `local_path` 列:没必要,URL 本身已足够,加列只会扩散契约面。)

### 53.8 明确不做

- **旧数据不回补**。库里的占位图行仍指向磁盘上真实存在的 `static/placeholder.svg`,
  不会裂;而历史真实临时 URL**已过期、源端无法再取**,回补任务无物可拉,只会失败刷日志。
- 孤儿文件回收、签名 URL 防盗链、生命周期归档 —— 单机内网场景暂不需要。
- **仍未验证**:真实模型下的出图画质与"图长得对不对"(属 §29 明确推迟项,需额度)。
- **上线前的必配项**(已加进部署清单):`tile.storage.public-base-url` 改成对外地址;
  反代要转发 `{media-path}/**`(dev 直连 8080 能跑通,**上线才炸**)。


## 54. "没人接手"要当场说清楚:受理前探测消费端,以及 40002 终于被接上(2026-10-02)

### 54.1 触发:一次误判成"额度不足"的实测

用户实测:在选砖页点"生成" → 前端一直停在 **"正在生成…(已排队)"**,十几分钟
没有任何解释。用户的判断是"大模型没额度了"。

**实际成因是 Python 生成服务根本没启动** —— 把服务起起来之后,那条任务
**3 秒内就 success 了**(方案 1196)。它从来不是"失败",只是"**从来没人接手**"。

**顺带排除一个误解**:**真没额度反而不会卡** —— 模型会立刻返回错误、Python 马上回调
失败,用户几秒内就能看到。所以"一直卡住"与"额度"无关。

### 54.2 三个查出来的事实

**① 受理是"静默降级"的,没有任何一路会告诉用户消费端不在。**
`PartCompletionService.completeIfNeeded` 的契约是 **永不抛异常**(注释写死了
"补齐是增强,不是主流程")。所以 Python 挂掉时受理照常成功,消息投进
`queue.generate` 就没人管了。

**② 兜底时间是 3–4 分钟,不是 30 分钟。**
按 `GenerateCompensationScanner` 实际代码算:投递器正常时 outbox 行是 `sent`,
`pending` 分支进得去 ⇒ 重投 2 次、第 3 轮扫描判失败 ⇒ **约 180 秒(算上扫描相位最多 ~4 分钟)**,
文案 `"生成任务长时间无响应,重试已达上限"`。
⚠️ `hard-deadline: 30m` 那条路**只在投递器也卡住时**才走(每轮扫描都被
`hasPendingOutbox` 挡掉)—— 它不是常态。
⇒ 也就是说:**用户要盯着一个进度恒为 0 的转圈看三到四分钟,最后收到一句行话。**

**③ `40002 生成服务繁忙` 四层齐备,却从来没被抛出过。**
契约 enum + `openapi-c.yaml` 的 `POST /generate` 失败码、Java 的
`ErrorCode.GENERATE_BUSY`、前端的 `GENERATION_BUSY` 常量与文案、
`generate.spec.ts` 里那条"40002 给出可读提示"的测试 —— **全都在,唯独没有抛点**。
⇒ 契约里这个码的本意就是**受理时**告诉用户,只是这条线一直没接。

### 54.3 决策:受理前问一句"队列有没有人在听"

`GenerationConsumerProbe` 用 `RabbitTemplate.execute(...)` 拿到底层
`Channel`,读 `channel.consumerCount(QUEUE_GENERATE)`;`GenerateService.accept()`
的**最前面**(早于归属校验、早于任何出网调用)据此决定是否当场 40002。

**① 为什么问队列,而不是调 Python 的 `/health`。**
那个 `/health` **只管进程活着**。它自己的注释写得很清楚:"生成消费端启动失败
只告警,**别把 `/health` 一起打掉**" ⇒ **消费端挂了 /health 照样 200**,
它答不了我们要问的问题。而"这个队列有没有消费者"问的**就是那件事本身**。

**② 探测出错时 fail-open(按"有消费端"放行)。**
刻意的取舍:探测本身抖动(AMQP 抽风)不该把正常用户挡在门外 —— 那种情况下
宁可照常受理、让补偿扫描兜底,也不要误伤。**只有在明确读到 0 时才拒绝。**

**③ 放在 accept 最前面。**
在这一步最便宜:排在归属校验与出网调用之前,拒绝时不会白做任何工作。

**④ 配置开关 `tile.generate.require-consumer`(默认 true)。**
集成测试**必须关掉** —— 跑测试的机器上没有 Python 消费端,`consumerCount` 恒为 0。

⚠️ **一共 5 个类要关,而第一次只找出了 2 个,导致全量跑出 37 个红**。
教训:只搜"谁是直接 `POST /generate` 的"是**不够**的 —— 还有类是通过**辅助方法
间接**走这条路的。实测受影响的是:

| 类 | 怎么走的 | 漏掉时的红 |
| --- | --- | --- |
| `GenerateIntegrationTest` | 直接 POST | — |
| `GeneratePartCompletionIntegrationTest` | 直接 POST | — |
| `PlanShareIntegrationTest` | 辅助方法 `generateAndSucceed` | 17 |
| `PlanRecalcIntegrationTest` | 辅助方法造方案 | 11 |
| `PlanManagementIntegrationTest` | 辅助方法造方案 | 9 |

⇒ **判断"改一个收口点会影响哪些测试"时,要顺着辅助方法找,不能只搜字面量。**
`src/test/resources` 里也**没有**共享配置可挂钩(且不能塞 `application.yml` ——
它会把主配置整体顶掉),所以只能逐个类加。

### 54.4 附带修的两处文案

- **失败原因翻成人话**(`tile-ai-agent/app/generate/orchestrator.py` 的
  `_user_facing_message`):原先直接把 `f"{type(error).__name__}: {error}"` 当展示文案,
  用户看到的是 `ProviderError: HTTP 400 ...`。
  ⚠️ 这与 Java 侧 `GenerateService.failureMessage` 的 Javadoc **直接矛盾** ——
  那句写着这应该是"我们自己的 Python 编排器写的**展示文案**,
  **不是第三方 SDK 的原始异常**"。**实现与注释分叉**,本次对齐。
  额度不足/欠费恰好落在 400 上,给了专门的解释(这是用户最需要被解释的一种失败);
  认不出的原因**保留技术串**,不抹掉排查线索。
- **补偿扫描判失败的那句行话**(`GIVE_UP_MESSAGE`):
  `"生成任务长时间无响应,重试已达上限"` → `"生成服务长时间无响应,请稍后重试"`。
  它管的是**残余情况**(受理时消费端还在、跑到一半才挂),与受理探测互补。

### 54.5 验证

- `GenerationConsumerProbeTest`(6 条):有消费者 / 读到 0 / 抛异常 fail-open /
  返回 null fail-open / `requireConsumer` 抛 40002 且文案可操作 / 有人在听时不抛。
- `GenerateConsumerRequiredIntegrationTest`(2 条,需 MySQL):
  **读到 0 ⇒ `POST /generate` 返回 40002**;对照组(读到 1)⇒ 照常受理并回 taskId。
  ⚠️ 用 `@MockitoBean RabbitTemplate` 给出**确定**的消费者数,而不是依赖本机
  Python 有没有启动 —— 否则同一份代码在开发机与 CI 上会得到相反结果。
  对照组是必须的:没有它,一个"把所有人都拒掉"的实现也能让主用例通过。
- Python 291 passed / 11 skipped(基线 289,新增 2 条文案测试)。
- 真机:见 §54.6。

### 54.6 明确边界

- **不动** `hard-deadline: 30m` 与两个 stuck 阈值 —— 它们是给"消费端活着但卡住"
  兜底的安全网,与"没人接手"是两回事。
- **不加**"前端等 N 秒就放弃":前端猜不出是"慢"还是"死",猜错了会把正常的长生成
  也判死。**真实性由服务端给**。
- **残余**:受理之后消费端才挂的情况,仍要靠补偿扫描(3–4 分钟)。这是刻意留的
  安全网,不在本次范围内扩。
- ⚠️ **探测的假阴性**:消费端正好在重启的那一两秒,用户会被拒一次。代价是
  "重试一下";对比"盯着零反馈的转圈等几分钟",这个取舍成立。


## 55. 导入:图片 URL 被 50 字符的通用闸门拦死(2026-10-02)

### 55.1 怎么发现的

导入 25 款真实瓷砖(蒙娜丽莎产品)时,**25 条挂了 24 条**,错误清一色是
`多角度图URL 中的值过长:https://www.monalisa.com.cn/uploadfiles/...jpg`。

### 55.2 根因

`ProductImportService.multiValues(...)` 把**所有多值列**都按 `MAX_MULTI_VALUE = 50`
卡长度,而图片地址那一列的上限本来另有规定 —— `MAX_COVER_IMAGE = 500`
(对应 `t_product_image.url VARCHAR(500)`)。

⇒ 一条 **70 字符的正常图床地址**,在**走到那段 500 的检查之前就被 50 的闸门拦掉**了。
**那行 500 的检查对任何超过 50 字符的 URL 根本执行不到,是死代码。**

**为什么一直没被发现**:导入模板自带的示例地址是
`https://oss.example.com/a.jpg` —— **28 字符**,恰好卡在 50 以下。
⇒ 照模板填的样例永远过得去,**只有真实数据才暴露**。

### 55.3 修法

`multiValues` 增加 `maxLength` 形参(**按列给**),图片列传 `MAX_COVER_IMAGE`;
原调用点后面那段重复的 URL 长度检查随之删掉。

`MAX_MULTI_VALUE` 的注释里补了一句:**不能拿它卡图片 URL** —— 那不是"值写得对不对"
的问题,真实图床地址天然就比它长。

### 55.4 验证

- `AdminProductImportIntegrationTest` 新增回归用例:一条 **>50 字符**的真实图床地址
  ⇒ 导入成功、两张多角度图都落库。该类共 **11 条,全过**。
- 真机:25 条重新导入 ⇒ **25 成功 / 0 失败**;
  导完的 25 款产品**默认是 `off_sale`**(模板没有状态列,`t_product.status` 的
  默认值就是下架),需要再走一次 `PATCH /admin/products/{id}/status` 上架。

### 55.5 契约

**不需要改**:`ProductUpsert` 的 `images[].url` 只是 `type: string`,没有 `maxLength`;
`t_product_image.url` 就是 `VARCHAR(500)`。本次是**实现向列宽对齐**,不是改契约。


## 56. 接入硅基流动 Qwen-Image-Edit-2509:实测纠正了三处文档错误(2026-10-02)

### 56.1 为什么接它

用户要真实出图,但**百炼额度用尽**。选型过程中逐一排除:

| 候选 | 为什么没选 |
| --- | --- |
| `z-image-turbo`(百炼) | **纯文生图**,请求体只接受一个 text 对象,传参考图直接报错 |
| MiniMax H3 | **是视频模型**;图生图靠"生成 5 帧取第一帧"绕路,且 16GB 笔记本跑不动 |
| 本地部署(FLUX.2 klein / Qwen-Image-2.1) | 可行,但是"再开一个项目"的量级(下 15–33GB + 写适配器),留作后手 |
| **硅基流动 `Qwen/Qwen-Image-Edit-2509`** | ✅ **有现成的多参考图编辑、云端调用、按张计费** |

**它的形态和架构 §15.3 要的"底图 + 各部位瓷砖切片"是对得上的** —— 这才是有决定性的一条。

### 56.2 ⚠️ 实测纠正的三处(官方文档都是错的)

**① 参考图字段名不是 `imageUpload`,是 `image`。**
按文档写会得到 `{"code":20015,"message":"Missing required key: image"}`。
多图是**并列字段**:`image` / `image2` / `image3` / `image4`……
**第一张没有数字后缀**。

**② 参考图上限实测是 4 张,不是文档暗示的 3 张。**
（文档只列到 `image3Upload`,照它写会误以为只有 3。）
**这一条很关键**:架构 §15.1 / §15.3 的生产路径要的正是"底图 1 + 切片最多 3 = **4 张**"
⇒ **换到这个模型不会收窄"多砖多部位"能力**。5 张以上没测,适配器保守卡 4。

**③ 返回体同时带 `images` 与 `data`**,两者都是 `[{"url": ...}]`。
解析**优先取 `images`、回退 `data`** —— 只认一个,厂商哪天真删了另一个就会静默出空。

**另外两条实测事实**:
- 出图 URL 是带签名的临时地址,**`X-Amz-Expires=86400` = 24 小时**(网上有说"1 小时"的,
  实测不是)。⇒ 与万相同档,**§53 的转存机制照常适用**。
- 一次编辑约 **28 秒**(服务端在 `timings.inference` 里自报),与万相的 24 秒同量级,
  §13.1 的"单图 ≤60s"仍成立。

### 56.3 一处**不报错、改为截断**的决定

出图编排层(`orchestrator._ref_images_of`)会把产品的**封面 + 所有图**都当参考图传下来,
实测导入的产品**每款 5 张左右** ⇒ 会**超出 4 张上限**。

**决定:截断到 4 张 + 记 WARN,不报错。**
理由:**产品多几张照片是正常情况**,不是一个配置错误 —— 为它让整次生成失败,
是把一个可以降级的小事变成了故障。保留前 4 张(封面排在最前,丢的是后面的备用角度图)。
*(对比:万相那边超 9 张是 `GatewayConfigError` —— 那是真的配错了。)*

### 56.4 实现

| 文件 | 作用 |
| --- | --- |
| `app/gateway/siliconflow_image.py` | 适配器;实测结论全部写在模块 docstring 里 |
| `app/gateway/factory.py` | `_IMAGE_PRESETS` 加 `siliconflow`;`_build_image_adapter` 按 provider 分发 |
| `app/core/config.py` | 新增 `model_image_api_key_siliconflow` |
| `.env.example` | 两家形态差异、字段名坑、非商用许可都写在注释里 |

**密钥为什么单开一个变量**:两家密钥不同,而"演示用硅基流动、答辩用万相、靠
`MODEL_IMAGE_PROVIDER` 切"是明确的使用方式。共用一个变量就得来回改写、还会把另一个
覆盖掉。留空时**回退**到 `MODEL_IMAGE_API_KEY`,所以只配一个也能跑。

⚠️ **该模型限非商用**(Qwen Research License)—— 毕业项目可以,**不得商用**。

### 56.5 验证

- `tests/test_siliconflow_image.py` **14 条**:字段名(钉住"第一张没有数字后缀")、
  不发 `image_size`、`seed` 有值才发、**超 4 张截断且封面保留**、`images`/`data` 双形态解析、
  空响应带出厂商原因、`n>1` 报错、4xx 翻译成 ProviderError、usage 恒 0 的说明。
- Python 全量 **305 passed / 11 skipped**(基线 291,新增 14)。
- **真实密钥实测**(2026-10-02):`image` 单图 200、`image`+`image2`+`image3` 200、
  再加 `image4` 仍 200;装配自检确认工厂真的装上了 `SiliconFlowImageGen`。

### 56.6 契约

**不需要改** —— 图像模型在 `ImageGenGateway` 端口后面,**没有任何对外接口/字段/枚举变化**。

### 56.7 明确边界

- **没有验证**:真实出图的**画质**、以及"它能不能真的把指定的砖贴到指定的部位"。
  实测只验到"接口通、能出图"。**§29 的出图质量评估仍然没做**。
- **成本**:约 ¥0.3/张;演示几十次是十几块钱。**不是免费方案**。
- 本地部署(FLUX.2 klein 4B / Qwen-Image-2.1)留作后手,没做。


## 57. 接上真模型后暴露的三个 bug(2026-10-02)

### 57.1 一句话

**§56 把模型接上了、接口也验通了,但真机一跑就是出不来图。前后失败三次,每次原因都不一样。**
三个 bug 有**同一个性质**:**占位图时代一个都看不见** —— 占位地址只有 40 来个字符、
参考图也少、内容类型规规矩矩。真实模型一来,三个一起冒出来。

### 57.2 bug ①:每个部位把"整个相册"当参考图发

**现象**:选 1 块砖 → 生成 → 卡在 `running` 进度 0 → 一直不出图。

**根因**:`orchestrator._photo_urls_of` 把 `coverImage` + `images[].url` **全部**当参考图。
导入的真实产品每款 5–6 张照片 ⇒ **选 1 块砖发 6 张、选 2 块发 12 张**
(日志逐字:`最多接受 4 张参考图,收到 12 张 —— 保留前 4 张,丢弃 8 张`)。
参考图越多模型越慢 ⇒ 超过 `gateway_image_timeout_seconds`(60 秒)⇒ 读超时 ⇒
重试 3 次 ⇒ **熔断器打开** ⇒ 任务判失败。

⚠️ **这同时偏离了架构设计**:§15.3 要的是"底图 1 张 + **各部位切片**最多 3 张",
即**每个部位一张**,不是"每个部位把它整个相册发过去"。

**修法**:`_ref_images_of` 改成**每部位一张代表图**(封面优先,缺封面退回相册第一张)。
效果:选 1 块砖发 1 张、3 个部位发 3 张 —— 加上底图正好是 §15.3 的 4 张上限。

**验证**:`test_generate.py` 新增"有 6 张照片的产品只发 1 张"与"缺封面退回第一张"两条回归;
实测选 1 块砖(系统补齐墙面后为 2 个部位)⇒ **48 秒出图成功**。

### 57.3 bug ②:图片地址超过 500 字符,成功回调被整条拒掉

**现象**:模型画完了、地址也回传了,但任务**一直不 success**,图进不了系统。

**根因**:真实图像模型返回的是**带签名的临时地址**(`s3.siliconflow.cn` + 一长串 JWT),
**实测 1570 字符**。而回调 DTO 的 `ImageRef.url` 有 `@Size(max = 500)` ⇒
**整个成功回调被 `10001 参数校验失败` 拒掉**。
实测证据:600 字符的地址即返回
`{"code":10001,"errors":[{"field":"imageUrls[0].url","reason":"图片 url 不能超过 500 字符"}]}`。

**修法**(两处必须同步,缺一不可):
- `GenerateCallbackRequest.ImageRef.url` 的上限 500 → **2048**;
- **`V12__widen_image_url.sql`** 把 `t_plan_image.url` 与 `t_generation_task.result_ref`
  放宽到 `VARCHAR(2048)`。

**为什么列宽也要放宽**:正常情况下 §53 的转存会把长地址换成本机短地址再入库,
但**转存可能失败并降级为保留原地址** —— 那种情况下长地址直接写库,列宽不够就是写库报错,
把一个本可降级的小事变成故障。

**验证**:`GenerateIntegrationTest` 新增"1500 字符地址被受理并完整入库";`SchemaMigrationTest`
的 `EXPECTED_SCHEMA`/`EXPECTED_MIGRATIONS` 同步更新(它**连列宽一起断言**,改列宽必须改它)。

### 57.4 bug ③:转存只认 `Content-Type`,于是把真图拒了

**现象**:bug ② 修完后终于 `success` 了,但**返回给前端的还是那个 24 小时过期的签名地址**。

**根因**:`ImageTransloader` 要求 `Content-Type` 必须是 `image/*`,而
**硅基流动的图床把生成结果标成 `application/octet-stream`**
(日志逐字:`效果图转存失败,保留原地址 … 原因=Content-Type 不是图片: application/octet-stream`)。
⇒ 转存整个失败、降级保留原地址 ⇒ **§53 想解决的问题原样复活**。

**修法**:去掉 `Content-Type` 闸门,**只认字节**(魔数)。
理由:Content-Type 是应用自己**声明**的、不是标准强制的;而魔数校验本来就有,
且**严格更强** ——
- 声明说是图、字节不是(200 + 一页 HTML 错误页)→ **魔数拦住** ✅(原有的防护没丢)
- 声明说不是图、字节是(本例)→ **放行** ✅

> **声明与事实冲突时,信事实。**

**验证**:`ImageTransloaderTest` 把"Content-Type 不是图片就拒"那条**改成相反断言**
(octet-stream + PNG 字节 ⇒ 照常转存),并给"多张里坏一张"那条换了个**真坏在字节上**的样本。

### 57.5 ⚠️ 未验证(别读成"已完成")

- **bug ③ 的修复没有真机复验。** 它只跑过单测(14 条)与集成(5 条),**端到端没跑**。
  ⇒ **"图到底有没有变成本机永久地址"这一条,目前是未验证状态。**
  下次接手第一件事就是补它:生成一次 → 看 `~\.tile-ai\media` 有没有新文件、
  库里地址是不是 `http://localhost:8080/media/...`。
  ✅ **已关闭(2026-10-03)** —— 上面这条照办了,**通过**。证据见 §57.8。
- **出图质量没评**:只验了"出得来",没验"画得像不像"(§29 推迟项)。

### 57.6 契约

**不需要改。** 放宽的两处上限都是**实现自己加的**:
`openapi-internal.yaml` 的 `imageUrls[].url` 与 `openapi-common.yaml` 的 `PlanImage.url`
**都只有 `type: string`,没有 `maxLength`**。本次是**实现向契约对齐**(契约更宽,实现原先更严)。

### 57.7 一条共同的教训

**这三个 bug 有同一个性质:只有真实数据/真实模型才暴露。**
占位图地址 40 来字符、参考图少、内容类型规规矩矩 —— 于是:
- "参考图会撑爆超时"看不见;
- "地址会超 500 字符"看不见;
- "图床的内容类型不规范"看不见。

⇒ 项目里凡是有"替身/占位"实现的地方,**换成真的那一刻都要重新走一遍端到端**,
不能因为"架构没变、契约没变"就假定它一定能跑。

### 57.8 复验(2026-10-03)—— §57.5 那条未验证项到此关闭

§57.5 留的作业照办了:起服务 → 生成一次 → 查磁盘与库。**通过。**

| 证据 | 结果 |
| --- | --- |
| Java 日志 | 「效果图已转存 … **字节=1211247** 地址=`http://localhost:8080/media/19/ed/….png`」 |
| 落盘文件 | `~/.tile-ai/media/19/ed/…png`,**1,211,247 字节 —— 与日志里的字节数逐字节一致** |
| 文件头 | `89 50 4E 47 0D 0A 1A 0A` = PNG 魔数 ⇒ **不是 HTML 错误页、不是占位图** |
| HTTP 取回 | 200 + `image/png`,长度一致 |
| 方案详情 | `coverImage` 是**本机短地址**,不再是 `s3.siliconflow.cn` 那种长签名地址 |

出的是**真实模型画的图**(1.2MB PNG),所以 §53 / §57 要解决的"依赖 24 小时临时地址"
这件事,在真实数据上确认解决。生成耗时 **48 秒**。

**参考图张数**:拿库里真实商品数据核算(每款相册 6 张)——
2 部位 2 款砖 = **2 张**(改前 12 张)、同一款砖铺 2 个部位 = **1 张**(去重)、
SKU 查不到 = 1 张且不报错。
⚠️ **运行时那行 `refImages=` 没抓到**:Python 默认日志级别只打 WARNING,而它记在 INFO。
⇒ 上面是**按代码接缝核算**的结果,**不是运行时观测值**;要铁证得把日志级别调到 INFO 再跑一次。

**同一次会话里另外补的两件(环境与数据,不入代码)**:

1. **服务令牌已配**(两侧同值,头名 `X-Service-Token`),四个方向实测:Java 侧闸门
   (不带/带错 ⇒ 401·10002,带对 ⇒ 200)、Python 侧闸门(同上)、Python→Java 的真实客户端
   取数与回调路径、Java→Python 的 `/internal/agent/parse-layout`(在 Python 访问日志里看到 200)。
   ⚠️ 该缺口的性质是"**本地未配置**",已在本地补上;**生产仍需在部署环境设
   `AGENT_SERVICE_TOKEN`** —— 两个配置文件都不入版本库,不会跟着代码走。
2. **报价参数与陈设矩阵补齐**(报价参数缺 `aux_unit_price`/`loss_rate` 两个**参与计算**的值,
   只配了允许范围 ⇒ 方案里根本没有报价字段)。补齐后:方案 1681 重算得 **6028.01**,
   材料费 5591.21 + 辅料 436.80,**逐项对得上 PRD §5.5 的公式**;陈设进了提示词
   (实测组装结果含「陈设:悬空白色洗手台、无框镜柜…」一行)。
   ⚠️ 顺带记一个**静默坑**:后台接口建陈设配置时**不传 `status`** 会落成 `off_sale`
   —— 数据存得进去、而后台列表里看得见,但**取数照样返回"未配置"**,且全程不报错。
   从接口批量灌配置时必须显式给 `status: on_sale`。

**当日晚些时候:真实浏览器复验(12:36~12:37)** —— 上文只覆盖了接口层,这里补上人眼那一趟。
在前端页面里**真的点了一次生成**:

- 受理 `taskId=g_4519048a…`(`parts=2`、**`angles=2`**);12:36:11 受理 → 12:37:40 完成,
  **两张都转存成了本机短地址**(`view=default` 1,494,969 字节 / `view=angle1` 1,242,799 字节),
  回调落方案 `planId=1685`(`images=2`)。两张串行合计约 **89 秒**。
- **报价快照这次没有报失败**:对比 11:20 那次(同一台机器)有 `QuoteService` 与
  `PlanAutoSaveService` 两条"报价参数未配置"的警告,这次**一条都没有**
  ⇒ 补上 `aux_unit_price` / `loss_rate` 之后,**生成路径上报价也自动算了**(不只是重算)。
  ⚠️ 该方案属于操作者本人的账号,本次没有那个账号的登录态,**只记"不再报失败",
  没有直接读到那笔报价的金额**。
- 陈设是否进了**这一次**的提示词,没有单独的运行时证据;但"数据 → 提示词"这一环上文
  已单独实测(组装结果里确实出现「陈设:…」一行),两处合起来可认为它生效。
- ⚠️ 本次生成走的是**用户的登录态**;而 `JWT_SECRET` 未配置时**每次重启 Java 都会让
  所有登录态失效**(本地开发如此),复验这类流程前要先重新登录。

---

## 58. F-0.2 账号与权限 / 操作日志:五个接口落地、两条防呆与契约推断项收口(2026-10-03)

### 58.1 一句话

**后台最后两个占位页做完了 —— 但工作量的大头不在页面上:那 5 个接口后端一个都没有,
而且整组契约是"推断"出来的。本次一并落地 + 评审确认。**

```
前端两个占位页   → 真实页面
后端 5 个接口    → 从 0 实现(原本以为是"只差前端")
契约推断项       → 逐条评审确认(不补 x-todo,理由见 58.4)
```

### 58.2 落地的五条路由与权限点

| 方法 + 路径 | 权限点 | 谁够得着 | 审计动作 |
| --- | --- | --- | --- |
| `GET /api/v1/admin/accounts` | `account:manage` | 仅 super_admin | —(读不记) |
| `POST /api/v1/admin/accounts` | `account:manage` | 仅 super_admin | `account.create` |
| `PUT /api/v1/admin/accounts/{id}` | `account:manage` | 仅 super_admin | `account.update` |
| `DELETE /api/v1/admin/accounts/{id}` | `account:manage` | 仅 super_admin | `account.delete` |
| `GET /api/v1/admin/logs` | `log:read` | super_admin / operator | —(读不记) |

权限点**照 §4.4 的对照表**挂,没有新造:拦在 `AdminPermissionInterceptor`,
失败是 **403 + 10004**(真实状态码)。实测:operator / editor 调四个账号接口一律 403,
operator 读日志 200。

### 58.3 两条防呆:实现**比契约严**,在此记账

契约里**没有**这两条,所以它们是"实现自己加的约束"。失败码用**既有的 `30002`,不新增码** ——
理由:语义正是"状态不允许该操作";`/accounts` 那组契约本就没声明失败码,而 `ErrorCode` 是全局码。
新增一个码要走全链改造(PRD → 架构 → `openapi-common.yaml` 的 enum → `ErrorCode`
→ `ErrorCodeContractTest` 的条数 → 前端 `errors.ts` 与文案 → `contract.spec.ts`),收益为零、风险不小。

| # | 规则 | 为什么要有 |
| --- | --- | --- |
| ① | 不能**停用/删除自己** | 一次手滑就能把自己锁在门外 |
| ② | 不能让"**启用中的超级管理员**"归零 | 后台从此**没人进得去**,只能手工往库里插 |

**② 为什么把「改角色」也算进去 —— 这是本次最重要的一个推理。**

按字面只防"停用/删除"的话,②**在当前权限模型下永远触发不到**:能调这组接口的必然是一个
**启用中的超管**(权限点只给超管,而账号一旦停用,`roleOf` 就返回空、判权失败关闭)
⇒ 他停用/删除**别人**时自己还留着 ⇒ 人数不可能归零。

真正的洞在**降级**:超管把自己(或另一个超管)改成 `operator` / `editor` —— 改完系统里
一个启用超管都不剩,后台**再也登不进去**。所以 ② 实现为
"**任何会移除最后一个启用超管的操作都拒绝**",覆盖停用 / 删除 / 改角色三条路径。

判定**用库里的现值**(`countEnabledSuperAdmins()`),不看请求体;操作人取自会话
(`CurrentPrincipal.requireAdmin()`),**绝不信任客户端自称**。

### 58.4 契约推断项:逐条评审确认(**不补 `x-todo`**)

`openapi-admin.yaml` 的文件头声明:架构 §10.3 **只给了资源前缀**,`/accounts` 与 `/logs`
是契约作者"按完整 RESTful CRUD 展开"的推断,并说会保留 `x-todo` 标注 ——
**但这两块实际上没打标**(那句"这是推断的"只写在文件头,读到那一段的人看不见)。

本次的处理:**在 description 里写明"推断项 + 已评审确认 + 裁决见本节"**,**不补 `x-todo`**。
理由:`x-todo` 的含义是"上游未定义、**待评审**";而**评审就是这次做的**,再标"待评审"是假的。
这沿用 `/auth/login` 那处的先例(它当年也挂过 `x-todo`,确认后改成"**已确认为正式契约**")。

逐条裁决(全部按契约现有形状实现,不另立):

| 推断项 | 裁决 |
| --- | --- |
| 方法集合(GET/POST/PUT/DELETE) | 照契约 |
| `/accounts` **没有单条 GET** | 照契约(前端也不需要) |
| `password` 新增必填、**编辑留空 = 不改** | 照契约;实测:编辑留空后**旧密码仍能登录** |
| "停用"走 **PUT 改 status**,不是独立端点 | 照契约 |
| `DELETE` = **真删除**(物理删该行) | 照契约;配套:日志页对已删账号兜底显示"已删除账号 #N" |
| `/logs` 的 4 个查询参数 | 照契约;纯日期按当日 00:00:00 / 23:59:59 解释 |
| `targetId` 是 **string** | 照契约 |
| `detail` 是 **object** | 照契约 —— 实体里存的是 JSON **文本**,读时用 `ObjectMapper` 转成对象 |

**一处为落地而改的契约(走了链)**:`OperationLogView` 新增 **`adminUsername`**
(`openapi-common.yaml`,`nullable: true`),上游出处补在**架构 §8.1**。理由:`log:read` 属于
**运营**,而运营**没有** `account:manage` —— 前端自己查不出"这个 id 是谁",富化只能放服务端;
账号被删时为 `null`。链:架构 §8.1 → 契约 → 本节。

### 58.5 审计:两个已知取舍(不是 bug)

1. **只记成功的方法。** 切面在 `proceed()` 之后才写日志 ⇒ 被拒的操作(30002 / 20003 / 20004)
   **不留痕**。实测确认:三次防呆拒绝与两次建号失败,日志里一条都没有。
   取舍:审计记的是"发生了什么",不是"有人试过什么";要记尝试得换掉整个切面模型。
2. **`POST` 的 `target_id` 恒为空** —— 路径里没有 `{id}`,而注解的 `targetIdParam` 取自路径变量。
   实测形如 `account.create · target=account#`。同样记在案。

**密码不进审计**:切面的 `detail` 只写 `{"method","path"}`,**刻意不抓请求体**。
本次补了断言钉住它(详情里不得出现密码明文),防的是以后有人"顺手" dump 请求体。

### 58.6 比对器暴露的两件事

1. **新接口被真比过。** 报告:意外缺失 0、多余路由 0、挂账已过期 0;`POST /accounts` 落进
   "形态无法对位(契约=对象 / 实现=键不可枚举)" —— 与 `products` / `combos` / `templates`
   的 create **同款限制**(响应是 `Map.of("id", …)`)。这是比对器已知抓不到的一类(`Map`),
   不是本次引入的。
2. **一条测试守卫被改,因为守的场景反转了。** `ContractImplDiffComparerTest` 原先断言
   "**挂账清单不能为空**",防的是"把清单清空让比对变绿"。本次那 5 条**合法地**全部销账
   ⇒ 改成"**恰为空**"。滥用场景仍被兜住、而且兜得更紧:契约里有、实现里没有的端点会落进
   `unexpectedMissing`,而它在 `ContractImplDiff` 里**直接判红**(§36)——
   清空清单只会让比对更红。新断言留了同向的绊索:将来真挂新缺口,它会红。

### 58.7 销账,以及一处**失效引用**的纠正

- `contract-impl-gaps.yaml` 的 `knownGaps` 里那 5 条**全部销账**(留着会被判"挂账已过期");
  两个只读接口补进 `canaries` 当锚点。
- ⚠️ 顺手纠正:**那 5 条的 `source` 引的是「PRD §10.3」,而 PRD 根本没有 §10.3**
  (PRD §10 是「边界与假设」)。真正的出处是**架构 §10.3**,且那里只给了资源前缀。
  缺口文件里已标注说明。

### 58.8 实测

| 项 | 结果 |
| --- | --- |
| Java 集成测试 | **603 passed / 0 failed**(基线 583 + 本次 **+20**:`AdminAccountIntegrationTest` 12 条、`AdminLogIntegrationTest` 8 条) |
| 前端 | **381 passed / 29 文件**(基线 356 + 本次 **+25**:两个 spec) |
| 契约↔实现比对 | 两侧干净(意外缺失 0、多余路由 0、挂账已过期 0) |
| 7 个门禁 | **全过**(G1–G7) |

**真机冒烟(超管身份,按"只读 → 写 → 删"的顺序)**

- **读**:账号列表分页正常;日志 **5332 条**、`detail` 是**对象**、`adminUsername` 富化出 `admin`。
- **写**:建号成功;用户名重复 → **20003**;不带密码 / 密码 3 位 → **20004**;
  **编辑不带密码后,用原密码仍能登录** ⇒ 密码没被覆盖。
- **防呆三条都被真的拒绝,且库里什么都没变**:停用自己 → 30002「不能停用自己」;
  **把自己降级成运营 → 30002「系统必须保留至少一个启用的超级管理员」**;
  删除自己 → 30002「不能删除自己」。确认是 **HTTP 200 + `code=30002`**。
- **删**:真删成功、列表回到 1 个账号。
- **"已删除账号"兜底有真实数据**:全库前 100 条日志里 **35 条**的操作人账号已不存在
  (如 `adminId=755`),前端会显示「已删除账号 #755」—— 不需要造假数据。

### 58.9 三个环境坑(都很容易误判成代码问题)

1. **跑全量集成测试前必须停掉本机的 Python 消费端。** 否则 `GenerateIntegrationTest` 里那两条
   "消息真的进了 `queue.generate`"会红 —— 消息**被消费端吃掉了**,测试等 30 秒也等不到。
   实测:带消费端跑 **2 红**;停掉后同一条命令 **25/25 全过**,耗时从 63 秒降到 12.77 秒。
   **这是环境干扰,不是回归。**
2. **集成测试要显式给 `DB_PASSWORD`。** 测试跑默认 profile、读环境变量;而本机口令在
   `application-local.yml`(只有 local profile 才加载)。不给就是 `CannotGetJdbcConnectionException`
   —— 看起来像"库挂了"。
3. **门禁要用 Git 的 bash,不是 PATH 里的那个。** 本机 `bash` 指向 **WSL**
   (`C:\Windows\system32\bash.exe`),它认 `/mnt/d` 不认 `/d` ⇒ 脚本里的
   `JDK_HOME_HINT=/d/dev-tools/jdk17` 找不到、`mvn` 报 "JAVA_HOME is not defined correctly"。
   用 **`D:\软件\Git\bin\bash.exe`**(CLAUDE.md 写的那个)并把 `JAVA_HOME` 留空交给脚本自己设即可。

### 58.10 浏览器复验(2026-10-03)

58.8 的冒烟是**接口层**做的。补上人眼那一趟:操作者在真实浏览器里登录后台
(`http://localhost:5173/admin/login`),「账号与权限」与「操作日志」两个页面**打开正常、
数据正常显示,无问题**。

⚠️ **这次浏览器复验有两个"没验到"要说清**:

1. 两条防呆**没有在界面上逐个点过** —— 它们是接口层验的(三条都被真的拒绝,见 58.8)。
   界面上"点了会被拒"这一层,若要签得更硬,建议真点一次。
2. 浏览器里**只看了页面能不能开、数据对不对**,没有走"新增/编辑/删除"的完整交互。

**顺带记两个把操作者卡住的坑**(都不是 bug,是两套体系/绑定地址的必然结果):

- **后台账号在 C 端登录页认不出来**。`admin` 是**后台**账号,C 端的用户表里没有它 ——
  拿它去 C 端登录会得到 **20005 账号或密码错误**(实测确认)。后台入口是
  **`/admin/login`**,与前台首页的登录是**两套账号体系**。
- **`127.0.0.1:5173` 打不开**。Vite 只绑本机 IPv6(`::1`),而 `127.0.0.1` 是 IPv4 ⇒
  连接被拒。必须用 `localhost`(实测确认)。

---

## 59. 多角度效果图:点缩略图换主图(2026-10-03)

### 59.1 一句话

**用户报的问题**:AI 生成的多角度图被压成 8rem×6rem 的小块,而且**点了没反应** ——
想看另一个角度只能眯着眼看小图。

**改法**:三个页面(生成结果 / 方案详情 / **分享页**)**照商品详情页已有的模式**改 ——
点缩略图就把它换到主图位置显示大图;缩略图列出**全部**图(含当前那张),
所以点回封面也有路。

### 59.2 为什么是"照商品详情页",不是新做放大浮层

商品详情页(`ProductDetailView.vue`)早就是 `activeImage` + `thumb--active` 这一套
(点缩略图换主图)。**同一个交互在同一个项目里有两套实现,迟早分叉** ——
而这套已经跑在前台页面上、用户也熟悉。

**没有做全屏放大浮层**:项目里没有任何弹窗/Overlay 组件,这个需求也没被提出过。
真要看原始尺寸(生成图是 1024px 级,页面上受列宽限制)是另一个需求,随时可加。

### 59.3 一处必须一起改的文档口径:**分享页的"整页 0 个按钮"**

分享页有一条长期存在的设计约束:**"一个管理动作都不能有"**(§45 四条边界之一),
落地的断言写成了 `findAll('button')).toHaveLength(0)`。

缩略图改成可点之后,**页面合法地有了按钮**。这里必须把两件事分开:

| | 原来的断言想守的 | 实际该守的 |
| --- | --- | --- |
| 目的 | 不让看的人**改动任何东西** | 同左 |
| 手段 | "一个 button 都没有" | "除了**切换视角**,没有别的按钮" |

⇒ 断言改成**允许清单**:每个按钮的文字必须匹配 `正面 / 角度 N`,出现任何别的按钮即红。
这比原来更强 —— 原来只数个数,加一个伪装成链接的管理入口它也照过;
而且保留了"逼人显式更新断言"的性质。

⚠️ **只改口径,不改约束**:分享页依然一个管理动作都没有。

### 59.4 一个"改错了不报错"的点

`selected`(用户点开的那张)存的是**图片对象**。而:**生成页重新生成会换掉整组图**、
方案详情页换方案、分享页换令牌时**组件实例都会被复用** ——
三种情况下 `selected` 里留着的都是**上一轮**的图。

所以 `active` 不是简单的 `selected ?? cover`,而是:
**点过的那张、且它还在当前这组图里**才用它,否则退回封面。

少了"还在不在"这个判断,主图会显示**上一份方案 / 上一轮生成**的图,而且**不报错**
(用户看到的现象是"重新生成完,画面没变")。

### 59.5 验证

- 前端 **384 passed**(+3:方案详情两条 —— 换主图、换方案后不残留;分享页一条;
  生成页在原有的 `angles=2` 用例里加了交互断言)
- `npm run build`(含 vue-tsc)通过
- 被改口径的那条分享页断言**仍然会红**:加任何非视角按钮都会失败(允许清单)
- 涉及页面:生成结果页 / 方案详情页 / 分享页(商品详情页本来就是对的,未动)

---

## 60. 实测:3 张参考图要 84 秒 —— 顶破了「单图 ≤60s」的硬指标(2026-10-03)

### 60.1 现象

用户在浏览器里连续三次生成失败,前端依次看到:

| 第几次 | 参数 | 时间 | 结果 |
| --- | --- | --- | --- |
| 1 | `parts=3 angles=4` | 13:28:58 受理 → 13:30:51 | 读超时 + 对方 **500**(`code 50507 Unknown error`) |
| 2 | `parts=3 angles=4` | 13:34:15 受理 → 13:35:17 | **62 秒**后超时 ⇒ 熔断打开 |
| 3 | `parts=3 angles=1` | 13:38:58 受理 → 13:40:01 | **63 秒**后超时 ⇒ 熔断打开 |

用户最初怀疑是"4 张参考图" —— **不是**。Java 日志写的是 `parts=3 angles=4`:
**4 是角度数(输出张数)**,参考图是"每部位一张" = **3 张**;而且适配器那条
"超过 4 张就截断并 WARN"的日志**没有出现** ⇒ 连上限都没碰到。

### 60.2 定位:**不是故障,是指标顶不住**

**直连硅基流动实测**(绕过本项目网关:不限时、不熔断、不重试;用 `.env` 里同一把密钥、
同一模型、3 张真实商品封面做参考图,`timeout=300s`):

| 场景 | 实测耗时 | 我们的上限 | 结论 |
| --- | --- | --- | --- |
| 2 张参考图(同日 12:36 成功那次,反推) | 约 **43 秒**/张 | 60 秒 | ✅ 赶得上 |
| **3 张参考图(直连实测)** | **84.1 秒**,成功返回 1 张 | 60 秒 | ❌ 每次都被掐断 |

⇒ **模型在正常出图**(直连成功返回),只是**慢**;我们在它画完之前把连接掐了。
**用户自己的判断是对的:"模型正在生成,但没有完成,被我们按超时停止了。"**

### 60.3 记账:这条 60 秒是**按上一个模型**定的

架构 §13.1 / §7.3 写着"单图 ≤60s",`config.py` 的注释更硬:
「图像 60s 是架构 §7.3 / §13.1 的**硬指标**(单图 ≤60s),不可放宽。」

而它是**按万相**实测定的 —— 架构 §15.1 记着万相"单图 24-28 秒",60 秒对那个模型是宽松的。
换成**现在这个模型 + 3 张参考图**就顶破了(而且不是线性:2 张 43 秒 → 3 张 84 秒)。
**这正是 CLAUDE.md 那条「换模型必须重核硬约束」的场景,这次轮到延迟。**

⚠️ **一个连锁反应值得单独记**:超时 → 判失败 → **熔断器打开** → 后续尝试
**连请求都不发**就被拒(前端显示"AI 出图服务当前不可用")。
所以用户先后看到的两句话 —— "响应超时"与"当前不可用" ——
**是同一个原因的两个阶段**,不是两件事。

### 60.4 待决(2026-10-03,尚未拍板)

- **A. 放宽读超时**(如 120 秒):改配置即可(`gateway_image_timeout_seconds`),
  但它公开违反 §13.1 的指标 ⇒ 必须同时重评那条指标,否则文档就是假的。
- **B. 参考图减到 2 张**:实测能进 60 秒,但会让"多砖多部位"能力收窄(架构 §15.3)。
- **C. 模型调用改异步**(提交 → 轮询):治本,而且架构 §7.3 本来就写着"**异步优先**"
  —— 当前实现是"同步调用 + 60 秒读超时",与那条设计意图有落差。工作量最大。

### 60.5 处置(2026-10-03):本地放宽读超时,**连带两个 Java 阈值一起改**

**用户裁决:根在超时,不在熔断** —— 熔断是果。所以只放宽读超时,
**熔断参数(30 秒)按原样不动**。

| 改哪 | 从 → 到 | 在哪 |
| --- | --- | --- |
| Python 读超时 | 60s → **600s** | `tile-ai-agent/.env` 的 `GATEWAY_IMAGE_TIMEOUT_SECONDS` |
| running 卡住阈值 | 360s → **60m** | `application-local.yml` 的 `tile.generate.running-stuck-after` |
| 硬截止 | 30m → **120m** | 同上 `tile.generate.hard-deadline` |

**为什么后两个必须跟着改**(少改一个会比现在更糟):`application.yml` 的那行注释写着
`running-stuck-after` 是"**最坏真实耗时(4 张 × 60s)的 1.5 倍**"推出来的 360s。
单张预算变成 600s 之后,4 个角度最坏 **40 分钟** ⇒ 补偿扫描会在**第 6 分钟**就把一个
**正在正常出图**的任务判成"卡住"并重投。阈值不跟着走,等于把"超时失败"换成
"重复出图 + 状态打架"。

⚠️ **这三处都是本地覆盖**(两个文件都已 gitignore)——
`application.yml` 与 `config.py` 的**项目默认值(60s / 360s / 30m)一个字没动**。
因为"指标要不要重评"(§60.4 的 A/B/C)**仍然待决**;等那个决定落地,
再把默认值、架构 §13.1 与 §35.1 一起改,否则文档就会自相矛盾。

**验证**:Python 侧打印读到 `600.0`;Java 侧键名与 `GenerateProperties` 的字段
(`runningStuckAfter` / `hardDeadline`)逐一核对,relaxed binding 对得上,启动无 YAML 报错。

⚠️ **一个还没解决的体验问题(记在案)**:读超时放宽之后,单张图**最坏要等 10 分钟**,
而进度回调目前只有 0 与 100 两个值 —— 用户会盯着一个不动的转圈等很久。
这不是本次引入的,但这次把它放大了。

---

## 61. 出图提示词加固:部位绑定、场景锚点、产品名(2026-10-03)

### 61.1 症状(用户报的)

> "大模型容易把选中的砖**不按描述画在对应位置**,而且生成的图片**根本不是同一个场景**。"

### 61.2 三个**具体的**原因(不是"提示词写得不够好"这种空话)

1. **部位行可以是完全相同的文字。** 部位行原先只写材质属性
   (`material/surface/colorPattern/spec`),而这些属性**可以完全一样**。
   2026-10-03 用真实商品实测:三块砖的短语都是
   `瓷质抛釉砖,高透亮面,仿石,750×1500mm` —— **三行一模一样**,
   提示词层面根本没有任何信息能说明"哪块砖铺哪里"。
   ⇒ 补上**产品名**(`丝路雪影` / `白雪公主` / `菲特米黄`),它是唯一可区分的记号。
2. **参考图与部位没有任何对应说明。** 我们一次发 N 张参考图,而提示词只按文字
   罗列各部位的砖 —— **没有一句把"第几张图"和"哪个部位"连起来**。
   模型手上是一堆图 + 一串文字,对应关系只能靠猜。这正是"砖画错部位"的直接原因。
   ⇒ 部位行改写成 `地面:铺贴参考图 1 的瓷砖(…)`,
   并在末尾加一句可执行的禁令(不得互换、不得混用、不要把砖画成样品/拼贴)。
3. **尺寸从来没进过提示词。** 约束里写着"空间结构与**给定户型尺寸**一致",
   但 `assemble_prompt` 从来没拿到过 `layout` —— **那句约束当时兑现不了**,
   模型对房间大小一无所知,每次自由发挥 ⇒ "每次像不同的房间"。
   ⇒ 从消息体的 `layout.spaces` 里挑出本次那间,把
   `长约 2 米,宽 1.8 米,层高 2.4 米` 写进提示词。

另外把"多角度必须是同一间房"从视角句的括号里**拆成独立一行**
(`多角度一致性:…`)—— 它约束的是整张图(房间、陈设、每块砖的位置),
贴着"视角"写容易被读成只管机位。

### 61.3 边界:不改契约、不发明新成分

- §7.3 的组装公式(空间 + 风格 + 各部位材质 + 陈设 + 视角)与顺序**不变**;
  新增的"场景句"是对**空间类型**这一项的展开,"绑定句/约束句"是对**约束**的展开。
- `PartMaterial.name` **不进契约**:`to_contract()` 里没有它
  (material-map 的响应形状一个字段都没加)。有一条测试专门钉住这件事。
- `ref_index` / `room` 都是**可选**参数:编排层拿不到时,输出退化成扩写前的形状
  (只是措辞更强),不会因为少一个字段就拼不出提示词。

### 61.4 ⚠️ 没有验证的那一半

**提示词改完,**"模型会不会照做"没人知道** —— 用户明确说了不需要跑真实出图测试。
本次能证明的只有:① 提示词按设计拼出来了(单测);
② 真实数据下编号与尺寸确实进了文本(用真商品拼了一遍,见 61.2 的例子)。

**画得像不像、砖贴得对不对,要等下一次真实生成才能判断。**
若仍不对,下一步的候选是"把首张生成结果作为后续角度的参考图"(治"不是同一个房间"),
但那超出提示词范围,不在本次。

### 61.5 顺带修掉的一处回归(与本任务无关,但同在本次会话)

配服务令牌(§58)之后,**Python 侧 30 条端点测试集体变红**:
`test_layout_parse` / `test_complete_parts` / `test_chat_stream` 里的
`TestClient` 请求没带令牌头 ⇒ 401 ⇒ 响应体里没有我们的信封 ⇒ `KeyError`。
根因是那些测试**默默依赖"开发机没配令牌"**,而不是依赖接口行为。

⇒ 新增 `tests/conftest.py`,用 autouse 夹具把闸门在测试里**显式**置空。
闸门本身仍由三个文件里的 `test_endpoint_enforces_service_token_when_configured`
单独覆盖(它们自己 monkeypatch,不受影响)。
**Python:316 passed / 11 skipped**(此前 307;本次 +6 提示词 +3 编排)。

> 教训与 §58.9 同类:**测试不该依赖开发机的 `.env`**。
> 那一节记的是"跑测试要先停消费端",这一条是"跑测试别让本机令牌把门关上"。

---

## 62. "生成服务长时间无响应"是误报:排队被判成卡住 + 消费端线程会死(2026-10-03)

### 62.1 症状

用户报:"生成服务长时间无响应,请稍后重试"。**先排除超时** —— §60 那条已经修好了:
同日 13:51 那次 **4 张图成功、耗时 4 分 44 秒**(`planId=1778`)。

### 62.2 实测时间线

| 时间 | 事件 |
| --- | --- |
| 13:51:13 | 任务 A 受理 |
| ~13:52 | **长调用期间 RabbitMQ 连接被掐断**(`StreamLostError 10053`)⇒ ack 失败 ⇒ 消息被重投 |
| 13:55:57 | 任务 A 完成(4 张图,4 分 44 秒) |
| 14:09:16 | 任务 B 受理 —— 但消费端正忙着跑任务 A 的**重投** |
| 14:10:41 | 扫描器:任务 B「`pending` 已 85 秒未更新」⇒ **重投第 2 次** |
| 14:11:01 | 任务 A 的重复回调被忽略(幂等挡住了,但白烧一次模型调用) |
| 14:11:42 / 14:12:42 | 再重投 ⇒ 达上限 ⇒ **判 failed**(用户看到的那句) |

### 62.3 两个根因(**都是"单消费端 + 单张图最长 600 秒"的连带后果**)

**① `pending-stuck-after: 60s` 把"排队"判成了"卡住"。**
那是按"一张图 45 秒"的时代定的;而 4 角度 × 最长 600 秒的任务可以占住消费端 **4~40 分钟**,
排在后面的任务在这期间**只能等**,却会被重投、最后判失败。
⇒ **本地覆盖改成 45 分钟**(最坏 4 × 600s = 40 分钟 + 余量);`application.yml` 的默认值没动。

> ⚠️ 记一笔操作教训:改的时候**缩进写错了**(2 空格),落成 `tile.pending-stuck-after`
> 而不是 `tile.generate.pending-stuck-after` —— **Spring 会静默忽略它,配置等于没改**。
> 是事后用 YAML 解析器核对结构才发现的。**改 Spring 配置后必须核对层级,不能只看"文件里有这行"。**

**② 消费端线程会死,而且死得毫无征兆。**
断线之后消息处理路径要走重投,而重投的 `basic_publish` 在**已关闭的 channel** 上抛
`ChannelWrongStateError` ⇒ 异常冒出 `_on_message` ⇒ `run_forever` 只接 `AMQPError`、
接不住它 ⇒ **消费者线程终止**。而 daemon 线程的死亡**不影响进程**:
HTTP 还活着、`/health` 照样 `{"status":"UP"}` ⇒ **服务看起来正常,却一个任务都不再处理**。

实测后果:队列里堆了 4 条没人接的消息,后续每一次生成都静静躺着。
(§54 那条"受理前探测消费端"正是为这类假活准备的 —— 它会当场 40002。)

### 62.4 修法

**两个消费端是同一份骨架,同一处缺陷,一并修**(`app/generate/consumer.py`、`app/index/consumer.py`):

1. **重投不再让异常逃逸**:`basic_publish` + `basic_ack` 整段 `try/except AMQPError`。
   连接已断时**什么也别做** —— 消息没 ACK,连接恢复后 broker 会重投它。
   这比"假装重投成功"安全:最坏是重复处理一次,而重复有幂等兜底。
2. **`run_forever` 接住任何异常**:原先只接 `pika.exceptions.AMQPError`,
   非 AMQP 的异常会直接终结循环。长期循环的第一职责是"活着"。

**负向对照**(证明断言不是摆设):把重投的 `try` 退回旧写法,新用例里的
`ChannelWrongStateError` **确实冒了出去** —— 即这三条回归测试真的抓得住它。

### 62.5 验证与遗留

- Python **319 passed / 11 skipped**(+3 条回归)
- 重启后 `queue.generate` 的 `consumers=1` ✓

⚠️ **仍没解决的:连接到底为什么会断。** 消费端在同步等模型时**没法给 broker 发心跳**
(最长 600 秒),broker 就把它当死连接掐了 —— 这是"同一单跑两遍"的来源。
两条候选路:**(a)** 调大/关掉两侧心跳(要动 broker 配置);
**(b)** 长调用放到工作线程、主线程继续泵事件(改动更大)。
**待决**,不在本次。

⚠️ **顺带确认的一件既存事项**:`queue.index.sync` 堆了 **65 条**、
`consumers=0` —— 因为**索引消费端从不随服务启动**(§3030 / §5548 记着的待决项:
"由谁托管它是个部署决定")。本次没有改变这一点,但要说清它的实际后果:
**向量索引是旧的**,AI 咨询的检索质量受此影响。

---

## 63. 心跳 60 秒 vs 单次调用 600 秒:同一单跑两遍的根因(2026-10-03)

### 63.1 病因

消费端是**同步阻塞**的:`_on_message` 里直接调模型,一次最长可阻塞 **600 秒**
(`gateway_image_timeout_seconds`,§60 刚放宽的)。而 pika 的心跳是由 I/O 循环发的 ——
**它阻塞在模型调用上的那几分钟里,一个心跳也发不出去**。

broker 的默认心跳是 **60 秒**(实测连接协商值 `timeout=60`),于是:
**连接被 broker 当死连接掐掉 ⇒ 消息未 ACK 被重投 ⇒ 同一单跑两遍、白花一次模型调用。**

实测证据(§62.2 的时间线):一次 4 角度的任务跑了 **4 分 44 秒**,
期间连接就是那么断的(`StreamLostError 10053`),那次任务因此**跑了两遍**
(第二次的重复回调被幂等挡下,但模型额度已经花了)。

### 63.2 修法:两侧都要放宽(**只改一边等于没改**)

心跳是**协商值**,取 `min(客户端, 服务端)`:

| 在哪 | 改什么 | 值 |
| --- | --- | --- |
| 客户端(消费端) | `settings.rabbitmq_heartbeat_seconds` → `parameters.heartbeat` | **900** |
| 服务端(broker) | `docker-compose.dev.yml` 的 `RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS` | **900** |

默认值 900 覆盖 `gateway_image_timeout_seconds`(600)并留余量。
有一条回归测试把这个**不变量**写成了断言:**心跳必须 > 单次模型调用的上限** ——
两个数字以后各改各的,那条会红。

**Java 侧不用动**:它提的是 60 秒,取 min 后仍是 60 —— 而它的 broker 操作都是短操作。

**验证**:broker `application:get_env(rabbit, heartbeat)` = `{ok,900}`;
pika 连接的协商值 = **900 秒**;Java 连接仍是 60 秒(符合预期)。

### 63.3 ⚠️ 处理过程中踩到的坑:**重启 broker 让 durable 队列"消失"**

为了应用 broker 侧的心跳,重建了 rabbitmq 容器 —— 结果**四个队列连同里面的 65 条消息
全都不见了**,而它们是 `QueueBuilder.durable(...)` 声明的、本该活得下来。

根因:**RabbitMQ 的节点身份默认取容器主机名,而数据目录按节点名分开。**
`docker compose up`(改任何一个环境变量都会重建容器)会换掉主机名 ⇒ 新节点
`rabbit@87ef493795da` 看到一个空目录 ⇒ 看起来"数据没了",其实**旧数据完好地躺在卷里**
(`rabbit@74b37757b8ef`,已确认)。

**修法:把主机名钉死** —— `docker-compose.dev.yml` 里加 `hostname: tile-ai-rabbitmq`。
之后节点名稳定为 `rabbit@tile-ai-rabbitmq`,重建不再"失忆"。

**那 65 条的实际损失**:它们是「商品/搭配变更 → 索引同步」事件。
**它们本来就没被消费过**(§62.5:索引消费端从不启动),索引早已陈旧 ⇒
按架构 §15.2 的设计,**全量重建才是正解**,不是靠补这 65 条。
旧节点目录没有去动(要恢复得改节点名/目录名,风险大于收益);
若哪天确要手工捞,它在卷的 `mnesia/rabbit@74b37757b8ef` 下。

### 63.4 ⚠️ 顺带发现的一处脆弱:队列是"懒声明"的

broker 重启之后,**Java 启动时并不建连、也不声明拓扑** ——
声明发生在"**第一次真的要用 broker**"时(`RabbitAdmin` 的建连回调)。
所以那段时间里 `queue.generate` 根本不存在,pika 只能一直刷
`404 NOT_FOUND - no queue`。

实测:随手打一次 **`GET /actuator/health`**(它的 Rabbit 健康指示器会建连)⇒
四个队列立刻全部回来,消费端当场挂上(`consumers=1`)。

> **记在案**:这意味着"broker 重启后、下一次生成"这条路上,队列声明与首次投递的顺序
> 值得再确认一次(Spring 的连接监听器是在建连时同步声明的,故**首次投递应当安全** ——
> 但这条**没有实测过**)。

---

## 64. 多角度出图:用首图当后续角度的基准图(2026-10-03)

### 64.1 一句话

**多角度是四次独立调用,模型互相看不见** —— 四张图"是同一个房间"原先只靠提示词请求,
没有任何机制保证(实测出现过"每张像不同的房间",§61.2)。本次**把第一张的产出图,
当作后面几张的参考图**。

**用户裁决(2026-10-03)**:① 后面几张**只发首图 1 张**,不再带瓷砖切片;
② 首图失败 → **整单失败**;③ 视角沿用代码里**已经写死的四句**,不让模型自由发挥。

### 64.2 与架构的关系:**把 §15.3 那个空着的「底图」位置填上**

架构 §15.3 原文要求参考图是「**同空间类型底图 1 张 + 各部位瓷砖切片最多 3 张 = 最多 4 张**」。
而实现**一直只发切片、不发底图**(暂停点里记着的既存缺口)。
本次在"多角度"这条路上,把**底图 = 首张生成结果**。

⚠️ 这**不等于**补上了户型底图:首图仍是"照着几块砖想象出来的一个房间",
保证的是"**四张是同一间房**",不是"**这间房就是你家那间**"。

### 64.3 一处必须一起改、不改也不报错的地方:**提示词的两种形态**

改之前,每个视角的提示词里都写着「地面：**铺贴参考图 1 的**瓷砖（…）」+ 一句绑定禁令
(§61 加的)。这个编号的含义是"第几张参考图对应哪个部位"。

**参考图清单一旦变成"只有 1 张房间图",这句话就成了假话** —— 参考图 1 是整间房,
不是地面砖。而**代码、YAML、测试都不会报错**,只会把一句错误指令喂给模型。

⇒ 提示词分**两种形态**,由 `assemble_prompt(base_image=...)` 选择,**互斥**:

| | 第 1 张(切片形态) | 第 2 张起(基准图形态) |
| --- | --- | --- |
| 部位行 | `地面：铺贴参考图 1 的瓷砖（…）` | `地面：铺贴瓷砖（…）` —— **去掉编号** |
| 收口句 | `REF_BINDING_CLAUSE` | `BASE_IMAGE_CLAUSE`(「以参考图 1 为该空间的基准图…」) |
| 材质/产品名 | 保留 | **保留** —— 丢的只是编号,不是"铺的是什么砖" |

`angles=1` 时两种形态等价,**行为与改动前逐字相同**(有回归测试钉住)。

### 64.4 ⚠️ 探针(第 0 步)与它顺带炸出来的一个问题

改代码之前先验了**单点前提**:厂商产出的**签名临时地址**(实测查询串 1452 字符,
`X-Amz-Expires=86400`)**能不能回灌当输入图**。

| 请求 | 结果 |
| --- | --- |
| 纯文字(不带输入图) | ❌ **HTTP 400 `20015 Missing required key: image`** |
| 带一张公开图 | ✅ 200,拿到产出图 |
| **把产出图 URL 当输入图再发** | ✅ **200** ⇒ 前提成立 |

⚠️ **但第 1 行是个独立的既存问题**(本次**未修**,超出本次范围):

`orchestrator.py` 那句注释「没有参考图也能出图(**退化成文生图**)」对**现在这个模型是假的**
—— 它**硬性要求输入图**。所以"一张参考图都没有"时,实际结果是**必然 HTTP 400 → 整单失败**,
不是降级。

触发条件真实存在:所选砖**一张照片都没有**(无封面、相册也空),或 SKU 详情取不到。
⇒ **待决**(见 64.7)。

### 64.5 改了什么

| 文件 | 改动 |
| --- | --- |
| `app/agent/prompt.py` | 新增 `BASE_IMAGE_CLAUSE`;`assemble_prompt` 加 `base_image`;`build_prompts` 加 `anchor_first_view`(只第 1 个视角走切片形态);`prepare_prompts` 透传 |
| `app/generate/orchestrator.py` | 循环里按视角换参考图:第 1 张发瓷砖切片,第 2 张起发 `[首图 url]`;加一条 INFO 日志 |
| `app/generate/consumer.py` | 把 `settings.image_anchor_first_view` 传进编排层 |
| `app/core/config.py` | 新增开关 `image_anchor_first_view`(默认 **True**)—— 便于一键回退 / 做对照 |

**契约零改动**:没有新字段、没有新路径,`openapi-*.yaml` 一个字没动。

**失败语义不需要新代码**:"整单失败"**本来就是现有行为** —— 循环里任何一张出异常 /
返回 0 张 / 没有 URL,都会冒泡到 `except` → 回调 `failed(40001)` → `raise`,
已入列的图一并丢弃。用户选的正是现状。

### 64.6 验证

- **Python 全量**:329 passed / 11 skipped(改前 319;**本次 +10**)
- **负向对照**(证明断言不是摆设):把部位行的编号改回"始终报编号"后,
  三条用例**立刻变红**,报错信息正好是把那句假话打了出来
  (`地面：铺贴参考图 1 的瓷砖（…）` 出现在基准图形态的提示词里)
- **门禁**:`contract-diff.sh` 干净;`ci.sh` **G1–G7 全 PASS**

### 64.7 ⚠️ 没有验证的那一半(别读成"已完成")

1. **真机一次都没跑过。** 单测只能证明"**参考图发对了、提示词拼对了**",
   证明不了"**模型会照做**"。
   ⇒ **下一步就是跑一次 `angles=4` 的真实生成,只看一件事:四张是不是同一个房间。**
   是 → 本条成立;不是 → 关掉开关,记进本节,回头再想(候选:改用真正的房间底图)。
2. **首图的错误会被继承并放大** —— 第一张把砖画错部位,后面三张会照着错的画。
   这是本方案的**固有代价**,不是 bug。
3. **瓷砖纹理可能失真** —— 后面几张不再拿原厂照片,纹理靠模型从首图还原。
   若实测发现变色/糊,退路是改成"首图 + 切片"(但会顶到 4 张配额)。
4. **64.4 那个 `image` 必填问题未修,也未拍板怎么修。**

### 64.8 与 §61.4 的关系

§61.4 记着"提示词改完,**模型会不会照做没人知道**",并把"把首张生成结果作为后续角度的
参考图"列为候选 —— 本次**正是做掉了那个候选**。但**同样没验证**:
§61 那半和这半,都要靠 64.7 第 1 条那一次真机生成一起回答。

### 64.9 真机跑过了(2026-10-03):**出图仍然乱画 —— 本次没有达到目的**

**真机跑了**(Python 服务重启后,由用户在真实浏览器里操作)。**用户反馈原话:
"大模型出图还是乱画"。**

⚠️ **这条反馈没有回答本方案的具体判据**(64.7 第 1 条问的是"**四张是不是同一个房间**"),
所以它**不能**用来断定"基准图无效",只能断定:**整体出图质量仍然不可接受。**
把这两件事混为一谈会让下一次判断失去依据。

**还没记到的(以后要补,别当成已知)**:这次的角度数、四张是否同房、总耗时
(改前 vs 改后 —— 基准图形每张只发 1 张参考图,按 §60 的实测本该更快)。

⇒ **用户 2026-10-03 决定:先记录,不在本轮继续追。** 开关 `image_anchor_first_view`
的去留见 64.10。

### 64.10 开关的处置(2026-10-03)

`image_anchor_first_view` **保持开启**(默认值不变)。

**为什么不是按 64.7 的原计划"关掉"**:那条计划写的是"判据为**否**就关掉",
而本次拿到的是"**整体乱画**",不是"四张不是同一个房间" —— **判据本身没被回答**,
所以"关掉"缺少依据。关掉它也不会让乱画变好(它只影响四张之间的一致性)。

⇒ 待办与 64.9 的补充项合并:下次做真机验证时,**先看清四张是不是同一间房**,
再决定去留。真正的瓶颈更可能在**出图质量**那一层(架构 §15.3 的"坏图率",
至今一次都没评过),而不是本节的视角一致性。

---

## 65. 前端视觉与工程规范落地:五份文档 + 引入 antd + 一次范围收紧(2026-10-03)

本节记录**与契约无关**的一轮决策 —— 目的是让"前端要重做"这件事**在文档链上有据可查**,
否则下次会话读到旧铁律会与现状打架。

> **契约零改动**:本轮不碰 PRD / 架构 / 4 份 OpenAPI / AsyncAPI。前端规范**不是**契约链的一环。

### 65.1 落了哪些文档

| 文档 | 管什么 |
| --- | --- |
| `tile-ai-design.md` | 视觉设计规范(**令牌的值**、双主题、改造范围) |
| `tile-ai-frontend-stack.md` | 技术选型与依赖准入(**用什么搭**) |
| `tile-ai-frontend-structure.md` | 目录 / 模块 / 组件组织(**放哪、谁依赖谁**) |
| `tile-ai-style-foundation.md` | 令牌机制 / 主题规则 / 多语言准备 |
| `tile-ai-ui-rollout-plan.md` | **实施计划**(业务逻辑冻结下的落地步骤) |

配了四个 Claude Skill(`.claude/skills/` 下)作为执行手册。

### 65.2 用户 2026-10-03 的拍板

| # | 项 | 选择 |
| --- | --- | --- |
| 1 | C 端视觉气质 | **暖石陶土**(陶土 `#B4552F` + 米白底 + 材质感) |
| 2 | 视觉对标 | **装修工具感** —— 图为主,但**参数 / 报价 / 规格同屏**(非杂志风) |
| 3 | 改造深度 | **全站重做**;后**收紧为"只改布局与视觉,业务逻辑冻结"**(见 65.5) |
| 4 | 组件策略 | **引入 Ant Design Vue 4.x**,**手动按需**引入(不引自动导入插件) |
| 5 | 时间线 | 两个月以上,按完整范围执行 |
| 6 | 分享预览卡片 | **要做**(社交平台 OG / SSR)—— 立为**独立工作流**,不混入样式改造 |

### 65.3 ⚠️ 两处对既有约定的**明确推翻**(不是遗忘,是拍板)

1. **推翻了铁律「别重做已完成页面」**
   (出处:本仓 `CLAUDE.md` 当前阶段一节、`tile-ai-roadmap.md` 暂停点)。
   ⇒ 已同步**订正** `CLAUDE.md`(限定为"**布局与视觉**可重做,业务逻辑不动")。

2. **引入了本项目第一个第三方运行时 npm 依赖**(Ant Design Vue)。
   此前项目**为画一个二维码都拒绝引入**第一个第三方依赖(见 §45 附近 / roadmap),
   故这是**同类决策中大得多的一个**,按 `frontend-stack.md` §3 的准入流程单独成条。

### 65.4 多语言的处置:**立规则,不引框架**

`design.md` §11 与 `frontend-stack.md` §11 原写"一期不做国际化",而本轮要求
"多语言规范" —— **正面冲突**。处置:

> **「一期不引 i18n 框架」继续有效**(引框架要走依赖闸门);
> 只立"让 i18n 将来可能、且现在零成本"的规则(逻辑属性、不写死宽度、
> 数字日期走 `Intl`、文字不进图片),**重活(文案抽 key)推到引框架时**。

两处 §11 的表述已同步指向 `tile-ai-style-foundation.md` §4。

### 65.5 范围收紧:业务逻辑冻结,及由此产生的 7 处冲突

用户随后追加约束:**"只改页面布局与设计 UI,业务逻辑不能动。"**
四份规范原按"全站重做"写成,**天然带着顺手还技术债的倾向**,与之冲突 7 处。
处置原则:**在规范里开"本阶段例外",不改规范本身**(两者是阶段不同,不是矛盾)。

| # | 冲突 | 处置 |
| --- | --- | --- |
| C1 | "表单校验必须用 antd `Form.rules`"(stack §4.1/§7/§8)| **开例外**:校验规则与提示**原样保留**,迁移推到「逻辑阶段」 |
| C2 | `AppForm`/`FormField`"统一校验提示"(design §6.2)| 本阶段**只做外观包装**,不接管校验 |
| C3 | 金额/日期改用 `Intl`(style-foundation §4.2)| 本阶段**延后**(改格式化 = 改输出) |
| C4 | `PlanDetail`"报价可展开看构成"(design §8)| 先确认数据已具备,否则只改呈现 |
| C5 | 决策"全站重做"措辞过宽(design §1)| **限缩**为"布局与视觉重做,业务逻辑不动" |
| C6 | `business` 组件允许依赖 `api`/`stores`(structure §4.1)| 本阶段只抽**展示型**(props 进 / emits 出) |
| C7 | 四份规范未进 `CLAUDE.md` 索引 | 已补(见 65.6) |

**"逻辑没动"的证明手段**(`ui-rollout-plan.md` §4):逐页比对 `<script setup>` 段落零改动、
行为类测试全过、请求 payload 逐字段一致。⇒ **没有基线就没有证明**,故计划里
**阶段 0 强制先取基线**(测试取绿 + 截图 + 抓真实请求)。

**用户追加的执行纪律(2026-10-03)**:「**不能影响整个系统;每做完一个要测试验收**」,
以及「**改造完成后再统一看界面,但不要阻塞开工**」。
已写成 `ui-rollout-plan.md` 的**最高纪律**(纪律 1–7):一步一提交一验收、验不过不进下一步、
出问题先回滚再查。**验收拆成两半** ——
**机器验收**(测试 / `<script>` 零改动 / build)**每步由 AI 独立完成**;
**视觉与链路验收**按纪律 7 **攒到改造完成后由用户统一做**。
⇒ **"看界面"不阻塞开工。** 该纪律高于计划其它所有条款。

### 65.6 ⚠️ 未验证 / 未做(别读成已完成)

1. **Ant Design Vue 与 Vite 8 / Vue 3.5 / vue-tsc 的兼容性未实测** ——
   安装当日必须**先验构建通过再动页面**;不通过就换版本。TS 必须保持 **5.x**。
2. **令牌完整性检查脚本未写** —— 新主题漏覆盖语义令牌会**静默出错**,建议进 CI。
3. **29 个前端测试**:这里曾预测"全站重做必然大面积失效" ——
   **实际未发生(2026-10-03 收官时核实:384/384 全程未红)**。
   原因是改造全程**保住了测试依赖的选择器与顺序**,很多页面只换 `<style>`、
   整个模板一字未动。**风险预测错了方向,结果是好的** —— 详见 §66.3。
4. **`CLAUDE.md` 索引已补**,但 **ESLint 是否引入**仍是**未决策项**
   (仓库当前无任何 lint 配置)。

---

## 66. UI 改造收官:28 个页面全部完成 + 删除死代码 `AdminPlaceholderView.vue`(2026-10-03)

接 §65。本轮把**视觉设计规范落地**执行完毕,并清掉一个历史尾巴。

### 66.1 交付面

| 范围 | 数量 |
| --- | --- |
| C 端页面(`views/site/`) | **12** |
| B 端外壳 + 页面(`layouts/AdminLayout.vue` + `views/admin/`) | **16** |
| 新增样式层 | `src/styles/`(`tokens.css` / `antdTheme.ts` / `index.css`) |
| 主题接线 | `App.vue`(唯一接线点)+ `main.ts`(一行 import) |
| 依赖 | `ant-design-vue@4.2.6` |

**全程不变式**:每步跑 `npm run test` —— **384/384 从未变红**;
每步比对 `<script setup>` —— **逐行零改动**;共享文件单独记账。

### 66.2 ⚠️ 一个必须记下的结果:**antd 引入了,但一个组件都没用上**

除 `antdTheme.ts` 与 `App.vue` 外,**28 个页面没有任何一处 import antd 组件**。
原因反复撞上同一堵墙:

- 多数页面的筛选 / 表单控件**没有测试覆盖** ⇒ 换成 antd 属于
  **不可验证的行为改动**,与本轮"每步可证明"的前提冲突(见 §65.5 的 C8);
- 有覆盖的页面,测试往往**逐字断言原生控件的结构与顺序**
  (如"option 里含 `local` 的那个 `select`")。

⇒ **那 42 kB(gzip)首屏成本已经付了,收益为零。** 需要一次决策:
**A. 补测试后真正迁移控件;或 B. 撤掉 antd、还原 `App.vue` 那一处接线。**(**未决**)

### 66.3 一个风险预测未应验(好事,但也要记)

§65.5 曾预测"全站重做会让 29 个测试大面积失效"。**实际一条都没失效**。
**预测错了方向。** 教训:**把"改结构"与"改外观"分开做,后者的回归面比想象中小得多。**

### 66.4 删除死代码 `AdminPlaceholderView.vue`

**证据链(三重)**:

1. 全仓库 grep `AdminPlaceholder` —— **代码 0 处引用**(仅文档 3 处提及);
2. `router/index.ts` 30 条路由 —— **全部指向真实组件**;
3. git 历史 —— `git log -S AdminPlaceholderView -- router/index.ts` 显示
   **最后一次移除引用是 `72e1dee`(F-0.2 两个后台页面,当时换掉引用但未删文件)**;
   该文件自身**只在最初 `83527ad` 被创建过,之后再没被碰**。

⇒ **自 2026-10-03 起即为死代码**。已删除文件,并同步 `tile-ai-design.md` §8。

⚠️ **§50.1 那条"没删 —— 还有 5 处在用"的记录不改**(它是 2026-10-02 的实况,
符合"历史记录只追加"的规矩);本节的用意就是给它一个**后来的**注脚。

### 66.5 仍未做 / 未验证

1. **视觉验收未做** —— 按 §65.5 纪律 7 攒到最后,**现在到了该看的时候**(需起服务)。
2. **antd 的去向未决**(见 66.2)。
3. 令牌完整性检查脚本、是否引入 ESLint —— 仍为**未决策项**。
4. **改动全部未提交**(用户 2026-10-03 决定:先做完全部页面再考虑提交)。

---

## 67. 视觉验收第一轮:28 个页面改完,却漏了整个 C 端外壳(2026-10-03)

§66 收官后,用户**在真浏览器里点开**,当场指出两条:**「前端界面没有背景板」**
与 **「上方目录没有修饰」**。核实结果:**属实,而且是本轮漏改。**

### 67.1 缺口是什么

| 检查 | 结果 |
| --- | --- |
| `src/layouts/` 的改动情况 | **只有 `AdminLayout.vue`** —— `SiteLayout.vue` **一次都没碰过** |
| 全项目给 `body` 设过背景吗 | **没有**。`tokens.css` 里定义了 `--color-bg`,但**没有任何元素用它** |
| 唯一用了 `--color-bg` 的地方 | `AdminLayout.vue:73` |

⇒ **C 端是"白底 + 白卡片"** —— 卡片与背景同色,**整套卡片式设计等于看不出来**;
而**后台反倒有背景**(因为给 AdminLayout 加了)。**两侧不一致。**

### 67.2 根因(两条,都要记)

1. **`tile-ai-design.md` §8 的 C 端改造范围表里根本没有 `SiteLayout`** ——
   表里只列了 12 个页面。执行时照着那张表逐页走,**外壳层不在任何一张表上**,
   于是整块没被想起来。⇒ **已补**:§8 显式加入外壳层,并写明
   **"逐页清单不足以覆盖外壳,必须单列"**。
2. **`base.css`(全局背景)在阶段 A 被"推迟到需要时再加"之后,再没回头加。**
   阶段 A 为了"零影响"刻意不加 reset —— 那个判断当时**是对的**;
   但**"推迟"没有变成一条带归属的待办**,于是静默丢失。

### 67.3 顺带发现的第三个问题:顶部有**两条导航栏叠着**

- `App.vue` 有一条全站 nav(瓷砖选购 AI / C 端 / 后台管理),**每条路由都渲染**;
- `SiteLayout` 又渲染一条(瓷砖选购 AI / 选砖 / 推荐搭配 / 问 AI / …)。

两条都叫 `.nav`、都是旧灰蓝、都写着"瓷砖选购 AI"。
**这不只是"没修饰",是重复且割裂** —— 大概率就是用户说"上方目录不对劲"的真正所指。

### 67.4 处置:阶段 F(外壳收口)

| 步 | 做什么 | 影响面 | 验收 |
| --- | --- | --- | --- |
| **F1** | 新增 `styles/base.css`:body 底色 / 文字色 / 字体栈 + `#app` 最小高度;**不引 reset** | **全站** | 构建 + 测试 + 人眼 |
| **F2** | 删 `App.vue` 的全站 nav(与两个 layout 的顶栏重复) | **全站** | 构建 + 测试(两处均**无测试挂载**)+ 人眼 |
| **F3** | `SiteLayout` 重排:吸顶顶栏 / 白底 + 暖色细线 + 轻阴影 / 当前页陶土色 + 2px 指示条 / 账号区 / 页脚 | **C 端全部** | ⚠️ **该外壳无测试,只能人眼** |

计划侧同步:见 `tile-ai-ui-rollout-plan.md` 的 **§0 C9** 与 **阶段 F**。

### 67.5 教训(值得写下来)

**"逐个页面往下改"这种节奏,天然会漏掉"不属于任何页面"的东西** ——
外壳、全局样式、布局容器。

本轮 **28 个页面每一步都验过、每步全绿**,而**恰恰是这种"每步都严谨"
掩盖了"整块没进清单"** —— **没有任何一步会红**。

⇒ **通用对策:改动范围要先按「层」清点**(页面层 / 外壳层 / 全局层),
**而不是只按"页面清单"清点**。已写进 `tile-ai-design.md` §8。

---

## 68. antd 控件迁移立项:真实的成本是「测试钉着旧行为」(2026-10-03)

用户在视觉验收之后要求:**给界面加动态效果**,并明确「**优先使用成熟组件**」。
评估结论:在这个项目里,那句话**具体化为"用 antd 的组件(自带动效)"** ——
而那正好撞上 §66.2 记下的未决问题(antd 引了、一个组件都没用上)。
⇒ **两个问题合并成同一个决定:要不要把控件迁到 antd。**

### 68.1 决定

**迁。** 但**分两批**(理由见 68.3),且**迁移前先落了回滚点**(见 68.4)。

### 68.2 ⚠️ 评估查出的真实成本 —— **这条推翻了 §65.5 的 C8 判断**

C8 当初把控件迁移推迟,理由是「**无测试覆盖 ⇒ 不可验证**」。
**实际查下来不是这样,而且更麻烦:是有测试覆盖,而它们钉着旧行为。**

| 想换成 | 现有测试钉着什么 | 结果 |
| --- | --- | --- |
| `Modal`(替 `window.confirm`) | **8 个测试文件**断言 `window.confirm` **被调用过**,并断言「**点取消就不执行**」 | **必红** |
| `Tabs`(替页签) | `button.tab`、按文案找 `button` | **必红** |
| `Message` / `Notification`(替提示块) | `.notice` 文本、`[role="alert"]` —— 而 antd 渲染到 **portal**,`wrapper` 里找不到 | **必红** |
| `Spin` / `Skeleton`(替「加载中…」) | 有页面断言这段文案 | **必红** |
| `Table`(替后台表格) | `tbody tr` / `.part-table` / `.num` / `.tag` | 列结构一变就红 |

⇒ **真实成本 = 重写相当一部分测试**,不是「顺手把控件换掉」。
而项目自己的规矩是**「不许靠删断言迁就」** —— 那些断言得**真的重写**
(含学会在 happy-dom 里驱动 antd 的 portal)。

**C8 的结论碰巧是对的,理由错了。** 已在
`tile-ai-ui-rollout-plan.md` 的 C8 行就地注明。

### 68.3 处置:分两批,第一批零风险

| 批 | 范围 | 测试风险 |
| --- | --- | --- |
| **G1** | **无测试覆盖**的页面先试:`ComboListView` · `StoreView` · `AdminHomeView` · `AdminProductEditView` | **零** —— 先回答「antd 在这套主题下好不好看、动效对不对」 |
| **G2** | 逐页迁**有测试**的页面,**每页同步重写受影响的测试** | 每页都要改测试;**这是主要工作量** |

**为什么先 G1**:花极小代价回答「方向对不对」。**万一效果不对,退回成本几乎为零**
—— 而 G2 一旦开工,每页都要碰测试,不适合拿来试水。

### 68.4 与既有记录的关系 / 回滚点

- **回滚点已落**(用户要求「迁移前先提交记录」):三笔独立提交 ——
  `de4099c`(文档)/ `5ca9be5`(UI 改造 37 文件 +4593/−1630)/ `a2864dc`(删死代码)。
  出问题 **`git revert 5ca9be5`** 即可退回改造前,且**文档那一笔独立**,不跟着被撤。
- **`design.md` §11「动画库 / 复杂过渡一期不做」需要精确化**:
  **仍不引第三方动画库**;但**组件自带的过渡**(antd 组件 / Vue 内置 `<Transition>`)
  **纳入范围** —— 那是「用已有能力」,不是「引一个库」。

### 68.5 未决 / 待验

1. **antd 组件在 happy-dom 里怎么测** —— G2 的真正难点,**尚无样板**。
   建议在 G1 之后、G2 之前**先解决一次**(拿一个有测试的页面做样板),否则 G2 会边做边卡。
   ✅ **已解决(2026-10-03)**:配方见 **§70.2**,工具落在 `tile-ai-web/tests/helpers/antd.ts`。
2. **42 kB 的账**:要到 G1 做完才开始**真正兑现**。
3. **`prefers-reduced-motion`**:组件自带动效也要能被系统开关降级 —— **待验**。

---

## 69. 调研高星前端 Agent Skills:**一个都没装**,但抄了两条思路(2026-10-03)

用户要求:「查查 GitHub 上面有没有高星的前端 skills」→ 进而「需要」通读三个候选。

### 69.1 查到的实况(星数取自 GitHub API,非转述)

| 仓库 | 星 | 是什么 | 结论 |
| --- | --- | --- | --- |
| `Leonxlnx/taste-skill` | **92,174** | 「Anti-Slop」前端品味技能,主文件 **87 KB / 1207 行** | ⚠️ **其自述明确排除本项目** |
| `Jakubantalik/transitions.dev` | 4,522 | 扫代码库给"动效评分"的 CLI + 技能 | ⚠️ **付费产品** |
| `arvindrk/extract-design-system` | 231 | 从公开网站反抽设计令牌 | 解决我们没有的问题 |

> 另查到若干"awesome 清单",**本身只有几百星**;而 `anthropics/skills`(官方)179k 星。
> ⚠️ API 返回的部分星数**高得可疑**(如 `mattpocock/skills` 27 万),仅作记录,不背书。

### 69.2 ⭐ 决定性发现:`taste-skill` 自述**不适用于本项目**

它在 SKILL.md 开头第 2 行自己写了适用范围:

> "Landing pages, portfolios, and redesigns. **Not dashboards, not data tables,
> not multi-step product UI.**"

而**本项目正好全中它排除的三样**:后台一堆数据表格;C 端是
「选砖 → 录户型 → 生成 → 方案」的多步骤流程。
⇒ **星最多 ≠ 适用。这一条比什么都重要。**

### 69.3 处置:**一个都不装**,只抄两条思路

| 候选 | 不装的理由 |
| --- | --- |
| `taste-skill` | **自述不适用**(见 69.2);且 87 KB 的 SKILL.md 一加载就吃掉大量上下文 |
| `transitions.dev` | **付费**(修复要 license key,**扫描才免费**);靠 `npx` **下载并执行第三方代码**;**修复会改写我们的文件** |
| `extract-design-system` | 我们**已有**深思熟虑的设计系统;该 skill 自己也写「未经确认不要覆盖已有设计系统」 |

**通用风险(一并记录)**:Skill **就是"给 AI 的指令"**,且**可以携带脚本文件**
—— 安装第三方 skill = 让外部指令进入工作流。对"文档先行、每步可验证"的项目,
这是**供应链风险**,不是装饰性选择。

### 69.4 抄了什么(已落进 `tile-ai-design.md` 与 `frontend-design` Skill)

| # | 内容 | 落点 | 来源 |
| --- | --- | --- | --- |
| 1 | **「设计读场」** —— 动手前先一句话说清「这一页服务谁、他此刻要干什么、下一步去哪」;读错场子后面全白做 | `design.md` **§2.1** + Skill 铁律 8 / 动手前清单 | taste-skill 的 BRIEF INFERENCE |
| 2 | **「禁止清单(AI Tells)」** —— 明令禁止"一眼就是 AI 生成的"套路(等大功能卡、到处玻璃拟态、无限循环动效…) + 本项目专属禁令 | `design.md` **§2.2** + Skill 动手前清单 / 常见误用 | taste-skill 的 AI TELLS |
| 3 | **「动效标尺」** —— `--dur-*` 三档**按用途**选(反馈 120 / 进出 200 / 大块 320),配五条规则 | `design.md` **§3.10** + Skill 铁律 9 / 提交前清单 | transitions.dev 的 motion scale |
| 4 | **「动效降级」** —— `prefers-reduced-motion` 用**极短时长**而非 `none`(否则骨架屏微光被干掉,加载态反而像卡死) | `design.md` **§3.11** | 本项目既有待验项,借来源补齐规则 |

**为什么"抄"比"装"好**:① 抄进来的每条都**能按本项目的规矩回溯**(就是本节);
② 不会让外部指令与已有的 4 个 skill **打架**;③ **零供应链风险、零上下文成本**
—— 87 KB 的 SKILL.md 加载一次就吃掉了,而抄进来的是**四段**,总量不到 2 KB。

### 69.5 未做的

- **没有安装任何第三方 skill。**
- `transitions.dev` 的 **免费只读扫描**(`npx transitions-agent --json`)看起来能给出
  "哪些组件缺动效"的清单 —— 但它要 `npx` 执行第三方代码,**未跑**。若要试,应当**单独决策**。
- 星数**未独立复核**(见 69.1 的警告)。

---

## 70. 阶段 G1 / G1.5 完成:四页迁 antd + **antd 在 happy-dom 里怎么测**(2026-10-03)

### 70.1 G1:四个**无测试**页面迁 antd(零测试风险)

| 页面 | 迁了什么 |
| --- | --- |
| `AdminProductEditView` | 文本 / 数字 / 下拉 / 复选 / 按钮 / **骨架屏** |
| `ComboListView` | `Select`(可清空)· `Button` · `Skeleton` · **`Empty`** |
| `StoreView` | `Skeleton` · `Button`(带 `href`)· `Empty` |
| `AdminHomeView` | **`Descriptions`**(账号事实)· **`Tag`**(权限点:可做=绿 / 不可做=默认灰) |

**验收**:构建通过,`vue-tsc` 抓到一处类型错(antd 数字框的值类型是 `string | number`,
不等于 `number | undefined`)已修;384/384 全绿。

**顺带落地了 §3.11**:`prefers-reduced-motion` 写进 `base.css`,
**用极短时长而非 `none`**(`none` 会把骨架屏微光一起干掉,加载态反而像卡死)。

### 70.2 ⭐ G1.5:antd 组件的测试配方(**三条,全部经实测**)

这一步的产出是 `tile-ai-web/tests/helpers/antd.ts` —— G2 要逐页迁 12 个**有测试的**
页面,每个都会撞上同一个问题,与其每页各写一遍,不如固化一次。

在 `AdminComboEditView` 的**部位下拉**上做了真实迁移 + 测试改写,实测确认:

| # | 规则 | 怎么发现的 |
| --- | --- | --- |
| 1 | **定位靠"测试锚点"** —— 模板上给 antd 组件挂一个稳定 `class`(如 `.part-select`)。别靠层级或序号。 | antd 的 `Select` **没有原生 `<select>`**,旧的 `findAll('select')` + 读 `<option>` **直接失效** |
| 2 | **打开下拉用 `mousedown`,不是 `click`** | ⚠️ **第一版用 `click`,下拉根本没打开** —— `document.body.innerHTML` 是**空的**。换 `mousedown` 后选项才出现 |
| 3 | **portal 里的东西查 `document`,不能用 `wrapper.find`** | 下拉/Modal/Message 都渲染到 `document.body`,而 `@vue/test-utils` 的 `wrapper` **只在组件自己的元素树里搜** |

**一条明确未验的**(写进 helper 的注释了,别当结论):
`mount()` 默认**不把组件挂到 document**。上述写法对此不敏感(portal 挂 body、组件用 wrapper 找);
但若哪天需要 `document.querySelector` 找**组件本身**,就得上 `attachTo: document.body`。**未实测。**

### 70.3 测试是**改写**的,不是删掉的

那条用例(`部位下拉恒为三个值(不走字典)`)的**断言方式换了,断言强度没降**:

- 旧:`findAll('select')` → 找到含 `local` 的那个 → 读它的 `<option>` 值
- 新:`.part-select` 锚点 → `mousedown` 打开 → 读 portal 里的选项文案 → `toEqual(['地面','墙面','局部'])`

**它仍然能证伪**:谁把 `PART_OPTIONS` 改错、或把它改成走字典,这里就红。
**测试总数 384 未变** —— 没有靠删断言迁就(项目规矩)。

### 70.4 打包体积:之前那个"一页 250 kB"是**暂时假象**

| | 只有 1 页用 antd 时 | **4 页用之后** |
| --- | --- | --- |
| 主包 `index` | 182.12 kB | **100.89 kB** ↓ |
| antd 共享块 | (全压在该页里) | `select` **141** · `AntdIcon` **83** · `skeleton` **30** |
| `AdminProductEditView` 自身 | 249.71 kB | **75.57 kB** |

⇒ Vite 把 antd **拆成了按需加载的共享块**(合计约 254 kB / gzip 84 kB),
**只有用到 antd 的页面才加载**。C 端 12 页里目前只有搭配页与门店页付这个钱。

⚠️ **未细查**:`AntdIcon` 那 83 kB 是被谁拉进来的(疑为 `Empty` 的插图,或 `Tag`/`Descriptions`)。

### 70.5 下一步

**G2**:逐页迁**有测试的**页面,每页同步重写受影响的测试。
配方已就位(70.2),但**每页仍要单独验收** —— 因为每页测试钉的东西不同。



