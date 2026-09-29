# 瓷砖选购 AI 效果图与报价系统 — 任务分解清单

## 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | 任务分解清单(Task Breakdown) |
| 版本 | v0.1 |
| 编写日期 | 2026-09-29 |
| 上游文档 | `tile-ai-prd.md` v0.6、`tile-ai-architecture.md` v1.2、4 份 OpenAPI 契约 |
| 状态 | 待评审 |

**用途**:把项目拆为**可独立执行、可独立验证**的最小任务单元。每个任务
自带输入文档、产出物、验证手段与依赖,可单独领取、单独验收,不需要先完成
整条链路才能判断对错。

**拆解原则**:

1. **输入的确定性** —— 每个任务的输入必须是**已冻结的规范文档**。凡是输入为
   "未定义"的任务,一律归入 P0 前置决策,不进入开发序列。
2. **验证的独立性** —— 每个任务有明确的、可执行的验收判据。优先使用**契约
   一致性检查**(实现导出的 OpenAPI 与契约 diff)和**自动化断言**,而非人工目测。
3. **依赖的单向性** —— 任务按依赖排序,不允许出现环。跨阶段依赖单独标注。

**任务卡片格式**:

```
### <ID> <任务名>
输入 · <文档> <章节>            ← 唯一权威来源
产出 · <交付物>
验证 · <可执行的验收判据>
依赖 · <前置任务 ID>
规模 · S / M / L               ← 相对规模,非工期承诺
```

---

## 0. 全局约定

**权威顺序**:PRD > 架构 > 契约 > 校验表。冲突时以 PRD 为准(架构文档开篇声明)。

**改动接口时的顺序**:改 PRD → 改架构 → 改契约 → 更新 `openapi-verification.md`。

**契约改动后必须**:`bash outputs/scripts/ci.sh`(6 个门禁全过)。

**"契约符合性验证"的含义**:下列多处验证项写作"实现导出的 OpenAPI 与契约
一致",指运行期从实现侧导出 OpenAPI(Java 用 springdoc,Python 用 FastAPI 自动
生成),与 `openapi-*.yaml` 做结构 diff。这是 §5 的 **T-12**,目前因无实现而
**不可执行**,是唯一能把契约与实现绑住的检查 —— 建议在 **T-2.1** 优先落地。

---

## P0. 前置决策(不产代码,但有前置周期)

> 本节任务**必须先于或并行于**开发启动。它们不产出代码,但其中两项有外部
> 审批/评测周期,启动晚了会直接卡住上线。依据:架构 §15.8「落地优先级」。

### P0-1 短信通道报备

```
输入 · 架构 §15.6(短信通道,关键路径)
产出 · 短信服务开通 + 签名/模板报备通过 + AccessKey;开发期兜底方案
        (固定验证码 / 白名单号码)
验证 · 测试环境能真实下发一条验证码,且 60s 内有效、同号限频生效
依赖 · 无
规模 · S(工作量小,但**审批周期数天至两周**)
```

> **这是全项目最长的外部等待项。** 登录是 PRD §5.1「一期必须」,没有短信通道
> 就没有登录,没有登录就没有生成/保存/问 AI。**建议第一件事就启动。**

### P0-2 模型选型 Bake-off

```
输入 · 架构 §15.1(模型选型)、§7.1(三类网关接口)、§7.2(实现与配置)
       PRD §8(技术方案建议)、§11 决策 #4/#5(国产大模型 + 国产图像模型)
产出 · 评测脚本 + 20–30 组真实样本(SKU × 户型 × 风格)+ 五维评分表
       + 选型结论(LLM 主力/备份双供应商、图像模型、Embedding 方案)
验证 · 评测可复现;每个维度有量化分数,而非主观印象
依赖 · 无(AI 相关任务的**共同前置**)
规模 · M
```

> 评分五维(架构 §15.1):①纹理/颜色还原度 ②空间结构与户型一致性
> ③同输入出图方差 ④单张时延与成本 ⑤合规。
> 架构同时建议 **Embedding 优先用国产开源模型(如 BGE-M3)自部署**。

### P0-3 MQ 选型与消息契约(D-13)

```
输入 · 架构 §6.2(异步 MQ)、§6.4(Outbox + 补偿)、§15.2(索引同步机制)、
       §15.9(已知单点与组件复杂度)
       openapi-verification.md §4 D-13、§7(裁决)
产出 · ① 选型结论:RabbitMQ 还是 Redis Stream(§15.9 要求在阶段 0 明确)
       ② AsyncAPI 规范:queue.generate / queue.index.sync / DLQ 的消息体
验证 · AsyncAPI 可被解析工具加载;消息体覆盖 taskId(幂等键)、traceId、
       payload、重试次数、DLQ 元数据
依赖 · 无
规模 · M
```

