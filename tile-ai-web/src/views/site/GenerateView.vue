<script setup lang="ts">
import { Alert, Button } from 'ant-design-vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { getGenerateProgress, submitGenerate } from '@/api/c/generate'
import type { TaskProgress } from '@/api/c/generate'
import { listDictsOfType } from '@/api/dicts'
import type { Dict } from '@/api/dicts'
import { BusinessError, ErrorCode, validationErrorsOf } from '@/api/errors'
import { useSelectionDraftStore } from '@/stores/selectionDraft'
import type { Part, PartSelection, PlanImage, TaskStatus } from '@/api/types'

/**
 * 生成结果(F-1.7,PRD §5.4)。
 *
 * 提交流程是**异步**的:POST /generate 只拿到一个 taskId(状态恒 pending),
 * 之后要**轮询** `GET /generate/{taskId}` 直到 success / failed。
 *
 * ⚠️ 三件容易踩的事,都写在下面的轮询里:
 *   1. **失败不是 HTTP 错误码**。后端返回 `200 + code:40001`,原因在 `message`,
 *      而 `request()` 会把它**抛成 BusinessError** —— 所以必须 catch,
 *      不能按"拿到 200 就是成功"处理(`TaskProgress` 里根本没有 `error` 字段)。
 *   2. **用递归 setTimeout 而不是 setInterval** —— 慢请求会叠罗汉。
 *   3. **离开页面必须停** —— 否则组件卸载后仍在发请求。
 *
 * 空间 / 风格只作展示时翻中文,提交的始终是字典 key(D-14)。
 */
const draft = useSelectionDraftStore()

/** 轮询间隔:契约写明一期 2s */
const POLL_INTERVAL_MS = 2000
/** 连续多少次网络类失败才放弃(2s × 10 ≈ 20s),避免无限打 */
const MAX_TRANSIENT_FAILURES = 10

const PART_LABELS: Record<Part, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部(腰线 / 花砖)',
}

// ---------------------------------------------------------------- 字典

const spaceOptions = ref<Dict[]>([])
const styleOptions = ref<Dict[]>([])

const spaceLabels = computed(() =>
  Object.fromEntries(spaceOptions.value.map((item) => [item.key, item.label])),
)
const styleLabels = computed(() =>
  Object.fromEntries(styleOptions.value.map((item) => [item.key, item.label])),
)

function labelOf(map: Record<string, string>, key: string): string {
  return map[key] ?? key
}

// ---------------------------------------------------------------- 配置

const space = ref('')
const style = ref('')
const angles = ref(1)
/** 张数 = 角度数(不是 +1)—— 文案要说清,免得用户以为多送一张 */
const ANGLE_CHOICES = [1, 2, 3, 4]

const ready = computed(() => draft.layout !== null && draft.hasParts)
const selectedParts = computed<PartSelection[]>(() => draft.toPartSelections())
const canSubmit = computed(() => ready.value && space.value !== '' && !submitting.value)

// ---------------------------------------------------------------- 提交与轮询

const submitting = ref(false)
const errorText = ref('')
const fieldErrors = ref<string[]>([])

const taskId = ref<string | null>(null)
const status = ref<TaskStatus | null>(null)
const progress = ref<number | null>(null)
const images = ref<PlanImage[]>([])
const planId = ref<number | null>(null)
const resultParts = ref<PartSelection[]>([])
/** 终态失败的原因(来自 40001 的 message) */
const failureReason = ref('')

let timer: ReturnType<typeof setTimeout> | null = null
/** 卸载标记:回包后先看它,再决定要不要排下一次 */
let stopped = false
let transientFailures = 0

function cancelPolling() {
  stopped = true
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
}

function applyProgress(view: TaskProgress) {
  status.value = view.status
  progress.value = view.progress ?? null
  images.value = view.images ?? []
  planId.value = view.planId ?? null
  resultParts.value = view.parts ?? []
}

/** 到达终态:停表,不再排下一次。 */
function finish() {
  cancelPolling()
}

function schedule(id: string) {
  timer = setTimeout(() => {
    void tick(id)
  }, POLL_INTERVAL_MS)
}

async function tick(id: string) {
  if (stopped) return

  try {
    const view = await getGenerateProgress(id)
    applyProgress(view)
    transientFailures = 0
    if (view.status === 'success' || view.status === 'failed') {
      finish()
      return
    }
  } catch (error) {
    if (error instanceof BusinessError) {
      // 40001:后端把"生成失败"做成 200 + 业务码,原因在 message 里。
      // 这是**终态**,不能当网络抖动重试 —— 重试只会拿到同样的失败。
      if (error.code === ErrorCode.GENERATION_FAILED) {
        failureReason.value = error.message
        status.value = 'failed'
        finish()
        return
      }
      // 40003:任务不存在或不属于当前账号 —— 同样没必要重试
      if (error.code === ErrorCode.TASK_NOT_FOUND) {
        failureReason.value = error.message
        status.value = 'failed'
        finish()
        return
      }
    }

    // 其余(网络类、5xx)视为**非终态**,保留重试,但要封顶
    transientFailures += 1
    if (transientFailures >= MAX_TRANSIENT_FAILURES) {
      failureReason.value = '与服务器失去联系,请稍后重试。'
      status.value = 'failed'
      finish()
      return
    }
  }

  if (!stopped) schedule(id)
}

