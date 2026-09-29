/**
 * C 端选砖相关的公开接口(openapi-c.yaml,基路径 /api/v1)。
 *
 * 这三组都是 **`security: []` 免登录**的(PRD §5.1:浏览免登录,生成/保存/问 AI 才要登录),
 * 所以放在一个模块里 —— 它们同属"进店先看看"这一段。
 *
 * 权限/身份相关不在这里,见 ./auth.ts。
 */

import { API_BASE, request } from '../http'
import type { Combo, PageData, ProductStatus, Sku, Store } from '../types'

// ----------------------------------------------------------------------
// 产品库(路径 A,§5.2.1)
// ----------------------------------------------------------------------

/**
 * 列表筛选参数 —— **八个筛选 + 两个分页,逐字段对应契约**。
 *
 * ⚠️ 这八个参数名曾经是契约里的推断项(挂过 x-todo),后由 **D-8 裁决**确定为正式口径,
 * 服务端与集成测试都按它实现(`allEightFiltersWork`)。前端不要自创参数名 ——
 * 多传一个服务端不认,界面看着能用实则不生效。
 *
 * 与**后台**列表的区别:后台只有 `keyword` + `status`,这里才有材质/规格/价格等维度。
 */
export interface ProductQuery {
  keyword?: string
  material?: string
  colorPattern?: string
  spec?: string
  style?: string
  space?: string
  priceMin?: number
  priceMax?: number
  page?: number
  size?: number
}

/** 产品列表。**只返回上架 SKU** —— 下架的砖不该出现在浏览与搜索里(§21.3)。 */
export function listProducts(query: ProductQuery = {}): Promise<PageData<Sku>> {
  return request<PageData<Sku>>({
    path: `${API_BASE}/products`,
    query: query as Record<string, unknown>,
  })
}

/**
 * 产品详情。
 *
 * 下架 SKU 按"不存在"处理(30001)—— 与列表口径统一,
 * 否则用户能拿着一块买不到的砖往下走。
 */
export function getProduct(sku: string): Promise<Sku> {
  return request<Sku>({ path: `${API_BASE}/products/${encodeURIComponent(sku)}` })
}

// ----------------------------------------------------------------------
// 推荐搭配(路径 B,§5.2.2)
// ----------------------------------------------------------------------

export interface ComboQuery {
  space?: string
  style?: string
  page?: number
  size?: number
}

/** 搭配列表。同样只返回上架项(后台要能管理下架项)。 */
export function listCombos(query: ComboQuery = {}): Promise<PageData<Combo>> {
  return request<PageData<Combo>>({
    path: `${API_BASE}/combos`,
    query: query as Record<string, unknown>,
  })
}

export function getCombo(id: number): Promise<Combo> {
  return request<Combo>({ path: `${API_BASE}/combos/${id}` })
}

// ----------------------------------------------------------------------
// 门店(§5.6、§7.6)
// ----------------------------------------------------------------------

/** 门店信息(单店单条)。未配置时服务端返回 **30001**,而不是空对象。 */
export function getStore(): Promise<Store> {
  return request<Store>({ path: `${API_BASE}/store` })
}

/** 仅用于类型收窄:把 status 字面量暴露给调用方。 */
export type { ProductStatus }
