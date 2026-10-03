---
name: frontend-structure
description: tile-ai-web 的代码组织规矩 —— 新文件放哪个目录、谁能 import 谁、什么时候把代码抽成组件。要新建文件/目录、要加 import、要判断"该不该抽组件"、要决定组件放哪一层时,先读这里。视觉看 frontend-design,选型与依赖看 frontend-stack。
---

# 目录 · 模块 · 组件规则 —— 执行手册

**权威来源:`tile-ai-frontend-structure.md`(仓库根目录)。**
本 Skill 是执行清单,**不产生新规则**;冲突时以文档为准。

**三个前端 Skill 的分工**(别互相打架):

| 问 | 看 |
| --- | --- |
| 长什么样 / 颜色 / 主题 | `frontend-design` |
| 用什么库 / 能不能引依赖 / 包装层 | `frontend-stack` |
| **东西放哪 / 谁能依赖谁 / 该不该抽组件** | **本 Skill** |

---

## 三条总原则

1. **边界靠方向约束,不靠自觉** —— 违反依赖方向 = 缺陷。
2. **单向依赖,禁止循环** —— 下层永不许反向依赖上层。
3. **复用是提升出来的** —— 先用最靠内的位置写,第 **3** 次出现才提升。

---

## 新文件放哪?(速查)

| 要写的东西 | 放哪 |
| --- | --- |
| 设计令牌 / 主题 | `src/styles/` |
| 出网请求 | `src/api/`(C 端 `api/c/`,B 端 `api/admin/`,两端共享放 `api/` 根) |
| 跨组件状态 | `src/stores/` |
| 权限 / 菜单 | `src/auth/` |
| 包装 antd 的基础件 | `src/components/base/` |
| **业务无关**的可复用件 | `src/components/common/` |
| **含业务语义**的组合件 | `src/components/business/` |
| 可复用的**有状态逻辑** | `src/composables/`(`useXxx`) |
| **纯函数** | `src/utils/` |
| 外壳 / 页面 | `src/layouts/` · `src/views/site|admin/` |

---

## 谁能 import 谁(方向图)

```
views ─▶ components/business ─▶ common ─▶ base ─▶ styles
  │            │                  │         │
  ├─▶ stores ─▶ api ◀─────────────┴─────────┘
  ├─▶ composables ─▶ stores / api
  ├─▶ auth ─▶ api
  └─▶ utils            utils 谁都依赖它,它不依赖任何人
```

**五条硬规矩**:

1. `utils/` **必须纯净** —— 无副作用、不发请求、不读 store、不 import 任何 `src` 内部模块。
2. **`api/` 是唯一出网口** —— 组件 / store / view 一律不许自己 `fetch`。
3. **`components/base` 与 `common` 不许碰 `api/` 与 `stores/`** —— 纯展示件,props 进、emits 出。
4. **C 端与 B 端互不越界** —— `views/site` ↛ `views/admin`,`api/c` ↛ `api/admin`。
5. **路由 name 才跳转** —— 不写死 path 字符串;`router/` 是路由唯一拥有者。

---

## 该不该抽组件?什么时候提升?

**优先级链**(动手写 UI 之前从内往外找):
页面内联 → `views/<端>/components/` → `components/business/` → `common/` → `base/` → antd。

**三次提升法**:

| 出现次数 | 怎么办 |
| --- | --- |
| 第 1 次 | 就地写 |
| 第 2 次 | 复制,并注释标记"该抽了" |
| **第 3 次** | **必须提升** |

**提到哪一层**:含业务语义 → `business/`;包装 antd → `base/`;都不含 → `common/`。

> ⚠️ **别提前抽象**。只有两次时,你还不知道接口该怎么设计。

---

## 提交前

- [ ] 新文件在**对**的目录。
- [ ] 无反向依赖、无循环依赖。
- [ ] `utils/` 新函数纯净。
- [ ] `api/` 之外没有 `fetch`。
- [ ] `components/base|common` 没 import `api/` 或 `stores/`。
- [ ] C 端没 import B 端(反向亦然)。
- [ ] 跳转用路由 name。
- [ ] 同一段 UI 没被复制到第 3 处。
- [ ] 新组件在对的层级,只暴露 props / emits,没有把内部 `ref` 透出去。
- [ ] 体量没超红线(`.vue` ≤ 300 行 / 函数 ≤ 50 行 / props ≤ 10)。

---

## 反模式(见到就打回)

1. ❌ 页面里直接 `fetch`。
2. ❌ `utils/` 里的函数读 store / 发请求。
3. ❌ `components/common` 里的组件自己取数。
4. ❌ C 端页面 import B 端组件。
5. ❌ 跨端引 `api/` 文件。
6. ❌ 页面之间互相 import。
7. ❌ 硬编码路径跳转。
8. ❌ 第二次出现就急着抽象。
9. ❌ 用 `index.ts` 把整个 `components/` 再导出(易成环、伤 tree-shaking)。
10. ❌ 为父组件方便,把子组件内部状态挂到 store。

---

## 边界(别顺手做)

- 不预先造空目录(`composables/` 有用例再建)。
- **ESLint 尚未引入** —— 依赖方向目前靠人工 review;
  若要把它落成 `import/no-restricted-paths` 机器规则,**先走依赖决策**(见 `frontend-stack`)。
- 本 Skill 不碰视觉、不碰选型、不碰契约。
