/**
 * 富文本渲染(utils/richText.ts)。
 *
 * 这里是**唯一一处 `v-html`**,而内容来自**大模型**(不可信输入)。
 * 所以最重要的不是"加粗好不好看",而是**它绝不会把模型吐的 HTML 当成 HTML 执行**。
 * 下面第一组用例就是钉这个。
 */

import { describe, expect, it } from 'vitest'

import { escapeHtml, renderRichText } from '../src/utils/richText'

describe('转义(安全底线)', () => {
  it('HTML 特殊字符一律转义', () => {
    expect(escapeHtml('<a href="x">&\'</a>')).toBe(
      '&lt;a href=&quot;x&quot;&gt;&amp;&#39;&lt;/a&gt;',
    )
  })

  it('**模型吐出的标签不会被执行** —— 它是文本,不是标记', () => {
    const html = renderRichText('<script>alert(1)</script>')

    expect(html).not.toContain('<script')
    expect(html).toContain('&lt;script&gt;')
  })

  it('包在加粗里的标签同样逃不掉转义', () => {
    const html = renderRichText('**<img src=x onerror=alert(1)>**')

    expect(html).not.toContain('<img')
    expect(html).toContain('<strong>')
    expect(html).toContain('&lt;img')
  })

  it('属性里带引号也逃不掉', () => {
    const html = renderRichText('<a href="javascript:alert(1)">点我</a>')

    expect(html).not.toContain('<a ')
    expect(html).toContain('&quot;')
  })
})

describe('渲染', () => {
  it('`**粗体**` 变成 `<strong>`', () => {
    expect(renderRichText('**首选墙砖:**云灰大理石')).toBe(
      '<strong>首选墙砖:</strong>云灰大理石',
    )
  })

  it('`` `代码` `` 变成 `<code>`', () => {
    expect(renderRichText('用 `TL-1001` 这块')).toBe('用 <code>TL-1001</code> 这块')
  })

  it('行首的 `- ` / `* ` 变成圆点,缩进保留', () => {
    const html = renderRichText('- 材质:金属面砖\n  - 表面:柔光')

    expect(html).toBe('• 材质:金属面砖\n  • 表面:柔光')
  })

  it('普通换行原样保留(气泡是 pre-wrap,靠它换行)', () => {
    expect(renderRichText('第一行\n第二行')).toBe('第一行\n第二行')
  })

  it('**没有闭合的 `**` 原样显示** —— 逐字流式时常见,不该突然变样', () => {
    expect(renderRichText('**还没写完')).toBe('**还没写完')
  })

  it('`**` 不跨行匹配(免得多行之间误配对)', () => {
    expect(renderRichText('**开了头\n结尾**')).toBe('**开了头\n结尾**')
  })

  it('空串与 undefined 不炸', () => {
    expect(renderRichText('')).toBe('')
    expect(renderRichText(undefined as unknown as string)).toBe('')
  })
})
