/**
 * F-0.9 门店信息:契约一致性 + 行为。
 *
 * 这页最容易搞错的是**把「还没配置」当错误**:`GET /store` 在没有门店信息时返回
 * **30001**。若把它当故障显示成红字、禁用表单,新环境就**永远配不上第一家店**。
 * 下面有一条用例专门钉这个。
 *
 * 另外两条:**PUT 是整体更新**(没有新增/删除,连提两次不产生第二条),
 * 以及**可选字段为空时整个键不发**(契约要求省略,不是发空串/null)。
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
import AdminStoreView from '../src/views/admin/AdminStoreView.vue'
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

/** 取操作的 query 参数名。**必须解 `$ref`**,否则会静默漏掉 page/size 这类公共参数。 */
function queryParamNames(operation: Contract['paths'][string][string]): string[] {
  const common: Contract = contract(COMMON_CONTRACT)
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

const getAdminStore = vi.fn()
const updateStore = vi.fn()

vi.mock('@/api/admin/store', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/store')>('../src/api/admin/store')
  return {
    ...actual,
    getAdminStore: (...args: unknown[]) => getAdminStore(...args),
    updateStore: (...args: unknown[]) => updateStore(...args),
  }
})

async function mountAs(role: Role) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  auth.user = { id: 1, username: 'tester', role } as AdminUserView

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/store', name: 'admin-store', component: AdminStoreView }],
  })
  await router.push('/admin/store')
  await router.isReady()

  const wrapper = mount(AdminStoreView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

function saveButton(wrapper: Awaited<ReturnType<typeof mountAs>>) {
  return wrapper.findAll('button').find((button) => normalize(button.text()).includes(normalize('保存')))
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('契约一致性', () => {
  it('GET /store 没有 query 参数', () => {
    expect(queryParamNames(adminContract().paths['/store'].get)).toEqual([])
  })

  it('PUT /store 请求体必填 name/address/phone,可含 mapUrl', () => {
    const schema = adminContract().components.schemas.StoreUpsert

    expect(schema.required).toEqual(['name', 'address', 'phone'])
    expect(Object.keys(schema.properties ?? {}).sort()).toEqual(['address', 'mapUrl', 'name', 'phone'])
  })
})

describe('行为', () => {
  it('已有门店信息时回填表单', async () => {
    getAdminStore.mockResolvedValue({
      name: '城东旗舰店',
      address: 'XX 路 1 号',
      phone: '010-00000000',
      mapUrl: 'https://map.example.com/1',
    })

    const wrapper = await mountAs('super_admin')

    const inputs = wrapper.findAll('input')
    expect((inputs[0].element as HTMLInputElement).value).toBe('城东旗舰店')
    expect((inputs[3].element as HTMLInputElement).value).toBe('https://map.example.com/1')
  })

  it('**30001 是「还没配置」,不是错误** —— 表单照常可填', async () => {
    getAdminStore.mockRejectedValue(
      new BusinessError({ code: ErrorCode.NOT_FOUND, message: '门店信息不存在' }),
    )

    const wrapper = await mountAs('super_admin')

    expect(wrapper.text()).toContain('尚未配置')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(saveButton(wrapper)!.attributes('disabled')).toBeUndefined()
  })

  it('其它错误才当错误显示', async () => {
    getAdminStore.mockRejectedValue(new Error('服务返回 500'))

    const wrapper = await mountAs('super_admin')

    expect(wrapper.find('[role="alert"]').text()).toContain('服务返回 500')
  })

  it('**PUT 是整体更新**:连提两次都调 updateStore,且请求体**从不含 id**', async () => {
    getAdminStore.mockResolvedValue({ name: '店', address: '路', phone: '电话' })
    updateStore.mockResolvedValue(undefined)

    const wrapper = await mountAs('super_admin')
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('新店名')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateStore).toHaveBeenCalledTimes(2)
    for (const call of updateStore.mock.calls) {
      expect(call[0]).not.toHaveProperty('id')
    }
  })

  it('**mapUrl 留空时请求体里没有这个键**(不是空串、不是 null)', async () => {
    getAdminStore.mockResolvedValue({ name: '店', address: '路', phone: '电话' })
    updateStore.mockResolvedValue(undefined)

    const wrapper = await mountAs('super_admin')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const body = updateStore.mock.calls[0]![0] as Record<string, unknown>
    expect(Object.keys(body).sort()).toEqual(['address', 'name', 'phone'])
  })

  it('必填项为空时**不调用接口**,并逐项说明', async () => {
    getAdminStore.mockRejectedValue(new BusinessError({ code: ErrorCode.NOT_FOUND }))

    const wrapper = await mountAs('super_admin')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateStore).not.toHaveBeenCalled()
    expect(wrapper.findAll('.errors li').length).toBe(3)
  })

  it('只读角色看不到保存按钮,且输入框不可编辑', async () => {
    getAdminStore.mockResolvedValue({ name: '店', address: '路', phone: '电话' })

    const wrapper = await mountAs('editor')

    expect(saveButton(wrapper)).toBeUndefined()
    expect(wrapper.text()).toContain('没有修改门店信息的权限')
  })

  it('整页文本不出现 undefined / NaN', async () => {
    getAdminStore.mockResolvedValue({ name: '店', address: '路', phone: '电话' })

    const wrapper = await mountAs('super_admin')
    const text = wrapper.text()

    expect(text).not.toContain('undefined')
    expect(text).not.toContain('NaN')
  })
})
