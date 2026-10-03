/**
 * 确认框 —— **状态与公开 API**(阶段 G2,见 `openapi-verification.md` §70.6)。
 *
 * ## ⚠️ 为什么不是 `Modal.confirm`
 *
 * antd 的**静态方法** `Modal.confirm` 是**脱离应用上下文**渲染的(自建根、挂到 body),
 * 于是两件事都做不到(均为实测):
 *   ① **拿不到主题** ⇒ 按钮会是 antd 默认蓝;
 *   ② `<ConfigProvider :auto-insert-space-in-button="false">` **管不到它**
 *      ⇒ 按钮文案渲染成「确 定」。
 * 全局配置那条路也走不通(类型不兼容、且那个开关不在全局字段表里)。
 *
 * ⇒ **改成声明式**:本文件只放"状态 + Promise",**不 import antd**;
 *   真正渲染的 `<Modal>` 放在 `AppConfirmHost.vue` 里,由 `App.vue` **挂在根上**
 *   ——那样它就在 `<ConfigProvider>` 之内,**主题与空格一起解决**。
 *
 * ## 这层切法的好处
 *
 * - **页面侧与测试都不碰 antd** —— 它们只认识 `appConfirm()`;
 * - 要换掉弹窗实现(或换 antd 版本),**只动宿主组件一个文件**。
 *
 * ## 用法
 *
 * ```ts
 * if (!(await appConfirm({ title: '确认删除', content: `确认删除 ${row.sku}?`, danger: true }))) return
 * ```
 *
 * ⚠️ **调用处必须 `await`** —— 忘了会拿到一个恒真的 Promise,于是
 * **确认框还没点就已经执行了**。这是静默错误,不会有任何报错。
 */

import { reactive } from 'vue'

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

/** 宿主组件读的当前状态。`open` 为真时弹窗显示。 */
export const confirmState = reactive({
  open: false,
  title: '',
  content: '',
  okText: '确定',
  cancelText: '取消',
  danger: false,
})

/** 正在等答案的那个 Promise 的 resolve —— 同一时刻只允许一个确认框。 */
let pending: ((answer: boolean) => void) | null = null

/**
 * 弹出确认框。**点了确定 → `true`;取消 / 关掉 → `false`。**
 *
 * ⚠️ 与 `window.confirm` 不同,它是**异步**的 —— 调用处要 `await`。
 */
export function appConfirm(options: AppConfirmOptions): Promise<boolean> {
  // 上一个还没答完就又弹一个 —— 把上一个**判为"取消"**再顶掉。
  // 不这么做的话,那个 Promise **永远不 resolve**,调用处的 `await` 会一直挂着
  // (页面上表现为"点了没反应",而且不会有任何报错)。
  pending?.(false)

  confirmState.open = true
  confirmState.title = options.title
  confirmState.content = options.content ?? ''
  confirmState.okText = options.okText ?? '确定'
  confirmState.cancelText = options.cancelText ?? '取消'
  confirmState.danger = options.danger ?? false

  return new Promise<boolean>((resolve) => {
    pending = resolve
  })
}

/** 由宿主组件调用:用户在弹窗上做了选择。 */
export function settleConfirm(answer: boolean): void {
  confirmState.open = false
  const resolve = pending
  pending = null
  resolve?.(answer)
}
