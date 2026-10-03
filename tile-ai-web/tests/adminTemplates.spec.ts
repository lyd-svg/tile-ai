/**
 * F-0.6 户型模板:契约一致性 + 列表 + 编辑。
 *
 * 这一页的**硬规则**是:`areas`(地面/墙面/局部面积)是**服务端按长宽高派生**的字段。
 * 读详情时后端会回填它,但前端:
 *   - 表单里**不能有它的输入项**;
 *   - 提交前**必须显式剔除** —— 整对象展开就会把它当"用户输入"送回去,
 *     等于前端也实现了一遍派生规则。
 * 下面有一条用例就是加载一个**带 areas 的模板**,断言提交体里没有它。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { flushPromises, mount } from '@vue/test-utils'
import { load } from 'js-yaml'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import AdminTemplatesView from '../src/views/admin/AdminTemplatesView.vue'
import AdminTemplateEditView from '../src/views/admin/AdminTemplateEditView.vue'
import { useAdminAuthStore } from '../src/stores/adminAuth'
import type { AdminUserView, Role } from '../src/api/types'

const ADMIN_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-admin.yaml')
const COMMON_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-common.yaml')

interface Contract {
  paths: Record<string, Record<string, {
    parameters?: { name?: string; in?: string; $ref?: string }[]
  }>>
  components: {
    schemas: Record<string, { required?: string[]; properties?: Record<string, { $ref?: string }> }>
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

const listTemplates = vi.fn()
const getTemplate = vi.fn()
const createTemplate = vi.fn()
const updateTemplate = vi.fn()
const deleteTemplate = vi.fn()
const listAdminDicts = vi.fn()

vi.mock('@/api/admin/templates', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/templates')>(
    '../src/api/admin/templates',
  )
  return {
    ...actual,
    listTemplates: (...args: unknown[]) => listTemplates(...args),
    getTemplate: (...args: unknown[]) => getTemplate(...args),
    createTemplate: (...args: unknown[]) => createTemplate(...args),
    updateTemplate: (...args: unknown[]) => updateTemplate(...args),
    deleteTemplate: (...args: unknown[]) => deleteTemplate(...args),
  }
})

vi.mock('@/api/admin/dicts', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/dicts')>('../src/api/admin/dicts')
  return { ...actual, listAdminDicts: (...args: unknown[]) => listAdminDicts(...args) }
})

async function mountView(component: unknown, path: string, role: Role) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  auth.user = { id: 1, username: 'tester', role } as AdminUserView

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/templates', name: 'admin-templates', component: AdminTemplatesView },
      { path: '/admin/templates/new', name: 'admin-template-new', component: AdminTemplateEditView },
      { path: '/admin/templates/:id', name: 'admin-template-edit', component: AdminTemplateEditView },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(component as never, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

const mountList = (role: Role = 'super_admin') =>
  mountView(AdminTemplatesView, '/admin/templates', role)
const mountEdit = (role: Role = 'super_admin') =>
  mountView(AdminTemplateEditView, '/admin/templates/5', role)

function buttonWith(wrapper: Awaited<ReturnType<typeof mountEdit>>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

/** 一个带**派生面积**的模板 —— 服务端会回填 areas,前端不该把它送回去。 */
const TEMPLATE_WITH_AREAS = {
  id: 5,
  name: '两室一卫',
  roomCount: 2,
  areaRange: '60-80㎡',
  structure: '两室一厅',
  status: 'on_sale' as const,
  layout: {
    spaces: [
      {
        type: 'bathroom',
        name: '主卫',
        length: 2,
        width: 1.8,
        height: 2.7,
        wallCount: 4,
        areas: { floor: 3.6, wall: 18.24, local: 0 },
        openings: [{ type: 'door' as const, w: 0.8, h: 2.1 }],
      },
    ],
  },
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
  listAdminDicts.mockResolvedValue([{ type: 'space', key: 'bathroom', label: '卫生间' }])
})

describe('契约一致性', () => {
  it('GET /templates 的 query 是三个筛选 + 分页', () => {
    expect(queryParamNames(adminContract().paths['/templates'].get)).toEqual([
      'areaRange',
      'page',
      'roomCount',
      'size',
      'structure',
    ])
  })

  it('UnitTemplateUpsert 必填 name 与 layout,layout 引用 common 的 LayoutModel', () => {
    const schema = adminContract().components.schemas.UnitTemplateUpsert

    expect(schema.required).toEqual(['name', 'layout'])
    expect(schema.properties?.layout?.$ref).toContain('LayoutModel')
  })
})

