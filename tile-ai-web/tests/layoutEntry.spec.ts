/**
 * 户型录入(F-1.5)。
 *
 * 两条路都要验到:选模板、手动录入(文字 / 参数)。
 * 另外两条是**失败时的可用性**:
 *   - 10001 会把逐字段原因放在 `data.errors[]` —— 必须逐项列出来,
 *     只显示一句"参数不对"等于让用户自己猜;
 *   - 模板的 `layout` 在契约里**可选** —— 没配要说明,不能画一个空户型。
 *
 * ⚠️ 字典必须打桩:页面 onMounted 会取字典,不打桩那次真实 fetch 会挂住,
 * 表现为"页面空白"而不是报错(与 catalog.spec.ts 同一个坑)。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { normalize } from './helpers/antd'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError, ErrorCode } from '../src/api/errors'
import LayoutEntryView from '../src/views/site/LayoutEntryView.vue'
import { useSelectionDraftStore } from '../src/stores/selectionDraft'
import type { LayoutModel, UnitTemplate } from '../src/api/types'

const listLayoutTemplates = vi.fn()
const parseLayout = vi.fn()
vi.mock('@/api/c/layout', () => ({
  listLayoutTemplates: (...args: unknown[]) => listLayoutTemplates(...args),
  parseLayout: (...args: unknown[]) => parseLayout(...args),
}))

vi.mock('@/api/dicts', () => ({
  listDictsOfType: vi.fn(async () => [
    { type: 'space', key: 'bathroom', label: '卫生间' },
  ]),
  listDicts: vi.fn(async () => []),
}))

const LAYOUT: LayoutModel = {
  spaces: [
    {
      type: 'bathroom',
      name: '主卫',
      length: 2,
      width: 1.8,
      height: 2.7,
      areas: { floor: 3.6, wall: 13.9, local: 0 },
    },
  ],
}

const TEMPLATES: UnitTemplate[] = [
  { id: 1, name: '小两居', structure: '两室一厅', areaRange: '70-90㎡', layout: LAYOUT },
  { id: 2, name: '无户型的模板' },
]

const Blank = { template: '<div />' }

async function mountView(withParts = true) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const draft = useSelectionDraftStore()
  if (withParts) draft.setPart('floor', 'TL-1001', { name: '雅灰石纹' })

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/layout', name: 'layout', component: LayoutEntryView },
      { path: '/products', name: 'products', component: Blank },
      { path: '/generate', name: 'generate', component: Blank },
    ],
  })
  await router.push('/layout')
  await router.isReady()

  const wrapper = mount(LayoutEntryView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, draft }
}

/** 切换顶层 tab。 */
async function switchMode(
  wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'],
  text: string,
) {
  const tab = wrapper.findAll('button.tab').find((item) => item.text().includes(text))
  if (!tab) throw new Error(`找不到 tab:${text}`)
  await tab.trigger('click')
}

/**
 * 点「解析户型」。
 *
 * ⚠️ **原来是 `wrapper.find('button.primary')`** —— 一个 **class 选择器**。
 * 那个按钮迁到 antd 之后,`primary` 这个 class **没了**(antd 用 `ant-btn-primary`),
 * 于是**找不到按钮**,四条用例一起红。
 * ⇒ 改成**按归一化后的文案找**(见 `tests/helpers/antd.ts`)。
 *
 * 这是同一类盲区的**第 4 次**:前三次是「断言里没有 select 字样」「测试 helper 名字不同」
 * 「按 `.text().includes` 搜不到 `.text() ===`」。**按形状搜也会漏 —— 得按"依赖了什么"搜。**
 */
async function clickParse(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper']) {
  const target = wrapper
    .findAll('button')
    .find((b) => normalize(b.text()).includes(normalize('解析户型')))
  if (!target) throw new Error('找不到「解析户型」按钮')
  await target.trigger('click')
}

beforeEach(() => {
  vi.clearAllMocks()
  listLayoutTemplates.mockResolvedValue({ list: TEMPLATES, total: 2, page: 1, size: 20 })
  parseLayout.mockResolvedValue(LAYOUT)
})

describe('模板', () => {
  it('列出模板;选用后把户型写进草稿,并显示派生面积', async () => {
    const { wrapper, draft } = await mountView()

    const card = wrapper.findAll('button.template').find((item) => item.text().includes('小两居'))
    await card?.trigger('click')
    await flushPromises()

    expect(draft.layout).toEqual(LAYOUT)
    expect(wrapper.text()).toContain('3.6')
    expect(wrapper.text()).toContain('13.9')
  })

  it('模板没有配户型时给出提示,而不是画一个空户型', async () => {
    const { wrapper, draft } = await mountView()

    const card = wrapper
      .findAll('button.template')
      .find((item) => item.text().includes('无户型的模板'))
    await card?.trigger('click')

    expect(draft.layout).toBeNull()
    expect(wrapper.find('[role="alert"]').text()).toContain('尚未配置户型')
  })
})

