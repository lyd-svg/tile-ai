/**
 * 报价参数(openapi-admin.yaml 的 quote-params 分组,基路径 /api/v1/admin)。
 *
 * 权限点 `quote-param:read` / `quote-param:write`,**仅 `super_admin`**
 * (架构 §4.4 点名:operator 连读都不行)。
 *
 * ## 只有四个固定键,而且**没有 POST / DELETE**
 *
 * 契约就是个 `PUT` 按 `paramKey` upsert:参数是**固定集合**,不是可自由增删的数据。
 * 所以本模块**不导出 create / delete** —— 页面也就没法提供"新增/删除"按钮
 * (真要"清空某个键",契约里没有路径,应回提契约,而不是前端私造)。
 *
 * ## 两个量纲(D-15 / D-19)
 *
 * - `aux_unit_price`:**元/㎡**。界面必须把单位显示出来 —— 填错量纲不会报错,
 *   只会算出一份离谱的报价。
 * - 三个 `loss_rate*`:**小数**,`0.06` 表示 6%。服务端**拒绝**百分数写法(填 `6` 会被拒),
 *   所以界面也按小数录入与展示,不在前端做换算。
 */

import { ADMIN_BASE, request } from '../http'

/** 四个固定键。写成联合类型,键名打错在编译期就挡住。 */
export type QuoteParamKey = 'aux_unit_price' | 'loss_rate' | 'loss_rate_min' | 'loss_rate_max'

/** 一条参数。契约 `QuoteParam`。 */
export interface QuoteParam {
  id?: number
  paramKey: QuoteParamKey
  /** 数值。`aux_unit_price` 单位元/㎡;`loss_rate*` 是小数(0.06 = 6%) */
  paramValue: number
  remark?: string | null
}

/** 新增/更新请求体(契约 `QuoteParamUpsert`)。 */
export interface QuoteParamUpsert {
  paramKey: QuoteParamKey
  paramValue: number
  remark?: string
}

/** 参数列表。**不分页**,拍平成数组返回。 */
export async function listQuoteParams(): Promise<QuoteParam[]> {
  const data = await request<{ list: QuoteParam[] }>({ path: `${ADMIN_BASE}/quote-params` })
  return data?.list ?? []
}

/** 按 `paramKey` upsert。同一键再提交就是更新。 */
export function upsertQuoteParam(body: QuoteParamUpsert): Promise<void> {
  return request<void>({ method: 'PUT', path: `${ADMIN_BASE}/quote-params`, body })
}
