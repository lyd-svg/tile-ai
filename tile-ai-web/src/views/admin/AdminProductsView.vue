<script setup lang="ts">
import { Alert, Button, Input, Select, Skeleton, Table } from 'ant-design-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { BusinessError } from '@/api/errors'
import { deleteProduct, listProducts, setProductStatus } from '@/api/admin/products'
import type { Sku } from '@/api/admin/products'
import type { ProductStatus } from '@/api/types'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { appConfirm } from '@/components/base/AppConfirm'

/**
 * 产品库列表(F-0.3)。
 *
 * 筛选只做契约里的两个参数:`keyword` 与 `status`。
 * **按钮按权限点显示**(架构 §4.4):editor 能进这页,但没有删除与上下架权限,
 * 所以那两个按钮对它不渲染 —— 别给出点了必然 403 的入口。
 * 这不是安全边界,真正的拦截在服务端。
 */
const auth = useAdminAuthStore()
const router = useRouter()

const rows = ref<Sku[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const status = ref<ProductStatus | ''>('')

const loading = ref(false)
const errorText = ref('')
const notice = ref('')

const canWrite = computed(() => auth.has('product:write'))
const canDelete = computed(() => auth.has('product:delete'))
const canPublish = computed(() => auth.has('product:publish'))

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listProducts({
      keyword: keyword.value || undefined,
      status: status.value || undefined,
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

async function toggleStatus(row: Sku) {
  if (row.id === undefined) return
  const next: ProductStatus = row.status === 'on_sale' ? 'off_sale' : 'on_sale'
  notice.value = ''
  errorText.value = ''
  try {
    await setProductStatus(row.id, next)
    notice.value = `已${next === 'on_sale' ? '上架' : '下架'}:${row.sku}`
    await load()
  } catch (error) {
    // 30002「状态不允许该操作」是重复上下架的预期结果 —— 原样透出,
    // 让用户知道是"状态没变"而不是"操作失败"
    errorText.value = error instanceof Error ? error.message : '操作失败'
  }
}

async function remove(row: Sku) {
  if (row.id === undefined) return
  // ⚠️ **必须 `await`** —— 忘了的话拿到的是个恒真的 Promise,确认框还没点就删了
  // (静默错误,不报错)。见 `components/base/AppConfirm.ts` 的文件头。
  if (
    !(await appConfirm({
      title: '确认删除',
      content: `确认删除 ${row.sku}?该操作不可撤销。`,
      okText: '删除',
      danger: true,
    }))
  ) {
    return
  }

  notice.value = ''
  errorText.value = ''
  try {
    await deleteProduct(row.id)
    notice.value = `已删除:${row.sku}`
    await load()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '删除失败'
  }
}

function edit(row: Sku) {
  if (row.id === undefined) return
  router.push({ name: 'admin-product-edit', params: { id: String(row.id) } })
}

function createNew() {
  router.push({ name: 'admin-product-new' })
}

onMounted(load)
</script>

<template>
  <section>
    <header class="head">
      <h1>产品库</h1>
      <Button v-if="canWrite" type="primary" @click="createNew">新增 SKU</Button>
    </header>

    <form class="filters" @submit.prevent="search">
      <label>
        关键词
        <Input v-model:value="keyword" placeholder="名称或编号" />
      </label>
      <label>
        状态
        <Select v-model:value="status" placeholder="全部" allow-clear>
          <Select.Option value="on_sale">已上架</Select.Option>
          <Select.Option value="off_sale">已下架</Select.Option>
        </Select>
      </label>
      <Button html-type="submit">查询</Button>
    </form>

    <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
    <Alert v-if="notice" class="alert-gap" type="success" :message="notice" show-icon />

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <Table
      v-else-if="rows.length"
      class="sku-table"
      size="small"
      :data-source="rows"
      :pagination="false"
      row-key="sku"
    >
      <Table.Column title="编号" key="sku" />
      <Table.Column title="名称" key="name" />
      <Table.Column title="材质" key="material" />
      <Table.Column title="规格" key="spec" />
      <Table.Column title="单价(元/㎡)" key="price" align="right" />
      <Table.Column title="状态" key="status" />
      <Table.Column title="操作" key="actions" />

      <!-- ⚠️ `record as Sku` —— `bodyCell` 的行是 `Record<string, any>`,
           而 `edit` / `toggleStatus` / `remove` 收的是 `Sku`。断言只写在模板里。 -->
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'sku'">{{ record.sku }}</template>
        <template v-else-if="column.key === 'name'">{{ record.name }}</template>
        <template v-else-if="column.key === 'material'">{{ record.material || '—' }}</template>
        <template v-else-if="column.key === 'spec'">{{ record.spec || '—' }}</template>
        <template v-else-if="column.key === 'price'">
          <span class="num">{{ record.pricePerSqm }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <span :class="['tag', record.status === 'on_sale' ? 'tag--on' : 'tag--off']">
            {{ record.status === 'on_sale' ? '已上架' : '已下架' }}
          </span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <div class="actions">
            <Button v-if="canWrite" @click="edit(record as Sku)">编辑</Button>
            <Button v-if="canPublish" @click="toggleStatus(record as Sku)">
              {{ record.status === 'on_sale' ? '下架' : '上架' }}
            </Button>
            <Button v-if="canDelete" danger @click="remove(record as Sku)">删除</Button>
          </div>
        </template>
      </template>
    </Table>

    <p v-else class="hint">没有符合条件的 SKU。</p>

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
   测试逐字断言按钮文案(`编辑` / `删除` / `上架` / `下架` / `新增 SKU`)、
   `共 N 条`、`没有符合条件的 SKU` 与 `[role="alert"]`,动结构风险高于收益。
   后台主题由 `App.vue` 的 `data-theme="admin"` 提供(更紧凑、圆角更小)。 */

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
  gap: var(--space-3);
  align-items: flex-end;
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

/* ⚠️ 筛选区的输入框 / 下拉 / 按钮**交给 antd** —— 见 `tile-ai-design.md` §5.6。
   裸元素选择器(`input` / `select` / `button`)会**照样命中 antd 渲染出的元素**,
   所以这里只留排版。 */
.filters :deep(.ant-input),
.filters :deep(.ant-select) {
  width: 100%;
}

/* ── 表格里的操作按钮**已迁 antd**(2026-10-03)────────────────────────────
   原先它们不能迁:antd 会给「两个汉字」的按钮插空格(「编辑」→「编 辑」),
   而测试不挂 `App.vue`、拿不到那条设置 ⇒ **按文案找按钮的断言会静默失败**。
   现在测试侧的比对**统一归一化空白**了(`tests/helpers/antd.ts` 的 `normalize`),
   这道坎没了 —— 所以**不再给它们写样式**,外观交给 antd 与主题令牌。
   见 `openapi-verification.md` §70.9。 */

/* ── 表格 ───────────────────────────────────────────────────────────────── */
/* ⚠️ 已迁 antd `Table`(§72)。原来那套裸 `table` / `th` / `td` / `tbody tr:hover`
   必须删掉 —— antd 渲染出来的**正是**这些标签(§5.6 同一个坑)。 */

/* 数字列右对齐 —— 对齐由列的 `align="right"` 负责,这里只留"数字等宽" */
.num {
  font-variant-numeric: tabular-nums;
}

.actions {
  display: flex;
  gap: var(--space-1);
}

/* 状态标签:上架=暖绿、下架=中性灰。**都不靠红色** —— 下架不是错误 */
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
