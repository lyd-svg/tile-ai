<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useUserAuthStore } from '@/stores/userAuth'

/**
 * C 端登录 / 注册(F-1.1)。
 *
 * 登录方式:**用户名 + 密码**(v0.7 起)。**不要再引入手机号 / 短信 / 验证码** ——
 * 短信通道已取消(架构 §15.6)。
 *
 * 登录失败**一律**显示同一句话(20005),**不区分「用户名不存在」与「密码错误」** ——
 * 区分开等于对外提供一个账号枚举接口。前端不要自作聪明地去分辨,服务端也不会告诉它。
 *
 * 登录成功后会回到 `?redirect=` 指向的页面 —— 生成页会在守卫里把人送到这里,
 * 登录后直接送回去,而不是一律进首页。
 *
 * 登录态**只存在内存**,但刷新后靠 `GET /auth/me` 回查(详见 stores/userAuth.ts)——
 * **不要**为了让界面"看起来还登着"而把身份缓存到 localStorage/sessionStorage。
 */
const auth = useUserAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const errorText = ref('')

const isRegister = computed(() => mode.value === 'register')
const title = computed(() => (isRegister.value ? '注册' : '登录'))

function switchMode() {
  mode.value = isRegister.value ? 'login' : 'register'
  errorText.value = ''
}

async function submit() {
  errorText.value = ''

  if (!username.value || !password.value) {
    errorText.value = '请填写用户名与密码'
    return
  }
  // 密码 6–20 位(契约 §5.1)。前端先挡一道,省一次往返;服务端仍会校验(20004)。
  if (isRegister.value && (password.value.length < 6 || password.value.length > 20)) {
    errorText.value = '密码需为 6–20 位'
    return
  }

  try {
    if (isRegister.value) await auth.register(username.value, password.value)
    else await auth.login(username.value, password.value)

    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : { name: 'home' })
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '操作失败,请重试'
  }
}
</script>

<template>
  <section class="wrap">
    <form class="card" @submit.prevent="submit">
      <h1>{{ title }}</h1>

      <label>
        用户名
        <input v-model="username" type="text" autocomplete="username" />
      </label>

      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" />
        <small v-if="isRegister">6–20 位</small>
      </label>

      <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>

      <button type="submit" class="primary" :disabled="auth.loading">
        {{ auth.loading ? '处理中…' : title }}
      </button>

      <p class="switch">
        {{ isRegister ? '已经有账号?' : '还没有账号?' }}
        <a href="#" @click.prevent="switchMode">{{ isRegister ? '去登录' : '去注册' }}</a>
      </p>

      <p class="notice">浏览免登录;生成、保存、问 AI 才需要登录。</p>
    </form>
  </section>
</template>

<style scoped>
.wrap {
  display: flex;
  justify-content: center;
  padding-top: 3rem;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 22rem;
  padding: 1.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
}

h1 {
  margin: 0;
  font-size: 1.2rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}

small {
  color: #6b7280;
}

input {
  padding: 0.4rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
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
}

.switch {
  margin: 0;
  font-size: 0.9rem;
  color: #6b7280;
}

.switch a {
  color: #1d4ed8;
}

.notice {
  margin: 0;
  color: #9ca3af;
  font-size: 0.8rem;
}

.error {
  margin: 0;
  color: #b91c1c;
  font-size: 0.9rem;
}
</style>
