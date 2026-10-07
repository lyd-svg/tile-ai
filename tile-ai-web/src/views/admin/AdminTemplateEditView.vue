<script setup lang="ts">
import { Alert, Button, Select, Skeleton } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listAdminDicts } from '@/api/admin/dicts'
import type { Dict } from '@/api/admin/dicts'
import { createTemplate, getTemplate, updateTemplate } from '@/api/admin/templates'
import type { UnitTemplateUpsert } from '@/api/admin/templates'
import { validationErrorsOf } from '@/api/errors'
import { useAdminAuthStore } from '@/stores/adminAuth'
import type { LayoutAreas, LayoutModel, LayoutSpace, Opening, OpeningType, OpeningWall, ProductStatus } from '@/api/types'

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

/**
 * 门窗在哪面墙(§8.3)。
 *
 * 文案用"对面墙/左墙/右墙"而不是 back/left/right —— 填表的人站在房间门口看,
 * 这个说法他才对得上。**没有"前墙"**:出图时相机在房间外,前墙在镜头背后,
 * 一点透视里永远拍不到,给了只会让人以为填漏了。
 */
const WALL_OPTIONS: { value: OpeningWall; label: string }[] = [
  { value: 'back', label: '对面墙' },
  { value: 'left', label: '左墙' },
  { value: 'right', label: '右墙' },
]

interface OpeningRow {
  type: OpeningType
  w: string | number
  h: string | number
  /**
   * 位置三件套。**都可以留空** —— 留空即"不知道位置",
   * 出图时的白模上不画这个洞(但不影响出图,也不影响报价)。
   */
  wall: OpeningWall | ''
  offset: string | number
  sill: string | number
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
      // ⚠️ 存量数据里这三个可能是 `undefined`(位置字段是 2026-10-07 才加的)——
      // 一律落成 `''`,让输入框是空的、而不是显示 "undefined"。
      wall: opening.wall ?? '',
      offset: opening.offset ?? '',
      sill: opening.sill ?? '',
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
  // 默认给"对面墙、居中偏左"—— 常见户型的门就在那。用户可以改,也可以清空。
  spaces.value[spaceIndex].openings.push({
    type: 'door', w: 0.8, h: 2.1, wall: 'back', offset: 0.5, sill: '',
  })
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
              openings: space.openings.map((opening) => {
                const offset = numberOrUndefined(opening.offset)
                const sill = numberOrUndefined(opening.sill)
                // 位置**可以留空** —— 不填就不带这三个键,后端当"位置未知"处理
                // (白模上不画这个洞)。`wall` 与 `offset` 必须成对,缺一即视为没填。
                //
                // ⚠️ 条件**内联**而不是抽成 `positioned` 变量:TS 靠这条判断把
                // `opening.wall` 从 `OpeningWall | ''` 收窄成 `OpeningWall`,
                // 抽成变量它就收窄不了,编译会报类型不符。
                const at = opening.wall !== '' && offset !== undefined
                  ? { wall: opening.wall, offset }
                  : {}
                return {
                  type: opening.type,
                  w: Number(opening.w),
                  h: Number(opening.h),
                  ...at,
                  ...(opening.wall !== '' && offset !== undefined && sill !== undefined ? { sill } : {}),
                }
              }),
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

