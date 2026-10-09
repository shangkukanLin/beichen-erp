import request from '@/utils/request'
import { ProductQualityType, ProductQualityTypeLabel } from '@/api/enums'

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
  /** 规格（ORIGINAL原装 / MATCHED原配 / MODIFIED改配） */
  specType?: string
  status?: string
}

/** 成品信息 */
export interface Product {
  id?: number | string
  name: string
  /** SKU 编码（产品级唯一；新增留空由后端自动生成 SKU-000001） */
  sku?: string
  brandId?: number
  /** 供货商ID（类型=product 的供货商）；新增时决定 SKU 前缀（取该供货商的 supplySku），编辑时改动不回改已有 SKU */
  supplierId?: number
  /** 规格（ORIGINAL原装 / MATCHED原配 / MODIFIED改配）；产品管理新增/编辑必填 */
  specType?: string
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

/**
 * 只改「安全库存」（2026-10-09 用户需求：成品库存详情列表里点安全库存直接弹框改）。
 *
 * <p>为什么不复用 {@link updateProduct}：那个是**整实体**提交、且要求**规格必填**（产品管理表单用），
 * 只发 `{ safetyStock }` 会被后端校验拦下 ✗。本端点是独立的最小写入口。</p>
 * <p>落点仍是 `/api/product` 前缀 ⇒ 后端权限自动走 `base:product`（与产品管理页同码），
 * 前端「product → productStock」域级联刷新（`dataFreshness.ts`）也会自动生效。</p>
 */
export function updateProductSafetyStock(id: number | string, safetyStock: number) {
  return request.put<void>(`/product/${id}/safety-stock`, { safetyStock })
}

/** 品质等级选项 */
export interface QualityOption {
  value: string
  label: string
}

/**
 * 品质等级选项（同步返回）
 * <p>2026-09-14 起由前端枚举映射生成，不再请求后端：后端 `/product/quality-types` 已按
 * 「接口只回 code」返回 code 列表（仅保留给 API 调用方）。</p>
 * <p>保留原函数名与返回形状，调用方 `await getQualityTypes()` 无需改动（await 非 Promise 值同样成立）。</p>
 */
export function getQualityTypes(): QualityOption[] {
  return Object.values(ProductQualityType).map(v => ({ value: v, label: ProductQualityTypeLabel[v] || v }))
}


