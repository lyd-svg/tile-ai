/**
 * AI 咨询页(F-1.6)。
 *
 * 这一页最容易出的错是**把两种"没答成"混为一谈**:
 *   - **拒答**(后端 `error` 帧,如问价 50001)—— 服务好好的,只是这事不答。
 *     该照抄后端文案、**不报"失去联系"、不给重试**(重试同一个问题答案不会变);
 *   - **连接断了** —— 才是"请重试"。
 * 下面的用例把这条钉死,顺带钉住"离开页面要中断流"与"采纳不覆盖用户别的选择"。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { normalize } from './helpers/antd'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError } from '../src/api/errors'
import { useSelectionDraftStore } from '../src/stores/selectionDraft'
import ConsultView from '../src/views/site/ConsultView.vue'
import type { ChatEvent } from '../src/api/c/chat'
import type { LayoutModel } from '../src/api/types'

const streamChat = vi.fn()
const listChatSessions = vi.fn()
const getChatSession = vi.fn()

vi.mock('@/api/c/chat', () => ({
  streamChat: (...args: unknown[]) => streamChat(...args),
  listChatSessions: (...args: unknown[]) => listChatSessions(...args),
  getChatSession: (...args: unknown[]) => getChatSession(...args),
}))

const Blank = { template: '<div />' }

const LAYOUT: LayoutModel = {
  spaces: [{ type: 'bathroom', name: '主卫', length: 2, width: 1.8, height: 2.7 }],
}

/** 让 `streamChat` 依次产出这些事件后正常结束。 */
function yields(events: ChatEvent[]) {
  return () =>
    (async function* () {
      for (const event of events) yield event
    })()
}

/** 让 `streamChat` 在中途抛错(模拟连接断了)。 */
function throws(error: unknown) {
  return () =>
    (async function* () {
      throw error
    })()
}

async function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/consult', name: 'consult', component: ConsultView },
      { path: '/layout', name: 'layout', component: Blank },
      { path: '/login', name: 'login', component: Blank },
    ],
  })
  await router.push('/consult')
  await router.isReady()

  const wrapper = mount(ConsultView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, draft: useSelectionDraftStore() }
}

/** 输入并发送一句。 */
async function ask(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], text: string) {
  await wrapper.find('input').setValue(text)
  await wrapper.find('form').trigger('submit')
  await flushPromises()
  await flushPromises()
}

function buttons(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper']) {
  return wrapper.findAll('button')
}

/** 按文案找按钮 —— **归一化空白**后再比(antd 会给两个汉字的按钮插空格)。 */
function buttonWith(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], text: string) {
  const target = normalize(text)
  return buttons(wrapper).find((button) => normalize(button.text()).includes(target))
}

