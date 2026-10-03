# 瓷砖选购 AI — 前端目录 · 模块 · 组件规范

## 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | 前端目录边界 · 模块边界 · 组件复用规则 |
| 版本 | v1.0(草案,待评审) |
| 编写日期 | 2026-10-03 |
| 状态 | **草案** |
| 上游 | `tile-ai-architecture.md` §3(前端选型)、`tile-ai-prd.md` |
| **姊妹篇** | `tile-ai-design.md`(**长什么样**)、`tile-ai-frontend-stack.md`(**用什么搭**) |
| 本文管 | **东西放在哪 · 谁能依赖谁 · 什么该抽成组件** |
| 下游 | `tile-ai-web` 目录结构、`.claude/skills/frontend-structure/`(执行手册) |

> **定位**:前端**代码组织**的唯一权威来源:目录边界、模块边界、组件分层与复用。
> **不是**:视觉(→ design)、技术选型与依赖(→ stack)、需求、接口。

---

## 1. 三条总原则

1. **边界靠"方向"约束,不靠自觉。**
   每个目录**能依赖谁、不能依赖谁**,写成一张有向图(§3)。违反方向 = 缺陷。
2. **单向依赖,禁止循环。**
   上层可以依赖下层;**下层永远不许反过来依赖上层**。
3. **复用是"提升"出来的,不是"设计"出来的。**
   先用**最靠内**的位置写(页面内联),第 **3** 次出现时**提升**一层(§5)。

---

## 2. 目录边界规范

### 2.1 目标结构

```
tile-ai-web/
├─ src/
│  ├─ main.ts                 ← 应用入口:只做装配,无业务
│  ├─ App.vue                 ← 根组件
│  ├─ env.d.ts
│  │
│  ├─ styles/                 ★新  设计令牌与主题
│  │   ├─ tokens.css                  唯一允许写十六进制色值的处所
│  │   ├─ antdTheme.ts                唯一允许写十六进制色值的处所
│  │   └─ base.css                    reset + 全局基础样式
│  │
│  ├─ api/                    ★   出网层(唯一 fetch 的地方)
│  │   ├─ http.ts  errors.ts  types.ts  sse.ts  dicts.ts   ← 两端共享
│  │   ├─ c/                  ← C 端接口
│  │   └─ admin/              ← B 端接口
│  │
│  ├─ stores/                 ← 跨组件状态(pinia)
│  ├─ auth/                   ← 权限点与菜单(领域模块)
│  │
│  ├─ components/             ★新  可复用组件(分三层,见 §5)
│  │   ├─ base/                       L0 包装 antd
│  │   ├─ common/                     L1 通用展示(业务无关)
│  │   └─ business/                   L2 业务组件
│  │
│  ├─ composables/            ★新  可复用逻辑(可选,有才建)
│  ├─ layouts/                ← 外壳(站点外壳 / 后台外壳)
│  ├─ router/                 ← 路由(全站唯一)
│  ├─ utils/                  ← 纯函数(无副作用 / 无网络 / 无状态)
│  └─ views/
│      ├─ site/               ← C 端页面
│      └─ admin/              ← B 端页面
└─ tests/                     ← 与 src/ 结构**镜像**
```

> ★新 = 本次规范新增的目录(`styles/` `components/` `composables/`)。
> 其余为**现状已有**,不搬迁。

### 2.2 每个目录"拥有什么"

