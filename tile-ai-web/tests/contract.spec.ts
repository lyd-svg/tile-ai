/**
 * 契约一致性断言(F-0.1)。
 *
 * **这是 T-2.1(契约 ↔ 实现双向 diff)在前端侧的对应物。**
 * 项目的验收传统是可执行的判据,不是肉眼对照 —— 前端这边同样:
 * 直接读契约 YAML,断言前端对契约的假设没有漂。
 *
 * 读的是仓库根目录下的契约文件(与四份 OpenAPI 同级),所以这些断言
 * 在契约被改动而前端没跟上时会立刻变红。
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// js-yaml v5 只有具名导出(没有带 .load 的默认导出)
import { load } from 'js-yaml'
import { describe, expect, it } from 'vitest'

import { ERROR_MESSAGES, ErrorCode } from '../src/api/errors'
import {
  API_RESPONSE_REQUIRED_FIELDS,
  CHAT_MESSAGE_REQUIRED_FIELDS,
  CHAT_MESSAGE_ROLES,
  CHAT_SESSION_REQUIRED_FIELDS,
  PAGE_DATA_REQUIRED_FIELDS,
  PART_VALUES,
  PLAN_PART_REQUIRED_FIELDS,
  PLAN_SUMMARY_REQUIRED_FIELDS,
  QUOTE_RESULT_REQUIRED_FIELDS,
  SKU_REF_REQUIRED_FIELDS,
} from '../src/api/types'

// 用 fileURLToPath + path.resolve 而不是把 URL 直接交给 readFileSync:
// 组件测试引入 happy-dom 后全局 URL 换成了它自己的实现,fs 不认。
// 先降级成普通路径字符串就与运行环境无关了。
const CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-common.yaml')
const C_CONTRACT = resolve(dirname(fileURLToPath(import.meta.url)), '../../openapi-c.yaml')

interface JsonSchema {
  required?: string[]
  enum?: unknown[]
  properties?: Record<string, JsonSchema>
  description?: string
}

function commonSchema(name: string): JsonSchema {
  const document = load(readFileSync(CONTRACT, 'utf8')) as {
    components: { schemas: Record<string, JsonSchema> }
  }
  const schema = document.components.schemas[name]
  if (!schema) throw new Error(`契约里找不到 schema:${name}`)
  return schema
}

interface Operation {
  security?: unknown[]
  responses?: Record<string, unknown>
  requestBody?: {
    required?: boolean
    content?: Record<string, { schema?: JsonSchema }>
  }
}

function cOperation(path: string, method: string): Operation {
  const document = load(readFileSync(C_CONTRACT, 'utf8')) as {
    paths: Record<string, Record<string, Operation>>
  }
  const op = document.paths[path]?.[method]
  if (!op) throw new Error(`C 端契约里找不到 ${method.toUpperCase()} ${path}`)
  return op
}

/**
 * C 端契约**自己**的 schema(不是 common 里的)。
 *
 * 大部分共用结构都在 openapi-common.yaml,但 `ShareResult` 直接写在
 * openapi-c.yaml 的 `components.schemas` 里 —— 所以要多一个读它的口子。
 */
function cSchema(name: string): JsonSchema {
  const document = load(readFileSync(C_CONTRACT, 'utf8')) as {
    components?: { schemas?: Record<string, JsonSchema> }
  }
  const schema = document.components?.schemas?.[name]
  if (!schema) throw new Error(`C 端契约里找不到 schema:${name}`)
  return schema
}

