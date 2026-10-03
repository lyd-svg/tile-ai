/**
 * F-0.2 操作日志:契约一致性 + 渲染 + **已删除账号兜底**。
 *
 * 契约一致性这部分是 F-0.1 承诺的兑现 —— 查询参数**直接对着
 * `openapi-admin.yaml` 断言**,而不是靠人肉核对:契约改了、前端没跟上,这里会红。
 *
 * 这个页面是只读的,所以没有权限门控相关的断言 —— 入口由菜单按 `log:read` 过滤,
 * 页面里一个写按钮都没有。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { flushPromises, mount } from '@vue/test-utils'
import { load } from 'js-yaml'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { OperationLog } from '../src/api/types'
import AdminLogsView from '../src/views/admin/AdminLogsView.vue'

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

interface Operation {
  parameters?: Parameter[]
}

interface Contract {
  paths: Record<string, Record<string, Operation>>
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

/**
 * 取某个操作的 query 参数名。
 *
 * ⚠️ **必须解析 `$ref`**:`page` / `size` 是
 * `$ref: './openapi-common.yaml#/components/parameters/PageParam'`,
 * 直接读 `parameter.name` 会**静默漏掉**它们 —— 断言就永远少两条,而且看起来还通过了。
 */
function queryParamNames(operation: Operation): string[] {
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

const listLogs = vi.fn()

vi.mock('@/api/admin/logs', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/logs')>(
    '../src/api/admin/logs',
  )
  return {
    ...actual,
    listLogs: (...args: unknown[]) => listLogs(...args),
  }
})

// ----------------------------------------------------------------------
// 契约一致性
// ----------------------------------------------------------------------

describe('查询参数与契约逐字段一致', () => {
  it('契约的 query 参数就是这六个(含 $ref 引入的 page/size)', () => {
    const params = queryParamNames(adminContract().paths['/logs'].get)

    expect(params).toEqual(['action', 'adminId', 'from', 'page', 'size', 'to'])
  })

  it('前端确实只发这六个,不多发', async () => {
    const actual = await vi.importActual<typeof import('../src/api/admin/logs')>(
      '../src/api/admin/logs',
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

    await actual.listLogs({
      adminId: 7,
      action: 'product.delete',
      from: '2026-10-01T00:00:00',
      to: '2026-10-03T23:59:59',
      page: 1,
      size: 10,
    })

    const query = new URL(sent[0], 'http://x').searchParams
    expect([...query.keys()].sort()).toEqual(['action', 'adminId', 'from', 'page', 'size', 'to'])
    vi.unstubAllGlobals()
  })

  it('OperationLog 的必填字段与契约一致(只有三个)', () => {
    const contract = loadContract(COMMON_CONTRACT)
    const required = contract.components.schemas.OperationLogView.required ?? []

    expect(new Set(required)).toEqual(new Set(['id', 'action', 'createdAt']))
  })

  it('规则有 adminUsername 且可空 —— 已删账号的兜底就靠它', () => {
    const contract = loadContract(COMMON_CONTRACT)
    const schema = contract.components.schemas.OperationLogView.properties?.adminUsername as
      | { type?: string; nullable?: boolean }
      | undefined

    // 若哪天这个字段被删掉/改成不可空,这里会红:页面上的"已删除账号 #N"就没有依据了
    expect(schema?.type).toBe('string')
    expect(schema?.nullable).toBe(true)
  })
})

// ----------------------------------------------------------------------
// 渲染
// ----------------------------------------------------------------------

/** 正常一条:操作人还在,详情是个对象。 */
const NORMAL: OperationLog = {
  id: 101,
  adminId: 257,
  adminUsername: 'admin',
  action: 'furnish.upsert',
  targetType: 'furnish_style',
  targetId: '',
  detail: { method: 'PUT', path: '/api/v1/admin/furnish' },
  ip: '127.0.0.1',
  createdAt: '2026-10-03T12:34:56',
}

/** 操作人**已被物理删除**:adminUsername 为 null(后端按 id 查不到人)。 */
const ORPHAN: OperationLog = {
  id: 100,
  adminId: 755,
  adminUsername: null,
  action: 'store.update',
  targetType: 'store',
  detail: null,
  ip: '127.0.0.1',
  createdAt: '2026-10-02T09:00:00',
}

async function mountWith(rows: OperationLog[], total = rows.length) {
  listLogs.mockResolvedValue({ list: rows, total, page: 1, size: 10 })
  const wrapper = mount(AdminLogsView)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  listLogs.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('列表渲染', () => {
  it('画出时间、操作人、动作、目标与详情', async () => {
    const wrapper = await mountWith([NORMAL])

    const text = wrapper.text()
    expect(text).toContain('2026-10-03 12:34') // 时间做了 T → 空格 的本地化
    expect(text).toContain('admin')
    expect(text).toContain('furnish.upsert')
    expect(text).toContain('furnish_style')
    // detail 是**对象**,渲染成「方法 + 路径」;若后端退回成字符串会被序列化成转义文本
    expect(text).toContain('PUT /api/v1/admin/furnish')
  })

  it('操作人已删除时显示「已删除账号 #N」,不留空白', async () => {
    const wrapper = await mountWith([ORPHAN])

    expect(wrapper.text()).toContain('已删除账号 #755')
  })

  it('没有详情时画破折号,不画 [object Object]', async () => {
    const wrapper = await mountWith([ORPHAN])

    expect(wrapper.text()).not.toContain('[object Object]')
  })

  it('没有数据时给空态文案', async () => {
    const wrapper = await mountWith([], 0)

    expect(wrapper.text()).toContain('没有符合条件的日志')
  })

  it('总数与页码画在分页条上', async () => {
    const wrapper = await mountWith([NORMAL], 42)

    expect(wrapper.text()).toContain('共 42 条')
  })
})

describe('筛选', () => {
  it('日期补上边界时刻 —— 不补的话「查到今天」会漏掉今天', async () => {
    const wrapper = await mountWith([])

    const dateInputs = wrapper.findAll('input[type="date"]')
    await dateInputs[0].setValue('2026-10-01')
    await dateInputs[1].setValue('2026-10-03')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // 最后一次调用(挂载时那次是空条件)
    const lastCall = listLogs.mock.calls.at(-1)?.[0] as Record<string, unknown>
    expect(lastCall.from).toBe('2026-10-01T00:00:00')
    expect(lastCall.to).toBe('2026-10-03T23:59:59')
    expect(lastCall.page).toBe(1) // 换条件要回到第 1 页
  })

  it('不填的筛选项不往下传(交给 buildQuery 丢弃)', async () => {
    const wrapper = await mountWith([])

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const lastCall = listLogs.mock.calls.at(-1)?.[0] as Record<string, unknown>
    expect(lastCall.adminId).toBeUndefined()
    expect(lastCall.action).toBeUndefined()
    expect(lastCall.from).toBeUndefined()
    expect(lastCall.to).toBeUndefined()
  })
})
