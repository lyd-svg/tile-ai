/**
 * 业务错误码与文案(openapi-common.yaml#/components/schemas/ErrorCode)。
 *
 * ⚠️ **以契约的 `enum` 为准**。本文件的码与文案必须与它逐字对应 ——
 * `tests/contract.spec.ts` 会直接读契约 YAML 来断言这一点(不多不少),
 * 所以这里漏加或多加一个码,测试会红,不会悄悄漂移。
 *
 * 注意契约当前是 **17 个码**,不是 19 —— 别处文档里若还写着 19,那是旧数。
 */

/** 业务错误码。取值与来源见文件头。 */
export const ErrorCode = {
  SUCCESS: 0,
  PARAM_INVALID: 10001,
  UNAUTHENTICATED: 10002,
  TOKEN_EXPIRED: 10003,
  FORBIDDEN: 10004,
  RATE_LIMITED: 10005,
  USERNAME_TAKEN: 20003,
  PASSWORD_INVALID: 20004,
  BAD_CREDENTIALS: 20005,
  NOT_FOUND: 30001,
  STATUS_NOT_ALLOWED: 30002,
  GENERATION_FAILED: 40001,
  GENERATION_BUSY: 40002,
  TASK_NOT_FOUND: 40003,
  PART_COMPLETION_FAILED: 40004,
  AGENT_OUT_OF_SCOPE: 50001,
  INTERNAL_ERROR: 90001,
} as const

/**
 * 错误码 → 默认文案。
 *
 * 文案取自契约描述,**是兜底**:服务端 `message` 更具体时优先用服务端的。
 * 之所以还要这份兜底,是因为有些路径(如 401/403 的真实状态码响应)
 * 未必带回可读消息,那时界面上不该只显示一个数字。
 */
export const ERROR_MESSAGES: Record<number, string> = {
  0: '成功',
  10001: '参数校验失败',
  10002: '未登录/令牌无效',
  10003: '令牌过期需刷新',
  10004: '无权限',
  10005: '限流',
  20003: '用户名已注册',
  20004: '密码格式不合规',
  20005: '账号或密码错误',
  30001: '资源不存在',
  30002: '状态不允许该操作',
  40001: '生成任务失败',
  40002: '生成服务繁忙',
  40003: '任务不存在',
  40004: '部位补齐失败',
  50001: 'AI 咨询超出范围',
  90001: '系统内部错误',
}

/** 取文案:优先服务端给的,其次契约默认,最后给个带码的兜底。 */
export function messageFor(code: number, serverMessage?: string): string {
  if (serverMessage && serverMessage.trim() && serverMessage !== 'ok') {
    return serverMessage
  }
  return ERROR_MESSAGES[code] ?? `未知错误(${code})`
}

/**
 * 业务失败。业务错误统一 **HTTP 200**、成败看 `code`
 * (CLAUDE.md 约定),所以请求成功返回不代表业务成功 —— 必须靠异常把两者分开,
 * 否则调用方很容易拿着 `data` 是 null 的"成功响应"继续往下走。
 */
export class BusinessError extends Error {
  readonly code: number
  readonly traceId: string
  readonly data: unknown

  constructor(params: { code: number; message?: string; traceId?: string; data?: unknown }) {
    super(messageFor(params.code, params.message))
    this.name = 'BusinessError'
    this.code = params.code
    this.traceId = params.traceId ?? ''
    this.data = params.data
  }

  /** 未登录 / 令牌过期 —— 需要重新登录 */
  get isUnauthenticated(): boolean {
    return this.code === ErrorCode.UNAUTHENTICATED || this.code === ErrorCode.TOKEN_EXPIRED
  }

  /** 无权限 —— 已登录但这个角色不能做 */
  get isForbidden(): boolean {
    return this.code === ErrorCode.FORBIDDEN
  }

  /** 资源不存在 / 状态不允许 —— 常见于并发编辑后的陈旧页面 */
  get isConflict(): boolean {
    return this.code === ErrorCode.NOT_FOUND || this.code === ErrorCode.STATUS_NOT_ALLOWED
  }
}

/** 参数校验失败(10001)的明细元素。§10.5 */
export interface ErrorItem {
  field: string
  reason: string
  rejectedValue?: unknown
}

/** 从 BusinessError 里取校验明细 —— 10001 会把逐字段原因放在 data.errors[] */
export function validationErrorsOf(error: unknown): ErrorItem[] {
  if (!(error instanceof BusinessError)) return []
  const data = error.data
  if (data && typeof data === 'object' && 'errors' in data) {
    const errors = (data as { errors?: unknown }).errors
    if (Array.isArray(errors)) return errors as ErrorItem[]
  }
  return []
}
