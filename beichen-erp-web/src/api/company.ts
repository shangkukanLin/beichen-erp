import request from '@/utils/request'

export interface Company {
  id?: number
  companyName: string
  status?: number
}

export function verifyAdmin(data: { username: string; password: string }) {
  return request.post<unknown, { token: string }>('/company/admin/verify', data)
}

export function getCompanyList() {
  return request.get<unknown, Company[]>('/company/list')
}

export function createCompany(data: Company) {
  return request.post<unknown, void>('/company', data)
}

export function updateCompany(id: number, data: Company) {
  return request.put<unknown, void>(`/company/${id}`, data)
}

export function deleteCompany(id: number) {
  return request.delete<unknown, void>(`/company/${id}`)
}
