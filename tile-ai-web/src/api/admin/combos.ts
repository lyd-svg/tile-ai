/**
 * 推荐搭配(openapi-admin.yaml 的 combos 分组,基路径 /api/v1/admin)。
 *
 * 权限点:读 `combo:read`、写 `combo:write`、删 `combo:delete`(架构 §4.4)。
 *
 * ## 一处容易踩的坑:PUT 是 **items 整体替换**
 *
 * `ComboUpsert.items` 会把原来的条目**整批换掉**。而 `ComboItem` 上还有两个可选字段
 * `note` / `sort`(契约里有,后端也落库)—— **编辑时必须原样带上**,
 * 否则"只改了个名字"就会把备注一起清空,而且**不会有任何报错**。
 * 所以调用方要先 `getCombo` 拿到完整条目,再逐字段回填。
 */

import { ADMIN_BASE, request } from '../http'
import type { Combo, ComboItem, PageData, ProductStatus } from '../types'

/**
 * 新增/编辑请求体(契约 `ComboUpsert`)。
 *
 * ⚠️ **没有 `id`** —— 它在路径里。
 */
export interface ComboUpsert {
  name: string
  space: string
  style: string
  refPriceMin?: number
  refPriceMax?: number
  coverImage?: string
  status?: ProductStatus
  sort?: number
  /** 契约 `minItems: 1` —— 一个搭配至少要配一块砖 */
  items: ComboItem[]
}

/** 列表查询。契约只有 `space` / `style` + 分页。 */
export interface ComboQuery {
  space?: string
  style?: string
  page?: number
  size?: number
}

export function listCombos(query: ComboQuery = {}): Promise<PageData<Combo>> {
  return request<PageData<Combo>>({
    path: `${ADMIN_BASE}/combos`,
    query: { space: query.space, style: query.style, page: query.page, size: query.size },
  })
}

/** 详情。失败码 30001。**编辑前必须先取一次**,否则会丢掉 note / sort。 */
export function getCombo(id: number): Promise<Combo> {
  return request<Combo>({ path: `${ADMIN_BASE}/combos/${id}` })
}

export function createCombo(body: ComboUpsert): Promise<{ id: number }> {
  return request<{ id: number }>({ method: 'POST', path: `${ADMIN_BASE}/combos`, body })
}

export function updateCombo(id: number, body: ComboUpsert): Promise<void> {
  return request<void>({ method: 'PUT', path: `${ADMIN_BASE}/combos/${id}`, body })
}

/**
 * 删除搭配。
 *
 * **被方案引用时服务端返回 30002** 并说明原因 —— 调用方原样透出,不要吞成"删除失败"。
 */
export function deleteCombo(id: number): Promise<void> {
  return request<void>({ method: 'DELETE', path: `${ADMIN_BASE}/combos/${id}` })
}
