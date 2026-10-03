<script setup lang="ts">
import { Button, Empty, Select, Skeleton } from 'ant-design-vue'
import { onMounted, reactive, ref } from 'vue'

import { listCombos } from '@/api/c/catalog'
import { listDictsOfType } from '@/api/dicts'
import type { Dict } from '@/api/dicts'
import type { Combo } from '@/api/types'

/**
 * 推荐搭配(路径 B,F-1.3)。
 *
 * 搭配是**人工维护**的成套方案(PRD §5.2.2),不是算法组合 ——
 * 所以这里不提供"智能推荐",只按空间/风格筛。
 *
 * `items[].part` 恒为 floor / wall / local 三值(契约 T-9),一期不含美缝。
 */
const filters = reactive<{ space?: string; style?: string }>({})
const combos = ref<Combo[]>([])
const total = ref(0)
const spaces = ref<Dict[]>([])
const styles = ref<Dict[]>([])
const loading = ref(false)
const errorText = ref('')

const PART_LABELS: Record<string, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部',
}

function label(map: Dict[], key: string): string {
  return map.find((item) => item.key === key)?.label ?? key
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listCombos({ ...filters, page: 1, size: 20 })
    combos.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    combos.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const [spaceDict, styleDict] = await Promise.all([
      listDictsOfType('space'),
      listDictsOfType('style'),
    ])
    spaces.value = spaceDict
    styles.value = styleDict
  } catch {
    spaces.value = []
    styles.value = []
  }
  await load()
})
</script>

<template>
  <section>
    <header class="page-head">
      <h1 class="page-head__title">推荐搭配</h1>
      <p class="sub">按空间与风格整理的成套方案,含参考报价区间。</p>
    </header>

    <form class="filters" @submit.prevent="load">
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
      <Button type="primary" html-type="submit">查询</Button>
    </form>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <Skeleton v-if="loading" active :paragraph="{ rows: 6 }" />

    <template v-else-if="combos.length">
      <p class="count">共 {{ total }} 套</p>
      <ul class="grid">
        <li v-for="combo in combos" :key="combo.id">
          <article class="card">
            <div class="thumb">
              <img v-if="combo.coverImage" :src="combo.coverImage" :alt="combo.name" />
              <span v-else class="thumb--empty">暂无图片</span>
            </div>
            <div class="card__body">
              <h3 class="card__name">{{ combo.name }}</h3>
              <p class="meta">
                {{ label(spaces, combo.space) }} · {{ label(styles, combo.style) }}
              </p>
              <p v-if="combo.refPriceMin || combo.refPriceMax" class="price">
                参考 {{ combo.refPriceMin ?? '?' }} – {{ combo.refPriceMax ?? '?' }} 元
              </p>
              <ul class="items">
                <li v-for="item in combo.items ?? []" :key="`${item.sku}-${item.part}`">
                  <span class="part">{{ PART_LABELS[item.part] ?? item.part }}</span>
                  {{ item.name || item.sku }}
                </li>
              </ul>
            </div>
          </article>
        </li>
      </ul>
    </template>

    <div v-else class="empty">
      <Empty description="还没有符合条件的搭配" />
      <p class="hint">搭配由后台人工维护,可在「后台管理」中添加。</p>
    </div>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 本页**没有测试用例** —— 改动无测试可证,只能等人眼验收(纪律 7)。 */

.page-head {
  margin-bottom: var(--space-4);
}

.page-head__title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-2xl);
  line-height: var(--leading-2xl);
  font-weight: 700;
  color: var(--color-text);
}

.sub {
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

.field__label {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

/* ⚠️ 下拉与按钮**交给 antd** —— 这里**不再给它们写样式**(写了会和 antd 打架)。
   见 `openapi-verification.md` §68。 */
.filters :deep(.ant-select) {
  min-width: 9rem;
}

/* ── 搭配卡 ─────────────────────────────────────────────────────────────── */
.count {
  margin: var(--space-5) 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
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
  transition:
    border-color var(--dur-base) var(--ease-standard),
    box-shadow var(--dur-base) var(--ease-standard);
}

.card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
}

/* 搭配图固定 4:3 —— 防加载抖动 */
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
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}

/* 参考报价 —— 主色 + 等宽数字 */
.price {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-primary);
}

.items {
  list-style: none;
  padding: var(--space-3) 0 0;
  margin: auto 0 0;
  border-top: 1px solid var(--color-border);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

.items li {
  padding: var(--space-1) 0;
}

.part {
  display: inline-block;
  margin-right: var(--space-2);
  padding: 0.05rem var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: var(--text-xs);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
.error {
  margin: var(--space-4) 0 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

/* 空态:主文案与插图由 antd 的 `Empty` 出,补充说明放在它下面。
   背景与内边距归**容器**,别让 `.hint` 再叠一层色块。 */
.empty {
  margin: var(--space-6) 0;
  padding: var(--space-6);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
  text-align: center;
}

.empty .hint {
  margin: var(--space-3) 0 0;
  padding: 0;
  background: none;
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
}
</style>
