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
.wrap {
  display: flex;
  justify-content: center;
  padding-top: 5rem;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 20rem;
  padding: 1.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
}

h1 {
  margin: 0;
  font-size: 1.15rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}

input {
  padding: 0.4rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
}

.error {
  margin: 0;
  color: #b91c1c;
  font-size: 0.9rem;
}

button {
  padding: 0.5rem;
  border: 0;
  border-radius: 0.25rem;
  background: #1d4ed8;
  color: white;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
