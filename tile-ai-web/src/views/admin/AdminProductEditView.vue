<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { createProduct, getProduct, updateProduct } from '@/api/admin/products'
import type { ProductUpsert } from '@/api/admin/products'
// ProductImage 来自 common 契约(不是 admin 专有),已挪到共享类型
import type { ProductImage } from '@/api/types'
import { listDictsOfType } from '@/api/dicts'
import type { Dict } from '@/api/dicts'
import { validationErrorsOf } from '@/api/errors'

/**
 * 新增 / 编辑 SKU(F-0.3)。
 *
 * 两个刻意的做法:
 *
 * 1. **适用空间与风格标签用字典多选,不让手敲**(架构 §10.1 · D-14):
 *    手敲的值与字典对不上,前台按字典筛就永远查不到这件砖。
 * 2. **提交体逐字段对应契约 `ProductUpsert`,不夹带 `id`** ——
 *    `id` 在路径里;把它塞进请求体既不在契约内,也让服务端无从判断该信谁。
 */
const route = useRoute()
const router = useRouter()

const editingId = computed(() => {
  const raw = route.params.id
  return raw === undefined ? null : Number(raw)
})
const isEdit = computed(() => editingId.value !== null)

const spaces = ref<Dict[]>([])
const styles = ref<Dict[]>([])

const form = reactive<ProductUpsert>({
  sku: '',
  name: '',
  spec: '',
  material: '',
  surface: '',
  colorPattern: '',
  pricePerSqm: 0,
  applicableSpaces: [],
  styleTags: [],
  status: 'on_sale',
  coverImage: '',
  images: [],
})

const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const fieldErrors = ref<string[]>([])

async function loadDicts() {
  try {
    // 字典是公开接口;失败不该挡住表单 —— 选项拿不到时仍可提交已选值
    spaces.value = await listDictsOfType('space')
    styles.value = await listDictsOfType('style')
  } catch {
    spaces.value = []
    styles.value = []
  }
}

