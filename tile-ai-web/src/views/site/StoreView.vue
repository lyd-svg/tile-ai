<script setup lang="ts">
import { Button, Empty, Skeleton } from 'ant-design-vue'
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
    <header class="page-head">
      <h1 class="page-head__title">到店看砖</h1>
      <p class="sub">线上挑好后,欢迎到店看实物、摸手感 —— 瓷砖的质感看照片替代不了。</p>
    </header>

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <div v-else-if="store" class="card">
      <h2 class="card__name">{{ store.name }}</h2>
      <dl class="info">
        <dt>地址</dt>
        <dd>{{ store.address }}</dd>
        <dt>电话</dt>
        <dd><a :href="`tel:${store.phone}`">{{ store.phone }}</a></dd>
      </dl>
      <Button v-if="store.mapUrl" type="primary" :href="store.mapUrl" target="_blank" rel="noopener">
        打开地图导航
      </Button>
      <p class="notice">一期不支持线上下单与支付,请到店选购。</p>
    </div>

    <div v-else class="empty">
      <Empty :description="errorText || '门店信息暂未配置。'" />
    </div>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 本页**没有测试用例** —— 改动无测试可证,只能等人眼验收(纪律 7)。 */

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

.sub {
  margin: 0;
  max-width: 38rem;
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text-secondary);
}

/* ── 门店卡 ─────────────────────────────────────────────────────────────── */
.card {
  max-width: 32rem;
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.card__name {
  margin: 0 0 var(--space-4);
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 600;
  color: var(--color-text);
}

.info {
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

.info dt {
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.info dd {
  margin: 0;
  color: var(--color-text);
}

.info dd a {
  color: var(--color-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* 地图入口改为 antd 的 `Button`(带 `href` 时它渲染成 `<a>`)——
   **不再自己写按钮样式**,见 `openapi-verification.md` §68。 */

/* ── 边界说明 ───────────────────────────────────────────────────────────── */
.notice {
  margin: var(--space-4) 0 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
/* 空态:插图与文案由 antd 的 `Empty` 出,这里只给容器底色 */
.empty {
  padding: var(--space-6);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
  text-align: center;
}
</style>
