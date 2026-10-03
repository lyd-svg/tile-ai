/**
 * F-0.4 批量导入:契约一致性 + 上传参数 + 轮询 + 错误行 + 导出。
 *
 * 四条特别值得钉的:
 *   - **上传是 multipart**:请求体必须是 `FormData`,而且**不能设 `Content-Type`**
 *     (设了就没 boundary,服务端解析不了);
 *   - **轮询要停**:到终态就不再排下一次,**卸载也要停**;
 *   - **错误行的行号要用好**:它是"改完这一行再传"的定位键,`row=0` 是批次级;
 *   - **`errorReportUrl` 后端恒为空** —— 所以导出只能前端拿 `errors[]` 生成,
 *     页面里**不该出现后端下载链接**(那会是个死链)。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { flushPromises, mount } from '@vue/test-utils'
import { load } from 'js-yaml'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { normalize } from './helpers/antd'
import { createMemoryHistory, createRouter } from 'vue-router'

import { BusinessError, ErrorCode } from '../src/api/errors'
import AdminProductImportView from '../src/views/admin/AdminProductImportView.vue'
import { useAdminAuthStore } from '../src/stores/adminAuth'
import type { AdminUserView, Role } from '../src/api/types'

const ADMIN_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-admin.yaml')

interface Contract {
  paths: Record<string, Record<string, {
    requestBody?: { content: Record<string, { schema: { $ref?: string; required?: string[] } }> }
  }>>
  components: {
    schemas: Record<string, {
      required?: string[]
      properties?: Record<string, { enum?: string[]; format?: string; type?: string }>
    }>
  }
}

function adminContract(): Contract {
  return load(readFileSync(ADMIN_CONTRACT, 'utf8')) as Contract
}

const acceptImport = vi.fn()
const getImportStatus = vi.fn()

vi.mock('@/api/admin/productImport', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/productImport')>(
    '../src/api/admin/productImport',
  )
  return {
    ...actual,
    acceptImport: (...args: unknown[]) => acceptImport(...args),
    getImportStatus: (...args: unknown[]) => getImportStatus(...args),
  }
})

async function mountAs(role: Role) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  auth.user = { id: 1, username: 'tester', role } as AdminUserView

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/products/import', name: 'admin-products-import', component: AdminProductImportView }],
  })
  await router.push('/admin/products/import')
  await router.isReady()

  const wrapper = mount(AdminProductImportView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

/** 按文案找按钮 —— **归一化空白**后再比(antd 会给两个汉字的按钮插空格)。 */
function buttonWith(wrapper: Awaited<ReturnType<typeof mountAs>>, text: string) {
  const target = normalize(text)
  return wrapper.findAll('button').find((button) => normalize(button.text()).includes(target))
}