> **进度(2026-09-29)**:
> - ✅ **② 已完成** —— 产出 `asyncapi-internal.yaml`(AsyncAPI 3.1.0),
>   接入 CI 作为 **G7 门禁**,详见 `openapi-verification.md` §9。D-13 已关闭。
> - ⬜ **① 仍待裁决** —— MQ 选型(RabbitMQ vs Redis Stream)。
>   契约按 AMQP 书写,但**消息体与选型无关**,选定后只需回填
>   `servers` 与 `channel.bindings`。

> **这是当前唯一的"输入缺失"硬缺口。** 架构 §6.2/§6.4 把可靠性机制写得很细
> (手动 ACK、重试、DLQ、Outbox、幂等、补偿扫描),但**消息体长什么样没写**。
> 不做这一步,`T-1.11 生成编排` 无法开工。契约本身覆盖不到 MQ(见
> openapi-verification.md §6 未覆盖范围说明)。

### P0-4 对象存储选型与产品库数据盘点

```
输入 · 架构 §15.5(对象存储)、§8.7(数据初始化与导入)、§8.2(t_product 字段)
       PRD §7.1(产品库核心字段)、§10(边界与假设"产品库数据已完整")
产出 · ① 存储选型:云 OSS 或自建 MinIO(§15.5 建议一期 OSS)
       ② 现有产品库数据盘点:数据位置、格式、字段映射、图片 URL 情况、
          与 t_product 字段的差异清单
验证 · 能从现有数据导出一份**字段齐全、可直接导入**的样本(≥10 条 SKU)
依赖 · 无
规模 · S
```

> PRD §10 与 §11 决策 #1 假定"产品库数据已完整"。**本仓库内未见任何数据。**
> 这个假定若不成立,`T-0.7 批量导入` 的设计输入就是空的。

---

## 阶段 0:地基与后台管理

> 架构 §14 明确:阶段 0 = 后台管理模块 + MySQL 表 + 产品库批量导入 +
> Qdrant 索引 + RBAC。PRD §5.7 给出理由:**推荐搭配与户型模板需人工维护,
> 后台必须先于或同步于 C 端建设**。
>
> 本阶段**无任何 AI 依赖**,契约全部冻结,是风险最低的起点。

### T-0.1 三工程脚手架

```
输入 · 架构 §3(技术选型)、§2(总体架构)、§12.1(一期部署拓扑)
产出 · Java(Spring Boot 3 / JDK 17 / MyBatis-Plus)工程骨架
       Python(FastAPI / 3.11+)工程骨架
       Vue 3 + Vite 前端工程骨架
       三者能各自本地启动,Java 暴露健康检查
验证 · 三个进程能起来;Java `/actuator/health` 返回 UP;
       Python `/docs` 可打开;前端 `npm run dev` 出页面
依赖 · 无
规模 · M
```

### T-0.2 数据库 Schema 与迁移

```
输入 · 架构 §8.1–§8.6(全部表定义,逐字段列出)
产出 · 版本化迁移脚本(Flyway 或 Liquibase),建立文档中定义的全部表:
       §8.1 账户鉴权   t_user / t_sms_code / t_admin_user / t_operation_log
       §8.2 产品库搭配 t_product / t_product_image /
                       t_recommend_combo / t_recommend_combo_item
       §8.3 户型模板   t_unit_template / t_layout
       §8.4 方案报价   t_plan / t_plan_part / t_plan_quote / t_plan_image
       §8.5 陈设参数   t_furnish_style / t_quote_param / t_store / t_dict
       §8.6 生成对话   t_generation_task / t_chat_session / t_chat_message / t_outbox
验证 · 空库执行迁移可跑到最新版本、可重复执行;
       用 information_schema 断言表名、字段名、类型、索引与 §8 逐条一致;
       命名规范:表前缀 t_、字段 snake_case、统一含 created_at/updated_at
依赖 · T-0.1
规模 · M
```

> ⚠️ **已发现的缺口**:契约中有 `GET /admin/products/import/{batchId}` 查询导入
> 批次进度(openapi-admin.yaml),但架构 §8 **未定义导入批次表**。做 T-0.7 前
> 需要先补 `t_import_batch`(批次 id、总行数、成功/失败数、错误报告 URL、状态)。

### T-0.3 统一响应体与业务错误码

```
输入 · 架构 §10.1(通用约定)、§10.5(业务错误码表)
       契约 openapi-common.yaml#/components/schemas/ApiResponse
       契约 openapi-common.yaml#/components/schemas/ErrorCode(19 个码)
产出 · ApiResponse 统一包装、全局异常处理、traceId 注入与透传、
       data.errors[](校验失败明细,ErrorItem)
验证 · 契约校验表 T-1 / T-2 / T-13 在**实现侧**成立:
       所有响应体含 code/message/traceId;业务结果一律 HTTP 200;
       仅 401/403 用真实状态码;所有业务码 ∈ ErrorCode 枚举
依赖 · T-0.1
规模 · S
```

### T-0.4 鉴权与会话(Cookie 方案)

