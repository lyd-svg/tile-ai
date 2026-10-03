<script setup lang="ts">
import { Alert, Button, Input, InputNumber, Select, Skeleton } from 'ant-design-vue'
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
    <header class="page-head">
      <h1 class="page-head__title">选砖</h1>
      <p class="page-head__sub">按花色、材质、规格、风格与价格区间挑砖。</p>
    </header>

    <form class="filters" @submit.prevent="search">
      <label class="field">
        <span class="field__label">关键词</span>
        <Input v-model:value="filters.keyword" placeholder="名称或编号" />
      </label>
      <label class="field">
        <span class="field__label">材质</span>
        <Input v-model:value="filters.material" placeholder="如 釉面砖" />
      </label>
      <label class="field">
        <span class="field__label">规格</span>
        <Input v-model:value="filters.spec" placeholder="如 750×1500mm" />
      </label>
      <label class="field">
        <span class="field__label">花色</span>
        <Input v-model:value="filters.colorPattern" placeholder="如 浅灰" />
      </label>
      <label class="field">
        <span class="field__label">空间</span>
        <Select v-model:value="filters.space" placeholder="全部" allow-clear>
          <Select.Option v-for="item in spaces" :key="item.key" :value="item.key">
            {{ item.label }}
          </Select.Option>
        </Select>
      </label>
      <label class="field">
        <span class="field__label">风格</span>
        <Select v-model:value="filters.style" placeholder="全部" allow-clear>
          <Select.Option v-for="item in styles" :key="item.key" :value="item.key">
            {{ item.label }}
          </Select.Option>
        </Select>
      </label>
      <label class="field field--narrow">
        <span class="field__label">价格下限</span>
        <InputNumber
          :value="filters.priceMin"
          :min="0"
          @update:value="(v) => (filters.priceMin = priceOf(v ?? undefined))"
        />
      </label>
      <label class="field field--narrow">
        <span class="field__label">价格上限</span>
        <InputNumber
          :value="filters.priceMax"
          :min="0"
          @update:value="(v) => (filters.priceMax = priceOf(v ?? undefined))"
        />
      </label>
      <div class="actions">
        <Button type="primary" html-type="submit">查询</Button>
        <Button v-if="hasFilter" @click="reset">清空</Button>
      </div>
    </form>

    <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <template v-else-if="rows.length">
      <p class="count">共 {{ total }} 款</p>
      <ul class="grid">
        <li v-for="row in rows" :key="row.sku">
          <RouterLink class="card" :to="{ name: 'product-detail', params: { sku: row.sku } }">
            <div class="thumb">
              <img v-if="row.coverImage" :src="row.coverImage" :alt="row.name" />
              <span v-else class="thumb--empty">暂无图片</span>
            </div>
            <div class="card__body">
              <h3 class="card__name">{{ row.name }}</h3>
              <p class="meta">{{ [row.material, row.spec, row.surface].filter(Boolean).join(' · ') }}</p>
              <p class="price">
                ¥{{ row.pricePerSqm }}<small>/㎡</small>
              </p>
              <p class="tags">
                <span v-for="tag in row.styleTags ?? []" :key="tag" class="tag">{{ tag }}</span>
              </p>
            </div>
          </RouterLink>
        </li>
      </ul>

      <footer class="pager">
        <button type="button" class="btn" :disabled="!hasPrev" @click="goPage(page - 1)">
          上一页
        </button>
        <span class="pager__pos">第 {{ page }} / {{ totalPages }} 页</span>
        <button type="button" class="btn" :disabled="!hasNext" @click="goPage(page + 1)">
          下一页
        </button>
      </footer>
    </template>

    <p v-else class="hint">没有符合条件的瓷砖。</p>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 元素与绑定**一个没动**:筛选仍是原生 input / select,
   因为这一页的筛选表单**没有测试覆盖**,换控件属于"不可验证的行为改动"。
   详见 `tile-ai-ui-rollout-plan.md` 登记表 C2 行。 */

.page-head {
  margin-bottom: var(--space-5);
}

.page-head__title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-2xl);
  line-height: var(--leading-2xl);
  font-weight: 700;
  color: var(--color-text);
}

.page-head__sub {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text-secondary);
}

/* ── 筛选区 ─────────────────────────────────────────────────────────────── */
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: flex-end;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 9rem;
}

.field--narrow {
  min-width: 6rem;
}

.field__label {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

/* ⚠️ 输入框 / 数字框 / 下拉 / 按钮**交给 antd** —— 这里不再自己写它们的样式。
   尤其**不能留 `.field input` 这类选择器**:antd 的 `<Input>` 渲染出来的就是
   `<input class="ant-input">`,那条规则会**照样命中它**,与 antd 自己的样式打架。
   见 `openapi-verification.md` §68。

   这里只保留**排版**:让 antd 的控件在格子里铺满。 */
.field :deep(.ant-input),
.field :deep(.ant-input-number),
.field :deep(.ant-select) {
  width: 100%;
}

.actions {
  display: flex;
  gap: var(--space-2);
}

/* ── 结果 ───────────────────────────────────────────────────────────────── */
.count {
  margin: var(--space-5) 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: var(--space-4);
  list-style: none;
  padding: 0;
  margin: 0;
}

.card {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  transition:
    border-color var(--dur-base) var(--ease-standard),
    box-shadow var(--dur-base) var(--ease-standard),
    transform var(--dur-base) var(--ease-standard);
}

.card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.card:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}

/* 瓷砖图固定 1:1 —— 防加载抖动,也让不同花色的砖可横向比较 */
.thumb {
  aspect-ratio: 1 / 1;
  background: var(--color-surface-sunken);
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
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

.card__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: var(--space-3) var(--space-4) var(--space-4);
}

.card__name {
  margin: 0 0 var(--space-1);
  font-size: var(--text-base);
  line-height: var(--leading-base);
  font-weight: 600;
  color: var(--color-text);
}

.meta {
  margin: 0 0 var(--space-2);
  min-height: 1.2em;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}

/* 报价是这一页的重点,用主色压住视线 */
.price {
  margin: auto 0 0;
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-primary);
}

.price small {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-muted);
}

.tags {
  margin: var(--space-2) 0 0;
}

.tag {
  display: inline-block;
  margin-right: var(--space-1);
  padding: 0.1rem var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
}

/* ── 分页 ───────────────────────────────────────────────────────────────── */
.pager {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  justify-content: center;
  margin-top: var(--space-6);
}

.pager__pos {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */

.hint {
  margin: var(--space-6) 0;
  padding: var(--space-6);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-align: center;
}
</style>
