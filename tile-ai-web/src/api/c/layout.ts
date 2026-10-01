/**
 * 户型模板 / 解析(openapi-c.yaml 的 layout 分组,基路径 /api/v1)。
 *
 * 两个接口都是**公开的**(契约 `security: []`)—— 录户型本身不需要登录,
 * 登录门槛落在**提交生成**那一步(§5.1:浏览免登录,生成要登录)。
 */

import { API_BASE, request } from '../http'
import type { LayoutModel, PageData, PageQuery, UnitTemplate } from '../types'

export interface LayoutTemplateQuery extends PageQuery {
  roomCount?: number
  areaRange?: string
  structure?: string
}

export interface ParseLayoutRequest {
  /** 自然语言,如"卫生间 2m×1.8m" */
  text?: string
  /** 结构化表单。**优先于 `text`**(契约明说),两者都可不给 → 10001 */
  params?: LayoutModel
}

/** 户型模板列表(支持按房间数 / 面积区间 / 户型结构筛选)。 */
export function listLayoutTemplates(
  query: LayoutTemplateQuery = {},
): Promise<PageData<UnitTemplate>> {
  return request<PageData<UnitTemplate>>({
    path: `${API_BASE}/layout/templates`,
    // interface 没有隐式索引签名,不能直接赋给 Record —— 与 listProducts 同一写法
    query: query as Record<string, unknown>,
  })
}

/**
 * 自然语言 / 参数 → 结构化户型。
 *
 * 解析失败按 **10001** 返回,并用 `data.errors[]` 说明缺哪一项(D-6 裁决)——
 * 调用方要把 `errors` 逐项展示出来,否则用户只知道"参数不对"却不知道哪不对。
 * 服务端**不返回半成品**:尺寸是报价的输入,缺了会让面积算错。
 */
export function parseLayout(payload: ParseLayoutRequest): Promise<LayoutModel> {
  return request<LayoutModel>({ method: 'POST', path: `${API_BASE}/layout/parse`, body: payload })
}
