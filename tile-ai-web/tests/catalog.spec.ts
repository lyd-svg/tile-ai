/**
 * C 端选砖(F-1.1 ~ F-1.4):契约一致性 + 界面行为。
 *
 * 契约一致性这部分尤其重要 —— C 端 `/products` 的**八个筛选参数**曾经是契约里的
 * 推断项(`x-todo`),后由 **D-8 裁决**定为正式口径。这里把它钉住:
 * 契约改了、前端没跟上,或者有人"顺手"加了个自创参数名,都会红。
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
import type { Sku } from '../src/api/types'
import ProductListView from '../src/views/site/ProductListView.vue'
import LoginView from '../src/views/site/LoginView.vue'
import { useUserAuthStore } from '../src/stores/userAuth'

const C_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-c.yaml')
const COMMON_CONTRACT = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../openapi-common.yaml',
)

interface Parameter {
  name?: string
  in?: string
  $ref?: string
}

function queryParamNames(documentPath: string, path: string, method: string): string[] {
  const contract = load(readFileSync(documentPath, 'utf8')) as {
    paths: Record<string, Record<string, { parameters?: Parameter[] }>>
  }
  const common = load(readFileSync(COMMON_CONTRACT, 'utf8')) as {
    components: { parameters: Record<string, { name: string; in: string }> }
  }

  return (contract.paths[path][method].parameters ?? [])
    .map((parameter) => {
      // page / size 是 $ref 引过来的 —— 不解析会**静默漏掉**它们
      if (parameter.$ref) {
        return common.components.parameters[parameter.$ref.split('/').pop() ?? '']
      }
      return parameter as { name: string; in: string }
    })
    .filter((parameter): parameter is { name: string; in: string } => Boolean(parameter))
    .filter((parameter) => parameter.in === 'query')
    .map((parameter) => parameter.name)
    .sort()
}

describe('C 端产品列表的查询参数与契约一致', () => {
  it('就是 D-8 裁决的那八个筛选 + 分页', () => {
    const params = queryParamNames(C_CONTRACT, '/products', 'get')

    expect(params).toEqual([
      'colorPattern',
      'keyword',
      'material',
      'page',
      'priceMax',
      'priceMin',
      'size',
      'space',
      'spec',
      'style',
    ])
  })

  it('前端确实只发这些参数,不多发', async () => {
    const actual = await vi.importActual<typeof import('../src/api/c/catalog')>(
      '../src/api/c/catalog',
    )
    const sent: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        sent.push(String(input))
        return new Response(
          JSON.stringify({ code: 0, message: 'ok', traceId: 't',
            data: { list: [], total: 0, page: 1, size: 9 } }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        )
      }),
    )

    await actual.listProducts({
      keyword: '灰', material: '釉面砖', colorPattern: '石纹', spec: '750',
      style: 'modern', space: 'bathroom', priceMin: 100, priceMax: 500,
      page: 1, size: 9,
    })

    const query = new URL(sent[0], 'http://x').searchParams
    expect([...query.keys()].sort()).toEqual([
      'colorPattern', 'keyword', 'material', 'page', 'priceMax',
      'priceMin', 'size', 'space', 'spec', 'style',
    ])
    vi.unstubAllGlobals()
  })

  it('详情按 sku(编号)取,不是主键 —— 选砖流程里流传的是编号', () => {
    const params = queryParamNames(C_CONTRACT, '/products/{sku}', 'get')
    // 路径参数不是 query,这里断言的是路径本身用的是 sku
    expect(Object.keys(
      (load(readFileSync(C_CONTRACT, 'utf8')) as {
        paths: Record<string, unknown>
      }).paths,
    )).toContain('/products/{sku}')
    expect(params).toEqual([])
  })
})

// ----------------------------------------------------------------------
// 界面行为
// ----------------------------------------------------------------------

const listProducts = vi.fn()
vi.mock('@/api/c/catalog', async () => {
  const actual = await vi.importActual<typeof import('../src/api/c/catalog')>(
    '../src/api/c/catalog',
  )
  return { ...actual, listProducts: (...args: unknown[]) => listProducts(...args) }
})

const login = vi.fn()
// ⚠️ 这个工厂**替换整个模块** —— 给 @/api/c/auth 加了新导出就必须在这里补上,
// 否则任何走到那个导出的地方都会报 "not a function"(不是编译错误,是运行时才炸)。
vi.mock('@/api/c/auth', () => ({
  login: (...args: unknown[]) => login(...args),
  register: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn(),
  fetchMe: vi.fn(),
}))

// ⚠️ 字典也要打桩:组件在 onMounted 里**先取字典再加载列表**,
// 不打桩的话那次真实 fetch 会挂住,后面的 load() 根本跑不到 ——
// 表现是"列表一片空白",而不是报错。第一版就踩了这个。
vi.mock('@/api/dicts', () => ({
  listDictsOfType: vi.fn(async () => []),
  listDicts: vi.fn(async () => []),
}))

const Blank = { template: '<div />' }

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Blank },
      { path: '/products', name: 'products', component: Blank },
      { path: '/products/:sku', name: 'product-detail', component: Blank },
      { path: '/login', name: 'login', component: Blank },
    ],
  })
}

const ROWS: Sku[] = [
  { sku: 'TL-1001', name: '雅灰石纹', material: '釉面砖', spec: '750×1500mm',
    surface: '柔光', pricePerSqm: 268, status: 'on_sale', styleTags: ['modern'] },
  { sku: 'TL-1002', name: '雪瓷白', material: '抛光砖', pricePerSqm: 199, status: 'on_sale' },
]

/**
 * 挂载列表页。
 *
 * ⚠️ mock 必须在**挂载之前**由本函数设置 —— 若测试自己在调用前后设 mock,
 * 会被这里覆盖掉(第一版就踩了,拿到的是上一轮的数据)。
 */
