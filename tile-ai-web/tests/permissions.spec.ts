/**
 * 权限点与角色归属(F-0.2,依据架构 §4.4)。
 *
 * §4.4 的「权限点 → 角色」对照表是**唯一权威来源**,而且它被 v1.4 专门修过一次
 * —— 修之前契约写「quote-param:write 仅 super_admin/operator」,而同一节的散文
 * 描述写 operator「不可管理报价参数」,两处直接矛盾。
 * 所以这张表值得用断言钉住,别再靠人去记。
 */

import { describe, expect, it } from 'vitest'

import { visibleMenu } from '../src/auth/adminMenu'
import { PERMISSIONS, ROLE_PERMISSIONS, can, canAny } from '../src/auth/permissions'

const ROLES = ['super_admin', 'operator', 'editor'] as const

describe('权限点集合', () => {
  it('恰好 22 个(架构 §4.4 的表格行数)', () => {
    expect(PERMISSIONS).toHaveLength(22)
    expect(new Set(PERMISSIONS).size).toBe(22)
  })

  it('每个角色只引用已声明的权限点', () => {
    for (const role of ROLES) {
      for (const permission of ROLE_PERMISSIONS[role]) {
        expect(PERMISSIONS, `${role} 引用了未声明的 ${permission}`).toContain(permission)
      }
    }
  })
})

describe('super_admin 拥有全部权限', () => {
  it('22 个权限点一个不缺(§4.4:「全部权限」)', () => {
    expect(new Set(ROLE_PERMISSIONS.super_admin)).toEqual(new Set(PERMISSIONS))
  })
})

describe('三处最容易搞错的边界(§4.4 特别点名)', () => {
  it('quote-param:* 只有 super_admin —— operator 连读都不行', () => {
    expect(can('super_admin', 'quote-param:read')).toBe(true)
    expect(can('super_admin', 'quote-param:write')).toBe(true)
    expect(can('operator', 'quote-param:read')).toBe(false)
    expect(can('operator', 'quote-param:write')).toBe(false)
    expect(can('editor', 'quote-param:read')).toBe(false)
  })

  it('product:import 只有 super_admin / operator', () => {
    expect(can('operator', 'product:import')).toBe(true)
    expect(can('editor', 'product:import')).toBe(false)
  })

  it('字典对 editor 只读', () => {
    expect(can('editor', 'dict:read')).toBe(true)
    expect(can('editor', 'dict:write')).toBe(false)
    expect(can('operator', 'dict:write')).toBe(true)
  })
})

describe('editor 的边界', () => {
  it('能创建与编辑产品/搭配', () => {
    expect(can('editor', 'product:write')).toBe(true)
    expect(can('editor', 'combo:write')).toBe(true)
  })

  it('不能删除、不能上下架', () => {
    expect(can('editor', 'product:delete')).toBe(false)
    expect(can('editor', 'product:publish')).toBe(false)
    expect(can('editor', 'combo:delete')).toBe(false)
  })

  it('不能碰账号与日志', () => {
    expect(can('editor', 'account:manage')).toBe(false)
    expect(can('editor', 'log:read')).toBe(false)
  })
})

describe('operator 的边界', () => {
  it('可看操作日志', () => {
    expect(can('operator', 'log:read')).toBe(true)
  })

  it('不可管理账号', () => {
    expect(can('operator', 'account:manage')).toBe(false)
  })

  it('可维护门店与陈设', () => {
    expect(can('operator', 'store:write')).toBe(true)
    expect(can('operator', 'furnish:write')).toBe(true)
  })
})

describe('未登录一律无权限', () => {
  it('角色为 null 时任何权限点都不通过', () => {
    for (const permission of PERMISSIONS) {
      expect(can(null, permission), `${permission} 不该对未登录用户开放`).toBe(false)
    }
    expect(canAny(null, ['product:read'])).toBe(false)
  })
})

describe('菜单门控', () => {
  function labelsFor(role: (typeof ROLES)[number]): string[] {
    return visibleMenu((permissions) => canAny(role, permissions)).map((item) => item.label)
  }

  it('editor 看不到报价参数 / 账号 / 日志 / 模板 等入口', () => {
    const labels = labelsFor('editor')
    expect(labels).toContain('产品库')
    expect(labels).toContain('推荐搭配')
    expect(labels).toContain('数据字典')
    expect(labels).not.toContain('报价参数')
    expect(labels).not.toContain('批量导入')
    expect(labels).not.toContain('账号与权限')
    expect(labels).not.toContain('操作日志')
  })

  it('operator 能看到批量导入,但看不到报价参数与账号', () => {
    const labels = labelsFor('operator')
    expect(labels).toContain('批量导入')
    expect(labels).toContain('操作日志')
    expect(labels).not.toContain('报价参数')
    expect(labels).not.toContain('账号与权限')
  })

  it('super_admin 看到全部菜单', () => {
    const labels = labelsFor('super_admin')
    expect(labels).toContain('报价参数')
    expect(labels).toContain('账号与权限')
    expect(labels).toContain('操作日志')
  })

  it('概览对所有已登录角色可见(它没有权限点要求)', () => {
    for (const role of ROLES) {
      expect(labelsFor(role)).toContain('概览')
    }
  })
})