    <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
    <ul v-if="fieldErrors.length" class="errors">
      <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
    </ul>

    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

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
        <Select
          v-model:value="form.status"
          class="status-select"
          :options="[
            { value: 'on_sale', label: '上架' },
            { value: 'off_sale', label: '下架' },
          ]"
        />
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
              <Select
                v-if="spaceOptions.length"
                v-model:value="space.type"
                class="space-type-select"
                :options="[
                  { value: '', label: '请选择' },
                  ...spaceOptions.map((item) => ({ value: item.key, label: item.label })),
                ]"
              />
              <input v-else v-model="space.type" type="text" placeholder="字典取不到,请填 key" />
            </label>
            <label>
              名称
              <input v-model="space.name" type="text" placeholder="如 主卫" />
            </label>
            <Button danger @click="removeSpace(spaceIndex)">移除空间</Button>
          </div>

          <div class="row">
            <label>
              长(米)
              <input v-model="space.length" type="number" step="0.01" min="0.01" max="30" />
            </label>
            <label>
              宽(米)
              <input v-model="space.width" type="number" step="0.01" min="0.01" max="30" />
            </label>
            <label>
              层高(米)
              <input v-model="space.height" type="number" step="0.01" min="0.01" max="10" />
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

          <p class="hint">
            门窗开口(可选):宽高用于面积扣减;<b>位置用于出图时画房间底图</b>,不填也能出图。
          </p>
          <ul class="openings">
            <li v-for="(opening, openingIndex) in space.openings" :key="openingIndex" class="row">
              <label>
                类型
                <Select
                  v-model:value="opening.type"
                  class="opening-type-select"
                  :options="OPENING_OPTIONS"
                />
              </label>
              <label>
                宽(米)
                <input v-model="opening.w" type="number" step="0.01" min="0.01" max="30" />
              </label>
              <label>
                高(米)
                <input v-model="opening.h" type="number" step="0.01" min="0.01" max="10" />
              </label>
              <label>
                哪面墙(可选)
                <Select
                  v-model:value="opening.wall"
                  class="opening-wall-select"
                  :options="[{ value: '', label: '不填' }, ...WALL_OPTIONS]"
                />
              </label>
              <label>
                距墙角(米)
                <input v-model="opening.offset" type="number" step="0.01" min="0" max="30" />
              </label>
              <label>
                离地高(米)
                <input v-model="opening.sill" type="number" step="0.01" min="0" max="10" placeholder="门 0 / 窗 0.9" />
              </label>
              <Button @click="removeOpening(spaceIndex, openingIndex)">移除</Button>
            </li>
          </ul>
          <p class="hint">
            位置按<b>站在房间门口往里看</b>算:「对面墙 / 左墙 / 右墙」是所见的那三面;
            「距墙角」= 洞口中心离该墙**起点角**的距离(对面墙从<b>左下角</b>量,
            左右墙从<b>靠门口那端</b>量)。
          </p>
          <Button @click="addOpening(spaceIndex)">加一个开口</Button>
        </li>
      </ul>

      <Button @click="addSpace">加一个空间</Button>

      <div class="actions">
        <Button v-if="canWrite" type="primary" html-type="submit" :loading="saving">保存</Button>
        <span v-else class="hint">当前账号没有修改权限。</span>
      </div>
    </form>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试靠 `findAll('label')` 找到**文案含「长(米)」的 label** 再取里面的 `input`,
   还断言 `findAll('select')` 的 option 值里**必须出现 `door` / `window`**。
   ⇒ 标签文案、控件类型与嵌套关系都不能变。
   ⚠️ 派生的面积是**只读展示**的一条提示,样式上不要做成输入框的样子。 */

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.head h1 {
  margin: 0 0 var(--space-1);
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 700;
  color: var(--color-text);
}

.head a {
  color: var(--color-primary);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.head a:hover {
  text-decoration: underline;
}

h2 {
  margin: var(--space-5) 0 var(--space-2);
  font-size: var(--text-base);
  line-height: var(--leading-base);
  font-weight: 600;
  color: var(--color-text);
}

.hint {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */

.errors {
  margin: var(--space-3) 0;
  padding-left: var(--space-5);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

/* ── 表单卡 ─────────────────────────────────────────────────────────────── */
.panel {
  max-width: 52rem;
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-bottom: var(--space-4);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

/* 三个下拉已迁 antd `Select`(§72.6),但**输入框仍是原生的** ——
   而 antd `Select` 内部会渲染 `<input class="ant-select-selection-search-input">`,
   裸 `input` 会照样命中它(§5.6)。故显式排除库的元素。 */
input:not([class*='ant-']) {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

input:not([class*='ant-']):focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
}

/* ── 空间列表(每个空间一张卡)─────────────────────────────────────────── */
.spaces {
  list-style: none;
  margin: 0;
  padding: 0;
}

.spaces > li {
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
}

/* ── 门窗开口 ───────────────────────────────────────────────────────────── */
.openings {
  list-style: none;
  margin: 0 0 var(--space-3);
  padding: 0;
}

/* ⚠️ **本页按钮已全部迁 antd**(移除空间 / 移除 / 加一个开口 / 加一个空间 / 保存)——
   不再写样式(§70.9)。原来这里是**裸 `button {}`**,它会照样命中 antd 的
   `<button class="ant-btn">` ⇒ 整块删掉(`tile-ai-design.md` §5.6)。 */

.actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
}
</style>
