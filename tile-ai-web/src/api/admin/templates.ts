/**
 * 户型模板(openapi-admin.yaml 的 templates 分组,基路径 /api/v1/admin)。
 *
 * 权限点:读 `template:read`、写 `template:write`、删 `template:delete`(架构 §4.4)。
 *
 * ## 一条硬规则:`areas` 是**服务端派生**字段,不进表单
 *
 * `LayoutSpace.areas`(地面/墙面/局部面积)由服务端按长宽高算(架构 §8.3),
 * 后端读详情时也会回填。**它不该作为用户输入提交回去** ——
 * 那等于前端也实现了一遍派生规则,两边迟早不一致。
 * 所以表单里没有它的输入项,提交前也要把它从 `layout` 里**显式剔除**。
 */

import { ADMIN_BASE, request } from '../http'
import type { LayoutModel, PageData, ProductStatus, UnitTemplate } from '../types'

/** 新增/编辑请求体(契约 `UnitTemplateUpsert`)。**没有 `id`**。 */
export interface UnitTemplateUpsert {
  name: string
  roomCount?: number
  areaRange?: string
  structure?: string
  /** 结构化户型。**提交前必须剔除 `spaces[].areas`**(派生字段) */
  layout: LayoutModel
  status?: ProductStatus
}

/** 列表查询。契约为三个**精确匹配**参数 + 分页。 */
export interface UnitTemplateQuery {
  roomCount?: number
  areaRange?: string
  structure?: string
  page?: number
  size?: number
}

export function listTemplates(query: UnitTemplateQuery = {}): Promise<PageData<UnitTemplate>> {
  return request<PageData<UnitTemplate>>({
    path: `${ADMIN_BASE}/templates`,
    query: {
      roomCount: query.roomCount,
      areaRange: query.areaRange,
      structure: query.structure,
      page: query.page,
      size: query.size,
    },
  })
}

/** 详情。失败码 30001。 */
export function getTemplate(id: number): Promise<UnitTemplate> {
  return request<UnitTemplate>({ path: `${ADMIN_BASE}/templates/${id}` })
}

export function createTemplate(body: UnitTemplateUpsert): Promise<{ id: number }> {
  return request<{ id: number }>({ method: 'POST', path: `${ADMIN_BASE}/templates`, body })
}

export function updateTemplate(id: number, body: UnitTemplateUpsert): Promise<void> {
  return request<void>({ method: 'PUT', path: `${ADMIN_BASE}/templates/${id}`, body })
}

export function deleteTemplate(id: number): Promise<void> {
  return request<void>({ method: 'DELETE', path: `${ADMIN_BASE}/templates/${id}` })
}
