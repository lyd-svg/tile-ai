<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { listQuoteParams, upsertQuoteParam } from '@/api/admin/quoteParams'
import type { QuoteParamKey } from '@/api/admin/quoteParams'
import { validationErrorsOf } from '@/api/errors'
import { useAdminAuthStore } from '@/stores/adminAuth'

/**
 * 报价参数(F-0.8 上半)。**仅 super_admin 可见**(菜单权限已就位)。
 *
 * ## 为什么是"固定四行"而不是"按服务端返回渲染列表"
 *
 * 参数是**四个固定键**,不是可自由增删的数据。而且现在服务端**只返回两条**
 * (损耗率上下限)—— 按返回渲染的话,缺失的 `aux_unit_price` 会**整行消失**,
 * 而"报价算不出来"恰恰就是因为它没配。固定四行保证每个键**永远看得见、填得上**。
 *
 * ## 两个量纲(D-15 / D-19)
 *
 * - `aux_unit_price` 是 **元/㎡** —— 单位必须显示出来:填错量纲**不会报错**,
 *   只会算出一份离谱的报价;
 * - 三个损耗率是**小数**,`0.06` = 6%。服务端**拒绝**百分数写法(填 `6` 会被拒),
 *   所以这里也按小数录入,**不在前端做换算** —— 换算等于把量纲错误藏进一层转换里。
 *   旁边的百分比只是**只读提示**。
 *
 * ## 没有"新增/删除"
 *
 * 契约只有 GET 与 PUT(按 `paramKey` upsert)。要"清空某个键",契约里没有路径 ——
 * 真需要就回提契约,不在这里私造。
 */
const auth = useAdminAuthStore()

/** 四个键的展示元数据。**顺序即界面行序**(是界面的常量,不是对服务端返回的排序)。 */
interface RowSpec {
  key: QuoteParamKey
  label: string
  unit: string
  hint: string
}

const ROWS: readonly RowSpec[] = [
  {
    key: 'aux_unit_price',
    label: '辅料单价',
    unit: '元/㎡',
    hint: '量纲是「元/㎡」。填错量纲不会报错,只会算出一份离谱的报价。',
  },
  { key: 'loss_rate', label: '损耗率(当前采用)', unit: '小数', hint: '小数口径:0.06 表示 6%。' },
  { key: 'loss_rate_min', label: '损耗率下限', unit: '小数', hint: '不应大于上限。' },
  { key: 'loss_rate_max', label: '损耗率上限', unit: '小数', hint: '不应小于下限。' },
]

interface RowState extends RowSpec {
  /** 输入框里的原文(字符串)。**没配过就是空串,不是 '0'** */
  input: string
  /** 服务端当前的值。**没配过是 null,不是 0** */
  value: number | null
}

const rows = ref<RowState[]>(ROWS.map((spec) => ({ ...spec, input: '', value: null })))

const loading = ref(false)
const savingKey = ref<QuoteParamKey | null>(null)
const errorText = ref('')
const notice = ref('')
const fieldErrors = ref<string[]>([])

const canWrite = computed(() => auth.has('quote-param:write'))

/** 辅料单价没配 ⇒ 报价根本算不出来。这是这一页最该喊出来的一件事。 */
const missingAuxPrice = computed(
  () => rows.value.find((row) => row.key === 'aux_unit_price')?.value === null,
)

