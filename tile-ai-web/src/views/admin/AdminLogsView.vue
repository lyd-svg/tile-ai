<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { listLogs } from '@/api/admin/logs'
import type { OperationLog } from '@/api/types'

/**
 * 操作日志(F-0.2)。**只读页** —— 契约里这个分组没有写接口。
 *
 * 筛选严格对应契约的 4 个 query 参数:`adminId` / `action` / `from` / `to`。
 * 日期用 `<input type="date">`,提交时补边界时刻 —— 不补的话 `to=今天` 会漏掉
 * 今天所有**带时刻**的记录(库里是 DATETIME)。
 *
 * 菜单入口由 `log:read` 门控(super_admin / operator)。页面本身没有写按钮,
 * 所以不需要再按权限点隐藏什么;editor 根本进不来。
 */
const rows = ref<OperationLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const adminIdInput = ref('')
const action = ref('')
const fromDate = ref('')
const toDate = ref('')

const loading = ref(false)
const errorText = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)

/** 纯日期补边界时刻:不补的话 `to` 会漏掉当天带时刻的记录。 */
function boundaryOf(value: string, endOfDay: boolean): string | undefined {
  if (!value) return undefined
  return endOfDay ? `${value}T23:59:59` : `${value}T00:00:00`
}

/** 操作人展示。**账号被物理删掉时后端给 null** —— 兜底显示"已删除账号 #N",不留空白。 */
function operatorOf(row: OperationLog): string {
  if (row.adminUsername) return row.adminUsername
  if (row.adminId === undefined || row.adminId === null) return '—'
  return `已删除账号 #${row.adminId}`
}

/**
 * 详情展示:后端已经把它从 JSON 文本还原成**对象**了。
 * 没有详情(或后端解析失败)时是 null —— 画个破折号,不画 `[object Object]`。
 */
function detailOf(row: OperationLog): string {
  const detail = row.detail
  if (!detail) return '—'
  const method = typeof detail.method === 'string' ? detail.method : ''
  const path = typeof detail.path === 'string' ? detail.path : ''
  const composed = `${method} ${path}`.trim()
  return composed || JSON.stringify(detail)
}

/** 目标展示:`类型#ID`;两边都可能缺(比如新建类操作没有 targetId)。 */
function targetOf(row: OperationLog): string {
  const type = row.targetType
  const id = row.targetId
  if (type && id) return `${type}#${id}`
  return type || (id ? `#${id}` : '—')
}

/** 与「我的方案」同款:没有公共格式化工具,项目里就是这么写的。 */
function timeOf(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const parsedId = adminIdInput.value.trim() === '' ? undefined : Number(adminIdInput.value.trim())
    const data = await listLogs({
      adminId: Number.isFinite(parsedId) ? parsedId : undefined,
      action: action.value.trim() || undefined,
      from: boundaryOf(fromDate.value, false),
      to: boundaryOf(toDate.value, true),
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

/** 筛选条件变了要回到第 1 页 —— 否则停在第 5 页而结果只有 1 页时是空白。 */
function search() {
  page.value = 1
  load()
}

function goPage(target: number) {
  if (target < 1 || target > totalPages.value) return
  page.value = target
  load()
}

onMounted(load)
</script>

<template>
  <section>
    <header class="head">
      <h1>操作日志</h1>
    </header>

    <form class="filters" @submit.prevent="search">
      <label>
        操作人 ID
        <input v-model="adminIdInput" type="number" min="1" placeholder="如 1" />
      </label>
      <label>
        动作
        <input v-model="action" type="text" placeholder="如 product.delete" />
      </label>
      <label>
        开始日期
        <input v-model="fromDate" type="date" />
      </label>
      <label>
        结束日期
        <input v-model="toDate" type="date" />
      </label>
      <button type="submit">查询</button>
    </form>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>

    <p v-if="loading" class="hint">加载中…</p>

    <table v-else-if="rows.length">
      <thead>
        <tr>
          <th>时间</th>
          <th>操作人</th>
          <th>动作</th>
          <th>目标</th>
          <th>IP</th>
          <th>详情</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ timeOf(row.createdAt) }}</td>
          <td>{{ operatorOf(row) }}</td>
          <td>{{ row.action }}</td>
          <td>{{ targetOf(row) }}</td>
          <td>{{ row.ip || '—' }}</td>
          <td>{{ detailOf(row) }}</td>
        </tr>
      </tbody>
    </table>

    <p v-else class="hint">没有符合条件的日志。</p>

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
  justify-content: space-between;
  align-items: center;
}

h1 {
  margin: 0;
  font-size: 1.15rem;
}

.filters {
  display: flex;
  gap: 1rem;
  align-items: flex-end;
  flex-wrap: wrap;
  margin: 1rem 0;
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}

input,
select {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
}

button {
  padding: 0.35rem 0.7rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

button:disabled {
  opacity: 0.5;
  cursor: default;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 0.5rem;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}

.actions {
  display: flex;
  gap: 0.4rem;
}

.pager {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-top: 1rem;
}

.error {
  color: #b91c1c;
}

.hint {
  color: #6b7280;
}
</style>
