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
  dashboardTabs?: string[]
  /** 页面权限模式：ROLE=跟随角色（默认）/ CUSTOM=自定义 */
  menuMode?: string
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
  /** 首页业务 TAB 可见性（空=全部可见） */
  dashboardTabs?: string[]
}

/**
 * 首页业务 TAB 标识与名称（与 dashboard/index.vue 的 hasModule 键一致）。
 *
 * 2026-09-27：**label 与顺序都与侧栏一级目录对齐** —— `dev` 目录名是「研发管理」（原 label 写「项目研发」）、
 * `finance` 是「财务管理」（原 label 写「财务」）；顺序改为侧栏 sort 序。
 * 2026-10-02 用户口径「菜单物料仓库放在委外加工下面」：侧栏「物料仓库」移到「委外加工」之后 ⇒ 本表 `materialWarehouse`
 * 同步移到 `outsource` 之后（当前顺序：研发管理 → 委外加工 → 物料仓库 → 进货业务 → 销售业务 → 成品库存 → 财务管理）。
 * 本列表用于角色配置里的「首页 TAB 可见性」，顺序即首页页签顺序 ⇒ 与 dashboard/index.vue 的 pane 顺序**必须同步**
 * （`verify-dashboard-tab-menu.ps1` 会同时校验本文件与 pane 顺序）。
 */
export const DASHBOARD_TABS: { key: string; label: string }[] = [
  { key: 'dev', label: '研发管理' },
  { key: 'outsource', label: '委外加工' },
  { key: 'materialWarehouse', label: '物料仓库' },
  { key: 'purchase', label: '进货业务' },
  { key: 'sale', label: '销售业务' },
  { key: 'stock', label: '成品库存' },
  { key: 'finance', label: '财务管理' }
]

export function getMyDashboardTabs() {
  return request.get<string[]>('/system/dashboard-tabs/mine')
}

