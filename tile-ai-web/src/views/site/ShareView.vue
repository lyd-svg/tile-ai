<script setup lang="ts">
import { Alert, Skeleton } from 'ant-design-vue'
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
    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

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
      <Alert class="alert-gap" type="error" :message="errorText" show-icon />
      <p class="hint">
        可能链接被分享者废除了,或者地址没复制全。麻烦找分享给你的人要一条新的。
      </p>
    </template>
  </main>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   它的测试有逐 class 断言,还有一条硬规矩:**页面上不许出现
   「改名 / 另存为 / 删除 / 重算 / 保存」**这五个词(公开页没有任何管理动作)。
   ⚠️ 测试依赖的选择器:`.banner` `.meta` `.spaces` `.main img` `.main .caption`
   `.thumbs .thumb`(含 `--active`)`.thumb-cap` `.part-table` `.quote-table`
   `.totals` `.stale` `[role="alert"]`。 */

/* 分享页是**陌生人看到的一页** —— 视作门面:居中、留白大、不堆控件 */
.share {
  max-width: 60rem;
  margin: 0 auto;
  padding: var(--space-6) var(--space-4) var(--space-8);
}

.banner {
  margin: 0 0 var(--space-5);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
}

.banner strong {
  color: var(--color-text);
}

.head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

h1 {
  margin: 0;
  font-size: var(--text-2xl);
  line-height: var(--leading-2xl);
  font-weight: 700;
  color: var(--color-text);
}

h2 {
  margin: 0 0 var(--space-3);
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: 600;
  color: var(--color-text);
}

.meta {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

/* 每一块内容做成一张卡 */
.block {
  margin-top: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

/* ── 效果图 ─────────────────────────────────────────────────────────────── */
.main img {
  max-width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.caption {
  margin: var(--space-2) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

/* 缩略图是 button(可点)—— 盖掉全局 button 的边框/背景,只留图与说明 */
.thumb {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  width: 8rem;
  padding: var(--space-1);
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  background: none;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard);
}

.thumb:hover {
  border-color: var(--color-border-strong);
}

.thumb img {
  width: 100%;
  height: 6rem;
  object-fit: cover;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

/* 当前正在主图位置展示的那张 */
.thumb--active {
  border-color: var(--color-primary);
}

.thumb--active .thumb-cap {
  color: var(--color-primary);
  font-weight: 600;
}

.thumb-cap {
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-secondary);
  text-align: center;
}

/* ── 户型与部位 ─────────────────────────────────────────────────────────── */
.spaces {
  margin: 0 0 var(--space-4);
  padding-left: var(--space-5);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text-secondary);
}

.spaces strong {
  color: var(--color-text);
}

/* ── 表格(部位与报价)──────────────────────────────────────────────────── */
table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}

th,
td {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--color-border);
  text-align: left;
}

th {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
}

/* 数字列一律右对齐 —— 金额与面积要能竖着比大小 */
.num {
  text-align: right;
}

.tag {
  margin-left: var(--space-2);
  padding: 0.05rem var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: var(--text-xs);
}

.stale {
  margin: 0 0 var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-warning);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-text);
  font-size: var(--text-sm);
}

.totals {
  display: grid;
  grid-template-columns: auto auto;
  gap: var(--space-1) var(--space-5);
  justify-content: end;
  margin: var(--space-4) 0 0;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}

.totals dt {
  color: var(--color-text-secondary);
}

.totals dd {
  margin: 0;
  text-align: right;
  color: var(--color-text);
}

/* 总计是这一页最重要的数字 —— 放大 + 主色 */
.totals .grand {
  padding-top: var(--space-2);
  border-top: 1px solid var(--color-border);
  color: var(--color-primary);
  font-size: var(--text-lg);
  font-weight: 700;
}

/* ── 状态 ───────────────────────────────────────────────────────────────── */
/* 打不开分享链接时的错误提示已迁 antd `Alert`(§71.3),间距走全局 `.alert-gap` */

.hint {
  margin: var(--space-3) 0;
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text-secondary);
}

.block > .hint:last-child {
  padding: var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-surface-sunken);
  text-align: center;
}
</style>
