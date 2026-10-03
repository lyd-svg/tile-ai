/**
 * F-0.2 账号与权限:契约一致性 + 权限门控 + **两条防呆的界面侧**。
 *
 * 这里最要紧的一条是「**编辑时密码留空 → 请求体里不带 password 键**」:
 * 传空串不是"不改",会走到服务端的密码校验上;写成编码一次就等于把密码静默清空。
 * 服务端已有一条对称的集成测试(改密码留空后旧密码仍能登录),两头都要钉住。
 *
 * ⚠️ 界面上隐藏/禁用按钮**不是安全边界** —— 真正的拦截在服务端
 * (403/10004 与 30002)。这里断言的是"别给出点了必然被拒的入口"。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { flushPromises, mount } from '@vue/test-utils'
import { load } from 'js-yaml'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { normalize } from './helpers/antd'

import { BusinessError, ErrorCode } from '../src/api/errors'
import type { AdminUserView, Role } from '../src/api/types'
import AdminAccountsView from '../src/views/admin/AdminAccountsView.vue'
import { useAdminAuthStore } from '../src/stores/adminAuth'

const ADMIN_CONTRACT = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../openapi-admin.yaml',
)
const COMMON_CONTRACT = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../openapi-common.yaml',
)

interface Parameter {
  name?: string
  in?: string
  $ref?: string
}

interface Contract {
  paths: Record<string, Record<string, { parameters?: Parameter[] }>>
  components: {
    schemas: Record<string, { required?: string[]; properties?: Record<string, unknown> }>
    parameters: Record<string, { name: string; in: string }>
  }
}

function loadContract(path: string): Contract {
  return load(readFileSync(path, 'utf8')) as Contract
}

function adminContract(): Contract {
  return loadContract(ADMIN_CONTRACT)
}

/** ⚠️ 必须解析 `$ref`,否则 `page`/`size` 会被静默漏掉(见 logs spec 的同名说明)。 */
function queryParamNames(operation: { parameters?: Parameter[] }): string[] {
  const common = loadContract(COMMON_CONTRACT)
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

const listAccounts = vi.fn()
const createAccount = vi.fn()
const updateAccount = vi.fn()
const deleteAccount = vi.fn()

vi.mock('@/api/admin/accounts', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/accounts')>(
    '../src/api/admin/accounts',
  )
  return {
    ...actual,
    listAccounts: (...args: unknown[]) => listAccounts(...args),
    createAccount: (...args: unknown[]) => createAccount(...args),
    updateAccount: (...args: unknown[]) => updateAccount(...args),
    deleteAccount: (...args: unknown[]) => deleteAccount(...args),
  }
})

// ----------------------------------------------------------------------
// 契约一致性
// ----------------------------------------------------------------------

describe('查询参数与请求体与契约逐字段一致', () => {
  it('列表的 query 参数只有 page / size(这个接口没有筛选)', () => {
    const params = queryParamNames(adminContract().paths['/accounts'].get)

    expect(params).toEqual(['page', 'size'])
  })

  it('前端只发 page / size,不多发', async () => {
    const actual = await vi.importActual<typeof import('../src/api/admin/accounts')>(
      '../src/api/admin/accounts',
    )

    const sent: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        sent.push(String(input))
        return new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            traceId: 't',
            data: { list: [], total: 0, page: 1, size: 10 },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        )
      }),
    )

    await actual.listAccounts({ page: 2, size: 10 })

    const query = new URL(sent[0], 'http://x').searchParams
    expect([...query.keys()].sort()).toEqual(['page', 'size'])
    vi.unstubAllGlobals()
  })

  it('AdminUserUpsert 的必填字段与契约一致', () => {
    const required = adminContract().components.schemas.AdminUserUpsert.required ?? []

    expect(new Set(required)).toEqual(new Set(['username', 'role']))
  })

  it('AdminUserUpsert 的字段集与契约一致(不多不少)', () => {
    const properties = Object.keys(
      adminContract().components.schemas.AdminUserUpsert.properties ?? {},
    ).sort()

    expect(properties).toEqual(['password', 'role', 'status', 'username'])
    // `id` **不在**请求体里 —— 它在路径里
    expect(properties).not.toContain('id')
  })
})

// ----------------------------------------------------------------------
// 界面行为
// ----------------------------------------------------------------------

const SELF: AdminUserView = {
  id: 1,
  username: 'admin',
  role: 'super_admin',
  status: 'enabled',
  createdAt: '2026-09-29T19:19:48',
}
const OTHER: AdminUserView = {
  id: 2,
  username: 'zhangsan',
  role: 'operator',
  status: 'enabled',
  createdAt: '2026-10-03T10:00:00',
}

async function mountAs(
  role: Role,
  options: { rows?: AdminUserView[]; total?: number; reject?: Error } = {},
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  // id=1 是「我自己」—— 页面据此禁用自己那一行的删除按钮
  auth.user = { id: 1, username: 'admin', role }

  if (options.reject) {
    listAccounts.mockRejectedValue(options.reject)
  } else {
    const rows = options.rows ?? [SELF, OTHER]
    listAccounts.mockResolvedValue({
      list: rows,
      total: options.total ?? rows.length,
      page: 1,
      size: 10,
    })
  }

  const wrapper = mount(AdminAccountsView, { global: { plugins: [pinia] } })
  await flushPromises()
  return { wrapper, auth }
}

