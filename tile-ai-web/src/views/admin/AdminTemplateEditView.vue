<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listAdminDicts } from '@/api/admin/dicts'
import type { Dict } from '@/api/admin/dicts'
import { createTemplate, getTemplate, updateTemplate } from '@/api/admin/templates'
import type { UnitTemplateUpsert } from '@/api/admin/templates'
import { validationErrorsOf } from '@/api/errors'
import { useAdminAuthStore } from '@/stores/adminAuth'
import type { LayoutAreas, LayoutModel, LayoutSpace, Opening, OpeningType, ProductStatus } from '@/api/types'

/**
 * 户型模板 新增 / 编辑(F-0.6)。
 *
 * ## 一条硬规则:`areas` 绝不进表单
 *
 * 地面/墙面/局部的面积是**服务端按长宽高派生**的(架构 §8.3),读详情时后端也会回填它。
 * 所以:
 *   - 表单里**没有任何 areas 输入项**;
 *   - 提交前**显式把它剔除**(见 `buildLayout`)—— 不能整对象展开,
 *     否则就是把服务端算出来的派生值当"用户输入"又送回去了,等于前端也实现了一遍派生规则。
 * 页面上只**只读展示**它,并注明"由系统派生"。
 */
const route = useRoute()
const router = useRouter()
const auth = useAdminAuthStore()

const OPENING_OPTIONS: { value: OpeningType; label: string }[] = [
  { value: 'door', label: '门' },
  { value: 'window', label: '窗' },
]

interface OpeningRow {
  type: OpeningType
  w: string | number
  h: string | number
}

interface SpaceRow {
  type: string
  name: string
  length: string | number
  width: string | number
  height: string | number
  wallCount: string | number
  openings: OpeningRow[]
  /** 服务端派生的面积,**只读展示**;提交时不带 */
  areas?: LayoutAreas
}

const editingId = ref<number | null>(null)
const isEdit = computed(() => editingId.value !== null)

const form = reactive({
  name: '',
  roomCount: '' as string | number,
  areaRange: '',
  structure: '',
  status: 'on_sale' as ProductStatus,
})

const spaces = ref<SpaceRow[]>([])
const spaceOptions = ref<Dict[]>([])

const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const fieldErrors = ref<string[]>([])

const canWrite = computed(() => auth.has('template:write'))

function blankSpace(): SpaceRow {
  return { type: '', name: '', length: '', width: '', height: '', wallCount: '', openings: [] }
}

async function loadDicts() {
  // 取不到就让 type 退化成手输(下面的输入框在没选项时仍可填)
  const [result] = await Promise.allSettled([listAdminDicts('space')])
  if (result.status === 'fulfilled') spaceOptions.value = result.value
}

function toRow(space: LayoutSpace): SpaceRow {
  return {
    type: space.type,
    name: space.name ?? '',
    length: space.length,
    width: space.width,
    height: space.height,
    wallCount: space.wallCount ?? '',
    openings: (space.openings ?? []).map((opening: Opening) => ({
      type: opening.type,
      w: opening.w,
      h: opening.h,
    })),
    areas: space.areas,
  }
}

