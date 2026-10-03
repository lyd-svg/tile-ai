<script setup lang="ts">
import { Button, Input, Select, Skeleton } from 'ant-design-vue'
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

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <table v-else-if="rows.length">
      <thead>
        <tr>
          <th>编号</th>
          <th>名称</th>
          <th>材质</th>
          <th>规格</th>
          <th class="num">单价(元/㎡)</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.sku">
          <td>{{ row.sku }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.material || '—' }}</td>
          <td>{{ row.spec || '—' }}</td>
          <td class="num">{{ row.pricePerSqm }}</td>
          <td>
            <span :class="['tag', row.status === 'on_sale' ? 'tag--on' : 'tag--off']">
              {{ row.status === 'on_sale' ? '已上架' : '已下架' }}
            </span>
          </td>
          <td class="actions">
            <button v-if="canWrite" type="button" @click="edit(row)">编辑</button>
            <button v-if="canPublish" type="button" @click="toggleStatus(row)">
              {{ row.status === 'on_sale' ? '下架' : '上架' }}
            </button>
            <button v-if="canDelete" type="button" class="danger" @click="remove(row)">
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>

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

/* ── 表格里的操作按钮**仍是原生的** ────────────────────────────────────────
   ⚠️ 它们**暂时不能迁 antd**:测试按文案找它们(`toContain('编辑')`),
   而测试**不挂 `App.vue`**、拿不到"两个汉字不插空格"那条设置
   ⇒ antd 会渲染成「编 辑」,断言**直接红**。
   要迁就得先让那些测试**归一化空白**。
   ⇒ 所以样式**收窄到 `.actions`**,不波及其他按钮。 */
.actions button {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.actions button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.actions button:disabled {
  opacity: 0.45;
  cursor: default;
}

/* 删除是危险操作 —— 危险色 + 明确的文字,不靠图标暗示 */
.danger {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.danger:hover:not(:disabled) {
  background: var(--color-danger-soft);
  border-color: var(--color-danger);
  color: var(--color-danger);
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

/* 数字列右对齐 —— 单价要能竖着比大小 */
.num {
  text-align: right;
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
