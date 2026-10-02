<script setup lang="ts">
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

    <p v-if="loading" class="hint">加载中…</p>

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
        <button v-if="canWrite" type="submit" class="primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <span v-else class="hint">当前账号没有修改门店信息的权限。</span>
      </div>
    </form>
  </section>
</template>

<style scoped>
.head h1 {
  margin: 0 0 0.5rem;
  font-size: 1.3rem;
}

.hint {
  color: #6b7280;
  font-size: 0.85rem;
}

.error {
  padding: 0.5rem 0.75rem;
  border: 1px solid #fca5a5;
  border-radius: 0.25rem;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 0.85rem;
}

.notice {
  padding: 0.5rem 0.75rem;
  border: 1px solid #bbf7d0;
  border-radius: 0.25rem;
  background: #f0fdf4;
  color: #166534;
  font-size: 0.85rem;
}

.errors {
  margin: 0.5rem 0;
  padding-left: 1.2rem;
  color: #b91c1c;
  font-size: 0.85rem;
}

.panel {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  padding: 1rem;
  max-width: 28rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  font-size: 0.85rem;
  color: #374151;
}

input {
  padding: 0.4rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

button.primary {
  padding: 0.5rem 1rem;
  border: 1px solid #1d4ed8;
  border-radius: 0.25rem;
  background: #1d4ed8;
  color: white;
  cursor: pointer;
}

button.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
