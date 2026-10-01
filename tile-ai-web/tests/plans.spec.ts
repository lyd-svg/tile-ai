/**
 * 我的方案列表(F-1.9)。
 *
 * 这一页最容易出的错不是"画不出来",而是**画出一个看起来对、其实在撒谎的价** ——
 * `totalAmount` 缺失表示"这次没算出价"(`PlanSummaryView` 的注释写得很明白),
 * 一旦写成 `totalAmount ?? 0`,用户看到的就是一张 ¥0 的报价单。
 * 所以下面专门有一条断言整页文本里**不含 `NaN`**。
 *
 * ⚠️ 字典也要打桩,理由同 catalog.spec.ts / generate.spec.ts:onMounted 里取字典,
 * 不打桩页面会**静默挂空白**(不是报错,是永远等不到渲染)。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import MyPlansView from '../src/views/site/MyPlansView.vue'
import type { PlanSummary } from '../src/api/types'

const listPlans = vi.fn()
vi.mock('@/api/c/plans', () => ({
  listPlans: (...args: unknown[]) => listPlans(...args),
}))

const listDictsOfType = vi.fn()
vi.mock('@/api/dicts', () => ({
  listDictsOfType: (...args: unknown[]) => listDictsOfType(...args),
  listDicts: vi.fn(async () => []),
}))

const DICTS: Record<string, { type: string; key: string; label: string }[]> = {
  space: [
    { type: 'space', key: 'bathroom', label: '卫生间' },
    { type: 'space', key: 'living_room', label: '客厅' },
  ],
  style: [{ type: 'style', key: 'modern', label: '现代' }],
}

/** 名称与「空间 · 风格」刻意不同字,免得断言里分不清是哪一个渲染出来的。 */
const PLAN: PlanSummary = {
  id: 7,
  name: '主卫方案',
  createdAt: '2026-10-01T21:19:22',
  space: 'bathroom',
  style: 'modern',
  coverImage: '/img/a.svg',
  totalAmount: 2109.41,
  quoteExpired: false,
  parts: [{ part: 'floor', sku: 'TL-1001' }],
}

const Blank = { template: '<div />' }

