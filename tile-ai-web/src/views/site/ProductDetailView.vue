<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProduct } from '@/api/c/catalog'
import { listDictsOfType } from '@/api/dicts'
import type { Sku } from '@/api/types'

/**
 * 产品详情(F-1.2)。
 *
 * 下架 SKU 服务端按 **30001** 返回(与列表口径一致:列表里看不到,
 * 拿旧链接进来也该说"不存在",而不是展示一块买不到的砖)。
 */
const route = useRoute()
const router = useRouter()

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

        <p class="notice">
          单张效果图与按部位报价**尚未开放**(开发中)。先把砖选好 ——
          下一步是户型录入与生成。
        </p>

        <div class="cta">
          <button type="button" class="primary" disabled>生成效果图(开发中)</button>
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
