---
name: frontend-stack
description: tile-ai-web 的技术选型与组件复用规矩。要新增依赖(npm install)、要自己写一个 UI 元素、要决定"用库还是自己造"、要动 antd 或组件复用边界时,先读这里。视觉与配色看 frontend-design,这里管"怎么实现、用什么搭"。
---

# 前端技术实现与选型 —— 执行手册

**权威来源:`tile-ai-frontend-stack.md`(仓库根目录)。**
本 Skill 是它的执行清单,**不产生新规则**;冲突时以文档为准。
**视觉 / 配色 / 令牌 → 看 `frontend-design` Skill 与 `tile-ai-design.md`。**

---

## 七条原则(冲突时上位赢)

1. 优先成熟框架、成熟组件库。
2. **遵守框架官方方案** —— 以官方文档为准,**不以网上教程为准**。
3. **禁止重复造轮子** —— 库有的通用能力,必须直接用。
4. **优先复用** —— 先找,再造。
5. **边界设定** —— 什么必须用库、什么必须自己写,划死线。
6. 对齐最佳实践 —— 不自创风格。
7. **组件库的首要职责是"统一"** —— 页面统一、元素统一。

---

## 动手前:三问

1. **`src/components/` 里有了吗?**(先 grep,别凭记忆)
2. **antd 有了吗?**(查**官方文档**,别查记忆)
3. **要引新依赖吗?** ⇒ 走下面「依赖闸门」。

---

## 依赖闸门(任何 `npm install` 之前)

- [ ] 先问:**官方 / 现有能力能不能覆盖?**(本项目连 axios、moment 都不引 ——
      HTTP 已有 `src/api/http.ts`,日期用 antd 自带的 dayjs。)
- [ ] 确需引入 ⇒ **先在 `openapi-verification.md` 记一次决策**,写清:
      **为什么 / 替代方案 / 体积代价 / 维护状态 / 退出成本**。
- [ ] 只加 `devDependencies` 也要说明理由。
- [ ] 装完**实测**:`vue-tsc` 通过、`vite build` 通过、TS 仍是 **5.x**。

> ⚠️ 本项目的默认答案是**"不引"**。要引,得说服记录。

---

## 禁止自己写(一律用 antd)

按钮 · 输入 · 下拉 · 单选/复选 · 开关 · 日期时间选择 · 数字输入 ·
**表单校验** · 表格 · 分页 · 排序筛选 · 弹窗 · 抽屉 · Popconfirm ·
Message / Notification · Tooltip · Tag · Steps · Tabs · Collapse ·
Upload · Progress · Skeleton · Empty · Tree · Transfer · Spin

> 觉得"我自己写 30 行更简单"?这 30 行会在第 20 个页面变成 600 行,
> 且样式、交互、无障碍各不相同。**这就是组件库要消除的。**

---

## 必须自己写

- 业务组件:产品卡 / 方案卡 / 报价构成 / 图集 / 陈设矩阵
- 领域逻辑:报价格式化、生成状态映射、权限点判断
- 契约适配:错误码 → 文案;服务端字段校验 → 表单高亮(**复用 `validationErrorsOf`**)
- 应用外壳:布局、主题注入、路由守卫

---

## 提交前

- [ ] 业务 `.vue` 里**没有**直接 `import { … } from 'ant-design-vue'`(走包装层)。
- [ ] 没有自研的通用 UI 元素。
- [ ] 表单校验走 antd `Form.rules`,不是每页手写 `validate()`。
- [ ] 没出现 `:deep(.ant-*)` / `!important` 硬改库样式(改 `antdTheme.ts`)。
- [ ] 同一段 UI 没被复制到第 3 处(该抽取了)。
- [ ] 新依赖有决策记录。
- [ ] `npm run test` + `npm run build` 通过;TS 仍 5.x。

---

## 包装层的红线

包装 = **薄适配**,不是重写。它必须仍然"是"那个库组件(可透传 attrs / slots)。

| ✅ 可以 | ❌ 不行 |
| --- | --- |
| 统一主题、统一 props 词表、统一默认行为 | 自己重写分页 / 校验逻辑 |
| 补充业务语义(`PriceText`、`StatusTag`) | 复制库源码来"改一点" |
| 加默认 loading 态、统一错误提示 | 包死到无法透传 attrs / slots |

---

## 官方方案对照(别自创)

Vue:`<script setup>` + 组合式 API · Router:`RouterLink` / 懒加载 ·
Pinia:setup store + `storeToRefs` · antd:`ConfigProvider` 主题 + `Form.rules` ·
Vite:`import.meta.env` · TS:`strict`、别用 `any` / `@ts-ignore` 止痛。

---

## 边界(别顺手做)

- 不引图表库 / 动画库 / 富文本 / 拖拽库 / i18n 框架。
- **SSR 与分享预览卡片是独立工作流**(`tile-ai-design.md` §7.1),不归本 Skill 管。
- 本 Skill 不碰接口 / 字段 —— 改契约回 `CLAUDE.md` 的契约链规矩走。
