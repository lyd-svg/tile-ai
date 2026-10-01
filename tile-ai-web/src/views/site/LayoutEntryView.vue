<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listLayoutTemplates, parseLayout } from '@/api/c/layout'
import { listDictsOfType } from '@/api/dicts'
import { validationErrorsOf } from '@/api/errors'
import type { Dict } from '@/api/dicts'
import { useSelectionDraftStore } from '@/stores/selectionDraft'
import type { LayoutModel, UnitTemplate } from '@/api/types'

/**
 * 户型录入(F-1.5,PRD §5.3)。
 *
 * 两条路:**选现成模板**,或**手动录入**(自然语言 / 表单参数)。
 * 一期不做"上传户型图识别",也不做人工校准 —— 解析完直接用(§5.3、契约 /layout/parse)。
 *
 * ⚠️ **面积由服务端派生,前端只展示、绝不重算**。前端再推一遍
 * (`floor = 长×宽`…)就等于有了第二份实现,迟早与报价对不上。
 *
 * 录户型本身**不需要登录**(契约里 /layout/* 都是 `security: []`),
 * 登录门槛在下一步「提交生成」。
 */
const router = useRouter()
const draft = useSelectionDraftStore()

type Mode = 'template' | 'manual'
type ManualMode = 'text' | 'params'

const mode = ref<Mode>('template')
const manualMode = ref<ManualMode>('text')

/** 空间类型下拉的选项 —— **来自字典,不硬编码**(D-14) */
const spaceOptions = ref<Dict[]>([])
const spaceLabels = computed(() =>
  Object.fromEntries(spaceOptions.value.map((item) => [item.key, item.label])),
)

function labelOf(key: string): string {
  return spaceLabels.value[key] ?? key
}

// ---------------------------------------------------------------- 模板

const templates = ref<UnitTemplate[]>([])
const templatesLoading = ref(false)
const templatesError = ref('')
/** 选中的模板 id —— 用来给选中态高亮,也用来发现"这个模板没配户型" */
const pickedId = ref<number | null>(null)

const picked = computed(() => templates.value.find((item) => item.id === pickedId.value) ?? null)

async function loadTemplates() {
  templatesLoading.value = true
  templatesError.value = ''
  try {
    const page = await listLayoutTemplates({ page: 1, size: 20 })
    templates.value = page.list
  } catch (error) {
    templates.value = []
    templatesError.value = error instanceof Error ? error.message : '加载模板失败'
  } finally {
    templatesLoading.value = false
  }
}

/** 选用某个模板。模板没配户型时**不写草稿**,只提示改用手动录入。 */
function useTemplate(template: UnitTemplate) {
  pickedId.value = template.id
  clearErrors()
  if (template.layout) {
    draft.setLayout(template.layout)
  }
}

// ---------------------------------------------------------------- 手动录入

interface ManualSpace {
  type: string
  name: string
  length: number | null
  width: number | null
  height: number | null
  wallCount: number | null
}

function emptySpace(): ManualSpace {
  return { type: '', name: '', length: null, width: null, height: null, wallCount: null }
}

const text = ref('')
const spaces = ref<ManualSpace[]>([emptySpace()])
const parsing = ref(false)
const errorText = ref('')
const fieldErrors = ref<string[]>([])

function clearErrors() {
  errorText.value = ''
  fieldErrors.value = []
}

function addSpace() {
  spaces.value.push(emptySpace())
}

function removeSpace(index: number) {
  spaces.value.splice(index, 1)
  if (spaces.value.length === 0) spaces.value.push(emptySpace())
}

/** 前端只挡"明显不成形"的输入;尺寸范围之类交给服务端(10001 会说清哪一项)。 */
function buildParams(): LayoutModel | null {
  if (spaces.value.length === 0) return null
  const model: LayoutModel = {
    spaces: spaces.value.map((item) => ({
      type: item.type || 'other',
      name: item.name || undefined,
      length: Number(item.length),
      width: Number(item.width),
      height: Number(item.height),
      wallCount: item.wallCount == null ? undefined : Number(item.wallCount),
    })),
  }
  return model
}

