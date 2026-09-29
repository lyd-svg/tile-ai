/**
 * 产品库(openapi-admin.yaml 的 products 分组,基路径 /api/v1/admin)。
 *
 * ⚠️ **筛选参数以契约为准**:后台列表**只有 `keyword` 与 `status`**。
 * `material` / `spec` / `priceMin` / `priceMax` 是 **C 端** `/products` 的参数,
 * 后台没有。前端不要"顺手"多传 —— 服务端不认,界面看着能用实则不生效。
 *
 * 权限点:读 `product:read`、写 `product:write`、删 `product:delete`、
 * 上下架 `product:publish`(架构 §4.4)。前端据此隐藏入口,
 * 但**真正的拦截在服务端**(403 / 10004)。
 */

import { ADMIN_BASE, request } from '../http'
import type { PageData, ProductStatus } from '../types'

/** 产品图。§8.2 t_product_image */
export interface ProductImage {
  url: string
  /** 拍摄角度,如 front / side / detail */
  angle?: string
  sort?: number
}

/** SKU 对外视图(契约 Sku)。§8.2、§7.1 */
export interface Sku {
  /** 内部主键。契约标为**可省略**,故按可选处理 —— 缺它时无法调详情/编辑/删除 */
  id?: number
  sku: string
  name: string
  spec?: string
  material?: string
  /** 光泽度 */
  surface?: string
  /** 花色/纹理描述 */
  colorPattern?: string
  /** 单价(元/㎡) */
  pricePerSqm: number
  applicableSpaces?: string[]
  styleTags?: string[]
  status: ProductStatus
  coverImage?: string
  images?: ProductImage[]
}

/** 新增/编辑请求体(契约 ProductUpsert)。**没有 `id`** —— 它在路径里 */
export interface ProductUpsert {
  sku: string
  name: string
  spec?: string
  material?: string
  surface?: string
  colorPattern?: string
  pricePerSqm: number
  applicableSpaces?: string[]
  styleTags?: string[]
  status?: ProductStatus
  coverImage?: string
  images?: ProductImage[]
}

/** 列表查询 —— 逐字段对应契约的 query 参数,不多不少。 */
export interface ProductQuery {
  keyword?: string
  status?: ProductStatus
  page?: number
  size?: number
}

export function listProducts(query: ProductQuery = {}): Promise<PageData<Sku>> {
  return request<PageData<Sku>>({
    path: `${ADMIN_BASE}/products`,
    query: {
      keyword: query.keyword,
      status: query.status,
      // 分页参数名与契约一致:page / size
      page: query.page,
      size: query.size,
    },
  })
}

export function getProduct(id: number): Promise<Sku> {
  return request<Sku>({ path: `${ADMIN_BASE}/products/${id}` })
}

export function createProduct(body: ProductUpsert): Promise<{ id: number }> {
  return request<{ id: number }>({
    method: 'POST',
    path: `${ADMIN_BASE}/products`,
    body: body,
  })
}

export function updateProduct(id: number, body: ProductUpsert): Promise<void> {
  return request<void>({ method: 'PUT', path: `${ADMIN_BASE}/products/${id}`, body: body })
}

export function deleteProduct(id: number): Promise<void> {
  return request<void>({ method: 'DELETE', path: `${ADMIN_BASE}/products/${id}` })
}

/**
 * 上架 / 下架。
 *
 * 重复操作(对同款砖再下一次架)服务端返回 **30002 状态不允许该操作** ——
 * 界面要把这条提示原样给用户,而不是吞掉。
 */
export function setProductStatus(id: number, status: ProductStatus): Promise<void> {
  return request<void>({
    method: 'PATCH',
    path: `${ADMIN_BASE}/products/${id}/status`,
    body: { status },
  })
}
