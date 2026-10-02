<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getChatSession, listChatSessions, streamChat } from '@/api/c/chat'
import type { ChatEvent } from '@/api/c/chat'
import { BusinessError, messageFor } from '@/api/errors'
import { useSelectionDraftStore } from '@/stores/selectionDraft'
import { renderRichText } from '@/utils/richText'
import type { ChatSession, SkuRef } from '@/api/types'

/**
 * AI 咨询(F-1.6;PRD §5.2.3、架构 §11.3)。
 *
 * 用大白话问瓷砖的事,后端**流式**给答案,末尾可能给几个可采纳的 SKU。
 *
 * ## 四件必须做对的事
 *
 * 1. **`error` 帧是正常终态,不是网络故障。** 比如问价会被 50001 拒答 ——
 *    那说明"服务好好地在回答你:这事我不答",**不该显示成"失去联系"、
 *    不该给重试按钮**。真正的网络中断是另一条路(见 `interruptError`)。
 *    契约里这两种结局是**互斥**的:失败帧之后不会再有 `[DONE]`。
 * 2. **离开页面 / 切会话必须中断在途的流**(`AbortController`),否则组件卸载后
 *    回调还在往一个已经不存在的列表里写。同 `GenerateView` 的"卸载即停表"。
 * 3. **服务端文案优先,不自己编。** 拒答的文案来自后端 `message`,前端照抄。
 * 4. **`name` 可能没有、也可能是乱码。** `SkuRef.name` 在契约里是**可选**的,
 *    而演示库里商品名还是**双重编码的乱码**(`openapi-verification.md` §47.4,
 *    数据问题)。所以卡片**始终把 `sku` 显示出来**当主标识,`name` 有就显示、
 *    没有也不留空白。前端**不去"修"乱码** —— 那会和产品库页显示的不一致,而且是猜。
 *
 * ⚠️ 另有一处已知缺口(登记在案,前端消不掉):`refs[].part` 是后端**按问句关键词
 *    猜的**(§46.10.3),猜错会把砖铺到错的部位,而采纳是**替换**语义、会静默覆盖。
 *    这里能做的是**把部位写在卡片上**、采纳后立刻更新摘要、**不自动跳走**,
 *    让用户有机会核对。
 */

const router = useRouter()
const draft = useSelectionDraftStore()

/** 部位中文名。项目没有公共常量模块,各页各留一份(惯例)。用短版,贴合紧凑列表。 */
const PART_LABELS: Record<string, string> = {
  floor: '地面',
  wall: '墙面',
  local: '局部',
}

/** 一次问答的生命周期。 */
type TurnState = 'idle' | 'sending' | 'streaming' | 'done' | 'rejected' | 'aborted' | 'interrupted'

interface ChatItem {
  id: number
  role: 'user' | 'assistant'
  content: string
  refs: SkuRef[]
  status: 'done' | 'streaming' | 'rejected' | 'aborted' | 'interrupted'
  /** 拒答 / 中断时的说明文字。拒答时**照抄后端 message** */
  errorText?: string
}

const messages = ref<ChatItem[]>([])
const sessionId = ref<string | null>(null)
const turn = ref<TurnState>('idle')
const draftText = ref('')
/** 真·连接类故障(与 `rejected` 分开显示 —— 见文件头第 1 条) */
const interruptError = ref('')

/** 全页同时只有一个在途请求。 */
let activeAbort: AbortController | null = null
/**
 * 代际标记:切会话 / 切页时 +1,让**在途**的回调认得出自己已经过期,
 * 免得它把新会话的消息覆盖掉。
 */
let requestSeq = 0
let idSeed = 0

const busy = computed(() => turn.value === 'sending' || turn.value === 'streaming')
const canSend = computed(() => !busy.value && draftText.value.trim().length > 0)

/** 已选部位的实时摘要 —— 采纳会**替换**同部位,让这件事看得见。 */
const selectedSummary = computed(() =>
  draft.parts
    .map((item) => `${PART_LABELS[item.part] ?? item.part} ${item.name?.trim() || item.sku}`)
    .join('、'),
)

/**
 * 最后一条是用户消息 = 这次提问**没有留下回答**。
 *
 * 这是真实存在的情形:后端在流中途失败时**不落 assistant 消息**(免得把半条答案
 * 沉淀成历史),所以库里就只剩用户那句。详情页如实渲染即可 ——
 * **不伪造助手气泡**,说明也只陈述"没有记录",**不猜原因**(前端并不知道当时发生了什么)。
 */
