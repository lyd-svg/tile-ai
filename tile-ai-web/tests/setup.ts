/**
 * 测试全局钩子 —— 由 `vitest.config.ts` 的 `setupFiles` 加载,**每个测试文件都会跑一次**。
 *
 * ## 这个文件只做一件事:清 portal
 *
 * antd 的弹层类组件(`Select` 的**下拉**、`Modal`、`Message`、`Tooltip` …)是**渲染到
 * `document.body` 的**(portal / teleport),**@vue/test-utils 的 `wrapper` 管不到它们,
 * 测试结束也不会自动回收**。
 *
 * ⇒ 不清的话,**上一条用例打开的下拉会漏到下一条用例里**,表现为
 * "**一条毫不相干的用例莫名其妙地红了**" —— 实测踩到:某条"下拉恒为三个值"的用例
 * 收到了 **5** 个选项,多出来的两个来自**上一条**用例。
 * 见 `openapi-verification.md` §70.8。
 *
 * ## 为什么放全局,而不是每个文件自己写
 *
 * 靠"每个文件都记得加"是**靠不住的** —— 漏一个文件就多一个极难定位的 flaky。
 * 而 `mount()` 默认**不把组件挂进 document**(它是游离的),所以清 `document.body`
 * **不会伤到组件本身**,只清掉了 portal。⇒ 全局做是安全的。
 */
import { afterEach } from 'vitest'

afterEach(() => {
  document.body.innerHTML = ''
})
