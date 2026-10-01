/**
 * 方案详情(F-1.9 管理 + F-1.8 报价展示与重算)。
 *
 * 这一页有三处**改错了不报错、只悄悄出错**的地方,下面逐条钉住:
 *
 *   1. `materialFee` 契约标必填、实现会**整个省略**(`PlanPartView` 是
 *      `@JsonInclude(NON_NULL)`)。所以 mock 里必须用 `delete` **把字段拿掉** ——
 *      塞 `null` 是假的,实现永远不发 null。显示成 `—`,不是 `undefined` 也不是 0。
 *   2. `quote` 可能整个没有(多空间方案走"这一次不算价"的分支)。那是**正常状态**,
 *      要有可读空态,而且**重算按钮仍然可点** —— 它存在的意义就是把"当时算不出"
 *      变成"现在能算"。
 *   3. 改名的 200 字上限**契约里没声明**,只在后端按**码点**判。前端若用
 *      `name.length`(UTF-16 单元)就会把"150 个 emoji"这种合法名字误判成超长。
 *
 * ⚠️ 字典要打桩,理由同 catalog.spec.ts:不打桩页面会静默挂空白。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError, ErrorCode } from '../src/api/errors'
import PlanDetailView from '../src/views/site/PlanDetailView.vue'
import type { PlanDetail, PlanPart } from '../src/api/types'

const getPlan = vi.fn()
const renamePlan = vi.fn()
const deletePlan = vi.fn()
const copyPlan = vi.fn()
const recalcPlan = vi.fn()

vi.mock('@/api/c/plans', () => ({
  getPlan: (...args: unknown[]) => getPlan(...args),
  renamePlan: (...args: unknown[]) => renamePlan(...args),
  deletePlan: (...args: unknown[]) => deletePlan(...args),
  copyPlan: (...args: unknown[]) => copyPlan(...args),
  recalcPlan: (...args: unknown[]) => recalcPlan(...args),
  listPlans: vi.fn(async () => ({ list: [], total: 0, page: 1, size: 9 })),
}))

const listDictsOfType = vi.fn()
vi.mock('@/api/dicts', () => ({
  listDictsOfType: (...args: unknown[]) => listDictsOfType(...args),
  listDicts: vi.fn(async () => []),
}))

/** 第二行**故意没有** materialFee / pricePerSqm / area —— 模拟"这一次没算出价"。 */
const WALL_PART: PlanPart = { part: 'wall', sku: 'TL-2002' }

const DETAIL: PlanDetail = {
  id: 7,
  name: '主卫方案',
  createdAt: '2026-10-01T21:19:22',
  space: 'bathroom',
  style: 'modern',
  quoteExpired: false,
  totalAmount: 2109.41,
  layout: {
    spaces: [{ type: 'bathroom', name: '主卫', length: 2, width: 1.8, height: 2.7 }],
  },
  parts: [
    {
      part: 'floor',
      sku: 'TL-1001',
      productName: '雅灰石纹',
      pricePerSqm: 120,
      area: 3.6,
      materialFee: 432,
    },
    WALL_PART,
  ],
  images: [
    { url: '/img/a.svg', view: 'default', isCover: true },
    { url: '/img/b.svg', view: 'angle1' },
  ],
  quote: {
    materialTotal: 1817.01,
    auxTotal: 292.4,
    total: 2109.41,
    items: [
      {
        part: 'floor',
        sku: 'TL-1001',
        grossArea: 3.6,
        effectiveArea: 3.6,
        lossRate: 0.06,
        pricePerSqm: 120,
        materialFee: 432,
      },
    ],
  },
}

const Blank = { template: '<div />' }

async function mountView(path = '/plans/7') {
  const pinia = createPinia()
  setActivePinia(pinia)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/plans', name: 'plans', component: Blank },
      { path: '/plans/:id', name: 'plan-detail', component: PlanDetailView },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(PlanDetailView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

/** 按文案找按钮 —— 比按下标猜稳。 */
function buttonByText(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!button) throw new Error(`找不到按钮:${text}`)
  return button
}

async function submitRename(
  wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'],
  name: string,
) {
  await wrapper.find('.rename input').setValue(name)
  await wrapper.find('form.rename').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  // happy-dom 里没有 `window.confirm`,直接赋值(与 adminProducts.spec.ts 同一写法)
  window.confirm = vi.fn(() => true)
  getPlan.mockImplementation(async (id: number) => ({ ...DETAIL, id }))
  listDictsOfType.mockImplementation(async (type: string) =>
    type === 'space'
      ? [{ type: 'space', key: 'bathroom', label: '卫生间' }]
      : [{ type: 'style', key: 'modern', label: '现代' }],
  )
})

