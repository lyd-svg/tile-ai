/**
 * AI 咨询(F-1.6;openapi-c.yaml 的 agent 分组,基路径 /api/v1)。
 *
 * 三条接口:流式问答(`POST /agent/chat`,SSE)+ 两条对话记录(列表 / 详情)。
 * 三条都要登录(契约里都是 `security: cookieAuth`)。
 *
 * ## 为什么这里不能复用 `request<T>()`
 *
 * `request<T>()` 会 `await response.json()` 把响应体**一次性读完**,并且按
 * `ApiResponse.code` 解包 —— 而流式响应是 `text/event-stream`,要的是**边收边处理**。
 * 所以本文件自带一个 `streamChat`。但它**仍然遵守 `http.ts` 的三条约定**:
 * 只连 Java、`credentials: 'include'`、**永不发 `Authorization` 头**。
 *
 * ## 为什么不用 `EventSource`
 *
 * 契约里 `/agent/chat` 是 **POST + JSON body**,而 `EventSource` **只支持 GET、
 * 不能带请求体**。只能 `fetch` + 读流 + 手工切帧(切帧逻辑在 `../sse.ts`)。
 * 附带的好处:自持 `TextDecoder('utf-8')`,不依赖响应头里的 charset。
 *
 * ## 事件集(D-7 冻结,契约原文)
 *
 * 正常:`{"sessionId"}`(**仅首帧**)→ 若干 `{"delta"}` → 至多一帧 `{"refs"}` → `[DONE]`
 * 失败:可能若干 `delta` 后 `{"error":{code,message,traceId}}` 为**最后一帧**,
 * **不再发 `[DONE]`** —— 两种结局**互斥**。
 */

import { API_BASE, HttpError, request, tryRefreshSession } from '../http'
import { BusinessError, ErrorCode } from '../errors'
import { DONE_MARKER, parseSseFrames } from '../sse'
import type { ApiResponse, ChatSession, ChatSessionDetail, PageData, SkuRef } from '../types'

const CHAT_PATH = `${API_BASE}/agent/chat`

/** 流式响应解析出来的事件。页面拿它驱动状态机。 */
export type ChatEvent =
  /** 会话 id(后端**每次都发**,收到就覆盖) */
  | { type: 'session'; sessionId: string }
  | { type: 'delta'; delta: string }
  | { type: 'refs'; refs: SkuRef[] }
  /** **正常终态之一**:超出咨询范围(50001)等业务拒绝 —— 不是网络故障 */
  | { type: 'error'; code: number; message: string; traceId: string }
  /** 正常终态:答完了 */
  | { type: 'done' }

/** 对话记录列表查询。契约 `/agent/chat/sessions` 只有 page / size。 */
export interface ChatQuery {
  page?: number
  size?: number
}

/** 对话记录列表。需登录。§10.2 */
export function listChatSessions(query: ChatQuery = {}): Promise<PageData<ChatSession>> {
  return request<PageData<ChatSession>>({
    path: `${API_BASE}/agent/chat/sessions`,
    query: { page: query.page, size: query.size },
  })
}

/**
 * 对话记录详情。失败码 **30001**(不存在 / 不是本人的 / 不是数字 —— 三者同码同文案,
 * 后端刻意不区分,防枚举)。§10.2
 */
export function getChatSession(id: string): Promise<ChatSessionDetail> {
  return request<ChatSessionDetail>({
    path: `${API_BASE}/agent/chat/sessions/${encodeURIComponent(id)}`,
  })
}

/**
 * 发起一次流式提问。**异步生成器** —— 调用方 `for await` 拿事件,
 * 比回调式好测(测试可以直接 `next()`、也可以只取前 N 个事件)。
 *
 * @param opts.message   用户这一句(必填)
 * @param opts.sessionId 续聊时带上;首次省略(后端会建新会话并在首帧下发 id)
 * @param opts.signal    离开页面 / 用户点停止时中断用
 *
 * @throws {BusinessError} 业务拒绝(如 401、50001 之外的码)/ 开流前的业务失败
 * @throws {HttpError}     网络或协议层故障(非 2xx、不是流、流在终态前断开、数据解析不了)
 * @throws {DOMException}  `AbortError` —— 主动中断,**调用方要把它和上两者分开**
 */
