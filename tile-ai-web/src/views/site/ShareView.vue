<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getSharedPlan } from '@/api/c/plans'
import { listDictsOfType } from '@/api/dicts'
import type { PlanDetail, PlanImage } from '@/api/types'

/**
 * 公开只读分享页(F-1.10)。**不需要登录。**
 *
 * 拿到链接的人(家人 / 设计师 / 门店店员)打开的就是这一页。
 * 他们是**没有账号**的,所以这一页:
 *
 *   - **不做任何管理动作** —— 没有改名、没有另存为、没有删除,也**没有重算**
 *     (重算要登录,而这里根本没有"谁"在操作);
 *   - **不 import 登录态**(`useUserAuthStore`)、不挂 `requiresAuth`;
 *   - 路由是**顶层**的(`/share/:token`),不套 `SiteLayout` —— 否则会给他们看
 *     一排点了就跳登录页的导航。
 *
 * ⚠️ **它是后端拼出来的那条链接的落点**:`PlanShareService.SHARE_PATH` = `/share/`,
 * 最终链接 = `tile.share.base-url + "/share/" + token`。改路由路径 = 后端发出去的
 * 链接打不开(而且会静默落到兜底重定向回首页,看不出是为什么)。
 *
 * ⚠️ **展示代码与 `PlanDetailView` 有重复**(部位表、报价表、视角名、字典降级)。
 * 这是**刻意的**,不是疏忽 —— 项目里没有 `src/components/`,而且这两页本来就该
 * 长得不一样(公开页有"只读"说明、没有管理区、错误态也不同)。为一个共用块
 * 新建一层抽象,代价比这几十行重复大(同一判断见 `GenerateView` 里内联的 `viewLabel`)。
 *
 * ⚠️ 三条"改错了不报错"的边界与详情页同源,别在这里放松:
 *   1. `materialFee` 可能**整个字段不在** → 一律 `?? '—'`,**不是 `?? 0`**;
 *   2. `quote` / `totalAmount` 缺失是**正常状态**(生成时没算出价),不是错误 ——
 *      绝不能拼出 `¥NaN`;
 *   3. 取不到一律按后端那句 `30001` 文案显示,**不猜**是"失效了"还是"打错了"。
 */

const route = useRoute()

/** 路径里的分享令牌。空/缺失时**不发请求** —— 那必然是一次无意义的 30001。 */
const token = computed<string>(() => {
  const raw = route.params.token
  const value = Array.isArray(raw) ? raw[0] : raw
  return typeof value === 'string' ? value : ''
})

const plan = ref<PlanDetail | null>(null)
const loading = ref(false)
const errorText = ref('')

const spaceLabels = ref<Map<string, string>>(new Map())
const styleLabels = ref<Map<string, string>>(new Map())

/** 部位中文名 —— 与其它 C 端页面一样各自定义一份(项目无公共展示常量模块)。 */
const PART_LABELS: Record<string, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部',
}

// ---------------------------------------------------------------- 加载

async function load() {
  if (!token.value) {
    errorText.value = '这个分享链接不完整 —— 地址里没有令牌。'
    plan.value = null
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    plan.value = await getSharedPlan(token.value)
  } catch (error) {
    // 30001 的四种情形后端**故意不区分**(令牌不存在 / 已废除 / 方案已删除,
    // 见 §43.4)—— 那是防枚举令牌的探针。所以这里也**不猜**,原样显示后端文案,
    // 只补一句"该找谁",因为看这一页的人多半不是方案的owner。
    errorText.value = error instanceof Error ? error.message : '打不开这个分享链接'
    plan.value = null
  } finally {
    loading.value = false
  }
}

// 令牌变 = 换了另一条分享链接。组件实例会被复用(同一个路由),
// onMounted **不会**再跑 —— 所以必须监听令牌本身。
watch(token, () => {
  void load()
})

onMounted(async () => {
  try {
    const [spaces, styles] = await Promise.all([
      listDictsOfType('space'),
      listDictsOfType('style'),
    ])
    spaceLabels.value = new Map(spaces.map((item) => [item.key, item.label]))
    styleLabels.value = new Map(styles.map((item) => [item.key, item.label]))
  } catch {
    // 字典拿不到就退化成显示 key —— 这一页是给人看方案的,
    // 不能因为一个附属接口挂了就白屏
  }
  await load()
})

// ---------------------------------------------------------------- 展示

function labelOf(labels: Map<string, string>, key?: string): string {
  if (!key) return ''
  return labels.get(key) ?? key
}

