/**
 * SSE 帧的**纯解析** —— 不碰网络,所以可以单独测「半帧」这种事。
 *
 * 为什么不直接用 `EventSource`:契约里 `/agent/chat` 是 **POST + JSON body**,
 * 而 `EventSource` **只支持 GET、不能带请求体**。所以只能 `fetch` + 读流 + 自己切帧,
 * 切帧这一步就落在这里。
 *
 * ## 两处必须做对的地方
 *
 * 1. **跨 chunk 的半帧要缓冲**。网络返回的字节块边界与应用层的帧边界**没有任何关系**:
 *    一帧可能被劈成两块,两块之间还可能夹着下一帧的开头。所以每块攒进 `buffer`,
 *    按 `\n\n` 切,**最后一段不完整的留在 `buffer` 里**等下一块。
 * 2. **多字节字符也可能被劈开**(一个汉字的 UTF-8 占 3 字节)。这一点在解码层处理
 *    (`TextDecoder.decode(value, { stream: true })`,见 `c/chat.ts`),
 *    到这里的 `chunk` 已经是完整字符了 —— 但**切帧仍要跨块缓冲**,两件事不能混。
 */

/** 帧分隔符。SSE 允许 `\n\n` 与 `\r\n\r\n` 两种行尾。 */
const FRAME_SEPARATOR = /\r?\n\r?\n/

/** 结束标记。**是字面量,不是 JSON** —— 解析方不要拿它去 `JSON.parse`。 */
export const DONE_MARKER = '[DONE]'

/**
 * 从 `buffer + chunk` 里切出**完整的**帧,返回帧载荷与剩余的不完整尾巴。
 *
 * 载荷取 `data:` 行(去掉冒号后**至多一个**前导空格,SSE 规范如此);
 * 一帧里有多行 `data:` 时按 `\n` 连接。
 * `:` 开头的注释行、`event:` / `id:` / `retry:` 字段一律忽略 ——
 * 契约里**所有帧都是默认 `data:` 帧、没有事件名**,所以忽略它们不会丢信息。
 */
export function parseSseFrames(chunk: string, buffer: string): { events: string[]; rest: string } {
  const combined = buffer + chunk
  const blocks = combined.split(FRAME_SEPARATOR)

  // 最后一段可能是半帧(也可能正好是空串:那说明 chunk 恰好以分隔符结尾)
  const rest = blocks.pop() ?? ''

  const events: string[] = []
  for (const block of blocks) {
    const payload = dataOf(block)
    if (payload !== null) events.push(payload)
  }
  return { events, rest }
}

/** 取一帧里的 `data:` 载荷。纯注释帧返回 `null`(跳过,不产出空事件)。 */
function dataOf(block: string): string | null {
  const lines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (!line.startsWith('data:')) continue
    const value = line.slice('data:'.length)
    lines.push(value.startsWith(' ') ? value.slice(1) : value)
  }
  return lines.length > 0 ? lines.join('\n') : null
}