describe('列表', () => {
  it('缺失字段显示「—」', async () => {
    listTemplates.mockResolvedValue({
      list: [{ id: 1, name: '极简' }],
      total: 1,
      page: 1,
      size: 10,
    })

    const wrapper = await mountList()

    expect(wrapper.find('tbody tr').text()).toContain('—')
  })

  it('删除要二次确认;取消则不发请求', async () => {
    listTemplates.mockResolvedValue({
      list: [{ id: 1, name: '极简', status: 'on_sale' }],
      total: 1,
      page: 1,
      size: 10,
    })
    appConfirm.mockResolvedValue(false)

    const wrapper = await mountList()
    await buttonWith(wrapper, '删除')!.trigger('click')
    await flushPromises()

    expect(deleteTemplate).not.toHaveBeenCalled()
  })
})

describe('编辑', () => {
  it('**提交体里没有 areas** —— 它是派生字段,不是用户输入', async () => {
    getTemplate.mockResolvedValue(TEMPLATE_WITH_AREAS)
    updateTemplate.mockResolvedValue(undefined)

    const wrapper = await mountEdit()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const body = updateTemplate.mock.calls[0]![1] as {
      layout: { spaces: Record<string, unknown>[] }
    }
    expect(body.layout.spaces[0]).not.toHaveProperty('areas')
    expect(body.layout.spaces[0]!.type).toBe('bathroom')
    // 派生面积照旧**只读展示**在页面上
    expect(wrapper.text()).toContain('派生面积(只读)')
  })

  it('页面上没有任何绑定 areas 的输入项', async () => {
    getTemplate.mockResolvedValue(TEMPLATE_WITH_AREAS)

    const wrapper = await mountEdit()

    const labels = wrapper.findAll('label').map((label) => label.text())
    expect(labels.some((text) => text.includes('面积') && !text.includes('区间'))).toBe(false)
  })

  it('保留门窗开口,且类型恒为门/窗两值', async () => {
    getTemplate.mockResolvedValue(TEMPLATE_WITH_AREAS)
    updateTemplate.mockResolvedValue(undefined)

    const wrapper = await mountEdit()
    const openingValues = wrapper
      .findAll('select')
      .flatMap((select) => select.findAll('option').map((option) => (option.element as HTMLOptionElement).value))

    expect(openingValues).toContain('door')
    expect(openingValues).toContain('window')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const body = updateTemplate.mock.calls[0]![1] as {
      layout: { spaces: { openings?: { type: string; w: number; h: number }[] }[] }
    }
    expect(body.layout.spaces[0]!.openings).toEqual([{ type: 'door', w: 0.8, h: 2.1 }])
  })

  it('空间可以增删;删光之后提交被本地拦住', async () => {
    getTemplate.mockResolvedValue(TEMPLATE_WITH_AREAS)

    const wrapper = await mountEdit()
    await buttonWith(wrapper, '移除空间')!.trigger('click')
    await flushPromises()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateTemplate).not.toHaveBeenCalled()
    expect(wrapper.find('.errors').text()).toContain('至少需要一个空间')
  })

  it('长宽高不是正数时本地先拦', async () => {
    getTemplate.mockResolvedValue(TEMPLATE_WITH_AREAS)

    const wrapper = await mountEdit()
    // 按标签定位,别按 value 抓 —— 别处的数字输入框会先被匹配到
    const lengthInput = wrapper
      .findAll('label')
      .find((label) => label.text().includes('长(米)'))!
      .find('input')
    await lengthInput.setValue('0')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateTemplate).not.toHaveBeenCalled()
    expect(wrapper.find('.errors').text()).toContain('正数')
  })

  it('只读角色看不到保存按钮', async () => {
    getTemplate.mockResolvedValue(TEMPLATE_WITH_AREAS)

    const wrapper = await mountEdit('editor')

    expect(buttonWith(wrapper, '保存')).toBeUndefined()
    expect(wrapper.text()).toContain('没有修改权限')
  })
})
