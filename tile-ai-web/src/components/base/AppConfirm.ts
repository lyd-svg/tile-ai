/**
 * 确认框 —— **antd `Modal` 的包装层**(阶段 G2 的第一个产出,见 `openapi-verification.md` §68)。
 *
 * ## 为什么要有这一层,而不是让 8 个页面各自调 `Modal.confirm`
 *
 * 1. **规范要求**:`tile-ai-design.md` §6.1 —— 页面不得直接 import antd 组件,
 *    全走 `src/components/` 包装层。
 * 2. **它把回调式 API 变回"同步式"**。原来页面里写的是:
 *    ```ts
 *    if (!window.confirm('确认删除?')) return
 *    await deleteX()
 *    ```
 *    而 antd 的 `Modal.confirm` 是**回调式**的 —— 直接用就得把 `deleteX()` 那段
 *    **搬进 `onOk` 回调**,那是一次真正的代码重构。
 *    包成 `Promise<boolean>` 之后,页面只需要把 `window.confirm(` 换成
 *    `await appConfirm({`,**其余一行不动**。
 * 3. **8 个页面共用一处** —— 要改文案、要接入主题、要统一"危险操作用红色确认按钮",
 *    只改这一个文件。
 *
 * ## 用法
 *
 * ```ts
 * if (!(await appConfirm({ title: '确认删除', content: `确认删除 ${row.sku}?`, danger: true }))) return
 * ```
 *
 * ⚠️ **调用处必须 `await`** —— 忘了 `await` 会拿到一个恒真的 Promise,
 * 于是**确认框还没点就已经执行了**。这是个静默错误,不会有任何报错。
 */

import { Modal } from 'ant-design-vue'

export interface AppConfirmOptions {
  /** 标题(短) */
  title: string
  /** 正文(说清后果) */
  content?: string
  okText?: string
  cancelText?: string
  /** 危险操作 ⇒ 确认按钮用危险色,而不是主色 */
  danger?: boolean
}

/**
 * 弹出确认框。**点了确定 → `true`;取消 / 关闭 → `false`。**
 *
 * ⚠️ 与 `window.confirm` 不同,它是**异步**的 —— 调用处要 `await`。
 */
export function appConfirm(options: AppConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title: options.title,
      content: options.content,
      okText: options.okText ?? '确定',
      cancelText: options.cancelText ?? '取消',
      okType: options.danger ? 'danger' : 'primary',
      // 遮罩点击 / ESC 关闭都算"取消" —— `window.confirm` 也是这个语义
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}
