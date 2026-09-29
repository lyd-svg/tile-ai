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

