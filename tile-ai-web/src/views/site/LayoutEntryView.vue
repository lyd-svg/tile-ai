<script setup lang="ts">
import { Alert, Button, Select, Skeleton } from 'ant-design-vue'
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
    <header class="page-head">
      <h1 class="page-head__title">录入户型</h1>
      <p class="lead">选一个现成模板,或用手动录入描述你的空间。面积由系统按尺寸算出。</p>
    </header>

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
      <Skeleton v-if="templatesLoading" active :paragraph="{ rows: 4 }" />
      <Alert v-else-if="templatesError" class="alert-gap" type="error" :message="templatesError" show-icon />

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
            <Select v-model:value="space.type" placeholder="请选择" allow-clear>
              <Select.Option v-for="option in spaceOptions" :key="option.key" :value="option.key">
                {{ option.label }}
              </Select.Option>
            </Select>
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
          <Button v-if="spaces.length > 1" danger class="remove" @click="removeSpace(index)">
            删除这个空间
          </Button>
        </div>

        <Button @click="addSpace">+ 再加一个空间</Button>
      </div>

      <div class="actions">
        <Button type="primary" :loading="parsing" @click="submitManual">解析户型</Button>
      </div>

      <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
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
        <Button type="primary" :disabled="!draft.hasParts" @click="goGenerate">
          下一步:去生成 →
        </Button>
        <RouterLink v-if="!draft.hasParts" :to="{ name: 'products' }">请先选好砖</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 测试依赖的选择器**全部保留**:`button.tab` · `button.template` ·
   `button.primary`(两个,顺序不变)· `textarea` · `input[type="number"]` ·
   `.errors li` · `[role="alert"]`(顺序不变)。 */

.page-head {
  margin-bottom: var(--space-4);
}

.page-head__title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-2xl);
  line-height: var(--leading-2xl);
  font-weight: 700;
  color: var(--color-text);
}

.lead {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text-secondary);
}

.warn {
  margin: 0 0 var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-warning);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-text);
  font-size: var(--text-sm);
}

.warn a {
  color: var(--color-primary);
  font-weight: 600;
}

/* ── 标签页 ─────────────────────────────────────────────────────────────── */
.tabs,
.subtabs {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.subtabs {
  margin-bottom: var(--space-3);
}

.tab {
  padding: var(--space-2) var(--space-4);
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

.tab:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.tab--active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}

/* ── 面板 ───────────────────────────────────────────────────────────────── */
.panel {
  margin-bottom: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

/* ── 模板卡 ─────────────────────────────────────────────────────────────── */
.templates {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: var(--space-3);
}

.template {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-4);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  text-align: left;
  font: inherit;
  color: var(--color-text);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.template:hover {
  border-color: var(--color-primary);
}

.template--active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.template .meta {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
}

/* ── 手动录入表单 ───────────────────────────────────────────────────────── */
.manual label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

/* ⚠️ `:not([class*='ant-'])` 是**必需的**,不是装饰 —— 见 `tile-ai-design.md` §5.6。
   antd 的 `Select` **内部就是一个 `<input>`**(带 antd 的类)。
   而 `.manual input`(特异性 0,1,1)**压得过** `.ant-select-selection-search-input`(0,1,0),
   于是会给下拉里的搜索框套上边框与内边距 —— **看起来就是坏的**,而且不报错。
   ⇒ 把带 antd 类的元素**排除掉**。 */
.manual textarea,
.manual input:not([class*='ant-']),
.manual select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.manual textarea:focus-visible,
.manual input:not([class*='ant-']):focus-visible,
.manual select:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

.space-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  padding: var(--space-4);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-bg);
}

.space-title {
  grid-column: 1 / -1;
  margin: 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
  font-weight: 600;
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.actions a {
  color: var(--color-primary);
  font-size: var(--text-sm);
  font-weight: 600;
}

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
/* ⚠️ 本页的**常规按钮已迁 antd**(删除这个空间 / 加一个空间 / 解析户型 / 去生成)——
   不再写样式(§70.9)。
   ⚠️ 但 **`.tab`(页签)与 `.template`(模板卡)是自定义交互元素**,刻意保留、**不迁**:
   页签是"两个胶囊切换视图",模板卡是"整块可点的卡片",都不是普通按钮。

   下面只剩一条**排版**:`.remove` 在 `.space-row` 这个网格里要贴底对齐。 */
.remove {
  align-self: end;
}

/* ── 结果 ───────────────────────────────────────────────────────────────── */
.result {
  margin-top: var(--space-5);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-border);
}

.result h2 {
  margin: 0 0 var(--space-1);
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: 600;
  color: var(--color-text);
}

.note {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}

.spaces {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: var(--space-3);
}

.spaces > li {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.space-name {
  margin: 0 0 var(--space-1);
  font-weight: 600;
  color: var(--color-text);
}

.space-name small {
  margin-left: var(--space-2);
  color: var(--color-text-muted);
  font-weight: 400;
}

.dims {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

.spaces dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-1) var(--space-3);
  margin: 0;
  padding-top: var(--space-2);
  border-top: 1px solid var(--color-border);
  font-size: var(--text-xs);
}

.spaces dt {
  color: var(--color-text-secondary);
}

.spaces dd {
  margin: 0;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
.error {
  margin: var(--space-3) 0 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

.panel .error:first-child {
  margin-top: 0;
}

.errors {
  margin: var(--space-3) 0 0;
  padding-left: var(--space-5);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

.hint {
  margin: 0;
  padding: var(--space-5);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-align: center;
}
</style>
