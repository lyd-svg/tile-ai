<script setup lang="ts">
import { Button, Skeleton } from 'ant-design-vue'
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
      <Button html-type="submit">查询</Button>
    </form>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

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
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试靠 `findAll('input[type="date"]')` 与 `find('form')` 定位,所以
   日期输入必须是 `type="date"`、筛选必须是同一个 `form`。
   ⚠️ **只读页**(契约里这组没有写接口)—— 加了写入口才是错。 */

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
}

h1 {
  margin: 0;
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 700;
  color: var(--color-text);
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

input,
select {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

input:focus-visible,
select:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
/* ⚠️ **筛选区的「查询」已迁 antd** —— 下面**只剩分页按钮**是原生的。
   原来是裸 `button {}`,那会**照样命中 antd 的 `<button class="ant-btn">`**
   ⇒ **必须收窄到 `.pager`**(`tile-ai-design.md` §5.6)。 */
.pager button {
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

.pager button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.pager button:disabled {
  opacity: 0.45;
  cursor: default;
}

/* ── 日志表 ─────────────────────────────────────────────────────────────── */
table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  font-size: var(--text-sm);
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

/* 时间与动作码是"要看清楚"的两列 —— 等宽数字/等宽字体便于竖着扫 */
td:first-child,
td:nth-child(3) {
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--text-xs);
}

.actions {
  display: flex;
  gap: var(--space-1);
}

/* ── 分页 ───────────────────────────────────────────────────────────────── */
.pager {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  justify-content: center;
  margin-top: var(--space-5);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
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

.hint {
  margin: var(--space-4) 0;
  padding: var(--space-5);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-align: center;
}
</style>
