/**
 * HTTP 封装的契约行为(F-0.1)。
 *
 * 这里测的是**封装层对契约的解释**,不是网络 —— 用替身 fetch 精确控制
 * 每一条分支:业务成功、业务失败(HTTP 200)、401/403(真实状态码)、
 * 非 JSON、网络故障。
 */

import { afterEach, describe, expect, it, vi } from 'vitest'

import { BusinessError, ErrorCode } from '../src/api/errors'
import { HttpError, buildQuery, request, upload } from '../src/api/http'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function stubFetch(implementation: (input: RequestInfo | URL, init?: RequestInit) => Response) {
  const spy = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => implementation(input, init))
  vi.stubGlobal('fetch', spy)
  return spy
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('buildQuery', () => {
  it('跳过空值 —— 空参数会被服务端当成"筛这个字段为空",而不是"不筛"', () => {
    expect(buildQuery({ keyword: '', status: undefined, page: null, size: 10 })).toBe('?size=10')
  })

  it('没有有效参数时不留一个孤零零的问号', () => {
    expect(buildQuery({ keyword: '' })).toBe('')
    expect(buildQuery(undefined)).toBe('')
  })

  it('数组展开为重复键', () => {
    expect(buildQuery({ space: ['bathroom', 'kitchen'] })).toBe('?space=bathroom&space=kitchen')
  })
})

describe('request 解包', () => {
  it('code=0 时返回 data', async () => {
    stubFetch(() => jsonResponse({ code: 0, message: 'ok', traceId: 't1', data: { total: 3 } }))
    await expect(request({ path: '/x' })).resolves.toEqual({ total: 3 })
  })

  it('data 为 null 时返回 undefined(契约允许"无数据")', async () => {
    stubFetch(() => jsonResponse({ code: 0, message: 'ok', traceId: 't1', data: null }))
    await expect(request({ path: '/x' })).resolves.toBeUndefined()
  })

  it('业务失败虽是 HTTP 200,也必须抛错', async () => {
    // CLAUDE.md:业务错误统一 HTTP 200,成败看 code。
    // 若把 200 一律当成功,调用方会拿着 null 的 data 继续往下走。
    stubFetch(() => jsonResponse({ code: 30002, message: '状态不允许该操作', traceId: 't2' }))
    await expect(request({ path: '/x' })).rejects.toBeInstanceOf(BusinessError)
  })

  it('业务失败要带上 traceId —— 没有它后端定位不到这次请求', async () => {
    stubFetch(() => jsonResponse({ code: 90001, message: '', traceId: 'trace-abc' }))
    await expect(request({ path: '/x' })).rejects.toMatchObject({ code: 90001, traceId: 'trace-abc' })
  })

  it('message 为空时回落到契约文案', async () => {
    stubFetch(() => jsonResponse({ code: 30001, message: '', traceId: 't' }))
    await expect(request({ path: '/x' })).rejects.toThrow('资源不存在')
  })

  it('服务端 message 优先于契约文案', async () => {
    stubFetch(() => jsonResponse({ code: 30001, message: 'SKU T-100 不存在', traceId: 't' }))
    await expect(request({ path: '/x' })).rejects.toThrow('SKU T-100 不存在')
  })
})

describe('401 / 403 走真实状态码(契约唯一例外)', () => {
  it('401 取响应体里的 code(10002),而不是只看状态码', async () => {
    stubFetch(() => jsonResponse({ code: 10002, message: '未登录', traceId: 't' }, 401))
    await expect(request({ path: '/x' })).rejects.toMatchObject({
      code: ErrorCode.UNAUTHENTICATED,
    })
  })

  it('401 且响应体不可解析时仍给出未登录', async () => {
    stubFetch(() => new Response('<html>gateway</html>', { status: 401 }))
    await expect(request({ path: '/x' })).rejects.toMatchObject({
      code: ErrorCode.UNAUTHENTICATED,
    })
  })

  it('403 归为无权限(10004)', async () => {
    stubFetch(() => jsonResponse({ code: 10004, message: '无权限', traceId: 't' }, 403))
    await expect(request({ path: '/x' })).rejects.toMatchObject({ code: ErrorCode.FORBIDDEN })
  })
})