/** 选一个文件并开始导入。 */
async function startImport(wrapper: Awaited<ReturnType<typeof mountAs>>) {
  const input = wrapper.find('input[type="file"]')
  const file = new File(['sku,name'], 'products.csv', { type: 'text/csv' })
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await buttonWith(wrapper, '开始导入')!.trigger('click')
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('契约一致性', () => {
  it('POST /products/import 只有 multipart,必填 file 与 mode', () => {
    const content = adminContract().paths['/products/import'].post?.requestBody?.content ?? {}

    expect(Object.keys(content)).toEqual(['multipart/form-data'])
    const schema = content['multipart/form-data']!.schema
    expect(schema.required).toEqual(['file', 'mode'])
    expect(schema.properties?.mode?.enum).toEqual(['upsert', 'insert_only'])
    expect(schema.properties?.file?.format).toBe('binary')
  })

  it('ImportStatus 的必填与四态', () => {
    const schema = adminContract().components.schemas.ImportStatus

    expect(schema.required).toEqual(['batchId', 'status'])
    expect(schema.properties?.status?.enum).toEqual(['pending', 'running', 'success', 'failed'])
  })

  it('模板接口同时提供 xlsx 与 csv 两种载体', () => {
    const responses = (adminContract().paths['/products/import/template'].get as { responses?: Record<string, { content?: Record<string, unknown> }> }).responses ?? {}
    const content = responses['200']?.content ?? {}

    expect(Object.keys(content).sort()).toEqual([
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'text/csv',
    ])
  })
})

describe('上传参数', () => {
  it('acceptImport 组装的 FormData 里有 file 与 mode(不设 Content-Type)', async () => {
    const actual = await vi.importActual<typeof import('../src/api/admin/productImport')>(
      '../src/api/admin/productImport',
    )
    const spy = vi.fn(async () =>
      new Response(JSON.stringify({ code: 0, message: 'ok', traceId: 't', data: { batchId: 'b1' } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', spy)

    const file = new File(['a,b'], 'p.csv', { type: 'text/csv' })
    await actual.acceptImport(file, 'insert_only')

    const init = spy.mock.calls[0]![1] as RequestInit
    const form = init.body as FormData
    expect(form.get('mode')).toBe('insert_only')
    expect(form.get('file')).toBe(file)
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined()
    vi.unstubAllGlobals()
  })
})

describe('行为', () => {
  it('提交后按批次号轮询,到终态就**不再排下一次**', async () => {
    acceptImport.mockResolvedValue({ batchId: 'b1' })
    getImportStatus
      .mockResolvedValueOnce({ batchId: 'b1', status: 'running', total: 10, successCount: 2, failedCount: 1 })
      .mockResolvedValueOnce({ batchId: 'b1', status: 'success', total: 10, successCount: 9, failedCount: 1, errors: [] })

    const wrapper = await mountAs('super_admin')
    vi.useFakeTimers()
    await startImport(wrapper)

    expect(acceptImport).toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1500)
    expect(getImportStatus).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1500)
    expect(getImportStatus).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已完成')

    // 终态之后一个请求都不该再发
    await vi.advanceTimersByTimeAsync(10_000)
    expect(getImportStatus).toHaveBeenCalledTimes(2)
  })

  it('离开页面就不再轮询', async () => {
    acceptImport.mockResolvedValue({ batchId: 'b1' })
    getImportStatus.mockResolvedValue({ batchId: 'b1', status: 'running' })

    const wrapper = await mountAs('super_admin')
    vi.useFakeTimers()
    await startImport(wrapper)
    await vi.advanceTimersByTimeAsync(1500)
    expect(getImportStatus).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(getImportStatus).toHaveBeenCalledTimes(1)
  })

  it('批次不存在(30001)是**终态**,不再重试', async () => {
    acceptImport.mockResolvedValue({ batchId: 'b1' })
    getImportStatus.mockRejectedValue(
      new BusinessError({ code: ErrorCode.NOT_FOUND, message: '批次不存在' }),
    )

    const wrapper = await mountAs('super_admin')
    vi.useFakeTimers()
    await startImport(wrapper)
    await vi.advanceTimersByTimeAsync(1500)

    expect(wrapper.find('[role="alert"]').text()).toContain('批次不存在')
    await vi.advanceTimersByTimeAsync(10_000)
    expect(getImportStatus).toHaveBeenCalledTimes(1)
  })

  it('错误行原样列出,行号准确;`row=0` 是批次级', async () => {
    acceptImport.mockResolvedValue({ batchId: 'b1' })
    getImportStatus.mockResolvedValue({
      batchId: 'b1',
      status: 'failed',
      total: 3,
      successCount: 1,
      failedCount: 2,
      errors: [
        { row: 0, reason: '表头缺少必填列:sku' },
        { row: 3, reason: '单价不是合法数字:abc' },
      ],
      errorReportUrl: null,
    })

    const wrapper = await mountAs('super_admin')
    vi.useFakeTimers()
    await startImport(wrapper)
    await vi.advanceTimersByTimeAsync(1500)

    const text = wrapper.text()
    expect(text).toContain('批次级')
    expect(text).toContain('3')
    expect(text).toContain('单价不是合法数字:abc')
    // 后端永远给 null —— 不该画一个下载链接
    expect(wrapper.findAll('a').some((link) => link.text().includes('错误报告'))).toBe(false)
  })

  it('没有导入权限时看不到上传按钮,但模板链接仍在', async () => {
    const wrapper = await mountAs('editor')

    expect(buttonWith(wrapper, '开始导入')).toBeUndefined()
    expect(wrapper.findAll('a').some((link) => link.text().includes('模板'))).toBe(true)
  })

  it('整页文本不出现 undefined / NaN', async () => {
    acceptImport.mockResolvedValue({ batchId: 'b1' })
    getImportStatus.mockResolvedValue({ batchId: 'b1', status: 'running' })

    const wrapper = await mountAs('super_admin')
    vi.useFakeTimers()
    await startImport(wrapper)
    await vi.advanceTimersByTimeAsync(1500)

    const text = wrapper.text()
    expect(text).not.toContain('undefined')
    expect(text).not.toContain('NaN')
  })
})

describe('errorsToCsv', () => {
  it('表头 + 行号 + 原因,**顺序不变**', async () => {
    const { errorsToCsv } = await vi.importActual<typeof import('../src/api/admin/productImport')>(
      '../src/api/admin/productImport',
    )

    const csv = errorsToCsv([
      { row: 3, reason: '单价不是合法数字:abc' },
      { row: 5, reason: '编号为空' },
    ])

    expect(csv.split('\n')).toEqual(['行号,原因', '3,单价不是合法数字:abc', '5,编号为空'])
  })

  it('含逗号 / 引号 / 换行的原因会按 CSV 规则转义', async () => {
    const { errorsToCsv } = await vi.importActual<typeof import('../src/api/admin/productImport')>(
      '../src/api/admin/productImport',
    )

    const csv = errorsToCsv([{ row: 7, reason: '包含,逗号与"引号"' }])

    expect(csv.split('\n')[1]).toBe('7,"包含,逗号与""引号"""')
  })
})
