<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { createAccount, deleteAccount, listAccounts, updateAccount } from '@/api/admin/accounts'
import type { AdminUserUpsert } from '@/api/admin/accounts'
import { validationErrorsOf } from '@/api/errors'
import type { ErrorItem } from '@/api/errors'
import type { AdminUserView, Role } from '@/api/types'
import { useAdminAuthStore } from '@/stores/adminAuth'

/**
 * 账号与权限(F-0.2)。后台里**权限最高的一个页面** —— 整组只有 `account:manage`。
 *
 * 三条要记住的:
 *
 * ① **编辑时密码留空 = 不修改**,所以构造请求体时**整个 `password` 键都不能带**
 *    (见 `buildBody`)。传 `password: ''` 不是"不改",会撞上服务端的密码校验。
 *
 * ② **两条防呆在服务端**,页面只是"别给出点了必然被拒的入口":
 *    自己那一行不给删除按钮;编辑自己时给一句提示。**服务端才是边界**
 *    —— 它用库里的现值判断,不看前端传了什么。
 *
 * ③ **删除是真删除**。删掉之后,那个账号写过的历史日志里「操作人」会变成空
 *    (日志页显示"已删除账号 #N")。
 */
const auth = useAdminAuthStore()

const rows = ref<AdminUserView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const loading = ref(false)
const errorText = ref('')
const notice = ref('')
const fieldErrors = ref<ErrorItem[]>([])

const canManage = computed(() => auth.has('account:manage'))
/** 当前登录者自己的 id —— 用来在界面上避开"对自己动手"。 */
const myId = computed(() => auth.user?.id ?? null)

const ROLE_LABELS: Record<Role, string> = {
  super_admin: '超级管理员',
  operator: '运营',
  editor: '内容编辑',
}

// ---------------------------------------------------------------- 内联表单
// 项目里没有弹窗组件,新增/编辑都用页面底部的内联面板(与字典页同款)。
const formOpen = ref(false)
/** null = 正在新增;有值 = 正在编辑该账号 */
const editingId = ref<number | null>(null)
const username = ref('')
const password = ref('')
const role = ref<Role>('operator')
const status = ref<'enabled' | 'disabled'>('enabled')
const saving = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)
const isEditingSelf = computed(() => editingId.value !== null && editingId.value === myId.value)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listAccounts({ page: page.value, size: size.value })
    rows.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goPage(target: number) {
  if (target < 1 || target > totalPages.value) return
  page.value = target
  load()
}

function clearMessages() {
  errorText.value = ''
  notice.value = ''
  fieldErrors.value = []
}

function startCreate() {
  clearMessages()
  formOpen.value = true
  editingId.value = null
  username.value = ''
  password.value = ''
  role.value = 'operator'
  status.value = 'enabled'
}

function startEdit(row: AdminUserView) {
  clearMessages()
  formOpen.value = true
  editingId.value = row.id
  username.value = row.username
  password.value = ''
  role.value = row.role
  status.value = row.status ?? 'enabled'
}

function cancelForm() {
  formOpen.value = false
  editingId.value = null
  password.value = ''
}

/**
 * 构造请求体。
 *
 * **编辑时密码留空 → 连键都不带**(展开语法),而不是传空串。
 * 传空串会让服务端把它当成"要改成空密码"从而走到校验分支,而不是"不改"。
 */
function buildBody(): AdminUserUpsert {
  return {
    username: username.value.trim(),
    role: role.value,
    status: status.value,
    ...(password.value ? { password: password.value } : {}),
  }
}