async function submit() {
  errorText.value = ''
  fieldErrors.value = []
  failureReason.value = ''
  cancelPolling()

  // 重新提交前清掉上一次的结果,免得旧图与新任务混在一起
  taskId.value = null
  status.value = null
  progress.value = null
  images.value = []
  planId.value = null
  resultParts.value = []
  transientFailures = 0

  submitting.value = true
  try {
    const accepted = await submitGenerate({
      space: space.value,
      // 风格可留空 —— 空串不要当值发出去
      style: style.value || undefined,
      angles: angles.value,
      layout: draft.layout!,
      parts: selectedParts.value,
    })
    taskId.value = accepted.taskId
    status.value = accepted.status
    stopped = false
    schedule(accepted.taskId)
  } catch (error) {
    if (error instanceof BusinessError) {
      if (error.code === ErrorCode.PARAM_INVALID) {
        fieldErrors.value = validationErrorsOf(error).map((item) => `${item.field}:${item.reason}`)
      }
      errorText.value = error.message
    } else {
      errorText.value = error instanceof Error ? error.message : '提交失败'
    }
  } finally {
    submitting.value = false
  }
}

// ---------------------------------------------------------------- 展示

const cover = computed<PlanImage | null>(() => {
  const list = images.value
  if (!list.length) return null
  return list.find((image) => image.isCover) ?? list[0]
})

/** 全部结果图。**不排序也不筛选** —— 顺序由服务端给(封面在前,见 §8.4)。 */
const gallery = computed<PlanImage[]>(() => images.value)

/** 用户点开看的那一张。`null` = 没点过 ⇒ 显示封面。 */
const selected = ref<PlanImage | null>(null)

/**
 * 主图位置显示哪一张:**点过的那张(且它还在这一轮结果里)**,否则封面。
 *
 * ⚠️ "还在这一轮结果里"这句是关键 —— **重新生成会换掉整组图**,而 `selected`
 * 里留着的还是上一轮那张。少了这个判断,主图会显示**上一轮**的图,而且**不报错**:
 * 用户看到的是"重新生成完,画面没变",实际上变的是别的角度的缩略图。
 */
const active = computed<PlanImage | null>(() => {
  const chosen = selected.value
  if (chosen && gallery.value.some((image) => image.url === chosen.url)) {
    return chosen
  }
  return cover.value
})

/** 视角名 → 中文标签。**按 view 区分,不靠数组下标猜**。 */
function viewLabel(view?: string): string {
  if (!view || view === 'default') return '正面'
  const matched = /^angle(\d+)$/.exec(view)
  if (matched) return `角度 ${matched[1]}`
  return view
}

function removePart(part: Part) {
  draft.removePart(part)
}

const generating = computed(
  () => taskId.value !== null && status.value !== 'success' && status.value !== 'failed',
)

function reset() {
  cancelPolling()
  taskId.value = null
  status.value = null
  progress.value = null
  images.value = []
  planId.value = null
  resultParts.value = []
  failureReason.value = ''
}

// ---------------------------------------------------------------- 生命周期

onMounted(async () => {
  try {
    const [spaces, styles] = await Promise.all([
      listDictsOfType('space'),
      listDictsOfType('style'),
    ])
    spaceOptions.value = spaces
    styleOptions.value = styles
  } catch {
    // 拿不到字典就退化成显示 key,不影响提交
  }

  // 户型里只有一个空间时,直接替用户选上,少一步操作
  const single = draft.layout?.spaces?.[0]
  if (single && draft.layout?.spaces.length === 1) {
    space.value = single.type
  }
})

/**
 * 这次有哪几个"未选的地面/墙面"**没被补上**。
 *
 * 判据:用户当初没选它、结果里也没有它 —— 说明 AI 没能补上。
 * **只陈述事实,不猜原因**:页面并不知道是"库里没有合适的砖"还是"补齐服务没答上",
 * 编一个理由就是在替后端说话(与分享页"照抄后端文案"同一口径)。
 */
const uncompletedParts = computed<Part[]>(() => {
  if (status.value !== 'success') return []
  const chosen = new Set(draft.parts.map((item) => item.part))
  const returned = new Set(resultParts.value.map((item) => item.part))
  return (['floor', 'wall'] as Part[]).filter((part) => !chosen.has(part) && !returned.has(part))
})

