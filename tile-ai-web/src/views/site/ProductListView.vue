<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import { listProducts } from '@/api/c/catalog'
import type { ProductQuery } from '@/api/c/catalog'
import { listDictsOfType } from '@/api/dicts'
import type { Dict } from '@/api/dicts'
import type { Sku } from '@/api/types'

/**
 * 产品库浏览(路径 A,F-1.2)。
 *
 * **只返回上架 SKU** —— 下架的砖不该出现在浏览里(服务端已保证,前端不另外过滤)。
 *
 * 空间 / 风格的下拉**由字典驱动,不硬编码**(D-14):契约里的 enum 只是内置默认值,
 * 真正的可选项由后台维护。硬编码一份,后台加了新风格这里就选不到。
 */
const filters = reactive<ProductQuery>({})
const rows = ref<Sku[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(9)

const spaces = ref<Dict[]>([])
const styles = ref<Dict[]>([])

const loading = ref(false)
const errorText = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)
const hasFilter = computed(() =>
  Boolean(
    filters.keyword || filters.material || filters.style || filters.space ||
    filters.priceMin || filters.priceMax || filters.spec || filters.colorPattern,
  ),
)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listProducts({ ...filters, page: page.value, size: size.value })
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

/** 换筛选条件要回到第 1 页 —— 否则停在第 3 页而结果只有 1 页时是空白。 */
function search() {
  page.value = 1
  load()
}

function reset() {
  Object.keys(filters).forEach((key) => delete (filters as Record<string, unknown>)[key])
  search()
}

function goPage(target: number) {
  if (target < 1 || target > totalPages.value) return
  page.value = target
  load()
}

/** 价格区间是 number,空字符串要转成 undefined 才能被 buildQuery 跳过。 */
function priceOf(value: string | number | undefined): number | undefined {
  if (value === '' || value === undefined || value === null) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

onMounted(async () => {
  try {
    spaces.value = await listDictsOfType('space')
    styles.value = await listDictsOfType('style')
  } catch {
    // 字典拿不到不影响浏览,只是少两个下拉选项
    spaces.value = []
    styles.value = []
  }
  await load()
})
</script>

<template>
  <section>
    <h1>选砖</h1>

    <form class="filters" @submit.prevent="search">
      <label>
        关键词
        <input v-model="filters.keyword" type="text" placeholder="名称或编号" />
      </label>
      <label>
        材质
        <input v-model="filters.material" type="text" placeholder="如 釉面砖" />
      </label>
      <label>
        规格
        <input v-model="filters.spec" type="text" placeholder="如 750×1500mm" />
      </label>
      <label>
        花色
        <input v-model="filters.colorPattern" type="text" placeholder="如 浅灰" />
      </label>
      <label>
        空间
        <select v-model="filters.space">
          <option value="">全部</option>
          <option v-for="item in spaces" :key="item.key" :value="item.key">
            {{ item.label }}
          </option>
        </select>
      </label>
      <label>
        风格
        <select v-model="filters.style">
          <option value="">全部</option>
          <option v-for="item in styles" :key="item.key" :value="item.key">
            {{ item.label }}
          </option>
        </select>
      </label>
      <label>
        价格下限
        <input
          :value="filters.priceMin"
          type="number"
          min="0"
          @input="filters.priceMin = priceOf(($event.target as HTMLInputElement).value)"
        />
      </label>
      <label>
        价格上限
        <input
          :value="filters.priceMax"
          type="number"
          min="0"
          @input="filters.priceMax = priceOf(($event.target as HTMLInputElement).value)"
        />
      </label>
      <div class="actions">
        <button type="submit" class="primary">查询</button>
        <button v-if="hasFilter" type="button" @click="reset">清空</button>
      </div>
    </form>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="loading" class="hint">加载中…</p>

    <template v-else-if="rows.length">
      <p class="count">共 {{ total }} 款</p>
      <ul class="grid">
        <li v-for="row in rows" :key="row.sku">
          <RouterLink class="card" :to="{ name: 'product-detail', params: { sku: row.sku } }">
            <div class="thumb">
              <img v-if="row.coverImage" :src="row.coverImage" :alt="row.name" />
              <span v-else class="thumb--empty">暂无图片</span>
            </div>
            <h3>{{ row.name }}</h3>
            <p class="meta">{{ [row.material, row.spec, row.surface].filter(Boolean).join(' · ') }}</p>
            <p class="price">
              ¥{{ row.pricePerSqm }}<small>/㎡</small>
            </p>
            <p class="tags">
              <span v-for="tag in row.styleTags ?? []" :key="tag" class="tag">{{ tag }}</span>
            </p>
          </RouterLink>
        </li>
      </ul>

      <footer class="pager">
        <button type="button" :disabled="!hasPrev" @click="goPage(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button type="button" :disabled="!hasNext" @click="goPage(page + 1)">下一页</button>
      </footer>
    </template>

    <p v-else class="hint">没有符合条件的瓷砖。</p>
  </section>
</template>

<style scoped>
h1 {
  margin: 0 0 1rem;
  font-size: 1.35rem;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: flex-end;
  padding: 0.9rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.85rem;
  color: #374151;
}

input,
select {
  padding: 0.3rem 0.45rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

button {
  padding: 0.35rem 0.8rem;
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

.count {
  color: #6b7280;
  font-size: 0.9rem;
  margin: 1rem 0 0.5rem;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: 1rem;
  list-style: none;
  padding: 0;
  margin: 0;
}

.card {
  display: block;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  height: 100%;
}

.card:hover {
  border-color: #93c5fd;
}

.thumb {
  height: 8rem;
  background: #f9fafb;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb--empty {
  color: #9ca3af;
  font-size: 0.85rem;
}

.card h3 {
  margin: 0.6rem 0.75rem 0.2rem;
  font-size: 1rem;
}

.meta {
  margin: 0 0.75rem;
  color: #6b7280;
  font-size: 0.82rem;
  min-height: 1.2em;
}

.price {
  margin: 0.4rem 0.75rem 0;
  color: #b45309;
  font-weight: 600;
}

.price small {
  color: #9ca3af;
  font-weight: 400;
}

.tags {
  margin: 0.4rem 0.75rem 0.75rem;
}

.tag {
  display: inline-block;
  margin-right: 0.3rem;
  padding: 0.05rem 0.35rem;
  border-radius: 0.25rem;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.75rem;
}

.pager {
  display: flex;
  gap: 1rem;
  align-items: center;
  justify-content: center;
  margin-top: 1.5rem;
}

.error {
  color: #b91c1c;
}

.hint {
  color: #6b7280;
}
</style>
