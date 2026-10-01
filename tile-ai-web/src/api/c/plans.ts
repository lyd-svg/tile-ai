/**
 * 方案管理(openapi-c.yaml 的 plan 分组,基路径 /api/v1)。
 *
 * 六条接口**全部需要登录**(契约里每条都是 `security: cookieAuth`)——
 * 方案是「谁的」,服务端按 Cookie 里的用户判定。别人的方案一律 **30001**,
 * 且文案与「不存在」完全相同(防枚举,见 T-1.12 的裁决)。
 *
 * 请求/响应类型:`PlanSummary` / `PlanDetail` / `PlanPart` / `QuoteResult` 来自
 * **common 契约**,所以放在 `../types` 供 C 端 B 端共用;只有本模块专有的
 * 查询参数与内联返回体留在这里。
 */

import { API_BASE, request } from '../http'
import type { PageData, PlanDetail, PlanSummary, QuoteResult } from '../types'

/** 列表查询 —— 契约 `/plans` **只有 page / size 两个参数**,不多传。 */
export interface PlanQuery {
  page?: number
  size?: number
}

/** 我的方案列表。需登录。§5.6 */
export function listPlans(query: PlanQuery = {}): Promise<PageData<PlanSummary>> {
  return request<PageData<PlanSummary>>({
    path: `${API_BASE}/plans`,
    query: { page: query.page, size: query.size },
  })
}

/** 方案详情。失败码 30001(不存在 / 不是本人的 / 已删除 —— 三者同码同文案)。§5.6 */
export function getPlan(id: number): Promise<PlanDetail> {
  return request<PlanDetail>({ path: `${API_BASE}/plans/${id}` })
}

/**
 * 重命名方案。§5.6、§10.2
 *
 * ID 在路径里,**请求体只有 `name`**(契约 `required: [name]`)。
 *
 * ⚠️ **名字长度上限 200 是「码点」而不是 UTF-16 单元**,而且**契约里没有声明**
 * (`openapi-c.yaml` 的 `name` 只写了 `type: string`)—— 上层只能在
 * `PlanService.rename` 里按码点判,超了报 **10001**。
 * 所以调用方的客户端校验要用 `[...name].length`,不能用 `name.length`
 * (后者会把 100 个 emoji 数成 200 而误判超长)。
 */
export function renamePlan(id: number, name: string): Promise<void> {
  return request<void>({ method: 'PATCH', path: `${API_BASE}/plans/${id}`, body: { name } })
}

/**
 * 删除方案(逻辑删除,库里留痕)。§5.6、§10.2
 *
 * **二次确认在前端** —— 契约把这句话直接写在 description 里。
 */
export function deletePlan(id: number): Promise<void> {
  return request<void>({ method: 'DELETE', path: `${API_BASE}/plans/${id}` })
}

/**
 * 另存为:复制为新方案,返回新 planId。§5.6、§11.4
 *
 * 返回体是**契约里内联写的** `{ planId }`(没有独立 schema),
 * 所以这个类型留在本文件,不进 `types.ts`。
 * 副本的名字由服务端定(`PlanNames.copyName`,形如「原名(副本)」)—— 契约未规定。
 */
export function copyPlan(id: number): Promise<{ planId: number }> {
  return request<{ planId: number }>({ method: 'POST', path: `${API_BASE}/plans/${id}/copy` })
}

/**
 * 按当前价重算报价。§15.7
 *
 * ⚠️ **没有请求体,也绝不能加** —— 这是架构不变量 6。
 * 部位 / 面积 / 开口一律取自**方案快照**,不接受前端传参;否则就等于给前端
 * 开了一条绕过 `/quote/calc` 参数校验的后门。
 *
 * 返回的 `data` **就是重算后的完整 `QuoteResult`** —— 调用方拿到后直接就地刷新即可,
 * 不需要再 `getPlan` 一轮(那是白打的请求,而且中间会闪一下旧值)。
 */
export function recalcPlan(id: number): Promise<QuoteResult> {
  return request<QuoteResult>({ method: 'POST', path: `${API_BASE}/plans/${id}/recalc` })
}
