/**
 * F-0.3 产品库管理:契约一致性 + 权限门控。
 *
 * 契约一致性这部分是 **F-0.1 承诺的兑现** —— 该模块的请求参数与请求体字段
 * **直接对着 `openapi-admin.yaml` 断言**,而不是靠人肉核对:
 * 契约改了、前端没跟上,这里会红。
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
import type { Sku } from '../src/api/admin/products'
import AdminProductsView from '../src/views/admin/AdminProductsView.vue'
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

interface Operation {
  parameters?: Parameter[]
  requestBody?: {
    content: Record<string, { schema: { $ref?: string } }>
  }
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
 * 直接读 `parameter.name` 会**静默漏掉**它们 —— 那样断言就永远少两条,
 * 而且看起来还通过了。这是个真踩过的坑:第一版就漏了,断言写得比契约松。
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

// ----------------------------------------------------------------------
// 契约一致性:请求参数与请求体
// ----------------------------------------------------------------------

/**
 * 确认框 —— **这一层刻意 mock 掉**。
 *
 * 页面只负责两件事:**调它**、**尊重它的答案**;弹窗本身长什么样、点了会怎样,
 * 由 `tests/appConfirm.spec.ts` **单独驱动真的 `Modal`** 去覆盖(4 条)。
 * ⇒ 这里 mock 掉它,**页面测试的断言一条没删**(从"stub `window.confirm`"
 *   换成"stub `appConfirm`",检的东西完全一样)。
 * 见 `openapi-verification.md` §70.7。
 */
const appConfirm = vi.fn()
vi.mock('@/components/base/AppConfirm', async () => {
  const actual = await vi.importActual<typeof import('../src/components/base/AppConfirm')>(
    '../src/components/base/AppConfirm',
  )
  return { ...actual, appConfirm: (...args: unknown[]) => appConfirm(...args) }
})

const listProducts = vi.fn()
const createProduct = vi.fn()
const getProduct = vi.fn()
const setProductStatus = vi.fn()
const deleteProduct = vi.fn()

vi.mock('@/api/admin/products', async () => {
  const actual = await vi.importActual<typeof import('../src/api/admin/products')>(
    '../src/api/admin/products',
  )
  return {
    ...actual,
    listProducts: (...args: unknown[]) => listProducts(...args),
    createProduct: (...args: unknown[]) => createProduct(...args),
    getProduct: (...args: unknown[]) => getProduct(...args),
    setProductStatus: (...args: unknown[]) => setProductStatus(...args),
    deleteProduct: (...args: unknown[]) => deleteProduct(...args),
  }
})

describe('列表查询参数与契约逐字段一致', () => {
  it('契约的 query 参数就是这四个(含 $ref 引入的 page/size)', () => {
    const params = queryParamNames(adminContract().paths['/products'].get)

    // 若契约将来加了筛选参数,这里会红 —— 提醒去补界面,而不是以为界面上有。
    // 若哪天 page/size 的 $ref 被改成内联,这里仍应通过(断言的是结果不是形式)。
    expect(params).toEqual(['keyword', 'page', 'size', 'status'])
  })

  it('前端确实只发这四个参数,不多发', async () => {
    const actual = await vi.importActual<typeof import('../src/api/admin/products')>(
      '../src/api/admin/products',
    )

    const sent: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        sent.push(String(input))
        return new Response(JSON.stringify({ code: 0, message: 'ok', traceId: 't',
          data: { list: [], total: 0, page: 1, size: 10 } }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }),
    )

    await actual.listProducts({ keyword: '灰', status: 'on_sale', page: 2, size: 20 })

    const query = new URL(sent[0], 'http://x').searchParams
    expect([...query.keys()].sort()).toEqual(['keyword', 'page', 'size', 'status'])
    // 后台列表**没有** material/spec/priceMin/priceMax —— 那些是 C 端的参数
    expect(query.has('material')).toBe(false)
    expect(query.has('priceMin')).toBe(false)
    vi.unstubAllGlobals()
  })

  it('ProductUpsert 的必填字段与契约一致', () => {
    const contract = adminContract()
    const required = contract.components.schemas.ProductUpsert.required ?? []
    expect(new Set(required)).toEqual(new Set(['sku', 'name', 'pricePerSqm']))
  })

  it('ProductUpsert 的字段集与契约一致(不多不少)', () => {
    const contract = adminContract()
    const properties = Object.keys(contract.components.schemas.ProductUpsert.properties ?? {}).sort()

    expect(properties).toEqual(
      [
        'applicableSpaces',
        'colorPattern',
        'coverImage',
        'images',
        'material',
        'name',
        'pricePerSqm',
        'sku',
        'spec',
        'status',
        'styleTags',
        'surface',
      ].sort(),
    )
    // `id` **不在**请求体里 —— 它在路径里。前端若顺手塞进去,就是超出契约。
    expect(properties).not.toContain('id')
  })

  it('上下架请求体的字段与契约一致', () => {
    const contract = adminContract()
    const schema = contract.paths['/products/{id}/status'].patch.requestBody as unknown as {
      content: Record<string, { schema: { required?: string[]; properties?: Record<string, unknown> } }>
    }
    const json = schema.content['application/json'].schema
    expect(json.required).toEqual(['status'])
    expect(Object.keys(json.properties ?? {})).toEqual(['status'])
  })
})

// ----------------------------------------------------------------------
// 界面行为
// ----------------------------------------------------------------------

