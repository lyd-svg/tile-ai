/**
 * 数据字典(C 端只读 `GET /dicts`,openapi-c.yaml)。
 *
 * **空间类型与装修风格走字典,前端不硬编码**(架构 §10.1 · D-14 定稿):
 * 契约里的 enum 只是**内置默认值**,真正的可选项由后台维护。
 * 所以产品表单里的"适用空间 / 风格标签"用字典渲染多选,
 * 而不是让运营手敲 —— 手敲出来的值与字典对不上,筛选就永远查不到东西。
 *
 * 该接口是公开的(契约 `security: []`),不需要登录态。
 *
 * ⚠️ 响应 `data` 是 **`{ list: [...] }`** 而不是裸数组 —— 契约明确写了
 * "与 B 端 `/admin/dicts` 保持同构"。这里把它拍平成数组再返回,
 * 免得每个调用点都写一遍 `.list`。
 */

import { API_BASE, request } from './http'

export type DictType = 'space' | 'style'

export interface Dict {
  id?: number
  type: DictType
  /** 存储键(对外提交值),如 living_room */
  key: string
  /** 展示名,如 客厅 */
  label: string
  sort?: number
  status?: string
}

/** 取字典项。契约支持按 `type` 过滤 —— 用服务端过滤,不在前端筛。 */
export async function listDicts(type?: DictType): Promise<Dict[]> {
  const data = await request<{ list: Dict[] }>({
    path: `${API_BASE}/dicts`,
    query: { type },
  })
  return data?.list ?? []
}

/** 取某一类字典项(空间的给"适用空间"、风格的给"风格标签")。 */
export function listDictsOfType(type: DictType): Promise<Dict[]> {
  return listDicts(type)
}
