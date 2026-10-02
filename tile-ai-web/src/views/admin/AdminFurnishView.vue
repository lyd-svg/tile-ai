<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import { listAdminDicts } from '@/api/admin/dicts'
import type { Dict } from '@/api/admin/dicts'
import { deleteFurnishStyle, listFurnishStyles, upsertFurnishStyle } from '@/api/admin/furnish'
import type { FurnishStyle } from '@/api/admin/furnish'
import { useAdminAuthStore } from '@/stores/adminAuth'
import type { ProductStatus } from '@/api/types'

/**
 * 陈设风格配置(F-0.7)。
 *
 * ## 为什么是"真矩阵"而不是"表格列表"
 *
 * 产品说的一直是"**空间 × 风格矩阵**" —— 一个组合要么配了、要么没配,**不存在两条**。
 * 做成矩阵,**每个格子本身就是一个确定的 `(space, style)`**:新增/编辑靠"点哪一格"决定,
 * 组合**不是手敲出来的** —— "不出现重复组合"于是成了**结构上的**结果,而不是靠校验去拦。
 *
 * 代价是要多拉两份字典作轴。轴取 **字典项 ∪ 已有配置里出现过的 key**:这样
 * **字典项被删掉之后遗留的配置不会被静默丢掉**(它会以"未在字典中"的标签留在轴上)。
 *
 * ## 两条与别处不同的口径
 *
 * - `status` 是 **`ProductStatus`(on_sale / off_sale)** —— 与字典的 enabled/disabled **不是一回事**;
 * - 表单里 **`space` / `style` 是静态文本,不是输入控件** —— 从交互上就造不出重复组合。
 */
const auth = useAdminAuthStore()

interface AxisItem {
  key: string
  label: string
  /** 字典里已停用(管理端能看见停用项) */
  disabled: boolean
  /** 还不在字典里(字典被删过,或历史数据)—— 仍显示,不静默丢弃 */
  inDict: boolean
}

const spaces = ref<AxisItem[]>([])
const styles = ref<AxisItem[]>([])
const configs = ref<Map<string, FurnishStyle>>(new Map())
/** 服务端若回了重复组合,这里置真并显示一条警示(矩阵仍只画一格) */
const duplicateCombo = ref(false)

const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const notice = ref('')
const fieldErrors = ref<string[]>([])

const selected = ref<{ space: string; style: string } | null>(null)
const form = reactive({ prompt: '', coverImage: '', status: 'on_sale' as ProductStatus })

const canWrite = computed(() => auth.has('furnish:write'))
const canDelete = computed(() => auth.has('furnish:delete'))

const selectedConfig = computed(() =>
  selected.value ? configs.value.get(comboKey(selected.value.space, selected.value.style)) : undefined,
)

function comboKey(space: string, style: string): string {
  // 用 NUL 连接:`space` / `style` 里可能出现短横线之类的字符,拼字符串会撞键
  return `${space}\u0000${style}`
}

