import request from '@/utils/request'

/** 采购单状态 */
export const PurchaseStatus = {
  DRAFT: 0,      // 草稿
  COMPLETED: 1,  // 已完成
  CANCELLED: 2   // 已作废
} as const

export const PurchaseStatusLabel: Record<number, string> = {
  [PurchaseStatus.DRAFT]: '草稿',
  [PurchaseStatus.COMPLETED]: '已完成',
  [PurchaseStatus.CANCELLED]: '已作废'
}

/** 退货单状态（同采购单） */
export const ReturnStatus = PurchaseStatus
export const ReturnStatusLabel = PurchaseStatusLabel

export interface PurchaseOrderItem {
  id?: number
  orderId?: number
  productId?: number
  quantity?: number
  unitPrice?: number
  amount?: number
  remark?: string
}

export interface PurchaseOrder {
  id?: number
  code?: string
  supplierId?: number
  supplierName?: string
  warehouseId?: number
  orderDate?: string
  status?: number
  taxIncluded?: number
  taxRate?: number
  totalAmount?: number
  remark?: string
  itemsSummary?: string
  items?: PurchaseOrderItem[]
}

export interface PurchaseInboundItem {
  id?: number
  inboundId?: number
  orderItemId?: number
  productId?: number
  quantity?: number
  unitPrice?: number
  amount?: number
  remark?: string
}

export interface PurchaseInbound {
  id?: number
  code?: string
  orderId?: number
  supplierId?: number
  supplierName?: string
  warehouseId?: number
  inboundDate?: string
  status?: string
  totalAmount?: number
  remark?: string
  items?: PurchaseInboundItem[]
}

// ---- 成品退货单 ----

export interface PurchaseReturnItem {
  id?: number
  returnId?: number
  productId?: number
  quantity?: number
  unitPrice?: number
  amount?: number
  remark?: string
}

export interface PurchaseReturn {
  id?: number
  code?: string
  supplierId?: number
  supplierName?: string
  warehouseId?: number
  returnDate?: string
  status?: number
  totalAmount?: number
  remark?: string
  itemsSummary?: string
  items?: PurchaseReturnItem[]
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

// ---- 成品采购单 API ----
export function getPurchaseOrderPage(params: any) {
  return request.get<unknown, PageResult<PurchaseOrder>>('/inventory/purchase/page', { params })
}
export function getPurchaseOrder(id: number) {
  return request.get<unknown, PurchaseOrder>(`/inventory/purchase/${id}`)
}
export function getPurchaseOrderItems(id: number) {
  return request.get<unknown, PurchaseOrderItem[]>(`/inventory/purchase/${id}/items`)
}
export function createPurchaseOrder(data: any) {
  return request.post<unknown, void>('/inventory/purchase', data)
}
export function updatePurchaseOrder(id: number, data: any) {
  return request.put<unknown, void>(`/inventory/purchase/${id}`, data)
}
export function auditPurchaseOrder(id: number) {
  return request.put<unknown, void>(`/inventory/purchase/${id}/audit`)
}
export function cancelPurchaseOrder(id: number) {
  return request.put<unknown, void>(`/inventory/purchase/${id}/cancel`)
}
export function unAuditPurchaseOrder(id: number) {
  return request.put<unknown, void>(`/inventory/purchase/${id}/un-audit`)
}

// ---- 采购入库单 API ----
export function getPurchaseInboundPage(params: any) {
  return request.get<unknown, PageResult<PurchaseInbound>>('/inventory/inbound/page', { params })
}
export function getPurchaseInbound(id: number) {
  return request.get<unknown, PurchaseInbound>(`/inventory/inbound/${id}`)
}
export function getPurchaseInboundItems(id: number) {
  return request.get<unknown, PurchaseInboundItem[]>(`/inventory/inbound/${id}/items`)
}
export function createPurchaseInbound(data: any) {
  return request.post<unknown, void>('/inventory/inbound', data)
}
export function updatePurchaseInbound(id: number, data: any) {
  return request.put<unknown, void>(`/inventory/inbound/${id}`, data)
}
export function auditPurchaseInbound(id: number) {
  return request.put<unknown, void>(`/inventory/inbound/${id}/audit`)
}
export function cancelPurchaseInbound(id: number) {
  return request.put<unknown, void>(`/inventory/inbound/${id}/cancel`)
}

// ---- 成品退货单 API ----
export function getPurchaseReturnPage(params: any) {
  return request.get<unknown, PageResult<PurchaseReturn>>('/inventory/purchase-return/page', { params })
}
export function getPurchaseReturn(id: number) {
  return request.get<unknown, PurchaseReturn>(`/inventory/purchase-return/${id}`)
}
export function getPurchaseReturnItems(id: number) {
  return request.get<unknown, PurchaseReturnItem[]>(`/inventory/purchase-return/${id}/items`)
}
export function createPurchaseReturn(data: any) {
  return request.post<unknown, void>('/inventory/purchase-return', data)
}
export function updatePurchaseReturn(id: number, data: any) {
  return request.put<unknown, void>(`/inventory/purchase-return/${id}`, data)
}
export function auditPurchaseReturn(id: number) {
  return request.put<unknown, void>(`/inventory/purchase-return/${id}/audit`)
}
export function cancelPurchaseReturn(id: number) {
  return request.put<unknown, void>(`/inventory/purchase-return/${id}/cancel`)
}
export function unAuditPurchaseReturn(id: number) {
  return request.put<unknown, void>(`/inventory/purchase-return/${id}/un-audit`)
}
export function deletePurchaseReturn(id: number) {
  return request.delete<unknown, void>(`/inventory/purchase-return/${id}`)
}
