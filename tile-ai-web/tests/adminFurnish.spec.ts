/**
 * F-0.7 陈设风格:契约一致性 + 矩阵语义。
 *
 * 这一页的设计核心是**"重复组合在结构上不可能出现"**,所以用例也照这个来钉:
 *   - 矩阵的每个格子就是一个确定的 `(space, style)`,组合**不是手敲**的
 *     → 表单里**没有** space/style 的输入控件;
 *   - 契约里**只有 PUT(upsert)没有 POST** → 模块里也就不存在 create 函数;
 *   - 服务端万一回了重复组合,矩阵**仍只画一格**(留第一条 + 一条警示)。
 *
 * 另外两条:`status` 是 on_sale/off_sale(**不是**字典那种 enabled/disabled);
 * 字典拉不到时**已有配置仍要看得见**(两件事分开兜底)。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { flushPromises, mount } from '@vue/test-utils'
import { load } from 'js-yaml'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import * as furnishApi from '../src/api/admin/furnish'
import AdminFurnishView from '../src/views/admin/AdminFurnishView.vue'
import { useAdminAuthStore } from '../src/stores/adminAuth'
import type { AdminUserView, Role } from '../src/api/types'

const ADMIN_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-admin.yaml')
const COMMON_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-common.yaml')

interface Operation {
  parameters?: { name?: string; in?: string; $ref?: string }[]
  requestBody?: { content: Record<string, { schema: { $ref?: string } }> }
}

interface Contract {
  paths: Record<string, Record<string, Operation>>
  components: {
    schemas: Record<string, { required?: string[]; properties?: Record<string, unknown> }>
    parameters: Record<string, { name: string; in: string }>
  }
}

function contract(path: string): Contract {
  return load(readFileSync(path, 'utf8')) as Contract
}

function adminContract(): Contract {
  return contract(ADMIN_CONTRACT)
}

function queryParamNames(operation: Operation): string[] {
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

const listFurnishStyles = vi.fn()
const upsertFurnishStyle = vi.fn()
const deleteFurnishStyle = vi.fn()
const listAdminDicts = vi.fn()

vi.mock('@/api/admin/furnish', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/furnish')>(
    '../src/api/admin/furnish',
  )
  return {
    ...actual,
    listFurnishStyles: (...args: unknown[]) => listFurnishStyles(...args),
    upsertFurnishStyle: (...args: unknown[]) => upsertFurnishStyle(...args),
    deleteFurnishStyle: (...args: unknown[]) => deleteFurnishStyle(...args),
  }
})

vi.mock('@/api/admin/dicts', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/dicts')>('../src/api/admin/dicts')
  return { ...actual, listAdminDicts: (...args: unknown[]) => listAdminDicts(...args) }
})

const SPACE_DICT = [
  { id: 1, type: 'space', key: 'bathroom', label: '卫生间', status: 'enabled' },
  { id: 2, type: 'space', key: 'living_room', label: '客厅', status: 'enabled' },
]
const STYLE_DICT = [
  { id: 3, type: 'style', key: 'modern', label: '现代', status: 'enabled' },
  { id: 4, type: 'style', key: 'nordic', label: '北欧', status: 'enabled' },
]

async function mountAs(role: Role) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  auth.user = { id: 1, username: 'tester', role } as AdminUserView

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/furnish', name: 'admin-furnish', component: AdminFurnishView }],
  })
  await router.push('/admin/furnish')
  await router.isReady()

  const wrapper = mount(AdminFurnishView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

function buttonWith(wrapper: Awaited<ReturnType<typeof mountAs>>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function cellWith(wrapper: Awaited<ReturnType<typeof mountAs>>, text: string) {
  return wrapper.findAll('.cell button').filter((button) => button.text().includes(text))
}

// 确认框:页面只负责"调它 + 尊重答案";弹窗本身由 tests/appConfirm.spec.ts 驱动真 Modal 覆盖。
const appConfirm = vi.fn()
vi.mock('@/components/base/AppConfirm', async () => {
  const actual = await vi.importActual<typeof import('../src/components/base/AppConfirm')>(
    '../src/components/base/AppConfirm',
  )
  return { ...actual, appConfirm: (...args: unknown[]) => appConfirm(...args) }
})

beforeEach(() => {
  vi.clearAllMocks()
  appConfirm.mockResolvedValue(true)
  listAdminDicts.mockImplementation(async (type?: string) =>
    type === 'space' ? SPACE_DICT : STYLE_DICT,
  )
})

describe('契约一致性', () => {
  it('GET /furnish 的 query 参数就是 space / style', () => {
    expect(queryParamNames(adminContract().paths['/furnish'].get)).toEqual(['space', 'style'])
  })

  it('PUT /furnish 请求体必填 space/style/prompt,字段恰好五个', () => {
    const schema = adminContract().components.schemas.FurnishStyleUpsert

    expect(schema.required).toEqual(['space', 'style', 'prompt'])
    expect(Object.keys(schema.properties ?? {}).sort()).toEqual([
      'coverImage',
      'prompt',
      'space',
      'status',
      'style',
    ])
  })

  it('**契约里没有 POST** —— 组合靠 PUT upsert,不靠新增', () => {
    expect(adminContract().paths['/furnish'].post).toBeUndefined()
  })

  it('**模块也不导出 create** —— 从接口上就造不出重复组合', () => {
    const exported = Object.keys(furnishApi)

    expect(exported.some((name) => name.startsWith('create'))).toBe(false)
  })
})

describe('矩阵', () => {
  it('按字典画格子:每个空间 × 风格一个格子', async () => {
    listFurnishStyles.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')

    // 2 个空间 × 2 个风格 = 4 个格子
    expect(wrapper.findAll('.cell')).toHaveLength(4)
    expect(wrapper.text()).toContain('卫生间')
    expect(wrapper.text()).toContain('北欧')
  })

  it('已有配置落在正确的格子上', async () => {
    listFurnishStyles.mockResolvedValue([
      { id: 11, space: 'bathroom', style: 'modern', prompt: '浅灰浴巾', status: 'on_sale' },
    ])

    const wrapper = await mountAs('super_admin')

    expect(cellWith(wrapper, '已配置')).toHaveLength(1)
    expect(cellWith(wrapper, '未配置')).toHaveLength(3)
  })

  it('**同一组合出现两条时只画一格**,并给一条警示', async () => {
    listFurnishStyles.mockResolvedValue([
      { id: 11, space: 'bathroom', style: 'modern', prompt: '第一条', status: 'on_sale' },
      { id: 12, space: 'bathroom', style: 'modern', prompt: '第二条', status: 'on_sale' },
    ])

    const wrapper = await mountAs('super_admin')

    expect(cellWith(wrapper, '已配置')).toHaveLength(1)
    expect(wrapper.find('[role="alert"]').text()).toContain('重复')
  })

  it('**字典拉不到时,已有配置仍然看得见**', async () => {
    listFurnishStyles.mockResolvedValue([
      { id: 11, space: 'bathroom', style: 'modern', prompt: '浅灰浴巾', status: 'on_sale' },
    ])
    listAdminDicts.mockRejectedValue(new Error('字典挂了'))

    const wrapper = await mountAs('super_admin')

    // 轴退化成"已有配置里出现过的 key",配置没有丢
    expect(wrapper.findAll('.cell')).toHaveLength(1)
    expect(cellWith(wrapper, '已配置')).toHaveLength(1)
  })

  it('字典里没有的组合**不静默丢弃**,标成「未在字典中」', async () => {
    listFurnishStyles.mockResolvedValue([
      { id: 11, space: 'kitchen', style: 'modern', prompt: '厨房', status: 'on_sale' },
    ])

    const wrapper = await mountAs('super_admin')

    expect(wrapper.text()).toContain('未在字典中')
    expect(cellWith(wrapper, '已配置')).toHaveLength(1)
  })
})

describe('编辑', () => {
  it('点格子开表单;**表单里没有 space/style 的输入控件**', async () => {
    listFurnishStyles.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')
    await cellWith(wrapper, '未配置')[0]!.trigger('click')
    await flushPromises()

    const form = wrapper.find('form')
    expect(form.find('h2').text()).toContain('bathroom')
    expect(form.findAll('input[type="text"]')).toHaveLength(1) // 只有展示图 URL
    expect(form.findAll('textarea')).toHaveLength(1)
    // 唯一的 select 是"状态",不是空间/风格选择器
    const selects = form.findAll('select')
    expect(selects).toHaveLength(1)
    expect((selects[0].element as HTMLSelectElement).value).toBe('on_sale')
  })

  it('**同一格再保存一次就是更新**(调 upsert,space/style 与首次逐字相同)', async () => {
    listFurnishStyles.mockResolvedValue([])
    upsertFurnishStyle.mockResolvedValue(undefined)

    const wrapper = await mountAs('super_admin')
    await cellWith(wrapper, '未配置')[0]!.trigger('click')
    await flushPromises()

    await wrapper.find('form textarea').setValue('浅灰浴巾 + 木纹')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(upsertFurnishStyle).toHaveBeenCalledTimes(2)
    const [first, second] = upsertFurnishStyle.mock.calls
    expect(first![0].space).toBe('bathroom')
    expect(second![0].space).toBe('bathroom')
    expect(second![0].style).toBe(first![0].style)
  })

  it('提示词为空时不发请求', async () => {
    listFurnishStyles.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')
    await cellWith(wrapper, '未配置')[0]!.trigger('click')
    await flushPromises()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(upsertFurnishStyle).not.toHaveBeenCalled()
    expect(wrapper.find('.errors').text()).toContain('陈设提示词')
  })

  it('删除要二次确认;取消不发请求', async () => {
    listFurnishStyles.mockResolvedValue([
      { id: 11, space: 'bathroom', style: 'modern', prompt: 'x', status: 'on_sale' },
    ])
    appConfirm.mockResolvedValue(false)

    const wrapper = await mountAs('super_admin')
    await cellWith(wrapper, '已配置')[0]!.trigger('click')
    await flushPromises()
    await buttonWith(wrapper, '删除这条配置')!.trigger('click')
    await flushPromises()

    expect(deleteFurnishStyle).not.toHaveBeenCalled()
  })
})

describe('权限', () => {
  it('只读角色看得到矩阵,但没有任何写入口', async () => {
    listFurnishStyles.mockResolvedValue([])

    const wrapper = await mountAs('editor')
    await cellWith(wrapper, '未配置')[0]!.trigger('click')
    await flushPromises()

    expect(buttonWith(wrapper, '保存')).toBeUndefined()
    expect(buttonWith(wrapper, '删除这条配置')).toBeUndefined()
    expect(wrapper.find('form textarea').attributes('readonly')).toBeDefined()
  })
})

describe('诚实边界', () => {
  it('整页文本不出现 undefined / NaN', async () => {
    listFurnishStyles.mockResolvedValue([
      { id: 11, space: 'bathroom', style: 'modern', prompt: 'x', status: 'on_sale' },
    ])

    const wrapper = await mountAs('super_admin')
    const text = wrapper.text()

    expect(text).not.toContain('undefined')
    expect(text).not.toContain('NaN')
  })
})
