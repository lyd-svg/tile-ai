/**
 * `appConfirm`(antd `Modal` 包装层)的测试 —— **G2 的配方验证**,见 `openapi-verification.md` §68。
 *
 * 这一个文件是**整批 `window.confirm` 迁移的样板**:8 个页面都要靠它。
 * 所以它不只测包装层,还负责回答一个问题:
 * **antd 的 `Modal.confirm` 在 happy-dom 里到底跑不跑得起来、怎么驱动。**
 *
 * 与 `Select` 同源的三条规则(见 `tests/helpers/antd.ts`)在这里同样适用:
 * 弹层渲染到 portal ⇒ **必须查 `document`,不能用 `wrapper.find`**。
 */

import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { appConfirm } from '../src/components/base/AppConfirm'

/**
 * 在 portal 里按文案找一个按钮。
 *
 * ⚠️ 查的是 `document` —— 确认框在 portal 里,`wrapper.find` 搜不到。
 *
 * ⚠️ **比对前必须把空白全部去掉**。antd 默认会在"两个汉字"的按钮里插空格
 *    (「确定」→「确 定」),而且它**不报错** —— 只是静默找不到。
 *
 *    生产里 `App.vue` 用 `ConfigProvider.config({ autoInsertSpaceInButton: false })` 关掉了它,
 *    但**本测试不挂 `App.vue`**,拿不到那个全局设置 —— 所以**这里必须自己去空白**,
 *    不是"双保险",是**唯一保险**。
 */
function portalButton(text: string): HTMLElement | undefined {
  const normalize = (value: string) => value.replace(/\s+/g, '')
  return Array.from(document.querySelectorAll('button')).find(
    (node) => normalize(node.textContent ?? '') === normalize(text),
  )
}

afterEach(() => {
  // 弹层不会被自动回收 —— 不清掉,上一条的确认框会漏到下一条里
  document.body.innerHTML = ''
})

describe('appConfirm —— antd Modal 包装层', () => {
  it('点「确定」→ true', async () => {
    const answer = appConfirm({ title: '确认删除', content: '该操作不可撤销', danger: true })
    await flushPromises()

    // eslint-disable-next-line no-console
    console.log('===MODAL-DOM===' + document.body.innerHTML.slice(0, 2500))

    const ok = portalButton('确定')
    expect(ok).toBeTruthy()
    ok!.click()
    await flushPromises()

    expect(await answer).toBe(true)
  })

  it('点「取消」→ false', async () => {
    const answer = appConfirm({ title: '确认删除' })
    await flushPromises()

    const cancel = portalButton('取消')
    expect(cancel).toBeTruthy()
    cancel!.click()
    await flushPromises()

    expect(await answer).toBe(false)
  })
})