describe('渲染', () => {
  it('方案名 / 空间风格 / 户型 / 效果图都出来', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('h1').text()).toBe('主卫方案')
    expect(wrapper.find('.meta').text()).toBe('卫生间 · 现代')
    expect(wrapper.find('.spaces').text()).toContain('主卫')
    expect(wrapper.find('.spaces').text()).toContain('2 × 1.8m')
    expect(wrapper.find('.main img').attributes('src')).toBe('/img/a.svg')
    expect(wrapper.find('.main .caption').text()).toBe('正面')
    expect(wrapper.find('.thumbs figcaption').text()).toBe('角度 1')
  })

  it('部位表与报价明细分属两张表,报价合计三项都在', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.findAll('.part-table tbody tr')).toHaveLength(2)
    expect(wrapper.findAll('.quote-table tbody tr')).toHaveLength(1)
    expect(wrapper.find('.totals').text()).toContain('¥1817.01')
    expect(wrapper.find('.totals').text()).toContain('¥292.4')
    expect(wrapper.find('.totals').text()).toContain('¥2109.41')
  })

  it('materialFee / 面积 / 单价缺失的那一行显示 `—`,**不是 undefined 也不是 0**', async () => {
    const { wrapper } = await mountView()

    // 第二行是 WALL_PART —— 那几个字段**整个不在**对象上(实现是 NON_NULL,不发 null)
    const cells = wrapper.findAll('.part-table tbody tr')[1].findAll('td')
    expect(cells.map((cell) => cell.text())).toEqual(['墙面', 'TL-2002', '—', '—', '—'])
    expect(wrapper.find('.part-table').text()).not.toContain('undefined')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('AI 补齐的部位挂标签', async () => {
    getPlan.mockResolvedValue({
      ...DETAIL,
      parts: [{ ...WALL_PART, source: 'ai' }],
    })
    const { wrapper } = await mountView()

    expect(wrapper.find('.part-table .tag').text()).toBe('AI 补齐')
  })
})

