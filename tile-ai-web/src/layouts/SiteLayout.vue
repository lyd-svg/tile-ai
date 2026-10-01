<script setup lang="ts">
import { useRouter } from 'vue-router'

import { useUserAuthStore } from '@/stores/userAuth'

/**
 * C 端(业主)外壳。
 *
 * 三条选砖路径的入口放这里:直接浏览 / 推荐搭配 / AI 咨询。
 * 后两者中 **AI 咨询(T-1.6)后端未实现**,故先不放入口 —— 点了只会 404,
 * 不如等做出来再挂,免得演示时点出一个坏页面。
 */
const auth = useUserAuthStore()
const router = useRouter()

async function handleLogout() {
  await auth.logout()
  router.push({ name: 'home' })
}
</script>

<template>
  <header class="nav">
    <RouterLink class="brand" to="/">瓷砖选购 AI</RouterLink>

    <nav>
      <RouterLink to="/products">选砖</RouterLink>
      <RouterLink to="/combos">推荐搭配</RouterLink>
      <RouterLink to="/layout">录户型</RouterLink>
      <RouterLink to="/plans">我的方案</RouterLink>
      <RouterLink to="/store">门店</RouterLink>
    </nav>

    <div class="account">
      <template v-if="auth.isAuthenticated">
        <span class="who">{{ auth.displayName }}</span>
        <button type="button" @click="handleLogout">退出</button>
      </template>
      <RouterLink v-else to="/login">登录 / 注册</RouterLink>
    </div>
  </header>

  <main class="main">
    <RouterView />
  </main>

  <footer class="foot">
    <RouterLink to="/admin">后台管理</RouterLink>
  </footer>
</template>

<style scoped>
.nav {
  display: flex;
  gap: 1.5rem;
  align-items: center;
  padding: 0.75rem 1.25rem;
  border-bottom: 1px solid #e5e7eb;
}

.brand {
  font-weight: 600;
  color: #111827;
  text-decoration: none;
}

.nav nav {
  display: flex;
  gap: 1rem;
  flex: 1;
}

.nav a {
  color: #374151;
  text-decoration: none;
}

.nav a.router-link-active {
  color: #1d4ed8;
  font-weight: 600;
}

.account {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.who {
  color: #6b7280;
  font-size: 0.9rem;
}

button {
  padding: 0.25rem 0.6rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

.main {
  padding: 1.25rem;
  max-width: 68rem;
  margin: 0 auto;
}

.foot {
  padding: 1.25rem;
  text-align: center;
  border-top: 1px solid #e5e7eb;
  margin-top: 2rem;
}

.foot a {
  color: #9ca3af;
  font-size: 0.85rem;
  text-decoration: none;
}
</style>
