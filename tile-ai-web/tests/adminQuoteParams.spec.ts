/**
 * F-0.8 报价参数:契约一致性 + 行为。
 *
 * 这页有三条特别值得钉住的东西,每一条都对应一个真实踩过的坑:
 *
 * 1. **四个键必须恒在**。服务端现在只返回两条(损耗率上下限)—— 按返回渲染的话,
 *    缺失的 `aux_unit_price` 会整行消失,而"报价算不出整单"恰恰就是因为没配它。
 * 2. **缺失 ≠ 0**。没配的键显示"未配置",输入框留空;**绝不能**默认成 0
 *    (0 元的辅料会静默算出一份错报价)。
 * 3. **量纲**。辅料单价是「元/㎡」、损耗率是**小数**(0.06 = 6%)—— 单位必须显示出来,
 *    因为填错量纲不会报错,只会算出离谱的报价。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { flushPromises, mount } from '@vue/test-utils'
import { load } from 'js-yaml'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import * as quoteParamsApi from '../src/api/admin/quoteParams'
import AdminQuoteParamsView from '../src/views/admin/AdminQuoteParamsView.vue'
import { useAdminAuthStore } from '../src/stores/adminAuth'
import type { AdminUserView, Role } from '../src/api/types'

const ADMIN_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-admin.yaml')

interface Operation {
  parameters?: { name?: string; in?: string; $ref?: string }[]
  requestBody?: { content: Record<string, { schema: { $ref?: string } }> }
}

interface Contract {
  paths: Record<string, Record<string, Operation>>
  components: { schemas: Record<string, { required?: string[]; properties?: Record<string, unknown> }> }
}

function adminContract(): Contract {
  return load(readFileSync(ADMIN_CONTRACT, 'utf8')) as Contract
}

const listQuoteParams = vi.fn()
const upsertQuoteParam = vi.fn()

vi.mock('@/api/admin/quoteParams', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/quoteParams')>(
    '../src/api/admin/quoteParams',
  )
  return {
    ...actual,
    listQuoteParams: (...args: unknown[]) => listQuoteParams(...args),
    upsertQuoteParam: (...args: unknown[]) => upsertQuoteParam(...args),
  }
})

async function mountAs(role: Role) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  auth.user = { id: 1, username: 'tester', role } as AdminUserView

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/quote-params', name: 'admin-quote-params', component: AdminQuoteParamsView }],
  })
  await router.push('/admin/quote-params')
  await router.isReady()

  const wrapper = mount(AdminQuoteParamsView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

/** 按 `paramKey` 找到那一行。 */
function rowOf(wrapper: Awaited<ReturnType<typeof mountAs>>, key: string) {
  const row = wrapper.findAll('tr').find((item) => item.text().includes(key))
  if (!row) throw new Error(`找不到参数行:${key}`)
  return row
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('契约一致性', () => {
  it('PUT /quote-params 的请求体:必填 paramKey/paramValue,键恰好四个', () => {
    const schema = adminContract().components.schemas.QuoteParamUpsert

    expect(schema.required).toEqual(['paramKey', 'paramValue'])
    expect(Object.keys(schema.properties ?? {}).sort()).toEqual(['paramKey', 'paramValue', 'remark'])
    expect((schema.properties?.paramKey as { enum?: string[] }).enum).toEqual([
      'aux_unit_price',
      'loss_rate',
      'loss_rate_min',
      'loss_rate_max',
    ])
  })

  it('**契约里没有 POST / DELETE** —— 参数是固定集合,不是可增删的数据', () => {
    const operation = adminContract().paths['/quote-params']

    expect(operation.post).toBeUndefined()
    expect(operation.delete).toBeUndefined()
  })

  it('**模块也不导出 create / delete** —— 从接口上就做不出"新增参数"', () => {
    const exported = Object.keys(quoteParamsApi)

    expect(exported.some((name) => name.startsWith('create'))).toBe(false)
    expect(exported.some((name) => name.startsWith('delete'))).toBe(false)
  })
})

describe('行为', () => {
  it('服务端**只返回两条**时,四个键仍然都在(含没配的 aux_unit_price)', async () => {
    listQuoteParams.mockResolvedValue([
      { id: 1, paramKey: 'loss_rate_min', paramValue: 0.05 },
      { id: 2, paramKey: 'loss_rate_max', paramValue: 0.08 },
    ])

    const wrapper = await mountAs('super_admin')

    for (const key of ['aux_unit_price', 'loss_rate', 'loss_rate_min', 'loss_rate_max']) {
      expect(rowOf(wrapper, key)).toBeTruthy()
    }
  })

  it('**缺失的键显示"未配置",不是 0**', async () => {
    listQuoteParams.mockResolvedValue([{ id: 1, paramKey: 'loss_rate', paramValue: 0.06 }])

    const wrapper = await mountAs('super_admin')
    const auxRow = rowOf(wrapper, 'aux_unit_price')

    expect(auxRow.text()).toContain('未配置')
    expect((auxRow.find('input').element as HTMLInputElement).value).toBe('')
  })

  it('辅料单价缺失时页顶给告警 —— 报价正是因为它算不出来', async () => {
    listQuoteParams.mockResolvedValue([{ id: 1, paramKey: 'loss_rate', paramValue: 0.06 }])

    const wrapper = await mountAs('super_admin')

    expect(wrapper.find('[role="alert"]').text()).toContain('辅料单价尚未配置')
  })

  it('**量纲要看得见**:辅料行有「元/㎡」,损耗率行说明「0.06 = 6%」', async () => {
    listQuoteParams.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')

    expect(rowOf(wrapper, 'aux_unit_price').text()).toContain('元/㎡')
    expect(rowOf(wrapper, 'loss_rate').text()).toContain('0.06 表示 6%')
  })

  it('**损耗率按小数录入与展示**:0.06 就显示 0.06,旁边提示 6%', async () => {
    listQuoteParams.mockResolvedValue([{ id: 1, paramKey: 'loss_rate', paramValue: 0.06 }])

    const wrapper = await mountAs('super_admin')
    const row = rowOf(wrapper, 'loss_rate')

    expect((row.find('input').element as HTMLInputElement).value).toBe('0.06')
    expect(row.text()).toContain('6%')
  })

  it('未配置的损耗率**不显示 0%**(宁可不显示,也不给一个假值)', async () => {
    listQuoteParams.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')

    expect(rowOf(wrapper, 'loss_rate').text()).not.toContain('0%')
  })

  it('保存发送的是**小数原值**(0.06),不是 6', async () => {
    listQuoteParams.mockResolvedValue([{ id: 1, paramKey: 'loss_rate', paramValue: 0.06 }])
    upsertQuoteParam.mockResolvedValue(undefined)

    const wrapper = await mountAs('super_admin')
    await rowOf(wrapper, 'loss_rate').find('button').trigger('click')
    await flushPromises()

    expect(upsertQuoteParam).toHaveBeenCalledWith({ paramKey: 'loss_rate', paramValue: 0.06 })
  })

  it('本地校验挡住百分数写法(填 6 会被服务端拒),拦在发请求之前', async () => {
    listQuoteParams.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')
    const row = rowOf(wrapper, 'loss_rate')
    await row.find('input').setValue('6')
    await row.find('button').trigger('click')
    await flushPromises()

    expect(upsertQuoteParam).not.toHaveBeenCalled()
    expect(wrapper.find('.errors').text()).toContain('0.06 表示 6%')
  })

  it('整页没有"新增/删除"入口', async () => {
    listQuoteParams.mockResolvedValue([])

    const wrapper = await mountAs('super_admin')
    const labels = wrapper.findAll('button').map((button) => button.text())

    expect(labels.some((text) => text.includes('删除'))).toBe(false)
    expect(labels.some((text) => text.includes('新增'))).toBe(false)
  })

  it('没有写权限的账号看不到保存按钮', async () => {
    listQuoteParams.mockResolvedValue([])

    const wrapper = await mountAs('operator')

    expect(wrapper.findAll('button').some((button) => button.text().includes('保存'))).toBe(false)
  })
})
