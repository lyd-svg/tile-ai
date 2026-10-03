<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAdminAuthStore } from '@/stores/adminAuth'

/**
 * 后台登录(F-0.2)。
 *
 * 登录失败**一律**显示同一个提示(20005「账号或密码错误」)——
 * 契约规定不区分「用户名不存在」与「密码错误」,否则这就是一个账号枚举接口。
 * 前端不要自作聪明地去分辨,服务端也不会告诉它。
 */
const auth = useAdminAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const errorText = ref('')

async function submit() {
  errorText.value = ''
  if (!username.value || !password.value) {
    errorText.value = '请填写用户名与密码'
    return
  }

  try {
    await auth.login(username.value, password.value)
    // 回到登录前想去的页面;没带就进后台首页
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : { name: 'admin-home' })
  } catch (error) {
    // 文案取自契约错误码表(BusinessError 已把 code 转成可读文案)
    errorText.value = error instanceof Error ? error.message : '登录失败,请重试'
  }
}
</script>

<template>
  <div class="wrap">
    <form class="card" @submit.prevent="submit">
      <h1>后台登录</h1>

      <label>
        用户名
        <input v-model="username" type="text" autocomplete="username" />
      </label>

      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>

      <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>

      <button type="submit" :disabled="auth.loading">
        {{ auth.loading ? '登录中…' : '登录' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 本页由路由直接渲染,**不套 `AdminLayout`** —— 所以它拿不到 layout 的上下文,
   主题由 `App.vue` 按 `/admin` 前缀挂的 `data-theme="admin"` 提供。
   ⚠️ 测试约束:`findAll('input')` 顺序必须是 用户名 → 密码;
   `find('button').text()` **必须正好是「登录」** ⇒ 这里**不能加第二个按钮**。 */

.wrap {
  display: flex;
  justify-content: center;
  padding: var(--space-8) var(--space-4);
}

.card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 20rem;
  padding: var(--space-6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

h1 {
  margin: 0;
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: 700;
  color: var(--color-text);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

input:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

.error {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
}

button {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-standard);
}

button:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
