import request from '@/utils/request'

/** 采购单状态 */
export const PurchaseStatus = {
  DRAFT: 'DRAFT',      // 草稿
  AUDITED: 'AUDITED',  // 已审核
  CANCELLED: 'CANCELLED'   // 已作废
} as const

export const PurchaseStatusLabel: Record<string, string> = {
  [PurchaseStatus.DRAFT]: '草稿',
  [PurchaseStatus.AUDITED]: '已审核',
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
  taxAmount?: number
  totalAmount?: number
  remark?: string
  itemsSummary?: string
  items?: PurchaseOrderItem[]
}

// ---- 采购退货单 ----

export interface PurchaseReturnItem {
  id?: number
  returnId?: number
  /** 关联采购单明细ID（用于追溯） */
  purchaseOrderItemId?: number
  productId?: number
  /** 产品名称（展示字段，后端回填） */
  productName?: string
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
  /** 关联采购单ID（可选） */
  purchaseOrderId?: number
  purchaseOrderCode?: string
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
  materialTypeId?: number
  materialTypeName?: string
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

// ---- 采购退货单 API ----
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
/** 按采购单查询关联的退货单列表 */
export function getPurchaseReturnByOrder(purchaseOrderId: number) {
  return request.get<any[]>(`/inventory/purchase-return/by-order`, { params: { purchaseOrderId } })
}
/** 查询采购单明细（含已退/可退数量，供退货带入） */
export function getPurchaseReturnPurchaseOrderItems(purchaseOrderId: number) {
  return request.get<any[]>(`/inventory/purchase-return/purchase-order-items`, { params: { purchaseOrderId } })
}