```
输入 · 架构 §10.1(D1 定稿:Set-Cookie 方案)、§11.1(登录注册时序)
       契约 openapi-common.yaml#/components/securitySchemes
产出 · JWT 签发/校验/刷新、HttpOnly Cookie 下发、鉴权拦截器、
       refresh_token 黑名单(Redis)、tempToken 一次性令牌
验证 · access_token 2h / refresh_token 14d 且 Path 收窄至 /api/v1/auth;
       **令牌不出现在响应体中**;**不使用 Authorization 头**
       (唯一例外:注册临时令牌走 Bearer);登出后 refresh 进黑名单;
       结合 T-1.1 验证完整登录链路
依赖 · T-0.1
规模 · M
```

### T-0.5 RBAC 与后台登录

```
输入 · 架构 §4.4(后台权限模型)、§8.1(t_admin_user / t_operation_log)
       契约 openapi-admin.yaml 全份(每个接口标注了所需权限点)
产出 · 三角色(super_admin / operator / editor)权限模型、
       权限点校验(product:write / combo:delete / quote-param:write /
       account:manage 等)、所有写操作记入 t_operation_log
验证 · 逐条对照 §4.4 权限矩阵:editor 调 `DELETE /products/{id}` 返回 10004/403;
       operator 调 `/admin/quote-params` 的 PUT 应**成功**(§4.4 允许),
       而 editor 调同一接口应 403;每次写操作后 t_operation_log 有对应记录
依赖 · T-0.4、T-0.2
规模 · M
```

### T-0.6 产品库 CRUD(后台)

```
输入 · 架构 §8.2(t_product / t_product_image)、§5.7(后台管理)
       契约 openapi-admin.yaml: /products、/products/{id}、
                              /products/{id}/status
       PRD §7.1(SKU 核心字段)、§5.7(产品库管理)
产出 · SKU 新增/编辑/详情/列表/上下架;多角度图片管理;上下架状态流转
验证 · 实现导出的 OpenAPI 与 openapi-admin.yaml 对应片段结构一致;
       契约校验表 T-3(列表分页含 list/total/page/size)成立;
       上下架重复操作返回 30002(§10.5 状态不允许该操作)
依赖 · T-0.3、T-0.5
规模 · M
```

### T-0.7 产品库批量导入

```
输入 · 架构 §8.7(数据初始化与导入,含校验规则与幂等策略)
       契约 openapi-admin.yaml: POST /products/import、
                              GET /products/import/{batchId}、
                              GET /products/import/template
       PRD 假设见 §10「产品库数据已完整」
产出 · Excel/CSV 模板下载、上传、逐行校验、错误报告(行号+原因)、
       批次进度查询、两种模式(upsert / insert_only)
验证 · 上传一份含**故意错误行**的样本:错误行不写入、错误报告行号准确、
       修正后重传可成功;以 sku_code 为业务主键的 upsert 不产生重复数据;
       同一文件重复导入结果幂等
依赖 · T-0.6、**P0-4(产品库数据盘点)**
规模 · M
```

### T-0.8 推荐搭配维护(后台)

```
输入 · 架构 §8.2(t_recommend_combo / t_recommend_combo_item)、§5.7
       契约 openapi-admin.yaml: /combos、/combos/{id}
       PRD §5.2.2(路径 B:推荐搭配,**纯人工维护**)§7.2
产出 · 搭配方案 CRUD、关联 SKU 并指定部位(floor/wall/local)、
       参考报价区间、展示图、上下架
验证 · 搭配详情返回的 items 含 sku + part(Part 枚举恒为三值,契约 T-9);
       删除被引用 SKU 时的行为符合预期(30002 或级联,需实现时明确)
依赖 · T-0.6
规模 · S
```

### T-0.9 户型模板维护(后台)

```
输入 · 架构 §8.3(t_unit_template / t_layout、结构化户型模型字段定义表)
       契约 openapi-admin.yaml: /templates、/templates/{id}
              openapi-common.yaml#/components/schemas/LayoutModel
       PRD §5.3.1(选择户型模板)
产出 · 户型模板 CRUD;layout_json 结构须与 §8.3 字段定义表完全一致;
       按房间数/面积区间/户型结构筛选
验证 · 存入的 layout 用 LayoutModel schema 校验通过(T-11 同类断言);
       areas 字段为**服务端派生**,录入时可省略(§8.3 说明)
依赖 · T-0.3
规模 · S
```

### T-0.10 陈设风格配置(后台)

```
输入 · 架构 §8.5(t_furnish_style,空间 × 风格矩阵)、§5.7
       契约 openapi-admin.yaml: /furnish、/furnish/{id}
       PRD §5.4(陈设随风格自动变化)、§5.7
产出 · 空间 × 风格维度的陈设提示词维护(upsert,以 space+style 唯一)
验证 · 同 (space,style) 重复提交为更新而非新增;矩阵无重复组合
依赖 · T-0.3
规模 · S
```

### T-0.11 报价参数与数据字典(后台)

