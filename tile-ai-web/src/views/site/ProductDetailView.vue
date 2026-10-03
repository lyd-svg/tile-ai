<script setup lang="ts">
import { Skeleton } from 'ant-design-vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProduct } from '@/api/c/catalog'
import { listDictsOfType } from '@/api/dicts'
import { useSelectionDraftStore } from '@/stores/selectionDraft'
import type { Part, Sku } from '@/api/types'

/**
 * 产品详情(F-1.2)。
 *
 * 下架 SKU 服务端按 **30001** 返回(与列表口径一致:列表里看不到,
 * 拿旧链接进来也该说"不存在",而不是展示一块买不到的砖)。
 *
 * F-1.5 起这里多了一步:**选定这块砖铺在哪个部位**,写进选购草稿,
 * 再由「录户型」页继续。部位是选砖与生成之间唯一的必填信息。
 */
const route = useRoute()
const router = useRouter()
const draft = useSelectionDraftStore()

/**
 * 部位的中文名。
 *
 * ⚠️ **这是前端唯一允许硬编码的枚举映射** —— `Part` 恒为三值(契约 T-9,
 * `tests/contract.spec.ts` 在钉)。**空间 / 风格不在此列**:它们由后台字典维护,
 * 必须走 `listDictsOfType`(D-14)。
 */
const PART_LABELS: Record<Part, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部(腰线 / 花砖)',
}

const PART_OPTIONS = (Object.keys(PART_LABELS) as Part[]).map((value) => ({
  value,
  label: PART_LABELS[value],
}))

/** 当前选中的部位 —— 只是"准备把这块砖用到哪",尚未写入草稿。 */
const activePart = ref<Part>('floor')
/** 刚加入草稿的提示文案,用来给一次轻确认 */
const justChosen = ref('')

const sku = computed(() => String(route.params.sku ?? ''))
const product = ref<Sku | null>(null)
const loading = ref(false)
const errorText = ref('')

/** 主图下标 —— 多角度图可以切换。 */
const activeImage = ref(0)
const images = computed(() => {
  const list = product.value?.images?.filter((image) => image.url) ?? []
  return list
})

/** 字典只用于把 key 翻成中文标签(展示);提交值始终是 key(D-14)。 */
const spaceLabels = ref<Record<string, string>>({})
const styleLabels = ref<Record<string, string>>({})

function label(map: Record<string, string>, key: string): string {
  return map[key] ?? key
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    product.value = await getProduct(sku.value)
    activeImage.value = 0
  } catch (error) {
    product.value = null
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 把当前这块砖写到选中的部位上。同部位再选一次是替换。 */
function choose(part: Part) {
  const current = product.value
  if (!current) return

  draft.setPart(part, current.sku, {
    name: current.name,
    pricePerSqm: current.pricePerSqm,
  })
  justChosen.value = `已加入:${PART_LABELS[part]} · ${current.name}`
}

/** 点已选中的部位 = 移除(同一个按钮承担双向操作,省一个"移除"按钮)。 */
function toggle(part: Part) {
  const existing = draft.partOf(part)
  if (existing && product.value && existing.sku === product.value.sku) {
    draft.removePart(part)
    justChosen.value = ''
    return
  }
  choose(part)
}

onMounted(async () => {
  try {
    const [spaces, styles] = await Promise.all([listDictsOfType('space'), listDictsOfType('style')])
    spaceLabels.value = Object.fromEntries(spaces.map((item) => [item.key, item.label]))
    styleLabels.value = Object.fromEntries(styles.map((item) => [item.key, item.label]))
  } catch {
    // 拿不到字典就退化成显示 key,不影响看砖
  }
  await load()
})
</script>

<template>
  <section>
    <button type="button" class="back" @click="router.back()">← 返回</button>

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />
    <p v-else-if="errorText" class="error" role="alert">{{ errorText }}</p>

    <article v-else-if="product" class="detail">
      <div class="gallery">
        <div class="main-image">
          <img
            v-if="images.length"
            :src="images[activeImage]?.url"
            :alt="product.name"
          />
          <img v-else-if="product.coverImage" :src="product.coverImage" :alt="product.name" />
          <span v-else class="empty">暂无图片</span>
        </div>
        <div v-if="images.length > 1" class="thumbs">
          <button
            v-for="(image, index) in images"
            :key="image.url"
            type="button"
            :class="['thumb', { 'thumb--active': index === activeImage }]"
            @click="activeImage = index"
          >
            <img :src="image.url" :alt="image.angle || `图 ${index + 1}`" />
          </button>
        </div>
      </div>

      <div class="info">
        <h1 class="title">{{ product.name }}</h1>
        <p class="sku">编号 {{ product.sku }}</p>
        <p class="price">¥{{ product.pricePerSqm }}<small>/㎡</small></p>

        <dl class="specs">
          <template v-if="product.spec"><dt>规格</dt><dd>{{ product.spec }}</dd></template>
          <template v-if="product.material"><dt>材质</dt><dd>{{ product.material }}</dd></template>
          <template v-if="product.surface"><dt>光泽度</dt><dd>{{ product.surface }}</dd></template>
          <template v-if="product.colorPattern">
            <dt>花色 / 纹理</dt><dd>{{ product.colorPattern }}</dd>
          </template>
          <dt>适用空间</dt>
          <dd>
            <template v-if="product.applicableSpaces?.length">
              {{ product.applicableSpaces.map((key) => label(spaceLabels, key)).join('、') }}
            </template>
            <template v-else>—</template>
          </dd>
          <dt>风格</dt>
          <dd>
            <template v-if="product.styleTags?.length">
              {{ product.styleTags.map((key) => label(styleLabels, key)).join('、') }}
            </template>
            <template v-else>—</template>
          </dd>
        </dl>

        <div class="pick">
          <p class="pick-label">这块砖用在哪个部位?</p>
          <div class="parts">
            <button
              v-for="option in PART_OPTIONS"
              :key="option.value"
              type="button"
              :class="['part', {
                'part--active': activePart === option.value,
                'part--chosen': draft.partOf(option.value)?.sku === product.sku,
              }]"
              @click="toggle(option.value)"
            >
              {{ option.label }}
              <span v-if="draft.partOf(option.value)?.sku === product.sku" class="tick">✓</span>
            </button>
          </div>
          <p v-if="justChosen" class="chosen" role="status">{{ justChosen }}</p>
        </div>

        <div v-if="draft.hasParts" class="draft">
          <p class="draft-title">已选部位({{ draft.selectedCount }})</p>
          <ul>
            <li v-for="item in draft.parts" :key="item.part">
              <span>{{ PART_LABELS[item.part] }} · {{ item.name || item.sku }}</span>
              <button type="button" class="remove" @click="draft.removePart(item.part)">移除</button>
            </li>
          </ul>
        </div>

        <p class="notice">选好砖后进入户型录入,再生成效果图。</p>

        <div class="cta">
          <button
            type="button"
            class="primary"
            :disabled="!draft.hasParts"
            @click="router.push({ name: 'layout' })"
          >
            去录户型 →
          </button>
          <RouterLink class="cta__alt" to="/products">继续选砖</RouterLink>
        </div>
      </div>
    </article>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 元素、class 名与绑定**一个没动**:`button.part` 等被测试依赖的选择器保持原样。 */