function buildAxis(dict: Dict[], used: string[]): AxisItem[] {
  const items: AxisItem[] = []
  const seen = new Set<string>()
  for (const entry of dict) {
    if (seen.has(entry.key)) continue
    seen.add(entry.key)
    items.push({
      key: entry.key,
      label: entry.label || entry.key,
      disabled: entry.status === 'disabled',
      inDict: true,
    })
  }
  for (const key of used) {
    if (seen.has(key)) continue
    seen.add(key)
    items.push({ key, label: key, disabled: false, inDict: false })
  }
  return items
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    // 三份分开兜底:**字典拉不到不该让已有配置不可见**(所以用 allSettled,不 all)
    const [listResult, spaceResult, styleResult] = await Promise.allSettled([
      listFurnishStyles(),
      listAdminDicts('space'),
      listAdminDicts('style'),
    ])

    const list: FurnishStyle[] = listResult.status === 'fulfilled' ? listResult.value : []
    if (listResult.status === 'rejected') {
      errorText.value =
        listResult.reason instanceof Error ? listResult.reason.message : '陈设配置加载失败'
    }

    const dictsFailed = spaceResult.status === 'rejected' || styleResult.status === 'rejected'
    const spaceDict = spaceResult.status === 'fulfilled' ? spaceResult.value : []
    const styleDict = styleResult.status === 'fulfilled' ? styleResult.value : []

    const map = new Map<string, FurnishStyle>()
    let duplicated = false
    for (const item of list) {
      const key = comboKey(item.space, item.style)
      if (map.has(key)) {
        // 同一组合出现两条:只留第一条,矩阵仍只画一格
        duplicated = true
        continue
      }
      map.set(key, item)
    }
    duplicateCombo.value = duplicated
    configs.value = map

    spaces.value = buildAxis(spaceDict, list.map((item) => item.space))
    styles.value = buildAxis(styleDict, list.map((item) => item.style))

    if (dictsFailed) {
      notice.value = '字典暂时取不到,轴按已有配置生成(不影响已有配置的编辑)。'
    }
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function pick(space: string, style: string) {
  selected.value = { space, style }
  const existing = configs.value.get(comboKey(space, style))
  form.prompt = existing?.prompt ?? ''
  form.coverImage = existing?.coverImage ?? ''
  form.status = existing?.status === 'off_sale' ? 'off_sale' : 'on_sale'
  errorText.value = ''
  notice.value = ''
  fieldErrors.value = []
}

function validate(): string[] {
  return form.prompt.trim() ? [] : ['陈设提示词:必填']
}

async function save() {
  if (!selected.value) return
  notice.value = ''
  errorText.value = ''
  fieldErrors.value = validate()
  if (fieldErrors.value.length) return

  saving.value = true
  try {
    const coverImage = form.coverImage.trim()
    await upsertFurnishStyle({
      space: selected.value.space,
      style: selected.value.style,
      prompt: form.prompt.trim(),
      // 可选字段为空就**不发这个键**(契约要求省略,不要发空串/null)
      ...(coverImage ? { coverImage } : {}),
      status: form.status,
    })
    notice.value = `已保存:${selected.value.space} × ${selected.value.style}`
    await load()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function remove() {
  const config = selectedConfig.value
  if (!config || config.id === undefined) return
  if (!window.confirm('确认删除这条陈设配置?该操作不可撤销。')) return

  notice.value = ''
  errorText.value = ''
  try {
    await deleteFurnishStyle(config.id)
    notice.value = '已删除'
    selected.value = null
    await load()
  } catch (error) {
    // 30001/30002 都是服务端说得更清楚 —— 原样透出
    errorText.value = error instanceof Error ? error.message : '删除失败'
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="head">
      <h1>陈设风格</h1>
    </header>

    <p class="hint">
      按「空间 × 风格」逐格配置陈设提示词。格子点开即可编辑 ——
      <strong>一个组合只会有一个格子</strong>,不会出现重复。
    </p>

    <p v-if="duplicateCombo" class="warn" role="alert">
      服务端返回了重复的「空间 × 风格」组合,已只保留第一条显示。
    </p>
    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <ul v-if="fieldErrors.length" class="errors">
      <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
    </ul>

    <p v-if="loading" class="hint">加载中…</p>

    <template v-else-if="spaces.length && styles.length">
      <table class="matrix">
        <thead>
          <tr>
            <th class="corner">空间 ＼ 风格</th>
            <th v-for="style in styles" :key="style.key">
              {{ style.label }}
              <span v-if="style.disabled" class="mark">(已停用)</span>
              <span v-else-if="!style.inDict" class="mark">(未在字典中)</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="space in spaces" :key="space.key">
            <th scope="row">
              {{ space.label }}
              <span v-if="space.disabled" class="mark">(已停用)</span>
              <span v-else-if="!space.inDict" class="mark">(未在字典中)</span>
            </th>
            <td
              v-for="style in styles"
              :key="style.key"
              :class="[
                'cell',
                configs.has(comboKey(space.key, style.key)) ? 'cell--on' : 'cell--off',
                selected && selected.space === space.key && selected.style === style.key ? 'cell--active' : '',
              ]"
            >
              <button type="button" @click="pick(space.key, style.key)">
                {{ configs.has(comboKey(space.key, style.key)) ? '已配置' : '未配置' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <form v-if="selected" class="panel" @submit.prevent="save">
        <h2>
          {{ selected.space }} × {{ selected.style }}
          <span v-if="selectedConfig" class="tag">已配置</span>
          <span v-else class="tag tag--off">未配置</span>
        </h2>

        <label>
          陈设提示词
          <textarea v-model="form.prompt" rows="4" :readonly="!canWrite" />
        </label>

        <label>
          展示图 URL(可选)
          <input v-model="form.coverImage" type="text" :readonly="!canWrite" />
        </label>

        <label>
          状态
          <select v-model="form.status" :disabled="!canWrite">
            <option value="on_sale">上架</option>
            <option value="off_sale">下架</option>
          </select>
        </label>

        <div class="actions">
          <button v-if="canWrite" type="submit" class="primary" :disabled="saving">
            {{ saving ? '保存中…' : '保存' }}
          </button>
          <button
            v-if="canDelete && selectedConfig?.id !== undefined"
            type="button"
            class="danger"
            @click="remove"
          >
            删除这条配置
          </button>
          <button type="button" @click="selected = null">关闭</button>
        </div>
      </form>
    </template>

    <p v-else class="hint">
      还没有可配置的组合 —— 空间与风格来自「数据字典」,先去那边添加,这里就会出现对应的格子。
    </p>
  </section>
</template>

<style scoped>
.head h1 {
  margin: 0 0 0.5rem;
  font-size: 1.3rem;
}

.hint {
  color: #6b7280;
  font-size: 0.8rem;
}

.warn {
  padding: 0.5rem 0.75rem;
  border: 1px solid #fcd34d;
  border-radius: 0.25rem;
  background: #fffbeb;
  color: #92400e;
  font-size: 0.85rem;
}

.error {
  padding: 0.5rem 0.75rem;
  border: 1px solid #fca5a5;
  border-radius: 0.25rem;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 0.85rem;
}

.notice {
  padding: 0.5rem 0.75rem;
  border: 1px solid #bbf7d0;
  border-radius: 0.25rem;
  background: #f0fdf4;
  color: #166534;
  font-size: 0.85rem;
}

.errors {
  margin: 0.5rem 0;
  padding-left: 1.2rem;
  color: #b91c1c;
  font-size: 0.85rem;
}

.matrix {
  border-collapse: collapse;
  font-size: 0.85rem;
}

.matrix th,
.matrix td {
  border: 1px solid #e5e7eb;
  padding: 0.35rem 0.5rem;
  text-align: left;
}

.corner {
  background: #f9fafb;
  color: #6b7280;
  font-weight: 400;
}

.mark {
  color: #9ca3af;
  font-size: 0.7rem;
}

.cell {
  padding: 0;
}

.cell button {
  width: 100%;
  border: none;
  padding: 0.4rem 0.6rem;
  background: transparent;
  cursor: pointer;
  font: inherit;
}

.cell--on button {
  color: #047857;
}

.cell--off button {
  color: #9ca3af;
}

.cell--active {
  outline: 2px solid #1d4ed8;
  outline-offset: -2px;
}

.panel {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  padding: 1rem;
  margin-top: 1.25rem;
  max-width: 32rem;
}

.panel h2 {
  margin: 0 0 0.75rem;
  font-size: 1rem;
}

.tag {
  margin-left: 0.4rem;
  padding: 0.1rem 0.35rem;
  border-radius: 0.2rem;
  background: #ecfdf5;
  color: #047857;
  font-size: 0.75rem;
}

.tag--off {
  background: #f3f4f6;
  color: #6b7280;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  font-size: 0.85rem;
  color: #374151;
}

input,
textarea,
select {
  padding: 0.4rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

.actions button {
  padding: 0.4rem 0.9rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

button.primary {
  padding: 0.4rem 0.9rem;
  border: 1px solid #1d4ed8;
  border-radius: 0.25rem;
  background: #1d4ed8;
  color: white;
  cursor: pointer;
}

button.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

button.danger {
  border-color: #fca5a5;
  color: #b91c1c;
}
</style>
