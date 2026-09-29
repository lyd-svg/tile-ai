/**
 * 后台登录态。
 *
 * **令牌不在前端** —— 服务端用 HttpOnly Cookie 下发会话,JS 读不到也不需要读
 * (CLAUDE.md:不用 Authorization 头,令牌不进响应体)。
 * 这个 store 只保存**用来渲染界面**的账号信息,且**只从 `/auth/me` 来**。
 *
 * ⚠️ **v1.9 起不再用 sessionStorage 缓存身份。**
 * 之前因为契约里没有"取当前账号"接口,刷新后无法回查用户,只好把 AdminUserView
 * 缓存一份。那是把**展示状态**当成了凭证来源 —— 账号被停用或降权后,界面会继续
 * 按旧角色渲染。现在每次页面加载都问一次服务端,以**当下的事实**为准。
 */

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { adminLogin, adminLogout, fetchAdminMe } from '@/api/admin/auth'
import { canAny } from '@/auth/permissions'
import type { Permission } from '@/auth/permissions'
import type { AdminUserView, Role } from '@/api/types'

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const user = ref<AdminUserView | null>(null)
  const loading = ref(false)
  /** 本次页面加载里是否已向服务端确认过身份 —— 避免每次路由跳转都打一次 /me */
  const verified = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const role = computed<Role | null>(() => user.value?.role ?? null)

  async function login(username: string, password: string): Promise<AdminUserView> {
    loading.value = true
    try {
      const account = await adminLogin({ username, password })
      user.value = account
      verified.value = true
      return account
    } finally {
      // 无论成败都要复位,否则失败后按钮永远转圈
      loading.value = false
    }
  }

  /**
   * 退出登录。
   *
   * 无论服务端是否成功,**本地状态一定要清** —— 登出不能因为网络抖动或
   * 会话已过期而把用户卡在登录态里。服务端失败只意味着 Cookie 可能没被清干净。
   */
  async function logout(): Promise<void> {
    try {
      await adminLogout()
    } catch {
      // 有意吞掉:清本地是必须的,失败与否都不改变这个结论
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
      user.value = await fetchAdminMe()
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

  function has(permission: Permission): boolean {
    return canAny(role.value, [permission])
  }

  function hasAny(permissions: readonly Permission[]): boolean {
    return canAny(role.value, permissions)
  }

  return {
    user,
    loading,
    isAuthenticated,
    role,
    login,
    logout,
    ensureSession,
    clearSession,
    has,
    hasAny,
  }
})
