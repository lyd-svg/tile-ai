/**
 * F-0.5 推荐搭配:契约一致性 + 列表 + 编辑。
 *
 * 这一页最要紧的一条是**别把备注弄丢**:
 * `PUT /combos/{id}` 是 **items 整体替换**,而 `ComboItem` 上还有可选字段
 * `note`(备注)—— 编辑时没带上它,备注就被**静默清空**,不会有任何报错。
 * 下面有一条用例专门盯这个。
 *
 * 另外两条:部位恒三值(不在字典里)、`name` 只是冗余名**不提交**。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { flushPromises, mount } from '@vue/test-utils'
import { load } from 'js-yaml'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError, ErrorCode } from '../src/api/errors'
import AdminCombosView from '../src/views/admin/AdminCombosView.vue'
import AdminComboEditView from '../src/views/admin/AdminComboEditView.vue'
import { useAdminAuthStore } from '../src/stores/adminAuth'
import type { AdminUserView, Role } from '../src/api/types'

const ADMIN_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-admin.yaml')
const COMMON_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-common.yaml')

interface Contract {
  paths: Record<string, Record<string, {
    parameters?: { name?: string; in?: string; $ref?: string }[]
    requestBody?: { content: Record<string, { schema: { $ref?: string } }> }
  }>>
  components: {
    schemas: Record<string, { required?: string[]; properties?: Record<string, unknown>; items?: { minItems?: number } }>
    parameters: Record<string, { name: string; in: string }>
  }
}

function contract(path: string): Contract {
  return load(readFileSync(path, 'utf8')) as Contract
}

function adminContract(): Contract {
  return contract(ADMIN_CONTRACT)
}

function queryParamNames(operation: Contract['paths'][string][string]): string[] {
  const common = contract(COMMON_CONTRACT)
  return (operation.parameters ?? [])
    .map((parameter) => {
      if (parameter.$ref) {
        const name = parameter.$ref.split('/').pop() ?? ''
        return common.components.parameters[name]
      }
      return parameter as { name: string; in: string }
    })
    .filter((parameter): parameter is { name: string; in: string } => Boolean(parameter))
    .filter((parameter) => parameter.in === 'query')
    .map((parameter) => parameter.name)
    .sort()
}

const listCombos = vi.fn()
const getCombo = vi.fn()
const createCombo = vi.fn()
const updateCombo = vi.fn()
const deleteCombo = vi.fn()
const listAdminDicts = vi.fn()
const listProducts = vi.fn()

vi.mock('@/api/admin/combos', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/combos')>('../src/api/admin/combos')
  return {
    ...actual,
    listCombos: (...args: unknown[]) => listCombos(...args),
    getCombo: (...args: unknown[]) => getCombo(...args),
    createCombo: (...args: unknown[]) => createCombo(...args),
    updateCombo: (...args: unknown[]) => updateCombo(...args),
    deleteCombo: (...args: unknown[]) => deleteCombo(...args),
  }
})

vi.mock('@/api/admin/dicts', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/dicts')>('../src/api/admin/dicts')
  return { ...actual, listAdminDicts: (...args: unknown[]) => listAdminDicts(...args) }
})

vi.mock('@/api/admin/products', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/products')>(
    '../src/api/admin/products',
  )
  return { ...actual, listProducts: (...args: unknown[]) => listProducts(...args) }
})

async function mountView(component: unknown, routeName: string, path: string, role: Role) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  auth.user = { id: 1, username: 'tester', role } as AdminUserView

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/combos', name: 'admin-combos', component: AdminCombosView },
      { path: '/admin/combos/new', name: 'admin-combo-new', component: AdminComboEditView },
      { path: '/admin/combos/:id', name: 'admin-combo-edit', component: AdminComboEditView },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(component as never, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

const mountList = (role: Role = 'super_admin') =>
  mountView(AdminCombosView, 'admin-combos', '/admin/combos', role)
const mountEdit = (role: Role = 'super_admin') =>
  mountView(AdminComboEditView, 'admin-combo-edit', '/admin/combos/7', role)

function buttonWith(wrapper: { findAll: (s: string) => { text: () => string }[] }, text: string) {
  return (wrapper.findAll('button') as unknown as { text: () => string; trigger: (e: string) => Promise<void> }[]).find(
    (button) => button.text().includes(text),
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  window.confirm = vi.fn(() => true)
  listAdminDicts.mockImplementation(async (type?: string) =>
    type === 'space'
      ? [{ type: 'space', key: 'bathroom', label: '卫生间' }]
      : [{ type: 'style', key: 'modern', label: '现代' }],
  )
})

describe('契约一致性', () => {
  it('GET /combos 的 query 是 space/style 加公共分页参数', () => {
    expect(queryParamNames(adminContract().paths['/combos'].get)).toEqual([
      'page',
      'size',
      'space',
      'style',
    ])
  })

  it('ComboUpsert 必填 name/space/style/items,且 items 至少一项', () => {
    const schema = adminContract().components.schemas.ComboUpsert

    expect(schema.required).toEqual(['name', 'space', 'style', 'items'])
    // `items` 是 properties 里的一个属性(数组),minItems 写在它自己身上
    expect((schema.properties?.items as { minItems?: number } | undefined)?.minItems).toBe(1)
    // 请求体里没有 id —— 它在路径里
    expect(Object.keys(schema.properties ?? {})).not.toContain('id')
  })

  it('ComboItem 必填 sku/part', () => {
    const item = contract(COMMON_CONTRACT).components.schemas.ComboItem

    expect(item.required).toEqual(['sku', 'part'])
  })
})

describe('列表', () => {
  it('筛选下拉来自字典,不硬编码', async () => {
    listCombos.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })

    const { wrapper } = await mountList()

    expect(listAdminDicts).toHaveBeenCalledWith('space')
    expect(wrapper.text()).toContain('卫生间')
    expect(wrapper.text()).toContain('现代')
  })

  it('删除要二次确认;取消则不发请求', async () => {
    listCombos.mockResolvedValue({
      list: [{ id: 7, name: '卫生间套装', space: 'bathroom', style: 'modern', status: 'on_sale' }],
      total: 1,
      page: 1,
      size: 10,
    })
    window.confirm = vi.fn(() => false)

    const { wrapper } = await mountList()
    await buttonWith(wrapper, '删除')!.trigger('click')
    await flushPromises()

    expect(deleteCombo).not.toHaveBeenCalled()
  })

  it('删除被引用时服务端那句 30002 原样透出', async () => {
    listCombos.mockResolvedValue({
      list: [{ id: 7, name: '卫生间套装', space: 'bathroom', style: 'modern', status: 'on_sale' }],
      total: 1,
      page: 1,
      size: 10,
    })
    deleteCombo.mockRejectedValue(
      new BusinessError({ ErrorCode: undefined, code: ErrorCode.STATE_NOT_ALLOWED, message: '该搭配已被方案引用,不能删除' }),
    )

    const { wrapper } = await mountList()
    await buttonWith(wrapper, '删除')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('该搭配已被方案引用,不能删除')
  })

  it('参考价缺失显示「—」,不显示 0', async () => {
    listCombos.mockResolvedValue({
      list: [{ id: 7, name: 'x', space: 'bathroom', style: 'modern', status: 'on_sale' }],
      total: 1,
      page: 1,
      size: 10,
    })

    const { wrapper } = await mountList()

    expect(wrapper.find('tbody tr').text()).toContain('—')
  })
})

describe('编辑', () => {
  it('**备注 note 必须原样带回** —— 否则编辑一次就被静默清空', async () => {
    getCombo.mockResolvedValue({
      id: 7,
      name: '卫生间套装',
      space: 'bathroom',
      style: 'modern',
      status: 'on_sale',
      items: [{ sku: 'TL-1001', part: 'floor', note: '淋浴区用防滑款' }],
    })
    updateCombo.mockResolvedValue(undefined)

    const { wrapper } = await mountEdit()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const body = updateCombo.mock.calls[0]![1] as { items: Record<string, unknown>[] }
    expect(body.items[0]!.note).toBe('淋浴区用防滑款')
  })

  it('**提交体里没有 name** —— 它只是冗余名,库里不存', async () => {
    getCombo.mockResolvedValue({
      id: 7,
      name: '卫生间套装',
      space: 'bathroom',
      style: 'modern',
      status: 'on_sale',
      items: [{ sku: 'TL-1001', part: 'floor', name: '雅灰石纹' }],
    })
    updateCombo.mockResolvedValue(undefined)

    const { wrapper } = await mountEdit()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const body = updateCombo.mock.calls[0]![1] as { items: Record<string, unknown>[] }
    expect(body.items[0]).not.toHaveProperty('name')
  })

  it('部位下拉恒为三个值(不走字典)', async () => {
    getCombo.mockResolvedValue({
      id: 7,
      name: 'x',
      space: 'bathroom',
      style: 'modern',
      status: 'on_sale',
      items: [{ sku: 'TL-1001', part: 'floor' }],
    })

    const { wrapper } = await mountEdit()
    const selects = wrapper.findAll('select')
    const partSelect = selects.find((select) =>
      select.findAll('option').some((option) => (option.element as HTMLOptionElement).value === 'local'),
    )

    expect(partSelect).toBeTruthy()
    expect(partSelect!.findAll('option').map((option) => (option.element as HTMLOptionElement).value)).toEqual([
      'floor',
      'wall',
      'local',
    ])
  })

  it('items 为空时客户端先拦住,不发请求', async () => {
    getCombo.mockResolvedValue({
      id: 7,
      name: 'x',
      space: 'bathroom',
      style: 'modern',
      status: 'on_sale',
      items: [],
    })

    const { wrapper } = await mountEdit()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateCombo).not.toHaveBeenCalled()
    expect(wrapper.find('.errors').text()).toContain('至少需要一项')
  })

  it('引用了不存在的编号时,服务端的 10001 逐字段原因原样展开', async () => {
    getCombo.mockResolvedValue({
      id: 7,
      name: 'x',
      space: 'bathroom',
      style: 'modern',
      status: 'on_sale',
      items: [{ sku: 'NOPE', part: 'floor' }],
    })
    updateCombo.mockRejectedValue(
      new BusinessError({
        code: ErrorCode.PARAM_INVALID,
        message: '参数校验失败',
        data: { errors: [{ field: 'items[0].sku', reason: '引用的瓷砖编号不存在:NOPE' }] },
      }),
    )

    const { wrapper } = await mountEdit()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('.errors').text()).toContain('引用的瓷砖编号不存在:NOPE')
  })

  it('「查」会用关键词搜产品,点候选填入编号', async () => {
    getCombo.mockResolvedValue({
      id: 7,
      name: 'x',
      space: 'bathroom',
      style: 'modern',
      status: 'on_sale',
      items: [{ sku: 'TL', part: 'floor' }],
    })
    listProducts.mockResolvedValue({
      list: [{ sku: 'TL-1001', name: '雅灰石纹' }],
      total: 1,
      page: 1,
      size: 20,
    })

    const { wrapper } = await mountEdit()
    await buttonWith(wrapper, '查')!.trigger('click')
    await flushPromises()

    expect(listProducts).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'TL' }))
    await buttonWith(wrapper, 'TL-1001')!.trigger('click')
    await flushPromises()

    const skuInput = wrapper.findAll('input').find(
      (input) => (input.element as HTMLInputElement).placeholder === '如 TL-1001',
    )
    expect((skuInput!.element as HTMLInputElement).value).toBe('TL-1001')
  })
})