.back {
  margin-bottom: var(--space-4);
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.back:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* ── 两栏:左图右信息 ───────────────────────────────────────────────────── */
.detail {
  display: grid;
  grid-template-columns: minmax(16rem, 1fr) minmax(18rem, 1fr);
  gap: var(--space-6);
  align-items: start;
}

@media (max-width: 768px) {
  .detail {
    grid-template-columns: 1fr;
    gap: var(--space-5);
  }
}

/* ── 图 ─────────────────────────────────────────────────────────────────── */
.main-image {
  aspect-ratio: 1 / 1;
  background: var(--color-surface-sunken);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.main-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.empty {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.thumb {
  width: 4rem;
  height: 4rem;
  padding: 0;
  border: 2px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--color-surface);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard);
}

.thumb:hover {
  border-color: var(--color-primary-hover);
}

.thumb--active {
  border-color: var(--color-primary);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ── 信息 ───────────────────────────────────────────────────────────────── */
.title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-2xl);
  line-height: var(--leading-2xl);
  font-weight: 700;
  color: var(--color-text);
}

.sku {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* 报价是这一页的重点 —— 用主色压住视线,数字等宽对齐 */
.price {
  margin: 0 0 var(--space-5);
  font-size: var(--text-3xl);
  line-height: var(--leading-3xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-primary);
}

.price small {
  font-size: var(--text-base);
  font-weight: 400;
  color: var(--color-text-muted);
}

.specs {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-2) var(--space-4);
  margin: 0 0 var(--space-5);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
}

.specs dt {
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.specs dd {
  margin: 0;
  color: var(--color-text);
}

/* ── 选部位 ─────────────────────────────────────────────────────────────── */
.pick {
  margin-bottom: var(--space-4);
}

.pick-label {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text);
  font-weight: 600;
}

.parts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.part {
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-full);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.part:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.part--active {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.part--chosen {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}

.tick {
  margin-left: var(--space-1);
}

.chosen {
  margin: var(--space-3) 0 0;
  font-size: var(--text-sm);
  color: var(--color-success);
}

/* ── 已选部位清单 ───────────────────────────────────────────────────────── */
.draft {
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.draft-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

.draft ul {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
}

.draft li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) 0;
}

/* 移除是危险操作 —— 用危险色,不靠图标暗示 */
.remove {
  padding: 0;
  border: none;
  background: none;
  color: var(--color-danger);
  cursor: pointer;
  font: inherit;
  font-size: var(--text-xs);
}

.remove:hover {
  text-decoration: underline;
}

.notice {
  margin: 0 0 var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-warning);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-text);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
}

/* ── 行动点 ─────────────────────────────────────────────────────────────── */
.cta {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  margin-top: var(--space-5);
}

button.primary {
  padding: var(--space-3) var(--space-5);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font: inherit;
  font-size: var(--text-base);
  font-weight: 600;
  cursor: pointer;
}

button.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

button.primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.cta__alt {
  color: var(--color-primary);
  text-decoration: none;
  font-size: var(--text-sm);
  font-weight: 600;
}

.cta__alt:hover {
  text-decoration: underline;
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
