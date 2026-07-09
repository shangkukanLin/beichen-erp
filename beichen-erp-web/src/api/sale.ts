import request from '@/utils/request'

export interface SaleOrderItem {
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
  return request.get<unknown, PageResult<SaleOrder>>('/inventory/sale/page', { params })
}
export function getSaleOrder(id: number) {
  return request.get<unknown, SaleOrder>(`/inventory/sale/${id}`)
}
export function getSaleOrderItems(id: number) {
  return request.get<unknown, SaleOrderItem[]>(`/inventory/sale/${id}/items`)
}
export function createSaleOrder(data: any) {
  return request.post<unknown, void>('/inventory/sale', data)
}
export function updateSaleOrder(id: number, data: any) {
  return request.put<unknown, void>(`/inventory/sale/${id}`, data)
}
export function auditSaleOrder(id: number) {
  return request.put<unknown, void>(`/inventory/sale/${id}/audit`)
}
export function cancelSaleOrder(id: number) {
  return request.put<unknown, void>(`/inventory/sale/${id}/cancel`)
}

export function getSaleOutboundPage(params: any) {
  return request.get<unknown, PageResult<SaleOutbound>>('/inventory/outbound/page', { params })
}
export function getSaleOutbound(id: number) {
  return request.get<unknown, SaleOutbound>(`/inventory/outbound/${id}`)
}
export function getSaleOutboundItems(id: number) {
  return request.get<unknown, SaleOutboundItem[]>(`/inventory/outbound/${id}/items`)
}
export function createSaleOutbound(data: any) {
  return request.post<unknown, void>('/inventory/outbound', data)
}
export function updateSaleOutbound(id: number, data: any) {
  return request.put<unknown, void>(`/inventory/outbound/${id}`, data)
}
export function auditSaleOutbound(id: number) {
  return request.put<unknown, void>(`/inventory/outbound/${id}/audit`)
}
export function cancelSaleOutbound(id: number) {
  return request.put<unknown, void>(`/inventory/outbound/${id}/cancel`)
}
