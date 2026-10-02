/**
 * 流式对话客户端(api/c/chat.ts)。
 *
 * 这一层是**契约的落点**,而 SSE 事件集**没有任何门禁能自动拦**
 * (`openapi-verification.md` §46.2)—— 所以下面这些断言就是它唯一的判据。
 *
 * 四件必须钉住的:
 *   1. **跨块半帧**,包括**把一个汉字从字节中间劈开**(UTF-8 里一个汉字占 3 字节,
 *      网络块边界可不管这些);
 *   2. **`error` 帧是终态**:它之后不会再发 `[DONE]`,生成器必须当场收手;
 *   3. **静默断流要报错** —— 不能停在"像说完了"的样子;
 *   4. **流开始前的 401 走全站同一把单飞刷新 + 重放一次**(不是自己再写一份刷新)。
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { streamChat } from '../src/api/c/chat'
import type { ChatEvent } from '../src/api/c/chat'
import { HttpError } from '../src/api/http'
import { BusinessError } from '../src/api/errors'

const fetchMock = vi.fn()

/** 把若干**字节块**拼成一个 SSE 响应 —— 块边界就是网络边界。 */
function bytesResponse(chunks: Uint8Array[], contentType = 'text/event-stream'): Response {
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(chunk)
      controller.close()
    },
  })
  return new Response(stream, { status: 200, headers: { 'content-type': contentType } })
}

