/**
 * 数据字典维护(openapi-admin.yaml 的 dicts 分组,基路径 /api/v1/admin)。
 *
 * 权限点 `dict:read` / `dict:write`(架构 §4.4)。
 *
 * ## 与 C 端 `api/dicts.ts` 的三处区别(别复用错)
 *
 * 1. **基路径不同**:这里是 `/admin/dicts`(要后台会话),那里是公开的 `/dicts`;
 * 2. **这里含停用项** —— 契约明说管理端返回 **包含 `status=disabled`** 的项,
 *    而 C 端只返回启用的。运营要能看见并改回停用项;
 * 3. **这里能写**(POST / PUT / DELETE),C 端只读。
 *
 * 所以 `Dict` / `DictType` 从 `../dicts` **复用**(两端描述的是同一批数据),
 * 但请求函数另写一份。
 */

import { ADMIN_BASE, request } from '../http'
import type { Dict, DictType } from '../dicts'

export type { Dict, DictType }

/** 新增/编辑请求体(契约 `DictUpsert`)。 */
export interface DictUpsert {
  type: DictType
  /** 存储键(对外提交值),如 living_room */
  key: string
  /** 展示名,如 客厅 */
  label: string
  /** 排序,升序 */
  sort?: number
  status?: 'enabled' | 'disabled'
}

/**
 * 字典项列表。**含停用项**。
 *
 * `type` 走**服务端过滤**(不在前端筛)—— 契约支持,前端再筛一遍就是第二份实现。
 */
export async function listAdminDicts(type?: DictType): Promise<Dict[]> {
  const data = await request<{ list: Dict[] }>({
    path: `${ADMIN_BASE}/dicts`,
    query: { type },
  })
  return data?.list ?? []
}

/** 新增字典项。返回新项的 id。 */
export function createDict(body: DictUpsert): Promise<{ id: number }> {
  return request<{ id: number }>({ method: 'POST', path: `${ADMIN_BASE}/dicts`, body })
}

/** 编辑字典项。 */
export function updateDict(id: number, body: DictUpsert): Promise<void> {
  return request<void>({ method: 'PUT', path: `${ADMIN_BASE}/dicts/${id}`, body })
}

/**
 * 删除字典项。
 *
 * **被产品/搭配/方案引用时,服务端返回 `30002`**,并会在 `message` 里说明原因 ——
 * 调用方要**原样透出那句话**,不要吞成"删除失败"(用户会不知道该怎么办)。
 */
export function deleteDict(id: number): Promise<void> {
  return request<void>({ method: 'DELETE', path: `${ADMIN_BASE}/dicts/${id}` })
}