describe('ErrorCode 与前端文案表', () => {
  it('码集必须与契约 enum **完全一致**(不多不少)', () => {
    const contractCodes = commonSchema('ErrorCode').enum as number[]
    const frontendCodes = Object.values(ERROR_MESSAGES).length
      ? Object.keys(ERROR_MESSAGES).map(Number)
      : []

    // 多一个 = 前端留了契约已删的码(通常是重构残渣);
    // 少一个 = 服务端会返回一个前端没有文案的码(界面只能显示数字)
    expect(new Set(frontendCodes)).toEqual(new Set(contractCodes))
  })

  it('ErrorCode 常量与契约 enum 对齐', () => {
    const contractCodes = new Set(commonSchema('ErrorCode').enum as number[])
    for (const [name, code] of Object.entries(ErrorCode)) {
      expect(contractCodes.has(code), `${name}=${code} 不在契约 enum 里`).toBe(true)
    }
  })

  it('0 是成功码 —— 契约描述与 enum 都这么写', () => {
    expect(ErrorCode.SUCCESS).toBe(0)
    expect(commonSchema('ErrorCode').enum).toContain(0)
  })

  it('每个码都有非空文案', () => {
    for (const [code, text] of Object.entries(ERROR_MESSAGES)) {
      expect(text.length, `${code} 的文案为空`).toBeGreaterThan(0)
    }
  })
})

describe('响应体结构', () => {
  it('ApiResponse 的必需字段与前端假设一致', () => {
    const required = commonSchema('ApiResponse').required ?? []
    expect(new Set(required)).toEqual(new Set(API_RESPONSE_REQUIRED_FIELDS))
  })

  it('PageData 的必需字段与前端假设一致', () => {
    const required = commonSchema('PageData').required ?? []
    expect(new Set(required)).toEqual(new Set(PAGE_DATA_REQUIRED_FIELDS))
  })
})

describe('C 端会话恢复接口', () => {
  it('GET /auth/me 需要登录 —— 前端靠它恢复登录态,不能是匿名可调', () => {
    // 前端用这个接口判定"刷新后我还是不是我";它若变成公开接口,
    // 返回值就不再可信,路由守卫便形同虚设。
    expect(cOperation('/auth/me', 'get').security).toEqual([{ cookieAuth: [] }])
  })

  it('GET /auth/me 声明真实 401 —— 未登录时前端要靠它清态跳登录页', () => {
    expect(cOperation('/auth/me', 'get').responses).toHaveProperty('401')
  })
})

describe('部位枚举', () => {
  it('恒为三值,一期不含美缝', () => {
    // CLAUDE.md:部位枚举恒为 floor/wall/local,一期不含美缝。
    // 若有人在契约里加了第四个值(比如美缝),这里会先红 ——
    // 前端到处都在按三值渲染部位。
    expect(new Set(commonSchema('Part').enum as string[])).toEqual(new Set(PART_VALUES))
  })
})