describe('没有报价是一种正常状态,不是错误', () => {
  it('quote 缺失 ⇒ 给可读空态,不留 NaN', async () => {
    const { quote: _omitted, ...withoutQuote } = DETAIL
    getPlan.mockResolvedValue({ ...withoutQuote, totalAmount: undefined })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('这份方案没有报价')
    expect(wrapper.find('.quote-table').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('重算按钮**仍然可点** —— 它存在的意义正是把"当时算不出"变成"现在能算"', async () => {
    const { quote: _omitted, ...withoutQuote } = DETAIL
    getPlan.mockResolvedValue({ ...withoutQuote, totalAmount: undefined })
    const { wrapper } = await mountView()

    const button = buttonByText(wrapper, '按当前价重算')
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('quoteExpired 为 true 时给出"这是当时的价"的提示', async () => {
    getPlan.mockResolvedValue({ ...DETAIL, quoteExpired: true })
    const { wrapper } = await mountView()

    expect(wrapper.find('.stale').text()).toContain('调过价')
  })
})

describe('按当前价重算(F-1.8)', () => {
  it('入参**只有一个 id、没有请求体** —— 架构不变量 6', async () => {
    // POST /plans/{id}/recalc 无请求体:部位/面积/开口一律取自方案快照,
    // 让前端能传参就等于开了一条绕过 /quote/calc 参数校验的后门。
    recalcPlan.mockResolvedValue({ materialTotal: 1900, auxTotal: 300, total: 2200, items: [] })
    const { wrapper } = await mountView()

    await buttonByText(wrapper, '按当前价重算').trigger('click')
    await flushPromises()

    expect(recalcPlan).toHaveBeenCalledWith(7) // 恰好一个实参 = 没有 body
  })

  it('成功后**就地**刷新总额并清掉过期标记,不重新拉一遍详情', async () => {
    getPlan.mockResolvedValue({ ...DETAIL, quoteExpired: true })
    recalcPlan.mockResolvedValue({
      materialTotal: 1900,
      auxTotal: 300,
      total: 2200,
      items: DETAIL.quote!.items,
    })
    const { wrapper } = await mountView()

    expect(wrapper.find('.stale').exists()).toBe(true)

    await buttonByText(wrapper, '按当前价重算').trigger('click')
    await flushPromises()

    expect(wrapper.find('.totals').text()).toContain('¥2200')
    expect(wrapper.find('.stale').exists()).toBe(false)
    // 契约写明 recalc 的 data 就是新的 QuoteResult —— 再 getPlan 一轮是白打的请求,
    // 而且中间那一拍会闪一下旧金额
    expect(getPlan).toHaveBeenCalledTimes(1)
  })

  it('重算失败原样透出后端文案,不自己编一句', async () => {
    // 失败一律 90001「方案在,坏的是系统」(§41.4)—— 后端那句才说得清是哪一步算不出来
    recalcPlan.mockRejectedValue(
      new BusinessError({ code: ErrorCode.INTERNAL_ERROR, message: '该方案的户型快照已损坏' }),
    )
    const { wrapper } = await mountView()

    await buttonByText(wrapper, '按当前价重算').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('该方案的户型快照已损坏')
  })
})

describe('改名', () => {
  it('按**码点**数长度:150 个 emoji 是合法名字,不能被误判超长', async () => {
    // '😀' 在 UTF-16 里是 2 个单元、在 MySQL/后端眼里是 1 个码点。
    // 用 `name.length` 判会得到 300 > 200 而**错拒**一个完全合法的名字。
    renamePlan.mockResolvedValue(undefined)
    const { wrapper } = await mountView()

    const emojiName = '😀'.repeat(150)
    await submitRename(wrapper, emojiName)

    expect(renamePlan).toHaveBeenCalledWith(7, emojiName)
  })

  it('真的超长(201 个码点)⇒ 前端就挡住,一次请求都不发', async () => {
    const { wrapper } = await mountView()

    await submitRename(wrapper, '木'.repeat(201))

    expect(renamePlan).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toContain('最多 200')
  })

  it('空名字不发请求', async () => {
    const { wrapper } = await mountView()

    await submitRename(wrapper, '   ')

    expect(renamePlan).not.toHaveBeenCalled()
  })

  it('服务端 10001 的逐字段原因逐条渲染,不吞掉', async () => {
    renamePlan.mockRejectedValue(
      new BusinessError({
        code: ErrorCode.PARAM_INVALID,
        message: '参数校验失败',
        data: { errors: [{ field: 'name', reason: '方案名最多 200 个字' }] },
      }),
    )
    const { wrapper } = await mountView()

    await submitRename(wrapper, '主卫方案 v2')

    expect(wrapper.findAll('.errors li').map((item) => item.text())).toEqual([
      'name:方案名最多 200 个字',
    ])
  })

  it('改名成功后页面上的标题跟着变', async () => {
    renamePlan.mockResolvedValue(undefined)
    const { wrapper } = await mountView()

    await submitRename(wrapper, '主卫方案 v2')

    expect(wrapper.find('h1').text()).toBe('主卫方案 v2')
    expect(wrapper.find('.notice').text()).toBe('已改名')
  })
})

describe('另存为与删除', () => {
  it('另存为之后跳到**新方案**的详情页', async () => {
    copyPlan.mockResolvedValue({ planId: 99 })
    const { wrapper, router } = await mountView()

    await buttonByText(wrapper, '另存为').trigger('click')
    await flushPromises()

    expect(copyPlan).toHaveBeenCalledWith(7)
    expect(router.currentRoute.value.path).toBe('/plans/99')
    // 换了方案要真的重新拉一次,不能还显示旧的那份
    expect(getPlan).toHaveBeenLastCalledWith(99)
  })

  it('删除的二次确认返回否 ⇒ 一个请求都不发', async () => {
    const confirmSpy = vi.fn(() => false)
    window.confirm = confirmSpy
    const { wrapper } = await mountView()

    await buttonByText(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalled()
    expect(deletePlan).not.toHaveBeenCalled()
  })

  it('确认后删除,并回到列表页', async () => {
    deletePlan.mockResolvedValue(undefined)
    const { wrapper, router } = await mountView()

    await buttonByText(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(deletePlan).toHaveBeenCalledWith(7)
    expect(router.currentRoute.value.path).toBe('/plans')
  })
})

describe('入参防护', () => {
  it('路由参数不是数字 ⇒ 走错误态,**一个请求都不发**', async () => {
    // 直接把 '/plans/abc' 的参数拼进 URL 会打出一个必然失败的请求,
    // 而用户看到的是一句莫名其妙的"加载失败"
    const { wrapper } = await mountView('/plans/abc')

    expect(getPlan).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toContain('方案编号不正确')
  })

  it('方案不存在(30001)⇒ 显示后端文案,且不画出一个空壳详情页', async () => {
    getPlan.mockRejectedValue(
      new BusinessError({ code: ErrorCode.NOT_FOUND, message: '方案不存在' }),
    )
    const { wrapper } = await mountView()

    expect(wrapper.find('[role="alert"]').text()).toContain('方案不存在')
    expect(wrapper.find('.rename').exists()).toBe(false)
  })
})
