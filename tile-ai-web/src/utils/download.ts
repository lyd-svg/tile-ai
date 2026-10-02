/**
 * 前端生成文件并让用户下载(零依赖)。
 *
 * 用在"导出错误报告"上:后端**没有**现成的下载地址(`errorReportUrl` 恒为 null,
 * 对象存储未选型),所以导出只能由前端拿数据生成。
 */

/**
 * 把一个文本变量下载成文件。
 *
 * ⚠️ **必须带 UTF-8 BOM**(`﻿`):Excel 打开不带 BOM 的 CSV 会把中文显示成乱码,
 * 而运营拿这份错误报告是**要在 Excel 里改完再传回来**的 —— 乱码等于报告白导。
 */
export function downloadTextFile(filename: string, text: string, mime = 'text/csv'): void {
  const blob = new Blob([`﻿${text}`], { type: `${mime};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  // 立刻回收:blob 会一直占内存直到页面卸载
  URL.revokeObjectURL(url)
}
