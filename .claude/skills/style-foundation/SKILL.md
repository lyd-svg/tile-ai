---
name: style-foundation
description: tile-ai-web 的样式地基 —— 设计令牌怎么命名分层、主题怎么切与扩、多语言要准备什么。要新建/修改 CSS 变量、要写颜色、要动主题(data-theme / antd theme)、要格式化金额日期、要写 CSS 布局属性时,先读这里。令牌的具体数值看 tile-ai-design.md,视觉规则看 frontend-design。
---

# 样式地基 —— 执行手册

**权威来源:`tile-ai-style-foundation.md`(仓库根目录)。**
本 Skill 是执行清单,不产生新规则;冲突时以文档为准。

**分工**:

| 问 | 看 |
| --- | --- |
| 主色几号?字号多大? | `tile-ai-design.md` §3(**值**) |
| **令牌怎么命名/分层/新增** | **本 Skill** |
| **主题怎么切/扩** | **本 Skill** |
| **金额日期怎么格式化** | **本 Skill** |

---

## 三层令牌:只准碰中间那层

| 层 | 长什么样 | 谁能用 |
| --- | --- | --- |
| L1 原始 | `--terracotta-500` `--stone-200` | ❌ **业务代码禁止** |
| **L2 语义** | `--color-primary` `--color-text` | ✅ **组件与页面只准用这层** |
| L3 组件 | `--button-bg` `--table-header-bg` | ✅ 仅 `components/base` |

```
❌ var(--terracotta-500)   ← 跨层直接引 L1
❌ #B4552F                 ← 绕过令牌
✅ var(--color-primary)    ← 只碰 L2
```

**命名格式 `--{类别}-{用途}-{变体}`**,类别受控:
`color` `space` `radius` `text` `font` `shadow` `z` `dur` `ease`。
变体受控:`hover` `active` `disabled` `soft` `strong` `muted`。

**三条铁律**:
1. **语义化,不含具体值** —— `--color-blue` ❌ → `--color-info` ✅。
2. **间距/字号的数字是「刻度索引」,不是像素** —— `--space-3` 是第 3 档,改值不改名。
3. kebab-case,全小写。

**文件**:`src/styles/tokens.css`(全站唯一写十六进制处)+ `antdTheme.ts`(第二处)+ `base.css` + `index.css`。

---

## 令牌生命周期

- **新增**:先问"现有令牌能表达吗?"—— **能就不加**。
- **改值**:同时改**三处** —— `design.md` 表格、`tokens.css`、`antdTheme.ts`。
- **废弃**:先标 `/* @deprecated */` **保留**,过一个迭代再删。
  ⚠️ 直接删会让引用**静默失效**(CSS 变量取不到值**不报错**)。

---

## 主题规则

主题 = **只覆盖 L2**;L1 不变,L3 自动跟变。挂 `data-theme="site|admin"` 在根节点。

| 谁 | 干什么 |
| --- | --- |
| `SiteLayout` | 设 `data-theme="site"` |
| `AdminLayout` | 设 `data-theme="admin"` |
| **页面** | ❌ **不许自己设主题** |

**两条最容易出事的**:

1. **`data-theme` 与 antd `ConfigProvider :theme` 必须同源** ——
   各写各的会得到"半身不遂"的主题(页面底色对了、antd 按钮还是默认蓝)。
2. **新主题必须覆盖全部 L2 语义令牌** ——
   漏一个 ⇒ 静默落到默认值,**看起来"差不多对",其实有块地方是错的**,
   没有测试会红。

暗色一期不做,但**现在就遵守**:颜色成对定义(bg 配 text)、组件里不许硬编码
"白底黑字"假设、留好 `[data-theme="dark"]` 位置。

---

## 多语言:立规则,不引框架

> **当前"一期不引 i18n 框架"仍然有效。** 下面只是"别挡住路"。

**现在就做(零成本)**:

1. **文字不进图片**。
2. **CSS 用逻辑属性** —— `margin-inline-start` 而非 `margin-left`;`padding-block`;`inset-inline-start`。
3. **文本容器不写死宽度**(英文比中文长 30%~50%)。
4. ⭐ **金额/日期走 `Intl`** —— `Intl.NumberFormat` / `Intl.DateTimeFormat`。
   **这条一箭双雕**:既为多语言铺路,又满足"报价数字必须可信"。**不做多语言也该做。**
5. 不用文字当图标。
6. 不用 JS 拼接句子/标点。

**引框架时再做**:文案抽 key(`{域}.{场景}.{要素}`)、带占位符的完整句、复数交框架。

---

## 提交前

- [ ] 没有十六进制(只该在 `tokens.css` / `antdTheme.ts`)
- [ ] 没有直接用 **L1** 令牌
- [ ] 没有内联 `:style` 颜色
- [ ] 改了主题 ⇒ 新主题**覆盖了全部 L2**
- [ ] `data-theme` 与 antd `theme` **同源**
- [ ] 新增 CSS 用**逻辑属性**
- [ ] 文本容器**没写死宽度**
- [ ] 金额/日期走 **`Intl`**
- [ ] 没把文字烤进图片 / 用文字当图标

---

## 反模式(见到就打回)

1. ❌ 组件里写 `#B4552F` 或 `--terracotta-500`。
2. ❌ 主题只覆盖一半令牌。
3. ❌ `data-theme` 与 antd 主题各写各的。
4. ❌ 页面自己设主题。
5. ❌ 为"只有一处用"新建令牌。
6. ❌ 令牌名带颜色词 / 像素值。
7. ❌ 直接删废弃令牌。
8. ❌ 固定 `px` 宽度卡文本。
9. ❌ 手写金额/日期格式化。
10. ❌ 文字烤进图片 / 文字当图标。

---

## 边界(别顺手做)

- 不做**暗色模式**、不引 **i18n 框架**(§4 —— 立规则不引框架)。
- 不做多品牌换肤、不做 RTL 实测。
- 令牌的**数值**不在本 Skill 定 —— 去 `tile-ai-design.md`。
