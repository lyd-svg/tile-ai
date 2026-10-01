/**
 * 效果图生成(openapi-c.yaml 的 generate 分组,基路径 /api/v1)。
 *
 * 两个接口**都需要登录**(契约 `security: cookieAuth`)—— 这是 C 端
 * 「浏览免登录、生成要登录」那条线所在的位置(§5.1)。
 */

import { API_BASE, request } from '../http'
import type { LayoutModel, PartSelection, PlanImage, TaskStatus } from '../types'

export interface GenerateRequest {
  /** 生成空间(单空间出图)。取值以字典为准 */
  space: string
  /** 装修风格。**可留空** */
  style?: string
  /**
   * **生成张数**。1–4,默认 1。
   * **张数 = angles,不是 angles+1** —— 见 `PlanImage.view`。
   */
  angles?: number
  /** 重新编辑时携带;不带则新建方案,新 id 经 `TaskProgress.planId` 回传 */
  planId?: number
  layout: LayoutModel
  /** **允许部分部位(至少 1 个)**;缺失的地面/墙面由后端补齐(§10.6①) */
  parts: PartSelection[]
}

/** 受理结果:`status` 此时恒为 `pending`。 */
export interface GenerateAccepted {
  taskId: string
  status: TaskStatus
}

/**
 * 任务进度 / 结果。
 *
 * ⚠️ **这里没有 `error` 字段** —— 失败时后端返回的是
 * **HTTP 200 + `code:40001` + 原因在 `ApiResponse.message`**,
 * 而 `request()` 对 `code≠0` 会**抛 `BusinessError`**。
 * 所以轮询必须 `try/catch`,不能按"拿到 200 就是成功"处理。
 */
export interface TaskProgress {
  taskId: string
  status: TaskStatus
  /** 0–100 */
  progress?: number
  /** 成功后方案 id */
  planId?: number | null
  /** 成功后可用。张数 = 提交时的 angles */
  images?: PlanImage[]
  /** 含 AI 补齐后的完整部位(source=ai 标注) */
  parts?: PartSelection[]
}

/** 提交生成任务(异步)。失败码 10001 校验 / 40002 生成繁忙 / 40004 部位补齐失败。 */
export function submitGenerate(body: GenerateRequest): Promise<GenerateAccepted> {
  return request<GenerateAccepted>({ method: 'POST', path: `${API_BASE}/generate`, body })
}

/** 查询任务进度 / 结果。 */
export function getGenerateProgress(taskId: string): Promise<TaskProgress> {
  return request<TaskProgress>({ method: 'GET', path: `${API_BASE}/generate/${taskId}` })
}
