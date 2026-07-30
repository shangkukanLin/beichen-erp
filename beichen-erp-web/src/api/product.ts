import request from '@/utils/request'

/** 成品查询参数 */
export interface ProductQueryParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  category?: string
}

/** 产品等级库存 */
export interface ProductQuality {
  id?: number | string
  productId?: number | string
  qualityType: string  // A/B/C/DEFECT
  quantity: number
  safetyStock?: number
}

/** 成品信息 */
export interface Product {
  id?: number | string
  code?: string
  name: string
  brandId?: number
  category?: string
  spec?: string
  /** 通用型号（适用多款机型） */
  generalModel?: string
  unit?: string
  safetyStock?: number
  currentStock?: number
  status: string
  projectId?: number
  remark?: string
  /** 等级库存列表 */
  qualities?: ProductQuality[]
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export function getProductPage(params: ProductQueryParams) {
  return request.get<unknown, PageResult<Product>>('/product/page', { params })
}

export function getProduct(id: number | string) {
  return request.get<unknown, Product>(`/product/${id}`)
}

export function addProduct(data: Product) {
  return request.post<unknown, void>('/product', data)
}

export function updateProduct(id: number | string, data: Product) {
  return request.put<unknown, void>(`/product/${id}`, data)
}

export function deleteProduct(id: number | string) {
  return request.delete<unknown, void>(`/product/${id}`)
}

/** 品质等级选项 */
export interface QualityOption {
  value: string
  label: string
}

/** 获取品质等级枚举列表 */
export function getQualityTypes() {
  return request.get<unknown, QualityOption[]>('/product/quality-types')
}
