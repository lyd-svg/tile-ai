<script setup lang="ts">
import { Skeleton } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'

import { createDict, deleteDict, listAdminDicts, updateDict } from '@/api/admin/dicts'
import type { Dict, DictType, DictUpsert } from '@/api/admin/dicts'
import { validationErrorsOf } from '@/api/errors'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { appConfirm } from '@/components/base/AppConfirm'

/**
 * 数据字典(F-0.8 下半)。空间类型与装修风格由这里维护,**前端不硬编码**
 * (架构 §10.1 · D-14):产品表单里的"适用空间 / 风格标签"就是从这批数据来的。
 *
 * ## 三处与 C 端不同
 *
 * 1. 走 `/admin/dicts`(要后台会话),不是公开的 `/dicts`;
 * 2. **这里看得见停用项** —— 管理端返回包含 `status=disabled` 的项,运营要能把它们改回来;
 * 3. 这里能写。
 *
 * ## 切页签走**服务端过滤**
 *
 * 契约的 `GET /dicts?type=` 就是干这个的;在拿到结果后再前端筛一遍等于第二份实现,
 * 而且会让"服务端到底给了什么"变得看不见。
 *
 * ## 编辑时 `type` 只读
 *
 * 把已被引用的 key 从"空间"悄悄挪到"风格"是很容易出事的一步(引用它的产品不会跟着变)。
 * 契约没禁止改,所以这是**界面上的保守取舍**,不是服务端规则。
 */
const auth = useAdminAuthStore()

const type = ref<DictType>('space')
const rows = ref<Dict[]>([])

const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const notice = ref('')
const fieldErrors = ref<string[]>([])

/** 正在编辑的那条;`null` 表示"新增" */
const editingId = ref<number | null>(null)
const form = reactive<DictUpsert>({ type: 'space', key: '', label: '', sort: undefined, status: 'enabled' })