onBeforeUnmount(cancelPolling)
</script>

<template>
  <section>
    <header class="page-head">
      <h1 class="page-head__title">生成效果图</h1>
    </header>

    <!-- 草稿是内存态,刷新即清空 —— 这里要如实说明,而不是拿空数据硬凑一次生成 -->
    <div v-if="!ready" class="empty" role="alert">
      <p>选购信息不完整,无法生成。</p>
      <ul>
        <li v-if="!draft.hasParts">
          还没选砖 —— <RouterLink :to="{ name: 'products' }">去选砖</RouterLink>
        </li>
        <li v-if="!draft.layout">
          还没录户型 —— <RouterLink :to="{ name: 'layout' }">去录户型</RouterLink>
        </li>
      </ul>
      <p class="note">（选购信息只保存在当前页面,刷新后会清空。）</p>
    </div>

    <template v-else>
      <!-- ---------------------------- 配置 ---------------------------- -->
      <div class="panel">
        <h2>生成设置</h2>

        <label>
          空间
          <select v-model="space">
            <option value="">请选择</option>
            <option v-for="option in spaceOptions" :key="option.key" :value="option.key">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label>
          风格(可选)
          <select v-model="style">
            <option value="">不限</option>
            <option v-for="option in styleOptions" :key="option.key" :value="option.key">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label>
          出图张数
          <select v-model.number="angles">
            <option v-for="count in ANGLE_CHOICES" :key="count" :value="count">{{ count }} 张</option>
          </select>
          <small>每张一个视角(正面 + 各角度),不是在此之上再加一张。</small>
        </label>
      </div>

      <!-- ---------------------------- 部位 ---------------------------- -->
      <div class="panel">
        <h2>本次使用的瓷砖</h2>
        <ul class="parts">
          <li v-for="item in draft.parts" :key="item.part">
            <span>
              <strong>{{ PART_LABELS[item.part] }}</strong>
              · {{ item.name || item.sku }}
            </span>
            <Button danger size="small" @click="removePart(item.part)">移除</Button>
          </li>
        </ul>
        <p class="note">
          至少选一个部位。<b>未选的地面/墙面会由 AI 自动补齐</b>;
          腰线/花砖等局部为可选项,不强制补齐。
        </p>
        <RouterLink v-if="!draft.hasParts" :to="{ name: 'products' }">去选砖</RouterLink>
      </div>

      <!-- ---------------------------- 提交 ---------------------------- -->
      <div v-if="!taskId" class="actions">
        <Button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
          开始生成
        </Button>
        <span v-if="!space" class="note">先选择空间。</span>
      </div>

      <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
      <ul v-if="fieldErrors.length" class="errors">
        <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
      </ul>

      <!-- ---------------------------- 进度 ---------------------------- -->
      <div v-if="generating" class="panel progress" role="status">
        <p>正在生成…{{ status === 'pending' ? '(已排队)' : '' }}</p>
        <div v-if="progress !== null" class="bar">
          <span :style="{ width: `${progress}%` }" />
        </div>
        <p class="note">任务号 {{ taskId }}</p>
      </div>

      <Alert v-if="failureReason" class="alert-gap" type="error" :message="failureReason" show-icon />
      <div v-if="status === 'failed'" class="actions">
        <Button type="primary" @click="reset">重新生成</Button>
      </div>

      <!-- ---------------------------- 结果 ---------------------------- -->
      <div v-if="status === 'success'" class="result">
        <h2>生成完成</h2>
        <!-- 主图与缩略图裹在同一个 v-if 里:缩略图要拿 active 做高亮,
             拆成兄弟节点的话 vue-tsc 窄化不到(active 可能是 null) -->
        <template v-if="active">
          <div class="main">
            <img :src="active.url" :alt="viewLabel(active.view)" />
            <p class="caption">{{ viewLabel(active.view) }}</p>
          </div>
          <!-- 多角度图:**点一下就换到主图位置**显示大图(与商品详情页同款交互)。
               缩略图列出全部图(含当前这张),这样点回封面也有路。 -->
          <div v-if="gallery.length > 1" class="thumbs">
            <button
              v-for="image in gallery"
              :key="image.url"
              type="button"
              :class="['thumb', { 'thumb--active': image.url === active.url }]"
              :aria-pressed="image.url === active.url"
              @click="selected = image"
            >
              <img :src="image.url" :alt="viewLabel(image.view)" />
              <span class="thumb-cap">{{ viewLabel(image.view) }}</span>
            </button>
          </div>
        </template>
        <p v-else class="note">任务已完成,但没有返回图片。</p>

        <!-- 方案已经存到服务端了 —— 给一条能走回去的路,而不是只丢一个编号让用户去猜。
             用字符串路径而不是 `{ name: 'plan-detail' }`:命名路由要求目标路由已注册,
             否则 RouterLink 在解析时就会抛;字符串路径只是渲染一个普通的 href,
             对"这条路由存不存在"不敏感。 -->
        <dl v-if="planId !== null" class="meta">
          <dt>方案编号</dt>
          <dd>
            {{ planId }}
            <RouterLink class="plan-link" :to="`/plans/${planId}`">查看方案</RouterLink>
          </dd>
        </dl>

        <div v-if="resultParts.length" class="meta">
          <p class="note">本次实际使用的部位:</p>
          <ul class="parts">
            <li v-for="item in resultParts" :key="item.part">
              <strong>{{ PART_LABELS[item.part] }}</strong> · {{ item.sku }}
              <span v-if="item.source === 'ai'" class="tag">AI 补齐</span>
            </li>
          </ul>
          <!-- 如实说明"这次没补上" —— 只讲事实,不替后端解释原因 -->
          <p v-if="uncompletedParts.length" class="note">
            未选的{{ uncompletedParts.map((part) => PART_LABELS[part]).join('、') }}
            这次没有自动补齐,可回上一步手动补选后再生成。
          </p>
        </div>

        <div class="actions">
          <Button @click="reset">再生成一次</Button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 测试依赖的**顺序**也必须保住:`select` 恰好 3 个(空间 → 风格 → 张数)、
   第一个 `<img>` 是 `.main img`、`[role="alert"]` 的先后顺序。
   ⚠️ 本页任何静态文案都不得出现"失败""超时"字样 —— 有测试在断言它们**不**出现。 */

