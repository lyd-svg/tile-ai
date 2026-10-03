<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'

import {
  MAX_IMPORT_FILE_BYTES,
  acceptImport,
  errorsToCsv,
  getImportStatus,
  productImportTemplateUrl,
} from '@/api/admin/productImport'
import type { ImportMode, ImportRowError, ImportStatus } from '@/api/admin/productImport'
import { BusinessError, ErrorCode } from '@/api/errors'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { downloadTextFile } from '@/utils/download'

/**
 * 产品库批量导入(F-0.4)。
 *
 * ## 导入是**异步**的
 *
 * `POST` 只受理、返回一个批次号,真正的处理在服务端继续跑,前端靠**轮询**看进度。
 * ⇒ **离开页面停的是轮询,不是导入** —— 批次会照跑完。这一点在界面上如实说明,
 * 不假装"关掉就取消"。
 *
 * ## 轮询照 `GenerateView.vue` 那套
 *
 * **递归 `setTimeout`(不是 `setInterval`)** —— 慢请求下 `setInterval` 会叠罗汉;
 * 到终态就**不再排下一次**;卸载时 `cancelPolling`。
 *
 * ## 导出的错误报告是**前端生成的**
 *
 * 契约里有个 `errorReportUrl`,但后端**永远给 null**(对象存储未选型),
 * 所以这里**不画那个链接**(画了就是个死链),改为拿 `errors[]` 现场生成 CSV。
 * ⚠️ 生成时带 UTF-8 BOM,否则 Excel 打开中文乱码 —— 而运营是要在 Excel 里改完再传的。
 */
const auth = useAdminAuthStore()

const POLL_INTERVAL_MS = 1500
const MAX_TRANSIENT_FAILURES = 10

const MODES: { value: ImportMode; label: string; hint: string }[] = [
  { value: 'upsert', label: '覆盖更新(upsert)', hint: '编号已存在就更新它' },
  { value: 'insert_only', label: '仅新增(insert_only)', hint: '编号已存在就记为错误行,不动原数据' },
]

const mode = ref<ImportMode>('upsert')
const file = ref<File | null>(null)

const uploading = ref(false)
const batchId = ref('')
const status = ref<ImportStatus | null>(null)
const errorText = ref('')
const notice = ref('')

const canImport = computed(() => auth.has('product:import'))

const errors = computed<ImportRowError[]>(() => status.value?.errors ?? [])
const isTerminal = computed(
  () => status.value?.status === 'success' || status.value?.status === 'failed',
)
const statusLabel = computed(() => {
  switch (status.value?.status) {
    case 'pending':
      return '已排队'
    case 'running':
      return '处理中…'
    case 'success':
      return '已完成'
    case 'failed':
      return '已失败'
    default:
      return ''
  }
})

let timer: ReturnType<typeof setTimeout> | null = null
/** 卸载/重开时置真 —— 回包后先看它,再决定要不要排下一次 */
let stopped = false
let transientFailures = 0

function cancelPolling() {
  stopped = true
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
}

function schedule(id: string) {
  timer = setTimeout(() => {
    void tick(id)
  }, POLL_INTERVAL_MS)
}

async function tick(id: string) {
  if (stopped) return

  try {
    const view = await getImportStatus(id)
    if (stopped) return
    transientFailures = 0
    status.value = view
    if (view.status === 'success' || view.status === 'failed') {
      cancelPolling()
      return
    }
  } catch (error) {
    if (stopped) return
    if (error instanceof BusinessError && error.code === ErrorCode.NOT_FOUND) {
      // 批次不存在是**终态**,不是抖动 —— 重试一万次也还是没有
      errorText.value = error.message
      cancelPolling()
      return
    }
    transientFailures += 1
    if (transientFailures >= MAX_TRANSIENT_FAILURES) {
      errorText.value = '与服务器失去联系,请稍后重试。'
      cancelPolling()
      return
    }
  }

  if (!stopped) schedule(id)
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
  errorText.value = ''
  notice.value = ''
}

async function start() {
  errorText.value = ''
  notice.value = ''
  if (!file.value) {
    errorText.value = '请先选择文件。'
    return
  }
  // 客户端先挡一道:超限的话服务端会拒,但让用户白等一次不划算
  if (file.value.size > MAX_IMPORT_FILE_BYTES) {
    errorText.value = `文件超过上限(${Math.round(MAX_IMPORT_FILE_BYTES / 1024 / 1024)}MB),请拆分后重试。`
    return
  }

  // 上一批还在轮询就先停掉,免得两批的进度互相覆盖
  cancelPolling()
  stopped = false
  transientFailures = 0
  uploading.value = true
  status.value = null

  try {
    const accepted = await acceptImport(file.value, mode.value)
    batchId.value = accepted.batchId
    notice.value = `已受理,批次号 ${accepted.batchId}`
    schedule(accepted.batchId)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '上传失败'
  } finally {
    uploading.value = false
  }
}

