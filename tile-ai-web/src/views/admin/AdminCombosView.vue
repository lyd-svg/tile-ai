<script setup lang="ts">
import { Alert, Button, Select, Skeleton, Table } from 'ant-design-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { deleteCombo, listCombos } from '@/api/admin/combos'
import { listAdminDicts } from '@/api/admin/dicts'
import type { Dict } from '@/api/admin/dicts'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { appConfirm } from '@/components/base/AppConfirm'
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
  if (
    !(await appConfirm({
      title: '确认删除搭配',
      content: `确认删除「${row.name}」?该操作不可撤销。`,
      okText: '删除',
      danger: true,
    }))
  ) {
    return
  }
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
      <Button v-if="canWrite" type="primary" @click="createNew">新增搭配</Button>
    </header>

    <form class="filters" @submit.prevent="search">
      <label>
        空间
        <Select v-model:value="space" placeholder="全部" allow-clear>
          <Select.Option v-for="item in spaceOptions" :key="item.key" :value="item.key">
            {{ item.label }}
          </Select.Option>
        </Select>
      </label>
      <label>
        风格
        <Select v-model:value="style" placeholder="全部" allow-clear>
          <Select.Option v-for="item in styleOptions" :key="item.key" :value="item.key">
            {{ item.label }}
          </Select.Option>
        </Select>
      </label>
      <Button html-type="submit">查询</Button>
    </form>

    <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
    <Alert v-if="notice" class="alert-gap" type="success" :message="notice" show-icon />

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <Table
      v-else-if="rows.length"
      class="combo-table"
      size="small"
      :data-source="rows"
      :pagination="false"
      row-key="id"
    >
      <Table.Column title="名称" key="name" />
      <Table.Column title="空间" key="space" />
      <Table.Column title="风格" key="style" />
      <Table.Column title="参考价区间(元)" key="price" align="right" />
      <Table.Column title="状态" key="status" />
      <Table.Column title="操作" key="actions" />

      <!-- ⚠️ `record as Combo` —— `bodyCell` 的行是 `Record<string, any>`,
           而下面这些函数收的是 `Combo`。断言只写在模板里,`<script>` 不动。 -->
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">{{ record.name }}</template>
        <template v-else-if="column.key === 'space'">
          {{ labelOf(spaceLabels, record.space) }}
        </template>
        <template v-else-if="column.key === 'style'">
          {{ labelOf(styleLabels, record.style) }}
        </template>
        <template v-else-if="column.key === 'price'">
          <span class="num">{{ priceRangeOf(record as Combo) }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <span v-if="!record.status" class="hint">—</span>
          <span v-else :class="['tag', record.status === 'on_sale' ? 'tag--on' : 'tag--off']">
            {{ record.status === 'on_sale' ? '已上架' : '已下架' }}
          </span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <div class="actions">
            <Button v-if="canWrite" @click="edit(record as Combo)">编辑</Button>
            <Button v-if="canDelete" danger @click="remove(record as Combo)">删除</Button>
          </div>
        </template>
      </template>
    </Table>

    <p v-else class="hint">没有符合条件的搭配。</p>

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
   ⚠️ 本页**列表上不做上下架按钮**(契约没有 PATCH status,toggle 会覆盖别的字段)——
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

.filters select {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */


/* ── 表格 ───────────────────────────────────────────────────────────────── */
/* ⚠️ 已迁 antd `Table`(§72)。原来那套裸 `table` / `th` / `td` / `tbody tr:hover`
   必须删掉 —— antd 渲染出来的**正是**这些标签(§5.6 同一个坑)。 */

/* 参考价区间右对齐 —— 对齐由列的 `align="right"` 负责,这里只留"数字等宽" */
.num {
  font-variant-numeric: tabular-nums;
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

/* ⚠️ **行内操作按钮(编辑 / 删除)与表头「新增搭配」已迁 antd** ——
   不再给它们写样式,外观交给库与主题令牌(见 `openapi-verification.md` §70.9)。
   下面保留的是**仍然是原生**的那两组:**分页按钮**、筛选区的**「查询」**。 */
.pager button {
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

.pager button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
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
