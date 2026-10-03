/**
 * 后台账号与权限(openapi-admin.yaml 的 accounts 分组,基路径 /api/v1/admin)。
 *
 * 权限点:整组只有 **`account:manage`**(只有 super_admin 有,架构 §4.4)。
 * 前端据此隐藏入口,但**真正的拦截在服务端**(403 / 10004)。
 *
 * ⚠️ **本组的路径/方法/字段是契约里的推断项**(架构 §10.3 只给了资源前缀),
 * 2026-10-03 随落地评审确认,裁决见 `openapi-verification.md` §58。
 */

import { ADMIN_BASE, request } from '../http'
import type { AdminUserView, PageData, Role } from '../types'

/**
 * 新增/编辑请求体(契约 `AdminUserUpsert`)。**没有 `id`** —— 它在路径里。
 *
 * ⚠️ `password` 的语义两端不一样,**调用方必须自己把握**:
 * - **新增**:必填(服务端空则 20004);
 * - **编辑**:**留空或不传 = 不修改密码**。
 *
 * ⇒ 编辑时**不要**传 `password: ''`,那不是"不改",会走到服务端的密码校验上。
 * 构造请求体时应当**整个键都不带**(见 `AdminAccountsView` 的 `buildBody`)。
 */
export interface AdminUserUpsert {
  username: string
  password?: string
  role: Role
  status?: 'enabled' | 'disabled'
}

/** 列表查询 —— 契约这个接口只有分页两个参数,没有筛选。 */
export interface AccountQuery {
  page?: number
  size?: number
}

export function listAccounts(query: AccountQuery = {}): Promise<PageData<AdminUserView>> {
  return request<PageData<AdminUserView>>({
    path: `${ADMIN_BASE}/accounts`,
    query: {
      // 分页参数名与契约一致:page / size
      page: query.page,
      size: query.size,
    },
  })
}

/**
 * 新增账号。
 *
 * 失败码:20003 用户名已注册、20004 密码格式不合规。两者都是 **HTTP 200**,
 * 由 `request` 抛成 `BusinessError`,页面把 `message` 原样透出即可。
 */
export function createAccount(body: AdminUserUpsert): Promise<{ id: number }> {
  return request<{ id: number }>({
    method: 'POST',
    path: `${ADMIN_BASE}/accounts`,
    body: body,
  })
}

/**
 * 编辑账号 / 分配角色。
 *
 * ⚠️ 服务端的两条防呆都会回 **30002**:
 * 不能停用自己;不能让"启用的超管"归零(含**改角色降级**)。原文案要透出给用户。
 */
export function updateAccount(id: number, body: AdminUserUpsert): Promise<void> {
  return request<void>({ method: 'PUT', path: `${ADMIN_BASE}/accounts/${id}`, body: body })
}

/**
 * 删除账号。**真删除**(物理删该行,不可撤销)。
 *
 * 服务端同样用 **30002** 拒绝"删自己"与"删掉最后一个启用超管"。
 * ⚠️ 删掉之后,它写过的历史日志里 `adminUsername` 会变成 null。
 */
export function deleteAccount(id: number): Promise<void> {
  return request<void>({ method: 'DELETE', path: `${ADMIN_BASE}/accounts/${id}` })
}
