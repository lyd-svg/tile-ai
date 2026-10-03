/**
 * 产品详情页的「选用部位」(步骤 1)。
 *
 * 选砖流程到这一步为止只有一件必填信息:**这块砖铺在哪个部位**。
 * 这里验的就是它真的进了草稿,以及没选砖时"去录户型"进不去。
 *
 * ⚠️ 字典必须打桩 —— 页面 `onMounted` 里先取字典再取详情,不打桩那次真实 fetch
 * 会挂住(见 catalog.spec.ts 里同样的教训)。
 */

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { normalize } from './helpers/antd'
import { createMemoryHistory, createRouter } from 'vue-router'

import ProductDetailView from '../src/views/site/ProductDetailView.vue'
import { useSelectionDraftStore } from '../src/stores/selectionDraft'
import type { Sku } from '../src/api/types'

const getProduct = vi.fn()
vi.mock('@/api/c/catalog', async () => {
  const actual = await vi.importActual<typeof import('../src/api/c/catalog')>(
    '../src/api/c/catalog',
  )
  return { ...actual, getProduct: (...args: unknown[]) => getProduct(...args) }
})

vi.mock('@/api/dicts', () => ({
  listDictsOfType: vi.fn(async () => []),
  listDicts: vi.fn(async () => []),
}))

const PRODUCT: Sku = {
  sku: 'TL-1001',
  name: '雅灰石纹',
  material: '釉面砖',
  pricePerSqm: 268,
  status: 'on_sale',
}

const Blank = { template: '<div />' }

async function mountDetail() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/products', name: 'products', component: Blank },
      { path: '/products/:sku', name: 'product-detail', component: ProductDetailView },
      { path: '/layout', name: 'layout', component: Blank },
    ],
  })
  await router.push('/products/TL-1001')
  await router.isReady()

  const wrapper = mount(ProductDetailView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, draft: useSelectionDraftStore() }
}

/** 找到写着某段文案的部位按钮。 */
function partButton(wrapper: Awaited<ReturnType<typeof mountDetail>>['wrapper'], text: string) {
  const button = wrapper.findAll('button.part').find((item) => item.text().includes(text))
  if (!button) throw new Error(`找不到部位按钮:${text}`)
  return button
}

beforeEach(() => {
  vi.clearAllMocks()
  getProduct.mockResolvedValue(PRODUCT)
})

describe('选定部位', () => {
  it('点"地面"就写进草稿,并给出确认', async () => {
    const { wrapper, draft } = await mountDetail()

    await partButton(wrapper, '地面').trigger('click')

    expect(draft.partOf('floor')?.sku).toBe('TL-1001')
    expect(draft.partOf('floor')?.name).toBe('雅灰石纹')
    expect(wrapper.text()).toContain('已加入:地面 · 雅灰石纹')
  })

  it('再点同一个部位 = 移除(同一个按钮承担双向,省一个按钮)', async () => {
    const { wrapper, draft } = await mountDetail()

    await partButton(wrapper, '地面').trigger('click')
    await partButton(wrapper, '地面').trigger('click')

    expect(draft.hasParts).toBe(false)
  })

  it('地面与墙面可以各放一块砖', async () => {
    const { wrapper, draft } = await mountDetail()

    await partButton(wrapper, '地面').trigger('click')
    await partButton(wrapper, '墙面').trigger('click')

    expect(draft.selectedCount).toBe(2)
    expect(wrapper.text()).toContain('已选部位(2)')
  })
})

describe('去录户型', () => {
  it('一块砖都没选时进不去,并说明原因', async () => {
    const { wrapper } = await mountDetail()

    const cta = wrapper.findAll('button').find((item) => normalize(item.text()).includes(normalize('去录户型')))
    expect(cta?.attributes('disabled')).toBeDefined()
  })

  it('选了砖之后跳到户型录入页', async () => {
    const { wrapper, router } = await mountDetail()

    await partButton(wrapper, '地面').trigger('click')
    const cta = wrapper.findAll('button').find((item) => normalize(item.text()).includes(normalize('去录户型')))
    await cta?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('layout')
  })

  it('页面不再写"尚未开放" —— 这个入口现在是真的', async () => {
    const { wrapper } = await mountDetail()
    expect(wrapper.text()).not.toContain('尚未开放')
  })
})
