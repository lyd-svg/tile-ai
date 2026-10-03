/**
 * Ant Design Vue 主题令牌映射。
 *
 * 职责:把我们的设计令牌喂给 antd,让它**别露出自带的默认蓝**(`#1677ff`)。
 * 权威来源:`tile-ai-design.md` §5(Ant Design Vue 的接入约束)。
 *
 * ## 为什么这里有十六进制(而不是 `var(--color-primary)`)
 *
 * antd 用的是 CSS-in-JS(运行时算样式),它的 seed token 只吃**具体值**,
 * 不接受 `var(...)`。所以值是**必须**在这里再写一遍的。
 * ⇒ **本文件是全站第二个(也是最后一个)允许写十六进制的地方。**
 * ⇒ **改任何值必须同时改三处**:`tile-ai-design.md` §3 表格、`tokens.css`、本文件。
 *
 * ## 怎么用
 *
 * 由 layout 挂在 `<a-config-provider :theme="...">` 上,且**必须与根节点的
 * `data-theme` 同源** —— 各写各的会得到"页面底色对了、按钮还是默认蓝"的
 * 半身不遂主题(`tile-ai-style-foundation.md` §3.2)。
 * (接线在阶段 A2,本步骤只定义、不引用。)
 */

import { theme } from 'ant-design-vue'

/** 两端口径统一的共享令牌 —— 主要来自 `tokens.css` 的 L2 语义层。 */
const sharedToken = {
  // 主色 —— 陶土(两端统一,保持品牌一致)
  colorPrimary: '#B4552F',
  colorPrimaryHover: '#CD7550',
  colorPrimaryActive: '#974626',
  colorLink: '#B4552F',

  // 语义状态 —— 与 tokens.css 的 --color-* 语义令牌对齐
  colorSuccess: '#4F7D4A',
  colorWarning: '#C98A1B',
  colorError: '#A32B1E',
  colorInfo: '#4A6274',

  // 文字 —— 暖黑系
  colorTextBase: '#2B2621',
  colorText: '#2B2621',
  colorTextSecondary: '#5E5140',
  colorTextTertiary: '#9F9280',

  // 表面与边框
  colorBgBase: '#FAF7F2',
  colorBgContainer: '#FFFFFF',
  colorBgLayout: '#FAF7F2',
  colorBorder: '#C0B6A7',
  colorBorderSecondary: '#DBD4C9',

  fontFamily:
    '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", ' +
    '"Microsoft YaHei", "Noto Sans SC", Roboto, "Helvetica Neue", Arial, sans-serif',
} as const

/** C 端(site)—— 松弛、圆角大、正文 16。 */
export const siteTheme = {
  algorithm: theme.defaultAlgorithm,
  token: {
    ...sharedToken,
    borderRadius: 10,
    borderRadiusLG: 16,
    borderRadiusSM: 6,
    fontSize: 16,
    controlHeight: 40,
  },
}

/** B 端(admin)—— 紧凑、圆角小、正文 14。 */
export const adminTheme = {
  algorithm: theme.compactAlgorithm,
  token: {
    ...sharedToken,
    borderRadius: 6,
    borderRadiusLG: 8,
    borderRadiusSM: 4,
    fontSize: 14,
    controlHeight: 32,
  },
}

export type AppThemeName = 'site' | 'admin'

/** 按主题名取配置 —— layout 用这个,避免两处各写一份 if。 */
export function themeFor(name: AppThemeName) {
  return name === 'admin' ? adminTheme : siteTheme
}
