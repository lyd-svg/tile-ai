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

import type { DOMWrapper, VueWrapper } from '@vue/test-utils'

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

/**
 * 读一个 `Select` **当前选中的文案**。
 *
 * ⚠️ antd `Select` **不是 `<select>`** —— 没有 `element.value` 可读
 * (原生的 `(el as HTMLSelectElement).value` 断言迁到 antd 后**必然失效**)。
 * 选中的东西渲染成 `.ant-select-selection-item` 的文字,读那个。
 */
export function selectedText(select: DOMWrapper<Element>): string {
  const node = select.find('.ant-select-selection-item')
  return node.exists() ? normalize(node.text()) : ''
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

/**
 * 去掉**全部空白**再比对 —— 见下面 `buttonTexts` 的说明。
 *
 * 导出它是给各 spec 里**已有的**局部 helper 用(那些 helper 各有各的签名,
 * 重写代价大于收益);**新写的用例请直接用 `buttonTexts` / `buttonByText`**。
 */
export function normalize(value: string): string {
  return value.replace(/\s+/g, '')
}

/**
 * 读页面上全部按钮的文案,**已去掉空白**。
 *
 * ## ⚠️ 为什么必须去空白
 *
 * antd **默认会给「两个汉字」的按钮插一个空格** —— 「编辑」渲染成「编 辑」。
 * 而**测试不挂 `App.vue`**,拿不到 `ConfigProvider :auto-insert-space-in-button="false"`
 * 那条设置(生产里关掉了,测试里没关)。
 *
 * ⇒ 直接写 `button.text() === '编辑'` 会**静默失败**:不报错、不提示,
 *    只是**永远找不到那个按钮**。实测踩到过(见 `openapi-verification.md` §70.6 / §70.9)。
 *
 * ⚠️ 一律用本文件这三个函数,**别在用例里手写 `findAll('button')`** ——
 *    手写就会忘记归一化,而且**忘了也不会红**,只会在某天迁移按钮时突然炸。
 */
export function buttonTexts(wrapper: VueWrapper): string[] {
  return wrapper.findAll('button').map((node) => normalize(node.text()))
}

/**
 * 读页内状态块(antd `Alert`)的文案,**已去掉空白**。
 *
 * ⚠️ **`Alert` 是"页内常驻"的,不在 portal 里** —— 所以用 `wrapper.find` 找得到,
 *    不像 `Select` / `Modal` 要查 `document`。它同时自带 `role="alert"`,
 *    所以原来 `[role="alert"]` 的断言**不用改**(见 `tile-ai-design.md` §5.5)。
 */
export function alertTexts(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.ant-alert-message').map((node) => normalize(node.text()))
}

/** 按文案找按钮(**去掉空白**后比)。找不到返回 `undefined`。 */
export function findButton(wrapper: VueWrapper, text: string): DOMWrapper<Element> | undefined {
  const target = normalize(text)
  return wrapper.findAll('button').find((node) => normalize(node.text()) === target)
}

/**
 * 按文案找按钮,**找不到直接抛** —— 大多数用例要的是这个:
 * 拿到 `undefined` 再随手 `!` 会得到一个更难读的失败信息。
 */
export function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<Element> {
  const found = findButton(wrapper, text)
  if (!found) {
    throw new Error(
      `页面上没有「${text}」这个按钮;当前有:${buttonTexts(wrapper).join(' / ') || '(一个都没有)'}`,
    )
  }
  return found
}
