import request from '@/utils/request'
import type { PageResult } from '@/api/product'

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
  return request.get<PageResult<UserVO>>('/system/user/page', { params })
}

export function getUser(id: number | string) {
  return request.get<UserVO>(`/system/user/${id}`)
}

export function addUser(data: UserDTO) {
  return request.post<void>('/system/user', data)
}

export function updateUser(data: UserDTO) {
  return request.put<void>('/system/user', data)
}

export function deleteUser(id: number | string) {
  return request.delete<void>(`/system/user/${id}`)
}

export function resetPassword(data: ResetPasswordParams) {
  return request.put<void>('/system/user/reset-password', data)
}

export function toggleUserStatus(id: number | string, status: number) {
  return request.put<void>(`/system/user/${id}/status`, { status })
}

/* ============================ 角色管理 API ============================ */

export function getRolePage(params: RoleQueryParams) {
  return request.get<PageResult<Role>>('/system/role/page', { params })
}

export function getEnabledRoles() {
  return request.get<Role[]>('/system/role/enabled')
}

export function addRole(data: RoleDTO) {
  return request.post<void>('/system/role', data)
}

export function updateRole(data: RoleDTO) {
  return request.put<void>('/system/role', data)
}

export function deleteRole(id: number | string) {
  return request.delete<void>(`/system/role/${id}`)
}

/* ============================ 菜单管理 API ============================ */

export function getMenuTree() {
  return request.get<MenuVO[]>('/system/menu/tree')
}

export function getUserMenuTree() {
  return request.get<MenuVO[]>('/system/menu/tree/user')
}

export function addMenu(data: MenuDTO) {
  return request.post<void>('/system/menu', data)
}

export function updateMenu(data: MenuDTO) {
  return request.put<void>('/system/menu', data)
}

export function deleteMenu(id: number | string) {
  return request.delete<void>(`/system/menu/${id}`)
}

/* ============================ 角色菜单 API ============================ */

export function getRoleMenus(roleId: number | string) {
  return request.get<number[]>(`/system/role/${roleId}/menus`)
}

export function saveRoleMenus(roleId: number | string, menuIds: number[]) {
  return request.put<void>(`/system/role/${roleId}/menus`, menuIds)
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
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface SupplierDTO {
  id?: number | string
  code: string
  name: string
  supplierType: string
  typeCodes?: string[]
  contact?: string
  phone?: string
  address?: string
  status: number
  relatedSupplierId?: number | string
  creditPeriodMonths?: number
  creditPeriod?: number
  brand?: string
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
  productId?: number | string
  productName?: string
  spec?: string
  unit?: string
  unitPrice?: number
  remark?: string
}

export interface SupplierProductDTO {
  productId?: number | string
  unitPrice?: number
  remark?: string
}

export function getSupplierPage(params: SupplierQueryParams) {
  return request.get<PageResult<SupplierVO>>('/supplier/page', { params })
}

export function getSupplier(id: number | string) {
  return request.get<SupplierVO>(`/supplier/${id}`)
}

export function getSupplierProducts(id: number | string) {
  return request.get<SupplierProductVO[]>(`/supplier/${id}/products`)
}

export function addSupplier(data: SupplierDTO) {
  return request.post<void>('/supplier', data)
}

export function updateSupplier(data: SupplierDTO) {
  return request.put<void>('/supplier', data)
}

export function deleteSupplier(id: number | string) {
  return request.delete<void>(`/supplier/${id}`)
}

export function toggleSupplierStatus(id: number | string) {
  return request.put<void>(`/supplier/${id}/status`)
}

export function saveSupplierProducts(id: number | string, products: SupplierProductDTO[]) {
  return request.put<void>(`/supplier/${id}/products`, products)
}

/* ============================ 研发项目 API ============================ */

export interface ProjectVO {
  id?: number | string; code: string; name: string
  assemblyName?: string
  displaySupplierName?: string; touchSupplierName?: string
  adaptModel?: string
  originalSize?: string; originalResolution?: string; projectLeaderId?: number
  sampleFactoryId?: number; outsourceFactoryId?: number
  sampleFactoryName?: string; outsourceFactoryName?: string
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
  sampleFactoryName?: string; outsourceFactoryName?: string
  startDate?: string; expectedEndDate?: string; actualEndDate?: string
  status?: string; remark?: string
}

export interface ProjectQueryParams {
  name?: string; status?: string; pageNum?: number; pageSize?: number
}

export interface BomVO {
  id?: number | string; projectId?: number; supplierId?: number; spec?: string; specification?: string; materialName: string
  unit?: string; quantityPerSet?: number; lossRate?: number; outsourceMaterialId?: number; bomTypeId?: number; quantity?: number; bomTypeName?: string
  remark?: string
}

export interface BomDTO { id?: number; parentId?: number; sortOrder?: number; materialName?: string; spec?: string; supplierId?: number; unit?: string; quantityPerSet?: number; lossRate?: number; outsourceMaterialId?: number; bomTypeId?: number; quantity?: number; remark?: string }

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
  return request.get<PageResult<ProjectVO>>('/dev/project/page', { params })
}
export function getProject(id: number | string) { return request.get<ProjectVO>(`/dev/project/${id}`) }
export function addProject(data: ProjectDTO, linkExistingProductId?: number | string) { return request.post<void>('/dev/project', data, { params: linkExistingProductId ? { linkExistingProductId } : {} }) }
export function checkProjectAssembly(name: string) { return request.get<{ exists: boolean; productId?: number; productName?: string }>('/dev/project/check-assembly', { params: { name } }) }
export function updateProject(data: ProjectDTO) { return request.put<void>('/dev/project', data) }
export function deleteProject(id: number | string) { return request.delete<void>(`/dev/project/${id}`) }
export function updateProjectStatus(id: number | string, status: string) { return request.put<void>(`/dev/project/${id}/status?status=${status}`) }

export function getProjectBom(projectId: number | string) { return request.get<BomVO[]>(`/dev/project/${projectId}/bom`) }
export function saveProjectBom(projectId: number | string, items: BomDTO[]) { return request.post<void>(`/dev/project/${projectId}/bom/batch`, items) }

export function getProjectDrawings(projectId: number | string) { return request.get<DrawingVO[]>(`/dev/project/${projectId}/drawing`) }
export function addProjectDrawing(projectId: number | string, data: DrawingVO) { return request.post<void>(`/dev/project/${projectId}/drawing`, data) }
export function deleteProjectDrawing(projectId: number | string, id: number | string) { return request.delete<void>(`/dev/project/${projectId}/drawing/${id}`) }

export function getProjectBugs(projectId: number | string) { return request.get<BugVO[]>(`/dev/project/${projectId}/bug`) }
export function addProjectBug(projectId: number | string, data: BugDTO) { return request.post<void>(`/dev/project/${projectId}/bug`, data) }
export function updateProjectBug(projectId: number | string, data: BugDTO) { return request.put<void>(`/dev/project/${projectId}/bug/${data.id}`, data) }
export function deleteProjectBug(projectId: number | string, id: number | string) { return request.delete<void>(`/dev/project/${projectId}/bug/${id}`) }