function exportErrors() {
  if (!errors.value.length) return
  downloadTextFile(`product-import-errors-${batchId.value || 'batch'}.csv`, errorsToCsv(errors.value))
}

onBeforeUnmount(cancelPolling)
</script>

<template>
  <section>
    <header class="head">
      <h1>批量导入</h1>
    </header>

    <p class="hint">
      用 Excel / CSV 批量导入产品。<b>导入是异步的</b> —— 提交后可以离开这一页,
      后台会继续跑完;但要回到这里看进度和错误行。
    </p>

    <div class="panel">
      <fieldset :disabled="uploading">
        <legend>导入设置</legend>

        <label v-for="option in MODES" :key="option.value" class="radio">
          <input v-model="mode" type="radio" :value="option.value" />
          <span>{{ option.label }}<small class="hint"> —— {{ option.hint }}</small></span>
        </label>

        <label>
          选择文件(.xlsx / .csv)
          <input type="file" accept=".xlsx,.csv" @change="onFileChange" />
        </label>

        <p class="hint">
          模板下载:
          <a :href="productImportTemplateUrl">导入模板(xlsx)</a>
          —— 填好后上传,错误行不会被写入,会列在下面供你改完重传。
        </p>
      </fieldset>

      <div class="actions">
        <button v-if="canImport" type="button" class="primary" :disabled="uploading" @click="start">
          {{ uploading ? '上传中…' : '开始导入' }}
        </button>
        <span v-else class="hint">当前账号没有批量导入权限。</span>
      </div>
    </div>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <div v-if="batchId" class="panel">
      <h2>
        批次 {{ batchId }}
        <span v-if="status" class="tag">{{ statusLabel }}</span>
      </h2>

      <p v-if="status">
        总数 {{ status.total ?? '—' }} · 成功 {{ status.successCount ?? '—' }} ·
        失败 {{ status.failedCount ?? '—' }}
      </p>
      <p v-else class="hint">等待服务端受理…</p>

      <template v-if="errors.length">
        <h3>错误行({{ errors.length }})</h3>
        <table>
          <thead>
            <tr>
              <th class="num">行号</th>
              <th>原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in errors" :key="`${item.row}-${index}`">
              <td class="num">{{ item.row === 0 ? '批次级' : item.row }}</td>
              <td>{{ item.reason }}</td>
            </tr>
          </tbody>
        </table>

        <div class="actions">
          <button type="button" @click="exportErrors">导出错误报告(CSV)</button>
        </div>
        <p class="hint">导出的就是上面这些行(行号 + 原因),改完那一行再传一次即可。</p>
      </template>

      <p v-else-if="isTerminal && status?.status === 'success'" class="hint">
        没有错误行,全部导入成功。
      </p>
    </div>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试依赖 `find('input[type="file"]')`、`[role="alert"]`,以及
   `findAll('a')` 里**必须含「模板」、且不含「错误报告」**
   (错误报告是**前端用 errors[] 现场生成的 CSV**,不是链接)。 */

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

.hint a {
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

/* ── 面板 ───────────────────────────────────────────────────────────────── */
.panel {
  margin-bottom: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.panel h2 {
  margin: 0 0 var(--space-3);
  font-size: var(--text-base);
  line-height: var(--leading-base);
  font-weight: 600;
  color: var(--color-text);
}

.panel h3 {
  margin: var(--space-4) 0 var(--space-2);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  font-weight: 600;
  color: var(--color-text);
}

fieldset {
  margin: 0;
  padding: 0;
  border: none;
}

legend {
  padding: 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  max-width: 32rem;
  margin-bottom: var(--space-4);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

label.radio {
  flex-direction: row;
  align-items: baseline;
  gap: var(--space-2);
  max-width: none;
  font-size: var(--text-sm);
}

label.radio small {
  font-size: var(--text-xs);
}

input[type='file'] {
  font: inherit;
  font-size: var(--text-sm);
  color: var(--color-text);
}

/* ── 批次状态标签 ───────────────────────────────────────────────────────── */
.tag {
  margin-left: var(--space-2);
  padding: 0.05rem var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  font-weight: 400;
}

/* ── 错误行表格 ─────────────────────────────────────────────────────────── */
table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
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
}

/* 行号右对齐并禁止换行("批次级"也不该被拆成两行) */
.num {
  text-align: right;
  white-space: nowrap;
}

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
.actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

button {
  height: var(--control-height);
  padding: 0 var(--space-4);
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

button.primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: 600;
}

button.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
  color: var(--color-on-primary);
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