async function submitManual() {
  clearErrors()

  // 两种方式互斥:选了参数就只发参数(params 优先于 text,契约明说)
  const usingParams = manualMode.value === 'params'
  let payload: { text?: string; params?: LayoutModel }

  if (usingParams) {
    const params = buildParams()
    if (!params) {
      errorText.value = '至少需要一个空间'
      return
    }
    const bad = params.spaces.findIndex(
      (space) => !(space.length > 0) || !(space.width > 0) || !(space.height > 0),
    )
    if (bad >= 0) {
      errorText.value = `第 ${bad + 1} 个空间的长 / 宽 / 层高都要大于 0`
      return
    }
    payload = { params }
  } else {
    if (!text.value.trim()) {
      errorText.value = '请描述一下户型,例如「卫生间 2m×1.8m,层高 2.7m」'
      return
    }
    payload = { text: text.value.trim() }
  }

  parsing.value = true
  try {
    const layout = await parseLayout(payload)
    draft.setLayout(layout)
    pickedId.value = null
  } catch (error) {
    draft.setLayout(null)
    errorText.value = error instanceof Error ? error.message : '解析失败'
    // 10001 会把逐字段原因放在 data.errors[] —— 直接列出来,别让用户猜哪一项不对
    fieldErrors.value = validationErrorsOf(error).map((item) => `${item.field}:${item.reason}`)
  } finally {
    parsing.value = false
  }
}

// ---------------------------------------------------------------- 结果与下一步

const layout = computed(() => draft.layout)

function areaOf(space: { areas?: { floor?: number; wall?: number; local?: number } }, part: 'floor' | 'wall' | 'local'): string {
  const value = space.areas?.[part]
  return typeof value === 'number' ? String(value) : '—'
}

function goGenerate() {
  router.push({ name: 'generate' })
}

onMounted(async () => {
  try {
    spaceOptions.value = await listDictsOfType('space')
  } catch {
    // 拿不到字典就退化成手填 key —— 不影响录户型本身
  }
  await loadTemplates()
})
</script>

<template>
  <section>
    <h1>录入户型</h1>
    <p class="lead">选一个现成模板,或用手动录入描述你的空间。面积由系统按尺寸算出。</p>

    <!-- 先选砖再录户型 —— 草稿为空时给出回去的路 -->
    <p v-if="!draft.hasParts" class="warn" role="alert">
      还没有选砖。
      <RouterLink :to="{ name: 'products' }">先去选砖 →</RouterLink>
    </p>

    <div class="tabs">
      <button
        type="button"
        :class="['tab', { 'tab--active': mode === 'template' }]"
        @click="mode = 'template'"
      >
        用现成模板
      </button>
      <button
        type="button"
        :class="['tab', { 'tab--active': mode === 'manual' }]"
        @click="mode = 'manual'"
      >
        手动录入
      </button>
    </div>

    <!-- ============================ 模板 ============================ -->
    <div v-if="mode === 'template'" class="panel">
      <p v-if="templatesLoading" class="hint">加载中…</p>
      <p v-else-if="templatesError" class="error" role="alert">{{ templatesError }}</p>

      <p v-else-if="!templates.length" class="hint">还没有可用的户型模板,请改用手动录入。</p>

      <ul v-else class="templates">
        <li v-for="template in templates" :key="template.id">
          <button
            type="button"
            :class="['template', { 'template--active': pickedId === template.id }]"
            @click="useTemplate(template)"
          >
            <strong>{{ template.name }}</strong>
            <span class="meta">
              <template v-if="template.structure">{{ template.structure }}</template>
              <template v-if="template.areaRange"> · {{ template.areaRange }}</template>
              <template v-if="template.roomCount"> · {{ template.roomCount }} 居室</template>
            </span>
          </button>
        </li>
      </ul>

      <!-- 模板的 layout 在契约里是可选的,没配就得说清,而不是画一个空户型 -->
      <p v-if="picked && !picked.layout" class="error" role="alert">
        该模板尚未配置户型,请改用手动录入。
      </p>
    </div>

    <!-- ========================== 手动录入 ========================== -->
    <div v-else class="panel">
      <div class="subtabs">
        <button
          type="button"
          :class="['tab', { 'tab--active': manualMode === 'text' }]"
          @click="manualMode = 'text'"
        >
          用文字描述
        </button>
        <button
          type="button"
          :class="['tab', { 'tab--active': manualMode === 'params' }]"
          @click="manualMode = 'params'"
        >
          填参数
        </button>
      </div>

      <div v-if="manualMode === 'text'" class="manual">
        <label>
          户型描述
          <textarea
            v-model="text"
            rows="3"
            placeholder="例如:卫生间 2m×1.8m,层高 2.7m,一扇门 0.8m×2.1m"
          />
        </label>
      </div>

      <div v-else class="manual">
        <div v-for="(space, index) in spaces" :key="index" class="space-row">
          <p class="space-title">空间 {{ index + 1 }}</p>
          <label>
            类型
            <select v-model="space.type">
              <option value="">请选择</option>
              <option v-for="option in spaceOptions" :key="option.key" :value="option.key">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            名称(可选)
            <input v-model="space.name" type="text" placeholder="如 主卫" />
          </label>
          <label>
            长(米)
            <input v-model.number="space.length" type="number" step="0.1" min="0" />
          </label>
          <label>
            宽(米)
            <input v-model.number="space.width" type="number" step="0.1" min="0" />
          </label>
          <label>
            层高(米)
            <input v-model.number="space.height" type="number" step="0.1" min="0" />
          </label>
          <label>
            墙面数(可选)
            <input v-model.number="space.wallCount" type="number" min="1" />
          </label>
          <button v-if="spaces.length > 1" type="button" class="remove" @click="removeSpace(index)">
            删除这个空间
          </button>
        </div>

        <button type="button" class="ghost" @click="addSpace">+ 再加一个空间</button>
      </div>

      <div class="actions">
        <button type="button" class="primary" :disabled="parsing" @click="submitManual">
          {{ parsing ? '解析中…' : '解析户型' }}
        </button>
      </div>

      <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
      <ul v-if="fieldErrors.length" class="errors">
        <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
      </ul>
    </div>

    <!-- ============================ 结果 ============================ -->
    <div v-if="layout" class="result">
      <h2>户型已就绪</h2>
      <p class="note">面积由服务端按长宽高派生,前端不重算。</p>

      <ul class="spaces">
        <li v-for="(space, index) in layout.spaces" :key="index">
          <p class="space-name">
            {{ space.name || labelOf(space.type) }}
            <small>{{ labelOf(space.type) }}</small>
          </p>
          <p class="dims">
            {{ space.length }}m × {{ space.width }}m,层高 {{ space.height }}m
            <template v-if="space.wallCount"> · {{ space.wallCount }} 面墙</template>
          </p>
          <dl>
            <dt>地面</dt><dd>{{ areaOf(space, 'floor') }} ㎡</dd>
            <dt>墙面</dt><dd>{{ areaOf(space, 'wall') }} ㎡</dd>
            <dt>局部</dt><dd>{{ areaOf(space, 'local') }} ㎡</dd>
          </dl>
        </li>
      </ul>

      <div class="actions">
        <button type="button" class="primary" :disabled="!draft.hasParts" @click="goGenerate">
          下一步:去生成 →
        </button>
        <RouterLink v-if="!draft.hasParts" :to="{ name: 'products' }">请先选好砖</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
