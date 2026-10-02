/**
 * 陈设风格配置(openapi-admin.yaml 的 furnish 分组,基路径 /api/v1/admin)。
 *
 * 权限点:读 `furnish:read`、写 `furnish:write`、删 `furnish:delete`(架构 §4.4)。
 * 前端据此隐藏入口,**真正的拦截在服务端**(403 / 10004)。
 *
 * ## 两条与别的模块不同的地方
 *
 * 1. **没有 POST** —— 契约只有 `PUT /furnish`,语义是**按 `(space, style)` upsert**
 *    (注释写明"防重复矩阵")。所以这里也**不导出任何 create 函数**:
 *    想造出重复组合,在这个模块的接口上就做不到。
 * 2. **列表不分页** —— `GET /furnish` 返回 `{ list: [...] }`(不是 `PageData`),
 *    这里拍平成数组再返回,免得每个调用点都写 `.list`(同 `api/dicts.ts` 的做法)。
 */

import { ADMIN_BASE, request } from '../http'
import type { ProductStatus } from '../types'

/**
 * 一条陈设配置。契约 `FurnishStyle`。
 *
 * ⚠️ `status` 是 **`ProductStatus`(on_sale / off_sale)**,**不是**字典那种
 * enabled / disabled —— 两者枚举不同,别混用。
 */
export interface FurnishStyle {
  id?: number
  space: string
  style: string
  prompt: string
  coverImage?: string
  status?: ProductStatus
}

/** 新增/更新请求体(契约 `FurnishStyleUpsert`,无 id —— 组合本身就是键)。 */
export interface FurnishStyleUpsert {
  space: string
  style: string
  prompt: string
  coverImage?: string
  status?: ProductStatus
}

/** 列表筛选。契约只有 `space` / `style` 两个参数。 */
export interface FurnishStyleQuery {
  space?: string
  style?: string
}

/** 陈设配置列表。需登录(`furnish:read`)。 */
export async function listFurnishStyles(query: FurnishStyleQuery = {}): Promise<FurnishStyle[]> {
  const data = await request<{ list: FurnishStyle[] }>({
    path: `${ADMIN_BASE}/furnish`,
    query: { space: query.space, style: query.style },
  })
  return data?.list ?? []
}

/**
 * 新增或更新一条配置 —— **按 `(space, style)` upsert**,同一组合再提交就是更新。
 *
 * 不传 id:`(space, style)` 组合本身就是键,服务端靠它判新增还是更新。
 */
export function upsertFurnishStyle(body: FurnishStyleUpsert): Promise<void> {
  return request<void>({ method: 'PUT', path: `${ADMIN_BASE}/furnish`, body })
}

/** 删除一条配置。**被引用的场景由服务端按 30002 表达**,前端原样透出。 */
export function deleteFurnishStyle(id: number): Promise<void> {
  return request<void>({ method: 'DELETE', path: `${ADMIN_BASE}/furnish/${id}` })
}