async function mountList(options: { rows?: Sku[]; total?: number; reject?: Error } = {}) {
  const rows = options.rows ?? ROWS
  const pinia = createPinia()
  setActivePinia(pinia)

  if (options.reject) {
    listProducts.mockRejectedValue(options.reject)
  } else {
    listProducts.mockResolvedValue({
      list: rows,
      total: options.total ?? rows.length,
      page: 1,
      size: 9,
    })
  }

  const router = makeRouter()
  await router.push('/products')
  await router.isReady()

  const wrapper = mount(ProductListView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.clearAllMocks()
  listProducts.mockResolvedValue({ list: [], total: 0, page: 1, size: 9 })
})

describe('产品列表', () => {
  it('画出服务端返回的砖', async () => {
    const { wrapper } = await mountList()
    expect(wrapper.text()).toContain('雅灰石纹')
    expect(wrapper.text()).toContain('¥268')
    expect(wrapper.text()).toContain('共 2 款')
  })

  it('每张卡片链到详情页,且用 sku 而不是主键', async () => {
    const { wrapper } = await mountList()
    const hrefs = wrapper.findAll('a').map((anchor) => anchor.attributes('href'))
    expect(hrefs).toContain('/products/TL-1001')
  })

  it('空结果给提示而不是一张空网格', async () => {
    const { wrapper } = await mountList({ rows: [], total: 0 })
    expect(wrapper.text()).toContain('没有符合条件的瓷砖')
  })

  it('加载失败给出可读提示', async () => {
    const { wrapper } = await mountList({
      reject: new BusinessError({ code: ErrorCode.INTERNAL_ERROR }),
    })
    expect(wrapper.find('[role="alert"]').text()).toBe('系统内部错误')
  })
})

describe('C 端登录', () => {
  async function mountLogin() {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await router.push('/login')
    await router.isReady()
    const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } })
    return { wrapper, router, auth: useUserAuthStore() }
  }

  async function submit(wrapper: Awaited<ReturnType<typeof mountLogin>>['wrapper'], u = 'fan', p = 'abc123456') {
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue(u)
    await inputs[1].setValue(p)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
  }

  it('登录成功写入登录态', async () => {
    login.mockResolvedValue({ userId: 1, username: 'fan' })
    const { wrapper, auth } = await mountLogin()
    await submit(wrapper)
    expect(auth.isAuthenticated).toBe(true)
  })

  it('失败提示不区分「用户名不存在」与「密码错误」', async () => {
    login.mockRejectedValue(new BusinessError({ code: ErrorCode.BAD_CREDENTIALS }))
    const { wrapper } = await mountLogin()
    await submit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe('账号或密码错误')

    const { wrapper: other } = await mountLogin()
    await submit(other, 'no-such-user', 'whatever')
    expect(other.find('[role="alert"]').text()).toBe('账号或密码错误')
  })

  it('注册模式下前端先挡密码长度(服务端仍会校验 20004)', async () => {
    const { wrapper } = await mountLogin()
    await wrapper.findAll('a')[0].trigger('click') // 切到注册
    await submit(wrapper, 'fan', '123') // 太短
    expect(wrapper.find('[role="alert"]').text()).toContain('6–20 位')
    expect(login).not.toHaveBeenCalled()
  })
})
