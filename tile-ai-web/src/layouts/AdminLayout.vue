<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { visibleMenu } from '@/auth/adminMenu'
import { useAdminAuthStore } from '@/stores/adminAuth'

/**
 * 后台外壳:左侧菜单 + 顶部账号信息。
 *
 * 菜单按**权限点**过滤(架构 §4.4)。再强调一次:隐藏入口不是安全边界,
 * 越权与否由服务端判(403 / 10004)。这里只是别给出点了必然失败的按钮。
 */
const auth = useAdminAuthStore()
const router = useRouter()

const menu = computed(() => visibleMenu((permissions) => auth.hasAny(permissions)))

async function handleLogout() {
  // 先等服务端登出(v1.9 起有真接口),再跳登录页 —— 顺序反了会让
  // 未清的会话残留着,用户手动回退又能进去
  await auth.logout()
  router.push({ name: 'admin-login' })
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">后台管理</div>
      <nav>
        <RouterLink
          v-for="item in menu"
          :key="item.name"
          :to="item.path"
          class="item"
          active-class="item--active"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <div class="content">
      <header class="topbar">
        <span class="who">
          {{ auth.user?.username }}
          <em class="role">{{ auth.role }}</em>
        </span>
        <button type="button" @click="handleLogout">退出登录</button>
      </header>

      <main class="page">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   主题由 `App.vue` 按 `/admin` 前缀挂上 `data-theme="admin"`,自动取 B 端那套
   更紧凑的密度与更小的圆角(`tile-ai-design.md` §4)。

   ⚠️ **两条测试约束,别动**:
     ① `findAll('a')` 会取菜单项的文字与 href —— 不要新增文字里含菜单名的链接;
     ② `find('button')` **必须是「退出登录」那一个** ⇒ **不能加汉堡菜单之类的按钮**。
        所以窄屏下侧栏不做抽屉折叠(那是被测试钉住的取舍,不是遗漏)。 */

.shell {
  display: flex;
  min-height: 100vh;
  background: var(--color-bg);
}

/* ── 侧栏 ───────────────────────────────────────────────────────────────── */
.sidebar {
  width: 12rem;
  flex-shrink: 0;
  padding: var(--space-4) var(--space-3);
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
}

.brand {
  padding: 0 var(--space-2) var(--space-4);
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--color-text);
}

.sidebar nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.item {
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: var(--text-sm);
  transition:
    background var(--dur-fast) var(--ease-standard),
    color var(--dur-fast) var(--ease-standard);
}

.item:hover {
  background: var(--color-surface-sunken);
  color: var(--color-text);
}

.item--active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}

/* ── 内容区 ─────────────────────────────────────────────────────────────── */
.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.who {
  font-size: var(--text-sm);
  color: var(--color-text);
}

.role {
  margin-left: var(--space-2);
  padding: 0.05rem var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font-style: normal;
  font-size: var(--text-xs);
}

.topbar button {
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard);
}

.topbar button:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.page {
  flex: 1;
  padding: var(--space-5);
}
</style>