export async function* streamChat(opts: {
  message: string
  sessionId?: string
  signal?: AbortSignal
}): AsyncGenerator<ChatEvent, void, unknown> {
  const init = (): RequestInit => ({
    method: 'POST',
    // 令牌是 HttpOnly Cookie,由浏览器带上(前端零存储)
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    // sessionId 为 undefined 时 JSON.stringify 会把这个键整个去掉 —— 正是契约要的"首次可省略"
    body: JSON.stringify({ sessionId: opts.sessionId, message: opts.message }),
    signal: opts.signal,
  })

  let response = await fetch(CHAT_PATH, init())

  // 401 只可能发生在**流开始之前**(一旦 200 开始流,状态码就改不了了)。
  // 此时响应体还是普通 JSON 信封,走全站同一把单飞刷新 + 重放一次 ——
  // 访问令牌 2 小时过期,长驻页面必然遇到,不该把用户踢去重登。
  if (response.status === 401 && (await tryRefreshSession(CHAT_PATH))) {
    response = await fetch(CHAT_PATH, init())
  }

  if (!response.ok) throw await asError(response)

  // 200 却不是流:只可能是失败被表达成了普通 JSON 信封(开流前就失败了)
  if (!(response.headers.get('content-type') ?? '').includes('text/event-stream')) {
    throw await asError(response)
  }

  const body = response.body
  if (!body) throw new HttpError(response.status, '响应没有可读的流')

  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let terminated = false

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break

      // `{ stream: true }` 是关键:一个多字节汉字可能被切成两半,不这样会解出乱码
      buffer += decoder.decode(value, { stream: true })

      // 切出**完整**的帧,不完整的尾巴留回 buffer 等下一块
      const { events, rest } = parseSseFrames('', buffer)
      buffer = rest

      for (const payload of events) {
        const event = toEvent(payload)
        yield event
        if (event.type === 'done' || event.type === 'error') {
          terminated = true
          return
        }
      }
    }

    // 流读完了:冲刷解码器,并处理没有以分隔符结尾的最后一帧
    buffer += decoder.decode()
    if (buffer.trim()) {
      for (const payload of parseSseFrames(`${buffer}\n\n`, '').events) {
        const event = toEvent(payload)
        yield event
        if (event.type === 'done' || event.type === 'error') {
          terminated = true
          return
        }
      }
    }
  } finally {
    // 释放连接。取消失败无所谓(连接本来就要关了),不要让它盖住真正的错
    await reader.cancel().catch(() => undefined)
  }

  // 没有终态就结束了 —— **把"静默断流"变成一次可判定的中断**,
  // 否则页面会停在"回答到一半"的样子,用户分不清是没说完还是断了。
  if (!terminated) throw new HttpError(0, '连接在结束前中断')
}

/** 一个 `data:` 载荷 → 事件。形状不认识就报错,不静默跳过(静默会藏 bug)。 */
function toEvent(payload: string): ChatEvent {
  if (payload === DONE_MARKER) return { type: 'done' }

  let parsed: unknown
  try {
    parsed = JSON.parse(payload)
  } catch {
    throw new HttpError(0, '服务返回了无法解析的数据')
  }
  if (!parsed || typeof parsed !== 'object') {
    throw new HttpError(0, '服务返回了无法解析的数据')
  }

  const frame = parsed as Record<string, unknown>
  if (typeof frame.sessionId === 'string') return { type: 'session', sessionId: frame.sessionId }
  if (typeof frame.delta === 'string') return { type: 'delta', delta: frame.delta }
  // 空数组照发 —— 后端"一个候选都校验不过"时给的就是空数组,由页面决定不渲染
  if (Array.isArray(frame.refs)) return { type: 'refs', refs: frame.refs as SkuRef[] }
  if (frame.error && typeof frame.error === 'object') {
    const error = frame.error as Record<string, unknown>
    return {
      type: 'error',
      code: typeof error.code === 'number' ? error.code : 0,
      message: typeof error.message === 'string' ? error.message : '',
      traceId: typeof error.traceId === 'string' ? error.traceId : '',
    }
  }
  throw new HttpError(0, '服务返回了无法解析的数据')
}

/** 把一个失败响应翻成异常。**业务失败优先**:它的文案比"HTTP 500"有用得多。 */
async function asError(response: Response): Promise<Error> {
  let payload: ApiResponse<unknown> | null = null
  try {
    payload = (await response.json()) as ApiResponse<unknown>
  } catch {
    payload = null
  }

  if (payload && typeof payload.code === 'number' && payload.code !== ErrorCode.SUCCESS) {
    return new BusinessError({
      code: payload.code,
      message: payload.message,
      traceId: payload.traceId,
      data: payload.data,
    })
  }

  if (response.status === 401 || response.status === 403) {
    return new BusinessError({
      code: response.status === 401 ? ErrorCode.UNAUTHENTICATED : ErrorCode.FORBIDDEN,
    })
  }

  return new HttpError(response.status, `服务返回 ${response.status}`)
}
