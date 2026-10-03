<script setup lang="ts">
import { Alert, Button, Skeleton, Table } from 'ant-design-vue'
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

    <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
    <Alert v-if="notice" class="alert-gap" type="success" :message="notice" show-icon />
    <ul v-if="fieldErrors.length" class="errors">
      <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
    </ul>

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <Table
      v-else
      class="param-table"
      size="small"
      :data-source="rows"
      :pagination="false"
      :row-key="(row: RowState) => row.key"
    >
      <Table.Column title="参数" key="label" />
      <Table.Column title="当前值" key="value" />
      <Table.Column title="填入" key="input" />
      <Table.Column title="说明" key="hint" />
      <Table.Column title="操作" key="actions" />

      <!-- ⚠️ 这一页**不加 `record as RowState`**:`record` 是 `Record<string, any>`,
           `record.value` / `record.input` 都是 `any` —— 正好绕开"`v-else` 无法
           为 `number | null` 做窄化"的麻烦。只有要喂给 `save()` 的那一处需要断言。 -->
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'label'">
          <strong>{{ record.label }}</strong>
          <div class="key">{{ record.key }}</div>
        </template>
        <template v-else-if="column.key === 'value'">
          <span v-if="record.value === null" class="hint">未配置</span>
          <template v-else>
            {{ record.value }}
            <span v-if="record.key !== 'aux_unit_price'" class="hint">
              (≈ {{ percentHint(record.value) }})
            </span>
          </template>
        </template>
        <template v-else-if="column.key === 'input'">
          <input
            v-model="record.input"
            type="text"
            inputmode="decimal"
            :placeholder="record.value === null ? '未配置' : String(record.value)"
          />
          <span class="unit">{{ record.unit }}</span>
        </template>
        <template v-else-if="column.key === 'hint'">
          <span class="hint">{{ record.hint }}</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <Button v-if="canWrite" :loading="savingKey === record.key" @click="save(record as RowState)">
            保存
          </Button>
        </template>
      </template>
    </Table>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试靠 `findAll('tr')` 找含某个 paramKey 的行,并在行内找 `input` / `button`,
   还断言行文案含 `元/㎡` 与 `0.06 表示 6%`,所以行的**结构必须原样**。
   ⚠️ 这一页**直接影响报价能否算出**(辅料单价缺了整单不出)——
   所以量纲单位与小数口径的提示保持醒目,不做弱化。 */

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

h1 {
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

.hint b {
  color: var(--color-text-secondary);
}

/* 「辅料单价未配置 ⇒ 整单不出」—— 这是警告,用暖黄,与"错误"区分开 */
.warn {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-warning);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-text);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
}

.warn b {
  color: var(--color-warning);
}


.errors {
  margin: var(--space-3) 0;
  padding-left: var(--space-5);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

/* ── 参数表 ─────────────────────────────────────────────────────────────── */
/* ⚠️ 已迁 antd `Table`(§72)。原来那套裸 `table` / `th` / `td` 必须删掉 ——
   antd 渲染出来的**正是**这些标签(§5.6 同一个坑)。 */

/* 参数键是代码 —— 等宽,和展示名一眼分开 */
.key {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--text-xs);
}

/* ⚠️ 原来是 `td input` —— 迁到 antd `Table` 后 `td` 由**库**渲染,
   这样写等于在改库的元素(§5.6)。收窄到本页自己的表格容器。 */
.param-table input {
  width: 8rem;
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.param-table input:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

/* 单位紧跟输入框 —— 填错量纲不报错,只会算出一份离谱报价,所以它必须显眼 */
.unit {
  margin-left: var(--space-2);
  color: var(--color-text);
  font-size: var(--text-xs);
  font-weight: 600;
}

/* ⚠️ **每行那个「保存」已迁 antd** —— 不再写样式(§70.9)。
   原来这里是**裸 `button {}`** —— 那会照样命中 antd 渲染出的 `<button class="ant-btn">`,
   所以**整块删掉**(这正是 `tile-ai-design.md` §5.6 说的陷阱)。 */
</style>
