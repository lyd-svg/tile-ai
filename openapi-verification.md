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

