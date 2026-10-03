<script setup lang="ts">
import { Select, Skeleton } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { createCombo, getCombo, updateCombo } from '@/api/admin/combos'
import type { ComboUpsert } from '@/api/admin/combos'
import { listAdminDicts } from '@/api/admin/dicts'
import type { Dict } from '@/api/admin/dicts'
import { listProducts } from '@/api/admin/products'
import { validationErrorsOf } from '@/api/errors'
import { useAdminAuthStore } from '@/stores/adminAuth'
import type { Part, ProductStatus, Sku } from '@/api/types'

/**
 * 推荐搭配 新增 / 编辑(F-0.5)。
 *
 * ## 三处必须当心的地方
 *
 * 1. **`note` 必须原样带回去**。契约的 `ComboItem` 有可选的 `note`,后端会落库,
 *    而 `PUT` 是 **items 整体替换** —— 编辑时把 note 丢了,备注就被**静默清空**,
 *    不会有任何报错。所以这里先 `getCombo` 拿全量、逐字段回填。
 * 2. **`name` 不提交**。它只是"展示用的冗余名",库里不存,读取时由后端从产品表带出来
 *    (`ComboService` 的注释写明了)。表单里显示它、提交时丢掉。
 * 3. **SKU 没有"按编号查"的接口**,只有"按关键词搜产品"。所以是"搜索 → 点选",
 *    同时允许手输;写错的兜底是服务端(引用了不存在的编号会回 **10001**)。
 */
const route = useRoute()
const router = useRouter()
const auth = useAdminAuthStore()

const PART_LABELS: Record<Part, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部',
}
/** 部位恒为三值(契约 T-9),**不走字典** —— 字典只有空间/风格 */
const PART_OPTIONS: Part[] = ['floor', 'wall', 'local']

interface RowItem {
  sku: string
  note: string
  part: Part
  /** 展示用的冗余名,来自产品表或搜索候选;**不提交** */
  name?: string
  /** 已有的排序值,**原样保留**;新增行不带(由服务端决定) */
  sort?: number
}

const editingId = ref<number | null>(null)
const isEdit = computed(() => editingId.value !== null)

const form = reactive({
  name: '',
  space: '',
  style: '',
  refPriceMin: '' as string | number,
  refPriceMax: '' as string | number,
  coverImage: '',
  status: 'on_sale' as ProductStatus,
})

const items = ref<RowItem[]>([])

const spaceOptions = ref<Dict[]>([])
const styleOptions = ref<Dict[]>([])

/** 搜索候选 —— 一次只服务一行 */
const candidates = ref<Sku[]>([])
const candidateFor = ref<number | null>(null)

const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const fieldErrors = ref<string[]>([])

const canWrite = computed(() => auth.has('combo:write'))

async function loadDicts() {
  const [spaces, styles] = await Promise.allSettled([listAdminDicts('space'), listAdminDicts('style')])
  if (spaces.status === 'fulfilled') spaceOptions.value = spaces.value
  if (styles.status === 'fulfilled') styleOptions.value = styles.value
}

