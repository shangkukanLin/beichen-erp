import request from '@/utils/request'

export interface PurchaseOrderItem {
  id?: number
  orderId?: number
  materialId?: number
  materialCode?: string
  materialName?: string
  spec?: string
  unit?: string
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
  status?: string
  taxIncluded?: number
  taxRate?: number
  totalAmount?: number
  remark?: string
  items?: PurchaseOrderItem[]
}

export interface PurchaseInboundItem {
  id?: number
  inboundId?: number
  orderItemId?: number
  materialId?: number
  materialCode?: string
  materialName?: string
  spec?: string
  unit?: string
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

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

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