describe('令牌过期时静默续期一次(v2.0)', () => {
  it('401 后刷新成功 → 自动重放原请求,用户无感', async () => {
    let call = 0
    const spy = stubFetch((input) => {
      call += 1
      if (String(input).includes('/auth/refresh')) {
        return jsonResponse({ code: 0, message: 'ok', traceId: 'r', data: null })
      }
      if (call === 1) return jsonResponse({ code: 10002, message: '过期', traceId: 't' }, 401)
      return jsonResponse({ code: 0, message: 'ok', traceId: 't', data: { ok: true } })
    })

    await expect(request({ path: '/api/v1/admin/products' })).resolves.toEqual({ ok: true })
    expect(spy).toHaveBeenCalledTimes(3) // 原请求 → 刷新 → 重放
    expect(String(spy.mock.calls[1][0])).toContain('/api/v1/admin/auth/refresh')
  })

  it('后台路径用后台的刷新端点,C 端路径用 C 端的', async () => {
    const spy = stubFetch((input) => {
      if (String(input).includes('/auth/refresh')) {
        return jsonResponse({ code: 0, message: 'ok', traceId: 'r', data: null })
      }
      return jsonResponse({ code: 10002, message: '过期', traceId: 't' }, 401)
    })

    await request({ path: '/api/v1/products' }).catch(() => {})
    expect(String(spy.mock.calls[1][0])).toContain('/api/v1/auth/refresh')

    spy.mockClear()
    await request({ path: '/api/v1/admin/products' }).catch(() => {})
    expect(String(spy.mock.calls[1][0])).toContain('/api/v1/admin/auth/refresh')
  })

  it('刷新失败 → 仍抛未登录,交给上层跳登录页', async () => {
    stubFetch(() => jsonResponse({ code: 10002, message: '未登录', traceId: 't' }, 401))
    await expect(request({ path: '/api/v1/admin/products' })).rejects.toMatchObject({
      code: ErrorCode.UNAUTHENTICATED,
    })
  })

  it('403 不触发刷新 —— 那是没权限,续期解决不了', async () => {
    const spy = stubFetch(() =>
      jsonResponse({ code: 10004, message: '无权限', traceId: 't' }, 403),
    )
    await expect(request({ path: '/api/v1/admin/quote-params' })).rejects.toBeInstanceOf(
      BusinessError,
    )
    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('刷新端点自己 401 时不再递归刷新', async () => {
    const spy = stubFetch(() => jsonResponse({ code: 10002, message: 'x', traceId: 't' }, 401))
    await expect(request({ path: '/api/v1/auth/refresh' })).rejects.toBeInstanceOf(BusinessError)
    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('只重试一次 —— 刷新成功但原请求仍 401 时不能死循环', async () => {
    const spy = stubFetch((input) => {
      if (String(input).includes('/auth/refresh')) {
        return jsonResponse({ code: 0, message: 'ok', traceId: 'r', data: null })
      }
      return jsonResponse({ code: 10002, message: '还是过期', traceId: 't' }, 401)
    })
    await expect(request({ path: '/api/v1/admin/products' })).rejects.toBeInstanceOf(BusinessError)
    expect(spy).toHaveBeenCalledTimes(3) // 原请求 → 刷新 → 重放一次后放弃
  })

  it('HTTP 200 + 10003 也续期(契约把"令牌过期需刷新"单列了一个码)', async () => {
    let call = 0
    const spy = stubFetch((input) => {
      call += 1
      if (String(input).includes('/auth/refresh')) {
        return jsonResponse({ code: 0, message: 'ok', traceId: 'r', data: null })
      }
      if (call === 1) return jsonResponse({ code: 10003, message: '需刷新', traceId: 't' })
      return jsonResponse({ code: 0, message: 'ok', traceId: 't', data: { ok: true } })
    })

    await expect(request({ path: '/api/v1/products' })).resolves.toEqual({ ok: true })
    expect(spy).toHaveBeenCalledTimes(3)
  })
})

describe('非业务故障走 HttpError,与业务错误分开', () => {
  it('502 是服务不可用,不该提示用户去改表单', async () => {
    stubFetch(() => new Response('bad gateway', { status: 502 }))
    await expect(request({ path: '/x' })).rejects.toBeInstanceOf(HttpError)
  })

  it('响应不是 JSON 时抛 HttpError', async () => {
    stubFetch(() => new Response('<html>', { status: 200 }))
    await expect(request({ path: '/x' })).rejects.toBeInstanceOf(HttpError)
  })
})

describe('请求本身的约定', () => {
  it('始终带 credentials:include —— 令牌是 HttpOnly Cookie', async () => {
    const spy = stubFetch(() => jsonResponse({ code: 0, message: 'ok', traceId: 't', data: {} }))
    await request({ path: '/x' })
    expect(spy.mock.calls[0][1]?.credentials).toBe('include')
  })

  it('不出现 Authorization 头 —— 这是 CLAUDE.md 明令禁止的', async () => {
    const spy = stubFetch(() => jsonResponse({ code: 0, message: 'ok', traceId: 't', data: {} }))
    await request({ path: '/x', body: { a: 1 } })
    const headers = spy.mock.calls[0][1]?.headers as Record<string, string>
    expect(Object.keys(headers).some((key) => key.toLowerCase() === 'authorization')).toBe(false)
  })

  it('带 body 时才设 Content-Type', async () => {
    const spy = stubFetch(() => jsonResponse({ code: 0, message: 'ok', traceId: 't', data: {} }))
    await request({ path: '/x' })
    const headers = spy.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers['Content-Type']).toBeUndefined()
  })
})

describe('upload(multipart)', () => {
  function formOf(file: File, mode: string): FormData {
    const form = new FormData()
    form.append('file', file)
    form.append('mode', mode)
    return form
  }

  it('把 FormData 原样交给 fetch,**不设 Content-Type**(boundary 得由浏览器算)', async () => {
    const spy = stubFetch(() => jsonResponse({ code: 0, message: 'ok', traceId: 't', data: { batchId: 'b1' } }))
    const form = formOf(new File(['x'], 'a.csv'), 'upsert')

    await upload('/x', form)

    const init = spy.mock.calls[0][1] as RequestInit
    expect(init.body).toBe(form)
    // 设了 Content-Type 就会丢掉 boundary,服务端直接解析不了
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined()
    expect(init.credentials).toBe('include')
  })

  it('同样是解包 ApiResponse、业务失败抛 BusinessError', async () => {
    stubFetch(() => jsonResponse({ code: 0, message: 'ok', traceId: 't', data: { batchId: 'b1' } }))
    await expect(upload('/x', formOf(new File(['x'], 'a.csv'), 'upsert'))).resolves.toEqual({
      batchId: 'b1',
    })

    stubFetch(() => jsonResponse({ code: 10001, message: '参数校验失败', traceId: 't' }))
    await expect(upload('/x', formOf(new File(['x'], 'a.csv'), 'upsert'))).rejects.toBeInstanceOf(
      BusinessError,
    )
  })

  it('401 时静默刷新一次再重放,且重放的**还是原来那个 FormData**', async () => {
    const form = formOf(new File(['x'], 'a.csv'), 'insert_only')
    let call = 0
    const spy = stubFetch(() => {
      call += 1
      if (call === 1) return jsonResponse({ code: 10002, message: '未登录', traceId: 't' }, 401)
      if (call === 2) return new Response(null, { status: 200 }) // /auth/refresh
      return jsonResponse({ code: 0, message: 'ok', traceId: 't', data: { batchId: 'b2' } })
    })

    await expect(upload('/x', form)).resolves.toEqual({ batchId: 'b2' })

    expect(spy).toHaveBeenCalledTimes(3)
    expect((spy.mock.calls[2][1] as RequestInit).body).toBe(form)
  })
})