describe('C 端方案接口(F-1.9 / F-1.8)', () => {
  const OPERATIONS: [string, string][] = [
    ['/plans', 'get'],
    ['/plans/{id}', 'get'],
    ['/plans/{id}', 'patch'],
    ['/plans/{id}', 'delete'],
    ['/plans/{id}/copy', 'post'],
    ['/plans/{id}/recalc', 'post'],
  ]

  it('六条接口全都要登录 —— 少一条就等于多一个匿名入口', () => {
    // 方案是"谁的",服务端按 Cookie 里的用户判定。任何一条掉成匿名,
    // 别人就能凭 id 猜到你的方案(30001 的三合一防的就是这个)。
    for (const [path, method] of OPERATIONS) {
      expect(cOperation(path, method).security, `${method.toUpperCase()} ${path}`).toEqual([
        { cookieAuth: [] },
      ])
    }
  })

  it('POST /plans/{id}/recalc **没有 requestBody** —— 架构不变量 6', () => {
    // 部位 / 面积 / 开口一律取自方案快照。让它收前端传参,就等于开了一条
    // 绕过 /quote/calc 参数校验的后门(可以直接把面积填成 1 ㎡ 去探价)。
    // 前端 `recalcPlan` 不带 body 是这条的另一半,两边都要钉住。
    expect(cOperation('/plans/{id}/recalc', 'post').requestBody).toBeUndefined()
  })

  it('PlanSummary 的必填字段与前端假设一致', () => {
    // ⚠️ 只有三个。前端把 totalAmount / coverImage / parts 都当**可选**渲染,
    // 这条红了说明契约改了必填口径,那几处兜底要跟着重看。
    const required = commonSchema('PlanSummary').required ?? []
    expect(new Set(required)).toEqual(new Set(PLAN_SUMMARY_REQUIRED_FIELDS))
  })

  it('PlanPart / QuoteResult 的必填字段与前端假设一致', () => {
    expect(new Set(commonSchema('PlanPart').required ?? [])).toEqual(
      new Set(PLAN_PART_REQUIRED_FIELDS),
    )
    expect(new Set(commonSchema('QuoteResult').required ?? [])).toEqual(
      new Set(QUOTE_RESULT_REQUIRED_FIELDS),
    )
  })

  it('⚠️ 登记一处已知分歧:PATCH /plans/{id} 的 name **没有声明长度上限**', () => {
    // 实现按**码点**卡 200(`PlanNames.NAME_MAX`),超了报 10001 —— 但契约只写了
    // `type: string`。`contract-diff.sh` 比的是路径/方法/字段名/类型/必填/枚举,
    // **约束维度它看不到**,所以这条分歧不会被任何门禁拦下。
    //
    // 这是**登记,不是修复**:补 `maxLength` 得走上游文档链(PRD → 架构 → 契约),
    // 不在本轮范围。前端因此自己按码点挡了一道(见 PlanDetailView.saveName)。
    //
    // 哪天有人给契约补上 `maxLength`,这条会红 —— 那时该做的是把前端的客户端
    // 校验改成读契约,而不是删掉这条断言。
    const schema = cOperation('/plans/{id}', 'patch').requestBody?.content?.['application/json']
      ?.schema
    const name = schema?.properties?.name

    expect(name, '契约里找不到 PATCH /plans/{id} 的 name 字段').toBeTruthy()
    expect(name?.type).toBe('string')
    expect(
      (name as { maxLength?: number } | undefined)?.maxLength,
      '契约补上了 maxLength —— 请把 PlanDetailView 的客户端校验改成读契约后再改这条',
    ).toBeUndefined()
  })
})

describe('C 端分享接口(F-1.10)', () => {
  it('生成 / 废除两条写接口**必须登录** —— 它们是「谁的方案」的入口', async () => {
    // 掉一条 = 任何人凭 id 就能把别人的方案分享出去 / 把别人的分享链接废掉
    for (const method of ['post', 'delete']) {
      expect(cOperation('/plans/{id}/share', method).security, method.toUpperCase()).toEqual([
        { cookieAuth: [] },
      ])
    }
  })

  it('⚠️ 公开分享页 `security` 是**空数组** —— 这是全项目唯一匿名可达的读接口', () => {
    // 契约写的是 `security: []`(不是没写 security)。写成 undefined 的话
    // 在 OpenAPI 里会**继承全局 security**,语义完全不同。
    //
    // 这条一变红,先想清楚:分享页一旦变成要登录,「拿到链接的家人打不开」
    // 就是必然结果(T-1.13 的整个卖点)。
    expect(cOperation('/share/{token}', 'get').security).toEqual([])
    expect(cOperation('/share/{token}', 'get').responses).toHaveProperty('200')
  })

  it('公开分享页的路径是 `/share/{token}` —— 前端路由与后端拼的链接都要对着它', () => {
    // 后端拼链接用的是 `baseUrl + "/share/" + token`(PlanShareService.SHARE_PATH),
    // 前端那条路由是 `/share/:token`。三处形状必须一致 —— 不一致的后果是
    // **后端发出去的链接落到前端的兜底重定向、静默回首页**,没有任何报错。
    // 契约是这三者里唯一能被测试读到的那个,所以钉在这里。
    const op = cOperation('/share/{token}', 'get')
    expect(op).toBeTruthy()
    expect((op as { parameters?: { name: string }[] }).parameters?.[0]?.name).toBe('token')
  })

  it('⚠️ 登记:`ShareResult` 三个字段**一个都没有 required**,`qrCodeUrl` 恒不出现', () => {
    // 契约这个 schema 上带着 `x-todo`(上游从没定义过 qrCodeUrl 该是什么形状)。
    // 2026-10-01 拍板「二维码由前端画」,后端 `ShareResultView` 恒返回 null,
    // 靠 `@JsonInclude(NON_NULL)` 让整个键消失 —— **不违约**(没写必填),
    // 但属于**已知的不一致面**,与 §41.6 的 materialFee 同类,
    // 登记在 openapi-verification.md §43.2。
    //
    // 哪天契约删掉 qrCodeUrl 或给它加上 required,这条会红 ——
    // 那时该做的是回头看 ShareResultView 与 docs 里的登记,而不是删掉这条断言。
    //
    // 前端据此把三个字段**都写成可选**,渲染前逐个判(见 c/plans.ts 的 ShareResult)。
    expect(cSchema('ShareResult').required).toBeUndefined()
    expect(Object.keys(cSchema('ShareResult').properties ?? {}).sort()).toEqual([
      'qrCodeUrl',
      'shareToken',
      'shareUrl',
    ])
  })
})

