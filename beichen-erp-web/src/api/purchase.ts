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
  /** SKU（展示字段，后端回填） */
  sku?: string
  qualityType?: string
  quantity?: number
  unitPrice?: number
  amount?: number
  /**
   * 逐产品付费（2026-09-21 用户口径：采购退货也要有付费，方向 = **我方付给供货商**，且精确到产品）。
   * <p>金额挂在**明细行**（一行 = 一个产品）；单据级 = Σ(本字段)，由后端回写。</p>
   */
  chargeFlag?: number
  chargeType?: string
  chargeAmount?: number
  chargeReason?: string
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
  /**
   * 逐产品付费（2026-09-21）：单据级为**派生值** —— charge_amount = Σ明细行付费、charge_flag 由它推导；
   * charge_type 仅当各付费行类型一致时回填，否则为空（前端显示"多类型"）。方向：**我方付给供货商**（正向应付）。
   */
  chargeFlag?: number
  chargeType?: string
  chargeAmount?: number
  chargeReason?: string
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
/**
 * 来源采购单头（退货页「从采购单带入明细」反查供货商 / 退货仓库 / 单号）。
 * 期 3（2026-09-19 读隔离）：走退货页自身前缀（原直读 `/inventory/purchase/{id}` 需 purchase:order）。
 */
export function getPurchaseReturnSourceOrder(purchaseOrderId: number) {
  return request.get<PurchaseOrder>(`/inventory/purchase-return/source-order`, { params: { purchaseOrderId } })
}

// ---- 采购换货单 API（进货业务：把采购成品退回供货商换新；同品换货、强关联采购单；2026-09-18 新增） ----

export function getPurchaseExchangePage(params: any) {
  return request.get<PageResult<any>>('/inventory/purchase-exchange/page', { params })
}
export function getPurchaseExchange(id: number) {
  return request.get<any>(`/inventory/purchase-exchange/${id}`)
}
/**
 * 来源采购单头（采购单详情「换货」跳转时反查供货商 / 换货仓 / 单号）。
 * 期 3（2026-09-19 读隔离）：走换货页自身前缀（原直读 `/inventory/purchase/{id}` 需 purchase:order）。
 */
export function getPurchaseExchangeSourceOrder(purchaseOrderId: number) {
  return request.get<any>(`/inventory/purchase-exchange/source-order`, { params: { purchaseOrderId } })
}
export function createPurchaseExchange(data: any) {
  return request.post<any>('/inventory/purchase-exchange', data)
}
export function updatePurchaseExchange(id: number, data: any) {
  return request.put<any>('/inventory/purchase-exchange', { ...data, id })
}
export function auditPurchaseExchange(id: number) {
  return request.put<void>(`/inventory/purchase-exchange/${id}/audit`)
}
export function unAuditPurchaseExchange(id: number) {
  return request.put<void>(`/inventory/purchase-exchange/${id}/un-audit`)
}
export function cancelPurchaseExchange(id: number) {
  return request.put<void>(`/inventory/purchase-exchange/${id}/cancel`)
}
/** 查询采购单明细（含已购/已退/已换/可换数量，供换货带入；可换量 = 已购 − 已退 − 已换） */
export function getPurchaseExchangePurchaseOrderItems(purchaseOrderId: number) {
  return request.get<any[]>(`/inventory/purchase-exchange/purchase-order-items`, { params: { purchaseOrderId } })
}
/** 查询已审核采购单（供换货选择来源采购单） */
export function getPurchaseExchangePurchaseOrders(supplierId?: number, kw?: string) {
  return request.get<any[]>(`/inventory/purchase-exchange/purchase-orders`, { params: { supplierId, kw } })
}