```
输入 · 架构 §8.5(t_quote_param / t_dict)、§5.5(报价公式)、§8.8(报价规则细化)
       契约 openapi-admin.yaml: /quote-params、/dicts、/dicts/{id}
              openapi-common.yaml#/components/schemas/QuoteParam、Dict
       PRD §5.5(辅料单价按行业标准、损耗率默认 6%)、§5.7(空间/风格字典配置)
       openapi-verification.md §7 D-14/D-15/D-19(裁决结论)
产出 · 报价参数维护(aux_unit_price 元/㎡、loss_rate_min/max);
       空间/风格字典的增删改查(C 端只读 /dicts,B 端 /admin/dicts)
验证 · **量纲必须落实为元/㎡ + 小数**(D-19);损耗率以小数存储,
       0.06 表示 6%(D-15);删除字典项时若被产品/搭配/方案引用,
       返回 30002 而非静默删除
依赖 · T-0.3
规模 · S
```

### T-0.12 门店信息维护(后台)

```
输入 · 架构 §8.5(t_store,单店单条)、§5.7
       契约 openapi-admin.yaml: /store;openapi-c.yaml: /store
       PRD §5.6(引导线下)、§7.6(门店信息:单店)
产出 · 门店信息的读取与更新(单店单条,无新增/删除)
验证 · PUT 为整体更新而非插入;GET 返回字段与契约 Store schema 一致
依赖 · T-0.3
规模 · S
```

### T-0.13 向量索引建立与同步

```
输入 · 架构 §9(向量库设计,全章:集合划分/切片嵌入/检索流程)、
       §6.2(异步 MQ 的索引同步)、§15.2(同步机制建议)
产出 · Qdrant 三个集合(product_index / combo_index / kb_index)建立;
       SKU/搭配变更 → 经 Outbox → queue.index.sync → 异步更新;
       **全量重建脚本**(§15.2 兜底要求)
验证 · SKU 上下架/改价后,payload 中 status/price 同步更新,
       且下架产品在检索过滤中被排除(§9.3 一致性);
       全量重建脚本可从 MySQL 恢复出一致的索引(索引是派生数据,可重建是硬要求)
依赖 · T-0.2、**P0-3(消息契约)**
规模 · M
```

---

## 阶段 1:MVP(C 端闭环)

> 对应 PRD §12「MVP(一期)」。按用户主流程组织:
> 选砖 → 录户型 → 生成 → 报价 → 方案 → 分享 → 引导线下。

### T-1.1 C 端注册登录

```
输入 · 架构 §10.1(D1 Cookie 方案)、§11.1(登录/注册完整时序)
       契约 openapi-c.yaml: /auth/sms/send、/auth/login/sms、
                           /auth/register/set-password、/auth/login/password、
                           /auth/password、/auth/logout、/auth/refresh
       PRD §5.1(手机号验证码 + 注册必须设密码 6–20 位)
产出 · 验证码下发/校验;新手机号返回 needSetPassword + tempToken(**不下发 Cookie**);
       老用户直接下发 Cookie;设密码后创建用户;密码登录;改密;登出;刷新令牌
验证 · 契约校验表 T-4 成立:需登录接口带 cookieAuth、公开接口 `security: []`;
       新用户分支**不下发任何 Cookie**(§11.1 Note);
       错误码覆盖:20001/20002/20003/20004/20005
依赖 · T-0.4、**P0-1(短信通道)**
规模 · M
```

### T-1.2 产品库浏览与筛选(C 端)

```
输入 · 契约 openapi-c.yaml: /products、/products/{sku}
       PRD §5.2.1(路径 A:直接选砖)、§6(瓷砖列表/详情页)
       openapi-verification.md §7 D-8(裁决:补 colorPattern 参数)
产出 · 列表筛选(花色/材质/规格/风格/价格区间)、关键词搜索、详情页
验证 · 契约校验表 T-3 成立(分页);查询参数与 D-8 裁决一致
       (keyword/material/colorPattern/spec/style/space/priceMin/priceMax);
       下架 SKU 不出现于列表、详情返回 30001
依赖 · T-0.6
规模 · M
```

### T-1.3 推荐搭配展示(C 端)

```
输入 · 契约 openapi-c.yaml: /combos、/combos/{id}
       PRD §5.2.2(路径 B)、§6(推荐搭配页)
产出 · 按空间/风格展示成套搭配,含参考报价区间,支持整套选用或替换单品
验证 · 返回结构与 Combo schema 一致;items 的 part 恒为三值枚举(T-9);
       仅返回上架搭配
依赖 · T-0.8
规模 · S
```

### T-1.4 数据字典接口(C 端)

```
输入 · 契约 openapi-c.yaml: /dicts
              openapi-common.yaml#/components/schemas/Dict
       openapi-verification.md §7 D-14(裁决:枚举不硬编码)
       PRD §5.7(空间/风格字典配置)
产出 · 只读字典接口,仅返回 status=enabled、按 sort 升序
验证 · 契约校验表 T-15 的**运行时部分**:前端提交的 space/style
       **必须 ∈ GET /dicts 返回集**(这条静态不可证,必须靠接口测试验证)
依赖 · T-0.11
规模 · S
```

### T-1.5 Python 智能体服务与模型网关

