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
    <header class="page-head">
      <h1 class="page-head__title">概览</h1>
    </header>

    <dl class="facts">
      <dt>账号</dt>
      <dd>{{ auth.user?.username }}</dd>
      <dt>角色</dt>
      <dd>{{ auth.role }}</dd>
      <dt>可见菜单</dt>
      <dd>{{ menu.length }} 项</dd>
    </dl>

    <h2>当前角色的权限点(架构 §4.4)</h2>
    <p class="tags">
      <span v-for="permission in granted" :key="permission" class="tag">{{ permission }}</span>
    </p>

    <template v-if="denied.length">
      <h2>此角色不可做的</h2>
      <p class="tags">
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
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 本页**没有测试用例** —— 改动无测试可证,只能等人眼验收(纪律 7)。 */

.page-head {
  margin-bottom: var(--space-4);
}

.page-head__title {
  margin: 0;
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 700;
  color: var(--color-text);
}

h2 {
  margin: var(--space-5) 0 var(--space-2);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  font-weight: 600;
  color: var(--color-text-secondary);
}

/* ── 账号事实 ───────────────────────────────────────────────────────────── */
.facts {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-2) var(--space-5);
  margin: 0;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
}

.facts dt {
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.facts dd {
  margin: 0;
  color: var(--color-text);
}

/* ── 权限点 ─────────────────────────────────────────────────────────────── */
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: 0;
}

.tag {
  display: inline-block;
  padding: 0.1rem var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
}

/* 不可做的用中性灰 —— **不靠红色**,它不是"错误",只是"不归你管" */
.tag--off {
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
}

.hint {
  margin: var(--space-3) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}

.hint strong {
  color: var(--color-text-secondary);
}
</style>