function partLabel(part: string): string {
  return PART_LABELS[part] ?? part
}

/** 数字格:缺失显示 `—`。**不是 0** —— 见文件头第 1 条。 */
function num(value: number | undefined | null): string {
  return value === undefined || value === null ? '—' : String(value)
}

const cover = computed<PlanImage | null>(() => {
  const list = plan.value?.images ?? []
  if (!list.length) return null
  return list.find((image) => image.isCover) ?? list[0]
})

/** 全部效果图。**不排序也不筛选** —— 顺序由服务端给(封面在前,见 §8.4)。 */
const gallery = computed<PlanImage[]>(() => plan.value?.images ?? [])

/** 用户点开看的那一张。`null` = 没点过 ⇒ 显示封面(契约没规定 images 的顺序)。 */
const selected = ref<PlanImage | null>(null)

/**
 * 主图位置显示哪一张:**点过的那张(且它还在这一份结果里)**,否则封面。
 *
 * ⚠️ 那个"还在结果里"的判断不是多余的 —— 换一条分享链接时组件实例会被复用,
 * `selected` 里存的还是**上一份**的图。少了它,主图会显示别的方案的图,而且**不报错**。
 */
const active = computed<PlanImage | null>(() => {
  const chosen = selected.value
  if (chosen && gallery.value.some((image) => image.url === chosen.url)) {
    return chosen
  }
  return cover.value
})

/** 视角名 → 中文标签。**按 view 区分,不靠数组下标猜**(与 GenerateView 同口径)。 */
function viewLabel(view?: string): string {
  if (!view || view === 'default') return '正面'
  const matched = /^angle(\d+)$/.exec(view)
  if (matched) return `角度 ${matched[1]}`
  return view
}

const spaces = computed(() => plan.value?.layout?.spaces ?? [])
const parts = computed(() => plan.value?.parts ?? [])
const quote = computed(() => plan.value?.quote ?? null)
const metaText = computed(() =>
  [
    labelOf(spaceLabels.value, plan.value?.space),
    labelOf(styleLabels.value, plan.value?.style),
  ]
    .filter(Boolean)
    .join(' · '),
)
</script>