```
输入 · 架构 §4.2(智能体能力单元)、§7.1(三类网关抽象接口)、
       §7.2(实现与配置)、§13.6(密钥管理)
产出 · FastAPI 服务骨架;LLMGateway / ImageGenGateway / EmbeddingGateway
       三类抽象接口与至少一个供应商实现;统一封装超时/重试/限流/熔断/成本计量
验证 · `model.llm.provider` 等配置切换可生效(可插拔性,架构 §1.1 架构目标 3);
       **密钥经环境变量注入,不落代码/不落库**(§13.6)
依赖 · T-0.1、**P0-2(模型选型)**
规模 · M
```

### T-1.6 AI 咨询(路径 C,RAG 多轮)

```
输入 · 架构 §9.4(检索流程)、§11.3(AI 咨询时序)、§4.2(对话咨询)、
       §4.3(工具落位:智能体只拥有「产品库检索/推荐」)
       契约 openapi-c.yaml: /agent/chat(SSE)、/agent/chat/sessions、
                           /agent/chat/sessions/{id}
              openapi-internal.yaml: /internal/agent/chat、/internal/products/{sku}
       PRD §5.2.3(路径 C:回答范围仅限瓷砖信息/推荐/搭配)
       openapi-verification.md §7 D-7(裁决:冻结 SSE 事件集)
产出 · RAG 检索(Qdrant topK + payload 过滤)→ LLM 生成 → SSE 流式返回
       + refs(可采纳 SKU);会话短期上下文存 Redis(TTL 30min),
       长期消息由 Java 落 t_chat_message
验证 · SSE 事件序列严格为 `{sessionId}`(仅首帧)→ delta… → refs → [DONE](D-7);
       **输出边界**:不返回效果图、不返回报价;问价格 → 50001 拒答(一致性评审 A1);
       **回答必须基于产品库真实数据,不得编造不存在的 SKU** —— refs 中的 SKU
       必须能在 /products/{sku} 查到
依赖 · T-1.5、T-0.13
规模 · L
```

### T-1.7 户型录入(模板 + 自然语言解析)

```
输入 · 架构 §8.3(结构化户型模型字段定义表)、§4.3(工具②:参数解析)
       契约 openapi-c.yaml: /layout/templates、/layout/parse
              openapi-internal.yaml: /internal/agent/parse-layout
              openapi-common.yaml#/components/schemas/LayoutModel
       PRD §5.3(两种方式:选模板 / 文字参数;**已取消**上传户型图与手动绘制)
       openapi-verification.md §7 D-2/D-6(裁决)
产出 · 模板列表(C 端);自然语言/表单 → 结构化 LayoutModel;
       解析在 Python,结果交 Java 落库
验证 · 输出可用 LayoutModel schema 校验通过;
       **解析失败按 10001 返回**(D-6 裁决);
       `areas` 为服务端派生字段,录入时可省略(§8.3)
依赖 · T-0.9、T-1.5
规模 · M
```

### T-1.8 报价引擎(Java 规则引擎)

```
输入 · PRD §5.5(报价计算与公式)
       架构 §8.8(报价规则细化:开口扣减、同砖合并)、§8.4(t_plan_part / t_plan_quote)
       契约 openapi-c.yaml: /quote/calc
              openapi-common.yaml#/components/schemas/QuoteResult、QuoteItem
       契约 openapi-common.yaml#/components/schemas/QuoteParam(量纲 D-19)
产出 · 按部位计算:有效面积 = 毛面积 − Σ开口投影面积;
       材料费 = 有效面积 ×(1+损耗率)× 单价;辅料费 = 总面积 × 辅料单价;
       同部位同砖**先合并再加价**;生成 rulesSnapshot
验证 · **用架构 §10.6⑤ 的示例数值做回归断言**,该示例算术已核对自洽:
       floor 3.6×1.06×268 = 1022.69
       wall 有效面积 13.9−(0.8×2.1+1.0×1.2)=11.02,11.02×1.06×68 = 794.32
       材料合计 1817.01;辅料 (3.6+11.02)×20 = 292.40;总价 2109.41
       ⚠️ 该示例在契约中已有(openapi-verification.md §3 第 3 条),可直接复用为测试用例
依赖 · T-0.2
规模 · M
```

> **本任务是全项目最适合"先写测试"的一个** —— 公式、边界、示例数值全部
> 已定死,且是纯确定性逻辑,没有任何 AI 参与。

### T-1.9 未选部位 AI 自动补齐

```
输入 · PRD §5.2.4(不强制选全;AI 自动补齐)、§5.4(部位补齐)
       架构 §4.2(搭配补齐)、§11.2(生成时序中的补齐步骤)
       契约 openapi-internal.yaml: /internal/agent/complete-parts
              openapi-common.yaml#/components/schemas/PartSource
       openapi-verification.md §7 D-4(裁决:source ∈ {user, ai})
产出 · 对**未选的地面/墙面**部位,基于已选砖 + 空间 + 风格补齐 SKU + 部位;
       补齐结果交 Java 落库并参与报价
验证 · **局部(腰线/花砖)为可选部位,不强制补齐**(PRD §5.2.4);
       补齐项标注 source=ai,用户选择标注 source=user(D-4);
       补齐失败返回 40004,用户可重试(§10.5)
依赖 · T-1.5、T-0.13
规模 · M
```

