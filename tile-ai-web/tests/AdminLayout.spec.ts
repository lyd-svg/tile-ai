/**
 * 后台外壳的**真实渲染**(F-0.2)。
 *
 * `permissions.spec.ts` 验的是菜单过滤**逻辑**;这里验的是它真的画出来了 ——
 * 逻辑对而页面没接上,是两回事,只有渲染测试能发现。
 *
 * 用真 router(memory history)而不是桩:RouterLink 渲染出的 `href` 本身就是
 * 断言的一部分 —— 菜单项指错页面同样是 bug。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Role } from '../src/api/types'
import AdminLayout from '../src/layouts/AdminLayout.vue'
import { useAdminAuthStore } from '../src/stores/adminAuth'

// v1.9 起身份**只从服务端来**(`/auth/me`),不再有本地缓存。
// 所以这里替身的是会话 API,而不是往 sessionStorage 里塞东西。
const fetchAdminMe = vi.fn()
const adminLogout = vi.fn()
vi.mock('@/api/admin/auth', () => ({
  fetchAdminMe: () => fetchAdminMe(),
  adminLogout: () => adminLogout(),
  adminLogin: vi.fn(),
}))

const Blank = { template: '<div />' }

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Blank },
      { path: '/admin', name: 'admin-home', component: Blank },
      { path: '/admin/login', name: 'admin-login', component: Blank },
      { path: '/admin/products', name: 'admin-products', component: Blank },
    ],
  })
}

async function mountAs(role: Role) {
  // 走真实的身份确认路径:store.ensureSession() 会调 `/auth/me`。
  // **不直接给 store.user 赋值** —— 那会绕过整个"身份从服务端来"的设计,
  // 让"账号停用后界面不该继续按旧角色渲染"这类断言失去意义。
  fetchAdminMe.mockResolvedValue({ id: 1, username: 'tester', role })

  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  await auth.ensureSession()

  const router = makeRouter()
  await router.push('/admin')
  await router.isReady()

  const wrapper = mount(AdminLayout, { global: { plugins: [pinia, router] } })
  return { wrapper, auth, router }
}

const labels = (wrapper: ReturnType<typeof mount>): string[] =>
  wrapper.findAll('a').map((anchor) => anchor.text())

beforeEach(() => {
  fetchAdminMe.mockReset()
  adminLogout.mockReset()
  adminLogout.mockResolvedValue(undefined)
})

describe('菜单按角色渲染', () => {
  it('super_admin 看得到全部模块入口', async () => {
    const { wrapper } = await mountAs('super_admin')
    const items = labels(wrapper)

    for (const expected of ['概览', '产品库', '批量导入', '推荐搭配', '户型模板', '报价参数', '账号与权限']) {
      expect(items, `super_admin 应看到「${expected}」`).toContain(expected)
    }
  })

  it('operator 看得到批量导入,但看不到报价参数与账号', async () => {
    const { wrapper } = await mountAs('operator')
    const items = labels(wrapper)

    expect(items).toContain('批量导入')
    expect(items).toContain('操作日志')
    expect(items).not.toContain('报价参数')
    expect(items).not.toContain('账号与权限')
  })

  it('editor 只保留产品库与搭配 —— 连菜单项都不该出现', async () => {
    const { wrapper } = await mountAs('editor')
    const items = labels(wrapper)

    expect(items).toContain('产品库')
    expect(items).toContain('推荐搭配')
    expect(items).toContain('数据字典')
    expect(items).not.toContain('报价参数')
    expect(items).not.toContain('批量导入')
    expect(items).not.toContain('账号与权限')
    expect(items).not.toContain('操作日志')
  })

  it('菜单项的 href 指向对应路由 —— 逻辑对但指错页面同样是 bug', async () => {
    const { wrapper } = await mountAs('super_admin')
    const hrefs = wrapper.findAll('a').map((anchor) => anchor.attributes('href'))

    expect(hrefs).toContain('/admin/products')
    expect(hrefs).toContain('/admin/quote-params')
  })
})

describe('顶部账号信息', () => {
  it('显示当前用户名与角色', async () => {
    const { wrapper } = await mountAs('operator')
    expect(wrapper.text()).toContain('tester')
    expect(wrapper.text()).toContain('operator')
  })
})

describe('退出登录', () => {
  it('清空登录态并回到登录页', async () => {
    const { wrapper, auth, router } = await mountAs('super_admin')
    expect(auth.isAuthenticated).toBe(true)

    await wrapper.find('button').trigger('click')
    // router.push 是异步的,组件里没有 await 它 —— 必须让微任务跑完再断言
    await flushPromises()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.user).toBeNull()
    expect(router.currentRoute.value.name).toBe('admin-login')
  })

  it('登出会真的请求服务端(v1.9 起有接口了)', async () => {
    const { wrapper } = await mountAs('super_admin')

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(adminLogout).toHaveBeenCalledTimes(1)
  })

  it('服务端登出失败也必须清本地 —— 不能把用户卡在登录态里', async () => {
    adminLogout.mockRejectedValue(new Error('服务返回 502'))
    const { wrapper, auth, router } = await mountAs('super_admin')

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(auth.isAuthenticated).toBe(false)
    expect(router.currentRoute.value.name).toBe('admin-login')
  })
})