const unanswered = computed(
  () => !busy.value && messages.value.length > 0 && messages.value[messages.value.length - 1]!.role === 'user',
)

function nextId(): number {
  idSeed += 1
  return idSeed
}

// ---------------------------------------------------------------- 发送

async function send() {
  if (busy.value) return
  const text = draftText.value.trim()
  if (!text) return

  draftText.value = ''
  interruptError.value = ''
  turn.value = 'sending'

  messages.value.push({ id: nextId(), role: 'user', content: text, refs: [], status: 'done' })
  messages.value.push({ id: nextId(), role: 'assistant', content: '', refs: [], status: 'streaming' })
  // 取回**响应式代理**再改:拿原始对象改不会触发视图更新(切会话时尤其容易踩)
  const reply = messages.value[messages.value.length - 1]!

  const controller = new AbortController()
  activeAbort = controller
  const seq = requestSeq

  try {
    for await (const event of streamChat({
      message: text,
      sessionId: sessionId.value ?? undefined,
      signal: controller.signal,
    })) {
      if (seq !== requestSeq) return
      if (applyEvent(reply, event)) {
        turn.value = event.type === 'error' ? 'rejected' : 'done'
        return
      }
      turn.value = 'streaming'
    }
    // 生成器只有在收到 done / error 后才会正常结束 —— 走到这里说明它没给出结局。
    // (生成器自身会把"静默断流"抛出来,所以正常不会到这)
  } catch (error) {
    if (seq !== requestSeq) return

    if (isAbort(error)) {
      reply.status = 'aborted'
      turn.value = 'aborted'
      return
    }
    if (error instanceof BusinessError) {
      // 业务拒绝(未登录、越界……)—— 照抄后端文案,**不给重试**:
      // 重试同一个问题不会有不同的答案
      reply.status = 'rejected'
      reply.errorText = messageFor(error.code, error.message)
      turn.value = 'rejected'
      return
    }
    reply.status = 'interrupted'
    interruptError.value = '与服务器失去联系,请重试。'
    turn.value = 'interrupted'
  } finally {
    if (activeAbort === controller) activeAbort = null
  }
}

/** 把一帧应用到气泡上。返回 true 表示这是**终态帧**。 */
function applyEvent(reply: ChatItem, event: ChatEvent): boolean {
  switch (event.type) {
    case 'session':
      sessionId.value = event.sessionId
      return false
    case 'delta':
      reply.content += event.delta
      return false
    case 'refs':
      // 空数组照收(后端"一个候选都校验不过"时给的就是空),渲染时自然不画卡片
      reply.refs = event.refs
      return false
    case 'done':
      reply.status = 'done'
      return true
    case 'error':
      reply.status = 'rejected'
      reply.errorText = messageFor(event.code, event.message)
      return true
  }
}

/** 用户点「停止」。**保留半截文本、标一句「已停止」,不弹错误**。 */
function stopStreaming() {
  activeAbort?.abort()
}

/** 切会话 / 离开页面:让在途回调彻底失效后再中断。 */
function interrupt() {
  requestSeq += 1
  activeAbort?.abort()
  activeAbort = null
}

function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

/**
 * Enter 提交,但**输入法组字期间不能提交** —— 中文输入法用回车选词,
 * 不判 `isComposing` 会把没打完的拼音当成问题发出去。
 */
function onEnter(event: KeyboardEvent) {
  if (event.isComposing) event.preventDefault()
}

// ---------------------------------------------------------------- 采纳

function isAdopted(candidate: SkuRef): boolean {
  return draft.partOf(candidate.part)?.sku === candidate.sku
}

/**
 * 采纳一条推荐:写进选购草稿(**同部位替换,其余保留**)。
 *
 * ⚠️ **绝不调用 `draft.clear()`** —— 那会把用户已经录好的**户型**一起清掉。
 * 采纳只该动"砖"这一层。
 */
function adopt(candidate: SkuRef) {
  const name = candidate.name?.trim()
  draft.setPart(candidate.part, candidate.sku, name ? { name } : {})
}