<template>
  <main class="share">
    <p v-if="loading" class="hint">加载中…</p>

    <template v-else-if="plan">
      <p class="banner">
        这是一份<strong>方案分享</strong>的只读页面 —— 可以查看,不能修改。
        链接可随时被分享者废除。
      </p>

      <header class="head">
        <h1>{{ plan.name }}</h1>
        <span v-if="metaText" class="meta">{{ metaText }}</span>
      </header>

      <!-- ---------------------------- 效果图 ---------------------------- -->
      <section class="block">
        <h2>效果图</h2>
        <template v-if="active">
          <div class="main">
            <img :src="active.url" :alt="viewLabel(active.view)" />
            <p class="caption">{{ viewLabel(active.view) }}</p>
          </div>
          <!-- 多角度图:**点一下就换到主图位置**(与商品详情页同款交互)。
               分享页是给别人看的,更不该出现"点了没反应的小图"。 -->
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
        <p v-else class="hint">这份方案没有效果图。</p>
      </section>

      <!-- ---------------------------- 户型与部位 ---------------------------- -->
      <section class="block">
        <h2>户型与部位</h2>

        <ul v-if="spaces.length" class="spaces">
          <li v-for="(space, index) in spaces" :key="index">
            <strong>{{ space.name || labelOf(spaceLabels, space.type) }}</strong>
            {{ space.length }} × {{ space.width }}m · 层高 {{ space.height }}m
          </li>
        </ul>
        <p v-else class="hint">没有户型快照。</p>

        <table v-if="parts.length" class="part-table">
          <thead>
            <tr>
              <th>部位</th>
              <th>瓷砖</th>
              <th class="num">有效面积(㎡)</th>
              <th class="num">单价(元/㎡)</th>
              <th class="num">材料费(元)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in parts" :key="index">
              <td>
                {{ partLabel(row.part) }}
                <span v-if="row.source === 'ai'" class="tag">AI 补齐</span>
              </td>
              <td>{{ row.productName || row.sku }}</td>
              <td class="num">{{ num(row.area) }}</td>
              <td class="num">{{ num(row.pricePerSqm) }}</td>
              <td class="num">{{ num(row.materialFee) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="hint">这份方案没有部位记录。</p>
      </section>

      <!-- ---------------------------- 报价 ---------------------------- -->
      <section class="block">
        <h2>报价</h2>

        <!-- 看这一页的人改不了任何东西,所以这里**不给重算按钮** ——
             与详情页同一句话,只是去掉那个他按不了的入口。 -->
        <p v-if="plan.quoteExpired" class="stale">
          产品调过价了,下面这个金额是生成当时的价,可能已经不是最新。
        </p>

        <template v-if="quote">
          <table class="quote-table">
            <thead>
              <tr>
                <th>部位</th>
                <th>瓷砖</th>
                <th class="num">毛面积(㎡)</th>
                <th class="num">有效面积(㎡)</th>
                <th class="num">单价(元/㎡)</th>
                <th class="num">材料费(元)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in quote.items" :key="index">
                <td>{{ partLabel(item.part) }}</td>
                <td>{{ item.sku }}</td>
                <td class="num">{{ num(item.grossArea) }}</td>
                <td class="num">{{ num(item.effectiveArea) }}</td>
                <td class="num">{{ num(item.pricePerSqm) }}</td>
                <td class="num">{{ num(item.materialFee) }}</td>
              </tr>
            </tbody>
          </table>

          <dl class="totals">
            <dt>材料费合计</dt>
            <dd>¥{{ quote.materialTotal }}</dd>
            <dt>辅料费合计</dt>
            <dd>¥{{ quote.auxTotal }}</dd>
            <dt class="grand">总计</dt>
            <dd class="grand">¥{{ quote.total }}</dd>
          </dl>
        </template>

        <p v-else class="hint">这份方案没有报价 —— 生成时没能算出价格。</p>
      </section>
    </template>

    <template v-else>
      <h1>打不开这个分享链接</h1>
      <p class="error" role="alert">{{ errorText }}</p>
      <p class="hint">
        可能链接被分享者废除了,或者地址没复制全。麻烦找分享给你的人要一条新的。
      </p>
    </template>
  </main>
</template>

<style scoped>
.share {
  max-width: 60rem;
  margin: 0 auto;
  padding: 1.5rem 1rem 3rem;
}

.banner {
  margin: 0 0 1rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.35rem;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.85rem;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
}

h1 {
  margin: 0;
  font-size: 1.35rem;
}

h2 {
  margin: 0 0 0.6rem;
  font-size: 1.05rem;
}

.meta {
  color: #6b7280;
  font-size: 0.85rem;
}

.block {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
}

.main img {
  max-width: 100%;
  border-radius: 0.5rem;
}

.caption {
  margin: 0.3rem 0 0;
  color: #6b7280;
  font-size: 0.8rem;
}

.thumbs {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-top: 0.75rem;
}

/* 缩略图是 button(可点)—— 盖掉全局 button 的边框/背景,只留图与说明 */
.thumb {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  width: 8rem;
  padding: 0.2rem;
  border: 2px solid transparent;
  border-radius: 0.4rem;
  background: none;
  cursor: pointer;
}

.thumb img {
  width: 100%;
  height: 6rem;
  object-fit: cover;
  border-radius: 0.25rem;
}

/* 当前正在主图位置展示的那张 */
.thumb--active {
  border-color: #1d4ed8;
}

.thumb--active .thumb-cap {
  color: #1d4ed8;
}

.thumb-cap {
  color: #6b7280;
  font-size: 0.75rem;
  text-align: center;
}

.spaces {
  margin: 0 0 1rem;
  padding-left: 1.2rem;
  color: #374151;
  font-size: 0.9rem;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

th,
td {
  padding: 0.45rem 0.5rem;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}

.num {
  text-align: right;
}

.tag {
  margin-left: 0.3rem;
  padding: 0.05rem 0.3rem;
  border-radius: 0.25rem;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.72rem;
}

.stale {
  padding: 0.5rem 0.75rem;
  border-radius: 0.35rem;
  background: #fef3c7;
  color: #92400e;
  font-size: 0.85rem;
}

.totals {
  display: grid;
  grid-template-columns: auto auto;
  gap: 0.3rem 1rem;
  justify-content: end;
  margin: 1rem 0 0;
  font-size: 0.9rem;
}

.totals dt {
  color: #6b7280;
}

.totals dd {
  margin: 0;
  text-align: right;
}

.totals .grand {
  color: #b45309;
  font-weight: 600;
}

.error {
  color: #b91c1c;
}

.hint {
  color: #6b7280;
  font-size: 0.9rem;
}
</style>
