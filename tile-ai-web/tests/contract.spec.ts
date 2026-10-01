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
  PAGE_DATA_REQUIRED_FIELDS,
  PART_VALUES,
  PLAN_PART_REQUIRED_FIELDS,
  PLAN_SUMMARY_REQUIRED_FIELDS,
  QUOTE_RESULT_REQUIRED_FIELDS,
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