h1 {
  margin: 0 0 0.25rem;
  font-size: 1.4rem;
}

.lead {
  color: #6b7280;
  font-size: 0.9rem;
  margin: 0 0 1rem;
}

.warn {
  padding: 0.6rem 0.75rem;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 0.25rem;
  color: #92400e;
  font-size: 0.9rem;
}

.warn a {
  color: #1d4ed8;
}

.tabs,
.subtabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.subtabs {
  margin-bottom: 0.75rem;
}

.tab {
  padding: 0.4rem 0.9rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

.tab--active {
  border-color: #1d4ed8;
  color: #1d4ed8;
  font-weight: 600;
}

.panel {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  padding: 1rem;
  margin-bottom: 1.5rem;
}

.templates {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: 0.75rem;
}

.template {
  width: 100%;
  text-align: left;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.template--active {
  border-color: #1d4ed8;
  background: #eff6ff;
}

.template .meta {
  color: #6b7280;
  font-size: 0.8rem;
}

.manual label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #374151;
}

.manual textarea,
.manual input,
.manual select {
  padding: 0.4rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
}

.space-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
  gap: 0.6rem;
  padding: 0.75rem;
  border: 1px dashed #e5e7eb;
  border-radius: 0.25rem;
  margin-bottom: 0.75rem;
}

.space-title {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 0.85rem;
  color: #6b7280;
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 1rem;
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

.ghost,
.remove {
  padding: 0.35rem 0.7rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
  font-size: 0.85rem;
}

.remove {
  border-color: #fca5a5;
  color: #b91c1c;
  align-self: end;
}

.result {
  border-top: 1px solid #e5e7eb;
  padding-top: 1rem;
}

.result h2 {
  margin: 0 0 0.25rem;
  font-size: 1.1rem;
}

.note {
  color: #6b7280;
  font-size: 0.8rem;
  margin: 0 0 0.75rem;
}

.spaces {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: 0.75rem;
}

.spaces > li {
  border: 1px solid #e5e7eb;
  border-radius: 0.25rem;
  padding: 0.75rem;
}

.space-name {
  margin: 0 0 0.25rem;
  font-weight: 600;
}

.space-name small {
  color: #9ca3af;
  font-weight: 400;
  margin-left: 0.4rem;
}

.dims {
  margin: 0 0 0.5rem;
  color: #6b7280;
  font-size: 0.85rem;
}

.spaces dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.2rem 0.75rem;
  margin: 0;
  font-size: 0.85rem;
}

.spaces dt {
  color: #6b7280;
}

.spaces dd {
  margin: 0;
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

.hint {
  color: #6b7280;
}

.actions a {
  color: #1d4ed8;
  font-size: 0.9rem;
}
</style>
