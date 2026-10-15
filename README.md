# 瓷砖选购 AI 效果图与报价系统

用户选瓷砖 → 录入户型 → AI 生成铺贴效果图 → 自动出报价单。
C 端给业主用,B 端给门店运营用。

> **这个项目是「文档先行」的** —— 先写清楚需求与接口契约,再写代码。
> 所以文档的分量不比代码轻。**想看懂它,从 [`CLAUDE.md`](CLAUDE.md) 的「文档索引」进,
> 比从代码进快得多。**

---

## 当前状态(2026-10-15)

| 事项 | 状态 |
| --- | --- |
| 端到端主流程 | ✅ **已跑通,并在真实浏览器里人工验收过** —— 选砖 → 录户型 → 生成 → 我的方案 → 改名 / 重算 → 分享 → **别人不登录也能打开** |
| C 端页面 + 后台六页 | ✅ 全部完成;全站 UI 重做过并视觉验收通过 |
| 真实图像模型 | ✅ 已接(硅基流动 `Qwen/Qwen-Image-Edit-2509`);替身实现保留,切配置即可回退 |
| **出图质量** | 🔴 **至今没评过** —— 用户实测反馈「还是乱画」,这是当前功能侧真正的瓶颈 |
| ⛔ **出图现在是停的** | 2026-10-07 起,厂商侧那两个可用的「按参考图编辑」模型持续零响应。**不是本项目代码的问题** —— 排查全过程见 `openapi-verification.md` §81 |
| 备案 / 大模型登记 | ⬜ 未启动(只挡上线,不挡现在) |

> ⚠️ **「能跑通」和「能上线」是两件事。** 这个仓库目前处于前者。
> 前者已经做到了;后者还有一段路(出图质量、备案周期 1–4 个月不可压缩)。

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue + Pinia |
| 业务后端 | Java 17 + Spring Boot 3.5 + MyBatis-Plus + Flyway |
| 智能体 | Python 3.11+ / FastAPI |
| 中间件 | MySQL 8(权威数据)· Redis(会话 / 缓存)· RabbitMQ(异步任务)· Qdrant(向量) |
| 模型 | LLM 主力 `deepseek-flash`、备份智谱 GLM(不同厂商,故障域独立);图像与 Embedding 均经**模型网关**抽象,可整体替换 |

---

## 目录结构

```
.
├── CLAUDE.md                    ← ★ 从这里进:文档索引 + 必须遵守的铁律
│
├── tile-ai-prd.md               需求(文档链的源头,冲突时以它为准)
├── tile-ai-architecture.md      架构
├── openapi-*.yaml               接口契约:C 端 / B 端 / 内部 / 公共
├── asyncapi-internal.yaml       MQ 消息契约
├── openapi-verification.md      追溯表 + 全部决策记录(篇幅最大的一份)
├── tile-ai-tasks.md             任务卡片与进度
├── tile-ai-roadmap.md           ★ 现在做到哪、还差什么、怎么验
├── tile-ai-design.md 等五份      前端视觉 / 选型 / 结构 / 令牌规范
│
├── tile-ai-server/              Java 业务核 —— 唯一对外 API
├── tile-ai-agent/               Python 智能体服务 —— 仅内网,不直接对外
├── tile-ai-web/                 Vue 3 前端(C 端 + B 端,同一个工程两个外壳)
├── deploy/                      本地中间件编排
├── outputs/scripts/             契约门禁(ci.sh)与各类校验脚本
├── tools/                       运维与审计脚本(重建索引 / 下载向量模型 / 数字字段审计)
└── .claude/ .githooks/ .github/ AI 协作配置 · 提交前钩子 · CI
```

---

## 怎么跑起来

### 0. 中间件

```powershell
docker compose -f deploy/docker-compose.dev.yml up -d     # Redis / Qdrant / RabbitMQ
```

MySQL 走**本机原生安装**,不在 compose 里。

### 1. Java 业务核(端口 8080)

```powershell
cd tile-ai-server
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

需要环境变量 `DB_PASSWORD`、`LAYOUT_ENC_KEY`(详见 `tile-ai-roadmap.md` §5.6)。
⚠️ `LAYOUT_ENC_KEY` **一旦用过就不能换** —— 换了历史户型数据解不开。

### 2. Python 智能体(端口 8000)

```powershell
cd tile-ai-agent
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000
```

### 3. 前端(端口 5173)

```powershell
cd tile-ai-web
npm run dev        # http://localhost:5173
```

> 默认管理员账号见 `tile-ai-roadmap.md` §5.6。

---

## 怎么验

**五类判据,全过才算完。** 完整命令与当前基线在 `tile-ai-roadmap.md` §5:

| # | 跑什么 | 命令要点 |
| --- | --- | --- |
| 1 | 契约门禁(7 道) | `bash outputs/scripts/ci.sh` |
| 2 | 契约 ↔ 实现比对 | `bash outputs/scripts/contract-diff.sh` ⚠️ **要在 1 之前跑**(门禁 G6 会读它的产物)|
| 3 | Java | `mvn test -Ddb.verify=true -Dredis.verify=true -Drabbit.verify=true` |
| 4 | Python | `cd tile-ai-agent` 后 `pytest -q` |
| 5 | 前端 | `cd tile-ai-web` 后 `npm test` + `npm run build` |

⚠️ **两个容易骗到自己的坑**(都真实踩过):

- **「跳过」不等于「通过」**。不带 `-Ddb.verify=true` 时集成测试会整批跳过,`mvn test` 照样显示绿色。
  需要外部依赖的测试必须显式开开关。
- **测试连的是测试库,不是你在用的那个**。MySQL `tile_ai_test` / Redis 第 15 库 / RabbitMQ `tileai_test` vhost
  已由 `pom.xml` 自动钉住,**不要用 `-Dspring.datasource.url=…` 覆盖**。

---

## 几条提醒(不在这里复述规则)

这个仓库有一条自我约束:**规则、字段、状态一律不在索引文件里复述** ——
因为复述必然与权威文件分叉,而过期的信息比没有信息更危险。

所以:

- **动手前**,先读 [`CLAUDE.md`](CLAUDE.md) 第二章「铁律」。那是**不查任何文档也必须遵守**的硬约束。
- **想知道某个决策为什么这么定** → `openapi-verification.md`。
- **改了接口** → 必须按 `PRD → 架构 → 契约 YAML → 追溯记录` 的顺序自上而下改,不跳步、不反向改。

---

## 一期明确不做

线上下单 / 支付 · 铺贴人工费 · 物流费 · 美缝 · 上传户型图识别 · 第三方登录 ·
多空间 / 全屋出图 · 效果图陈设计入报价。

完整边界见 `tile-ai-prd.md` §10 / §12。

---

> 私有仓库。仓库内所有文档与代码为项目内部资料。
