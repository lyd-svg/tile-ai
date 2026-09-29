/**
 * C 端账号(openapi-c.yaml 的 auth 分组,基路径 /api/v1)。
 *
 * 登录方式:**用户名 + 密码**(v0.7 起)。**不要再引入手机号 / 短信 / 验证码** ——
 * 短信通道已取消(架构 §15.6 记录了原因)。
 *
 * ⚠️ **契约里没有 C 端的"取当前账号"接口**(只有注册/登录/改密/登出/刷新)。
 * 所以刷新页面后前端无法回查"当前是谁",只能显示未登录。
 * 后台那边补了 `GET /admin/auth/me`(§24),**C 端尚未补** ——
 * 等做生成/方案这类"必须登录"的页面时再一并处理。
 * 在那之前**不要**把账号信息缓存到 localStorage/sessionStorage:
 * 缓存身份等于把展示状态当凭证,账号被停用后界面仍会按旧状态渲染。
 */

import { API_BASE, request } from '../http'
import type { UserInfo } from '../types'

export interface RegisterRequest {
  username: string
  password: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/**
 * 注册 —— 成功即登录(契约:注册与登录**各是一次请求**,无两阶段流程)。
 *
 * 失败码:20003 用户名已注册、20004 密码格式不合规。
 */
export function register(payload: RegisterRequest): Promise<UserInfo> {
  return request<UserInfo>({ method: 'POST', path: `${API_BASE}/auth/register`, body: payload })
}

/**
 * 登录。
 *
 * **失败一律 20005,不区分「用户名不存在」与「密码错误」** ——
 * 区分开等于对外提供一个账号枚举接口。前端不要自作聪明地去分辨。
 */
export function login(payload: LoginRequest): Promise<UserInfo> {
  return request<UserInfo>({ method: 'POST', path: `${API_BASE}/auth/login`, body: payload })
}

/** 修改密码(需登录)。 */
export function changePassword(payload: ChangePasswordRequest): Promise<void> {
  return request<void>({ method: 'POST', path: `${API_BASE}/auth/password`, body: payload })
}

/** 退出登录。服务端拉黑 refresh 并清 Cookie。 */
export function logout(): Promise<void> {
  return request<void>({ method: 'POST', path: `${API_BASE}/auth/logout` })
}
