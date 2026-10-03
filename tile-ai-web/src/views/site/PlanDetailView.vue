<script setup lang="ts">
import { Alert, Button, Skeleton } from 'ant-design-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  copyPlan,
  deletePlan,
  getPlan,
  recalcPlan,
  renamePlan,
  revokeShare,
  sharePlan,
} from '@/api/c/plans'
import { listDictsOfType } from '@/api/dicts'
import { validationErrorsOf } from '@/api/errors'
import type { ErrorItem } from '@/api/errors'
import type { PlanDetail, PlanImage, QuoteResult } from '@/api/types'
import { appConfirm } from '@/components/base/AppConfirm'

/**
 * 方案详情(F-1.9 + F-1.8)。需登录。
 *
 * 这是本轮**唯一的管理入口** —— 改名 / 另存为 / 删除 / 按当前价重算四个动作
 * 全在这一页。列表页只读,免得同一个动作有两份实现、各自漂。
 *
 * ⚠️ **三处必须守住的诚实边界**(都是"改错了不报错,只悄悄出错"):
 *
 *   1. `PlanPart.materialFee` **契约标必填、实现会整个省略**。后端 `PlanPartView`
 *      带 `@JsonInclude(NON_NULL)` —— 报价算不出来时 JSON 里根本没有这个键。
 *      所以每个数字格都要 `?? '—'`,**不能 `?? 0`**(那会显示成 ¥0,看着像真的)。
 *   2. `quote` / `totalAmount` 可能整个没有 —— 多空间方案会走"这一次不算价"的分支
 *      (§41 / §42.5)。**没有报价是一种正常状态,不是错误**,要给出可读的空态,
 *      而且**重算按钮仍然可用**:它存在的意义就是把"当时算不出"变成"现在能算"。
 *   3. 改名有 200 字的长度上限,但**契约里没声明**(`openapi-c.yaml` 的 `name` 只有
 *      `type: string`),上限只在后端 `PlanNames.NAME_MAX` 里按**码点**判,超了报 10001。
 *      所以这里按码点先挡一道(`[...name].length`,不是 `name.length` ——
 *      后者把 100 个 emoji 数成 200 而误判),服务端的 10001 也照常渲染出来。
 */
const route = useRoute()
const router = useRouter()

/**
 * 路径里的方案 id。
 *
 * ⚠️ `Number.isInteger` 必须在**发请求之前**判掉:直接把 `/plans/abc` 的参数拼进
 * URL 会打出一个必然失败的请求,而用户看到的是一句莫名其妙的加载失败。
 */
