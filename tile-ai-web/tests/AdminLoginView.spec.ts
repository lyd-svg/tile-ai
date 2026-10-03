/**
 * 登录页的真实交互(F-0.2)。
 *
 * API 层被替换掉:**这里要验的是页面行为**(提示对不对、跳不跳转),
 * 不是网络。真实的登录连通性已由另一处验证 —— 起真实 Java + MySQL,
 * 用 admin/Admin@2026 实际登录过(见 tile-ai-tasks.md 的 F-0.2 进度块)。
 */

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { normalize } from './helpers/antd'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError, ErrorCode } from '../src/api/errors'
import AdminLoginView from '../src/views/admin/AdminLoginView.vue'
import { useAdminAuthStore } from '../src/stores/adminAuth'

const adminLogin = vi.fn()
const fetchAdminMe = vi.fn()
const adminLogout = vi.fn()
vi.mock('@/api/admin/auth', () => ({
  adminLogin: (...args: unknown[]) => adminLogin(...args),
  fetchAdminMe: () => fetchAdminMe(),
  adminLogout: () => adminLogout(),
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

async function mountLogin(redirect?: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = makeRouter()
  await router.push(redirect ? `/admin/login?redirect=${encodeURIComponent(redirect)}` : '/admin/login')
  await router.isReady()

  const wrapper = mount(AdminLoginView, { global: { plugins: [pinia, router] } })
  return { wrapper, router, auth: useAdminAuthStore() }
}

async function submit(wrapper: Awaited<ReturnType<typeof mountLogin>>['wrapper'], user = 'admin', pass = 'secret123') {
  const inputs = wrapper.findAll('input')
  await inputs[0].setValue(user)
  await inputs[1].setValue(pass)
  await wrapper.find('form').trigger('submit')
  // 等 Promise 链(登录 → 跳转)跑完
  await new Promise((resolve) => setTimeout(resolve, 0))
}

beforeEach(() => {
  adminLogin.mockReset()
  fetchAdminMe.mockReset()
  // 登录成功后跳转时会过路由守卫,守卫会确认会话
  fetchAdminMe.mockResolvedValue({ id: 1, username: 'admin', role: 'super_admin' })
})

describe('表单校验', () => {
  it('不填就提交时给出提示,且**不发请求**', async () => {
    const { wrapper } = await mountLogin()
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('请填写用户名与密码')
    expect(adminLogin).not.toHaveBeenCalled()
  })
})

describe('登录成功', () => {
  it('写入登录态并跳到后台首页', async () => {
    adminLogin.mockResolvedValue({ id: 1, username: 'admin', role: 'super_admin' })
    const { wrapper, router, auth } = await mountLogin()

    await submit(wrapper)

    expect(adminLogin).toHaveBeenCalledWith({ username: 'admin', password: 'secret123' })
    expect(auth.isAuthenticated).toBe(true)
    expect(router.currentRoute.value.name).toBe('admin-home')
  })

  it('带 redirect 时回到原目标,而不是一律进首页', async () => {
    adminLogin.mockResolvedValue({ id: 1, username: 'admin', role: 'super_admin' })
    const { wrapper, router } = await mountLogin('/admin/products')

    await submit(wrapper)

    expect(router.currentRoute.value.path).toBe('/admin/products')
  })
})

describe('登录失败', () => {
  it('显示契约文案「账号或密码错误」', async () => {
    adminLogin.mockRejectedValue(new BusinessError({ code: ErrorCode.BAD_CREDENTIALS }))
    const { wrapper, auth } = await mountLogin()

    await submit(wrapper, 'admin', 'wrong')

    expect(wrapper.text()).toContain('账号或密码错误')
    expect(auth.isAuthenticated).toBe(false)
  })

  it('失败后按钮不再转圈(loading 必须复位)', async () => {
    adminLogin.mockRejectedValue(new BusinessError({ code: ErrorCode.BAD_CREDENTIALS }))
    const { wrapper, auth } = await mountLogin()

    await submit(wrapper)

    expect(auth.loading).toBe(false)
    // 归一化空白:antd 会把「登录」渲染成「登 录」(测试不挂 App.vue,拿不到那条设置)
    expect(normalize(wrapper.find('button').text())).toBe('登录')
  })

  it('网络类错误也给出可读提示,而不是抛到控制台没人管', async () => {
    adminLogin.mockRejectedValue(new Error('服务返回 502'))
    const { wrapper } = await mountLogin()

    await submit(wrapper)

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })
})

describe('不区分「用户名不存在」与「密码错误」', () => {
  it('两种失败在界面上是同一句话 —— 否则这是个账号枚举接口', async () => {
    adminLogin.mockRejectedValue(new BusinessError({ code: ErrorCode.BAD_CREDENTIALS }))
    const first = await mountLogin()
    await submit(first.wrapper, 'admin', 'wrong')
    const wrongPasswordText = first.wrapper.find('[role="alert"]').text()

    adminLogin.mockRejectedValue(new BusinessError({ code: ErrorCode.BAD_CREDENTIALS }))
    const second = await mountLogin()
    await submit(second.wrapper, 'no-such-admin', 'wrong')
    const noSuchUserText = second.wrapper.find('[role="alert"]').text()

    expect(noSuchUserText).toBe(wrongPasswordText)
  })
})
