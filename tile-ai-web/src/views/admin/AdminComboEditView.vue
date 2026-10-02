<script setup lang="ts">
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

    <p v-if="loading" class="hint">加载中…</p>

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
              <select v-model="item.part">
                <option v-for="part in PART_OPTIONS" :key="part" :value="part">
                  {{ PART_LABELS[part] }}
                </option>
              </select>
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
  max-width: 44rem;
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

.items {
  list-style: none;
  margin: 0;
  padding: 0;
}

.items > li {
  border: 1px solid #e5e7eb;
  border-radius: 0.25rem;
  padding: 0.75rem;
  margin-bottom: 0.75rem;
}

.candidates {
  list-style: none;
  margin: 0.25rem 0 0;
  padding: 0;
}

.link {
  border: none;
  background: none;
  padding: 0.15rem 0;
  color: #1d4ed8;
  cursor: pointer;
  font: inherit;
  text-align: left;
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
