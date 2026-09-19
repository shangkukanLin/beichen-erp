import request from '@/utils/request'

export interface Customer {
  id?: number
  code: string
  name: string
  contact?: string
  phone?: string
  address?: string
  creditPeriod?: number
  creditPeriodMonths?: number
  creditLimit?: number
  status: number
  remark?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export function getCustomerPage(params: any) {
  return request.get<PageResult<Customer>>('/inventory/customer/page', { params })
}

export function listCustomers() {
  return request.get<Customer[]>('/inventory/customer/list')
}

export function getCustomer(id: number) {
  return request.get<Customer>(`/inventory/customer/${id}`)
}

/**
 * 期 2（2026-09-19 读隔离）：客户详情页「销售单」页签改走**本页前缀**
 * （原直读 `/inventory/sale/page`，需 sale:order ⇒ 只有客户档案权限的用户会 403）。
 * 服务端分页语义不变：翻页 / 改每页条数仍重新查询。
 */
export function getCustomerSaleOrders(id: number, params: any) {
  return request.get<PageResult<any>>(`/inventory/customer/${id}/sale-orders`, { params })
}

export function createCustomer(data: Customer) {
  return request.post<void>('/inventory/customer', data)
}

export function updateCustomer(data: Customer) {
  return request.put<void>('/inventory/customer', data)
}

export function updateCustomerStatus(id: number, status: number) {
  return request.put<void>(`/inventory/customer/${id}/status`, null, { params: { status } })
}

