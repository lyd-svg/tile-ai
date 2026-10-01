<script setup lang="ts">
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

    <p v-if="loading" class="hint">加载中…</p>
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
        <h1>{{ product.name }}</h1>
        <p class="sku">编号 {{ product.sku }}</p>
        <p class="price">¥{{ product.pricePerSqm }}<small>/㎡</small></p>

        <dl>
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
          <RouterLink to="/products">继续选砖</RouterLink>
        </div>
      </div>
    </article>
  </section>
</template>

<style scoped>
.back {
  padding: 0.25rem 0.6rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
  margin-bottom: 1rem;
}

.detail {
  display: grid;
  grid-template-columns: minmax(16rem, 1fr) minmax(16rem, 1fr);
  gap: 2rem;
}

@media (max-width: 44rem) {
  .detail {
    grid-template-columns: 1fr;
  }
}

.main-image {
  height: 20rem;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
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
  color: #9ca3af;
}

.thumbs {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.6rem;
}

.thumb {
  width: 4rem;
  height: 4rem;
  padding: 0;
  border: 2px solid #e5e7eb;
  border-radius: 0.25rem;
  overflow: hidden;
  background: white;
  cursor: pointer;
}

.thumb--active {
  border-color: #1d4ed8;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

h1 {
  margin: 0 0 0.25rem;
  font-size: 1.4rem;
}

.sku {
  color: #9ca3af;
  font-size: 0.85rem;
  margin: 0 0 0.75rem;
}

.price {
  color: #b45309;
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 1rem;
}

.price small {
  font-size: 0.9rem;
  font-weight: 400;
  color: #9ca3af;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.35rem 1rem;
  margin: 0 0 1.25rem;
  font-size: 0.9rem;
}

dt {
  color: #6b7280;
}

dd {
  margin: 0;
}

.pick {
  margin-bottom: 1rem;
}

.pick-label {
  margin: 0 0 0.5rem;
  font-size: 0.9rem;
  color: #374151;
}

.parts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.part {
  padding: 0.4rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
  font-size: 0.9rem;
}

.part--active {
  border-color: #1d4ed8;
  color: #1d4ed8;
}

.part--chosen {
  border-color: #1d4ed8;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 600;
}

.tick {
  margin-left: 0.25rem;
}

.chosen {
  margin: 0.5rem 0 0;
  color: #15803d;
  font-size: 0.85rem;
}

.draft {
  border: 1px solid #e5e7eb;
  border-radius: 0.25rem;
  padding: 0.6rem 0.75rem;
  margin-bottom: 1rem;
}

.draft-title {
  margin: 0 0 0.4rem;
  font-size: 0.85rem;
  color: #6b7280;
}

.draft ul {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 0.9rem;
}

.draft li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  padding: 0.2rem 0;
}

.remove {
  border: none;
  background: none;
  color: #b91c1c;
  cursor: pointer;
  font-size: 0.85rem;
  padding: 0;
}

.notice {
  padding: 0.6rem 0.75rem;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 0.25rem;
  color: #92400e;
  font-size: 0.85rem;
}

.cta {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 1rem;
}

button.primary {
  padding: 0.5rem 1rem;
  border: 1px solid #1d4ed8;
  border-radius: 0.25rem;
  background: #1d4ed8;
  color: white;
}

button.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cta a {
  color: #1d4ed8;
  text-decoration: none;
  font-size: 0.9rem;
}

.error {
  color: #b91c1c;
}

.hint {
  color: #6b7280;
}
</style>
