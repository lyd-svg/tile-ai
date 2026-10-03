/**
 * 操作日志(openapi-admin.yaml 的 logs 分组,基路径 /api/v1/admin)。
 *
 * **只有读** —— 契约里这个分组没有写接口。日志由服务端的审计切面在写操作成功后追加。
 *
 * 权限点:`log:read`(super_admin / operator)。前端据此隐藏菜单入口,
 * 但**真正的拦截在服务端**(403 / 10004)。
 *
 * ⚠️ 契约的筛选参数就是这 4 个:`adminId` / `action` / `from` / `to`。
 * 不要"顺手"加别的 —— 服务端不认,界面看着能用实则不生效。
 */

import { ADMIN_BASE, request } from '../http'
import type { OperationLog, PageData } from '../types'

/** 列表查询 —— 逐字段对应契约的 query 参数,不多不少。 */
export interface LogQuery {
  /** 操作人 id(精确匹配) */
  adminId?: number
  /** 动作标识(精确匹配,如 `product.delete`) */
  action?: string
  /** 起始时间。契约收 date-time;传纯日期时服务端按当日 00:00:00 解释 */
  from?: string
  /** 结束时间。传纯日期时服务端按当日 23:59:59 解释 */
  to?: string
  page?: number
  size?: number
}

export function listLogs(query: LogQuery = {}): Promise<PageData<OperationLog>> {
  return request<PageData<OperationLog>>({
    path: `${ADMIN_BASE}/logs`,
    query: {
      adminId: query.adminId,
      action: query.action,
      from: query.from,
      to: query.to,
      // 分页参数名与契约一致:page / size
      page: query.page,
      size: query.size,
    },
  })
}
