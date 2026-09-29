<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { BusinessError } from '@/api/errors'
import { deleteProduct, listProducts, setProductStatus } from '@/api/admin/products'
import type { Sku } from '@/api/admin/products'
import type { ProductStatus } from '@/api/types'
import { useAdminAuthStore } from '@/stores/adminAuth'

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
  if (!window.confirm(`确认删除 ${row.sku}?该操作不可撤销。`)) return

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
      <button v-if="canWrite" type="button" class="primary" @click="createNew">新增 SKU</button>
    </header>

    <form class="filters" @submit.prevent="search">
      <label>
        关键词
        <input v-model="keyword" type="text" placeholder="名称或编号" />
      </label>
      <label>
        状态
        <select v-model="status">
          <option value="">全部</option>
          <option value="on_sale">已上架</option>
          <option value="off_sale">已下架</option>
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

.primary {
  background: #1d4ed8;
  border-color: #1d4ed8;
  color: white;
}

.danger {
  color: #b91c1c;
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

.num {
  text-align: right;
}

.actions {
  display: flex;
  gap: 0.4rem;
}

.tag {
  padding: 0.1rem 0.4rem;
  border-radius: 0.25rem;
  font-size: 0.8rem;
}

.tag--on {
  background: #ecfdf5;
  color: #047857;
}

.tag--off {
  background: #f3f4f6;
  color: #6b7280;
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

.notice {
  color: #047857;
}

.hint {
  color: #6b7280;
}
</style>