/** 按角色推导首页业务 TAB 默认勾选 */
export function getDefaultDashboardTabs(roleIds: (number | string)[]) {
  return request.get<string[]>('/system/user/default-dashboard-tabs', { params: { roleIds: roleIds.join(',') } })
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

/* ============================ 数据管理 / 清库（F8-26 整理 · 2026-09-30）============================
   这四个端点原先由页面**内联**调用（data-manage / clear-data），不经过 API 层。
   现已提供封装；`clearCompanyData` 已被两个页面采用。
   导出/导入（blob 下载、multipart 上传 + "缺表二次确认"预检流程）暂保留页面内联写法，
   待与预检流程一起复核后再切换（机械替换有回归风险）。 */
export function clearCompanyData(params: Record<string, any> = {}) {
  return request.post<any, any>('/system/clear-company-data', null, { params })
}

export function exportData() {
  return request.get<any, any>('/system/export-data')
}

export function precheckImportData(form: FormData) {
  return request.post<any, any>('/system/import-data/precheck', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function importData(form: FormData, confirmMissingTables?: boolean) {
  return request.post<any, any>('/system/import-data', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: confirmMissingTables ? { confirmMissingTables: true } : {}
  })
}

/* ============================ 用户页面权限（用户管理「页面权限」弹窗） ============================ */

export interface UserMenuPerm {
  /** ROLE=跟随角色（默认，清空自定义）/ CUSTOM=以用户级勾选为准（可加可减，不叠加角色） */
  menuMode: string
  /** CUSTOM 时的菜单 id 集合（保存时会自动补齐目录并保留「首页」） */
  menuIds?: (number | string)[]
  /** 只读返回：该用户当前角色菜单并集（前端切「自定义」时的初始勾选） */
  roleMenuIds?: (number | string)[]
}

export function getUserMenus(id: number | string) {
  return request.get<UserMenuPerm>(`/system/user/${id}/menus`)
}

export function saveUserMenus(id: number | string, data: UserMenuPerm) {
  return request.put<void>(`/system/user/${id}/menus`, data)
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
  /** 供货SKU：该供货商所供产品 SKU 的前缀（如 ABC ⇒ 产品 SKU 形如 ABC-000001）；空则产品走默认前缀 SKU- */
  supplySku?: string
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
  /** 供货SKU（可空，1-24 位字母/数字/短横线；保存时后端统一转大写并校验公司内唯一） */
  supplySku?: string
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

/* 供应商接口的实现已迁至 `@/api/supplier`（F8-26 整理 · 2026-09-30：路径是 /supplier/**，
   本就不属于"系统设置"）。这里保留 **re-export**，既有 `from '@/api/system'` 的页面无需改动；
   类型定义暂留本文件。 */
export {
  getSupplierPage, getSupplier, getSupplierProducts, addSupplier,
  updateSupplier, deleteSupplier, toggleSupplierStatus, saveSupplierProducts
} from './supplier'

/* ============================ 研发项目 API ============================ */

export interface ProjectVO {
  id?: number | string; code: string; name: string
  /** 产品名称（2026-09-21 由「总成名称」更名；DB 列 assembly_name → product_name） */
  productName?: string
  /** 规格：MATCHED原配 / MODIFIED改配（立项必填；与关联产品的 spec_type 联动） */
  specType?: string
  /** 立项页填写的产品SKU：新增时是"待创建产品的 SKU"，详情接口则回填**关联产品的当前 SKU**（不入库） */
  productSku?: string
  /** 关联产品ID（2026-09-21 补类型）：详情接口返回，用于判断产品SKU 是否可改 */
  productId?: number
  brandId?: number; brandName?: string
  displaySupplierName?: string; touchSupplierName?: string
  adaptModel?: string
  originalSize?: string; originalResolution?: string; projectLeaderId?: number
  originalDriveIc?: string; originalTouchIc?: string
  glassSize?: string; glassResolution?: string
  configDriveIcId?: number; configTouchIcId?: number; configCodeIcId?: number
  sampleFactoryId?: number; outsourceFactoryId?: number
  sampleFactoryName?: string; outsourceFactoryName?: string
  startDate?: string; expectedEndDate?: string; actualEndDate?: string
  status?: string; remark?: string; createTime?: string; updateTime?: string
}

export interface ProjectDTO {
  id?: number | string; code?: string; name: string
  /** 制单人（2026-09-23 用户口径：研发立项显示制单人；无审核流程 ⇒ 无审核人） */
  createByName?: string
  /** 产品名称（2026-09-21 由「总成名称」更名，用户："这样更贴切业务"） */
  productName?: string
  /** 规格：MATCHED原配 / MODIFIED改配（立项必填）。选原配时页面隐藏显示方案/触摸方案/改配信息 */
  specType?: string
  /** 产品SKU（2026-09-21 新增，需求 1）：默认 NS- 打头自动生成、允许修改；仅立项新增时生效 */
  productSku?: string
  brandId?: number; brandName?: string
  displaySupplierName?: string; touchSupplierName?: string
  adaptModel?: string
  originalSize?: string; originalResolution?: string; projectLeaderId?: number
  originalDriveIc?: string; originalTouchIc?: string
  glassSize?: string; glassResolution?: string
  configDriveIcId?: number; configTouchIcId?: number; configCodeIcId?: number
  sampleFactoryId?: number; outsourceFactoryId?: number
  sampleFactoryName?: string; outsourceFactoryName?: string
  startDate?: string; expectedEndDate?: string; actualEndDate?: string
  // F7-89（2026-09-19）：DTO 不再携带 status —— 项目状态由阶段推导、不允许由编辑页写回；
  // 后端 updateProject 也改为白名单字段更新。列表/详情展示继续用 ProjectVO.status。
  remark?: string
}

export interface ProjectQueryParams {
  name?: string; brandId?: number; status?: string; pageNum?: number; pageSize?: number
}

export interface BomVO {
  id?: number | string; projectId?: number; supplierId?: number; spec?: string; specification?: string; materialName: string
  unit?: string; quantityPerSet?: number; lossRate?: number; outsourceMaterialId?: number; materialTypeId?: number; quantity?: number; materialTypeName?: string
  remark?: string
}

export interface BomDTO { id?: number; parentId?: number; sortOrder?: number; materialName?: string; spec?: string; supplierId?: number; unit?: string; quantityPerSet?: number; lossRate?: number; outsourceMaterialId?: number; materialTypeId?: number; quantity?: number; remark?: string }

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

/* 研发项目接口的实现已迁至 `@/api/dev`（F8-26 整理 · 2026-09-30：路径是 /dev/project/**）。
   这里保留 **re-export**，dev/project/* 等 10+ 个既有页面无需改动；类型定义暂留本文件。 */
export {
  getProjectPage, getProject, addProject, checkProjectProductName, updateProject,
  deleteProject, updateProjectStatus, getProjectBom, saveProjectBom, getProjectBomSnapshots,
  getProjectDrawings, addProjectDrawing, deleteProjectDrawing,
  getProjectBugs, addProjectBug, updateProjectBug, deleteProjectBug
} from './dev'