### T-1.10 材质映射与陈设提示词组装

```
输入 · 架构 §7.3(提示词工程:材质映射 / 场景组装 / 陈设 / 约束 / 内容安全)、
       §4.2(材质映射、陈设提示词组装)
       契约 openapi-internal.yaml: /internal/agent/material-map、
                                   /internal/furnish/prompts
       PRD §5.4(画面内容:按部位铺贴、含常规陈设、陈设随风格变化)
产出 · SKU → 材质/纹理/颜色短语,按 floor/wall/local 对号入座;
       场景提示词 = 空间类型 + 风格 + 各部位材质 + 常规陈设 + 视角
验证 · **不同部位的砖必须对号入座**(PRD §5.4 要求"不同部位砖要对号入座");
       陈设提示词取自 t_furnish_style,不硬编码;
       陈设**不计入报价**(PRD §10 边界)
依赖 · T-1.5、T-0.10
规模 · M
```

### T-1.11 生成编排(异步 + Outbox + 幂等)

```
输入 · 架构 §6.2(异步 MQ)、§6.4(Outbox + 补偿,含状态机定义)、
       §11.2(生成效果图完整时序)、§4.1(generate 模块职责)
       契约 openapi-c.yaml: /generate、/generate/{taskId}
              openapi-internal.yaml: /internal/generate/callback
              openapi-common.yaml#/components/schemas/TaskStatus
       PRD §5.4(生成范围/数量/耗时 ≤60s)
产出 · 受理生成请求 → (必要时先调补齐)→ 落 t_generation_task(pending)
       + 同事务写 t_outbox → 投递 → Python 消费出图 → 回调 → 更新状态
       + 触发方案自动保存;进度查询接口
验证 · **任务状态机恒为 pending → running → success / failed**(契约 T-10);
       **回调幂等**:以 taskId 去重,重复回调不产生副作用(已 success 则忽略);
       Outbox 投递失败可按指数退避重试;
       补偿扫描能捞回长时间停留 pending/running 的任务(§6.4)
依赖 · T-0.2、T-1.5、**P0-3(消息契约)**
规模 · L
```

### T-1.12 方案管理(我的方案)

```
输入 · PRD §5.6(方案查看/保存/分享)、§11(决策 #14:自动保存)
       架构 §8.4(t_plan / t_plan_part / t_plan_quote / t_plan_image)、
       §11.4(我的方案·重新编辑时序)、§13.5(户型加密)
       契约 openapi-c.yaml: /plans、/plans/{id}、/plans/{id}/copy
              openapi-common.yaml#/components/schemas/PlanSummary、PlanDetail
产出 · 生成后**自动保存**;列表卡片(名称/封面/时间/空间/瓷砖摘要/报价总额);
       详情;重命名;逻辑删除;另存为;重新编辑(覆盖 or 另存)
验证 · 契约校验表 T-8 成立:**对外 schema 不得出现 `layout_enc`**,
       仅返回解密后的明文 layout(§13.5);
       列表卡片字段与 PlanSummary 一致(D-9);
       重新编辑的两条分支(覆盖原方案 / 另存为新方案)均可复现(§11.4)
依赖 · T-1.11、T-1.8
规模 · L
```

### T-1.13 分享

```
输入 · PRD §5.6(生成分享链接或二维码)
       架构 §13.5(分享 token 足够随机 ≥128bit,支持失效与访问范围控制)
       契约 openapi-c.yaml: /plans/{id}/share(POST/DELETE)、/share/{token}
       openapi-verification.md §7 D-17(裁决:采纳 DELETE /plans/{id}/share)
产出 · 生成分享链接/二维码(token ≥128bit);废除分享;公开只读分享页
验证 · token 随机性满足 ≥128bit;**废除后原链接立即不可访问**;
       分享页为**只读**,不能修改方案;失效 token 返回 30001
依赖 · T-1.12
规模 · S
```

### T-1.14 门店信息与线下引导

```
输入 · PRD §5.6(引导线下:单店地址与联系方式)、§7.6
       契约 openapi-c.yaml: /store
产出 · 展示单店名称/地址/电话/地图链接;一期**不做线上下单**
验证 · 与 PRD §10 边界一致:仅展示,无下单/支付入口
依赖 · T-0.12
规模 · S
```

### T-1.15 报价时效与重算

