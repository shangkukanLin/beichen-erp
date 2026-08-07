import request from '@/utils/request'

export interface SaleOrderItem {
  id?: number
  orderId?: number
  productId?: number
  materialId?: number
  materialCode?: string
  materialName?: string
  spec?: string
  unit?: string
  qualityType?: string
  quantity?: number
  unitPrice?: number
  amount?: number
  remark?: string
}

export interface SaleOrder {
  id?: number
  code?: string
  customerId?: number
  customerName?: string
  warehouseId?: number
  orderDate?: string
  status?: string
  taxIncluded?: number
  taxRate?: number
  totalAmount?: number
  remark?: string
  items?: SaleOrderItem[]
}

export interface SaleOutboundItem {
  id?: number
  outboundId?: number
  orderItemId?: number
  materialId?: number
  materialCode?: string
  materialName?: string
  spec?: string
  unit?: string
  qualityType?: string
  quantity?: number
  unitPrice?: number
  amount?: number
  remark?: string
}

export interface SaleOutbound {
  id?: number
  code?: string
  orderId?: number
  customerId?: number
  customerName?: string
  warehouseId?: number
  outboundDate?: string
  status?: string
  totalAmount?: number
  remark?: string
  items?: SaleOutboundItem[]
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export function getSaleOrderPage(params: any) {
  return request.get<PageResult<SaleOrder>>('/inventory/sale/page', { params })
}
export function getSaleOrder(id: number) {
  return request.get<SaleOrder>(`/inventory/sale/${id}`)
}
export function getSaleOrderItems(id: number) {
  return request.get<SaleOrderItem[]>(`/inventory/sale/${id}/items`)
}
export function createSaleOrder(data: any) {
  return request.post<void>('/inventory/sale', data)
}
export function updateSaleOrder(id: number, data: any) {
  return request.put<void>(`/inventory/sale/${id}`, data)
}
export function auditSaleOrder(id: number) {
  return request.put<void>(`/inventory/sale/${id}/audit`)
}
export function cancelSaleOrder(id: number) {
  return request.put<void>(`/inventory/sale/${id}/cancel`)
}

/** 库存检查：传入 warehouseId + items，返回各物料库存对比 */
export function checkSaleOrderStock(data: { warehouseId?: number; items: SaleOrderItem[] }) {
  return request.post<{ materialName: string; spec: string; unit: string; required: number; available: number; shortage: number; sufficient: boolean }[]>(
    '/inventory/sale/check-stock', data
  )
}

export function getSaleOutboundPage(params: any) {
  return request.get<PageResult<SaleOutbound>>('/inventory/outbound/page', { params })
}
// ==================== 销售退货单 ====================
/** 销售退货单状态：0=草稿 1=已审核 2=已作废（与后端 SaleReturnStatus 一致） */
export const SaleReturnStatus = {
  DRAFT: 0,
  AUDITED: 1,
  CANCELLED: 2,
} as const

export const SaleReturnStatusLabel: Record<number, string> = {
  [SaleReturnStatus.DRAFT]: '草稿',
  [SaleReturnStatus.AUDITED]: '已审核',
  [SaleReturnStatus.CANCELLED]: '已作废',
}

export interface SaleReturnItem {
  id?: number
  returnId?: number
  productId?: number
  productName?: string
  /** 品质等级：销售退货固定为 DEFECT(不良品) */
  qualityType?: string
  quantity?: number
  unitPrice?: number
  amount?: number
  remark?: string
}

export interface SaleReturn {
  id?: number
  code?: string
  customerId?: number
  customerName?: string
  warehouseId?: number
  returnDate?: string
  status?: number
  totalAmount?: number
  remark?: string
  auditorId?: number
  auditorName?: string
  auditTime?: string
  createTime?: string
  items?: SaleReturnItem[]
}

export function getSaleReturnPage(params: any) {
  return request.get<PageResult<SaleReturn>>('/sale/return/page', { params })
}
export function getSaleReturn(id: number) {
  return request.get<SaleReturn>(`/sale/return/${id}`)
}
export function getSaleReturnItems(id: number) {
  return request.get<SaleReturnItem[]>(`/sale/return/${id}/items`)
}
export function createSaleReturn(data: any) {
  return request.post<void>('/sale/return', data)
}
export function updateSaleReturn(id: number, data: any) {
  return request.put<void>(`/sale/return/${id}`, data)
}
export function auditSaleReturn(id: number) {
  return request.put<void>(`/sale/return/${id}/audit`)
}
export function unAuditSaleReturn(id: number) {
  return request.put<void>(`/sale/return/${id}/unaudit`)
}
export function cancelSaleReturn(id: number) {
  return request.put<void>(`/sale/return/${id}/cancel`)
}
export function deleteSaleReturn(id: number) {
  return request.delete<void>(`/sale/return/${id}`)
}
export function getSaleOutboundItems(id: number) {
  return request.get<SaleOutboundItem[]>(`/inventory/outbound/${id}/items`)
}
export function createSaleOutbound(data: any) {
  return request.post<void>('/inventory/outbound', data)
}
export function updateSaleOutbound(id: number, data: any) {
  return request.put<void>(`/inventory/outbound/${id}`, data)
}
export function auditSaleOutbound(id: number) {
  return request.put<void>(`/inventory/outbound/${id}/audit`)
}
export function cancelSaleOutbound(id: number) {
  return request.put<void>(`/inventory/outbound/${id}/cancel`)
}

