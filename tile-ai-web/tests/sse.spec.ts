/**
 * SSE 帧解析(api/sse.ts)。
 *
 * 这一步最容易出的错是**跨块半帧**:网络返回的字节块边界与应用层的帧边界
 * 没有任何关系 —— 一帧可能被劈成两块。这里把它单独测,因为**它是纯函数**,
 * 不掺网络就能把边界钉死。
 *
 * ⚠️ 契约里 `/agent/chat` 的事件集**没有任何门禁能自动拦**(G1–G7 与契约比对
 * 都不解析 SSE,见 `openapi-verification.md` §46.2)。所以这类测试就是唯一判据。
 */

import { describe, expect, it } from 'vitest'

import { DONE_MARKER, parseSseFrames } from '../src/api/sse'

describe('parseSseFrames', () => {
  it('切出完整的帧,载荷是 data: 后面的内容', () => {
    const { events, rest } = parseSseFrames('data: {"delta":"浅"}\n\n', '')

    expect(events).toEqual(['{"delta":"浅"}'])
    expect(rest).toBe('')
  })

  it('一帧里的多行 data: 用换行连接', () => {
    const { events } = parseSseFrames('data: 第一行\ndata: 第二行\n\n', '')

    expect(events).toEqual(['第一行\n第二行'])
  })

  it('忽略注释行与 data 之外的字段', () => {
    const { events } = parseSseFrames(': keep-alive\nevent: message\nid: 7\ndata: {"x":1}\n\n', '')

    expect(events).toEqual(['{"x":1}'])
  })

  it('纯注释帧不产出空事件', () => {
    const { events, rest } = parseSseFrames(': ping\n\n', '')

    expect(events).toEqual([])
    expect(rest).toBe('')
  })

  it('认 \\r\\n\\r\\n 行尾', () => {
    const { events } = parseSseFrames('data: {"delta":"x"}\r\n\r\n', '')

    expect(events).toEqual(['{"delta":"x"}'])
  })

  it('**[DONE] 是字面量**,原样给出,不当 JSON 处理', () => {
    const { events } = parseSseFrames('data: [DONE]\n\n', '')

    expect(events).toEqual([DONE_MARKER])
  })

  it('**半帧留在缓冲里**,等下一块拼上', () => {
    const first = parseSseFrames('data: {"delta":"浅', '')

    expect(first.events).toEqual([])
    expect(first.rest).toBe('data: {"delta":"浅')

    const second = parseSseFrames('灰"}\n\n', first.rest)

    expect(second.events).toEqual(['{"delta":"浅灰"}'])
    expect(second.rest).toBe('')
  })

  it('一块里既有整帧又有半帧时,两边都对', () => {
    const { events, rest } = parseSseFrames('data: a\n\ndata: b\n\ndata: c', '')

    expect(events).toEqual(['a', 'b'])
    expect(rest).toBe('data: c')
  })

  it('data: 后面没有空格也认(SSE 的空格是可选的)', () => {
    const { events } = parseSseFrames('data:{"delta":"x"}\n\n', '')

    expect(events).toEqual(['{"delta":"x"}'])
  })
})
