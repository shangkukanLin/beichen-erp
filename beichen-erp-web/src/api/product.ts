import request from '@/utils/request'

/** 产品状态（对应 ProductStatus 枚举） */
export const ProductStatus = {
  NORMAL: 'NORMAL',
  DISCONTINUED: 'DISCONTINUED',
  DEVELOPING: 'DEVELOPING'
} as const

export const ProductStatusLabel: Record<string, string> = {
  [ProductStatus.NORMAL]: '正常',
  [ProductStatus.DISCONTINUED]: '停售',
  [ProductStatus.DEVELOPING]: '研发中'
}

export const ProductStatusTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [ProductStatus.NORMAL]: 'success',
  [ProductStatus.DISCONTINUED]: 'danger',
  [ProductStatus.DEVELOPING]: 'warning'
}

/** 成品查询参数 */
export interface ProductQueryParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  category?: string
  status?: string
}

/** 成品信息 */
export interface Product {
  id?: number | string
  name: string
  brandId?: number
  category?: string
  spec?: string
  /** 通用型号（适用多款机型） */
  generalModel?: string
  unit?: string
  safetyStock?: number
  currentStock?: number
  status: typeof ProductStatus[keyof typeof ProductStatus] | string
  projectId?: number
  remark?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export function getProductPage(params: ProductQueryParams) {
  return request.get<PageResult<Product>>('/product/page', { params })
}

export function getProduct(id: number | string) {
  return request.get<Product>(`/product/${id}`)
}

export function addProduct(data: Product) {
  return request.post<void>('/product', data)
}

export function updateProduct(id: number | string, data: Product) {
  return request.put<void>(`/product/${id}`, data)
}

export function deleteProduct(id: number | string) {
  return request.delete<void>(`/product/${id}`)
}

/** 品质等级选项 */
export interface QualityOption {
  value: string
  label: string
}

/** 获取品质等级枚举列表 */
export function getQualityTypes() {
  return request.get<QualityOption[]>('/product/quality-types')
}


