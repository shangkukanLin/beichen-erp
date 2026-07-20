import request from '@/utils/request'
import type { PageResult } from '@/api/material'

/* ============================ 类型定义 ============================ */

export interface Role {
  id?: number | string
  roleName: string
  roleCode: string
  status: number
  remark?: string
  [key: string]: unknown
}

export interface UserVO {
  id?: number | string
  username: string
  phone?: string | null
  dept?: string | null
  status: number
  roles?: Role[]
  roleIds?: number[] | string[]
  [key: string]: unknown
}

export interface UserQueryParams {
  pageNum?: number
  pageSize?: number
  username?: string
  phone?: string
  status?: number | string
  roleId?: number | string
}

export interface UserDTO {
  id?: number | string
  username: string
  password?: string
  phone?: string | null
  dept?: string | null
  status: number
  roleIds: (number | string)[]
}

export interface RoleQueryParams {
  pageNum?: number
  pageSize?: number
  roleName?: string
  status?: number | string
}

export interface RoleDTO {
  id?: number | string
  roleName: string
  roleCode: string
  status: number
  remark?: string
}

export interface ResetPasswordParams {
  id: number | string
  password: string
}

export interface UserStatusParams {
  id: number | string
  status: number
}

/* ============================ 菜单相关类型 ============================ */

export interface MenuVO {
  id: number | string
  parentId: number
  menuName: string
  menuType: string
  routePath: string
  routeName: string
  icon: string
  sortOrder: number
  visible: number
  status: number
  children?: MenuVO[]
}

export interface MenuDTO {
  id?: number | string
  parentId: number
  menuName: string
  menuType: string
  routePath: string
  routeName: string
  icon: string
  sortOrder: number
  visible: number
  status: number
}

/* ============================ 用户管理 API ============================ */

export function getUserPage(params: UserQueryParams) {
  return request.get<unknown, PageResult<UserVO>>('/system/user/page', { params })
}

export function getUser(id: number | string) {
  return request.get<unknown, UserVO>(`/system/user/${id}`)
}

export function addUser(data: UserDTO) {
  return request.post<unknown, void>('/system/user', data)
}

export function updateUser(data: UserDTO) {
  return request.put<unknown, void>('/system/user', data)
}

export function deleteUser(id: number | string) {
  return request.delete<unknown, void>(`/system/user/${id}`)
}

export function resetPassword(data: ResetPasswordParams) {
  return request.put<unknown, void>('/system/user/reset-password', data)
}

export function toggleUserStatus(id: number | string, status: number) {
  return request.put<unknown, void>(`/system/user/${id}/status`, { status })
}

/* ============================ 角色管理 API ============================ */

export function getRolePage(params: RoleQueryParams) {
  return request.get<unknown, PageResult<Role>>('/system/role/page', { params })
}

export function getEnabledRoles() {
  return request.get<unknown, Role[]>('/system/role/enabled')
}

export function addRole(data: RoleDTO) {
  return request.post<unknown, void>('/system/role', data)
}

export function updateRole(data: RoleDTO) {
  return request.put<unknown, void>('/system/role', data)
}

export function deleteRole(id: number | string) {
  return request.delete<unknown, void>(`/system/role/${id}`)
}

/* ============================ 菜单管理 API ============================ */

export function getMenuTree() {
  return request.get<unknown, MenuVO[]>('/system/menu/tree')
}

export function getUserMenuTree() {
  return request.get<unknown, MenuVO[]>('/system/menu/tree/user')
}

export function addMenu(data: MenuDTO) {
  return request.post<unknown, void>('/system/menu', data)
}

export function updateMenu(data: MenuDTO) {
  return request.put<unknown, void>('/system/menu', data)
}

export function deleteMenu(id: number | string) {
  return request.delete<unknown, void>(`/system/menu/${id}`)
}

/* ============================ 角色菜单 API ============================ */

export function getRoleMenus(roleId: number | string) {
  return request.get<unknown, number[]>(`/system/role/${roleId}/menus`)
}

export function saveRoleMenus(roleId: number | string, menuIds: number[]) {
  return request.put<unknown, void>(`/system/role/${roleId}/menus`, menuIds)
}

/* ============================ 供应商管理 API ============================ */

