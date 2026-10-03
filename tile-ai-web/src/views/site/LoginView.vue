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
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试依赖三条结构约定:
     ① `findAll('input')` 的**顺序** = 用户名、密码;
     ② `find('form')` 能触发提交;
     ③ `findAll('a')[0]` 是**登录/注册切换链接** —— 所以这里**不许再加任何链接**。 */

.wrap {
  display: flex;
  justify-content: center;
  padding: var(--space-7) var(--space-4);
}

.card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 22rem;
  padding: var(--space-6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

h1 {
  margin: 0;
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
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

small {
  color: var(--color-text-muted);
}

input {
  padding: var(--space-3) var(--space-4);
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
  cursor: not-allowed;
}

.switch {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.switch a {
  color: var(--color-primary);
  font-weight: 600;
}

.notice {
  margin: 0;
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
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
</style>