async function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/plans', name: 'plans', component: MyPlansView },
      { path: '/plans/:id', name: 'plan-detail', component: Blank },
      { path: '/products', name: 'products', component: Blank },
      { path: '/layout', name: 'layout', component: Blank },
    ],
  })
  await router.push('/plans')
  await router.isReady()

  const wrapper = mount(MyPlansView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

function page(rows: PlanSummary[], total = rows.length) {
  return { list: rows, total, page: 1, size: 9 }
}

beforeEach(() => {
  vi.clearAllMocks()
  listDictsOfType.mockImplementation(async (type: string) => DICTS[type] ?? [])
})

describe('卡片内容', () => {
  it('名称 / 空间风格中文名 / 瓷砖摘要 / 报价都渲染出来', async () => {
    listPlans.mockResolvedValue(page([PLAN]))
    const { wrapper } = await mountView()

    expect(wrapper.find('.card h3').text()).toBe('主卫方案')
    // 空间与风格是字典 key,展示时翻成中文
    expect(wrapper.find('.meta').text()).toBe('卫生间 · 现代')
    expect(wrapper.find('.parts').text()).toBe('地面 · TL-1001')
    expect(wrapper.find('.price').text()).toContain('¥2109.41')
    expect(wrapper.find('.thumb img').attributes('src')).toBe('/img/a.svg')
  })

  it('totalAmount 缺失 ⇒ 显示「未出报价」,整页**不出现 NaN**', async () => {
    // 缺的是"这一次没算出价",不是 0 —— 后端 PlanSummaryView 的注释原文如此。
    // 一旦写成 `totalAmount ?? 0`,用户看到的是 ¥0,那比"没有报价"更有害:
    // 它看起来像一张真报价单。
    const { totalAmount: _omitted, ...withoutAmount } = PLAN
    listPlans.mockResolvedValue(page([withoutAmount as PlanSummary]))
    const { wrapper } = await mountView()

    expect(wrapper.find('.price').text()).toContain('未出报价')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('coverImage 缺失 ⇒ 画占位,**不渲染空的 img**', async () => {
    const { coverImage: _omitted, ...withoutCover } = PLAN
    listPlans.mockResolvedValue(page([withoutCover as PlanSummary]))
    const { wrapper } = await mountView()

    expect(wrapper.find('.thumb img').exists()).toBe(false)
    expect(wrapper.find('.thumb--empty').text()).toBe('暂无效果图')
  })

  it('报价过期时挂标签', async () => {
    listPlans.mockResolvedValue(page([{ ...PLAN, quoteExpired: true }]))
    const { wrapper } = await mountView()

    expect(wrapper.find('.price .stale').text()).toBe('报价已过期')
  })

  it('parts 缺失时整段不渲染,不留一行空白', async () => {
    const { parts: _omitted, ...withoutParts } = PLAN
    listPlans.mockResolvedValue(page([withoutParts as PlanSummary]))
    const { wrapper } = await mountView()

    expect(wrapper.find('.parts').exists()).toBe(false)
  })
})

describe('字典降级(拿不到字典也要能用)', () => {
  it('字典里没有的 key ⇒ 显示原始 key,不空白', async () => {
    listPlans.mockResolvedValue(page([{ ...PLAN, space: 'attic', style: '' }]))
    const { wrapper } = await mountView()

    expect(wrapper.find('.meta').text()).toBe('attic')
  })

  it('字典接口整个挂掉 ⇒ 卡片照常渲染,只是退化成 key', async () => {
    // 字典是"锦上添花",它挂了不该把整页拖成白屏
    listDictsOfType.mockRejectedValue(new Error('字典服务不可用'))
    listPlans.mockResolvedValue(page([PLAN]))
    const { wrapper } = await mountView()

    expect(wrapper.find('.card h3').text()).toBe('主卫方案')
    expect(wrapper.find('.meta').text()).toBe('bathroom · modern')
  })
})

describe('加载与分页', () => {
  it('第一页请求带 page/size', async () => {
    listPlans.mockResolvedValue(page([PLAN]))
    await mountView()

    expect(listPlans).toHaveBeenCalledWith({ page: 1, size: 9 })
  })

  it('空列表给空态,并说明往哪走', async () => {
    listPlans.mockResolvedValue(page([]))
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('还没有方案')
    expect(wrapper.text()).toContain('选砖')
  })

  it('接口失败 ⇒ 显示后端文案', async () => {
    listPlans.mockRejectedValue(new Error('服务不可用,请稍后重试'))
    const { wrapper } = await mountView()

    expect(wrapper.find('[role="alert"]').text()).toContain('服务不可用')
  })

  it('翻页:第 1 页上一页禁用,点下一页后请求 page=2', async () => {
    listPlans.mockResolvedValue(page([PLAN], 20))
    const { wrapper } = await mountView()

    const buttons = wrapper.findAll('.pager button')
    expect(buttons[0].attributes('disabled')).toBeDefined()

    await buttons[1].trigger('click')
    await flushPromises()

    expect(listPlans).toHaveBeenLastCalledWith({ page: 2, size: 9 })
  })
})

describe('入口', () => {
  it('整张卡片是指向方案详情的链接', async () => {
    listPlans.mockResolvedValue(page([PLAN]))
    const { wrapper } = await mountView()

    expect(wrapper.find('.card').attributes('href')).toBe('/plans/7')
  })

  it('列表页是只读的 —— 不放改名 / 删除这类动作用户会误点的按钮', async () => {
    // 四个管理动作只在详情页有一份实现,免得两处各自漂
    listPlans.mockResolvedValue(page([PLAN]))
    const { wrapper } = await mountView()

    expect(wrapper.findAll('button')).toHaveLength(2) // 只有上一页 / 下一页
  })
})
