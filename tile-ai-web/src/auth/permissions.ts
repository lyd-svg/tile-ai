/**
 * 后台权限点与角色归属(架构 §4.4)。
 *
 * ⚠️ **本表是架构 §4.4「权限点 → 角色」对照表的逐条转写,共 22 个权限点。**
 * 改这里之前先改架构 —— 别在前端"顺手放宽",前端放宽只会做出一个
 * 看起来能用、点下去 403 的界面。契约里的接口只声明"需要哪个权限点",
 * **不负责**声明角色归属;角色归属的唯一权威来源是架构 §4.4。
 *
 * ⚠️ **前端权限不是安全边界。** 这里只用来隐藏入口,避免给用户误导性的按钮;
 * 真正的拦截在服务端(403 / 10004)。所以「前端显示了按钮」不构成越权 ——
 * 服务端仍然会拒。反过来,"前端隐藏了"也不能当作已经安全。
 */

import type { Role } from '@/api/types'

/** 全部 22 个权限点,顺序与架构 §4.4 的表格一致。 */
export const PERMISSIONS = [
  'product:read',
  'product:write',
  'product:import',
  'product:delete',
  'product:publish',
  'combo:read',
  'combo:write',
  'combo:delete',
  'template:read',
  'template:write',
  'template:delete',
  'furnish:read',
  'furnish:write',
  'furnish:delete',
  'quote-param:read',
  'quote-param:write',
  'store:read',
  'store:write',
  'dict:read',
  'dict:write',
  'account:manage',
  'log:read',
] as const

export type Permission = (typeof PERMISSIONS)[number]

/**
 * 角色 → 权限。逐条对应架构 §4.4 的对照表。
 *
 * 三处最容易搞错的边界(§4.4 特别点名):
 * - `quote-param:*` **只有 super_admin** —— operator 连读都不行;
 * - `product:import` 只有 super_admin / operator;
 * - `dict:read` 三个角色都有,但 `dict:write` 没有 editor(字典对它是只读)。
 */
export const ROLE_PERMISSIONS: Record<Role, readonly Permission[]> = {
  super_admin: [...PERMISSIONS],
  operator: [
    'product:read',
    'product:write',
    'product:import',
    'product:delete',
    'product:publish',
    'combo:read',
    'combo:write',
    'combo:delete',
    'template:read',
    'template:write',
    'template:delete',
    'furnish:read',
    'furnish:write',
    'furnish:delete',
    'store:read',
    'store:write',
    'dict:read',
    'dict:write',
    'log:read',
  ],
  editor: [
    'product:read',
    'product:write',
    'combo:read',
    'combo:write',
    'dict:read',
  ],
}

/** 该角色是否拥有某权限点。角色为空(未登录)一律否。 */
export function can(role: Role | null | undefined, permission: Permission): boolean {
  if (!role) return false
  return ROLE_PERMISSIONS[role]?.includes(permission) ?? false
}

/** 该角色是否拥有其中**任意**一个权限点。用于菜单项(进得去就该看得见)。 */
export function canAny(role: Role | null | undefined, permissions: readonly Permission[]): boolean {
  return permissions.some((permission) => can(role, permission))
}