| 目录 | 拥有 | **禁止**出现 |
| --- | --- | --- |
| `styles/` | 设计令牌、主题、reset | 任何业务选择器、组件样式 |
| `api/` | 出网、契约解包、错误分类 | 业务判断、状态、UI |
| `stores/` | 跨组件共享状态 | 出网裸调(走 `api/`)、UI |
| `auth/` | 权限点、菜单可见性 | UI 组件、出网 |
| `components/base` | 包装 antd 的基础件 | `api/`、`stores/`、业务语义 |
| `components/common` | 业务无关的可复用展示件 | `api/`、`stores/`、业务语义 |
| `components/business` | 含业务语义的组合件 | 路由跳转(除非明确的路由型组件) |
| `composables/` | 可复用的**有状态逻辑** | UI 渲染(`.vue`)、纯函数(→ `utils/`) |
| `layouts/` | 站点外壳、后台外壳 | 具体页面内容 |
| `router/` | 路由表、守卫 | 页面内容 |
| `utils/` | **纯函数** | 副作用、网络、状态、import 任何 `src` 内部模块 |
| `views/` | 具体页面 | 被其它页面复用(该提升为组件) |

### 2.3 命名约定

| 对象 | 约定 | 例 |
| --- | --- | --- |
| Vue 组件文件 | **PascalCase**,多词(避免与原生标签撞名) | `ProductCard.vue` |
| TS 模块文件 | **camelCase** | `userAuth.ts` |
| 目录 | **小写**(可复用层用复数) | `components/` `stores/` |
| composable | `useXxx` | `usePlanQuote.ts` |
| 测试 | 与被测同名 + `.spec.ts` | `ProductCard.spec.ts` |
| 路由 `name` | **kebab-case**,跳转**用 name 不写死 path** | `admin-login` |

---

## 3. 模块边界规范(谁能依赖谁)

### 3.1 依赖方向图(单向,禁止循环)

```
views/  ──▶ components/business ──▶ components/common ──▶ components/base ──▶ styles/
   │                │                      │                    │
   │                ▼                      ▼                    ▼
   ├──────────▶ stores/ ──▶ api/ ◀──────────┘                 utils/
   │                │        ▲                                  ▲
   ├──────────▶ composables/─┘                                  │
   ├──────────▶ auth/ ───────┘                                  │
   └──────────▶ utils/ ─────────────────────────────────────────┘
                  (utils 谁都依赖它,它不依赖任何人)

layouts/  ──▶ components/* / stores / router
router/   ──▶ views(懒加载)/ stores / auth
```

**读法:箭头是"允许依赖"的方向。**

### 3.2 五条硬规矩

1. **`utils/` 必须纯净。**
   纯函数,无副作用、**不发请求、不读 store、不 import 任何 `src` 内部模块**。
   (所以 `router/` `views/` `api/` 都能放心用它们,不会成环。)

2. **`api/` 是唯一出网口。**
   组件、store、view **一律不许自己 `fetch`**。要数据 → 调 `api/` 里的函数。

3. **`components/base` 与 `components/common` 不许碰 `api/` 与 `stores/`。**
   它们是**纯展示件**:数据靠 `props` 进,事件靠 `emits` 出。
   要自己取数的,那是 `components/business` 或 `views/`。
   ⇒ 好处:能被任意页面复用、能被测试直接喂 props、不会有隐藏副作用。

4. **C 端与 B 端互不越界。**
   `views/site/**` ↛ `views/admin/**`;`api/c/**` ↛ `api/admin/**`。
   两边都要用的东西,放到**共享层**(`api/` 根、`components/common`、`utils/`)。
   > 现状已符合:`api/dicts.ts` 就放在根上,而不是复制成两份。

5. **`router/` 是路由的唯一拥有者。**
   页面不许自己拼路径字符串跳转 —— 用路由 `name`。
   > 好处:改路径不用全站搜替换。

### 3.3 模块的"公开面"

- **一个文件即一个模块**,它 `export` 什么,什么就是公开面。
- **禁止深挖别人的内部文件**:跨模块不许 import 对方的私有文件。
  私有文件用 `_` 前缀(如 `_helpers.ts`)或放进 `internal/`。
- **不强制 barrel(`index.ts` 再导出)**:
  `components/` 尤其不用 —— barrel 容易引入循环依赖,也会削弱 tree-shaking。
  **要什么就显式 import 什么。**
