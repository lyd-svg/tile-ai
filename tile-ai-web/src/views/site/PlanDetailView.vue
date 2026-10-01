<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { copyPlan, deletePlan, getPlan, recalcPlan, renamePlan } from '@/api/c/plans'
import { listDictsOfType } from '@/api/dicts'
import { validationErrorsOf } from '@/api/errors'
import type { ErrorItem } from '@/api/errors'
import type { PlanDetail, PlanImage, QuoteResult } from '@/api/types'

/**
 * 方案详情(F-1.9 + F-1.8)。需登录。
 *
 * 这是本轮**唯一的管理入口** —— 改名 / 另存为 / 删除 / 按当前价重算四个动作
 * 全在这一页。列表页只读,免得同一个动作有两份实现、各自漂。
 *
 * ⚠️ **三处必须守住的诚实边界**(都是"改错了不报错,只悄悄出错"):
 *
 *   1. `PlanPart.materialFee` **契约标必填、实现会整个省略**。后端 `PlanPartView`
 *      带 `@JsonInclude(NON_NULL)` —— 报价算不出来时 JSON 里根本没有这个键。
 *      所以每个数字格都要 `?? '—'`,**不能 `?? 0`**(那会显示成 ¥0,看着像真的)。
 *   2. `quote` / `totalAmount` 可能整个没有 —— 多空间方案会走"这一次不算价"的分支
 *      (§41 / §42.5)。**没有报价是一种正常状态,不是错误**,要给出可读的空态,
 *      而且**重算按钮仍然可用**:它存在的意义就是把"当时算不出"变成"现在能算"。
 *   3. 改名有 200 字的长度上限,但**契约里没声明**(`openapi-c.yaml` 的 `name` 只有
 *      `type: string`),上限只在后端 `PlanNames.NAME_MAX` 里按**码点**判,超了报 10001。
 *      所以这里按码点先挡一道(`[...name].length`,不是 `name.length` ——
 *      后者把 100 个 emoji 数成 200 而误判),服务端的 10001 也照常渲染出来。
 */
const route = useRoute()
const router = useRouter()

/**
 * 路径里的方案 id。
 *
 * ⚠️ `Number.isInteger` 必须在**发请求之前**判掉:直接把 `/plans/abc` 的参数拼进
 * URL 会打出一个必然失败的请求,而用户看到的是一句莫名其妙的加载失败。
 */