describe('AI 咨询(F-1.6)', () => {
  it('三条接口都要登录(契约 cookieAuth)—— C 端提问前必须登录', () => {
    const endpoints = [
      ['/agent/chat', 'post'],
      ['/agent/chat/sessions', 'get'],
      ['/agent/chat/sessions/{id}', 'get'],
    ] as const

    for (const [path, method] of endpoints) {
      expect(cOperation(path, method).security, `${method.toUpperCase()} ${path}`).toEqual([
        { cookieAuth: [] },
      ])
    }
  })

  it('/agent/chat 是 POST + `text/event-stream`;`message` 必填、`sessionId` 可省略', () => {
    const op = cOperation('/agent/chat', 'post')

    expect(op.requestBody?.required).toBe(true)
    const request = op.requestBody?.content?.['application/json']?.schema
    // **只有 message 必填** —— sessionId 首次省略是契约明说的(服务端建会话并在首帧下发)
    expect(request?.required).toEqual(['message'])
    expect(Object.keys(request?.properties ?? {}).sort()).toEqual(['message', 'sessionId'])

    const ok = (op.responses?.['200'] ?? {}) as { content?: Record<string, unknown> }
    expect(Object.keys(ok.content ?? {})).toContain('text/event-stream')
    expect(op.responses).toHaveProperty('401')
  })

  it('⚠️ 弱哨兵:`/agent/chat` 的说明文字里还写着事件集的关键词', () => {
    // **这条拦不住行为漂移** —— SSE 事件集在契约里只是**散文描述**
    // (响应 schema 只有一个不透明的 `{type: string}`),而 G1–G7 与契约比对
    // **都不解析 SSE**(见 openapi-verification.md §46.2:两个 /agent/chat 都落在
    // 「无法比对」那一栏)。**真正的判据是 tests/chat.spec.ts**。
    //
    // 留着它只有一个用处:哪天有人把这段协议说明整段删掉,这里会红 ——
    // 提醒他那是**契约变更**,要按文档链走,而不是顺手删。
    const ok = (cOperation('/agent/chat', 'post').responses?.['200'] ?? {}) as { description?: string }
    const description = ok.description ?? ''

    for (const token of ['[DONE]', 'delta', 'refs', 'sessionId', 'error']) {
      expect(description, `说明里应仍提到 ${token}`).toContain(token)
    }
  })

  it('结构 schema 的必填与枚举,与前端常量逐字对齐', () => {
    expect(commonSchema('SkuRef').required).toEqual(SKU_REF_REQUIRED_FIELDS)
    // `name` 是**可选展示名** —— 前端必须能兜住"没有它"
    expect(SKU_REF_REQUIRED_FIELDS).not.toContain('name')

    expect(commonSchema('ChatSession').required).toEqual(CHAT_SESSION_REQUIRED_FIELDS)
    // ⚠️ **id 是 string**(库里是自增 BIGINT,对外映射成十进制字符串,§46.4)
    expect(commonSchema('ChatSession').properties?.id?.type).toBe('string')

    expect(commonSchema('ChatMessage').required).toEqual(CHAT_MESSAGE_REQUIRED_FIELDS)
    expect(commonSchema('ChatMessage').properties?.role?.enum).toEqual(CHAT_MESSAGE_ROLES)
  })
})