export interface SupplierVO {
  id?: number | string
  code: string
  name: string
  supplierType: string
  contact?: string
  phone?: string
  address?: string
  status: number
  relatedSupplierId?: number | string
  creditPeriodMonths?: number
  creditPeriod?: number
  brand?: string
  materialType?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface SupplierDTO {
  id?: number | string
  code: string
  name: string
  supplierType: string
  contact?: string
  phone?: string
  address?: string
  status: number
  relatedSupplierId?: number | string
  creditPeriodMonths?: number
  creditPeriod?: number
  brand?: string
  materialType?: string
  remark?: string
}

export interface SupplierQueryParams {
  supplierType: string
  name?: string
  phone?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface SupplierProductVO {
  id?: number | string
  supplierId?: number | string
  productName: string
  spec?: string
  unit?: string
  unitPrice?: number
  remark?: string
}

export interface SupplierProductDTO {
  productName: string
  spec?: string
  unit?: string
  unitPrice?: number
  remark?: string
}

export function getSupplierPage(params: SupplierQueryParams) {
  return request.get<unknown, PageResult<SupplierVO>>('/supplier/page', { params })
}

export function getSupplier(id: number | string) {
  return request.get<unknown, SupplierVO>(`/supplier/${id}`)
}

export function getSupplierProducts(id: number | string) {
  return request.get<unknown, SupplierProductVO[]>(`/supplier/${id}/products`)
}

export function addSupplier(data: SupplierDTO) {
  return request.post<unknown, void>('/supplier', data)
}

export function updateSupplier(data: SupplierDTO) {
  return request.put<unknown, void>('/supplier', data)
}

export function deleteSupplier(id: number | string) {
  return request.delete<unknown, void>(`/supplier/${id}`)
}

export function toggleSupplierStatus(id: number | string) {
  return request.put<unknown, void>(`/supplier/${id}/status`)
}

export function saveSupplierProducts(id: number | string, products: SupplierProductDTO[]) {
  return request.put<unknown, void>(`/supplier/${id}/products`, products)
}

/* ============================ 研发项目 API ============================ */

export interface ProjectVO {
  id?: number | string; code: string; name: string
  assemblyName?: string
  displaySupplierName?: string; touchSupplierName?: string
  adaptModel?: string
  originalSize?: string; originalResolution?: string; projectLeaderId?: number
  sampleFactoryId?: number; outsourceFactoryId?: number
  startDate?: string; expectedEndDate?: string; actualEndDate?: string
  status?: string; remark?: string; createTime?: string; updateTime?: string
}

export interface ProjectDTO {
  id?: number | string; code?: string; name: string
  assemblyName?: string
  displaySupplierName?: string; touchSupplierName?: string
  adaptModel?: string
  originalSize?: string; originalResolution?: string; projectLeaderId?: number
  sampleFactoryId?: number; outsourceFactoryId?: number
  startDate?: string; expectedEndDate?: string; actualEndDate?: string
  status?: string; remark?: string
}

export interface ProjectQueryParams {
  name?: string; status?: string; pageNum?: number; pageSize?: number
}

export interface BomVO {
  id?: number | string; projectId?: number; supplierId?: number; spec?: string; materialName: string
  materialId?: number; unit?: string; quantityPerSet?: number; lossRate?: number
  materialType?: string; remark?: string
}

export interface BomDTO { id?: number; parentId?: number; sortOrder?: number; materialId?: number; materialName: string; spec?: string; supplierId?: number; unit?: string; quantityPerSet?: number; lossRate?: number; materialType?: string; remark?: string }

export interface BugVO {
  id?: number | string; projectId?: number; code?: string; title: string
  severity?: string; bugType?: string; status?: string; description?: string
  foundBy?: number; assignedTo?: number; foundTime?: string; resolvedTime?: string
}

export interface BugDTO { id?: number | string; title: string; severity?: string; bugType?: string; status?: string; description?: string; assignedTo?: number }

export interface DrawingVO {
  id?: number | string; projectId?: number; docName: string; docType?: string
  fileUrl?: string; fileSize?: number; version?: string; uploadUserId?: number; createTime?: string
}

export function getProjectPage(params: ProjectQueryParams) {
  return request.get<unknown, PageResult<ProjectVO>>('/dev/project/page', { params })
}
export function getProject(id: number | string) { return request.get<unknown, ProjectVO>(`/dev/project/${id}`) }
export function addProject(data: ProjectDTO) { return request.post<unknown, void>('/dev/project', data) }
export function updateProject(data: ProjectDTO) { return request.put<unknown, void>('/dev/project', data) }
export function deleteProject(id: number | string) { return request.delete<unknown, void>(`/dev/project/${id}`) }
export function updateProjectStatus(id: number | string, status: string) { return request.put<unknown, void>(`/dev/project/${id}/status?status=${status}`) }

export function getProjectBom(projectId: number | string) { return request.get<unknown, BomVO[]>(`/dev/project/${projectId}/bom`) }
export function saveProjectBom(projectId: number | string, items: BomDTO[]) { return request.put<unknown, void>(`/dev/project/${projectId}/bom`, items) }

export function getProjectDrawings(projectId: number | string) { return request.get<unknown, DrawingVO[]>(`/dev/project/${projectId}/drawing`) }
export function addProjectDrawing(projectId: number | string, data: DrawingVO) { return request.post<unknown, void>(`/dev/project/${projectId}/drawing`, data) }
export function deleteProjectDrawing(projectId: number | string, id: number | string) { return request.delete<unknown, void>(`/dev/project/${projectId}/drawing/${id}`) }

export function getProjectBugs(projectId: number | string) { return request.get<unknown, BugVO[]>(`/dev/project/${projectId}/bug`) }
export function addProjectBug(projectId: number | string, data: BugDTO) { return request.post<unknown, void>(`/dev/project/${projectId}/bug`, data) }
export function updateProjectBug(projectId: number | string, data: BugDTO) { return request.put<unknown, void>(`/dev/project/${projectId}/bug/${data.id}`, data) }
export function deleteProjectBug(projectId: number | string, id: number | string) { return request.delete<unknown, void>(`/dev/project/${projectId}/bug/${id}`) }
