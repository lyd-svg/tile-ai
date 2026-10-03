/**
 * 生成结果页(F-1.7)。
 *
 * 这个页面最容易出的错是**轮询**:停不下来、或者把"生成失败"当成网络抖动一直重试。
 * 所以下面用假定时器把这几条钉死:
 *   - 到终态就**不再发请求**;
 *   - 组件卸载后**不再发请求**(否则用户都走了还在打接口);
 *   - 40001 是**终态失败**(后端把失败做成 200 + 业务码,原因在 message),
 *     不能当网络抖动重试 —— 重试只会拿到同样的失败。
 *
 * ⚠️ 字典也要打桩,理由同 catalog.spec.ts:onMounted 里取字典,不打桩会挂住。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError, ErrorCode } from '../src/api/errors'
import GenerateView from '../src/views/site/GenerateView.vue'
import { useSelectionDraftStore } from '../src/stores/selectionDraft'
import type { LayoutModel } from '../src/api/types'

const submitGenerate = vi.fn()
const getGenerateProgress = vi.fn()
vi.mock('@/api/c/generate', () => ({
  submitGenerate: (...args: unknown[]) => submitGenerate(...args),
  getGenerateProgress: (...args: unknown[]) => getGenerateProgress(...args),
}))

vi.mock('@/api/dicts', () => ({
  listDictsOfType: vi.fn(async (type: string) =>
    type === 'space'
      ? [
          { type: 'space', key: 'bathroom', label: '卫生间' },
          { type: 'space', key: 'living_room', label: '客厅' },
        ]
      : [{ type: 'style', key: 'modern', label: '现代' }],
  ),
  listDicts: vi.fn(async () => []),
}))

const LAYOUT: LayoutModel = {
  spaces: [{ type: 'bathroom', name: '主卫', length: 2, width: 1.8, height: 2.7 }],
}

const Blank = { template: '<div />' }

async function mountView(withDraft = true) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const draft = useSelectionDraftStore()
  if (withDraft) {
    draft.setPart('floor', 'TL-1001', { name: '雅灰石纹' })
    draft.setLayout(LAYOUT)
  }

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/generate', name: 'generate', component: GenerateView },
      { path: '/products', name: 'products', component: Blank },
      { path: '/layout', name: 'layout', component: Blank },
    ],
  })
  await router.push('/generate')
  await router.isReady()

  const wrapper = mount(GenerateView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, draft }
}

async function startGeneration(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper']) {
  const button = wrapper.findAll('button').find((item) => item.text().includes('开始生成'))
  await button?.trigger('click')
  await flushPromises()
}

/** 推进一个轮询周期(2s)并让回包处理完。 */
async function tick() {
  await vi.advanceTimersByTimeAsync(2000)
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('前置检查', () => {
  it('没有选砖 / 没录户型时给出空态,并说明往哪走 —— 不拿空数据硬凑一次生成', async () => {
    const { wrapper } = await mountView(false)

    expect(wrapper.find('[role="alert"]').text()).toContain('选购信息不完整')
    expect(wrapper.text()).toContain('还没选砖')
    expect(wrapper.text()).toContain('还没录户型')
    // 选购信息只在内存里,刷新即清空 —— 这一点要对用户说清楚
    expect(wrapper.text()).toContain('刷新后会清空')
  })

  it('草稿齐备时直接亮出配置区,空间按户型里的唯一空间预选', async () => {
    const { wrapper } = await mountView()

    const selects = wrapper.findAll('select')
    expect(selects.length).toBe(3)
    expect((selects[0].element as HTMLSelectElement).value).toBe('bathroom')
  })
})

describe('提交', () => {
  it('提交的是草稿里的部位与户型,张数默认 1', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    const { wrapper } = await mountView()
    await startGeneration(wrapper)

    expect(submitGenerate).toHaveBeenCalledWith({
      space: 'bathroom',
      style: undefined, // 风格留空时不要发空串
      angles: 1,
      layout: LAYOUT,
      parts: [{ part: 'floor', sku: 'TL-1001' }],
    })
  })

  it('提交后立刻进入排队态,并且还没开始轮询(先等 2s)', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    getGenerateProgress.mockResolvedValue({ taskId: 'g_1', status: 'running' })
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    await startGeneration(wrapper)

    expect(wrapper.text()).toContain('正在生成')
    expect(getGenerateProgress).not.toHaveBeenCalled()
  })

  it('10001 的逐字段原因逐项列出', async () => {
    submitGenerate.mockRejectedValue(
      new BusinessError({
        code: ErrorCode.PARAM_INVALID,
        message: '参数校验失败',
        data: { errors: [{ field: 'parts', reason: '至少需要一个部位' }] },
      }),
    )
    const { wrapper } = await mountView()
    await startGeneration(wrapper)

    expect(wrapper.find('[role="alert"]').text()).toContain('参数校验失败')
    expect(wrapper.findAll('.errors li').map((item) => item.text())).toEqual([
      'parts:至少需要一个部位',
    ])
  })

  it('40002 生成繁忙给出可读提示', async () => {
    submitGenerate.mockRejectedValue(
      new BusinessError({ code: ErrorCode.GENERATION_BUSY, message: '生成服务繁忙' }),
    )
    const { wrapper } = await mountView()
    await startGeneration(wrapper)

    expect(wrapper.find('[role="alert"]').text()).toContain('生成服务繁忙')
  })
})

