/**
 * antd 组件在 happy-dom 里的测试工具 —— **阶段 G1.5 的产出**(见 `openapi-verification.md` §68)。
 *
 * ## 为什么需要它
 *
 * antd 的组件**不是原生元素**:`Select` 没有 `<select>`、`Modal` 没有 `<dialog>`。
 * 所以项目里原来那套"查 `select` / 读 `option` / 点 `button`"的断言方式**直接失效**。
 * G2 要逐页迁移 12 个**有测试的**页面,每个都会撞上同一个问题 ——
 * 与其每页各写一遍,不如在这里固化三条规则。
 *
 * ## 三条规则(都经实测确认,不是推测)
 *
 * 1. **定位靠"测试锚点"** —— 在模板上给 antd 组件挂一个稳定的 `class`
 *    (如 `.part-select`),测试用它找。**别靠层级或序号**。
 * 2. **打开下拉/弹层用 `mousedown`,不是 `click`** ——
 *    实测:用 `click` 触发 `Select` 时**下拉根本没打开**,`document.body` 是空的。
 * 3. **portal 里的东西要查 `document`,不能用 `wrapper.find`** ——
 *    下拉、Modal、Message 都被渲染到 `document.body`;
 *    而 `@vue/test-utils` 的 `wrapper` **只在组件自己的元素树里搜**。
 *
 * ## 还有一条没验的
 *
 * `mount()` 默认**不把组件挂到 document**(它是游离的)。上面的写法对此**不敏感**
 * —— 因为 portal 挂的是 `document.body`,而组件本身用 `wrapper` 找。
 * 但如果哪天需要 `document.querySelector` 找**组件本身**,就得上 `attachTo: document.body`。
 * **这一条没有实测过,别当结论用。**
 */

import type { DOMWrapper } from '@vue/test-utils'

/**
 * 打开一个 antd `Select` 的下拉。
 *
 * ⚠️ **必须 `mousedown`** —— 实测 `click` 不会打开它。
 */
export async function openSelect(select: DOMWrapper<Element>): Promise<void> {
  await select.find('.ant-select-selector').trigger('mousedown')
}

/**
 * 读**当前打开的下拉**里的选项文案。
 *
 * ⚠️ 查的是 `document` 而不是 `wrapper` —— 下拉在 portal 里。
 * ⚠️ 调用前必须先 `openSelect()`,否则拿到空数组(而且**不会报错**,只是断言失败)。
 */
export function openedSelectOptionTexts(): string[] {
  return Array.from(document.querySelectorAll('.ant-select-item-option-content')).map((node) =>
    (node.textContent ?? '').trim(),
  )
}

/** 点中下拉里的某一项(按文案精确匹配)。须先 `openSelect()`。 */
export async function pickSelectOption(text: string): Promise<void> {
  const target = Array.from(document.querySelectorAll('.ant-select-item-option-content')).find(
    (node) => (node.textContent ?? '').trim() === text,
  )
  if (!target) {
    throw new Error(`下拉里没有「${text}」这一项;当前有:${openedSelectOptionTexts().join(' / ')}`)
  }
  ;(target as HTMLElement).click()
}

/**
 * portal 里的全部文案 —— Modal / Message / Notification 都走 portal,用这个断言。
 *
 * ⚠️ 它读的是 **`document.body` 的全文**,而 `mount()` 默认不挂载组件到 document,
 * 所以正常情况下 `body` 里**只有 portal 的内容**。若将来改用 `attachTo`,
 * 这个函数会把组件自身也算进去 —— **那时要改**。
 */
export function portalText(): string {
  return document.body.textContent ?? ''
}