const Blank = { template: '<div />' }

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/login', name: 'admin-login', component: Blank },
      { path: '/admin/products', name: 'admin-products', component: Blank },
      { path: '/admin/products/new', name: 'admin-product-new', component: Blank },
      { path: '/admin/products/:id', name: 'admin-product-edit', component: Blank },
    ],
  })
}

const ROWS: Sku[] = [
  { id: 7, sku: 'TL-2043', name: '雅灰石纹', material: '釉面砖', spec: '750×1500mm',
    pricePerSqm: 268, status: 'on_sale' },
  { id: 8, sku: 'TL-3012', name: '雪瓷白', pricePerSqm: 199, status: 'off_sale' },
]

/**
 * 挂载列表页。
 *
 * ⚠️ 数据必须在**挂载之前**设好 —— 组件的 `onMounted` 会立刻发请求,
 * 挂载后再改 mock 已经晚了(第一版就踩了这个,断言拿到的是上一次的数据)。
 */
async function mountAs(
  role: 'super_admin' | 'operator' | 'editor',
  options: { rows?: Sku[]; total?: number; reject?: Error } = {},
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAdminAuthStore()
  auth.user = { id: 1, username: 'tester', role }
  const router = makeRouter()
  await router.push('/admin/products')
  await router.isReady()

  if (options.reject) {
    listProducts.mockRejectedValue(options.reject)
  } else if (options.rows) {
    listProducts.mockResolvedValue({
      list: options.rows,
      total: options.total ?? options.rows.length,
      page: 1,
      size: 10,
    })
  }

  const wrapper = mount(AdminProductsView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, auth, router }
}

/** 带两行样本数据的挂载 —— 多数用例用它。 */
function mountWithRows(role: 'super_admin' | 'operator' | 'editor') {
  return mountAs(role, { rows: ROWS, total: 2 })
}

beforeEach(() => {
  vi.clearAllMocks()
  listProducts.mockResolvedValue({ list: [], total: 0, page: 1, size: 10 })
  // 默认"点了确定" —— 与原来 `window.confirm = vi.fn(() => true)` 同义
  appConfirm.mockResolvedValue(true)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('列表渲染', () => {
  it('画出服务端返回的行', async () => {
    const { wrapper } = await mountWithRows('super_admin')
    expect(wrapper.text()).toContain('TL-2043')
    expect(wrapper.text()).toContain('雅灰石纹')
    expect(wrapper.text()).toContain('已下架')
  })

  it('分页信息取自 total/size,而不是当前页条数', async () => {
    const { wrapper } = await mountWithRows('super_admin')
    expect(wrapper.text()).toContain('共 2 条')
  })

  it('空列表给出提示而不是一张空表', async () => {
    // 数据要在**挂载前**设好 —— onMounted 立刻发请求,挂载后再改 mock 已经晚了
    const { wrapper } = await mountAs('super_admin', { rows: [], total: 0 })
    expect(wrapper.text()).toContain('没有符合条件的 SKU')
  })
})

describe('权限门控(架构 §4.4)', () => {
  it('editor 看不到删除与上下架,但看得到编辑', async () => {
    const { wrapper } = await mountWithRows('editor')
    const labels = wrapper.findAll('button').map((button) => normalize(button.text()))

    expect(labels).toContain('编辑')
    expect(labels).not.toContain('删除')
    // editor 无 product:publish —— 上下架按钮的两个文案都不该出现
    expect(labels).not.toContain('上架')
    expect(labels).not.toContain('下架')
  })

  it('editor 也看不到新增入口(它没有 product:write 吗?)', async () => {
    // editor **有** product:write(§4.4:能创建与编辑),所以新增按钮应当可见
    const { wrapper } = await mountWithRows('editor')
    expect(wrapper.findAll('button').map((b) => normalize(b.text()))).toContain(normalize('新增 SKU'))
  })

  it('operator 看得到全部三个操作', async () => {
    const { wrapper } = await mountWithRows('operator')
    const labels = wrapper.findAll('button').map((button) => normalize(button.text()))

    expect(labels).toContain('编辑')
    expect(labels).toContain('删除')
    expect(labels).toContain('下架')
  })
})

describe('错误提示', () => {
  it('重复上下架按 30002 的文案原样提示', async () => {
    const { wrapper } = await mountWithRows('operator')
    setProductStatus.mockRejectedValue(new BusinessError({ code: ErrorCode.STATUS_NOT_ALLOWED }))

    const downButton = wrapper.findAll('button').find(
      (button) => normalize(button.text()) === normalize('下架'),
    )
    await downButton!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('状态不允许该操作')
  })

  it('加载失败时给出可读提示,不留一张空表', async () => {
    const { wrapper } = await mountAs('super_admin', {
      reject: new BusinessError({ code: ErrorCode.FORBIDDEN }),
    })
    expect(wrapper.find('[role="alert"]').text()).toBe('无权限')
  })

  it('删除要先确认 —— 不可撤销的操作不该一点就走', async () => {
    // 用户在确认框上点了"取消"
    appConfirm.mockResolvedValue(false)
    const { wrapper } = await mountWithRows('operator')

    const removeButton = wrapper.findAll('button').find(
      (button) => normalize(button.text()) === normalize('删除'),
    )
    await removeButton!.trigger('click')
    await flushPromises()

    expect(appConfirm).toHaveBeenCalled()
    expect(deleteProduct).not.toHaveBeenCalled()
  })
})
