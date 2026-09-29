<script setup lang="ts">
import { computed } from 'vue'

import { visibleMenu } from '@/auth/adminMenu'
import { PERMISSIONS, ROLE_PERMISSIONS } from '@/auth/permissions'
import { useAdminAuthStore } from '@/stores/adminAuth'

/**
 * 后台概览(F-0.2)。
 *
 * 刻意把**当前角色的权限点**列出来:架构 §4.4 的「权限点 → 角色」对照表
 * 是这个项目里最容易搞错的地方(它被 v1.4 专门修过一次,当时契约与描述互相矛盾),
 * 让登录的人一眼看到"我这个角色到底能做什么",比藏在文档里可靠。
 */
const auth = useAdminAuthStore()

const menu = computed(() => visibleMenu((permissions) => auth.hasAny(permissions)))
const granted = computed(() => (auth.role ? ROLE_PERMISSIONS[auth.role] : []))
const denied = computed(() => PERMISSIONS.filter((permission) => !granted.value.includes(permission)))
</script>

<template>
  <section>
    <h1>概览</h1>

    <dl class="facts">
      <dt>账号</dt>
      <dd>{{ auth.user?.username }}</dd>
      <dt>角色</dt>
      <dd>{{ auth.role }}</dd>
      <dt>可见菜单</dt>
      <dd>{{ menu.length }} 项</dd>
    </dl>

    <h2>当前角色的权限点(架构 §4.4)</h2>
    <p>
      <span v-for="permission in granted" :key="permission" class="tag">{{ permission }}</span>
    </p>

    <template v-if="denied.length">
      <h2>此角色不可做的</h2>
      <p>
        <span v-for="permission in denied" :key="permission" class="tag tag--off">{{
          permission
        }}</span>
      </p>
      <p class="hint">
        前端隐藏入口只为避免误导;<strong>真正的拦截在服务端</strong>(403 / 10004)。
      </p>
    </template>
  </section>
</template>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 1.15rem;
}

h2 {
  font-size: 0.95rem;
  color: #374151;
}

.facts {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.25rem 1rem;
  margin: 0 0 1.5rem;
}

.facts dt {
  color: #6b7280;
}

.facts dd {
  margin: 0;
}

.tag {
  display: inline-block;
  margin: 0 0.25rem 0.25rem 0;
  padding: 0.1rem 0.4rem;
  border-radius: 0.25rem;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.8rem;
  font-family: ui-monospace, monospace;
}

.tag--off {
  background: #f3f4f6;
  color: #9ca3af;
}

.hint {
  color: #6b7280;
  font-size: 0.875rem;
}
</style>
