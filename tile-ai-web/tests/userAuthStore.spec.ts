/**
 * C 端登录态的会话恢复(步骤 0:`GET /auth/me`)。
 *
 * 这里验的是**刷新后能不能拿回身份**这件事本身 —— F-1.7 的路由守卫靠它,
 * 没有它,"必须登录"的页面刷新一次就会被踢出去。
 *
 * API 层被替换掉:要验的是 store 的判定逻辑,不是网络。
 * 真实的 401 / 10002 行为由 Java 侧 AuthIntegrationTest 验(那才是有数据库的那一层)。
 */

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { BusinessError, ErrorCode } from '../src/api/errors'
import { useUserAuthStore } from '../src/stores/userAuth'

const fetchMe = vi.fn()
const login = vi.fn()
const logout = vi.fn()
vi.mock('@/api/c/auth', () => ({
  fetchMe: () => fetchMe(),
  login: (...args: unknown[]) => login(...args),
  register: vi.fn(),
  logout: () => logout(),
  changePassword: vi.fn(),
}))

const ALICE = { userId: 1, username: 'alice' }

beforeEach(() => {
  setActivePinia(createPinia())
  fetchMe.mockReset()
  login.mockReset()
  logout.mockReset()
})

describe('ensureSession:刷新页面后恢复会话', () => {
  it('服务端认这个会话 → 写入身份并返回 true', async () => {
    fetchMe.mockResolvedValue(ALICE)
    const auth = useUserAuthStore()

    expect(auth.isAuthenticated).toBe(false) // 刷新后内存里什么都没有
    await expect(auth.ensureSession()).resolves.toBe(true)

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.displayName).toBe('alice')
  })

  it('会话已失效(401 / 10002)→ 清态并返回 false,而不是抛出去', async () => {
    fetchMe.mockRejectedValue(new BusinessError({ code: ErrorCode.UNAUTHENTICATED }))
    const auth = useUserAuthStore()

    await expect(auth.ensureSession()).resolves.toBe(false)
    expect(auth.isAuthenticated).toBe(false)
  })

  it('网络类错误同样返回 false —— 守卫不该因为一次请求抖动就抛异常', async () => {
    fetchMe.mockRejectedValue(new Error('服务返回 502'))
    const auth = useUserAuthStore()

    await expect(auth.ensureSession()).resolves.toBe(false)
    expect(auth.isAuthenticated).toBe(false)
  })

  it('确认过一次就不再重复请求(否则每次路由跳转都打一次 /me)', async () => {
    fetchMe.mockResolvedValue(ALICE)
    const auth = useUserAuthStore()

    await auth.ensureSession()
    await auth.ensureSession()

    expect(fetchMe).toHaveBeenCalledTimes(1)
  })

  it('force=true 时重新问服务端 —— 账号可能已被停用,缓存结论不算数', async () => {
    fetchMe.mockResolvedValue(ALICE)
    const auth = useUserAuthStore()
    await auth.ensureSession()

    fetchMe.mockRejectedValue(new BusinessError({ code: ErrorCode.UNAUTHENTICATED }))
    await expect(auth.ensureSession(true)).resolves.toBe(false)

    expect(fetchMe).toHaveBeenCalledTimes(2)
    expect(auth.isAuthenticated).toBe(false)
  })
})

describe('登录 / 登出与 verified 标记的配合', () => {
  it('刚登录成功就已是权威状态,不必再打一次 /me', async () => {
    login.mockResolvedValue(ALICE)
    const auth = useUserAuthStore()

    await auth.login('alice', 'secret-123')
    await auth.ensureSession()

    expect(auth.isAuthenticated).toBe(true)
    expect(fetchMe).not.toHaveBeenCalled()
  })

  it('登出后清态;再 ensureSession 会重新问服务端', async () => {
    login.mockResolvedValue(ALICE)
    logout.mockResolvedValue(undefined)
    const auth = useUserAuthStore()

    await auth.login('alice', 'secret-123')
    await auth.logout()
    expect(auth.isAuthenticated).toBe(false)

    fetchMe.mockRejectedValue(new BusinessError({ code: ErrorCode.UNAUTHENTICATED }))
    await expect(auth.ensureSession()).resolves.toBe(false)
    expect(fetchMe).toHaveBeenCalledTimes(1)
  })

  it('登出接口失败也要清本地 —— 不能因为网络抖动把用户卡在登录态里', async () => {
    login.mockResolvedValue(ALICE)
    logout.mockRejectedValue(new Error('网络断了'))
    const auth = useUserAuthStore()

    await auth.login('alice', 'secret-123')
    await auth.logout()

    expect(auth.isAuthenticated).toBe(false)
  })

  it('clearSession 同时复位 verified(会话被踢掉后必须能重新确认)', async () => {
    fetchMe.mockResolvedValue(ALICE)
    const auth = useUserAuthStore()
    await auth.ensureSession()

    auth.clearSession()
    await auth.ensureSession()

    expect(fetchMe).toHaveBeenCalledTimes(2)
  })
})