/** 跳「户型录入」页继续标准流程(PRD §5.2.3)。数据都在 store 里,路径不带参数。 */
function goLayout() {
  router.push({ name: 'layout' })
}

// ---------------------------------------------------------------- 历史

const historyOpen = ref(false)
const sessions = ref<ChatSession[]>([])
const historyLoading = ref(false)
const historyError = ref('')

async function toggleHistory() {
  historyOpen.value = !historyOpen.value
  if (!historyOpen.value || sessions.value.length > 0) return

  historyLoading.value = true
  historyError.value = ''
  try {
    const page = await listChatSessions({ page: 1, size: 20 })
    sessions.value = page.list ?? []
  } catch (error) {
    // **不阻塞聊天**:历史打不开就直说,输入框照常可用
    historyError.value =
      error instanceof BusinessError ? messageFor(error.code, error.message) : '历史对话暂时打不开。'
  } finally {
    historyLoading.value = false
  }
}

async function openSession(id: string) {
  interrupt()
  turn.value = 'idle'
  interruptError.value = ''
  messages.value = []
  sessionId.value = id

  const seq = requestSeq
  try {
    const detail = await getChatSession(id)
    if (seq !== requestSeq) return
    sessionId.value = detail.session.id
    messages.value = (detail.messages ?? []).map((message) => ({
      id: nextId(),
      role: message.role,
      content: message.content,
      refs: message.refs ?? [],
      status: 'done' as const,
    }))
  } catch (error) {
    if (seq !== requestSeq) return
    interruptError.value =
      error instanceof BusinessError ? messageFor(error.code, error.message) : '这条对话打不开。'
  }
}

onBeforeUnmount(interrupt)
</script>

<template>
  <section>
    <h1>问 AI 顾问</h1>
    <p class="note">
      用大白话描述你的空间和喜好,AI 会基于产品库给出搭配建议与可采纳的瓷砖。
      <strong>不提供报价</strong>。
    </p>

    <div class="chat">
      <p v-if="!messages.length" class="empty">
        还没有对话。可以试试:「卫生间浅灰地砖配什么墙砖?」
      </p>

      <ol v-else class="stream">
        <li v-for="item in messages" :key="item.id" class="bubble" :class="item.role">
          <div class="who">{{ item.role === 'user' ? '我' : 'AI 顾问' }}</div>
          <!-- 回答是 Markdown 风格的文本,这里渲染成安全的一小撮 HTML。
               ⚠️ **`renderRichText` 会先转义再插标签** —— 内容来自大模型,是不可信的,
               详见 `src/utils/richText.ts` 的说明。不要绕开它直接 v-html 原始文本。 -->
          <p class="text">
            <!-- eslint-disable-next-line vue/no-v-html -- 见上方说明:已先转义 -->
            <span v-html="renderRichText(item.content)" /><span
              v-if="item.status === 'streaming'"
              class="caret"
            >▍</span>
          </p>

          <!-- 拒答:照抄后端文案,提示态而不是故障态 -->
          <p v-if="item.status === 'rejected'" class="notice">{{ item.errorText }}</p>
          <p v-if="item.status === 'aborted'" class="note">(已停止)</p>
          <p v-if="item.status === 'interrupted'" class="note">(不完整,连接中断)</p>

          <div v-if="item.refs.length" class="refs">
            <h3>可采纳的推荐</h3>
            <ul>
              <li v-for="candidate in item.refs" :key="`${candidate.part}-${candidate.sku}`" class="ref">
                <div class="ref-name">
                  <strong>{{ candidate.name?.trim() ? candidate.name : candidate.sku }}</strong>
                  <span class="sku">{{ candidate.sku }}</span>
                </div>
                <p class="note">将铺到:{{ PART_LABELS[candidate.part] ?? candidate.part }}</p>
                <button type="button" class="ghost" :disabled="isAdopted(candidate)" @click="adopt(candidate)">
                  {{ isAdopted(candidate) ? '已采纳' : '采纳' }}
                </button>
              </li>
            </ul>
          </div>
        </li>
      </ol>

      <p v-if="unanswered" class="note">(这次提问没有留下回答)</p>
    </div>

    <!-- 真·故障与拒答分开:这一块只在连接出问题时出现 -->
    <p v-if="interruptError" class="error" role="alert">{{ interruptError }}</p>

    <form class="composer" @submit.prevent="send">
      <input
        v-model="draftText"
        type="text"
        :readonly="busy"
        placeholder="例如:卫生间浅灰地砖配什么墙砖?"
        aria-label="你的问题"
        @keydown.enter="onEnter"
      />
      <button type="submit" class="primary" :disabled="!canSend">
        {{ turn === 'sending' ? '连接中…' : '发送' }}
      </button>
      <button v-if="busy" type="button" class="ghost" @click="stopStreaming">停止</button>
    </form>

    <div v-if="draft.hasParts" class="actions">
      <span class="note">已选部位:{{ selectedSummary }}</span>
      <button type="button" class="primary" @click="goLayout">带着这些砖去录户型</button>
    </div>

    <div class="panel">
      <button type="button" class="ghost" @click="toggleHistory">
        {{ historyOpen ? '收起历史' : '历史对话' }}
      </button>

      <template v-if="historyOpen">
        <p v-if="historyLoading" class="note">载入中…</p>
        <p v-else-if="historyError" class="note">{{ historyError }}</p>
        <ul v-else-if="sessions.length" class="history">
          <li v-for="item in sessions" :key="item.id">
            <button type="button" class="link" @click="openSession(item.id)">
              {{ item.title?.trim() ? item.title : '未命名对话' }}
            </button>
          </li>
        </ul>
        <p v-else class="note">还没有历史对话。</p>
      </template>
    </div>
  </section>
