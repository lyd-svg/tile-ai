<script setup lang="ts">
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
    <h1>推荐搭配</h1>
    <p class="sub">按空间与风格整理的成套方案,含参考报价区间。</p>

    <form class="filters" @submit.prevent="load">
      <label>
        空间
        <select v-model="filters.space">
          <option value="">全部</option>
          <option v-for="item in spaces" :key="item.key" :value="item.key">{{ item.label }}</option>
        </select>
      </label>
      <label>
        风格
        <select v-model="filters.style">
          <option value="">全部</option>
          <option v-for="item in styles" :key="item.key" :value="item.key">{{ item.label }}</option>
        </select>
      </label>
      <button type="submit" class="primary">查询</button>
    </form>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="loading" class="hint">加载中…</p>

    <template v-else-if="combos.length">
      <p class="count">共 {{ total }} 套</p>
      <ul class="grid">
        <li v-for="combo in combos" :key="combo.id">
          <article class="card">
            <div class="thumb">
              <img v-if="combo.coverImage" :src="combo.coverImage" :alt="combo.name" />
              <span v-else class="thumb--empty">暂无图片</span>
            </div>
            <h3>{{ combo.name }}</h3>
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
          </article>
        </li>
      </ul>
    </template>

    <p v-else class="hint">还没有符合条件的搭配。搭配由后台人工维护,可在「后台管理」中添加。</p>
  </section>
</template>

<style scoped>
h1 {
  margin: 0 0 0.25rem;
  font-size: 1.35rem;
}

.sub {
  color: #6b7280;
  margin: 0 0 1rem;
  font-size: 0.9rem;
}

.filters {
  display: flex;
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

select {
  padding: 0.3rem 0.45rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
}

button {
  padding: 0.35rem 0.8rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
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
  grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
  gap: 1rem;
  list-style: none;
  padding: 0;
  margin: 0;
}

.card {
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  padding-bottom: 0.75rem;
  height: 100%;
}

.thumb {
  height: 9rem;
  background: #f9fafb;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.5rem 0.5rem 0 0;
  overflow: hidden;
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

h3 {
  margin: 0.6rem 0.75rem 0.2rem;
  font-size: 1rem;
}

.meta {
  margin: 0 0.75rem;
  color: #6b7280;
  font-size: 0.82rem;
}

.price {
  margin: 0.3rem 0.75rem;
  color: #b45309;
  font-size: 0.9rem;
  font-weight: 600;
}

.items {
  list-style: none;
  padding: 0 0.75rem;
  margin: 0.4rem 0 0;
  font-size: 0.85rem;
  color: #374151;
}

.items li {
  padding: 0.1rem 0;
}

.part {
  display: inline-block;
  margin-right: 0.35rem;
  padding: 0.02rem 0.3rem;
  border-radius: 0.2rem;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.75rem;
}

.error {
  color: #b91c1c;
}

.hint {
  color: #6b7280;
}
</style>
