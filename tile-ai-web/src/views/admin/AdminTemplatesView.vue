<script setup lang="ts">
import { Alert, Button, Skeleton, Table } from 'ant-design-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { deleteTemplate, listTemplates } from '@/api/admin/templates'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { appConfirm } from '@/components/base/AppConfirm'
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
  if (
    !(await appConfirm({
      title: '确认删除户型模板',
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
      <Button v-if="canWrite" type="primary" @click="createNew">新增模板</Button>
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
      <Button html-type="submit">查询</Button>
    </form>

    <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
    <Alert v-if="notice" class="alert-gap" type="success" :message="notice" show-icon />

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <Table
      v-else-if="rows.length"
      class="template-table"
      size="small"
      :data-source="rows"
      :pagination="false"
      row-key="id"
    >
      <Table.Column title="名称" key="name" />
      <Table.Column title="居室数" key="roomCount" align="right" />
      <Table.Column title="面积区间" key="areaRange" />
      <Table.Column title="结构" key="structure" />
      <Table.Column title="状态" key="status" />
      <Table.Column title="操作" key="actions" />

      <!-- ⚠️ `record as UnitTemplate`:antd 把 `bodyCell` 的行标成 `Record<string, any>`,
           而 `edit` / `remove` 收的是 `UnitTemplate`。断言只写在模板里,`<script>` 不动。 -->
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">{{ record.name }}</template>
        <template v-else-if="column.key === 'roomCount'">
          <span class="num">{{ record.roomCount ?? '—' }}</span>
        </template>
        <template v-else-if="column.key === 'areaRange'">{{ record.areaRange || '—' }}</template>
        <template v-else-if="column.key === 'structure'">{{ record.structure || '—' }}</template>
        <template v-else-if="column.key === 'status'">
          <span v-if="!record.status" class="hint">—</span>
          <span v-else :class="['tag', record.status === 'on_sale' ? 'tag--on' : 'tag--off']">
            {{ record.status === 'on_sale' ? '已上架' : '已下架' }}
          </span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <div class="actions">
            <Button v-if="canWrite" @click="edit(record as UnitTemplate)">编辑</Button>
            <Button v-if="canDelete" danger @click="remove(record as UnitTemplate)">删除</Button>
          </div>
        </template>
      </template>
    </Table>

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


/* ── 表格 ───────────────────────────────────────────────────────────────── */
/* ⚠️ 已迁 antd `Table`(§72)。原来那套裸 `table` / `th` / `td` / `tbody tr:hover`
   必须删掉 —— antd 渲染出来的**正是**这些标签(§5.6 同一个坑)。 */

/* 居室数右对齐 —— 数字要能竖着比。对齐本身由列的 `align="right"` 负责,
   这里只保留"数字等宽"(原来挂在裸 `table` 上,现在收窄到这一列)。 */
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

/* ⚠️ **行内操作按钮与表头「新增模板」已迁 antd** —— 不再给它们写样式(§70.9)。
   下面保留的是**仍是原生**的两组:分页按钮、筛选区的「查询」。 */
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