function rowButtons(wrapper: ReturnType<typeof mount>, rowIndex: number) {
  return wrapper.findAll('tbody tr')[rowIndex].findAll('button')
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
  listAccounts.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
  appConfirm.mockResolvedValue(true)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('列表渲染', () => {
  it('画出用户名、角色中文名与状态', async () => {
    const { wrapper } = await mountAs('super_admin')

    const text = wrapper.text()
    expect(text).toContain('admin')
    expect(text).toContain('超级管理员')
    expect(text).toContain('运营')
    expect(text).toContain('启用中')
  })

  it('把自己那一行标出来', async () => {
    const { wrapper } = await mountAs('super_admin')

    expect(wrapper.text()).toContain('你自己')
  })
})

describe('权限门控(架构 §4.4)', () => {
  it('super_admin 能看到新增/编辑/删除', async () => {
    const { wrapper } = await mountAs('super_admin')

    const labels = wrapper.findAll('button').map((button) => normalize(button.text()))
    expect(labels).toContain('新增账号')
    expect(labels).toContain('编辑')
    expect(labels).toContain('删除')
  })

  it('operator / editor 一个写入口都没有', async () => {
    for (const role of ['operator', 'editor'] as const) {
      const { wrapper } = await mountAs(role)

      const labels = wrapper.findAll('button').map((button) => normalize(button.text()))
      expect(labels).not.toContain('新增账号')
      expect(labels).not.toContain('编辑')
      expect(labels).not.toContain('删除')
    }
  })
})

describe('防呆的界面侧', () => {
  it('自己那一行的删除按钮是禁用的(服务端仍会再拦一次)', async () => {
    const { wrapper } = await mountAs('super_admin')

    const selfDelete = rowButtons(wrapper, 0).find((button) => normalize(button.text()) === normalize('删除'))
    expect(selfDelete?.attributes('disabled')).toBeDefined()

    const otherDelete = rowButtons(wrapper, 1).find((button) => normalize(button.text()) === normalize('删除'))
    expect(otherDelete?.attributes('disabled')).toBeUndefined()
  })

  it('服务端拒绝(30002)的原文案透出到界面上', async () => {
    const { wrapper } = await mountAs('super_admin')

    // 点第一行(自己)的「编辑」→ 打开表单 → 保存
    await rowButtons(wrapper, 0).find((button) => normalize(button.text()) === normalize('编辑'))?.trigger('click')
    updateAccount.mockRejectedValue(
      new BusinessError({ code: ErrorCode.STATUS_NOT_ALLOWED, message: '不能停用自己' }),
    )
    await wrapper.find('form.panel').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('不能停用自己')
  })
})

describe('密码语义(最容易出事故的一处)', () => {
  it('编辑时密码留空 → 请求体里**没有** password 键', async () => {
    const { wrapper } = await mountAs('super_admin')
    updateAccount.mockResolvedValue(undefined)

    await rowButtons(wrapper, 1).find((button) => normalize(button.text()) === normalize('编辑'))?.trigger('click')
    // 编辑态下密码框本来就是空的
    await wrapper.find('form.panel').trigger('submit')
    await flushPromises()

    expect(updateAccount).toHaveBeenCalledTimes(1)
    const [id, body] = updateAccount.mock.calls[0] as [number, Record<string, unknown>]
    expect(id).toBe(2)
    expect(Object.keys(body)).not.toContain('password')
    expect(body.username).toBe('zhangsan')
    expect(body.role).toBe('operator')
  })

  it('编辑时填了密码 → 才带上 password', async () => {
    const { wrapper } = await mountAs('super_admin')
    updateAccount.mockResolvedValue(undefined)

    await rowButtons(wrapper, 1).find((button) => normalize(button.text()) === normalize('编辑'))?.trigger('click')
    await wrapper.find('form.panel input[type="password"]').setValue('NewPass123')
    await wrapper.find('form.panel').trigger('submit')
    await flushPromises()

    const [, body] = updateAccount.mock.calls[0] as [number, Record<string, unknown>]
    expect(body.password).toBe('NewPass123')
  })

  it('新增时不填密码 → 不发请求,当场提示', async () => {
    const { wrapper } = await mountAs('super_admin')

    await wrapper
      .findAll('button')
      .find((button) => normalize(button.text()) === normalize('新增账号'))
      ?.trigger('click')
    await wrapper.find('form.panel input[type="text"]').setValue('lisi')
    await wrapper.find('form.panel').trigger('submit')
    await flushPromises()

    expect(createAccount).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toContain('密码')
  })
})

describe('错误提示', () => {
  it('加载失败时给出可读文案', async () => {
    const { wrapper } = await mountAs('super_admin', {
      reject: new BusinessError({ code: ErrorCode.FORBIDDEN }),
    })

    expect(wrapper.find('[role="alert"]').text()).toBeTruthy()
  })
})
