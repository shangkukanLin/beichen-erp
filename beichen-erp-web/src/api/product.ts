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
  /** SKU 精确查询 */
  sku?: string
  brandId?: number
  category?: string
  status?: string
}

/** 成品信息 */
export interface Product {
  id?: number | string
  name: string
  /** SKU 编码（产品级唯一；新增留空由后端自动生成 SKU-000001） */
  sku?: string
  brandId?: number
  category?: string
  spec?: string
  /** 通用型号（适用多款机型） */
  generalModel?: string
  unit?: string
  safetyStock?: number
  /** 移动加权平均成本价（采购/委外入库自动更新；costManual=1 时以手填为准） */
  costPrice?: number
  /** 成本价是否手工锁定（1=手改，自动加权跳过） */
  costManual?: number
  /** 最近一次入库单价（参考价） */
  lastInPrice?: number
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

/**
 * 产品统一展示标签：`SKU | 名称`（无 SKU 时只显示名称）。
 * <p>用于产品下拉与选品控件，便于直接按 SKU 识别产品；搜索侧后端已支持 keyword 同时匹配名称与 SKU。</p>
 */
export function productLabel(row: any): string {
  if (!row) return ''
  const name = row.name ?? row.productName ?? ''
  return row.sku ? `${row.sku} | ${name}` : String(name)
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


