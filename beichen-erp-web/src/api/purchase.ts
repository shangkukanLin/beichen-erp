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

// ---- 成品退货单 ----

export interface PurchaseReturnItem {
  id?: number
  returnId?: number
  productId?: number
  qualityType?: string
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

/** 委外物料（成品采购物料下拉用） */
export interface OutsourceMaterialOption {
  id?: number
  materialName?: string
  spec?: string
  unit?: string
  bomTypeId?: number
  bomTypeName?: string
}

/** 查询委外物料列表（用于采购单物料下拉） */
export function getOutsourceMaterialPage(params: { pageNum?: number; pageSize?: number; materialName?: string }) {
  return request.get<PageResult<OutsourceMaterialOption>>('/outsource/material/page', { params })
}

// ---- 成品采购单 API ----
export function getPurchaseOrderPage(params: any) {
  return request.get<PageResult<PurchaseOrder>>('/inventory/purchase/page', { params })
}
export function getPurchaseOrder(id: number) {
  return request.get<PurchaseOrder>(`/inventory/purchase/${id}`)
}
export function getPurchaseOrderItems(id: number) {
  return request.get<PurchaseOrderItem[]>(`/inventory/purchase/${id}/items`)
}
export function createPurchaseOrder(data: any) {
  return request.post<void>('/inventory/purchase', data)
}
export function updatePurchaseOrder(id: number, data: any) {
  return request.put<void>(`/inventory/purchase/${id}`, data)
}
export function auditPurchaseOrder(id: number) {
  return request.put<void>(`/inventory/purchase/${id}/audit`)
}
export function cancelPurchaseOrder(id: number) {
  return request.put<void>(`/inventory/purchase/${id}/cancel`)
}
export function unAuditPurchaseOrder(id: number) {
  return request.put<void>(`/inventory/purchase/${id}/un-audit`)
}

// ---- 成品退货单 API ----
export function getPurchaseReturnPage(params: any) {
  return request.get<PageResult<PurchaseReturn>>('/inventory/purchase-return/page', { params })
}
export function getPurchaseReturn(id: number) {
  return request.get<PurchaseReturn>(`/inventory/purchase-return/${id}`)
}
export function getPurchaseReturnItems(id: number) {
  return request.get<PurchaseReturnItem[]>(`/inventory/purchase-return/${id}/items`)
}
export function createPurchaseReturn(data: any) {
  return request.post<void>('/inventory/purchase-return', data)
}
export function updatePurchaseReturn(id: number, data: any) {
  return request.put<void>(`/inventory/purchase-return/${id}`, data)
}
export function auditPurchaseReturn(id: number) {
  return request.put<void>(`/inventory/purchase-return/${id}/audit`)
}
export function cancelPurchaseReturn(id: number) {
  return request.put<void>(`/inventory/purchase-return/${id}/cancel`)
}
export function unAuditPurchaseReturn(id: number) {
  return request.put<void>(`/inventory/purchase-return/${id}/un-audit`)
}
export function deletePurchaseReturn(id: number) {
  return request.delete<void>(`/inventory/purchase-return/${id}`)
}
