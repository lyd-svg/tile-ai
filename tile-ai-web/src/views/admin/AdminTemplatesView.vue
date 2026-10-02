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
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.head h1 {
  margin: 0 0 0.5rem;
  font-size: 1.3rem;
}

.hint {
  color: #6b7280;
  font-size: 0.8rem;
}

.filters {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  margin: 0.75rem 0;
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #374151;
}

.filters input {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
  width: 12rem;
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

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.5rem;
  text-align: left;
}

th {
  color: #374151;
  font-weight: 600;
}

.num {
  text-align: right;
}

.tag {
  padding: 0.1rem 0.35rem;
  border-radius: 0.2rem;
  font-size: 0.75rem;
}

.tag--on {
  background: #ecfdf5;
  color: #047857;
}

.tag--off {
  background: #f3f4f6;
  color: #6b7280;
}

.actions {
  display: flex;
  gap: 0.4rem;
}

.actions button,
.pager button,
.filters button {
  padding: 0.3rem 0.7rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

.actions button.danger {
  border-color: #fca5a5;
  color: #b91c1c;
}

button.primary {
  padding: 0.5rem 1rem;
  border: 1px solid #1d4ed8;
  border-radius: 0.25rem;
  background: #1d4ed8;
  color: white;
  cursor: pointer;
}

.pager {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 1rem;
  font-size: 0.85rem;
  color: #374151;
}

.pager button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