const planId = computed<number | null>(() => {
  const raw = route.params.id
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const plan = ref<PlanDetail | null>(null)
/** 改名输入框的内容。加载详情后回填当前名字。 */
const nameDraft = ref('')
const loading = ref(false)
const errorText = ref('')
const notice = ref('')
const fieldErrors = ref<ErrorItem[]>([])

const spaceLabels = ref<Map<string, string>>(new Map())
const styleLabels = ref<Map<string, string>>(new Map())

/** 部位中文名 —— 与其它 C 端页面一样各自定义一份(项目无公共展示常量模块)。 */
const PART_LABELS: Record<string, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部',
}

/** `t_plan.name` 的上限,**按码点**。与后端 `PlanNames.NAME_MAX` 同值。 */
const NAME_MAX = 200

// ---------------------------------------------------------------- 加载

function clearMessages() {
  errorText.value = ''
  notice.value = ''
  fieldErrors.value = []
}

async function load() {
  if (planId.value === null) {
    errorText.value = '方案编号不正确'
    plan.value = null
    return
  }
  loading.value = true
  clearMessages()
  try {
    plan.value = await getPlan(planId.value)
    nameDraft.value = plan.value?.name ?? ''
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    plan.value = null
  } finally {
    loading.value = false
  }
}

// 详情页之间互相跳(另存为之后跳去新方案)时路由参数变、组件实例被复用,
// onMounted **不会**再跑一次 —— 所以必须监听参数本身,否则会一直显示旧方案。
watch(planId, () => {
  void load()
})

onMounted(async () => {
  try {
    const [spaces, styles] = await Promise.all([
      listDictsOfType('space'),
      listDictsOfType('style'),
    ])
    spaceLabels.value = new Map(spaces.map((item) => [item.key, item.label]))
    styleLabels.value = new Map(styles.map((item) => [item.key, item.label]))
  } catch {
    // 拿不到字典就退化成显示 key,不影响这一页的任何操作
  }
  await load()
})

// ---------------------------------------------------------------- 展示

function labelOf(labels: Map<string, string>, key?: string): string {
  if (!key) return ''
  return labels.get(key) ?? key
}

function partLabel(part: string): string {
  return PART_LABELS[part] ?? part
}

/** 数字格:缺失显示 `—`。**不是 0** —— 见文件头第 1 条。 */
function num(value: number | undefined | null): string {
  return value === undefined || value === null ? '—' : String(value)
}

const cover = computed<PlanImage | null>(() => {
  const list = plan.value?.images ?? []
  if (!list.length) return null
  return list.find((image) => image.isCover) ?? list[0]
})

const thumbs = computed(() =>
  (plan.value?.images ?? []).filter((image) => image.url !== cover.value?.url),
)

/** 视角名 → 中文标签。**按 view 区分,不靠数组下标猜**(与 GenerateView 同口径)。 */
function viewLabel(view?: string): string {
  if (!view || view === 'default') return '正面'
  const matched = /^angle(\d+)$/.exec(view)
  if (matched) return `角度 ${matched[1]}`
  return view
}

const spaces = computed(() => plan.value?.layout?.spaces ?? [])
const parts = computed(() => plan.value?.parts ?? [])
const quote = computed(() => plan.value?.quote ?? null)
const metaText = computed(() =>
  [
    labelOf(spaceLabels.value, plan.value?.space),
    labelOf(styleLabels.value, plan.value?.style),
  ]
    .filter(Boolean)
    .join(' · '),
)

// ---------------------------------------------------------------- 改名

const savingName = ref(false)

/** 保存按钮可不可点:空的不发,超长的也不发。 */
const canSaveName = computed(
  () => !savingName.value && nameDraft.value.trim() !== '' && plan.value !== null,
)

async function saveName() {
  if (planId.value === null) return
  clearMessages()

  const next = nameDraft.value.trim()
  if (!next) {
    errorText.value = '方案名不能为空'
    return
  }
  // 按**码点**数长度 —— `next.length` 数的是 UTF-16 单元,会把 100 个 emoji
  // 数成 200 而误判超长(后端按码点,两处必须用同一把尺子)。
  if ([...next].length > NAME_MAX) {
    errorText.value = `方案名最多 ${NAME_MAX} 个字`
    return
  }

  savingName.value = true
  try {
    await renamePlan(planId.value, next)
    if (plan.value) plan.value = { ...plan.value, name: next }
    // 输入框回填**去掉首尾空白后的**那个名字 —— 否则用户看到的还是自己敲的原样,
    // 而列表/详情里已经是修剪过的版本
    nameDraft.value = next
    notice.value = '已改名'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '改名失败'
    // 10001 会把逐字段原因放在 data.errors[] —— 原样渲染,不要吞掉
    fieldErrors.value = validationErrorsOf(error)
  } finally {
    savingName.value = false
  }
}

// ---------------------------------------------------------------- 重算

const recalculating = ref(false)

async function recalc() {
  if (planId.value === null) return
  clearMessages()

  recalculating.value = true
  try {
    const result: QuoteResult = await recalcPlan(planId.value)
    // 契约写明 recalc 的 `data` **就是**新的 QuoteResult,所以就地刷新即可 ——
    // 再 getPlan 一轮是白打的请求,而且中间那一拍会闪一下旧金额。
    if (plan.value) {
      plan.value = {
        ...plan.value,
        quote: result,
        // 后端同一次 UPDATE 里也把 total_amount 覆盖成了新总额、quote_expired 置回 false
        // (PlanRecalcService),前端照做,免得页面上两个数打架。
        totalAmount: result.total,
        quoteExpired: false,
      }
    }
    notice.value = '已按当前价格重算'
  } catch (error) {
    // 重算失败一律 90001「方案在,坏的是系统」(§41.4)—— 原样透出后端文案,
    // 不要自己编一句,后端那句才说得清是哪一步算不出来。
    errorText.value = error instanceof Error ? error.message : '重算失败'
  } finally {
    recalculating.value = false
  }
}

// ---------------------------------------------------------------- 另存为 / 删除

const busy = ref(false)

async function copyAsNew() {
  if (planId.value === null) return
  clearMessages()

  busy.value = true
  try {
    const created = await copyPlan(planId.value)
    // 跳到新方案 —— 契约只回 `{ planId }`,新名字由服务端定(形如「原名(副本)」),
    // 拉一遍详情自然就有了。
    await router.push({ name: 'plan-detail', params: { id: created.planId } })
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '另存为失败'
  } finally {
    busy.value = false
  }
}

async function remove() {
  if (planId.value === null) return
  // 二次确认在前端 —— 契约把这句话写在 DELETE 的 description 里(PRD §5.6)
  if (!window.confirm(`确认删除「${plan.value?.name ?? ''}」?该操作不可撤销。`)) return

  clearMessages()
  busy.value = true
  try {
    await deletePlan(planId.value)
    await router.push({ name: 'plans' })
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '删除失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section>
    <p v-if="loading" class="hint">加载中…</p>

    <p v-else-if="errorText && !plan" class="error" role="alert">{{ errorText }}</p>

    <template v-else-if="plan">
      <header class="head">
        <h1>{{ plan.name }}</h1>
        <span v-if="metaText" class="meta">{{ metaText }}</span>
      </header>

      <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
      <ul v-if="fieldErrors.length" class="errors">
        <li v-for="item in fieldErrors" :key="item.field">{{ item.field }}:{{ item.reason }}</li>
      </ul>
      <p v-if="notice" class="notice">{{ notice }}</p>

      <!-- ---------------------------- 效果图 ---------------------------- -->
      <section class="block">
        <h2>效果图</h2>
        <template v-if="cover">
          <div class="main">
            <img :src="cover.url" :alt="viewLabel(cover.view)" />
            <p class="caption">{{ viewLabel(cover.view) }}</p>
          </div>
          <div v-if="thumbs.length" class="thumbs">
            <figure v-for="image in thumbs" :key="image.url">
              <img :src="image.url" :alt="viewLabel(image.view)" />
              <figcaption>{{ viewLabel(image.view) }}</figcaption>
            </figure>
          </div>
        </template>
        <p v-else class="hint">这份方案没有效果图。</p>
      </section>

      <!-- ---------------------------- 户型与部位 ---------------------------- -->
      <section class="block">
        <h2>户型与部位</h2>

        <ul v-if="spaces.length" class="spaces">
          <li v-for="(space, index) in spaces" :key="index">
            <strong>{{ space.name || labelOf(spaceLabels, space.type) }}</strong>
            {{ space.length }} × {{ space.width }}m · 层高 {{ space.height }}m
          </li>
        </ul>
        <p v-else class="hint">没有户型快照。</p>

        <table v-if="parts.length" class="part-table">
          <thead>
            <tr>
              <th>部位</th>
              <th>瓷砖</th>
              <th class="num">有效面积(㎡)</th>
              <th class="num">单价(元/㎡)</th>
              <th class="num">材料费(元)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in parts" :key="index">
              <td>
                {{ partLabel(row.part) }}
                <span v-if="row.source === 'ai'" class="tag">AI 补齐</span>
              </td>
              <td>{{ row.productName || row.sku }}</td>
              <td class="num">{{ num(row.area) }}</td>
              <td class="num">{{ num(row.pricePerSqm) }}</td>
              <td class="num">{{ num(row.materialFee) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="hint">这份方案没有部位记录。</p>
      </section>

      <!-- ---------------------------- 报价(F-1.8) ---------------------------- -->
      <section class="block">
        <header class="quote-head">
          <h2>报价</h2>
          <button type="button" :disabled="recalculating" @click="recalc">
            {{ recalculating ? '重算中…' : '按当前价重算' }}
          </button>
        </header>

        <p v-if="plan.quoteExpired" class="stale">
          产品调过价了,下面这个金额是生成当时的价 —— 点「按当前价重算」可以刷新。
        </p>

        <template v-if="quote">
          <table class="quote-table">
            <thead>
              <tr>
                <th>部位</th>
                <th>瓷砖</th>
                <th class="num">毛面积(㎡)</th>
                <th class="num">有效面积(㎡)</th>
                <th class="num">单价(元/㎡)</th>
                <th class="num">材料费(元)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in quote.items" :key="index">
                <td>{{ partLabel(item.part) }}</td>
                <td>{{ item.sku }}</td>
                <td class="num">{{ num(item.grossArea) }}</td>
                <td class="num">{{ num(item.effectiveArea) }}</td>
                <td class="num">{{ num(item.pricePerSqm) }}</td>
                <td class="num">{{ num(item.materialFee) }}</td>
              </tr>
            </tbody>
          </table>

          <dl class="totals">
            <dt>材料费合计</dt>
            <dd>¥{{ quote.materialTotal }}</dd>
            <dt>辅料费合计</dt>
            <dd>¥{{ quote.auxTotal }}</dd>
            <dt class="grand">总计</dt>
            <dd class="grand">¥{{ quote.total }}</dd>
          </dl>
        </template>

        <p v-else class="hint">
          这份方案没有报价 —— 生成时没能算出价格(面积未定、产品下架或报价参数未配)。
          可以点上面的「按当前价重算」再试一次。
        </p>
      </section>

      <!-- ---------------------------- 管理 ---------------------------- -->
      <section class="block">
        <h2>管理</h2>

        <form class="rename" @submit.prevent="saveName">
          <!-- 这里**故意不加 HTML 的 `maxlength`** —— 它数的是 UTF-16 单元,
               而后端数的是码点。挂上它就会出现"浏览器拦住 100 个 emoji、
               后端却认为完全合法"这种两把尺子的事。长度只在下面的 JS 里判一次。 -->
          <label>
            方案名
            <input v-model="nameDraft" type="text" />
          </label>
          <button type="submit" class="primary" :disabled="!canSaveName">保存</button>
        </form>

        <div class="actions">
          <button type="button" :disabled="busy" @click="copyAsNew">另存为</button>
          <button type="button" class="danger" :disabled="busy" @click="remove">删除</button>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
}

h1 {
  margin: 0;
  font-size: 1.35rem;
}

h2 {
  margin: 0 0 0.6rem;
  font-size: 1.05rem;
}

.meta {
  color: #6b7280;
  font-size: 0.85rem;
}

.block {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
}

.main img {
  max-width: 100%;
  border-radius: 0.5rem;
}

.caption {
  margin: 0.3rem 0 0;
  color: #6b7280;
  font-size: 0.8rem;
}

.thumbs {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-top: 0.75rem;
}

.thumbs figure {
  margin: 0;
}

.thumbs img {
  width: 8rem;
  height: 6rem;
  object-fit: cover;
  border-radius: 0.35rem;
}

.thumbs figcaption {
  color: #6b7280;
  font-size: 0.75rem;
  text-align: center;
}

.spaces {
  margin: 0 0 1rem;
  padding-left: 1.2rem;
  color: #374151;
  font-size: 0.9rem;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

th,
td {
  padding: 0.45rem 0.5rem;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}

.num {
  text-align: right;
}

.tag {
  margin-left: 0.3rem;
  padding: 0.05rem 0.3rem;
  border-radius: 0.25rem;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.72rem;
}

.quote-head {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.stale {
  padding: 0.5rem 0.75rem;
  border-radius: 0.35rem;
  background: #fef3c7;
  color: #92400e;
  font-size: 0.85rem;
}

.totals {
  display: grid;
  grid-template-columns: auto auto;
  gap: 0.3rem 1rem;
  justify-content: end;
  margin: 1rem 0 0;
  font-size: 0.9rem;
}

.totals dt {
  color: #6b7280;
}

.totals dd {
  margin: 0;
  text-align: right;
}

.totals .grand {
  color: #b45309;
  font-weight: 600;
}

.rename {
  display: flex;
  gap: 0.5rem;
  align-items: flex-end;
  margin-bottom: 1rem;
}

.rename label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.85rem;
  color: #374151;
}

.rename input {
  min-width: 18rem;
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

button {
  padding: 0.35rem 0.8rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

button:disabled {
  opacity: 0.5;
  cursor: default;
}

.primary {
  background: #1d4ed8;
  border-color: #1d4ed8;
  color: white;
}

.danger {
  color: #b91c1c;
}

.errors {
  color: #b91c1c;
  font-size: 0.85rem;
}

.error {
  color: #b91c1c;
}

.notice {
  color: #047857;
}

.hint {
  color: #6b7280;
  font-size: 0.9rem;
}
</style>