</template>

<style scoped>
h1 {
  margin: 0 0 0.5rem;
  font-size: 1.4rem;
}

h3 {
  margin: 0 0 0.5rem;
  font-size: 0.9rem;
}

.note {
  color: #6b7280;
  font-size: 0.8rem;
}

.empty {
  border: 1px dashed #d1d5db;
  border-radius: 0.375rem;
  padding: 1rem;
  color: #6b7280;
  font-size: 0.9rem;
}

.stream {
  list-style: none;
  margin: 0 0 1rem;
  padding: 0;
}

.bubble {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  padding: 0.75rem;
  margin-bottom: 0.75rem;
}

.bubble.user {
  background: #f9fafb;
}

.who {
  font-size: 0.75rem;
  color: #6b7280;
  margin-bottom: 0.25rem;
}

.text {
  margin: 0;
  white-space: pre-wrap;
  font-size: 0.95rem;
}

/* ⚠️ 必须用 `:deep()`:`v-html` 注入的元素**拿不到 scoped 属性**,
   直接写 `.text strong` 是不匹配的 —— 而且这种不匹配不会报错,只会"样式没生效"。 */
.text :deep(strong) {
  font-weight: 600;
}

.text :deep(code) {
  padding: 0.05rem 0.25rem;
  border-radius: 0.2rem;
  background: #f3f4f6;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85em;
}

.caret {
  animation: blink 1s step-start infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.notice {
  margin: 0.5rem 0 0;
  padding: 0.5rem 0.75rem;
  border-left: 3px solid #d1d5db;
  background: #f9fafb;
  color: #374151;
  font-size: 0.85rem;
}

.error {
  padding: 0.5rem 0.75rem;
  border: 1px solid #fca5a5;
  border-radius: 0.25rem;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 0.85rem;
}

.refs {
  margin-top: 0.75rem;
  border-top: 1px dashed #e5e7eb;
  padding-top: 0.75rem;
}

.refs ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ref {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f3f4f6;
}

.ref-name {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.sku {
  color: #6b7280;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.75rem;
}

.ref .note {
  margin: 0;
}

.composer {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.composer input {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  font: inherit;
}

button.primary {
  padding: 0.5rem 1rem;
  border: 1px solid #1d4ed8;
  border-radius: 0.25rem;
  background: #1d4ed8;
  color: white;
  cursor: pointer;
}

button.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

button.ghost {
  padding: 0.5rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 0.25rem;
  background: white;
  cursor: pointer;
}

button.ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.panel {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  padding: 1rem;
  margin-bottom: 1rem;
}

.history {
  list-style: none;
  margin: 0.75rem 0 0;
  padding: 0;
}

.history li {
  padding: 0.25rem 0;
}

.link {
  border: none;
  background: none;
  padding: 0;
  color: #1d4ed8;
  cursor: pointer;
  font: inherit;
  text-align: left;
}
</style>
