/**
 * 门店信息(openapi-admin.yaml 的 store 分组,基路径 /api/v1/admin)。
 *
 * 权限点 `store:read` / `store:write`(架构 §4.4)。
 *
 * ## 单店单条:只有 GET 与 PUT
 *
 * `t_store` 是**单店单条**的数据(PRD §7.6),所以契约里**没有 POST / DELETE** ——
 * 页面也就没有"新增/删除",永远是一次**整体更新**。
 * 重复提交仍是 PUT,服务端保证只有一行(集成测试已验)。
 *
 * ⚠️ `GET /store` 在**尚未配置**时返回 **30001**,那是**"还没填",不是故障** ——
 * 调用方要把它当成正常的"空"来处理(见 `AdminStoreView`),而不是当错误显示。
 *
 * `Store` 是 C 端也用同一套的 common 契约结构,故留在 `../types`。
 */

import { ADMIN_BASE, request } from '../http'
import type { Store } from '../types'

export type { Store }

/** 整体更新请求体(契约 `StoreUpsert`)。 */
export interface StoreUpsert {
  name: string
  address: string
  phone: string
  mapUrl?: string
}

/** 取门店信息。未配置时服务端返回 30001,由调用方当"空"处理。 */
export function getAdminStore(): Promise<Store> {
  return request<Store>({ path: `${ADMIN_BASE}/store` })
}

/** 整体更新门店信息。**没有 create/delete** —— 单店只有这一条。 */
export function updateStore(body: StoreUpsert): Promise<void> {
  return request<void>({ method: 'PUT', path: `${ADMIN_BASE}/store`, body })
}