describe('手动录入', () => {
  it('文字解析成功后写入草稿', async () => {
    const { wrapper, draft } = await mountView()
    await switchMode(wrapper, '手动录入')

    await wrapper.find('textarea').setValue('卫生间 2m×1.8m')
    await clickParse(wrapper)
    await flushPromises()

    expect(parseLayout).toHaveBeenCalledWith({ text: '卫生间 2m×1.8m' })
    expect(draft.layout).toEqual(LAYOUT)
  })

  it('文字为空时不发请求,先提示', async () => {
    const { wrapper } = await mountView()
    await switchMode(wrapper, '手动录入')

    await clickParse(wrapper)

    expect(parseLayout).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toContain('描述一下户型')
  })

  it('参数方式:尺寸不合规时前端先挡,不发请求', async () => {
    const { wrapper } = await mountView()
    await switchMode(wrapper, '手动录入')
    await switchMode(wrapper, '填参数')

    const numbers = wrapper.findAll('input[type="number"]')
    await numbers[0].setValue('2') // 长
    // 宽 / 层高留空 → 不该提交
    await clickParse(wrapper)

    expect(parseLayout).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toContain('大于 0')
  })

  it('填参数:门窗与它的位置会原样提交', async () => {
    parseLayout.mockResolvedValue(LAYOUT)
    const { wrapper } = await mountView()
    await switchMode(wrapper, '手动录入')
    await switchMode(wrapper, '填参数')

    const numbers = wrapper.findAll('input[type="number"]')
    await numbers[0].setValue('2') // 长
    await numbers[1].setValue('1.8') // 宽
    await numbers[2].setValue('2.4') // 层高

    const addOpening = wrapper
      .findAll('button')
      .find((button) => button.text().includes('加一扇门窗'))!
    await addOpening.trigger('click')
    await flushPromises()

    // 门窗行里的三个数字框依次是:宽 / 高 / 距墙角。前两个有默认值,只补位置。
    const openingNumbers = wrapper.findAll('.opening-row input[type="number"]')
    await openingNumbers[2].setValue('0.55')

    await clickParse(wrapper)
    await flushPromises()

    expect(parseLayout).toHaveBeenCalledWith({
      params: expect.objectContaining({
        spaces: [
          expect.objectContaining({
            openings: [{ type: 'door', w: 0.8, h: 2.1, wall: 'back', offset: 0.55 }],
          }),
        ],
      }),
    })
  })

  it('填参数:门窗位置留空时不带那三个键(位置是可选的)', async () => {
    parseLayout.mockResolvedValue(LAYOUT)
    const { wrapper } = await mountView()
    await switchMode(wrapper, '手动录入')
    await switchMode(wrapper, '填参数')

    const numbers = wrapper.findAll('input[type="number"]')
    await numbers[0].setValue('2')
    await numbers[1].setValue('1.8')
    await numbers[2].setValue('2.4')

    const addOpening = wrapper
      .findAll('button')
      .find((button) => button.text().includes('加一扇门窗'))!
    await addOpening.trigger('click')
    await flushPromises()

    // ⚠️ 新加的门窗默认**只填了宽高、没填位置**(`offset` 是空的)——
    // 这正好就是"位置留空"那种情况,不用再去动那个下拉。

    await clickParse(wrapper)
    await flushPromises()

    const body = parseLayout.mock.calls[0]![0] as {
      params: { spaces: { openings?: Record<string, unknown>[] }[] }
    }
    // **不带位置键** —— 带上 `wall: ''` / `offset: null` 会被契约的枚举校验拒收
    expect(body.params.spaces[0]!.openings).toEqual([{ type: 'door', w: 0.8, h: 2.1 }])
  })
})

describe('解析失败', () => {
  it('10001 的逐字段原因要**逐项**列出来,而不是只说一句"参数不对"', async () => {
    parseLayout.mockRejectedValue(
      new BusinessError({
        code: ErrorCode.PARAM_INVALID,
        message: '参数校验失败',
        data: {
          errors: [
            { field: 'spaces[0].height', reason: '层高不能为空' },
            { field: 'spaces[0].width', reason: '宽度必须大于 0' },
          ],
        },
      }),
    )
    const { wrapper, draft } = await mountView()
    await switchMode(wrapper, '手动录入')

    await wrapper.find('textarea').setValue('卫生间')
    await clickParse(wrapper)
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('参数校验失败')
    const listed = wrapper.findAll('.errors li').map((item) => item.text())
    expect(listed).toEqual([
      'spaces[0].height:层高不能为空',
      'spaces[0].width:宽度必须大于 0',
    ])
    // 失败时不能留下上一次的结果
    expect(draft.layout).toBeNull()
  })
})

describe('下一步', () => {
  it('没选砖时"去生成"进不去,并提示先选砖', async () => {
    const { wrapper } = await mountView(false)

    const cta = wrapper.findAll('button').find((item) => normalize(item.text()).includes(normalize('去生成')))
    // 还没解析出户型,结果区根本不在 —— 先确认有回选砖的链接
    expect(wrapper.text()).toContain('还没有选砖')
    expect(cta).toBeUndefined()
  })

  it('户型就绪且已选砖时能跳到生成页', async () => {
    const { wrapper, router } = await mountView()

    const card = wrapper.findAll('button.template').find((item) => item.text().includes('小两居'))
    await card?.trigger('click')
    await flushPromises()

    const cta = wrapper.findAll('button').find((item) => normalize(item.text()).includes(normalize('去生成')))
    expect(cta?.attributes('disabled')).toBeUndefined()
    await cta?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('generate')
  })
})
