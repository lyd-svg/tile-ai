/**
 * C 端登录态。
 *
 * ⚠️ **只存在内存里,不落 localStorage/sessionStorage**。
 * 令牌在 HttpOnly Cookie 里,前端读不到;而契约里**没有 C 端的"取当前账号"接口**,
 * 所以刷新页面后拿不回身份 —— 那时显示未登录是**如实反映**,不是 bug。
 *
 * 把账号信息缓存下来会让界面"看起来还登着",而实际会话可能早已失效 ——
 * 那是把展示状态当凭证。后台侧已补 `/auth/me` 根治(§24),C 端待做。
 */

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { login as loginApi, logout as logoutApi, register as registerApi } from '@/api/c/auth'
import type { UserInfo } from '@/api/types'

export const useUserAuthStore = defineStore('userAuth', () => {
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  async function login(username: string, password: string): Promise<UserInfo> {
    loading.value = true
    try {
      const info = await loginApi({ username, password })
      user.value = info
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
      user.value = null
    }
  }

  function clearSession(): void {
    user.value = null
  }

  return { user, loading, isAuthenticated, displayName, login, register, logout, clearSession }
})
