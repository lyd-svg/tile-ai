/**
 * 方案管理(openapi-c.yaml 的 plan 分组,基路径 /api/v1)。
 *
 * **九条接口里有八条需要登录**(契约里每条都是 `security: cookieAuth`)——
 * 方案是「谁的」,服务端按 Cookie 里的用户判定。别人的方案一律 **30001**,
 * 且文案与「不存在」完全相同(防枚举,见 T-1.12 的裁决)。
 *
 * **唯一的例外是 `getSharedPlan`**:`GET /share/{token}` 的契约写的是
 * `security: []`,拿到链接的人**不需要账号**(T-1.13)。
 *
 * 请求/响应类型:`PlanSummary` / `PlanDetail` / `PlanPart` / `QuoteResult` 来自
 * **common 契约**,所以放在 `../types` 供 C 端 B 端共用;只有本模块专有的
 * 查询参数与内联返回体(`{ planId }`、`ShareResult`)留在这里。
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

// ------------------------------------------------------------------
// 分享(T-1.13 的后端,F-1.10 的前端)
// ------------------------------------------------------------------

/**
 * 分享结果(契约 `openapi-c.yaml` 的 `ShareResult`)。
 *
 * ⚠️ **契约里这三个字段一个都没有 `required`**,而这个类型来自 C 端契约本身
 * (不是 common),所以留在本模块,与 `{ planId }` 同理。
 *
 * ⚠️ **`qrCodeUrl` 恒不出现** —— 后端 `ShareResultView` 带 `@JsonInclude(NON_NULL)`,
 * 该字段永远是 null、序列化后**整个键消失**。这是 2026-10-01 用户拍板的:
 * **二维码由前端画**,后端只给链接(后端不经手二维码图,就少一个对象存储依赖)。
 * 所以这里三个都写成可选 —— 读 `qrCodeUrl` 只会拿到 `undefined`,
 * **不要指望它**,更不要顺手把它填成一个图片地址。
 *
 * 契约这个 schema 上还挂着 `x-todo`("返回字段结构未定义"),即上游从未定义过
 * `qrCodeUrl` 该是什么形状;这是**登记在案的已知不一致面**(§43.2),不是笔误。
 */
export interface ShareResult {
  /** 分享令牌。**它是不登录就能换到一份方案的全部凭据** —— 别打日志、别外传 */
  shareToken?: string
  /** 完整分享链接,**指向前端页面**(不是 API 地址) */
  shareUrl?: string
  /** 恒为 `undefined` —— 见上方说明 */
  qrCodeUrl?: string
}

/**
 * 生成分享链接。§5.6、§10.2、§13.5
 *
 * **幂等**:已经分享过就**复用旧令牌**,点几次拿到的都是同一条链接 ——
 * 后端刻意不重新生成,否则家人手里那条已经发出去的链接会当场作废,
 * 而用户完全不知道自己做错了什么(`PlanShareService.share`)。
 *
 * 没有请求体:有效期、访问范围这类开关上游从未定义过,不凭空造
 * (架构 §13.5 那句「访问范围控制」**没有展开**,§43.9 已登记)。
 */
export function sharePlan(id: number): Promise<ShareResult> {
  return request<ShareResult>({ method: 'POST', path: `${API_BASE}/plans/${id}/share` })
}

/**
 * 废除分享链接 —— 旧链接**立刻**打不开。§13.5
 *
 * **幂等**:对一份本来就没分享的方案再废除一次也是成功(后端不看受影响行数)。
 * 返回的是**裸信封**(契约里这条的响应就是 `$ref ApiResponse`),没有 `data`。
 */
export function revokeShare(id: number): Promise<void> {
  return request<void>({ method: 'DELETE', path: `${API_BASE}/plans/${id}/share` })
}

/**
 * 凭令牌读一份**公开只读**方案快照。**不需要登录。** §10.2
 *
 * 返回的就是完整的 `PlanDetail` —— 与本人详情接口**同一个 schema、同一份组装代码**
 * (`PlanDetailAssembler.assemble`),里面有**明文户型**、完整报价、全部效果图。
 * 契约就是这么定的(§43.8 已把这条暴露面显式记下来)。
 *
 * ⚠️ **取不到一律 30001、文案固定「方案不存在」**,后端刻意不区分
 * 「令牌从没存在过 / 已废除 / 方案已删除」—— 区分开来就等于对外提供了一个
 * **能枚举令牌的探针**,而这个接口恰恰是唯一匿名可达的读接口(§43.4)。
 * 所以界面上不要自作聪明地猜"是失效了还是打错了",照后端那句话显示。
 *
 * ⚠️ 令牌进的是 **URL 路径**,必须 `encodeURIComponent` ——
 * 令牌是 Base64URL(只有 `A-Za-z0-9-_`),今天编不编码都一样,
 * 但这一步挡的是"以后有人换了令牌算法"。
 */
export function getSharedPlan(token: string): Promise<PlanDetail> {
  return request<PlanDetail>({ path: `${API_BASE}/share/${encodeURIComponent(token)}` })
}
