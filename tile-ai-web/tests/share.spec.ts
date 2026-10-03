/**
 * 公开只读分享页(F-1.10)。
 *
 * 这一页和前几页有个根本区别:**看它的人没有账号**。所以除了"渲染对不对",
 * 还要钉住几件"多了就出事"的事:
 *
 *   1. **一个管理动作都不能有** —— 没有改名 / 另存为 / 删除,也**没有重算**
 *      (重算要登录,而这里根本没有"谁"在操作)。多一个按钮就是一个匿名入口;
 *   2. **不发请求的那个分支**:地址里没有令牌时不能拿空串去打接口;
 *   3. 取不到时一律按后端那句 30001 文案显示,**不猜**是"失效了"还是"打错了"
 *      (后端刻意四合一,防的就是枚举令牌的探针,见 §43.4);
 *   4. `materialFee` **整个字段可能不在** ⇒ 一律显示 `—`,**不是 0 也不是 NaN**。
 *
 * ⚠️ 字典要打桩,理由同 catalog.spec.ts:不打桩页面会静默挂空白。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { normalize } from './helpers/antd'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError, ErrorCode } from '../src/api/errors'
import ShareView from '../src/views/site/ShareView.vue'
import type { PlanDetail, PlanPart } from '../src/api/types'

const getSharedPlan = vi.fn()

vi.mock('@/api/c/plans', () => ({
  getSharedPlan: (...args: unknown[]) => getSharedPlan(...args),
  // 这一页**不该碰**下面这些。留着它们是为了"没被调用"这件事能被断言 ——
  // 少一个导出,将来有人误 import 时会变成运行期报错而不是这里先红。
  listPlans: vi.fn(),
  getPlan: vi.fn(),
  renamePlan: vi.fn(),
  deletePlan: vi.fn(),
  copyPlan: vi.fn(),
  recalcPlan: vi.fn(),
  sharePlan: vi.fn(),
  revokeShare: vi.fn(),
}))

const listDictsOfType = vi.fn()
vi.mock('@/api/dicts', () => ({
  listDictsOfType: (...args: unknown[]) => listDictsOfType(...args),
  listDicts: vi.fn(async () => []),
}))

/** 第二行**故意没有** materialFee / pricePerSqm / area —— 模拟"这一次没算出价"。 */
const WALL_PART: PlanPart = { part: 'wall', sku: 'TL-2002' }

const SHARED: PlanDetail = {
  id: 7,
  name: '主卫方案',
  createdAt: '2026-10-01T21:19:22',
  space: 'bathroom',
  style: 'modern',
  quoteExpired: false,
  totalAmount: 2109.41,
  shareToken: 'Tok-42',
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

async function mountView(path = '/share/Tok-42') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/share/:token', name: 'share', component: ShareView },
      // 只有这一条能测"地址里没有令牌" —— `/share/:token` 匹配不到 `/share`
      { path: '/share', name: 'share-blank', component: ShareView },
      { path: '/', name: 'home', component: Blank },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(ShareView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.clearAllMocks()
  getSharedPlan.mockImplementation(async () => ({ ...SHARED }))
  listDictsOfType.mockImplementation(async (type: string) =>
    type === 'space'
      ? [{ type: 'space', key: 'bathroom', label: '卫生间' }]
      : [{ type: 'style', key: 'modern', label: '现代' }],
  )
})