const planId = computed<number | null>(() => {
  const raw = route.params.id
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const plan = ref<PlanDetail | null>(null)
/** 改名输入框的内容。加载详情后回填当前名字。 */
const nameDraft = ref('')
const loading = ref(false)
const errorText = ref('')
const notice = ref('')
const fieldErrors = ref<ErrorItem[]>([])

/**
 * 服务端刚回的**完整分享链接**(权威值)。
 *
 * 只有「刚点过生成分享」时才拿得到 —— 刷新页面后 `PlanDetail` 里**只有令牌**,
 * 没有链接(链接是 `POST /plans/{id}/share` 现拼的)。所以它是个独立状态,
 * 不是从 `plan` 派生的,用法见下面的 `shareLink`。
 */
const shareUrl = ref('')

const spaceLabels = ref<Map<string, string>>(new Map())
const styleLabels = ref<Map<string, string>>(new Map())

/** 部位中文名 —— 与其它 C 端页面一样各自定义一份(项目无公共展示常量模块)。 */
const PART_LABELS: Record<string, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部',
}

/** `t_plan.name` 的上限,**按码点**。与后端 `PlanNames.NAME_MAX` 同值。 */
const NAME_MAX = 200

// ---------------------------------------------------------------- 加载

function clearMessages() {
  errorText.value = ''
  notice.value = ''
  fieldErrors.value = []
}

async function load() {
  if (planId.value === null) {
    errorText.value = '方案编号不正确'
    plan.value = null
    return
  }
  loading.value = true
  clearMessages()
  try {
    plan.value = await getPlan(planId.value)
    nameDraft.value = plan.value?.name ?? ''
    // 换了一份方案 —— 上一条服务端拼的链接不能再留着(见 shareLink)
    shareUrl.value = ''
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    plan.value = null
  } finally {
    loading.value = false
  }
}

// 详情页之间互相跳(另存为之后跳去新方案)时路由参数变、组件实例被复用,
// onMounted **不会**再跑一次 —— 所以必须监听参数本身,否则会一直显示旧方案。
watch(planId, () => {
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
    // 拿不到字典就退化成显示 key,不影响这一页的任何操作
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

/**
 * 用户点开看的那一张。`null` = 没点过 ⇒ 显示封面。
 *
 * 为什么不直接用一个下标:契约**没规定** `images` 的顺序(只说第 1 张
 * `view=default` 且 `isCover=true`)。用"点过就用点的、没点过就用封面"这种写法,
 * 顺序变了也不会让默认显示的那张跟着变。
 */
const selected = ref<PlanImage | null>(null)

/**
 * 主图位置显示哪一张:**点过的那张(且它还在这一份结果里)**,否则封面。
 *
 * ⚠️ 那个"还在结果里"的判断不是多余的 —— 换一份方案、或换个分享令牌时,
 * 组件实例会被复用,而 `selected` 里存的还是**上一份**的图。
 * 少了这个判断,主图会显示别的方案的图,而且**不报错**。
 * 用自愈式判据而不是"在加载处记得重置":后者要求每个改动 `images` 的地方都不能忘。
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

// ---------------------------------------------------------------- 改名

const savingName = ref(false)

/** 保存按钮可不可点:空的不发,超长的也不发。 */
const canSaveName = computed(
  () => !savingName.value && nameDraft.value.trim() !== '' && plan.value !== null,
)

async function saveName() {
  if (planId.value === null) return
  clearMessages()

  const next = nameDraft.value.trim()
  if (!next) {
    errorText.value = '方案名不能为空'
    return
  }
  // 按**码点**数长度 —— `next.length` 数的是 UTF-16 单元,会把 100 个 emoji
  // 数成 200 而误判超长(后端按码点,两处必须用同一把尺子)。
  if ([...next].length > NAME_MAX) {
    errorText.value = `方案名最多 ${NAME_MAX} 个字`
    return
  }

  savingName.value = true
  try {
    await renamePlan(planId.value, next)
    if (plan.value) plan.value = { ...plan.value, name: next }
    // 输入框回填**去掉首尾空白后的**那个名字 —— 否则用户看到的还是自己敲的原样,
    // 而列表/详情里已经是修剪过的版本
    nameDraft.value = next
    notice.value = '已改名'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '改名失败'
    // 10001 会把逐字段原因放在 data.errors[] —— 原样渲染,不要吞掉
    fieldErrors.value = validationErrorsOf(error)
  } finally {
    savingName.value = false
  }
}

// ---------------------------------------------------------------- 重算

const recalculating = ref(false)

async function recalc() {
  if (planId.value === null) return
  clearMessages()

  recalculating.value = true
  try {
    const result: QuoteResult = await recalcPlan(planId.value)
    // 契约写明 recalc 的 `data` **就是**新的 QuoteResult,所以就地刷新即可 ——
    // 再 getPlan 一轮是白打的请求,而且中间那一拍会闪一下旧金额。
    if (plan.value) {
      plan.value = {
        ...plan.value,
        quote: result,
        // 后端同一次 UPDATE 里也把 total_amount 覆盖成了新总额、quote_expired 置回 false
        // (PlanRecalcService),前端照做,免得页面上两个数打架。
        totalAmount: result.total,
        quoteExpired: false,
      }
    }
    notice.value = '已按当前价格重算'
  } catch (error) {
    // 重算失败一律 90001「方案在,坏的是系统」(§41.4)—— 原样透出后端文案,
    // 不要自己编一句,后端那句才说得清是哪一步算不出来。
    errorText.value = error instanceof Error ? error.message : '重算失败'
  } finally {
    recalculating.value = false
  }
}

// ---------------------------------------------------------------- 另存为 / 删除

const busy = ref(false)

async function copyAsNew() {
  if (planId.value === null) return
  clearMessages()

  busy.value = true
  try {
    const created = await copyPlan(planId.value)
    // 跳到新方案 —— 契约只回 `{ planId }`,新名字由服务端定(形如「原名(副本)」),
    // 拉一遍详情自然就有了。
    await router.push({ name: 'plan-detail', params: { id: created.planId } })
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '另存为失败'
  } finally {
    busy.value = false
  }
}

async function remove() {
  if (planId.value === null) return
  // 二次确认在前端 —— 契约把这句话写在 DELETE 的 description 里(PRD §5.6)
  if (
    !(await appConfirm({
      title: '确认删除方案',
      content: `确认删除「${plan.value?.name ?? ''}」?该操作不可撤销。`,
      okText: '删除',
      danger: true,
    }))
  ) {
    return
  }

  clearMessages()
  busy.value = true
  try {
    await deletePlan(planId.value)
    await router.push({ name: 'plans' })
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '删除失败'
  } finally {
    busy.value = false
  }
}

// ---------------------------------------------------------------- 分享(F-1.10)

const sharing = ref(false)
const revoking = ref(false)

/** 当前方案已有的分享令牌;没分享过就是空串。**它就是这个方案的全部凭据**。 */
const shareToken = computed(() => plan.value?.shareToken ?? '')

/**
 * 给用户复制的那条链接。
 *
 * 服务端给过 `shareUrl` 就用它;只有令牌时(刷新页面后、或之前就分享过)
 * 按 `当前站点源 + /share/ + 令牌` 现拼 —— 同源部署下这与后端配的
 * `tile.share.base-url` 是同一个地址。
 *
 * ⚠️ **能用服务端那条就用服务端的。** 后端那个配置默认是
 * `http://localhost:5173`,**上线前必须改**(§43.12-7):它是个**发给别人**的
 * 地址,配错了分享者自己在浏览器里还试不出来,只会收到一句"你这链接打不开"。
 */
const shareLink = computed(() => {
  if (!shareToken.value) return ''
  if (shareUrl.value) return shareUrl.value
  return `${window.location.origin}/share/${shareToken.value}`
})

/** 生成分享链接。**幂等** —— 已经分享过,后端回的是同一条链接。 */
async function createShare() {
  if (planId.value === null) return
  clearMessages()

  sharing.value = true
  try {
    const result = await sharePlan(planId.value)
    // 契约里 ShareResult 三个字段**一个都不 required**,实现里 qrCodeUrl 恒不出现
    // —— 所以逐个判,不假设它们在(§43.2 登记的那处不一致面)。
    if (plan.value && result.shareToken) {
      plan.value = { ...plan.value, shareToken: result.shareToken }
    }
    shareUrl.value = result.shareUrl ?? ''
    notice.value = '分享链接已生成 —— 拿到链接的人不用登录就能看'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '生成分享链接失败'
  } finally {
    sharing.value = false
  }
}

async function copyShareLink() {
  clearMessages()
  const text = shareLink.value
  if (!text) return

  try {
    // 剪贴板在非 https / 权限被拒 / 老浏览器里会直接不可用 —— 不抛给用户,
    // 退化成"链接就在上面的框里,自己选中复制"。
    await navigator.clipboard.writeText(text)
    notice.value = '链接已复制'
  } catch {
    errorText.value = '复制失败,请手动选中上面的链接复制'
  }
}

/** 废除分享链接 —— 旧链接**立刻**打不开(这是唯一的失效手段,§43.9)。 */
async function revokeShareLink() {
  if (planId.value === null) return
  // 二次确认在前端:这个动作影响的是**已经在别人手里**的那条链接,不可撤销
  // ⚠️ 这一句要**说清后果**:这个动作影响的是**已经在别人手里**的那条链接,
  // 而且不可撤销 —— 所以正文里点明"立刻打不开",别让它看起来像个普通开关。
  if (
    !(await appConfirm({
      title: '确认废除分享链接',
      content: '已经在别人手里的那条链接会立刻打不开,且不可恢复。',
      okText: '废除',
      danger: true,
    }))
  ) {
    return
  }

  clearMessages()
  revoking.value = true
  try {
    await revokeShare(planId.value)
    // 后端把 share_token 置空了,本地跟着清 —— 否则页面还显示一条已经作废的链接
    if (plan.value) plan.value = { ...plan.value, shareToken: undefined }
    shareUrl.value = ''
    notice.value = '分享链接已废除'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '废除失败'
  } finally {
    revoking.value = false
  }
}
</script>

<template>
  <section>
    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />

    <Alert v-else-if="errorText && !plan" class="alert-gap" type="error" :message="errorText" show-icon />

    <template v-else-if="plan">
      <header class="head">
        <h1>{{ plan.name }}</h1>
        <span v-if="metaText" class="meta">{{ metaText }}</span>
      </header>

      <Alert v-if="errorText" class="alert-gap" type="error" :message="errorText" show-icon />
      <ul v-if="fieldErrors.length" class="errors">
        <li v-for="item in fieldErrors" :key="item.field">{{ item.field }}:{{ item.reason }}</li>
      </ul>
      <Alert v-if="notice" class="alert-gap" type="success" :message="notice" show-icon />

      <!-- ---------------------------- 效果图 ---------------------------- -->
      <section class="block">
        <h2>效果图</h2>
        <template v-if="active">
          <div class="main">
            <img :src="active.url" :alt="viewLabel(active.view)" />
            <p class="caption">{{ viewLabel(active.view) }}</p>
          </div>
          <!-- 多角度图:**点一下就换到主图位置**显示大图(与商品详情页同款交互)。
               缩略图把**全部**图都列出来(含当前这张),这样点回封面也有路。 -->
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

      <!-- ---------------------------- 报价(F-1.8) ---------------------------- -->
      <section class="block">
        <header class="quote-head">
          <h2>报价</h2>
          <Button :loading="recalculating" @click="recalc">按当前价重算</Button>
        </header>

        <p v-if="plan.quoteExpired" class="stale">
          产品调过价了,下面这个金额是生成当时的价 —— 点「按当前价重算」可以刷新。
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

        <p v-else class="hint">
          这份方案没有报价 —— 生成时没能算出价格(面积未定、产品下架或报价参数未配)。
          可以点上面的「按当前价重算」再试一次。
        </p>
      </section>

      <!-- ---------------------------- 分享(F-1.10) ---------------------------- -->
      <section class="block">
        <h2>分享</h2>

        <template v-if="shareToken">
          <p class="hint">
            拿到这条链接的人<strong>不需要登录</strong>就能看到这份方案(只读,不能改)。
            链接一直有效,除非你点「废除链接」。
          </p>
          <div class="share-row">
            <!-- readonly 而不是 disabled:disabled 的输入框选不中文本,
                 用户想手动复制时就抓瞎了 -->
            <input class="share-link" type="text" readonly :value="shareLink" />
            <Button @click="copyShareLink">复制链接</Button>
            <Button danger :loading="revoking" @click="revokeShareLink">废除链接</Button>
          </div>
        </template>

        <template v-else>
          <p class="hint">把这份方案发给家人或设计师 —— 他们不用注册就能打开看。</p>
          <Button type="primary" :loading="sharing" @click="createShare">生成分享链接</Button>
        </template>
      </section>

      <!-- ---------------------------- 管理 ---------------------------- -->
      <section class="block">
        <h2>管理</h2>

        <form class="rename" @submit.prevent="saveName">
          <!-- 这里**故意不加 HTML 的 `maxlength`** —— 它数的是 UTF-16 单元,
               而后端数的是码点。挂上它就会出现"浏览器拦住 100 个 emoji、
               后端却认为完全合法"这种两把尺子的事。长度只在下面的 JS 里判一次。 -->
          <label>
            方案名
            <input v-model="nameDraft" type="text" />
          </label>
          <Button type="primary" html-type="submit" :disabled="!canSaveName">保存</Button>
        </form>

        <div class="actions">
          <Button :disabled="busy" @click="copyAsNew">另存为</Button>
          <Button danger :disabled="busy" @click="remove">删除</Button>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)—— 本文件不出现任何十六进制。
   ⚠️ **本页 `<template>` 与 `<script>` 一个字节未动**,只换了这一块 ——
   因为它的测试有 60+ 条逐 class 断言(`.main` / `.thumbs` / `.thumb--active` /
   `.part-table` / `.quote-table` / `.totals` / `.stale` / `.share-link` /
   `.rename` …),动结构风险高于收益。 */

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

/* 每一块内容做成一张卡 —— 工具感:信息分开、边界清楚 */
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

/* ── 表格(报价与部位)─────────────────────────────────────────────────── */
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
  color: var(--color-text-secondary);
  font-weight: 600;
  font-size: var(--text-xs);
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

.quote-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.quote-head h2 {
  margin-bottom: 0;
}

.stale {
  margin: var(--space-3) 0 0;
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

/* 总计是这一页最重要的数字 —— 放大 + 主色,压住视线 */
.totals .grand {
  padding-top: var(--space-2);
  border-top: 1px solid var(--color-border);
  color: var(--color-primary);
  font-size: var(--text-lg);
  font-weight: 700;
}

/* ── 分享 ───────────────────────────────────────────────────────────────── */
.share-row {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-top: var(--space-3);
}

.share-link {
  flex: 1;
  min-width: 12rem;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface-sunken);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
}

/* ── 管理 ───────────────────────────────────────────────────────────────── */
.rename {
  display: flex;
  gap: var(--space-2);
  align-items: flex-end;
  margin-bottom: var(--space-4);
}

.rename label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.rename input {
  min-width: 18rem;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.rename input:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
  border-color: var(--color-primary);
}

.actions {
  display: flex;
  gap: var(--space-2);
}

/* ── 按钮 ───────────────────────────────────────────────────────────────── */
/* ⚠️ **本页 7 个常规按钮已全部迁 antd**(按当前价重算 / 复制链接 / 废除链接 /
   生成分享链接 / 保存 / 另存为 / 删除)—— 不再写样式(§70.9)。
   原来这里是**裸 `button {}`**,会照样命中 antd 的 `<button class="ant-btn">`。
   ⚠️ 但上面的 **`.thumb`(多角度缩略图)是自定义交互元素**,刻意保留、**不迁**。 */

/* ── 状态 ───────────────────────────────────────────────────────────────── */
.errors {
  margin: var(--space-3) 0 0;
  padding-left: var(--space-5);
  color: var(--color-danger);
  font-size: var(--text-sm);
}


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