async function save() {
  clearMessages()
  if (!username.value.trim()) {
    errorText.value = '用户名不能为空'
    return
  }
  if (editingId.value === null && !password.value) {
    errorText.value = '新增账号必须填密码'
    return
  }

  saving.value = true
  try {
    if (editingId.value === null) {
      await createAccount(buildBody())
      notice.value = `已新增账号:${username.value.trim()}`
    } else {
      await updateAccount(editingId.value, buildBody())
      notice.value = `已保存:${username.value.trim()}`
    }
    cancelForm()
    await load()
  } catch (error) {
    fieldErrors.value = validationErrorsOf(error)
    errorText.value = fieldErrors.value.length ? '' : error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function remove(row: AdminUserView) {
  if (!window.confirm(`确认删除账号 ${row.username}?该操作不可撤销。`)) return

  clearMessages()
  try {
    await deleteAccount(row.id)
    notice.value = `已删除:${row.username}`
    // 删掉的正好是表单里正在编辑的那一个 —— 关掉表单,免得保存时撞 30001
    if (editingId.value === row.id) cancelForm()
    await load()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '删除失败'
  }
}

/** 与「我的方案」同款:没有公共格式化工具,项目里就是这么写的。 */
function timeOf(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

onMounted(load)
</script>

<template>
  <section>
    <header class="head">
      <h1>账号与权限</h1>
      <button v-if="canManage" type="button" class="primary" @click="startCreate">新增账号</button>
    </header>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <ul v-if="fieldErrors.length" class="errors" role="alert">
      <li v-for="item in fieldErrors" :key="item.field">{{ item.field }}:{{ item.reason }}</li>
    </ul>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <p v-if="loading" class="hint">加载中…</p>

    <table v-else-if="rows.length">
      <thead>
        <tr>
          <th>用户名</th>
          <th>角色</th>
          <th>状态</th>
          <th>创建时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>
            {{ row.username }}
            <span v-if="row.id === myId" class="hint">(你自己)</span>
          </td>
          <td>{{ ROLE_LABELS[row.role] ?? row.role }}</td>
          <td>
            <span :class="['tag', row.status === 'disabled' ? 'tag--off' : 'tag--on']">
              {{ row.status === 'disabled' ? '已停用' : '启用中' }}
            </span>
          </td>
          <td>{{ timeOf(row.createdAt) }}</td>
          <td class="actions">
            <button v-if="canManage" type="button" @click="startEdit(row)">编辑</button>
            <button
              v-if="canManage"
              type="button"
              class="danger"
              :disabled="row.id === myId"
              :title="row.id === myId ? '不能删除自己' : ''"
              @click="remove(row)"
            >
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-else class="hint">还没有后台账号。</p>

    <footer v-if="total > 0" class="pager">
      <button type="button" :disabled="!hasPrev" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页,共 {{ total }} 条</span>
      <button type="button" :disabled="!hasNext" @click="goPage(page + 1)">下一页</button>
    </footer>

    <form v-if="canManage && formOpen" class="panel" @submit.prevent="save">
      <h2>{{ editingId === null ? '新增账号' : `编辑账号 #${editingId}` }}</h2>

      <p v-if="isEditingSelf" class="hint">
        这是你自己 —— 不能把自己停用(服务端会拒绝),也不能删除自己。
      </p>

      <label>
        用户名
        <input v-model="username" type="text" maxlength="50" required />
      </label>
      <label>
        密码
        <input
          v-model="password"
          type="password"
          :placeholder="editingId === null ? '必填,6-20 位' : '留空则不修改'"
        />
      </label>
      <label>
        角色
        <select v-model="role">
          <option value="super_admin">超级管理员</option>
          <option value="operator">运营</option>
          <option value="editor">内容编辑</option>
        </select>
      </label>
      <label>
        状态
        <select v-model="status">
          <option value="enabled">启用</option>
          <option value="disabled">停用</option>
        </select>
      </label>

      <div class="panel-actions">
        <button type="submit" class="primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button type="button" @click="cancelForm">取消</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试逐字断言按钮文案(`新增账号` / `编辑` / `删除`),并靠
   **`form.panel`** 这个选择器定位表单与其中的 input,所以
   `form` 上的 `panel` class **必须保留**。 */

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
}

h1 {
  margin: 0;
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 700;
  color: var(--color-text);
}

h2 {
  margin: 0 0 var(--space-3);
  font-size: var(--text-base);
  line-height: var(--leading-base);
  font-weight: 600;
  color: var(--color-text);
}

/* ── 表单控件 ───────────────────────────────────────────────────────────── */
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

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
button {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* 禁用的删除按钮有 title 说明原因("不能删除自己")—— 弱化但仍可见 */
button:disabled {
  opacity: 0.45;
  cursor: default;
}

.primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: 600;
}

.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
  color: var(--color-on-primary);
}

/* 删除账号是危险且不可撤销的 —— 危险色 + 明确的文字 */
.danger {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.danger:hover:not(:disabled) {
  background: var(--color-danger-soft);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

/* ── 账号表 ─────────────────────────────────────────────────────────────── */
table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  font-size: var(--text-sm);
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

.actions {
  display: flex;
  gap: var(--space-1);
}

/* 状态标签:启用中=暖绿、已停用=中性灰。**停用不是错误,不用红色** */
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

/* ── 分页 ───────────────────────────────────────────────────────────────── */
.pager {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  justify-content: center;
  margin-top: var(--space-5);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

/* ── 内联表单(新增 / 编辑)─────────────────────────────────────────────────
   ⚠️ 这个 `panel` class 被测试用作定位器(`form.panel`),不能改名。 */
.panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: 26rem;
  margin-top: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.panel label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

.panel-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-1);
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

.errors {
  margin: var(--space-3) 0;
  padding-left: var(--space-5);
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

.hint {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}
</style>