describe('渲染', () => {
  it('拿的是地址里的那个令牌', async () => {
    await mountView('/share/Tok-42')

    expect(getSharedPlan).toHaveBeenCalledWith('Tok-42')
  })

  it('方案名 / 空间风格 / 户型 / 效果图 / 部位表 / 报价都出来', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('h1').text()).toBe('主卫方案')
    expect(wrapper.find('.meta').text()).toBe('卫生间 · 现代')
    expect(wrapper.find('.spaces').text()).toContain('主卫')
    expect(wrapper.find('.main img').attributes('src')).toBe('/img/a.svg')
    expect(wrapper.findAll('.thumbs .thumb-cap').map((node) => node.text())).toEqual([
      '正面',
      '角度 1',
    ])
    expect(wrapper.findAll('.part-table tbody tr')).toHaveLength(2)
    expect(wrapper.find('.totals').text()).toContain('¥2109.41')
  })

  it('多角度图:点缩略图换到主图位置 —— 看的人也能自己翻角度', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('.main img').attributes('src')).toBe('/img/a.svg')

    await wrapper.findAll('.thumbs .thumb')[1].trigger('click')
    expect(wrapper.find('.main img').attributes('src')).toBe('/img/b.svg')
    expect(wrapper.find('.main .caption').text()).toBe('角度 1')
  })

  it('顶部说明这是一份只读分享', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('.banner').text()).toContain('只读')
  })

  it('materialFee 缺失的那一行显示 `—`,**不是 0 也不是 NaN**', async () => {
    const { wrapper } = await mountView()

    const cells = wrapper.findAll('.part-table tbody tr')[1].findAll('td')
    expect(cells.map((cell) => cell.text())).toEqual(['墙面', 'TL-2002', '—', '—', '—'])
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('quote 缺失 ⇒ 给可读空态,不留 NaN', async () => {
    const { quote: _omitted, ...withoutQuote } = SHARED
    getSharedPlan.mockResolvedValue({ ...withoutQuote, totalAmount: undefined })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('这份方案没有报价')
    expect(wrapper.find('.quote-table').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('quoteExpired ⇒ 提醒这是当时的价(但没有重算按钮 —— 看的人改不了)', async () => {
    getSharedPlan.mockResolvedValue({ ...SHARED, quoteExpired: true })
    const { wrapper } = await mountView()

    expect(wrapper.find('.stale').text()).toContain('当时的价')
    // 不该有「重算」入口(看的人没有账号,重算要登录)。
    // 注意口径:缩略图也是 button,但那是**切换视角**,不算管理动作 —— 见下面那条。
    expect(
      wrapper.findAll('button').some((item) => normalize(item.text()).includes(normalize('重算'))),
    ).toBe(false)
  })

  it('字典接口失败 ⇒ 照常渲染方案,只退化成显示 key', async () => {
    listDictsOfType.mockRejectedValue(new Error('字典服务不可用'))
    const { wrapper } = await mountView()

    expect(wrapper.find('h1').text()).toBe('主卫方案')
    expect(wrapper.find('.meta').text()).toBe('bathroom · modern')
  })
})

describe('⚠️ 一个管理动作都不能有', () => {
  it('整页只有「切换视角」这一种按钮(改名 / 另存为 / 删除 / 重算都不该出现)', async () => {
    const { wrapper } = await mountView()

    // ⚠️ 2026-10-03 起这条换了口径。原先写的是 `findAll('button')).toHaveLength(0)`,
    // 而多角度缩略图改成可点之后,页面**合法地**有了按钮 —— 它是"切换视角",
    // 不是管理动作。这一页真正要守的是"**看的人改不了任何东西**",不是"一个 button 都没有"。
    // 改成**允许清单**:出现任何别的按钮都会红,逼人显式更新这条断言(而不是悄悄放行)。
    const labels = wrapper.findAll('button').map((item) => item.text())
    expect(
      labels.every((text) => /^(正面|角度 \d+)$/.test(text)),
      `出现了非视角按钮:${labels.join(' / ')}`,
    ).toBe(true)

    for (const word of ['改名', '另存为', '删除', '重算', '保存']) {
      expect(wrapper.text(), `公开页不该出现「${word}」`).not.toContain(word)
    }
  })

  it('不碰任何写接口', async () => {
    await mountView()

    // 打桩表里那些写函数**一次都没被调到** —— 公开页只有一条读路径
    const plans = await import('@/api/c/plans')
    expect(plans.sharePlan).not.toHaveBeenCalled()
    expect(plans.revokeShare).not.toHaveBeenCalled()
    expect(plans.renamePlan).not.toHaveBeenCalled()
    expect(plans.deletePlan).not.toHaveBeenCalled()
    expect(plans.recalcPlan).not.toHaveBeenCalled()
    expect(plans.getPlan).not.toHaveBeenCalled()
  })
})

describe('打不开的时候', () => {
  it('30001 ⇒ 显示后端文案,并告诉看的人该找谁', async () => {
    // 后端**故意**不区分「令牌不存在 / 已废除 / 方案已删除」(§43.4)——
    // 所以这一页也不猜,原样显示那句话
    getSharedPlan.mockRejectedValue(
      new BusinessError({ code: ErrorCode.NOT_FOUND, message: '方案不存在' }),
    )
    const { wrapper } = await mountView()

    expect(wrapper.find('[role="alert"]').text()).toContain('方案不存在')
    expect(wrapper.text()).toContain('分享给你的人')
    // 不能画出一个空壳的方案页 —— 那会让人以为方案就是空的
    expect(wrapper.find('.part-table').exists()).toBe(false)
  })

  it('地址里没有令牌 ⇒ 走错误态,**一个请求都不发**', async () => {
    const { wrapper } = await mountView('/share')

    expect(getSharedPlan).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toContain('没有令牌')
  })

  it('整页文本里不会出现 undefined', async () => {
    getSharedPlan.mockResolvedValue({ ...SHARED })
    const { wrapper } = await mountView()

    expect(wrapper.text()).not.toContain('undefined')
  })
})

describe('换一条分享链接', () => {
  it('令牌变了要重新拉 —— 组件实例被复用,onMounted 不会再跑', async () => {
    const { router } = await mountView('/share/AAA')
    expect(getSharedPlan).toHaveBeenCalledWith('AAA')

    await router.push('/share/BBB')
    await flushPromises()

    expect(getSharedPlan).toHaveBeenLastCalledWith('BBB')
  })
})
