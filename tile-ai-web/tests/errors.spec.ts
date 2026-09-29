/**
 * 业务错误对象的行为(F-0.1)。
 *
 * 码与文案的**完整性**由 `contract.spec.ts` 对着契约 YAML 断言;
 * 这里测的是拿到码之后怎么用 —— 分类判断与校验明细提取。
 */

import { describe, expect, it } from 'vitest'

import { BusinessError, ErrorCode, messageFor, validationErrorsOf } from '../src/api/errors'

describe('messageFor', () => {
  it('优先用服务端消息', () => {
    expect(messageFor(30001, 'SKU T-100 不存在')).toBe('SKU T-100 不存在')
  })

  it('服务端没给消息时回落契约文案', () => {
    expect(messageFor(30001)).toBe('资源不存在')
    expect(messageFor(30001, '   ')).toBe('资源不存在')
  })

  it('契约里的占位 "ok" 不算有效消息', () => {
    // 成功响应的 message 常常就是 "ok",若让它透传,失败提示会显示成 "ok"
    expect(messageFor(30001, 'ok')).toBe('资源不存在')
  })

  it('未知码也给出可读文案,不留一个裸数字', () => {
    expect(messageFor(12345)).toContain('12345')
  })
})

describe('BusinessError 分类', () => {
  it('未登录与令牌过期归为需要重新登录', () => {
    expect(new BusinessError({ code: ErrorCode.UNAUTHENTICATED }).isUnauthenticated).toBe(true)
    expect(new BusinessError({ code: ErrorCode.TOKEN_EXPIRED }).isUnauthenticated).toBe(true)
    expect(new BusinessError({ code: ErrorCode.FORBIDDEN }).isUnauthenticated).toBe(false)
  })

  it('无权限与未登录分得开 —— 处理方式不同(去登录 vs 提示权限不足)', () => {
    const forbidden = new BusinessError({ code: ErrorCode.FORBIDDEN })
    expect(forbidden.isForbidden).toBe(true)
    expect(forbidden.isUnauthenticated).toBe(false)
  })

  it('资源不存在与状态不允许归为冲突 —— 常见于并发编辑后的陈旧页面', () => {
    expect(new BusinessError({ code: ErrorCode.NOT_FOUND }).isConflict).toBe(true)
    expect(new BusinessError({ code: ErrorCode.STATUS_NOT_ALLOWED }).isConflict).toBe(true)
    expect(new BusinessError({ code: ErrorCode.PARAM_INVALID }).isConflict).toBe(false)
  })

  it('是 Error 的子类,能被统一 catch 与展示', () => {
    const error = new BusinessError({ code: 90001 })
    expect(error).toBeInstanceOf(Error)
    expect(error.name).toBe('BusinessError')
    expect(error.message).toBe('系统内部错误')
  })
})

describe('validationErrorsOf', () => {
  it('取出 10001 的逐字段明细', () => {
    const error = new BusinessError({
      code: ErrorCode.PARAM_INVALID,
      data: { errors: [{ field: 'parts[0].sku', reason: 'SKU 不存在' }] },
    })
    expect(validationErrorsOf(error)).toEqual([{ field: 'parts[0].sku', reason: 'SKU 不存在' }])
  })

  it('没有明细时返回空数组,而不是抛错', () => {
    expect(validationErrorsOf(new BusinessError({ code: ErrorCode.PARAM_INVALID }))).toEqual([])
    expect(validationErrorsOf(new Error('普通错误'))).toEqual([])
    expect(validationErrorsOf(null)).toEqual([])
  })
})
