/**
 * 后台会话(openapi-admin.yaml 的 admin-auth 分组,基路径 /api/v1/admin)。
 *
 * 三个接口:**都不需要权限点** —— 它们是"我是谁"的接口,不是"我能做什么"。
 *
 * ⚠️ **契约里没有后台登出接口时,前端只能清本地状态**;v1.9 补上了 `/auth/logout`,
 * 现在登出会真的请求服务端(拉黑 refresh + 清 Cookie)。
 *
 * ⚠️ **已知缺口(2026-09-29 实测确认,尚未修复)**:`refresh_token` 的
 * `Path=/api/v1/auth`,而浏览器只在路径匹配时才发送/存储 Cookie ——
 * 后台登录在 `/api/v1/admin/auth/login`,该路径**不覆盖**它,
 * 于是**后台的 refresh 令牌会被浏览器直接丢弃**:浏览器里只有 `access_token`。
 * 后果:后台会话 2 小时后无法刷新,只能重新登录。
 * 详见 `openapi-verification.md` §24.4。前端此处不做规避(规避不了)。
 */

import { ADMIN_BASE, request } from '../http'
import type { AdminUserView } from '../types'

export interface AdminLoginRequest {
  username: string
  password: string
}

/** 后台登录。成功即由服务端 `Set-Cookie` 下发会话,令牌不进响应体。 */
export function adminLogin(payload: AdminLoginRequest): Promise<AdminUserView> {
  return request<AdminUserView>({
    method: 'POST',
    path: `${ADMIN_BASE}/auth/login`,
    body: payload,
  })
}

/**
 * 退出登录。
 *
 * 服务端会拉黑 refresh 令牌并下发两条清除 Cookie 的指令。
 * 但见文件头的已知缺口 —— 后台根本没有 refresh 令牌可供拉黑,
 * 实际效果是**清 Cookie**。
 */
export function adminLogout(): Promise<void> {
  return request<void>({ method: 'POST', path: `${ADMIN_BASE}/auth/logout` })
}

/**
 * 取当前登录账号 —— 页面刷新后恢复会话的唯一依据。
 *
 * 令牌在 HttpOnly Cookie 里,前端读不到,所以**身份只能问服务端**。
 * 未经此确认就把账号信息从本地缓存里读出来渲染,等于把展示状态当凭证:
 * 账号被停用或降权后,界面会继续按旧角色画。
 */
export function fetchAdminMe(): Promise<AdminUserView> {
  return request<AdminUserView>({ method: 'GET', path: `${ADMIN_BASE}/auth/me` })
}
