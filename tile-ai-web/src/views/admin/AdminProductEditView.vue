<script setup lang="ts">
import { Alert, Button, Checkbox, Input, InputNumber, Select, Skeleton } from 'ant-design-vue'
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
 *
 * ⚠️ **2026-10-03:本页控件已迁到 antd**(阶段 G1 试点,见 `openapi-verification.md` §68)。
 *    `<script>` 的改动**只有两处,都不含业务逻辑**:
 *      ① 新增一行 antd 组件 import;
 *      ② 新增一个只服务模板的静态选项常量 `STATUS_OPTIONS`(上架/下架的展示名)。
 *    **业务逻辑一行未动**:所有 `v-model` 绑定的仍是原来那些字段,
 *    `toggle()` / `addImage()` / `buildBody()` / `validate` 相关的原有函数**逐字未改**。
 *    组件自带主题(走 `App.vue` 的 `ConfigProvider`)与过渡动效。
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

/** 状态的选项 —— 只服务于这一个下拉,故就近定义在模板里(不改 `<script>` 的常量面)。 */
const STATUS_OPTIONS = [
  { value: 'on_sale', label: '上架' },
  { value: 'off_sale', label: '下架' },
]

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

    <Skeleton v-if="loading" active :paragraph="{ rows: 8 }" />

    <form v-else class="form" @submit.prevent="submit">
      <!-- 标量字段:DOM 顺序不变(制表顺序跟着走),只是两列排开 -->
      <div class="grid">
        <label class="field">
          <span class="field__label">产品编号 <em>*</em></span>
          <Input v-model:value="form.sku" placeholder="如 TL-2043" />
          <small>业务主键,唯一</small>
        </label>

        <label class="field">
          <span class="field__label">名称 <em>*</em></span>
          <Input v-model:value="form.name" placeholder="如 雅灰石纹" />
        </label>

        <label class="field">
          <span class="field__label">单价(元/㎡) <em>*</em></span>
          <InputNumber
            :value="form.pricePerSqm"
            :min="0"
            :max="9999"
            :step="0.01"
            class="num-input"
            @update:value="(v) => (form.pricePerSqm = Number(v ?? 0))"
          />
          <small>元/㎡ —— 报价引擎按此乘面积与损耗率;上限 9999(防手滑多打几个零)</small>
        </label>

        <label class="field">
          <span class="field__label">规格</span>
          <Input v-model:value="form.spec" placeholder="如 750×1500mm" />
        </label>

        <label class="field">
          <span class="field__label">材质</span>
          <Input v-model:value="form.material" placeholder="如 釉面砖" />
        </label>

        <label class="field">
          <span class="field__label">光泽度</span>
          <Input v-model:value="form.surface" placeholder="如 柔光" />
        </label>

        <label class="field">
          <span class="field__label">花色 / 纹理</span>
          <Input v-model:value="form.colorPattern" placeholder="如 浅灰细石纹" />
        </label>

        <label class="field">
          <span class="field__label">状态</span>
          <Select v-model:value="form.status" :options="STATUS_OPTIONS" />
        </label>
      </div>

      <fieldset>
        <legend>适用空间</legend>
        <p v-if="!spaces.length" class="hint">字典未配置空间项</p>
        <div class="checks">
          <Checkbox
            v-for="item in spaces"
            :key="item.key"
            :checked="(form.applicableSpaces ?? []).includes(item.key)"
            @change="toggle(form.applicableSpaces, item.key)"
          >
            {{ item.label }}
          </Checkbox>
        </div>
      </fieldset>

      <fieldset>
        <legend>风格标签</legend>
        <p v-if="!styles.length" class="hint">字典未配置风格项</p>
        <div class="checks">
          <Checkbox
            v-for="item in styles"
            :key="item.key"
            :checked="(form.styleTags ?? []).includes(item.key)"
            @change="toggle(form.styleTags, item.key)"
          >
            {{ item.label }}
          </Checkbox>
        </div>
      </fieldset>

      <fieldset>
        <legend>图片</legend>
        <label class="field field--full">
          <span class="field__label">封面图 URL</span>
          <Input v-model:value="form.coverImage" placeholder="https://oss/..." />
          <small>图片一律存对象存储,库里只存 URL</small>
        </label>

        <p class="sub-legend">多角度图</p>
        <div v-for="(image, index) in form.images ?? []" :key="index" class="image-row">
          <Input v-model:value="image.url" placeholder="图片 URL" />
          <Input v-model:value="image.angle" placeholder="角度,如 side" class="narrow" />
          <InputNumber
            :value="image.sort"
            class="narrow"
            @update:value="
              (v) => (image.sort = v === null || v === undefined || v === '' ? undefined : Number(v))
            "
          />
          <Button danger @click="removeImage(index)">移除</Button>
        </div>
        <Button class="add-image" @click="addImage">添加一张</Button>
      </fieldset>

      <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
      <ul v-if="fieldErrors.length" class="error errors">
        <li v-for="item in fieldErrors" :key="item">{{ item }}</li>
      </ul>

      <div class="buttons">
        <Button type="primary" html-type="submit" :loading="saving">保存</Button>
        <Button @click="router.back()">取消</Button>
      </div>
    </form>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ 本页**没有测试用例** —— 改动无测试可证,只能等人眼验收(纪律 7)。
   ⚠️ **2026-10-03 起控件交给 antd**:输入框 / 数字框 / 下拉 / 复选 / 按钮的外观与
   动效由 antd 与 `ConfigProvider` 主题负责 —— **本文件不再给它们写样式**
   (写了就会和 antd 打架)。这里只剩**排版**:栅格、标签、分组、间距。
   见 `openapi-verification.md` §68。 */

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

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.field--full {
  max-width: none;
  margin-bottom: var(--space-3);
}

.field__label {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

/* 必填星号 —— 红色 + 星号,**不靠颜色单独承载信息** */
em {
  color: var(--color-danger);
  font-style: normal;
}

small {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

/* 让 antd 的输入框铺满格子 —— 它们自身宽度由 antd 定,这里只给"占满" */
.grid :deep(.ant-input),
.grid :deep(.ant-input-number),
.grid :deep(.ant-select) {
  width: 100%;
}

.narrow {
  width: 6rem;
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

/* ── 多角度图行 ─────────────────────────────────────────────────────────── */
.image-row {
  display: flex;
  gap: var(--space-2);
  width: 100%;
  align-items: center;
}

.image-row :deep(.ant-input) {
  flex: 1;
}

.add-image {
  align-self: flex-start;
}

/* ── 按钮行 ─────────────────────────────────────────────────────────────── */
.buttons {
  display: flex;
  gap: var(--space-2);
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */

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