function textResponse(chunks: string[], contentType = 'text/event-stream'): Response {
  const encoder = new TextEncoder()
  return bytesResponse(chunks.map((chunk) => encoder.encode(chunk)), contentType)
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

/** 收集生成器的全部事件。 */
async function collect(opts: { message: string; sessionId?: string; signal?: AbortSignal }): Promise<ChatEvent[]> {
  const events: ChatEvent[] = []
  for await (const event of streamChat(opts)) events.push(event)
  return events
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

describe('streamChat', () => {
  it('按 D-7 的顺序产出事件:sessionId → delta… → refs → [DONE]', async () => {
    fetchMock.mockResolvedValue(
      textResponse([
        'data: {"sessionId":"23"}\n\n',
        'data: {"delta":"浅"}\n\n',
        'data: {"delta":"灰"}\n\n',
        'data: {"refs":[{"sku":"TL-1","name":"雅灰","part":"wall"}]}\n\n',
        'data: [DONE]\n\n',
      ]),
    )

    const events = await collect({ message: '卫生间配什么墙砖' })

    expect(events.map((event) => event.type)).toEqual(['session', 'delta', 'delta', 'refs', 'done'])
    expect(events[0]).toEqual({ type: 'session', sessionId: '23' })
    expect(events[3]).toEqual({ type: 'refs', refs: [{ sku: 'TL-1', name: '雅灰', part: 'wall' }] })
  })

  it('**半帧跨块**也能拼对(帧从中间被切开)', async () => {
    fetchMock.mockResolvedValue(
      textResponse(['data: {"delta":"浅', '灰"}\n\ndata: [DONE]\n\n']),
    )

    const events = await collect({ message: 'x' })

    expect(events).toEqual([{ type: 'delta', delta: '浅灰' }, { type: 'done' }])
  })

  it('**一个汉字被从字节中间劈成两块**也不出乱码', async () => {
    const encoder = new TextEncoder()
    const whole = encoder.encode('data: {"delta":"瓷砖"}\n\ndata: [DONE]\n\n')
    // 在第 20 个字节处切 —— 正好落在某个汉字的 UTF-8 序列中间
    fetchMock.mockResolvedValue(bytesResponse([whole.slice(0, 20), whole.slice(20)]))

    const events = await collect({ message: 'x' })

    expect(events).toEqual([{ type: 'delta', delta: '瓷砖' }, { type: 'done' }])
  })

  it('`error` 帧之后生成器立即结束,不再有 done', async () => {
    fetchMock.mockResolvedValue(
      textResponse([
        'data: {"delta":"半句"}\n\n',
        'data: {"error":{"code":50001,"message":"超出范围","traceId":"t-1"}}\n\n',
      ]),
    )

    const events = await collect({ message: '多少钱' })

    expect(events.at(-1)).toEqual({ type: 'error', code: 50001, message: '超出范围', traceId: 't-1' })
    expect(events.some((event) => event.type === 'done')).toBe(false)
  })

  it('**静默断流**(既没 done 也没 error)要报错,不能当成功', async () => {
    fetchMock.mockResolvedValue(textResponse(['data: {"delta":"说到一半"}\n\n']))

    await expect(collect({ message: 'x' })).rejects.toBeInstanceOf(HttpError)
  })

  it('refs 是空数组时照发(由页面决定不渲染)', async () => {
    fetchMock.mockResolvedValue(
      textResponse(['data: {"refs":[]}\n\n', 'data: [DONE]\n\n']),
    )

    const events = await collect({ message: 'x' })

    expect(events[0]).toEqual({ type: 'refs', refs: [] })
  })

  it('续聊时把 sessionId 放进请求体;首次则不出现这个键', async () => {
    // ⚠️ 每次调用都要给**新的** Response:响应体只能被读一次,
    // 复用同一个实例第二次 `getReader()` 会直接抛
    fetchMock.mockImplementation(async () => textResponse(['data: [DONE]\n\n']))

    await collect({ message: '接着问', sessionId: '23' })
    const withId = JSON.parse(fetchMock.mock.calls[0]![1].body as string)
    expect(withId).toEqual({ sessionId: '23', message: '接着问' })

    fetchMock.mockClear()
    await collect({ message: '第一问' })
    const without = JSON.parse(fetchMock.mock.calls[0]![1].body as string)
    expect(without).toEqual({ message: '第一问' })
    expect('sessionId' in without).toBe(false)
  })

  it('鉴权靠 Cookie:带 credentials,且**不出现 Authorization 头**', async () => {
    fetchMock.mockResolvedValue(textResponse(['data: [DONE]\n\n']))

    await collect({ message: 'x' })

    const init = fetchMock.mock.calls[0]![1] as RequestInit
    expect(init.credentials).toBe('include')
    expect(Object.keys(init.headers as Record<string, string>)).not.toContain('Authorization')
  })

  it('**流开始前的 401 走单飞刷新并重放一次**', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { code: 10002, message: '未登录或令牌无效', traceId: 't' }))
      .mockResolvedValueOnce(new Response(null, { status: 200 })) // /auth/refresh
      .mockResolvedValueOnce(textResponse(['data: [DONE]\n\n']))

    const events = await collect({ message: 'x' })

    expect(events).toEqual([{ type: 'done' }])
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1]![0]).toContain('/auth/refresh')
    expect(fetchMock.mock.calls[2]![0]).toContain('/agent/chat')
  })

  it('刷新也救不回来时,抛未登录的业务错误(而不是"格式错误")', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { code: 10002, message: '未登录或令牌无效', traceId: 't' }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))

    await expect(collect({ message: 'x' })).rejects.toBeInstanceOf(BusinessError)
  })

  it('非 2xx 且不是信封 → HttpError', async () => {
    fetchMock.mockResolvedValue(new Response('<html>502</html>', { status: 502 }))

    await expect(collect({ message: 'x' })).rejects.toBeInstanceOf(HttpError)
  })

  it('200 但响应不是流 → 按业务信封解(它表示开流前就失败了)', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { code: 50001, message: '超出范围', traceId: 't' }))

    await expect(collect({ message: '多少钱' })).rejects.toMatchObject({ code: 50001 })
  })

  it('数据解析不了就报错,不静默跳过', async () => {
    fetchMock.mockResolvedValue(textResponse(['data: {不是 json}\n\n']))

    await expect(collect({ message: 'x' })).rejects.toBeInstanceOf(HttpError)
  })

  it('用户中断 → 抛 AbortError(调用方要把它和协议错误分开)', async () => {
    const controller = new AbortController()
    fetchMock.mockImplementation(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () =>
            reject(new DOMException('Aborted', 'AbortError')),
          )
        }),
    )

    const pending = collect({ message: 'x', signal: controller.signal })
    controller.abort()

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
  })
})
