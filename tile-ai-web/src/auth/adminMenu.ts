/**
 * 后台菜单定义。
 *
 * 每项声明**进入该页所需的权限点**,与契约里各接口标注的权限点一致。
 * 用权限点而不是角色来定义菜单 —— 角色归属会变(架构 §4.4 的对照表就被
 * v1.4 改过一次),权限点稳定得多。
 *
 * 菜单项用 `canAny`:只需拥有其中任意一个权限点就该看得见入口
 * (例如只读角色也该能进列表页,只是看不见写操作按钮)。
 */

import type { Permission } from '@/auth/permissions'

export interface AdminMenuItem {
  /** 路由 name */
  name: string
  path: string
  label: string
  /** 进入该页所需的权限点;空数组 = 所有登录用户可见 */
  permissions: readonly Permission[]
  /** 对应的任务卡 ID,页面未实现前显示在占位页上 */
  task: string
}

export const ADMIN_MENU: readonly AdminMenuItem[] = [
  { name: 'admin-home', path: '/admin', label: '概览', permissions: [], task: 'F-0.2' },
  {
    name: 'admin-products',
    path: '/admin/products',
    label: '产品库',
    permissions: ['product:read'],
    task: 'F-0.3',
  },
  {
    name: 'admin-products-import',
    path: '/admin/products/import',
    label: '批量导入',
    // 导入是单独的权限点:operator 可以,editor 不行(架构 §4.4)
    permissions: ['product:import'],
    task: 'F-0.4',
  },
  {
    name: 'admin-combos',
    path: '/admin/combos',
    label: '推荐搭配',
    permissions: ['combo:read'],
    task: 'F-0.5',
  },
  {
    name: 'admin-templates',
    path: '/admin/templates',
    label: '户型模板',
    permissions: ['template:read'],
    task: 'F-0.6',
  },
  {
    name: 'admin-furnish',
    path: '/admin/furnish',
    label: '陈设风格',
    permissions: ['furnish:read'],
    task: 'F-0.7',
  },
  {
    name: 'admin-quote-params',
    path: '/admin/quote-params',
    label: '报价参数',
    // ⚠️ 只有 super_admin —— operator 连读都不行(架构 §4.4 点名)
    permissions: ['quote-param:read'],
    task: 'F-0.8',
  },
  {
    name: 'admin-dicts',
    path: '/admin/dicts',
    label: '数据字典',
    permissions: ['dict:read'],
    task: 'F-0.8',
  },
  {
    name: 'admin-store',
    path: '/admin/store',
    label: '门店信息',
    permissions: ['store:read'],
    task: 'F-0.9',
  },
  {
    name: 'admin-accounts',
    path: '/admin/accounts',
    label: '账号与权限',
    permissions: ['account:manage'],
    task: 'F-0.2',
  },
  {
    name: 'admin-logs',
    path: '/admin/logs',
    label: '操作日志',
    permissions: ['log:read'],
    task: 'F-0.2',
  },
]

/** 按权限点过滤出该角色可见的菜单。 */
export function visibleMenu(
  hasAny: (permissions: readonly Permission[]) => boolean,
): AdminMenuItem[] {
  return ADMIN_MENU.filter((item) => item.permissions.length === 0 || hasAny(item.permissions))
}
