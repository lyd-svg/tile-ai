<script setup lang="ts">
import { useRouter } from 'vue-router'

import { useUserAuthStore } from '@/stores/userAuth'

/**
 * C 端(业主)外壳。
 *
 * 三条选砖路径的入口都放这里:直接浏览 / 推荐搭配 / **AI 咨询**。
 * (AI 咨询的那个入口曾经因为"后端未实现"而留空,后端 T-1.6 与页面 F-1.6
 * 均已于 2026-10-02 落地,入口随之挂上。)
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

    <nav class="links">
      <RouterLink to="/products">选砖</RouterLink>
      <RouterLink to="/combos">推荐搭配</RouterLink>
      <RouterLink to="/consult">问 AI</RouterLink>
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
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 本外壳**没有测试覆盖** —— 改动的正确性只能靠人眼验收(见纪律 7)。
   ⚠️ 这是 C 端**唯一的**顶栏:`App.vue` 里那条全站导航已于 2026-10-03 删除
   (原先两条叠着,见 `openapi-verification.md` §67.3)。 */

/* ── 顶栏:吸顶 ─────────────────────────────────────────────────────────── */
.nav {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: var(--space-3) var(--space-5);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

/* 品牌:左侧一个陶土色小方块作标记 —— 用伪元素,不额外加 DOM */
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-lg);
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text);
  text-decoration: none;
  white-space: nowrap;
}

.brand::before {
  content: '';
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 3px;
  background: var(--color-primary);
}

/* ── 导航项 ─────────────────────────────────────────────────────────────── */
.links {
  display: flex;
  flex: 1;
  gap: var(--space-1);
  min-width: 0;
}

.links a {
  position: relative;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: var(--text-sm);
  white-space: nowrap;
  transition:
    color var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.links a:hover {
  background: var(--color-surface-sunken);
  color: var(--color-text);
}

/* 当前页:陶土色 + 下方 2px 指示条(用 ::after 而非 border ——
   加 border 会把这一项往下撑 2px,整条导航会抖)。 */
.links a.router-link-active {
  color: var(--color-primary);
  font-weight: 600;
}

.links a.router-link-active::after {
  content: '';
  position: absolute;
  left: var(--space-3);
  right: var(--space-3);
  bottom: 0;
  height: 2px;
  border-radius: 2px;
  background: var(--color-primary);
}

.links a:focus-visible,
.brand:focus-visible,
.account a:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}

/* ── 账号区 ─────────────────────────────────────────────────────────────── */
.account {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-left: auto;
}

.who {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.account a {
  color: var(--color-primary);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.account a:hover {
  text-decoration: underline;
}

.account button {
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-full);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-standard),
    color var(--dur-fast) var(--ease-standard);
}

.account button:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* ── 页面容器 ───────────────────────────────────────────────────────────── */
.main {
  max-width: 72rem;
  margin: 0 auto;
  padding: var(--space-6) var(--space-5);
}

/* ── 页脚 ───────────────────────────────────────────────────────────────── */
.foot {
  max-width: 72rem;
  margin: 0 auto;
  padding: var(--space-6) var(--space-5);
  border-top: 1px solid var(--color-border);
  text-align: center;
}

.foot a {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  text-decoration: none;
}

.foot a:hover {
  color: var(--color-primary);
}

/* ── 窄屏:导航换行,不做汉堡(那是给有测试钉着的后台留的取舍)─────────── */
@media (max-width: 900px) {
  .nav {
    flex-wrap: wrap;
    gap: var(--space-3);
  }

  .links {
    order: 3;
    width: 100%;
    overflow-x: auto;
  }
}
</style>
