/**
 * C 端登录态。
 *
 * ⚠️ **只存在内存里,不落 localStorage/sessionStorage**。
 * 令牌在 HttpOnly Cookie 里,前端读不到;身份**只从 `/auth/me` 来** ——
 * 刷新页面时调一次它,以**服务端当下的事实**为准。
 *
 * 把账号信息缓存下来会让界面"看起来还登着",而实际会话可能早已失效 ——
 * 那是把展示状态当凭证。账号被停用后,缓存的旧身份会让界面继续按旧状态渲染。
 */

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  fetchMe,
  login as loginApi,
  logout as logoutApi,
  register as registerApi,
} from '@/api/c/auth'
import type { UserInfo } from '@/api/types'

export const useUserAuthStore = defineStore('userAuth', () => {
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)
  /** 本次页面加载里是否已向服务端确认过身份 —— 避免每次路由跳转都打一次 /me */
  const verified = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  async function login(username: string, password: string): Promise<UserInfo> {
    loading.value = true
    try {
      const info = await loginApi({ username, password })
      user.value = info
      verified.value = true
      return info
    } finally {
      loading.value = false
    }
  }

  async function register(username: string, password: string): Promise<UserInfo> {
    loading.value = true
    try {
      // 注册成功即登录(契约:一次请求完成)
      const info = await registerApi({ username, password })
      user.value = info
      verified.value = true
      return info
    } finally {
      loading.value = false
    }
  }

  /** 无论服务端是否成功都清本地 —— 登出不能因为网络抖动把用户卡在登录态里。 */
  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // 有意吞掉:清本地是必须的
    } finally {
      clearSession()
    }
  }

  /**
   * 向服务端确认当前会话,并刷新账号信息。
   *
   * 每次页面加载调一次即可(`verified` 记住结果);返回 false 表示会话无效,
   * 调用方应跳登录页。**这是权威判定**,路由守卫据此放行。
   */
  async function ensureSession(force = false): Promise<boolean> {
    if (verified.value && !force) return isAuthenticated.value
    try {
      user.value = await fetchMe()
      verified.value = true
      return true
    } catch {
      // 401 / 10002(会话失效或账号已停用)都归到这里 —— 都要重新登录
      clearSession()
      return false
    }
  }

  function clearSession(): void {
    user.value = null
    verified.value = false
  }

  return {
    user,
    loading,
    isAuthenticated,
    displayName,
    login,
    register,
    logout,
    ensureSession,
    clearSession,
  }
})
