/**
 * 选购草稿(步骤 1):把选砖结果带到录户型 / 生成页的那份内存状态。
 *
 * 这里最要紧的一条是 **`toPartSelections()` 只发 `{ part, sku }`** ——
 * `area` 由服务端按户型派生(前端算就是第二份实现),`source` 请求中可省略。
 * 多发一个字段不会报错,只会让服务端按错的面积报价,所以拿测试钉住。
 */

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useSelectionDraftStore } from '../src/stores/selectionDraft'
import type { LayoutModel } from '../src/api/types'

const LAYOUT: LayoutModel = {
  spaces: [{ type: 'bathroom', name: '卫生间', length: 2, width: 1.8, height: 2.7 }],
}

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('部位选定', () => {
  it('一开始没有草稿 —— 页面要按"没选过砖"渲染空态', () => {
    const draft = useSelectionDraftStore()
    expect(draft.hasParts).toBe(false)
    expect(draft.selectedCount).toBe(0)
  })

  it('选定一个部位后能查到它', () => {
    const draft = useSelectionDraftStore()
    draft.setPart('floor', 'TL-1001', { name: '雅灰石纹', pricePerSqm: 268 })

    expect(draft.hasParts).toBe(true)
    expect(draft.partOf('floor')?.name).toBe('雅灰石纹')
  })

  it('同一部位再选一次是**替换**不是叠加 —— 一个部位只铺一种砖', () => {
    const draft = useSelectionDraftStore()
    draft.setPart('floor', 'TL-1001', { name: '雅灰石纹' })
    draft.setPart('floor', 'TL-1002', { name: '雪瓷白' })

    expect(draft.selectedCount).toBe(1)
    expect(draft.partOf('floor')?.sku).toBe('TL-1002')
  })

  it('不同部位各存一份(地砖 + 墙砖是常规组合)', () => {
    const draft = useSelectionDraftStore()
    draft.setPart('floor', 'TL-1001')
    draft.setPart('wall', 'TL-1002')

    expect(draft.selectedCount).toBe(2)
    expect(draft.partOf('wall')?.sku).toBe('TL-1002')
  })

  it('移除只影响那一个部位', () => {
    const draft = useSelectionDraftStore()
    draft.setPart('floor', 'TL-1001')
    draft.setPart('wall', 'TL-1002')

    draft.removePart('floor')

    expect(draft.partOf('floor')).toBeUndefined()
    expect(draft.partOf('wall')?.sku).toBe('TL-1002')
  })
})

describe('提交给 /generate 的形状', () => {
  it('只发 { part, sku } —— area 由服务端派生、source 请求中可省略', () => {
    const draft = useSelectionDraftStore()
    draft.setPart('floor', 'TL-1001', { name: '雅灰石纹', pricePerSqm: 268 })

    // 展示冗余(name / pricePerSqm)绝不能混进请求体
    expect(draft.toPartSelections()).toEqual([{ part: 'floor', sku: 'TL-1001' }])
  })

  it('没有草稿时是空数组,而不是 undefined —— 调用方不必再判空', () => {
    const draft = useSelectionDraftStore()
    expect(draft.toPartSelections()).toEqual([])
  })
})

describe('户型', () => {
  it('录入后能取回;清空草稿时一并清掉', () => {
    const draft = useSelectionDraftStore()
    draft.setPart('floor', 'TL-1001')
    draft.setLayout(LAYOUT)

    expect(draft.layout).toEqual(LAYOUT)

    draft.clear()
    expect(draft.layout).toBeNull()
    expect(draft.hasParts).toBe(false)
  })
})
