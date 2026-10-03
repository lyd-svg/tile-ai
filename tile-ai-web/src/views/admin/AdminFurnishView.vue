<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import { listAdminDicts } from '@/api/admin/dicts'
import type { Dict } from '@/api/admin/dicts'
import { deleteFurnishStyle, listFurnishStyles, upsertFurnishStyle } from '@/api/admin/furnish'
import type { FurnishStyle } from '@/api/admin/furnish'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { appConfirm } from '@/components/base/AppConfirm'
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
  if (
    !(await appConfirm({
      title: '确认删除陈设配置',
      content: '确认删除这条陈设配置?该操作不可撤销。',
      okText: '删除',
      danger: true,
    }))
  ) {
    return
  }

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
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试依赖 `findAll('.cell')` 的**个数**、每个 cell 内的按钮文案、
   `find('form')` 里恰好 1 个 `input[type="text"]` / 1 个 `textarea` / 1 个 `select`。
   ⇒ **矩阵与表单的元素数量不能变**,样式改动不碰它们。 */

.head h1 {
  margin: 0 0 var(--space-2);
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 700;
  color: var(--color-text);
}

.hint {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}

.hint strong {
  color: var(--color-text-secondary);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
.warn {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-warning);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-text);
  font-size: var(--text-sm);
}

.error {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

.notice {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-success);
  border-radius: var(--radius-sm);
  background: var(--color-success-soft);
  color: var(--color-success);
  font-size: var(--text-sm);
}

.errors {
  margin: var(--space-3) 0;
  padding-left: var(--space-5);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

/* ── 空间 × 风格矩阵 ────────────────────────────────────────────────────── */
.matrix {
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--text-sm);
}

.matrix th,
.matrix td {
  padding: var(--space-2) var(--space-3);
  border-right: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  text-align: left;
}

/* 左上角那格是"轴标签",不是数据 —— 弱化它 */
.corner {
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
  font-weight: 400;
  font-size: var(--text-xs);
}

/* 行头 / 列头:让轴站得住 */
.matrix thead th {
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
}

.matrix tbody th {
  background: var(--color-surface);
  color: var(--color-text);
  font-weight: 600;
  white-space: nowrap;
}

.mark {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  font-weight: 400;
}

/* 格子本身不带内边距 —— 里面那个按钮要铺满整格,点哪儿都算点了这一格 */
.cell {
  padding: 0;
  border-right: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.cell button {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: var(--text-sm);
  transition: background var(--dur-fast) var(--ease-standard);
}

.cell button:hover {
  background: var(--color-bg);
}

/* 已配置 = 暖绿(**不是"成功"语义,只是"这儿有东西"**);未配置 = 弱化灰 */
.cell--on button {
  color: var(--color-success);
  font-weight: 600;
}

.cell--off button {
  color: var(--color-text-muted);
}

/* 当前选中的那一格 —— 用主色描边,与主题一致 */
.cell--active {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* ── 编辑面板 ───────────────────────────────────────────────────────────── */
.panel {
  max-width: 32rem;
  margin-top: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.panel h2 {
  margin: 0 0 var(--space-4);
  font-size: var(--text-base);
  line-height: var(--leading-base);
  font-weight: 600;
  color: var(--color-text);
}

.tag {
  margin-left: var(--space-2);
  padding: 0.05rem var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-success-soft);
  color: var(--color-success);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
}

.tag--off {
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-bottom: var(--space-4);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

input,
textarea,
select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

input,
select {
  height: var(--control-height);
}

textarea {
  resize: vertical;
}

input:focus-visible,
textarea:focus-visible,
select:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

/* 无权限时是 readonly —— 浅底提示"改不了",别让它看起来可编辑 */
input[readonly],
textarea[readonly] {
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
}

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
.actions {
  display: flex;
  gap: var(--space-2);
}

.actions button {
  height: var(--control-height);
  padding: 0 var(--space-4);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard);
}

.actions button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

button.primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: 600;
}

button.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
  color: var(--color-on-primary);
}

button.primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* 删除是危险操作 —— 危险色 + 明确的文字 */
button.danger {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

button.danger:hover:not(:disabled) {
  background: var(--color-danger-soft);
  border-color: var(--color-danger);
  color: var(--color-danger);
}
</style>