describe('轮询', () => {
  it('pending → running → success,到终态就停下', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    getGenerateProgress
      .mockResolvedValueOnce({ taskId: 'g_1', status: 'running', progress: 40 })
      .mockResolvedValueOnce({
        taskId: 'g_1',
        status: 'success',
        progress: 100,
        planId: 7,
        images: [{ url: '/img/a.svg', view: 'default', isCover: true }],
      })
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    await startGeneration(wrapper)

    await tick()
    expect(getGenerateProgress).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('正在生成')

    await tick()
    expect(getGenerateProgress).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('生成完成')
    expect(wrapper.find('img').attributes('src')).toBe('/img/a.svg')

    // 终态之后**一个请求都不该再发**
    await vi.advanceTimersByTimeAsync(10_000)
    expect(getGenerateProgress).toHaveBeenCalledTimes(2)
  })

  it('离开页面就不再轮询 —— 用户都走了不该还在打接口', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    getGenerateProgress.mockResolvedValue({ taskId: 'g_1', status: 'running' })
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    await startGeneration(wrapper)
    await tick()
    expect(getGenerateProgress).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(getGenerateProgress).toHaveBeenCalledTimes(1)
  })

  it('40001 是**终态失败**:显示后端给的原因,且不再重试', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    // 后端把"生成失败"做成 200 + code:40001,原因在 message 里 →
    // request() 会把它抛成 BusinessError,必须接住
    getGenerateProgress.mockRejectedValue(
      new BusinessError({ code: ErrorCode.GENERATION_FAILED, message: '出图模型返回超时' }),
    )
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    await startGeneration(wrapper)
    await tick()

    expect(getGenerateProgress).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[role="alert"]').text()).toContain('出图模型返回超时')

    await vi.advanceTimersByTimeAsync(10_000)
    expect(getGenerateProgress).toHaveBeenCalledTimes(1)
  })

  it('40003 任务不存在同样是终态,不做无谓重试', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    getGenerateProgress.mockRejectedValue(
      new BusinessError({ code: ErrorCode.TASK_NOT_FOUND, message: '任务不存在' }),
    )
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    await startGeneration(wrapper)
    await tick()

    expect(wrapper.find('[role="alert"]').text()).toContain('任务不存在')
    await vi.advanceTimersByTimeAsync(10_000)
    expect(getGenerateProgress).toHaveBeenCalledTimes(1)
  })

  it('网络类错误**保留重试**,但要封顶,不能无限打', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    getGenerateProgress.mockRejectedValue(new Error('网络断了'))
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    await startGeneration(wrapper)

    for (let i = 0; i < 12; i += 1) await tick()

    // 上限 10 次,之后停表并给出可读提示
    expect(getGenerateProgress).toHaveBeenCalledTimes(10)
    expect(wrapper.find('[role="alert"]').text()).toContain('失去联系')
  })
})

describe('多角度图片', () => {
  it('angles=2 出两张图,按 view 标注(不是靠下标猜)', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    getGenerateProgress.mockResolvedValue({
      taskId: 'g_1',
      status: 'success',
      images: [
        { url: '/img/a.svg', view: 'default', isCover: true },
        { url: '/img/b.svg', view: 'angle1' },
      ],
    })
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    // 张数改成 2 —— 张数 = 角度数,不是 +1
    const angleSelect = wrapper.findAll('select')[2]
    await angleSelect.setValue('2')
    await startGeneration(wrapper)

    expect(submitGenerate).toHaveBeenCalledWith(
      expect.objectContaining({ angles: 2 }),
    )

    await tick()

    // 封面是 default,另一张是 angle1;两张都渲染出来
    expect(wrapper.find('.main img').attributes('src')).toBe('/img/a.svg')
    expect(wrapper.find('.main .caption').text()).toBe('正面')
    expect(wrapper.findAll('.thumbs .thumb-cap').map((node) => node.text())).toEqual([
      '正面',
      '角度 1',
    ])

    // 多角度图点一下就换到主图位置(与商品详情页同款交互)
    await wrapper.findAll('.thumbs .thumb')[1].trigger('click')
    await tick()
    expect(wrapper.find('.main img').attributes('src')).toBe('/img/b.svg')
    expect(wrapper.find('.main .caption').text()).toBe('角度 1')
  })
})

describe('T-1.9 未选部位由 AI 补齐', () => {
  it('页面不再说"不会被自动补齐" —— 那句话已经过时了', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('会由 AI 自动补齐')
    expect(wrapper.text()).not.toContain('不会被自动补齐')
  })

  it('补齐来的部位标「AI 补齐」', async () => {
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    getGenerateProgress.mockResolvedValue({
      taskId: 'g_1',
      status: 'success',
      progress: 100,
      parts: [
        { part: 'floor', sku: 'TL-1001', source: 'user' },
        { part: 'wall', sku: 'TL-2001', source: 'ai' },
      ],
    })
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    await startGeneration(wrapper)
    await tick()

    expect(wrapper.text()).toContain('TL-2001')
    expect(wrapper.find('.parts .tag').text()).toBe('AI 补齐')
    expect(wrapper.text()).not.toContain('没有自动补齐')
  })

  it('**没补上的部位如实说明**,且不替后端解释原因', async () => {
    // 草稿里只有地面(mountView 的预置),结果里也只有地面 ⇒ 墙面没补上
    submitGenerate.mockResolvedValue({ taskId: 'g_1', status: 'pending' })
    getGenerateProgress.mockResolvedValue({
      taskId: 'g_1',
      status: 'success',
      progress: 100,
      parts: [{ part: 'floor', sku: 'TL-1001', source: 'user' }],
    })
    const { wrapper } = await mountView()

    vi.useFakeTimers()
    await startGeneration(wrapper)
    await tick()

    expect(wrapper.text()).toContain('没有自动补齐')
    expect(wrapper.text()).toContain('墙面')
    // 页面并不知道是"库里没有"还是"服务没答上" —— 编个理由就是替后端说话
    expect(wrapper.text()).not.toContain('失败')
    expect(wrapper.text()).not.toContain('超时')
  })
})