async function loadCombo() {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    errorText.value = '搭配编号不合法'
    return
  }
  loading.value = true
  try {
    const combo = await getCombo(id)
    editingId.value = id
    form.name = combo.name ?? ''
    form.space = combo.space ?? ''
    form.style = combo.style ?? ''
    form.refPriceMin = combo.refPriceMin ?? ''
    form.refPriceMax = combo.refPriceMax ?? ''
    form.coverImage = combo.coverImage ?? ''
    form.status = combo.status === 'off_sale' ? 'off_sale' : 'on_sale'
    // ⚠️ note / sort 一定要接住 —— 丢了它们等于把备注清空
    items.value = (combo.items ?? []).map((item) => ({
      sku: item.sku,
      note: item.note ?? '',
      part: item.part,
      name: item.name,
      sort: item.sort,
    }))
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function addItem() {
  items.value.push({ sku: '', note: '', part: 'floor' })
}

function removeItem(index: number) {
  items.value.splice(index, 1)
  if (candidateFor.value === index) {
    candidates.value = []
    candidateFor.value = null
  }
}

async function searchProducts(index: number) {
  const keyword = items.value[index]?.sku.trim()
  if (!keyword) return
  errorText.value = ''
  try {
    const data = await listProducts({ keyword, page: 1, size: 20 })
    candidates.value = data?.list ?? []
    candidateFor.value = index
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '查询产品失败'
  }
}

function pick(index: number, product: Sku) {
  items.value[index].sku = product.sku
  items.value[index].name = product.name
  candidates.value = []
  candidateFor.value = null
}

function numberOrUndefined(value: string | number): number | undefined {
  const text = String(value).trim()
  if (text === '') return undefined
  const parsed = Number(text)
  return Number.isFinite(parsed) ? parsed : undefined
}

/** 组装提交体 —— 逐字段对齐契约,`name` 丢掉。 */
function buildBody(): ComboUpsert {
  const coverImage = form.coverImage.trim()
  return {
    name: form.name.trim(),
    space: form.space,
    style: form.style,
    refPriceMin: numberOrUndefined(form.refPriceMin),
    refPriceMax: numberOrUndefined(form.refPriceMax),
    ...(coverImage ? { coverImage } : {}),
    status: form.status,
    items: items.value
      .filter((item) => item.sku.trim() !== '')
      .map((item) => {
        const note = item.note.trim()
        return {
          sku: item.sku.trim(),
          part: item.part,
          // 空备注不发这个键(契约里它是可选可空的)
          ...(note ? { note } : {}),
          // 已有的排序原样带回;新增行不带,交给服务端
          ...(item.sort === undefined ? {} : { sort: item.sort }),
        }
      }),
  }
}

function validate(): string[] {
  const problems: string[] = []
  if (!form.name.trim()) problems.push('名称:必填')
  if (!form.space) problems.push('空间:必选')
  if (!form.style) problems.push('风格:必选')
  if (items.value.filter((item) => item.sku.trim() !== '').length < 1) {
    problems.push('搭配内容:至少需要一项瓷砖')
  }
  return problems
}

async function submit() {
  errorText.value = ''
  fieldErrors.value = validate()
  if (fieldErrors.value.length) return

  saving.value = true
  try {
    const body = buildBody()
    if (editingId.value === null) await createCombo(body)
    else await updateCombo(editingId.value, body)
    await router.push({ name: 'admin-combos' })
  } catch (error) {
    // 10001 会把逐字段原因放在 data.errors[](如"引用的瓷砖编号不存在:XXX")—— 原样展开
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
  if (route.name === 'admin-combo-edit') await loadCombo()
})
</script>

<template>
  <section>
    <header class="head">
      <h1>{{ isEdit ? '编辑搭配' : '新增搭配' }}</h1>
      <RouterLink :to="{ name: 'admin-combos' }">返回列表</RouterLink>
    </header>

    <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <ul v-if="fieldErrors.length" class="errors">
      <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
    </ul>

    <Skeleton v-if="loading" active :paragraph="{ rows: 6 }" />

    <form v-else class="panel" @submit.prevent="submit">
      <label>
        名称
        <input v-model="form.name" type="text" />
      </label>

      <label>
        空间
        <select v-model="form.space">
          <option value="">请选择</option>
          <option v-for="item in spaceOptions" :key="item.key" :value="item.key">
            {{ item.label }}
          </option>
        </select>
      </label>

      <label>
        风格
        <select v-model="form.style">
          <option value="">请选择</option>
          <option v-for="item in styleOptions" :key="item.key" :value="item.key">
            {{ item.label }}
          </option>
        </select>
      </label>

      <div class="row">
        <label>
          参考价下限(元)
          <input v-model="form.refPriceMin" type="number" />
        </label>
        <label>
          参考价上限(元)
          <input v-model="form.refPriceMax" type="number" />
        </label>
      </div>

      <label>
        展示图 URL
        <input v-model="form.coverImage" type="text" />
      </label>

      <label>
        状态
        <select v-model="form.status">
          <option value="on_sale">上架</option>
          <option value="off_sale">下架</option>
        </select>
      </label>

      <h2>搭配内容</h2>
      <p class="hint">
        至少一项。编号可以先搜再选(按名称或编号搜),也可以直接填 ——
        填错的编号服务端会拒掉并说明是哪一个。
      </p>

      <ul class="items">
        <li v-for="(item, index) in items" :key="index">
          <div class="row">
            <label>
              瓷砖编号
              <input v-model="item.sku" type="text" placeholder="如 TL-1001" />
            </label>
            <button type="button" class="ghost" @click="searchProducts(index)">查</button>
            <span v-if="item.name" class="hint">{{ item.name }}</span>
          </div>

          <div class="row">
            <label>
              部位
              <!-- ⚠️ `part-select` 这个 class 是**测试锚点**:antd 的 Select 没有原生
                   `<select>` 可供定位,测试得靠一个稳定的钩子。见 §68 / G1.5。 -->
              <Select v-model:value="item.part" class="part-select">
                <Select.Option v-for="part in PART_OPTIONS" :key="part" :value="part">
                  {{ PART_LABELS[part] }}
                </Select.Option>
              </Select>
            </label>
            <label>
              备注(可选)
              <input v-model="item.note" type="text" />
            </label>
            <button type="button" class="danger" @click="removeItem(index)">移除</button>
          </div>

          <ul v-if="candidateFor === index && candidates.length" class="candidates">
            <li v-for="product in candidates" :key="product.sku">
              <button type="button" class="link" @click="pick(index, product)">
                {{ product.sku }} · {{ product.name }}
              </button>
            </li>
          </ul>
          <p v-else-if="candidateFor === index" class="hint">没有匹配的产品。</p>
        </li>
      </ul>

      <button type="button" class="ghost" @click="addItem">加一项</button>

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
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   测试靠 `findAll('select')` 里"option 含 `local`"的那个定位**部位下拉**,
   并断言它的 option 值序列,所以下拉的**结构与顺序**必须原样。 */

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
.error {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

.errors {
  margin: var(--space-3) 0;
  padding-left: var(--space-5);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

/* ── 表单卡 ─────────────────────────────────────────────────────────────── */
.panel {
  max-width: 44rem;
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

input,
select {
  height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

input:focus-visible,
select:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

.row {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
}

/* 行内的 label 不该把行撑成两截 —— 让它自己收缩 */
.row > label {
  flex: 0 1 auto;
}

/* ── 搭配内容(一行一项)─────────────────────────────────────────────────── */
.items {
  list-style: none;
  margin: 0;
  padding: 0;
}

/* 每一项做成一张内嵌小卡 —— 它自己还有两行字段 + 候选列表 */
.items > li {
  margin-bottom: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
}

/* ── 搜索候选 ───────────────────────────────────────────────────────────── */
.candidates {
  list-style: none;
  margin: var(--space-2) 0 0;
  padding: 0;
}

/* 候选是"从搜索结果里点一条" — 做成可点的行,而不是裸链接 */
.link {
  display: block;
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-primary);
  font: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.link:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
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

/* 移除 / 删除是危险操作 —— 危险色 + 明确的文字,不靠图标暗示 */
button.danger {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

button.danger:hover:not(:disabled) {
  background: var(--color-danger-soft);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
}
</style>