- **状态归属**:跨组件共享才进 `stores/`;组件本地状态用 `ref` / `reactive`。
  **不许为了"方便取值"把本地状态塞进 store。**

### 3.4 边界执行

- **人工**:提交前对照 §6 检查清单。
- **脚本(可选,需先定 lint 决策)**:用 ESLint `import/no-restricted-paths` 把 §3.1
  的方向图**落成机器可拦的规则**。
  ⚠️ 仓库目前**没有任何 lint 配置** —— 是否引入是**待决策项**
  (见 `tile-ai-frontend-stack.md` §10)。

---

## 4. 组件分层与复用规则

### 4.1 四个层级

| 层 | 位置 | 特征 | **允许依赖** | 例子 |
| --- | --- | --- | --- | --- |
| **L0 基础** | `components/base/` | 包装 antd,统一主题与行为 | `styles` `utils` | `AppButton` `AppTable` `AppForm` |
| **L1 通用** | `components/common/` | **业务无关**的可复用展示件 | L0 · `styles` `utils` | `PageHeader` `EmptyState` `LoadingState` `ErrorState` |
| **L2 业务** | `components/business/` | **含业务语义**的组合件 | L0 · L1 · `api` `stores` `utils` | `ProductCard` `PlanCard` `QuoteBreakdown` `ImageGallery` |
| **L3 页面** | `views/site|admin/` | 具体页面,**不跨页复用** | 以上全部 · `router` `auth` |

> ⏸️ **UI 阶段例外(2026-10-03)**:L2 那一栏写着"允许依赖 `api` / `stores`" ——
> 那是长期规则。**当前 UI 阶段只抽"展示型"组件**(props 进 / emits 出),
> **数据获取留在页面**,不许顺手把取数搬进组件。
> 依据即 §3.2 规矩 3;见 `tile-ai-ui-rollout-plan.md` §2 手法 3 与 §0 的 C6。

### 4.2 复用优先级链(动手写 UI 之前,按顺序找)

```
① 同一个页面里已经有了?  → 就地抽成本页子组件
② views/<端>/components/ 里有了?  → 直接用
③ components/business/ 里有了?   → 直接用
④ components/common/ 里有了?     → 直接用
⑤ components/base/ 里有了?       → 直接用
⑥ antd 组件里有?                 → 经 base 包装后用
⑦ 都没有                          → 才自己写
```

### 4.3 rule of three(三次提升法)

**同一段 UI / 逻辑出现第 3 次时,必须提升。**

| 出现次数 | 放哪 |
| --- | --- |
| 第 1 次 | **就地写**(页面内联) |
| 第 2 次 | 复制一份,**并留一个注释标记"该抽了"** |
| **第 3 次** | **必须提升** —— 按 §4.4 判断提到哪一层 |

> 这是刻意的:**不提前抽象**。只有两次,你还没有足够信息知道接口该怎么设计。

### 4.4 提升到哪一层?(判断顺序)

```
它含业务语义吗?
  ├─ 含  → components/business/
  └─ 不含 → 它包装 antd 吗?
              ├─ 是 → components/base/
              └─ 否 → components/common/
```

### 4.5 组件契约规则

- **对外接口只有 `props` 与 `emits`**;不许把内部 `ref` 暴露出去让父组件改。
- 双向绑定用 `v-model`(Vue 3 的 `defineModel`),不自造 `xxx + onChange` 两件套。
- **L0 / L1 不许有副作用**:不发通知(`message`)、不跳路由、不发请求 ——
  一律通过 `emit` 交给上层决定。
- 组件名**用多词**(`ProductCard`,不是 `Card`)—— 避免与原生标签 / antd 组件撞名。

### 4.6 体量红线(软约束,超了就该拆)

