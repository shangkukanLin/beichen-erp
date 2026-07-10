import request from '@/utils/request'

export interface Company {
  id?: number
  companyName: string
  status?: number
}

export function verifyAdmin(data: { username: string; password: string }) {
  return request.post<unknown, { token: string }>('/company/admin/verify', data)
}

/** 超管选择公司进入系统：切换 session companyId 并返回菜单 */
export function switchCompany(companyId: number) {
  return request.post<unknown, { companyId: number; roles: string[]; menus: any[] }>(
    '/company/switch',
    { companyId: String(companyId) }
  )
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
