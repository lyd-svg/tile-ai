/**
 * 选购草稿 —— 把「选砖」的结果带到「录户型」和「生成」页。
 *
 * 只装**两件事**:部位 → SKU 的选定、解析好的户型。这就是从选砖走到生成
 * 需要跨页携带的全部内容。
 *
 * **空间 / 风格 / 角度不放这里** —— 它们是"这一次生成的选择",属于生成页自己的
 * 本地状态。放进全局 store 会让一次性的选择在页面间残留,下次进来还是上次的选项。
 *
 * ⚠️ **只存在内存里,不落 localStorage/sessionStorage**(与 `userAuth` 同一条规矩:
 * 前端零存储,架构 §10.1)。刷新页面即清空 —— 页面要按"没有草稿"渲染空态并给回去的路,
 * 而不是拿旧数据硬凑一次生成。
 */

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import type { LayoutModel, Part, PartSelection } from '@/api/types'

/** 草稿里的一个部位选定。`name` / `pricePerSqm` 是展示冗余,不参与提交。 */
export interface DraftPart {
  part: Part
  sku: string
  name?: string
  pricePerSqm?: number
}

export const useSelectionDraftStore = defineStore('selectionDraft', () => {
  /** 已选部位,**按 part 唯一** —— 同一部位再选一次是替换,不是叠加。 */
  const parts = ref<DraftPart[]>([])
  /** 户型录入页的产出。未录入时为 null */
  const layout = ref<LayoutModel | null>(null)

  const hasParts = computed(() => parts.value.length > 0)
  const selectedCount = computed(() => parts.value.length)

  function partOf(part: Part): DraftPart | undefined {
    return parts.value.find((item) => item.part === part)
  }

  /** 选定某部位用的砖。同部位已有选择则**替换**(一个部位只铺一种砖)。 */
  function setPart(part: Part, sku: string, meta: Omit<DraftPart, 'part' | 'sku'> = {}): void {
    const index = parts.value.findIndex((item) => item.part === part)
    const next: DraftPart = { part, sku, ...meta }
    if (index >= 0) {
      parts.value[index] = next
    } else {
      parts.value.push(next)
    }
  }

  function removePart(part: Part): void {
    parts.value = parts.value.filter((item) => item.part !== part)
  }

  function setLayout(value: LayoutModel | null): void {
    layout.value = value
  }

  function clear(): void {
    parts.value = []
    layout.value = null
  }

  /**
   * 转成契约的 `PartSelection[]`。
   *
   * **只带 `{ part, sku }`**:`area` 由服务端按户型派生(前端算就是第二份实现),
   * `source` 请求中可省略(默认 user)。别自作聪明往里补字段 ——
   * 多传一个错值比少传更难查。
   */
  function toPartSelections(): PartSelection[] {
    return parts.value.map(({ part, sku }) => ({ part, sku }))
  }

  return {
    parts,
    layout,
    hasParts,
    selectedCount,
    partOf,
    setPart,
    removePart,
    setLayout,
    clear,
    toPartSelections,
  }
})