async function loadProduct() {
  if (editingId.value === null) return
  loading.value = true
  try {
    const sku = await getProduct(editingId.value)
    form.sku = sku.sku
    form.name = sku.name
    form.spec = sku.spec ?? ''
    form.material = sku.material ?? ''
    form.surface = sku.surface ?? ''
    form.colorPattern = sku.colorPattern ?? ''
    form.pricePerSqm = sku.pricePerSqm
    form.applicableSpaces = [...(sku.applicableSpaces ?? [])]
    form.styleTags = [...(sku.styleTags ?? [])]
    form.status = sku.status
    form.coverImage = sku.coverImage ?? ''
    form.images = (sku.images ?? []).map((image) => ({ ...image }))
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function toggle(list: string[] | undefined, key: string) {
  const target = list ?? []
  const index = target.indexOf(key)
  if (index >= 0) target.splice(index, 1)
  else target.push(key)
}

function addImage() {
  form.images = [...(form.images ?? []), { url: '', angle: '', sort: (form.images?.length ?? 0) }]
}

function removeImage(index: number) {
  form.images = (form.images ?? []).filter((_item, i) => i !== index)
}

/** 组装提交体 —— 只放契约声明的字段。 */
function buildBody(): ProductUpsert {
  return {
    sku: form.sku.trim(),
    name: form.name.trim(),
    spec: form.spec?.trim() || undefined,
    material: form.material?.trim() || undefined,
    surface: form.surface?.trim() || undefined,
    colorPattern: form.colorPattern?.trim() || undefined,
    pricePerSqm: Number(form.pricePerSqm),
    applicableSpaces: form.applicableSpaces ?? [],
    styleTags: form.styleTags ?? [],
    status: form.status,
    coverImage: form.coverImage?.trim() || undefined,
    // 丢掉空 URL 的图片行 —— 它们在界面上是"还没填",不该当成一条合法数据提交
    images: (form.images ?? []).filter((image: ProductImage) => image.url.trim() !== ''),
  }
}

async function submit() {
  errorText.value = ''
  fieldErrors.value = []

  if (!form.sku.trim() || !form.name.trim()) {
    errorText.value = '编号与名称为必填'
    return
  }

  saving.value = true
  try {
    const body = buildBody()
    if (editingId.value === null) await createProduct(body)
    else await updateProduct(editingId.value, body)
    await router.push({ name: 'admin-products' })
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '保存失败'
    // 10001 会把逐字段原因放在 data.errors[] —— 直接展示,别让用户猜哪一项不对
    fieldErrors.value = validationErrorsOf(error).map((item) => `${item.field}:${item.reason}`)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  await loadProduct()
})
</script>

<template>
  <section>
    <header class="page-head">
      <h1 class="page-head__title">{{ isEdit ? '编辑 SKU' : '新增 SKU' }}</h1>
    </header>

    <p v-if="loading" class="hint">加载中…</p>

    <form v-else class="form" @submit.prevent="submit">
      <!-- 标量字段:DOM 顺序不变(制表顺序跟着走),只是两列排开 -->
      <div class="grid">
        <label>
          产品编号 <em>*</em>
          <input v-model="form.sku" type="text" placeholder="如 TL-2043" />
          <small>业务主键,唯一</small>
        </label>

        <label>
          名称 <em>*</em>
          <input v-model="form.name" type="text" placeholder="如 雅灰石纹" />
        </label>

        <label>
          单价(元/㎡) <em>*</em>
          <input v-model.number="form.pricePerSqm" type="number" step="0.01" min="0" />
          <small>元/㎡ —— 报价引擎按此乘面积与损耗率</small>
        </label>

        <label>
          规格
          <input v-model="form.spec" type="text" placeholder="如 750×1500mm" />
        </label>

        <label>
          材质
          <input v-model="form.material" type="text" placeholder="如 釉面砖" />
        </label>

        <label>
          光泽度
          <input v-model="form.surface" type="text" placeholder="如 柔光" />
        </label>

        <label>
          花色 / 纹理
          <input v-model="form.colorPattern" type="text" placeholder="如 浅灰细石纹" />
        </label>

        <label>
          状态
          <select v-model="form.status">
            <option value="on_sale">上架</option>
            <option value="off_sale">下架</option>
          </select>
        </label>
      </div>

      <fieldset>
        <legend>适用空间</legend>
        <p v-if="!spaces.length" class="hint">字典未配置空间项</p>
        <div class="checks">
          <label v-for="item in spaces" :key="item.key" class="check">
            <input
              type="checkbox"
              :checked="(form.applicableSpaces ?? []).includes(item.key)"
              @change="toggle(form.applicableSpaces, item.key)"
            />
            {{ item.label }}
          </label>
        </div>
      </fieldset>

      <fieldset>
        <legend>风格标签</legend>
        <p v-if="!styles.length" class="hint">字典未配置风格项</p>
        <div class="checks">
          <label v-for="item in styles" :key="item.key" class="check">
            <input
              type="checkbox"
              :checked="(form.styleTags ?? []).includes(item.key)"
              @change="toggle(form.styleTags, item.key)"
            />
            {{ item.label }}
          </label>
        </div>
      </fieldset>

      <fieldset>
        <legend>图片</legend>
        <label class="full">
          封面图 URL
          <input v-model="form.coverImage" type="text" placeholder="https://oss/..." />
          <small>图片一律存对象存储,库里只存 URL</small>
        </label>

        <p class="sub-legend">多角度图</p>
        <div v-for="(image, index) in form.images ?? []" :key="index" class="image-row">
          <input v-model="image.url" type="text" placeholder="图片 URL" />
          <input v-model="image.angle" type="text" placeholder="角度,如 side" class="narrow" />
          <input v-model.number="image.sort" type="number" class="narrow" />
          <button type="button" class="danger" @click="removeImage(index)">移除</button>
        </div>
        <button type="button" class="ghost" @click="addImage">添加一张</button>
      </fieldset>

      <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
      <ul v-if="fieldErrors.length" class="error errors">
        <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
      </ul>

      <div class="buttons">
        <button type="submit" class="primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button type="button" @click="router.back()">取消</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 本页**没有测试用例** —— 改动无测试可证,只能等人眼验收(纪律 7)。 */

.page-head {
  margin-bottom: var(--space-4);
}

.page-head__title {
  margin: 0;
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 700;
  color: var(--color-text);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 46rem;
}

/* ── 标量字段:两列,窄屏自动落回一列 ───────────────────────────────────── */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: var(--space-4);
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
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

label.full {
  max-width: none;
  margin-bottom: var(--space-3);
}

/* 必填星号 —— 红色 + 星号,**不靠颜色单独承载信息** */
em {
  color: var(--color-danger);
  font-style: normal;
}

small {
  color: var(--color-text-muted);
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

/* ── 分组 ───────────────────────────────────────────────────────────────── */
fieldset {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

legend {
  padding: 0 var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
}

.sub-legend {
  margin: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.checks {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
}

.check {
  flex-direction: row;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
}

/* 多选/单选不该被拉成整行高 —— 覆盖上面的 input 规则 */
.check input {
  height: auto;
  padding: 0;
  width: auto;
}

/* ── 多角度图行 ─────────────────────────────────────────────────────────── */
.image-row {
  display: flex;
  gap: var(--space-2);
  width: 100%;
}

.image-row input:first-child {
  flex: 1;
}

.narrow {
  width: 6rem;
}

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
.buttons {
  display: flex;
  gap: var(--space-2);
}

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

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ghost {
  align-self: flex-start;
}

/* 移除是危险操作 —— 危险色 + 明确的文字 */
.danger {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.danger:hover:not(:disabled) {
  background: var(--color-danger-soft);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
.error {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

.errors {
  padding-left: var(--space-6);
}

.hint {
  margin: 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-muted);
}
</style>
