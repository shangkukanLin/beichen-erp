import request from '@/utils/request'

export interface MaterialQueryParams {
  pageNum?: number
  pageSize?: number
  code?: string
  name?: string
  category?: string
  status?: number | string
}

export interface Material {
  id?: number | string
  code: string
  name: string
  category: string
  spec?: string
  unit: string
  safetyStock?: number
  currentStock?: number
  status: number
  remark?: string
  /** 子物料组成（新增/编辑时提交） */
  bomChildren?: MaterialBomItem[]
}

/** BOM 子物料项 */
export interface MaterialBomItem {
  childMaterialId?: number | string
  quantity?: number
  lossRate?: number
  remark?: string
}

/** BOM 树节点（含子物料实时信息 + 下级） */
export interface MaterialBomNode {
  id?: number | string
  parentMaterialId?: number | string
  childMaterialId?: number | string
  quantity?: number
  lossRate?: number
  remark?: string
  childCode?: string
  childName?: string
  childSpec?: string
  childUnit?: string
  childCategory?: string
  children?: MaterialBomNode[]
}

/** 物料简要信息 */
export interface MaterialBrief {
  id?: number | string
  code: string
  name: string
  spec?: string
  unit?: string
  category?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export function getMaterialPage(params: MaterialQueryParams) {
  return request.get<unknown, PageResult<Material>>('/material/page', { params })
}

export function getMaterial(id: number | string) {
  return request.get<unknown, Material>(`/material/${id}`)
}

export function addMaterial(data: Material) {
  return request.post<unknown, void>('/material', data)
}

export function updateMaterial(data: Material) {
  return request.put<unknown, void>('/material', data)
}

export function deleteMaterial(id: number | string) {
  return request.delete<unknown, void>(`/material/${id}`)
}

/** BOM：直接子物料（编辑弹窗加载） */
export function getMaterialBomDirect(parentId: number | string) {
  return request.get<unknown, MaterialBomNode[]>('/material-bom/direct', { params: { parentId } })
}

/** BOM：多级树（BOM表展示） */
export function getMaterialBomTree(materialId: number | string) {
  return request.get<unknown, MaterialBomNode[]>('/material-bom/tree', { params: { materialId } })
}

/** BOM：保存(替换)某物料的直接子物料组成 */
export function saveMaterialBom(parentId: number | string, children: MaterialBomItem[]) {
  return request.post<unknown, void>(`/material-bom/${parentId}`, children)
}

/** BOM：某子物料被哪些成品/半成品使用 */
export function getMaterialBomWhereUsed(childId: number | string) {
  return request.get<unknown, MaterialBrief[]>('/material-bom/where-used/' + childId)
}
