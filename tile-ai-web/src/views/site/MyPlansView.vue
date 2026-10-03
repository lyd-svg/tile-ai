<script setup lang="ts">
import { Skeleton } from 'ant-design-vue'
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
    <header class="page-head">
      <h1 class="page-head__title">我的方案</h1>
    </header>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <template v-else-if="rows.length">
      <p class="count">共 {{ total }} 份</p>
      <ul class="grid">
        <li v-for="row in rows" :key="row.id">
          <RouterLink class="card" :to="{ name: 'plan-detail', params: { id: row.id } }">
            <div class="thumb">
              <img v-if="row.coverImage" :src="row.coverImage" alt="" />
              <span v-else class="thumb--empty">暂无效果图</span>
            </div>
            <div class="card__body">
              <h3 class="card__name">{{ row.name }}</h3>
              <p class="meta">{{ metaOf(row) }}</p>
              <p class="price">
                {{ priceOf(row) }}
                <span v-if="row.quoteExpired" class="stale">报价已过期</span>
              </p>
              <p v-if="partsOf(row)" class="parts">{{ partsOf(row) }}</p>
              <p class="time">{{ timeOf(row.createdAt) }}</p>
            </div>
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
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 测试逐 class 断言,以下必须原样保留:`.card`(含 h3)· `.thumb` ·
   `.thumb--empty` · `.meta` · `.price` · `.price .stale` · `.parts` · `.pager button`。
   ⚠️ 本页**只允许有两个 `<button>`**(上一页 / 下一页)—— 有测试在数字。 */

.page-head {
  margin-bottom: var(--space-4);
}

.page-head__title {
  margin: 0;
  font-size: var(--text-2xl);
  line-height: var(--leading-2xl);
  font-weight: 700;
  color: var(--color-text);
}

.count {
  margin: var(--space-4) 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

/* ── 方案卡网格 ─────────────────────────────────────────────────────────── */
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

/* 效果图固定 4:3 —— 防加载抖动,也让不同方案可横向比较 */
.thumb {
  aspect-ratio: 4 / 3;
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

/* 报价是这一页的重点 —— 主色 + 等宽数字 */
.price {
  margin: auto 0 0;
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-primary);
}

.price .stale {
  margin-left: var(--space-2);
  padding: 0.05rem var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-warning-soft);
  color: var(--color-warning);
  font-size: var(--text-xs);
  font-weight: 400;
}

.parts {
  margin: var(--space-2) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

.time {
  margin: var(--space-2) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ── 分页 ───────────────────────────────────────────────────────────────── */
.pager {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  justify-content: center;
  margin-top: var(--space-6);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

button {
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard);
}

button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

button:disabled {
  opacity: 0.45;
  cursor: default;
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
.error {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

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