```
输入 · 架构 §15.7(报价时效)
       契约 openapi-c.yaml: /plans/{id}/recalc
              openapi-common.yaml#/components/schemas/PlanSummary.quoteExpired
       PRD §5.6(卡片标注"报价为生成时估算")
产出 · 生成时存 rulesSnapshot;打开方案默认展示**生成时报价**并标注时间;
       提供"按当前价格重算";产品调价时把受影响方案标记为报价已过期
验证 · `POST /plans/{id}/recalc` **无请求体** —— 部位/面积/开口取自方案快照,
       **不接受前端传参**(这是防止绕过 /quote/calc 参数校验的设计);
       重算后覆盖 parts/quote 快照、刷新 rulesSnapshot、quoteExpired 置回 false
依赖 · T-1.12、T-1.8
规模 · M
```

---

## 阶段 2:收尾与上线

### T-2.1 契约 ↔ 实现双向 Diff(T-12)

```
输入 · openapi-verification.md §5 T-12、§5 T-14;4 份契约全份
       架构 §12.3(CI/CD)
产出 · CI 中自动导出实现侧 OpenAPI(Java springdoc / Python FastAPI)
       并与 openapi-*.yaml 做**双向**结构 diff
验证 · 契约校验表 T-12 由「不可执行」转为「通过」;
       T-14(所有 $ref 可解析)在实现侧产物上同样成立
依赖 · 各实现模块基本就位
规模 · M
```

> **这是唯一能把契约与实现绑住的检查。** 不落地它,契约会从第一天起与实现
> 悄悄分叉,而 CI 仍显示绿色(因为 G1–G6 只校验契约自身)。

### T-2.2 内容安全审核

```
输入 · 架构 §7.3(内容安全:出图与问答前后接审核)、§13.5(内容安全)
       PRD §9(安全:生成图、AI 问答合规)
产出 · 生成图与 AI 问答的前置/后置审核;违规拦截并降级
验证 · 构造违规输入能触发拦截;审核服务不可用时降级不阻断主流程(§13.3)
依赖 · T-1.6、T-1.11
规模 · M
```

### T-2.3 可观测与告警

```
输入 · 架构 §13.7(可观测:9 项指标与告警阈值表)、§13.1(性能目标)
产出 · 结构化日志 + traceId 全链路;Prometheus + Grafana 看板;
       按 §13.7 阈值表配置告警(生成成功率 / P95 耗时 / 队列积压 /
       接口错误率 / DLQ 消息数 等)
验证 · 按 §13.7 逐条触发一次告警并确认能收到;DLQ 有消息时必须告警
依赖 · T-1.11
规模 · M
```

### T-2.4 部署编排(Docker Compose)

