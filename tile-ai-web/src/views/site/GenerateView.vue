<script setup lang="ts">
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

const thumbs = computed(() => images.value.filter((image) => image.url !== cover.value?.url))

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

onBeforeUnmount(cancelPolling)
</script>

<template>
  <section>
    <h1>生成效果图</h1>

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
            <button type="button" class="remove" @click="removePart(item.part)">移除</button>
          </li>
        </ul>
        <p class="note">
          至少选一个部位。当前阶段建议把地面与墙面都选上 ——
          未选的部位不会被自动补齐。
        </p>
        <RouterLink v-if="!draft.hasParts" :to="{ name: 'products' }">去选砖</RouterLink>
      </div>

      <!-- ---------------------------- 提交 ---------------------------- -->
      <div v-if="!taskId" class="actions">
        <button type="button" class="primary" :disabled="!canSubmit" @click="submit">
          {{ submitting ? '提交中…' : '开始生成' }}
        </button>
        <span v-if="!space" class="note">先选择空间。</span>
      </div>

      <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
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

      <p v-if="failureReason" class="error" role="alert">{{ failureReason }}</p>
      <div v-if="status === 'failed'" class="actions">
        <button type="button" class="primary" @click="reset">重新生成</button>
      </div>

      <!-- ---------------------------- 结果 ---------------------------- -->
      <div v-if="status === 'success'" class="result">
        <h2>生成完成</h2>
        <div v-if="cover" class="main">
          <img :src="cover.url" :alt="viewLabel(cover.view)" />
          <p class="caption">{{ viewLabel(cover.view) }}</p>
        </div>
        <p v-else class="note">任务已完成,但没有返回图片。</p>

        <div v-if="thumbs.length" class="thumbs">
          <figure v-for="image in thumbs" :key="image.url">
            <img :src="image.url" :alt="viewLabel(image.view)" />
            <figcaption>{{ viewLabel(image.view) }}</figcaption>
          </figure>
        </div>

        <dl v-if="planId !== null" class="meta">
          <dt>方案编号</dt><dd>{{ planId }}</dd>
        </dl>

        <div v-if="resultParts.length" class="meta">
          <p class="note">本次实际使用的部位:</p>
          <ul class="parts">
            <li v-for="item in resultParts" :key="item.part">
              <strong>{{ PART_LABELS[item.part] }}</strong> · {{ item.sku }}
              <span v-if="item.source === 'ai'" class="tag">AI 补齐</span>
            </li>
          </ul>
        </div>

        <div class="actions">
          <button type="button" class="ghost" @click="reset">再生成一次</button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
h1 {
  margin: 0 0 1rem;
  font-size: 1.4rem;
}

h2 {
  margin: 0 0 0.75rem;
  font-size: 1.05rem;
}

.panel {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  padding: 1rem;
  margin-bottom: 1rem;
}

.panel label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #374151;
  margin-bottom: 0.75rem;
  max-width: 22rem;
}

.panel select {
  padding: 0.4rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
}

.panel small,
.note {
  color: #6b7280;
  font-size: 0.8rem;
}

.parts {
  list-style: none;
  margin: 0 0 0.5rem;
  padding: 0;
  font-size: 0.9rem;
}

.parts li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.25rem 0;
}

.tag {
  margin-left: 0.4rem;
  padding: 0.1rem 0.35rem;
  border-radius: 0.2rem;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.75rem;
}

.remove {
  border: 1px solid #fca5a5;
  border-radius: 0.25rem;
  background: white;
  color: #b91c1c;
  cursor: pointer;
  font-size: 0.85rem;
  padding: 0.2rem 0.5rem;
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
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

.ghost {
  padding: 0.5rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

.progress .bar {
  height: 0.5rem;
  border-radius: 0.25rem;
  background: #e5e7eb;
  overflow: hidden;
}

.progress .bar span {
  display: block;
  height: 100%;
  background: #1d4ed8;
}

.result .main img {
  max-width: 100%;
  border-radius: 0.375rem;
  border: 1px solid #e5e7eb;
}

.caption {
  color: #6b7280;
  font-size: 0.85rem;
  margin: 0.25rem 0 1rem;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.thumbs figure {
  margin: 0;
  width: 10rem;
}

.thumbs img {
  width: 100%;
  border-radius: 0.25rem;
  border: 1px solid #e5e7eb;
}

.thumbs figcaption {
  color: #6b7280;
  font-size: 0.8rem;
  text-align: center;
}

.meta {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.2rem 0.75rem;
  font-size: 0.85rem;
  margin: 1rem 0;
}

.meta dt {
  color: #6b7280;
}

.meta dd {
  margin: 0;
}

.empty {
  padding: 1rem;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 0.25rem;
  color: #92400e;
}

.empty a {
  color: #1d4ed8;
}

.error {
  color: #b91c1c;
}

.errors {
  margin: 0.5rem 0 0;
  padding-left: 1.2rem;
  color: #b91c1c;
  font-size: 0.85rem;
}
</style>