async function loadTemplate() {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    errorText.value = '模板编号不合法'
    return
  }
  loading.value = true
  try {
    const template = await getTemplate(id)
    editingId.value = id
    form.name = template.name ?? ''
    form.roomCount = template.roomCount ?? ''
    form.areaRange = template.areaRange ?? ''
    form.structure = template.structure ?? ''
    form.status = template.status === 'off_sale' ? 'off_sale' : 'on_sale'
    spaces.value = (template.layout?.spaces ?? []).map(toRow)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function addSpace() {
  spaces.value.push(blankSpace())
}

function removeSpace(index: number) {
  spaces.value.splice(index, 1)
}

function addOpening(spaceIndex: number) {
  spaces.value[spaceIndex].openings.push({ type: 'door', w: 0.8, h: 2.1 })
}

function removeOpening(spaceIndex: number, openingIndex: number) {
  spaces.value[spaceIndex].openings.splice(openingIndex, 1)
}

function numberOrUndefined(value: string | number): number | undefined {
  const text = String(value).trim()
  if (text === '') return undefined
  const parsed = Number(text)
  return Number.isFinite(parsed) ? parsed : undefined
}

/**
 * 组装 `layout`。**逐字段重建,`areas` 一律不出现** —— 它是服务端派生字段。
 * 这里刻意不写 `...space` 展开:`areas` 会跟着一起被带进去。
 */
function buildLayout(): LayoutModel {
  return {
    spaces: spaces.value.map((space) => {
      const wallCount = numberOrUndefined(space.wallCount)
      return {
        type: space.type,
        name: space.name.trim() || undefined,
        length: Number(space.length),
        width: Number(space.width),
        height: Number(space.height),
        ...(wallCount === undefined ? {} : { wallCount }),
        ...(space.openings.length
          ? {
              openings: space.openings.map((opening) => ({
                type: opening.type,
                w: Number(opening.w),
                h: Number(opening.h),
              })),
            }
          : {}),
        // ⚠️ areas 绝不出现
      }
    }),
  }
}

function buildBody(): UnitTemplateUpsert {
  const roomCount = numberOrUndefined(form.roomCount)
  const areaRange = form.areaRange.trim()
  const structure = form.structure.trim()
  return {
    name: form.name.trim(),
    ...(roomCount === undefined ? {} : { roomCount }),
    ...(areaRange ? { areaRange } : {}),
    ...(structure ? { structure } : {}),
    layout: buildLayout(),
    status: form.status,
  }
}

function validate(): string[] {
  const problems: string[] = []
  if (!form.name.trim()) problems.push('名称:必填')
  if (spaces.value.length < 1) problems.push('户型:至少需要一个空间')
  spaces.value.forEach((space, index) => {
    if (!space.type.trim()) problems.push(`空间 ${index + 1}:请选择或填写空间类型`)
    for (const field of ['length', 'width', 'height'] as const) {
      const value = numberOrUndefined(space[field])
      if (value === undefined || value <= 0) {
        problems.push(`空间 ${index + 1}:长/宽/高必须是正数`)
        break
      }
    }
  })
  return problems
}

async function submit() {
  errorText.value = ''
  fieldErrors.value = validate()
  if (fieldErrors.value.length) return

  saving.value = true
  try {
    const body = buildBody()
    if (editingId.value === null) await createTemplate(body)
    else await updateTemplate(editingId.value, body)
    await router.push({ name: 'admin-templates' })
  } catch (error) {
    fieldErrors.value = validationErrorsOf(error).map((item) => `${item.field}:${item.reason}`)
    if (!fieldErrors.value.length) {
      errorText.value = error instanceof Error ? error.message : '保存失败'
    }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  if (route.name === 'admin-template-edit') await loadTemplate()
  else spaces.value = [blankSpace()]
})
</script>

<template>
  <section>
    <header class="head">
      <h1>{{ isEdit ? '编辑户型模板' : '新增户型模板' }}</h1>
      <RouterLink :to="{ name: 'admin-templates' }">返回列表</RouterLink>
    </header>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <ul v-if="fieldErrors.length" class="errors">
      <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
    </ul>

    <p v-if="loading" class="hint">加载中…</p>

    <form v-else class="panel" @submit.prevent="submit">
      <label>
        模板名称
        <input v-model="form.name" type="text" />
      </label>

      <div class="row">
        <label>
          居室数
          <input v-model="form.roomCount" type="number" />
        </label>
        <label>
          面积区间
          <input v-model="form.areaRange" type="text" placeholder="如 80-100㎡" />
        </label>
        <label>
          户型结构
          <input v-model="form.structure" type="text" placeholder="如 三室两厅" />
        </label>
      </div>

      <label>
        状态
        <select v-model="form.status">
          <option value="on_sale">上架</option>
          <option value="off_sale">下架</option>
        </select>
      </label>

      <h2>户型</h2>
      <p class="hint">
        每个空间填长宽高(米)。面积由系统按长宽高派生,**不需要也不能在这里填**。
      </p>

      <ul class="spaces">
        <li v-for="(space, spaceIndex) in spaces" :key="spaceIndex">
          <div class="row">
            <label>
              空间类型
              <select v-if="spaceOptions.length" v-model="space.type">
                <option value="">请选择</option>
                <option v-for="item in spaceOptions" :key="item.key" :value="item.key">
                  {{ item.label }}
                </option>
              </select>
              <input v-else v-model="space.type" type="text" placeholder="字典取不到,请填 key" />
            </label>
            <label>
              名称
              <input v-model="space.name" type="text" placeholder="如 主卫" />
            </label>
            <button type="button" class="danger" @click="removeSpace(spaceIndex)">移除空间</button>
          </div>

          <div class="row">
            <label>
              长(米)
              <input v-model="space.length" type="number" step="0.01" />
            </label>
            <label>
              宽(米)
              <input v-model="space.width" type="number" step="0.01" />
            </label>
            <label>
              层高(米)
              <input v-model="space.height" type="number" step="0.01" />
            </label>
            <label>
              墙面数(可选)
              <input v-model="space.wallCount" type="number" />
            </label>
          </div>

          <!-- 派生面积:只读展示,不是输入项、也不提交 -->
          <p v-if="space.areas" class="hint">
            派生面积(只读):地面 {{ space.areas.floor ?? '—' }} ㎡ ·
            墙面 {{ space.areas.wall ?? '—' }} ㎡ ·
            局部 {{ space.areas.local ?? '—' }} ㎡
          </p>

          <p class="hint">门窗开口(可选,用于面积扣减):</p>
          <ul class="openings">
            <li v-for="(opening, openingIndex) in space.openings" :key="openingIndex" class="row">
              <label>
                类型
                <select v-model="opening.type">
                  <option v-for="option in OPENING_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label>
                宽(米)
                <input v-model="opening.w" type="number" step="0.01" />
              </label>
              <label>
                高(米)
                <input v-model="opening.h" type="number" step="0.01" />
              </label>
              <button type="button" @click="removeOpening(spaceIndex, openingIndex)">移除</button>
            </li>
          </ul>
          <button type="button" class="ghost" @click="addOpening(spaceIndex)">加一个开口</button>
        </li>
      </ul>

      <button type="button" class="ghost" @click="addSpace">加一个空间</button>

      <div class="actions">
        <button v-if="canWrite" type="submit" class="primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <span v-else class="hint">当前账号没有修改权限。</span>
      </div>
    </form>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.head h1 {
  margin: 0 0 0.5rem;
  font-size: 1.3rem;
}

h2 {
  margin: 1rem 0 0.5rem;
  font-size: 1rem;
}

.hint {
  color: #6b7280;
  font-size: 0.8rem;
}

.error {
  padding: 0.5rem 0.75rem;
  border: 1px solid #fca5a5;
  border-radius: 0.25rem;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 0.85rem;
}

.errors {
  margin: 0.5rem 0;
  padding-left: 1.2rem;
  color: #b91c1c;
  font-size: 0.85rem;
}

.panel {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  padding: 1rem;
  max-width: 52rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  font-size: 0.85rem;
  color: #374151;
}

input,
select {
  padding: 0.4rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
}

.row {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
}

.spaces {
  list-style: none;
  margin: 0;
  padding: 0;
}

.spaces > li {
  border: 1px solid #e5e7eb;
  border-radius: 0.25rem;
  padding: 0.75rem;
  margin-bottom: 0.75rem;
}

.openings {
  list-style: none;
  margin: 0 0 0.5rem;
  padding: 0;
}

button {
  padding: 0.35rem 0.8rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

button.primary {
  padding: 0.5rem 1rem;
  border-color: #1d4ed8;
  background: #1d4ed8;
  color: white;
}

button.danger {
  border-color: #fca5a5;
  color: #b91c1c;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 1rem;
}
</style>
