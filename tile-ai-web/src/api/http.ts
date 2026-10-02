/**
 * HTTP 封装。
 *
 * 三条不可违反的约定(CLAUDE.md):
 *
 * 1. **前端只连 Java**(架构不变量 3)。这里只有一个 base,不存在第二个后端地址。
 * 2. **鉴权走 HttpOnly Cookie**,所以每个请求都带 `credentials: 'include'`,
 *    且**永远不出现 `Authorization` 头**。前端读不到令牌,也不需要读。
 * 3. **业务错误统一 HTTP 200**,成败看响应体 `code`;仅 401/403 用真实状态码。
 *    这两条路径都要处理,漏掉任何一条都会让调用方拿到"看着成功其实失败"的结果。
 */

import { BusinessError, ErrorCode, messageFor } from './errors'
import type { ApiResponse } from './types'

/** 后端基路径。B 端与 C 端两个前缀,均由 Java 提供。 */
export const API_BASE = '/api/v1'
export const ADMIN_BASE = `${API_BASE}/admin`

/**
 * 非业务错误(网络断了、网关 502、响应不是 JSON)。
 *
 * 与 `BusinessError` 分开是刻意的:业务错误该提示用户"哪里填错了",
 * 这类错误该提示"服务不可用,请重试" —— 混在一起会让用户对着一个
 * 网络故障去改表单。
 */
export class HttpError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'HttpError'
    this.status = status
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** 相对后端的路径,如 `${ADMIN_BASE}/products` */
  path: string
  query?: Record<string, unknown>
  body?: unknown
  signal?: AbortSignal
}

/**
 * 拼查询串。**空值一律不传** —— `?status=` 这种空参数在服务端会被当成
 * "筛选 status 为空字符串",而不是"不筛选",结果查不到东西还找不到原因。
 */
export function buildQuery(query?: Record<string, unknown>): string {
  if (!query) return ''
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item === undefined || item === null || item === '') continue
        params.append(key, String(item))
      }
      continue
    }
    params.append(key, String(value))
  }
  const serialised = params.toString()
  return serialised ? `?${serialised}` : ''
}

/**
 * 刷新中的那条请求。**全站共用一条** —— 多个请求同时 401 时,
 * 若各自去刷新,会打出 N 次刷新请求,而每次刷新都会签发新令牌,
 * 后到的那个可能已把先到的刷新令牌状态覆盖掉。共用一条则只刷一次。
 */
let refreshInFlight: Promise<boolean> | null = null

/** 该路径属于哪一端 —— 决定用哪个刷新端点与哪套 Cookie。 */
function refreshPathFor(path: string): string {
  return path.startsWith(ADMIN_BASE)
    ? `${ADMIN_BASE}/auth/refresh`
    : `${API_BASE}/auth/refresh`
}

/** 刷新端点自身不能触发刷新,否则失败时会无限递归。 */
function isAuthRefreshCall(path: string): boolean {
  return path.endsWith('/auth/refresh')
}

/**
 * 尝试刷新访问令牌。返回是否成功。
 *
 * 成功与否**不抛异常** —— 调用方只关心"要不要重试原请求";
 * 刷新失败本身就是"该重新登录了",由原请求的错误统一表达。
 *
 * ⚠️ **本函数对外导出,是给流式请求(`api/c/chat.ts`)复用的。**
 * SSE 那条路不能用 `request()`,但它**必须复用这一把单飞锁** ——
 * 见 `refreshInFlight` 的说明:各自刷新会打出 N 次刷新、且令牌轮转互相覆盖。
 * 所以宁可把这里导出,也不要让流式那条路自己再写一份。
 */
export async function tryRefreshSession(path: string): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight

  refreshInFlight = fetch(refreshPathFor(path), {
    method: 'POST',
    credentials: 'include',
  })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null
    })

  return refreshInFlight
}

/** 发一个请求并解包 `ApiResponse.data`。业务失败抛 `BusinessError`。 */
export async function request<T>(options: RequestOptions): Promise<T> {
  return send<T>(options, true)
}

async function send<T>(options: RequestOptions, allowRefresh: boolean): Promise<T> {
  const { method = 'GET', path, query, body, signal } = options

  const response = await fetch(`${path}${buildQuery(query)}`, {
    method,
    // 令牌是 HttpOnly Cookie,由浏览器自动带上
    credentials: 'include',
    headers: buildHeaders(body),
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })

  // 契约约定:仅 401/403 用真实状态码。响应体仍是 ApiResponse,尽量解出来 ——
  // 服务端的 code(10002 / 10004)比状态码更能说明是哪一种。
  if (response.status === 401 || response.status === 403) {
    const payload = await readJson<unknown>(response)

    // 401 多半只是 access 过期(2 小时),而 refresh 还有效(14 天)。
    // 静默续期一次再重放原请求,用户不该为一次正常的令牌轮转重登一遍。
    // **403 不刷新** —— 那是"没有权限",续期解决不了,只会白打一次请求。
    if (
      allowRefresh &&
      response.status === 401 &&
      !isAuthRefreshCall(path) &&
      (await tryRefreshSession(path))
    ) {
      return send<T>(options, false) // 只重试一次,避免刷新成功但原请求仍 401 时死循环
    }

    throw new BusinessError({
      code:
        payload?.code ??
        (response.status === 401 ? ErrorCode.UNAUTHENTICATED : ErrorCode.FORBIDDEN),
      message: payload?.message,
      traceId: payload?.traceId,
      data: payload?.data,
    })
  }

  if (!response.ok) {
    throw new HttpError(
      response.status,
      `服务返回 ${response.status}${response.statusText ? ` ${response.statusText}` : ''}`,
    )
  }

  const payload = await readJson<T>(response)
  if (payload === null) {
    throw new HttpError(response.status, '响应不是合法的 JSON')
  }

  // 10003「令牌过期需刷新」也会以 HTTP 200 回来 —— 同样静默续期一次
  if (
    payload.code === ErrorCode.TOKEN_EXPIRED &&
    allowRefresh &&
    !isAuthRefreshCall(path) &&
    (await tryRefreshSession(path))
  ) {
    return send<T>(options, false)
  }

  if (payload.code !== ErrorCode.SUCCESS) {
    throw new BusinessError({
      code: payload.code,
      message: payload.message,
      traceId: payload.traceId,
      data: payload.data,
    })
  }

  // data 可为 null('无数据'),此时返回 undefined 更贴合 TS 的空值语义
  return (payload.data ?? undefined) as T
}

function buildHeaders(body: unknown): Record<string, string> {
  // 注意:这里**没有** Authorization —— 鉴权靠 Cookie(CLAUDE.md)
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  return headers
}

async function readJson<T>(response: Response): Promise<ApiResponse<T> | null> {
  try {
    return (await response.json()) as ApiResponse<T>
  } catch {
    return null
  }
}

/** PATCH 语义(SKIP/None)在个别接口里出现,单独留个出口便于复用。 */
export { messageFor }
