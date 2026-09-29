<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 后台各模块的占位页。
 *
 * 存在的意义:让菜单与**权限门控**现在就能验证 —— 不同角色看到的菜单不同,
 * 这件事不依赖任何具体页面实现。F-0.3 ~ F-0.9 逐个落地时,
 * 把路由里的 component 换成真实页面即可,本文件随之退场。
 *
 * 刻意显示任务卡 ID:看到这个页面的人一眼知道该看哪个任务,
 * 而不是以为"这功能坏了"。
 */
const route = useRoute()
const title = computed(() => (route.meta.title as string | undefined) ?? '未命名')
const task = computed(() => (route.meta.task as string | undefined) ?? '')
</script>

<template>
  <section>
    <h1>{{ title }}</h1>
    <p class="hint">
      该页面由任务 <strong>{{ task }}</strong> 交付,尚未实现。
    </p>
    <p class="hint">
      后台对应的 API 已就绪(见 <code>openapi-admin.yaml</code>),
      路由与权限门控也已可用 —— 缺的只是界面本身。
    </p>
  </section>
</template>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 1.15rem;
}

.hint {
  color: #6b7280;
}
</style>
