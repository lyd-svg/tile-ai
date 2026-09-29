<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getStore } from '@/api/c/catalog'
import type { Store } from '@/api/types'

/**
 * 门店与线下引导(F-1.4)。
 *
 * **一期不做线上下单 / 支付**(PRD §10 边界)—— 这页只有地址与电话,没有购买入口。
 * 未配置门店时服务端返回 **30001**,界面要说"暂未配置",而不是画一张空卡片。
 */
const store = ref<Store | null>(null)
const loading = ref(false)
const errorText = ref('')

onMounted(async () => {
  loading.value = true
  try {
    store.value = await getStore()
  } catch (error) {
    store.value = null
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section>
    <h1>到店看砖</h1>
    <p class="sub">线上挑好后,欢迎到店看实物、摸手感 —— 瓷砖的质感看照片替代不了。</p>

    <p v-if="loading" class="hint">加载中…</p>

    <div v-else-if="store" class="card">
      <h2>{{ store.name }}</h2>
      <dl>
        <dt>地址</dt>
        <dd>{{ store.address }}</dd>
        <dt>电话</dt>
        <dd><a :href="`tel:${store.phone}`">{{ store.phone }}</a></dd>
      </dl>
      <a v-if="store.mapUrl" class="map" :href="store.mapUrl" target="_blank" rel="noopener">
        打开地图导航
      </a>
      <p class="notice">一期不支持线上下单与支付,请到店选购。</p>
    </div>

    <p v-else class="hint">{{ errorText || '门店信息暂未配置。' }}</p>
  </section>
</template>

<style scoped>
h1 {
  margin: 0 0 0.25rem;
  font-size: 1.35rem;
}

.sub {
  color: #6b7280;
  margin: 0 0 1.25rem;
  font-size: 0.9rem;
}

.card {
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  padding: 1.25rem;
  max-width: 32rem;
}

h2 {
  margin: 0 0 0.75rem;
  font-size: 1.1rem;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.35rem 1rem;
  margin: 0 0 1rem;
  font-size: 0.95rem;
}

dt {
  color: #6b7280;
}

dd {
  margin: 0;
}

dd a {
  color: #1d4ed8;
}

.map {
  display: inline-block;
  padding: 0.4rem 0.8rem;
  border: 1px solid #1d4ed8;
  border-radius: 0.25rem;
  color: #1d4ed8;
  text-decoration: none;
  font-size: 0.9rem;
}

.notice {
  margin: 1rem 0 0;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 0.25rem;
  padding: 0.5rem 0.7rem;
  font-size: 0.85rem;
}

.hint {
  color: #6b7280;
}
</style>
