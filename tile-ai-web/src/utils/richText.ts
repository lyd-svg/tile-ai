/**
 * 把 **模型吐出来的文本**渲染成一小撮安全的 HTML。
 *
 * 背景:AI 咨询的回答是 Markdown 风格的文本(`**首选墙砖:**`、行首 `- ` 的列表),
 * 而页面按纯文本渲染时,那些 `**` 会**原样显示**出来,很难看。
 *
 * ## 安全:这是唯一一处用 `v-html` 的地方,所以顺序不能反
 *
 * 内容来自**大模型**,属于**不可信输入** —— 它完全可能吐出一个 `<script>` 或
 * `<img onerror=...>`。所以这里**先整体转义**,再把自己生成的那几个标签插进去。
 * 也就是说:进入 `v-html` 的字符串里,**任何 HTML 都不是原文带来的**,只能是本文件
 * 造出来的 `<strong>` / `<code>`。**改这个文件时不要打乱这个顺序。**
 *
 * ## 只认三种写法(刻意不做完整的 Markdown 解析)
 *
 * - `**粗体**` → `<strong>`
 * - `` `行内代码` `` → `<code>`
 * - 行首的 `- ` / `* ` → `• `(一个圆点字符,不生成 `<ul>`)
 *
 * **为什么列表用圆点而不是 `<ul><li>`**:气泡是 `white-space: pre-wrap` 逐行显示的,
 * 插入块级元素会在列表前后多出空行,反而更乱。圆点在逐行显示下同样读得出是列表,
 * 而且不会引入任何排版意外。
 *
 * 其余 Markdown(标题、表格、链接……)**一律按纯文本显示** —— 每多支持一条规则,
 * 就多一处可以被模型内容穿透的缝。
 */

const HTML_ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

/** 转义 HTML 特殊字符。**必须发生在插入任何标签之前。** */
export function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (char) => HTML_ESCAPES[char] ?? char)
}

/** `**粗体**`:同一行内、非贪婪。跨行不匹配 —— 模型偶尔会漏一个闭合标记, */
const BOLD = /\*\*([^\n]+?)\*\*/g
/** 行内代码。同样限制在一行内。 */
const CODE = /`([^\n`]+?)`/g
/** 行首的列表符号。保留缩进(模型常用缩进来做二级列表)。 */
const BULLET = /^(\s*)[-*]\s+/gm

/** 行内标记:`**粗体**` 与 `` `代码` ``。 */
function inline(escaped: string): string {
  return escaped.replace(BOLD, '<strong>$1</strong>').replace(CODE, '<code>$1</code>')
}

/**
 * 渲染成可交给 `v-html` 的字符串(**已经过转义**,只含本文件生成的标签)。
 */
export function renderRichText(text: string): string {
  const escaped = escapeHtml(text ?? '')
  return inline(escaped).replace(BULLET, '$1• ')
}
