/**
 * 研发（dev）API（F8-26 整理 · 2026-09-30）
 *
 * <p>背景：这些函数的路径全是 `/dev/project/**`，与"系统设置"无关，却一直塞在 `api/system.ts` 里。
 * 实现已迁到本文件；`api/system.ts` 保留 **re-export** 兼容既有 import（dev/project/* 等 10+ 个页面
 * 无需改动），后续可逐步改为 `from '@/api/dev'`。</p>
 *
 * <p>类型定义暂时仍留在 `system.ts`，故这里按需 `import type`。</p>
 */
import request from '@/utils/request'
import type { PageResult } from '@/api/product'
import type { ProjectVO, ProjectDTO, ProjectQueryParams, BomVO, BomDTO, DrawingVO, BugVO, BugDTO } from '@/api/system'

export function getProjectPage(params: ProjectQueryParams) {
  return request.get<PageResult<ProjectVO>>('/dev/project/page', { params })
}
export function getProject(id: number | string) { return request.get<ProjectVO>(`/dev/project/${id}`) }
export function addProject(data: ProjectDTO, linkExistingProductId?: number | string) { return request.post<void>('/dev/project', data, { params: linkExistingProductId ? { linkExistingProductId } : {} }) }
/** 产品名称查重（2026-09-21：端点随「总成名称 → 产品名称」改名 /check-assembly → /check-product-name） */
export function checkProjectProductName(name: string) { return request.get<{ exists: boolean; productId?: number; productName?: string }>('/dev/project/check-product-name', { params: { name } }) }
export function updateProject(data: ProjectDTO) { return request.put<void>('/dev/project', data) }
export function deleteProject(id: number | string) { return request.delete<void>(`/dev/project/${id}`) }
export function updateProjectStatus(id: number | string, status: string) { return request.put<void>(`/dev/project/${id}/status?status=${status}`) }

export function getProjectBom(projectId: number | string) { return request.get<BomVO[]>(`/dev/project/${projectId}/bom`) }
export function saveProjectBom(projectId: number | string, items: BomDTO[]) { return request.post<void>(`/dev/project/${projectId}/bom/batch`, items) }
/** BOM 历史快照（2026-09-17）：下加工单时按「研发BOM版本 + 明细内容」生成/共享，仅在"有变化"时新增 */
export function getProjectBomSnapshots(projectId: number | string) { return request.get<any[]>(`/dev/project/${projectId}/bom-snapshots`) }

export function getProjectDrawings(projectId: number | string) { return request.get<DrawingVO[]>(`/dev/project/${projectId}/drawing`) }
export function addProjectDrawing(projectId: number | string, data: DrawingVO) { return request.post<void>(`/dev/project/${projectId}/drawing`, data) }
export function deleteProjectDrawing(projectId: number | string, id: number | string) { return request.delete<void>(`/dev/project/${projectId}/drawing/${id}`) }

export function getProjectBugs(projectId: number | string) { return request.get<BugVO[]>(`/dev/project/${projectId}/bug`) }
export function addProjectBug(projectId: number | string, data: BugDTO) { return request.post<void>(`/dev/project/${projectId}/bug`, data) }
export function updateProjectBug(projectId: number | string, data: BugDTO) { return request.put<void>(`/dev/project/${projectId}/bug/${data.id}`, data) }
export function deleteProjectBug(projectId: number | string, id: number | string) { return request.delete<void>(`/dev/project/${projectId}/bug/${id}`) }
