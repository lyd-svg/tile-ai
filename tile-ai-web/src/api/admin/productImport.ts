/**
 * 产品库批量导入(F-0.4;openapi-admin.yaml 的 products 分组)。
 *
 * 权限点:上传 `product:import`、查询与模板 `product:read`(架构 §4.4)。
 *
 * ## 三处与别的模块不同
 *
 * 1. **上传是 multipart**,走 `http.ts` 的 `upload()`(那里复用了 401 续期与错误解包);
 * 2. **导入是异步的** —— `POST` 只返回 `batchId`,进度靠 `GET` 轮询,
 *    所以调用方要自己轮询到终态(见 `AdminProductImportView`);
 * 3. **`errorReportUrl` 后端恒为 `null`**(对象存储未选型,见 `ImportBatch` 的注释)——
 *    所以"导出错误报告"只能由前端拿 `errors[]` 自己生成 CSV(见 `errorsToCsv`)。
 */

import { ADMIN_BASE, request, upload } from '../http'

/**
 * 模板下载地址 —— **直接给 `<a href>` 用,不走 fetch**。
 *
 * 后端按 `Accept` 头协商格式(不含 `text/csv` 就是 xlsx)并带 `Content-Disposition: attachment`;
 * 同源导航会自带登录 Cookie,所以点一下就下载,零 JS。
 * (CSV 需要 `Accept: text/csv`,而 `<a>` 设不了请求头 —— 本轮不做 CSV 按钮。)
 */
export const productImportTemplateUrl = `${ADMIN_BASE}/products/import/template`

/** 导入模式。契约 enum 只有这两个值。 */
export type ImportMode = 'upsert' | 'insert_only'

/** 批次状态机。`pending` / `running` 时计数仍在变,前端应继续轮询。 */
export type ImportBatchStatus = 'pending' | 'running' | 'success' | 'failed'

/** 一条错误行。`row` 是**表里的行号** —— 修正后重导要靠它定位。 */
export interface ImportRowError {
  row: number
  reason: string
}

/** 受理结果。 */
export interface ImportAccepted {
  batchId: string
}

/** 批次进度与结果。 */
export interface ImportStatus {
  batchId: string
  status: ImportBatchStatus
  total?: number
  successCount?: number
  failedCount?: number
  errors?: ImportRowError[]
  /** ⚠️ **后端永远给 null**(对象存储未选型)—— 别拿它画下载链接,会是个死链 */
  errorReportUrl?: string | null
}

/**
 * 单个文件大小上限。**必须与后端 `application.yml` 的
 * `spring.servlet.multipart.max-file-size` 保持一致** —— 前端预检只是为了让用户
 * 少跑一趟,真正的闸门在那一边。
 */
export const MAX_IMPORT_FILE_BYTES = 20 * 1024 * 1024

/** 受理一次导入(异步)。返回 batchId,之后靠 `getImportStatus` 轮询。 */
export function acceptImport(file: File, mode: ImportMode): Promise<ImportAccepted> {
  const form = new FormData()
  form.append('file', file)
  form.append('mode', mode)
  return upload<ImportAccepted>(`${ADMIN_BASE}/products/import`, form)
}

/** 查批次进度 / 结果。批次不存在时服务端返回 **30001**。 */
export function getImportStatus(batchId: string): Promise<ImportStatus> {
  return request<ImportStatus>({ path: `${ADMIN_BASE}/products/import/${batchId}` })
}

/** CSV 单元格转义:含分隔符 / 引号 / 换行时用双引号包起来,内部的引号翻倍。 */
function csvCell(value: string): string {
  return /[",\n\r]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}

/**
 * 把错误行拼成 CSV 文本。
 *
 * **顺序原样保留**(列表没有 sort 参数,前端不重排);`row` 放在第一列,
 * 因为它是"改完这一行再传一次"的定位键。
 */
export function errorsToCsv(errors: ImportRowError[]): string {
  const lines = ['行号,原因', ...errors.map((item) => `${item.row},${csvCell(item.reason)}`)]
  return lines.join('\n')
}
