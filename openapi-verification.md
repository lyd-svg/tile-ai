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
- **开关默认关 —— 已定,不是待办(2026-09-30 用户裁决)。** 常态就是"**改接口后跑一次、
  人看清单**",不自动判红:打开开关等于让任何一次字段漂移都阻断构建,与当前
  "先把功能跑通"的节奏冲突。开关(`GATE=1` / `-Dcontract.diff.gate=true`)作为
  **手动诊断手段**保留 —— 想确认"这两侧到底还有没有分叉"时打开它,红了就是有。
  代价要认清:自动化程度低,靠人记得跑;**这是刻意选的代价,不是遗漏**。

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
| 1 | **真正的 T-12 未落地** —— 不导出实现侧 OpenAPI、不做全结构 diff | 类型 / 必填 / 枚举值 / 格式一律不比 | §36.9;任务卡 T-2.1 进度块 ③⑤ | 导出真 OpenAPI + 双向 diff 跑通,T-12 由「不可执行」转「通过」 |
| 2 | **枚举值漂移 100% 漏掉** | §31.2 那次真实分叉正是这个形态,而**任何门禁都不会变红** | §36.5、§31.2 | 同 #1 |
| 3 | admin `POST /combos`、`/products`、`/templates` 响应实现返回 `Map` | 比对只能记「**跳过**」,不是「通过」 | §36.8 末 | 改实现返回类型化对象,或由 #1 覆盖 |
| 4 | **挂账清单要人工维护** | 新接口未开工就得挂账,否则算「意外缺失」;挂账过期**无条件判红** | §36.4;`tile-ai-server/src/test/resources/contract-impl-gaps.yaml` | 长期随 #1 收进真判据 |
| 5 | **比对默认不判红,靠人跑** | 没人跑 = 没量过 | §36.9、`CLAUDE.md` §三 | **已裁决为常态**,不计划关闭;除非改主意 |

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