beforeEach(() => {
  vi.clearAllMocks()
  listChatSessions.mockResolvedValue({ list: [], total: 0, page: 1, size: 20 })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('ConsultView', () => {
  it('初始是空态,输入为空时发送不可用', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('还没有对话')
    expect(buttonWith(wrapper, '发送')!.attributes('disabled')).toBeDefined()
  })

  it('发送后先出现用户气泡与助手占位,delta 逐帧累积,[DONE] 之后可以再问', async () => {
    streamChat.mockImplementation(
      yields([
        { type: 'session', sessionId: '23' },
        { type: 'delta', delta: '浅' },
        { type: 'delta', delta: '灰' },
        { type: 'done' },
      ]),
    )
    const { wrapper } = await mountView()

    await ask(wrapper, '卫生间配什么墙砖')

    expect(wrapper.text()).toContain('卫生间配什么墙砖')
    expect(wrapper.text()).toContain('浅灰')
    expect(wrapper.find('input').attributes('readonly')).toBeUndefined()
  })

  it('busy 期间重复提交只调一次(防重复提交)', async () => {
    streamChat.mockImplementation(
      () =>
        (async function* () {
          yield { type: 'delta', delta: '…' } as ChatEvent
          await new Promise((resolve) => setTimeout(resolve, 10))
          yield { type: 'done' } as ChatEvent
        })(),
    )
    const { wrapper } = await mountView()

    await wrapper.find('input').setValue('推荐一款')
    await wrapper.find('form').trigger('submit')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(streamChat).toHaveBeenCalledTimes(1)
  })

  it('记住首帧 sessionId,第二次提问带上它续聊', async () => {
    streamChat.mockImplementation(yields([{ type: 'session', sessionId: '23' }, { type: 'done' }]))
    const { wrapper } = await mountView()

    await ask(wrapper, '第一问')
    expect(streamChat.mock.calls[0]![0].sessionId).toBeUndefined()

    await ask(wrapper, '第二问')
    expect(streamChat.mock.calls[1]![0].sessionId).toBe('23')
  })

  it('**拒答(50001)是正常终态**:照抄后端文案,不报"失去联系"、不给重试', async () => {
    streamChat.mockImplementation(
      yields([
        { type: 'session', sessionId: '23' },
        { type: 'error', code: 50001, message: '抱歉,我只能回答瓷砖相关的问题。', traceId: 't' },
      ]),
    )
    const { wrapper } = await mountView()

    await ask(wrapper, '这款砖多少钱一平')

    expect(wrapper.text()).toContain('抱歉,我只能回答瓷砖相关的问题。')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(buttonWith(wrapper, '重试')).toBeUndefined()
    expect(wrapper.find('input').attributes('readonly')).toBeUndefined()
  })

  it('**连接断了**才算故障:出现 alert,已收到的半截保留', async () => {
    streamChat.mockImplementation(
      () =>
        (async function* () {
          yield { type: 'delta', delta: '说到一半' } as ChatEvent
          throw new Error('network down')
        })(),
    )
    const { wrapper } = await mountView()

    await ask(wrapper, '推荐一款')

    expect(wrapper.find('[role="alert"]').text()).toContain('与服务器失去联系')
    expect(wrapper.text()).toContain('说到一半')
  })

  it('用户点「停止」:标"已停止",**不弹错误**,输入恢复', async () => {
    streamChat.mockImplementation(
      ({ signal }: { signal?: AbortSignal }) =>
        (async function* () {
          yield { type: 'delta', delta: '半句' } as ChatEvent
          await new Promise((_resolve, reject) => {
            signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
          })
        })(),
    )
    const { wrapper } = await mountView()

    await wrapper.find('input').setValue('推荐一款')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    await buttonWith(wrapper, '停止')!.trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.text()).toContain('已停止')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('input').attributes('readonly')).toBeUndefined()
  })

  it('离开页面会中断在途的流', async () => {
    let captured: AbortSignal | undefined
    streamChat.mockImplementation(({ signal }: { signal?: AbortSignal }) => {
      captured = signal
      return (async function* () {
        yield { type: 'delta', delta: '…' } as ChatEvent
        await new Promise(() => undefined) // 永不结束,等被中断
      })()
    })
    const { wrapper } = await mountView()

    await wrapper.find('input').setValue('推荐一款')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(captured?.aborted).toBe(false)
    wrapper.unmount()
    expect(captured?.aborted).toBe(true)
  })

  it('采纳写入选购草稿,**不自动跳走**', async () => {
    streamChat.mockImplementation(
      yields([
        { type: 'refs', refs: [{ sku: 'TL-1001', name: '雅灰石纹', part: 'wall' }] },
        { type: 'done' },
      ]),
    )
    const { wrapper, router, draft } = await mountView()

    await ask(wrapper, '卫生间配什么墙砖')
    await buttonWith(wrapper, '采纳')!.trigger('click')
    await flushPromises()

    expect(draft.partOf('wall')?.sku).toBe('TL-1001')
    expect(router.currentRoute.value.path).toBe('/consult')
    expect(buttonWith(wrapper, '已采纳')).toBeDefined()
  })

  it('采纳**不会清掉**用户已录的户型与别的部位(绝不调 clear)', async () => {
    streamChat.mockImplementation(
      yields([{ type: 'refs', refs: [{ sku: 'TL-2000', part: 'wall' }] }, { type: 'done' }]),
    )
    const { wrapper, draft } = await mountView()
    draft.setPart('floor', 'TL-1001', { name: '雅灰石纹' })
    draft.setLayout(LAYOUT)

    await ask(wrapper, '配什么墙砖')
    await buttonWith(wrapper, '采纳')!.trigger('click')
    await flushPromises()

    expect(draft.partOf('wall')?.sku).toBe('TL-2000')
    expect(draft.partOf('floor')?.sku).toBe('TL-1001')
    expect(draft.layout).not.toBeNull()
  })

  it('点「带着这些砖去录户型」才跳转', async () => {
    streamChat.mockImplementation(
      yields([{ type: 'refs', refs: [{ sku: 'TL-1001', part: 'wall' }] }, { type: 'done' }]),
    )
    const { wrapper, router } = await mountView()

    await ask(wrapper, '配什么墙砖')
    await buttonWith(wrapper, '采纳')!.trigger('click')
    await flushPromises()
    await buttonWith(wrapper, '带着这些砖去录户型')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('layout')
  })

  it('**name 缺失或乱码都不留空白** —— 始终把 sku 显示出来', async () => {
    const withoutName = { sku: 'TL-3000', part: 'wall' as const }
    const garbled = { sku: 'TL-3001', name: 'éç°ç³çº¹', part: 'wall' as const }
    streamChat.mockImplementation(yields([{ type: 'refs', refs: [withoutName, garbled] }, { type: 'done' }]))
    const { wrapper } = await mountView()

    await ask(wrapper, '配什么墙砖')

    const text = wrapper.text()
    expect(text).toContain('TL-3000') // 没有 name 时以 sku 作标题
    expect(text).toContain('TL-3001') // 有 name 也照样把 sku 摆出来
    expect(text).toContain('éç°ç³çº¹') // 乱码原样渲染,前端不"修"(修了与产品库页不一致)
  })

  it('refs 是空数组时不渲染任何推荐卡片(不瞎补)', async () => {
    streamChat.mockImplementation(yields([{ type: 'refs', refs: [] }, { type: 'done' }]))
    const { wrapper } = await mountView()

    await ask(wrapper, '推荐一款')

    expect(wrapper.text()).not.toContain('可采纳的推荐')
  })

  it('回复里的 Markdown 标记**渲染出来**(加粗变粗,而不是显示 `**`)', async () => {
    streamChat.mockImplementation(
      yields([{ type: 'delta', delta: '**首选墙砖:**云灰大理石' }, { type: 'done' }]),
    )
    const { wrapper } = await mountView()

    await ask(wrapper, '配什么墙砖')

    expect(wrapper.find('.text strong').exists()).toBe(true)
    expect(wrapper.find('.text strong').text()).toBe('首选墙砖:')
    expect(wrapper.text()).not.toContain('**')
  })

  it('**模型吐出的 HTML 不会被当成标记执行**(内容不可信)', async () => {
    streamChat.mockImplementation(
      yields([{ type: 'delta', delta: '<img src=x onerror=alert(1)>加了张图' }, { type: 'done' }]),
    )
    const { wrapper } = await mountView()

    await ask(wrapper, '配什么墙砖')

    expect(wrapper.html()).not.toContain('<img')
    expect(wrapper.text()).toContain('加了张图')
  })

  it('整页文本不出现 NaN / undefined / null', async () => {
    streamChat.mockImplementation(
      yields([
        { type: 'refs', refs: [{ sku: 'TL-1', part: 'local' }] },
        { type: 'error', code: 50001, message: '超出范围', traceId: 't' },
      ]),
    )
    const { wrapper } = await mountView()

    await ask(wrapper, '推荐一款')
    const text = wrapper.text()

    expect(text).not.toContain('NaN')
    expect(text).not.toContain('undefined')
    expect(text).not.toContain('null')
  })

  it('历史:点开才拉列表,点某条加载它的消息', async () => {
    listChatSessions.mockResolvedValue({
      list: [{ id: '7', title: '卫生间瓷砖' }],
      total: 1,
      page: 1,
      size: 20,
    })
    getChatSession.mockResolvedValue({
      session: { id: '7', title: '卫生间瓷砖' },
      messages: [
        { role: 'user', content: '卫生间配什么墙砖' },
        { role: 'assistant', content: '推荐雅灰石纹' },
      ],
    })
    const { wrapper } = await mountView()

    expect(listChatSessions).not.toHaveBeenCalled()
    await buttonWith(wrapper, '历史对话')!.trigger('click')
    await flushPromises()

    await buttonWith(wrapper, '卫生间瓷砖')!.trigger('click')
    await flushPromises()

    expect(getChatSession).toHaveBeenCalledWith('7')
    expect(wrapper.text()).toContain('推荐雅灰石纹')
  })

  it('**只有用户那条、没有回答**的会话:如实渲染,不伪造助手内容', async () => {
    listChatSessions.mockResolvedValue({ list: [{ id: '9', title: '半截对话' }], total: 1, page: 1, size: 20 })
    getChatSession.mockResolvedValue({
      session: { id: '9', title: '半截对话' },
      messages: [{ role: 'user', content: '只问了这一句' }],
    })
    const { wrapper } = await mountView()

    await buttonWith(wrapper, '历史对话')!.trigger('click')
    await flushPromises()
    await buttonWith(wrapper, '半截对话')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('只问了这一句')
    expect(wrapper.text()).toContain('没有留下回答')
    expect(wrapper.findAll('.bubble.assistant')).toHaveLength(0)
  })

  it('历史打不开时只提示,不影响继续提问(诚实降级)', async () => {
    listChatSessions.mockRejectedValue(new BusinessError({ code: 90001, message: '系统内部错误' }))
    const { wrapper } = await mountView()

    await buttonWith(wrapper, '历史对话')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('系统内部错误')
    expect(wrapper.find('input').attributes('readonly')).toBeUndefined()
  })

  it('切会话前会中断正在跑的流', async () => {
    let captured: AbortSignal | undefined
    streamChat.mockImplementation(({ signal }: { signal?: AbortSignal }) => {
      captured = signal
      return (async function* () {
        yield { type: 'delta', delta: '…' } as ChatEvent
        await new Promise(() => undefined)
      })()
    })
    listChatSessions.mockResolvedValue({ list: [{ id: '7', title: '旧对话' }], total: 1, page: 1, size: 20 })
    getChatSession.mockResolvedValue({ session: { id: '7' }, messages: [] })
    const { wrapper } = await mountView()

    await wrapper.find('input').setValue('正在问')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(captured?.aborted).toBe(false)

    await buttonWith(wrapper, '历史对话')!.trigger('click')
    await flushPromises()
    await buttonWith(wrapper, '旧对话')!.trigger('click')
    await flushPromises()

    expect(captured?.aborted).toBe(true)
  })
})