const canWrite = computed(() => auth.has('dict:write'))
const isEditing = computed(() => editingId.value !== null)
const typeLabel = computed(() => (type.value === 'space' ? '空间' : '风格'))

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    rows.value = await listAdminDicts(type.value)
  } catch (error) {
    rows.value = []
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function switchType(next: DictType) {
  if (type.value === next) return
  type.value = next
  resetForm()
  load()
}

function resetForm() {
  editingId.value = null
  form.type = type.value
  form.key = ''
  form.label = ''
  form.sort = undefined
  form.status = 'enabled'
}

function startEdit(row: Dict) {
  errorText.value = ''
  notice.value = ''
  fieldErrors.value = []
  editingId.value = row.id ?? null
  form.type = row.type
  form.key = row.key
  form.label = row.label
  form.sort = row.sort
  form.status = row.status === 'disabled' ? 'disabled' : 'enabled'
}

function buildBody(): DictUpsert {
  const sort = form.sort
  return {
    type: form.type,
    key: form.key.trim(),
    label: form.label.trim(),
    // 排序没填就**不发这个键** —— 契约里它可省,发个 null 反而会让服务端为难
    ...(typeof sort === 'number' && Number.isFinite(sort) ? { sort } : {}),
    status: form.status,
  }
}

function validate(): string[] {
  const problems: string[] = []
  if (!form.key.trim()) problems.push('存储键:必填')
  if (!form.label.trim()) problems.push('展示名:必填')
  return problems
}

async function submit() {
  notice.value = ''
  errorText.value = ''
  fieldErrors.value = validate()
  if (fieldErrors.value.length) return

  saving.value = true
  try {
    if (editingId.value !== null) {
      await updateDict(editingId.value, buildBody())
      notice.value = '已更新'
    } else {
      await createDict(buildBody())
      notice.value = '已新增'
    }
    resetForm()
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

async function remove(row: Dict) {
  if (row.id === undefined) return
  if (
    !(await appConfirm({
      title: '确认删除字典项',
      content: `确认删除「${row.label}」(${row.key})?该操作不可撤销。`,
      okText: '删除',
      danger: true,
    }))
  ) {
    return
  }

  notice.value = ''
  errorText.value = ''
  try {
    await deleteDict(row.id)
    notice.value = `已删除:${row.label}`
    if (editingId.value === row.id) resetForm()
    await load()
  } catch (error) {
    // 被产品 / 搭配 / 方案引用时服务端回 30002,message 会说清是什么在引用 ——
    // **原样透出**,不要吞成"删除失败"(那用户不知道该怎么办)
    errorText.value = error instanceof Error ? error.message : '删除失败'
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="head">
      <h1>数据字典</h1>
    </header>

    <p class="hint">
      空间类型与装修风格由这里维护 —— 产品、搭配、方案的这些字段都引用这里的<strong>存储键</strong>。
    </p>

    <nav class="tabs">
      <button type="button" :class="{ active: type === 'space' }" @click="switchType('space')">
        空间
      </button>
      <button type="button" :class="{ active: type === 'style' }" @click="switchType('style')">
        风格
      </button>
    </nav>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <ul v-if="fieldErrors.length" class="errors">
      <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
    </ul>

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <table v-else-if="rows.length">
      <thead>
        <tr>
          <th>展示名</th>
          <th>存储键</th>
          <th class="num">排序</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id ?? row.key">
          <td>{{ row.label }}</td>
          <td class="key">{{ row.key }}</td>
          <td class="num">{{ row.sort ?? '—' }}</td>
          <td>
            <span v-if="!row.status" class="hint">—</span>
            <span v-else :class="['tag', row.status === 'enabled' ? 'tag--on' : 'tag--off']">
              {{ row.status === 'enabled' ? '启用' : '停用' }}
            </span>
          </td>
          <td class="actions">
            <button v-if="canWrite" type="button" @click="startEdit(row)">编辑</button>
            <button v-if="canWrite" type="button" class="danger" @click="remove(row)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-else class="hint">暂无{{ typeLabel }}字典项。</p>

    <form v-if="canWrite" class="panel" @submit.prevent="submit">
      <h2>{{ isEditing ? `编辑${typeLabel}字典项` : `新增${typeLabel}字典项` }}</h2>

      <label>
        类别
        <!-- 编辑时只读:把已被引用的键挪到另一个命名空间很容易出事(界面守卫,不是服务端规则) -->
        <select v-model="form.type" :disabled="isEditing">
          <option value="space">空间</option>
          <option value="style">风格</option>
        </select>
      </label>

      <label>
        存储键(对外提交值)
        <input v-model="form.key" type="text" placeholder="如 living_room" />
      </label>

      <label>
        展示名
        <input v-model="form.label" type="text" placeholder="如 客厅" />
      </label>

      <label>
        排序(可选,越小越前)
        <input v-model.number="form.sort" type="number" />
      </label>

      <label>
        状态
        <select v-model="form.status">
          <option value="enabled">启用</option>
          <option value="disabled">停用</option>
        </select>
      </label>

      <div class="actions">
        <button type="submit" class="primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button v-if="isEditing" type="button" @click="resetForm">取消编辑</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块。
   ⚠️ 测试断言 `.tag--off` **恰好 1 个** —— 样式改了不影响计数,但不能新增该 class。 */

.head h1 {
  margin: 0 0 var(--space-2);
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 700;
  color: var(--color-text);
}

.hint {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}

.hint strong {
  color: var(--color-text-secondary);
}

/* ── 页签 ───────────────────────────────────────────────────────────────── */
.tabs {
  display: flex;
  gap: var(--space-2);
  margin: var(--space-4) 0;
}

.tabs button {
  height: var(--control-height);
  padding: 0 var(--space-4);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-full);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.tabs button:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.tabs button.active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
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

/* ── 字典表 ─────────────────────────────────────────────────────────────── */
table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}

th,
td {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--color-border);
  text-align: left;
}

th {
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
}

tbody tr:hover {
  background: var(--color-bg);
}

.num {
  text-align: right;
}

/* 存储键是代码,用等宽 —— 与展示名一眼可分 */
.key {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

/* 状态标签:启用=暖绿、停用=中性灰。**停用不是错误,不用红色** */
.tag {
  display: inline-block;
  padding: 0.05rem var(--space-2);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  white-space: nowrap;
}

.tag--on {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.tag--off {
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
}

/* ── 行内操作 ───────────────────────────────────────────────────────────── */
.actions {
  display: flex;
  gap: var(--space-1);
}

.actions button {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard);
}

.actions button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.actions button.danger {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.actions button.danger:hover:not(:disabled) {
  background: var(--color-danger-soft);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

/* ── 编辑 / 新增表单 ────────────────────────────────────────────────────── */
.panel {
  max-width: 30rem;
  margin-top: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.panel h2 {
  margin: 0 0 var(--space-4);
  font-size: var(--text-base);
  line-height: var(--leading-base);
  font-weight: 600;
  color: var(--color-text);
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

input,
select {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

input:focus-visible,
select:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

select:disabled {
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

button.primary {
  height: var(--control-height);
  padding: 0 var(--space-4);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
}

button.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

button.primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
