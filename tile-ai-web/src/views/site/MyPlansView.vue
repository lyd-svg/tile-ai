<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { listPlans } from '@/api/c/plans'
import { listDictsOfType } from '@/api/dicts'
import type { PlanSummary } from '@/api/types'

/**
 * 我的方案(F-1.9)。需登录。
 *
 * 这一页是**生成之后的落点**:用户在「生成」页拿到图,方案就存在服务端了;
 * 之前没有任何入口能再找到它(生成页只打了一行编号文本),这一页补上那个入口。
 *
 * **列表页只读** —— 卡片整块是链接,点进详情页才能改名 / 另存为 / 删除 /
 * 重算报价。四个管理动作只放一处,免得两处各自漂。
 *
 * ⚠️ 两个必须守住的口径:
 *   1. `totalAmount` **缺失 = 没算出价,不是 0**。绝不能 `?? 0`,
 *      否则用户看到一张 ¥0 的报价单(比"没有报价"更有害,因为它看着像真的)。
 *   2. 空 / 风格走**后台字典**,拿不到字典就退化成显示 key —— 但**绝不能因此白屏**,
 *      所以 onMounted 里先取字典、失败吞掉,再 load()。
 */
const rows = ref<PlanSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(9)

/** 字典 key → 中文名。拿不到字典就是空表,展示时退回原始 key。 */
const spaceLabels = ref<Map<string, string>>(new Map())
const styleLabels = ref<Map<string, string>>(new Map())

const loading = ref(false)
const errorText = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listPlans({ page: page.value, size: size.value })
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

function goPage(target: number) {
  if (target < 1 || target > totalPages.value) return
  page.value = target
  load()
}

/** 字典查不到就显示原始 key —— 宁可露出 `living_room`,也不要一片空白。 */
function labelOf(labels: Map<string, string>, key?: string): string {
  if (!key) return ''
  return labels.get(key) ?? key
}

/** 「空间 · 风格」,两个都可能没有(契约里它们都是可选字段)。 */
function metaOf(row: PlanSummary): string {
  return [labelOf(spaceLabels.value, row.space), labelOf(styleLabels.value, row.style)]
    .filter(Boolean)
    .join(' · ')
}

/**
 * 报价文案。
 *
 * `totalAmount === undefined`(或 null)时**不是 0,是"没算出价"** ——
 * 后端 `PlanSummaryView.totalAmount` 的注释写得很明白。这里必须给出一个
 * 诚实的空态,不能让 `¥NaN` 或 `¥0` 上屏。
 */
function priceOf(row: PlanSummary): string {
  const amount = row.totalAmount
  if (amount === undefined || amount === null) return '未出报价'
  return `¥${amount}`
}

// 部位中文名,与 GenerateView / ProductDetailView / ComboListView 一样各自定义一份 ——
// 项目里没有公共的展示层常量模块,为三个词新建一层抽象不划算。
const PART_LABELS: Record<string, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部',
}

/** 瓷砖摘要,如「地面 · TL-1001」。没有 `parts` 就整段不渲染。 */
function partsOf(row: PlanSummary): string {
  return (row.parts ?? [])
    .map((part) => `${PART_LABELS[part.part] ?? part.part} · ${part.sku}`)
    .join('、')
}

/** `2026-10-01T21:19:22` → `2026-10-01 21:19`(只做展示,不引日期库)。 */
function timeOf(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  try {
    const [spaces, styles] = await Promise.all([
      listDictsOfType('space'),
      listDictsOfType('style'),
    ])
    spaceLabels.value = new Map(spaces.map((item) => [item.key, item.label]))
    styleLabels.value = new Map(styles.map((item) => [item.key, item.label]))
  } catch {
    // 字典拿不到不影响看方案,只是空间/风格显示成 key
  }
  await load()
})
</script>

<template>
  <section>
    <h1>我的方案</h1>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="loading" class="hint">加载中…</p>

    <template v-else-if="rows.length">
      <p class="count">共 {{ total }} 份</p>
      <ul class="grid">
        <li v-for="row in rows" :key="row.id">
          <RouterLink class="card" :to="{ name: 'plan-detail', params: { id: row.id } }">
            <div class="thumb">
              <img v-if="row.coverImage" :src="row.coverImage" alt="" />
              <span v-else class="thumb--empty">暂无效果图</span>
            </div>
            <h3>{{ row.name }}</h3>
            <p class="meta">{{ metaOf(row) }}</p>
            <p class="price">
              {{ priceOf(row) }}
              <span v-if="row.quoteExpired" class="stale">报价已过期</span>
            </p>
            <p v-if="partsOf(row)" class="parts">{{ partsOf(row) }}</p>
            <p class="time">{{ timeOf(row.createdAt) }}</p>
          </RouterLink>
        </li>
      </ul>

      <footer class="pager">
        <button type="button" :disabled="!hasPrev" @click="goPage(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button type="button" :disabled="!hasNext" @click="goPage(page + 1)">下一页</button>
      </footer>
    </template>

    <p v-else class="hint">
      还没有方案。先去<RouterLink to="/products">选砖</RouterLink>、<RouterLink to="/layout">
        录户型</RouterLink>,然后生成效果图。
    </p>
  </section>
</template>

<style scoped>
h1 {
  margin: 0 0 1rem;
  font-size: 1.35rem;
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

.price .stale {
  margin-left: 0.4rem;
  padding: 0.05rem 0.35rem;
  border-radius: 0.25rem;
  background: #fef3c7;
  color: #b45309;
  font-size: 0.72rem;
  font-weight: 400;
}

.parts {
  margin: 0.3rem 0.75rem 0;
  color: #6b7280;
  font-size: 0.78rem;
}

.time {
  margin: 0.3rem 0.75rem 0.75rem;
  color: #9ca3af;
  font-size: 0.75rem;
}

.pager {
  display: flex;
  gap: 1rem;
  align-items: center;
  justify-content: center;
  margin-top: 1.5rem;
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

.error {
  color: #b91c1c;
}

.hint {
  color: #6b7280;
}
</style>