```
输入 · 架构 §12.1(一期单机拓扑)、§13.6(密钥管理)
产出 · Nginx(静态资源 + 反代 + **阻断 /internal 外部访问**)、
       Java、Python、MySQL、Redis、Qdrant、RabbitMQ、对象存储的编排
验证 · **`/internal/*` 从公网不可达**(架构 §6.1 硬要求);
       Python 端口不对公网开放;模型密钥经环境变量注入
依赖 · T-2.1
规模 · M
```

### T-2.5 CI/CD 与灰度

```
输入 · 架构 §12.3(环境划分、配置管理、流水线、发布与回滚、灰度)
产出 · dev/staging/prod 三套配置隔离;Java/Python/前端三条流水线;
       滚动更新与一键回滚;数据库变更走版本化迁移
验证 · 回滚可执行且验证过;数据库迁移可回退;
       换模型/改提示词类变更先在 staging 用真实样本验证
依赖 · T-2.4
规模 · M
```

### T-2.6 容灾备份与恢复演练

```
输入 · 架构 §13.4(容灾与备份:RPO ≤ 5min、RTO ≤ 2h)、§15.8(上线前必做)
产出 · MySQL binlog + 每日全量 + 实时增量,备份异地留存;
       **每季度一次恢复演练**;Redis AOF;对象存储多副本
验证 · **实际执行一次恢复演练**,记录实际 RPO/RTO 与目标的差距
依赖 · T-2.4
规模 · M
```

> 架构 §15.9 指出一期 MySQL / RabbitMQ / Qdrant 为**已知单点**,并明确
> "备份与恢复演练**必须做**,否则单点故障不可恢复"。

### T-2.7 非功能验收

```
输入 · PRD §9(非功能需求)、架构 §13.1(性能)、§13.2(并发)、§13.3(可用性)
产出 · 性能与可用性验收报告
验证 · 逐条对齐:
       · 单图生成 ≤ 60s(PRD §9 / §13.1)
       · 页面首屏 < 3s(PRD §9)
       · 非生成类接口 P95 < 500ms(§13.1)
       · 核心业务月可用性 ≥ 99.5%(§13.3)
       · 兼容 Chrome / Safari / Edge 与移动端浏览器(§13.9)
依赖 · T-2.4
规模 · M
```

---

## 附 A. 依赖关系与关键路径

```
P0-1 短信报备 ────────────────────────────┐
P0-2 模型选型 ──────────────────┐         │
P0-3 MQ 契约 ────────┐          │         │
P0-4 数据盘点 ─┐     │          │         │
               │     │          │         │
T-0.1 脚手架 ──┴─────┼──────────┼─────────┼──> T-0.2 Schema
                     │          │         │        │
                     │          │         │        v
                     │          │         │   T-0.3 响应/错误码
                     │          │         │        │
                     │          │         │        v
                     │          │         │   T-0.4 鉴权 ──> T-0.5 RBAC
                     │          │         │                     │
                     │          │         │                     v
                     │          │         │              T-0.6 产品库CRUD
                     │          │         │                │      │
                     │          └─────────┼────────────────┘      v
                     │                    │                 T-0.7 批量导入
                     │                    │
                     │            T-0.8/0.9/0.10/0.11/0.12(可并行)
                     │                    │
                     │                    v
                     └──────────> T-0.13 Qdrant 索引
                                          │
                    ┌─────────────────────┴──────────────┐
                    v                                    v
              T-1.5 智能体+网关                    T-1.1 登录注册 <-- P0-1
                    │                                    │
        ┌───────────┼───────────┐                        │
        v           v           v                        │
   T-1.6 AI咨询  T-1.7 户型  T-1.9 补齐                  │
        │           │           │                        │
        └───────────┴─────┬─────┘                        │
                          v                              │
                   T-1.10 材质映射                       │
                          │                              │
                          └──────> T-1.11 生成编排 <──────┘
                                        │
                                        v
                                  T-1.12 方案管理
                                   │         │
                                   v         v
                            T-1.13 分享  T-1.15 报价重算
```

**关键路径**(决定最早可上线时间):

```
P0-1 短信报备 ──> T-1.1 登录 ──┐
                                ├──> T-1.11 生成 ──> T-1.12 方案 ──> 可上线
P0-3 MQ 契约 ──> T-0.13 索引 ──┤
P0-2 模型选型 ─> T-1.5 网关 ───┘
```

> **P0-1 / P0-2 / P0-3 三项都在关键路径上**,且 P0-1 有数天至两周的外部审批
> 周期。这是"先做 P0 再做 0 阶段"的依据。

**可立即并行、零阻塞的任务**(不依赖任何 P0):T-0.1 ~ T-0.13 中除 T-0.13 外
全部,以及 T-1.8(报价引擎 —— 公式已定死、无 AI 依赖、最适合先写测试)。

---

## 附 B. 验证手段汇总

| 验证手段 | 适用任务 | 说明 |
| --- | --- | --- |
| **契约一致性 Diff** | T-0.6/0.7/0.8/0.11/0.12、T-1.2/1.3/1.4/1.12/1.13/1.14/1.15 | 实现导出的 OpenAPI 与契约片段结构 diff(T-12) |
| **示例数据回归** | T-1.8 | 架构 §10.6⑤ 的报价数值已核对自洽,可直接作断言 |
| **Schema 校验** | T-0.9、T-1.7 | 输出用对应 JSON Schema(如 LayoutModel)校验 |
| **状态机 / 枚举断言** | T-1.11、T-1.9 | TaskStatus 四值、Part 三值、PartSource 二值 |
| **幂等性测试** | T-0.7、T-1.11 | 重复请求/重复回调不产生副作用 |
| **权限矩阵测试** | T-0.5 | 逐条对照架构 §4.4 的三角色权限表 |
| **边界符合性** | T-1.2/1.6/1.9/1.10/1.14 | 对照 PRD §10「边界与假设」与 §11「已确认决策」 |
| **故障注入** | T-1.11、T-2.2 | 回调重复、MQ 投递失败、审核服务不可用 |
| **恢复演练** | T-2.6 | 实际执行,记录真实 RPO/RTO |
| **告警触发** | T-2.3 | 逐条触发 §13.7 阈值表 |

---

## 附 C. 拆解过程中发现的文档缺口

以下为编制本清单时发现的、**上游文档尚未覆盖**的点,建议在对应任务开工前补齐:

| # | 缺口 | 影响任务 | 建议 |
| --- | --- | --- | --- |
| ~~G-1~~ | ~~**MQ 消息体结构未定义**(D-13)~~ | T-0.13、T-1.11 | ✅ **已关闭** —— 产出 `asyncapi-internal.yaml` + G7 门禁,见 openapi-verification.md §9 |
| G-2 | **导入批次表 `t_import_batch` 未定义**,但契约有 `/products/import/{batchId}` | T-0.7 | 在架构 §8 补表定义 |
| G-3 | **辅料单价 `aux_unit_price` 的具体数值**未给出(仅说"按行业标准") | T-0.11、T-1.8 | 由业务方给定初始值 |
| G-4 | **模型厂商与图像生成模型未定** | T-1.5 及全部 AI 任务 | bake-off 后确定(P0-2) |
| G-5 | **产品库现有数据的实际形态未知**(PRD 假定"已完整") | T-0.7 | 数据盘点(P0-4) |
| G-6 | **PRD 状态标注为"待评审"**,但结尾称"需求定稿",架构为"已确认" | 全部 | 确认 PRD 是否已定稿 |

---

*本清单由 PRD v0.6、架构 v1.2 与 4 份 OpenAPI 契约推导而来。若上游文档变更,
请先更新上游,再同步本清单。*
