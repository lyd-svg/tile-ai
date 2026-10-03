/**
 * F-0.8 数据字典:契约一致性 + 行为。
 *
 * 四条特别值得钉的:
 *   - 切页签是**服务端过滤**(`?type=`),不是拿到结果再前端筛;
 *   - 管理端**看得见停用项**(与 C 端只回 enabled 不同);
 *   - 删除被引用的项时,**服务端那句 30002 要原样透出** —— 吞成"删除失败",
 *     用户就不知道到底是谁在引用;
 *   - `type` 在编辑时**只读**(把已被引用的键挪到另一个命名空间很容易出事)。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { flushPromises, mount } from '@vue/test-utils'
import { load } from 'js-yaml'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { normalize } from './helpers/antd'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError, ErrorCode } from '../src/api/errors'
import AdminDictsView from '../src/views/admin/AdminDictsView.vue'
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

const listAdminDicts = vi.fn()
const createDict = vi.fn()
const updateDict = vi.fn()
const deleteDict = vi.fn()

vi.mock('@/api/admin/dicts', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/dicts')>('../src/api/admin/dicts')
  return {
    ...actual,
    listAdminDicts: (...args: unknown[]) => listAdminDicts(...args),
    createDict: (...args: unknown[]) => createDict(...args),
    updateDict: (...args: unknown[]) => updateDict(...args),
    deleteDict: (...args: unknown[]) => deleteDict(...args),
  }
})

async function mountAs(role: Role) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  auth.user = { id: 1, username: 'tester', role } as AdminUserView

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/dicts', name: 'admin-dicts', component: AdminDictsView }],
  })
  await router.push('/admin/dicts')
  await router.isReady()

  const wrapper = mount(AdminDictsView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

/** 按文案找按钮 —— **归一化空白**后再比(antd 会给两个汉字的按钮插空格)。 */
function buttonWith(wrapper: Awaited<ReturnType<typeof mountAs>>, text: string) {
  const target = normalize(text)
  return wrapper.findAll('button').find((button) => normalize(button.text()).includes(target))
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
})

describe('契约一致性', () => {
  it('GET /dicts 只有 type 一个 query 参数', () => {
    expect(queryParamNames(adminContract().paths['/dicts'].get)).toEqual(['type'])
  })

  it('新增请求体必填 type/key/label', () => {
    const schema = adminContract().components.schemas.DictUpsert

    expect(schema.required).toEqual(['type', 'key', 'label'])
    expect(Object.keys(schema.properties ?? {}).sort()).toEqual(['key', 'label', 'sort', 'status', 'type'])
  })

  it('编辑与删除都在 /dicts/{id} 上', () => {
    const paths = adminContract().paths

    expect(paths['/dicts/{id}'].put).toBeTruthy()
    expect(paths['/dicts/{id}'].delete).toBeTruthy()
  })
})

describe('行为', () => {
  it('默认拉空间字典;**切到风格页签是调服务端过滤,不是前端筛**', async () => {
    listAdminDicts.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')
    expect(listAdminDicts).toHaveBeenCalledWith('space')

    await buttonWith(wrapper, '风格')!.trigger('click')
    await flushPromises()

    expect(listAdminDicts).toHaveBeenCalledWith('style')
  })

  it('**停用项要看得见**(管理端与 C 端不同)', async () => {
    listAdminDicts.mockResolvedValue([
      { id: 1, type: 'space', key: 'living_room', label: '客厅', status: 'enabled' },
      { id: 2, type: 'space', key: 'balcony', label: '阳台', status: 'disabled' },
    ])

    const wrapper = await mountAs('super_admin')

    expect(wrapper.text()).toContain('阳台')
    expect(wrapper.findAll('.tag--off').length).toBe(1)
  })

  it('sort 缺失显示「—」,不是 0', async () => {
    listAdminDicts.mockResolvedValue([
      { id: 1, type: 'space', key: 'living_room', label: '客厅' },
    ])

    const wrapper = await mountAs('super_admin')

    expect(wrapper.find('tbody tr').text()).toContain('—')
  })

  it('新增时提交 trim 后的 key / label', async () => {
    listAdminDicts.mockResolvedValue([])
    createDict.mockResolvedValue({ id: 9 })

    const wrapper = await mountAs('super_admin')
    // ⚠️ antd `Select` 内部会渲染 `<input class="ant-select-selection-search-input">` ——
    //    它会**混进 `findAll('input')` 的下标里**(§72.6)。只取本页自己的输入框。
    const inputs = wrapper.findAll("form input:not([class*='ant-'])")
    await inputs[0].setValue('  study  ')
    await inputs[1].setValue('  书房  ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createDict).toHaveBeenCalledWith(
      expect.objectContaining({ key: 'study', label: '书房', type: 'space', status: 'enabled' }),
    )
  })

  it('编辑走 PUT 且带 id;**类别不可改**(界面守卫)', async () => {
    listAdminDicts.mockResolvedValue([
      { id: 7, type: 'space', key: 'living_room', label: '客厅', sort: 1, status: 'enabled' },
    ])
    updateDict.mockResolvedValue(undefined)

    const wrapper = await mountAs('super_admin')
    await buttonWith(wrapper, '编辑')!.trigger('click')
    await flushPromises()

    // ⚠️ antd `Select` **不是 `<select>`**(§72.6)—— 没有 `disabled` 属性可读,
    //    禁用渲染成 `.ant-select-disabled` 这个类。断言强度不变。
    expect(wrapper.find('.type-select').classes()).toContain('ant-select-disabled')

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(updateDict.mock.calls[0]![0]).toBe(7)
  })

  it('删除要二次确认;取消则**不发请求**', async () => {
    listAdminDicts.mockResolvedValue([
      { id: 7, type: 'space', key: 'living_room', label: '客厅', status: 'enabled' },
    ])
    appConfirm.mockResolvedValue(false)

    const wrapper = await mountAs('super_admin')
    await buttonWith(wrapper, '删除')!.trigger('click')
    await flushPromises()

    expect(deleteDict).not.toHaveBeenCalled()
  })

  it('**删除被引用的项:服务端那句 30002 原样透出**,不吞成"删除失败"', async () => {
    listAdminDicts.mockResolvedValue([
      { id: 7, type: 'space', key: 'living_room', label: '客厅', status: 'enabled' },
    ])
    deleteDict.mockRejectedValue(
      new BusinessError({ code: ErrorCode.STATE_NOT_ALLOWED, message: '该字典项已被产品引用,不能删除' }),
    )

    const wrapper = await mountAs('super_admin')
    await buttonWith(wrapper, '删除')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('该字典项已被产品引用,不能删除')
  })

  it('空态按当前页签给文案', async () => {
    listAdminDicts.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')

    expect(wrapper.text()).toContain('暂无空间字典项')
  })

  it('只读角色看得到列表,但没有任何写入口', async () => {
    listAdminDicts.mockResolvedValue([
      { id: 7, type: 'space', key: 'living_room', label: '客厅', status: 'enabled' },
    ])

    const wrapper = await mountAs('editor')

    expect(wrapper.text()).toContain('客厅')
    expect(buttonWith(wrapper, '编辑')).toBeUndefined()
    expect(buttonWith(wrapper, '删除')).toBeUndefined()
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('整页文本不出现 undefined / NaN', async () => {
    listAdminDicts.mockResolvedValue([{ id: 1, type: 'space', key: 'living_room', label: '客厅' }])

    const wrapper = await mountAs('super_admin')
    const text = wrapper.text()

    expect(text).not.toContain('undefined')
    expect(text).not.toContain('NaN')
  })
})
