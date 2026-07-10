import request from '@/utils/request'

export interface MaterialQueryParams {
  pageNum?: number
  pageSize?: number
  name?: string
  status?: string
}

export interface Material {
  id?: number | string
  code?: string
  name: string
  brandId?: number
  category?: string
  spec?: string
  unit?: string
  safetyStock?: number
  currentStock?: number
  status: string
  remark?: string
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


