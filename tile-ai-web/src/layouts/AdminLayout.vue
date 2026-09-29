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
.shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 12rem;
  flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  padding: 1rem 0.75rem;
}

.brand {
  font-weight: 600;
  padding: 0 0.5rem 0.75rem;
}

.sidebar nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.item {
  padding: 0.4rem 0.5rem;
  border-radius: 0.25rem;
  color: #374151;
  text-decoration: none;
}

.item--active {
  background: #eff6ff;
  color: #1d4ed8;
}

.content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1.25rem;
  border-bottom: 1px solid #e5e7eb;
}

.role {
  margin-left: 0.5rem;
  color: #6b7280;
  font-style: normal;
  font-size: 0.85em;
}

.page {
  padding: 1.25rem;
}
</style>
