<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { deleteCombo, listCombos } from '@/api/admin/combos'
import { listAdminDicts } from '@/api/admin/dicts'
import type { Dict } from '@/api/admin/dicts'
import { useAdminAuthStore } from '@/stores/adminAuth'
import type { Combo } from '@/api/types'

/**
 * 推荐搭配列表(F-0.5)。
 *
 * 筛选的两个下拉**来自字典**(空间 / 风格由后台维护),不硬编码。
 *
 * ⚠️ **列表上不做"上下架"按钮**:契约没有 `PATCH /combos/{id}/status`,
 * 要做 toggle 就得"先取详情再整体 PUT" —— 那会**把别的字段一起覆盖掉**。
 * 上下架只在编辑表单里表达(和其他字段一起提交)。
 */
const auth = useAdminAuthStore()
const router = useRouter()

const rows = ref<Combo[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const space = ref('')
const style = ref('')

const spaceOptions = ref<Dict[]>([])
const styleOptions = ref<Dict[]>([])

const loading = ref(false)
const errorText = ref('')
const notice = ref('')

const canWrite = computed(() => auth.has('combo:write'))
const canDelete = computed(() => auth.has('combo:delete'))

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)

const spaceLabels = computed(() =>
  Object.fromEntries(spaceOptions.value.map((item) => [item.key, item.label])),
)
const styleLabels = computed(() =>
  Object.fromEntries(styleOptions.value.map((item) => [item.key, item.label])),
)

function labelOf(map: Record<string, string>, key: string | undefined): string {
  // 字典里查不到就退回显示 key —— 字典项可能被删,但搭配还在引用它
  return key ? (map[key] ?? key) : '—'
}

function priceRangeOf(row: Combo): string {
  const min = row.refPriceMin
  const max = row.refPriceMax
  if (min === undefined && max === undefined) return '—'
  return `${min ?? '—'} ~ ${max ?? '—'}`
}

async function loadDicts() {
  // 字典取不到不该让列表不可用 —— 分开兜底
  const [spaces, styles] = await Promise.allSettled([listAdminDicts('space'), listAdminDicts('style')])
  if (spaces.status === 'fulfilled') spaceOptions.value = spaces.value
  if (styles.status === 'fulfilled') styleOptions.value = styles.value
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listCombos({
      space: space.value || undefined,
      style: style.value || undefined,
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

/** 筛选变了要回第 1 页 —— 否则停在第 5 页而结果只有 1 页时是空白 */
function search() {
  page.value = 1
  load()
}

function goPage(target: number) {
  if (target < 1 || target > totalPages.value) return
  page.value = target
  load()
}

async function remove(row: Combo) {
  if (!window.confirm(`确认删除搭配「${row.name}」?该操作不可撤销。`)) return
  notice.value = ''
  errorText.value = ''
  try {
    await deleteCombo(row.id)
    notice.value = `已删除:${row.name}`
    await load()
  } catch (error) {
    // 被方案引用时服务端回 30002 并说明原因 —— 原样透出
    errorText.value = error instanceof Error ? error.message : '删除失败'
  }
}

function edit(row: Combo) {
  router.push({ name: 'admin-combo-edit', params: { id: String(row.id) } })
}

function createNew() {
  router.push({ name: 'admin-combo-new' })
}

onMounted(async () => {
  await Promise.all([loadDicts(), load()])
})
</script>

<template>
  <section>
    <header class="head">
      <h1>推荐搭配</h1>
      <button v-if="canWrite" type="button" class="primary" @click="createNew">新增搭配</button>
    </header>

    <form class="filters" @submit.prevent="search">
      <label>
        空间
        <select v-model="space">
          <option value="">全部</option>
          <option v-for="item in spaceOptions" :key="item.key" :value="item.key">
            {{ item.label }}
          </option>
        </select>
      </label>
      <label>
        风格
        <select v-model="style">
          <option value="">全部</option>
          <option v-for="item in styleOptions" :key="item.key" :value="item.key">
            {{ item.label }}
          </option>
        </select>
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
          <th>空间</th>
          <th>风格</th>
          <th>参考价区间(元)</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.name }}</td>
          <td>{{ labelOf(spaceLabels, row.space) }}</td>
          <td>{{ labelOf(styleLabels, row.style) }}</td>
          <td class="num">{{ priceRangeOf(row) }}</td>
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

    <p v-else class="hint">没有符合条件的搭配。</p>

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

.filters select {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
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
