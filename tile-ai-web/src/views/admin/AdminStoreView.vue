<script setup lang="ts">
import { Button, Skeleton } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'

import { getAdminStore, updateStore } from '@/api/admin/store'
import { BusinessError, ErrorCode, validationErrorsOf } from '@/api/errors'
import { useAdminAuthStore } from '@/stores/adminAuth'

/**
 * 门店信息(F-0.9)。
 *
 * 单店单条(PRD §7.6):**只有 GET 与 PUT**,没有新增/删除,页面上也不引入 id ——
 * 重复提交仍是整体更新,服务端保证只有一条。
 *
 * ## 一处必须当心的地方:`GET` 在"还没配置"时返回 **30001**
 *
 * 那是**"还没有门店信息",不是故障**。第一反应很可能是把它当错误显示成红字 +
 * 表单禁用 —— 那样**新环境永远配不上第一家店**。所以这里把它当"空"处理:
 * 表单留空、给一句"填好保存即可"。
 */
const auth = useAdminAuthStore()

const form = reactive({
  name: '',
  address: '',
  phone: '',
  mapUrl: '',
})

const loading = ref(false)
/** 服务端说"还没有" —— 与"加载失败"是两件事 */
const notConfigured = ref(false)
const saving = ref(false)
const errorText = ref('')
const notice = ref('')
const fieldErrors = ref<string[]>([])

const canWrite = computed(() => auth.has('store:write'))

async function load() {
  loading.value = true
  errorText.value = ''
  notConfigured.value = false
  try {
    const store = await getAdminStore()
    form.name = store.name ?? ''
    form.address = store.address ?? ''
    form.phone = store.phone ?? ''
    form.mapUrl = store.mapUrl ?? ''
  } catch (error) {
    if (error instanceof BusinessError && error.code === ErrorCode.NOT_FOUND) {
      // 「还没配」不是错 —— 留空表单让用户填
      notConfigured.value = true
      return
    }
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 组装请求体:trim;**可选字段为空时整个键不发**(契约要求省略而不是 null / 空串)。 */
function buildBody() {
  const mapUrl = form.mapUrl.trim()
  return {
    name: form.name.trim(),
    address: form.address.trim(),
    phone: form.phone.trim(),
    ...(mapUrl ? { mapUrl } : {}),
  }
}

function validate(): string[] {
  const problems: string[] = []
  if (!form.name.trim()) problems.push('门店名称:必填')
  if (!form.address.trim()) problems.push('地址:必填')
  if (!form.phone.trim()) problems.push('电话:必填')
  return problems
}

async function submit() {
  notice.value = ''
  errorText.value = ''
  fieldErrors.value = validate()
  if (fieldErrors.value.length) return

  saving.value = true
  try {
    await updateStore(buildBody())
    notice.value = '已保存'
    notConfigured.value = false
    await load()
  } catch (error) {
    const details = validationErrorsOf(error)
    if (details.length) {
      fieldErrors.value = details.map((item) => `${item.field}:${item.reason}`)
    } else {
      errorText.value = error instanceof Error ? error.message : '保存失败'
    }
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="head">
      <h1>门店信息</h1>
    </header>

    <p v-if="notConfigured" class="hint">门店信息尚未配置,填写后保存即可。</p>
    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <ul v-if="fieldErrors.length" class="errors">
      <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
    </ul>

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <form v-else class="panel" @submit.prevent="submit">
      <label>
        门店名称
        <input v-model="form.name" type="text" :readonly="!canWrite" />
      </label>
      <label>
        地址
        <input v-model="form.address" type="text" :readonly="!canWrite" />
      </label>
      <label>
        电话
        <input v-model="form.phone" type="text" :readonly="!canWrite" />
      </label>
      <label>
        地图链接(可选)
        <input v-model="form.mapUrl" type="text" :readonly="!canWrite" />
      </label>

      <div class="actions">
        <Button v-if="canWrite" type="primary" html-type="submit" :loading="saving">保存</Button>
        <span v-else class="hint">当前账号没有修改门店信息的权限。</span>
      </div>
    </form>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试依赖 `findAll('input')` 的顺序、`find('form')` 提交、`[role="alert"]`、
   `.errors li` 恰好 3 条,以及「尚未配置」这段文案。 */

.head h1 {
  margin: 0 0 var(--space-2);
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 700;
  color: var(--color-text);
}

.hint {
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text-secondary);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
.error {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

.notice {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-success);
  border-radius: var(--radius-sm);
  background: var(--color-success-soft);
  color: var(--color-success);
  font-size: var(--text-sm);
}

.errors {
  margin: var(--space-3) 0;
  padding-left: var(--space-5);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

/* ── 表单卡 ─────────────────────────────────────────────────────────────── */
.panel {
  max-width: 28rem;
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-bottom: var(--space-4);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

input {
  height: var(--control-height);
  padding: 0 var(--space-3);
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

/* 无权限时输入框是 readonly —— 用浅底提示"改不了",而不是让它看起来可编辑 */
input[readonly] {
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

/* ⚠️ **「保存」已迁 antd** —— 不再写样式(§70.9)。 */
</style>
