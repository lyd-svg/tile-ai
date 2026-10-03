<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { deleteTemplate, listTemplates } from '@/api/admin/templates'
import { useAdminAuthStore } from '@/stores/adminAuth'
import type { UnitTemplate } from '@/api/types'

/**
 * 户型模板列表(F-0.6)。
 *
 * 三个筛选参数在契约里是**精确匹配**(PRD §5.3.1),不是模糊搜索 —— 所以占位文案
 * 也照实写"精确匹配",免得用户按关键词搜不到还以为坏了。
 *
 * ⚠️ 列表上**不做"上下架"按钮**:契约没有独立的上下架接口,做 toggle 就得
 * "先取详情再整体 PUT",会覆盖别的字段。上下架在编辑表单里表达。
 */
const auth = useAdminAuthStore()
const router = useRouter()

const rows = ref<UnitTemplate[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const roomCount = ref('')
const areaRange = ref('')
const structure = ref('')

const loading = ref(false)
const errorText = ref('')
const notice = ref('')

const canWrite = computed(() => auth.has('template:write'))
const canDelete = computed(() => auth.has('template:delete'))

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const parsedRoomCount = roomCount.value.trim() === '' ? undefined : Number(roomCount.value)
    const data = await listTemplates({
      roomCount: parsedRoomCount === undefined || Number.isFinite(parsedRoomCount) ? parsedRoomCount : undefined,
      areaRange: areaRange.value.trim() || undefined,
      structure: structure.value.trim() || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function goPage(target: number) {
  if (target < 1 || target > totalPages.value) return
  page.value = target
  load()
}

async function remove(row: UnitTemplate) {
  if (!window.confirm(`确认删除户型模板「${row.name}」?该操作不可撤销。`)) return
  notice.value = ''
  errorText.value = ''
  try {
    await deleteTemplate(row.id)
    notice.value = `已删除:${row.name}`
    await load()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '删除失败'
  }
}

function edit(row: UnitTemplate) {
  router.push({ name: 'admin-template-edit', params: { id: String(row.id) } })
}

function createNew() {
  router.push({ name: 'admin-template-new' })
}

onMounted(load)
</script>

<template>
  <section>
    <header class="head">
      <h1>户型模板</h1>
      <button v-if="canWrite" type="button" class="primary" @click="createNew">新增模板</button>
    </header>

    <form class="filters" @submit.prevent="search">
      <label>
        居室数
        <input v-model="roomCount" type="number" placeholder="精确匹配" />
      </label>
      <label>
        面积区间
        <input v-model="areaRange" type="text" placeholder="如 80-100㎡(精确匹配)" />
      </label>
      <label>
        户型结构
        <input v-model="structure" type="text" placeholder="如 三室两厅(精确匹配)" />
      </label>
      <button type="submit">查询</button>
    </form>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <p v-if="loading" class="hint">加载中…</p>

    <table v-else-if="rows.length">
      <thead>
        <tr>
          <th>名称</th>
          <th class="num">居室数</th>
          <th>面积区间</th>
          <th>结构</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="num">{{ row.roomCount ?? '—' }}</td>
          <td>{{ row.areaRange || '—' }}</td>
          <td>{{ row.structure || '—' }}</td>
          <td>
            <span v-if="!row.status" class="hint">—</span>
            <span v-else :class="['tag', row.status === 'on_sale' ? 'tag--on' : 'tag--off']">
              {{ row.status === 'on_sale' ? '已上架' : '已下架' }}
            </span>
          </td>
          <td class="actions">
            <button v-if="canWrite" type="button" @click="edit(row)">编辑</button>
            <button v-if="canDelete" type="button" class="danger" @click="remove(row)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-else class="hint">没有符合条件的户型模板。</p>

    <footer v-if="total > 0" class="pager">
      <button type="button" :disabled="!hasPrev" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页,共 {{ total }} 条</span>
      <button type="button" :disabled="!hasNext" @click="goPage(page + 1)">下一页</button>
    </footer>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块。
   ⚠️ 列表上**不做上下架按钮**(契约没有独立接口,toggle 会覆盖别的字段)——
   那是逻辑取舍,样式改动不碰它。 */

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.head h1 {
  margin: 0 0 var(--space-1);
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

/* ── 筛选 ───────────────────────────────────────────────────────────────── */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
  margin: var(--space-4) 0;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

.filters input {
  width: 12rem;
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.filters input:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
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

/* ── 表格 ───────────────────────────────────────────────────────────────── */
table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}

th,
td {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--color-border);
  text-align: left;
}

th {
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
}

tbody tr:hover {
  background: var(--color-bg);
}

/* 居室数右对齐 —— 数字要能竖着比 */
.num {
  text-align: right;
}

/* 状态标签:上架=暖绿、下架=中性灰。**下架不是错误,不用红色** */
.tag {
  display: inline-block;
  padding: 0.05rem var(--space-2);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  white-space: nowrap;
}

.tag--on {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.tag--off {
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
}

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
.actions {
  display: flex;
  gap: var(--space-1);
}

.actions button,
.pager button,
.filters button {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard);
}

.actions button:hover:not(:disabled),
.pager button:hover:not(:disabled),
.filters button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* 删除是危险操作 —— 危险色 + 明确的文字 */
.actions button.danger {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.actions button.danger:hover:not(:disabled) {
  background: var(--color-danger-soft);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

button.primary {
  height: var(--control-height);
  padding: 0 var(--space-4);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
}

button.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

/* ── 分页 ───────────────────────────────────────────────────────────────── */
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  margin-top: var(--space-5);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.pager button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