.page-head {
  margin-bottom: var(--space-4);
}

.page-head__title {
  margin: 0;
  font-size: var(--text-2xl);
  line-height: var(--leading-2xl);
  font-weight: 700;
  color: var(--color-text);
}

h2 {
  margin: 0 0 var(--space-3);
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: 600;
  color: var(--color-text);
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

.panel label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  max-width: 22rem;
  margin-bottom: var(--space-4);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

.panel select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.panel select:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

.panel small,
.note {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}

.panel label small {
  margin-top: var(--space-1);
}

/* ── 部位清单 ───────────────────────────────────────────────────────────── */
.parts {
  list-style: none;
  margin: 0 0 var(--space-3);
  padding: 0;
  font-size: var(--text-sm);
}

.parts li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--color-border);
}

.parts li:last-child {
  border-bottom: none;
}

.tag {
  margin-left: var(--space-2);
  padding: 0.1rem var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
}

/* ⚠️ **本页的常规按钮已迁 antd**(移除 / 开始生成 / 重新生成 / 再生成一次)——
   不再写样式(§70.9)。
   ⚠️ 但下面的 **`.thumb`(多角度缩略图)是自定义交互元素**,刻意保留、**不迁**:
   它是"点一下换主图"的图块,不是普通按钮,套 antd `Button` 会跟设计打架。 */
.actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

/* ── 进度 ───────────────────────────────────────────────────────────────── */
.progress .bar {
  height: 0.5rem;
  border-radius: var(--radius-full);
  background: var(--color-surface-sunken);
  overflow: hidden;
}

.progress .bar span {
  display: block;
  height: 100%;
  background: var(--color-primary);
  transition: width var(--dur-base) var(--ease-standard);
}

/* ── 结果 ───────────────────────────────────────────────────────────────── */
.result {
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.result .main img {
  max-width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.caption {
  margin: var(--space-2) 0 var(--space-4);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

/* 缩略图是 button(可点)—— 盖掉全局 button 的边框/背景,只留图与说明 */
.thumb {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  width: 10rem;
  padding: var(--space-1);
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  background: none;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard);
}

.thumb:hover {
  border-color: var(--color-border-strong);
}

.thumb img {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

/* 当前正在主图位置展示的那张 */
.thumb--active {
  border-color: var(--color-primary);
}

.thumb--active .thumb-cap {
  color: var(--color-primary);
  font-weight: 600;
}

.thumb-cap {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
  text-align: center;
}

.meta {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-1) var(--space-3);
  margin: var(--space-4) 0;
  font-size: var(--text-sm);
}

.meta dt {
  color: var(--color-text-secondary);
}

.meta dd {
  margin: 0;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.plan-link {
  margin-left: var(--space-2);
  color: var(--color-primary);
  font-weight: 600;
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
.empty {
  padding: var(--space-4);
  border: 1px solid var(--color-warning);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-text);
}

.empty p {
  margin: 0 0 var(--space-2);
}

.empty ul {
  margin: 0 0 var(--space-2);
  padding-left: var(--space-5);
}

.empty a {
  color: var(--color-primary);
  font-weight: 600;
}


.errors {
  margin: var(--space-3) 0 0;
  padding-left: var(--space-5);
  color: var(--color-danger);
  font-size: var(--text-sm);
}
</style>
