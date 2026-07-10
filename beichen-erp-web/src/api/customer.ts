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
  receivableBalance?: number
  prepaidBalance?: number
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
  return request.get<unknown, PageResult<Customer>>('/inventory/customer/page', { params })
}

export function listCustomers() {
  return request.get<unknown, Customer[]>('/inventory/customer/list')
}

export function getCustomer(id: number) {
  return request.get<unknown, Customer>(`/inventory/customer/${id}`)
}

export function createCustomer(data: Customer) {
  return request.post<unknown, void>('/inventory/customer', data)
}

export function updateCustomer(data: Customer) {
  return request.put<unknown, void>('/inventory/customer', data)
}

export function updateCustomerStatus(id: number, status: number) {
  return request.put<unknown, void>(`/inventory/customer/${id}/status`, null, { params: { status } })
}