/** 只读的百分比提示。值不在 (0,1) 时显示 `—`,**绝不显示 0%**。 */
function percentHint(value: number | null): string {
  if (value === null || !Number.isFinite(value) || value <= 0 || value >= 1) return '—'
  // 0.06 * 100 在浮点下是 6.000000000000001 —— 四舍五入到两位小数再显示
  return `${Number((value * 100).toFixed(2))}%`
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const list = await listQuoteParams()
    const byKey = new Map(list.map((item) => [item.paramKey, item.paramValue]))
    rows.value = rows.value.map((row) => {
      const value = byKey.get(row.key)
      return {
        ...row,
        value: value === undefined ? null : value,
        input: value === undefined ? '' : String(value),
      }
    })
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 本地校验。返回错误文案,通过则返回空串。 */
function validate(row: RowState): string {
  const text = row.input.trim()
  if (text === '') return '请填写数值(未配置不会自动按 0 处理)'
  const value = Number(text)
  if (!Number.isFinite(value)) return '不是有效数字'
  if (row.key === 'aux_unit_price') return value < 0 ? '不能为负' : ''
  return value <= 0 || value >= 1 ? '损耗率是小数,应落在 0 与 1 之间(0.06 表示 6%)' : ''
}

async function save(row: RowState) {
  const invalid = validate(row)
  fieldErrors.value = invalid ? [`${row.label}:${invalid}`] : []
  if (invalid) return

  notice.value = ''
  errorText.value = ''
  savingKey.value = row.key
  try {
    await upsertQuoteParam({ paramKey: row.key, paramValue: Number(row.input.trim()) })
    notice.value = `已保存:${row.label}`
    await load()
  } catch (error) {
    // 服务端拒绝百分数写法等情况会回 10001 —— 逐字段展开,让用户知道该改哪一格
    const details = validationErrorsOf(error)
    if (details.length) {
      fieldErrors.value = details.map((item) => `${item.field}:${item.reason}`)
    } else {
      errorText.value = error instanceof Error ? error.message : '保存失败'
    }
  } finally {
    savingKey.value = null
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="head">
      <h1>报价参数</h1>
    </header>

    <p class="hint">
      报价计算用的全局参数。<b>只有这四项</b>,没有新增与删除 —— 未配置的用「保存」补上即可。
    </p>

    <p v-if="missingAuxPrice" class="warn" role="alert">
      辅料单价尚未配置 —— 报价会因为算不出辅料费而<b>整单不出</b>。填上它,报价才能算。
    </p>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <ul v-if="fieldErrors.length" class="errors">
      <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
    </ul>

    <p v-if="loading" class="hint">加载中…</p>

    <table v-else>
      <thead>
        <tr>
          <th>参数</th>
          <th>当前值</th>
          <th>填入</th>
          <th>说明</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.key">
          <td>
            <strong>{{ row.label }}</strong>
            <div class="key">{{ row.key }}</div>
          </td>
          <td>
            <span v-if="row.value === null" class="hint">未配置</span>
            <template v-else>
              {{ row.value }}
              <span v-if="row.key !== 'aux_unit_price'" class="hint">
                (≈ {{ percentHint(row.value) }})
              </span>
            </template>
          </td>
          <td>
            <input v-model="row.input" type="text" inputmode="decimal" :placeholder="row.value === null ? '未配置' : String(row.value)" />
            <span class="unit">{{ row.unit }}</span>
          </td>
          <td class="hint">{{ row.hint }}</td>
          <td>
            <button
              v-if="canWrite"
              type="button"
              :disabled="savingKey === row.key"
              @click="save(row)"
            >
              {{ savingKey === row.key ? '保存中…' : '保存' }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

h1 {
  margin: 0 0 0.5rem;
  font-size: 1.3rem;
}

.hint {
  color: #6b7280;
  font-size: 0.8rem;
}

.warn {
  padding: 0.5rem 0.75rem;
  border: 1px solid #fcd34d;
  border-radius: 0.25rem;
  background: #fffbeb;
  color: #92400e;
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

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.5rem;
  text-align: left;
  vertical-align: top;
}

th {
  color: #374151;
  font-weight: 600;
}

.key {
  color: #9ca3af;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.75rem;
}

td input {
  width: 8rem;
  padding: 0.3rem 0.4rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
}

.unit {
  margin-left: 0.35rem;
  color: #374151;
  font-size: 0.8rem;
}

button {
  padding: 0.35rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
