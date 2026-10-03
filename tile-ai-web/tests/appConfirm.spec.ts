/**
 * `appConfirm` 的测试 —— **G2 的样板**,见 `openapi-verification.md` §70.6。
 *
 * 与上一版(测静态 `Modal.confirm`)的关键区别:
 * **现在弹窗是一个组件,必须先挂宿主**。生产里宿主由 `App.vue` 挂在
 * `<ConfigProvider>` 之内;这里单独挂一个。
 *
 * 规则与 `Select` 同源(见 `tests/helpers/antd.ts`):弹层在 **portal** 里,
 * ⇒ **必须查 `document`,不能用 `wrapper.find`**。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { appConfirm } from '../src/components/base/AppConfirm'
import AppConfirmHost from '../src/components/base/AppConfirmHost.vue'
import { normalize } from './helpers/antd'

/**
 * 在 portal 里按文案找按钮。
 *
 * ⚠️ 查 `document` —— 弹窗在 portal 里。
 * ⚠️ **比对前去掉全部空白**:antd 会给"两个汉字"的按钮插空格(「确 定」),
 *    而且**不报错**。生产里 `App.vue` 关了那个开关,但**本测试不挂 `App.vue`**,
 *    拿不到那条设置 —— 所以这里**必须**自己去空白。
 */
function portalButton(text: string): HTMLElement | undefined {
  const normalize = (value: string) => value.replace(/\s+/g, '')
  return Array.from(document.querySelectorAll('button')).find(
    (node) => normalize(node.textContent ?? '') === normalize(text),
  )
}

/**
 * 挂宿主。
 *
 * ⚠️ **不挂它,`appConfirm` 什么都弹不出来** —— 状态变了,但没有人渲染它。
 * 这是"声明式"与"静态方法"最直接的区别。
 */
function mountHost() {
  return mount(AppConfirmHost)
}

describe('appConfirm —— 声明式确认框', () => {
  it('点「确定」→ true', async () => {
    mountHost()
    const answer = appConfirm({ title: '确认删除', content: '该操作不可撤销', danger: true })
    await flushPromises()

    // 弹窗走 portal:查 document,不是 wrapper
    expect(document.body.textContent).toContain('确认删除')
    expect(document.body.textContent).toContain('该操作不可撤销')

    const ok = portalButton('确定')
    expect(ok).toBeTruthy()
    ok!.click()
    await flushPromises()

    expect(await answer).toBe(true)
  })

  it('点「取消」→ false', async () => {
    mountHost()
    const answer = appConfirm({ title: '确认删除' })
    await flushPromises()

    const cancel = portalButton('取消')
    expect(cancel).toBeTruthy()
    cancel!.click()
    await flushPromises()

    expect(await answer).toBe(false)
  })

  it('上一个没答完又弹一个时,上一个判为「取消」—— 不会永远挂着', async () => {
    mountHost()

    const first = appConfirm({ title: '第一个' })
    await flushPromises()

    // 还在等答案时又弹一个
    const second = appConfirm({ title: '第二个' })
    await flushPromises()

    // ⚠️ 被顶掉的那个**必须 resolve** —— 否则调用处的 `await` 永远不返回,
    // 界面上表现为"点了没反应",而且不会有任何报错。
    expect(await first).toBe(false)

    // 页面上应当只剩"第二个"这一个弹窗(声明式的好处:同一个 Modal 实例)
    expect(document.body.textContent).toContain('第二个')
    expect(document.body.textContent).not.toContain('第一个')

    portalButton('确定')!.click()
    await flushPromises()
    expect(await second).toBe(true)
  })

  /**
   * ⚠️ **特点说明测试(characterization test)** —— 它钉的是 **antd 的行为,不是我们的代码**。
   *
   * 目的:让"为什么到处都在归一化空白"这件事**有个可执行的出处**。
   * 少了它,后来人会以为那些 `.replace(/\s+/g, '')` 是多余的噪音而清理掉,
   * 然后在某次迁移按钮时**静默踩坑**(见 `openapi-verification.md` §70.6 / §70.9)。
   *
   * 哪天 antd 不再插空格了,这条会红 —— 那时**就可以把归一化撤掉**。这正是它的用处。
   */
  it('⚠️ antd 会给「两个汉字」的按钮插空格(所以比对必须归一化)', async () => {
    mountHost()
    appConfirm({ title: 'x' })
    await flushPromises()

    const raw = Array.from(document.querySelectorAll('button')).map((n) => n.textContent ?? '')
    expect(raw.some((text) => text.includes('确 定'))).toBe(true)

    // 归一化之后才比得上干净的「确定」
    const ok = Array.from(document.querySelectorAll('button')).find(
      (node) => normalize(node.textContent ?? '') === '确定',
    )
    expect(ok).toBeTruthy()
  })

  it('自定义按钮文案生效', async () => {
    mountHost()
    const answer = appConfirm({ title: '废除链接', okText: '废除', cancelText: '算了' })
    await flushPromises()

    expect(portalButton('废除')).toBeTruthy()
    expect(portalButton('算了')).toBeTruthy()

    portalButton('算了')!.click()
    await flushPromises()
    expect(await answer).toBe(false)
  })
})