| 对象 | 建议上限 |
| --- | --- |
| 单个 `.vue` 文件 | ≤ 300 行 |
| 单个函数 | ≤ 50 行 |
| 单个组件 props | ≤ 10 个(多了说明职责不单一) |
| `views/` 单页内的子组件 | 若本页专属子组件 > 3 个,考虑建 `views/<端>/components/` |

---

## 5. 现状与目标的差距(诚实)

| 项 | 现状 | 目标 |
| --- | --- | --- |
| `src/components/` | **不存在** | 建 `base` / `common` / `business` 三层 |
| `src/styles/` | **不存在**(样式散落在各 `.vue` 的 `scoped style`) | 建令牌与主题层 |
| `src/composables/` | 不存在 | 有可复用逻辑时再建,不预先造空目录 |
| `api/` 分层 | **已符合**(`c/` `admin/` + 根上共享) | 保持 |
| `utils/` 纯度 | 待核 | 保持纯净 |
| C/B 越界 | 待核 | 零越界 |
| lint 配置 | **完全没有** | **待决策**(见 `frontend-stack` §10) |
| 测试结构 | `tests/` 平铺(29 个 spec) | 与 `src/` 镜像(可分批迁) |

---

## 6. 提交前检查清单

- [ ] 新文件放进**对**的目录(§2.2 的"拥有什么 / 禁止什么")。
- [ ] 没有反向依赖、没有循环依赖(§3.1)。
- [ ] `utils/` 新函数**纯净**(无副作用 / 无网络 / 无状态 / 无内部 import)。
- [ ] `api/` 之外**没有** `fetch`。
- [ ] `components/base|common` **没有** import `api/` 或 `stores/`。
- [ ] C 端没有 import B 端(反向亦然)。
- [ ] 跳转用的是路由 **name**,不是写死的 path。
- [ ] 同一段 UI 没被复制到**第 3 处**(该提升了)。
- [ ] 新组件放在了**正确层级**(§4.4),对外只暴露 props / emits。
- [ ] 单文件 / 单函数没超体量红线(§4.6)。

---

## 7. 反模式清单(见到就打回)

1. ❌ 页面里直接 `fetch` —— 走 `api/`。
2. ❌ `utils/` 里的函数读 store / 发请求 —— 那它不是 util。
3. ❌ `components/common` 里的组件自己去取数据 —— 它是纯展示件。
4. ❌ C 端页面 import B 端组件 —— 共享的东西上提到 `common`。
5. ❌ 从一端 `api/` 引另一端的接口文件。
6. ❌ 页面之间互相 import(页面不该被复用,该抽组件)。
7. ❌ 硬编码路径跳转,不用路由 name。
8. ❌ 第二次出现就急着抽象(信息不足,接口会设计错)。
9. ❌ 用 barrel `index.ts` 把整个 `components/` 再导出(易成环、伤 tree-shaking)。
10. ❌ 把组件内部状态挂到 store 上,只为父组件方便读。

---

## 8. 与其它文档的关系 / 维护

| 想知道 | 看 |
| --- | --- |
| 长什么样、什么颜色、C/B 双主题 | `tile-ai-design.md` |
| 用什么库、依赖能不能引、包装层边界 | `tile-ai-frontend-stack.md` |
| **东西放哪、谁能依赖谁、什么该抽组件** | **本文** |

**边界冲突时**:视觉问题看 design,选型/依赖问题看 stack,组织问题看本文。
三者若有实质冲突(比如 stack 的"包装层"与本文的"分层"打架),**以 stack 为准**
(它管"能不能做"),然后回来改本文。

**待办**:
1. ☐ 与 design / stack 的待办**一并**去补 `openapi-verification.md` / `CLAUDE.md` /
   `tile-ai-roadmap.md` 的记录。
2. ☐ 若决定引入 ESLint,把 §3.1 的方向图落成 `import/no-restricted-paths` 规则。
3. ☐ `tests/` 是否迁成与 `src/` 镜像 —— 需一次决策(涉及重写中的测试)。
