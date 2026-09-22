import request from '@/utils/request'

export interface SaleOrderItem {
  id?: number
  orderId?: number
  productId?: number
  productName?: string
  /** SKU（展示用；后端按 productId 回填，新增行未选产品时为空） */
  sku?: string
  unit?: string
  qualityType?: string
  quantity?: number
  unitPrice?: number
  amount?: number
  remark?: string
}

export interface SaleOrder {
  /** 制单人 / 审核人（2026-09-23 单据详情口径：详情页显示这两项） */
  createByName?: string
  auditorName?: string
  id?: number
  code?: string
  customerId?: number
  customerName?: string
  warehouseId?: number
  orderDate?: string
  status?: string
  taxIncluded?: number
  /** 结算方式（2026-09-18 按单记）：CREDIT 账期（只挂应收）/ CASH 现金（**立即到账**：审核销售单时系统自动生成**已审核**收款单并完成核销） */
  settleType?: string
  /** 结算账户ID（现金结算时必填） */
  settleAccountId?: number
  /** 结算账户名（后端实时回显，不落库） */
  settleAccountName?: string
  taxRate?: number
  taxAmount?: number
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
  qualityType?: string
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
  return request.get<PageResult<SaleOrder>>('/inventory/sale/page', { params })
}
export function getSaleOrder(id: number) {
  return request.get<SaleOrder>(`/inventory/sale/${id}`)
}
export function getSaleOrderItems(id: number) {
  return request.get<SaleOrderItem[]>(`/inventory/sale/${id}/items`)
}
/** 标记销售单列表需刷新（详情页数据变动后置位，列表页 onActivated 消费） */
export const SALE_ORDER_DIRTY_KEY = 'saleOrderListDirty'
export function createSaleOrder(data: any) {
  return request.post<void>('/inventory/sale', data)
}
export function updateSaleOrder(id: number, data: any) {
  return request.put<void>(`/inventory/sale/${id}`, data)
}
export function auditSaleOrder(id: number) {
  return request.put<void>(`/inventory/sale/${id}/audit`)
}
export function cancelSaleOrder(id: number) {
  return request.put<void>(`/inventory/sale/${id}/cancel`)
}
export function unAuditSaleOrder(id: number) {
  return request.put<void>(`/inventory/sale/${id}/un-audit`)
}

/** 库存检查：传入 warehouseId + items，返回各产品库存对比 */
export function checkSaleOrderStock(data: { warehouseId?: number; items: SaleOrderItem[] }) {
  return request.post<{ productName: string; unit: string; required: number; available: number; shortage: number; sufficient: boolean }[]>(
    '/inventory/sale/check-stock', data
  )
}

export function getSaleOutboundPage(params: any) {
  return request.get<PageResult<SaleOutbound>>('/inventory/outbound/page', { params })
}
// ==================== 销售退货单（售后：只退不换） ====================
/** 销售退货单状态：DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废（与后端 DocStatus 一致） */
export const SaleReturnStatus = {
  DRAFT: 'DRAFT',
  AUDITED: 'AUDITED',
  CANCELLED: 'CANCELLED',
} as const

export const SaleReturnStatusLabel: Record<string, string> = {
  [SaleReturnStatus.DRAFT]: '草稿',
  [SaleReturnStatus.AUDITED]: '已审核',
  [SaleReturnStatus.CANCELLED]: '已作废',
}

export interface SaleReturnItem {
  id?: number
  returnId?: number
  /** 关联销售单明细ID（用于追溯） */
  saleOrderItemId?: number
  productId?: number
  productName?: string
  /** SKU（非表字段，明细接口按 productId 回填） */
  sku?: string
  /** 品质等级：销售退货固定为 DEFECT(不良品) */
  qualityType?: string
  quantity?: number
  unitPrice?: number
  amount?: number
  /**
   * 逐产品收费（2026-09-21 用户口径：收费精确到产品）。
   * <p>金额挂在**明细行**上（一行 = 一个产品）；单据级 amount = Σ(本字段)，由后端回写。
   * 方向：**向客户收取** ⇒ 审核生成一条正向应收（单号 -FEE）。</p>
   */
  chargeFlag?: number
  chargeType?: string
  chargeAmount?: number
  chargeReason?: string
  remark?: string
}

export interface SaleReturn {
  id?: number
  code?: string
  customerId?: number
  customerName?: string
  warehouseId?: number
  /** 关联销售单ID（可选） */
  saleOrderId?: number
  saleOrderCode?: string
  returnDate?: string
  status?: number
  totalAmount?: number
  /** ⚠️ 已停用（2026-09-21 用户口径：现在没有折损收款了）：字段保留仅为兼容旧数据/旧接口，界面不再展示、也不再生效 */
  lossAmount?: number
  /** 是否收费：0否 1是（收费则审核生成一条正向应收，单号后缀 -FEE） */
  chargeFlag?: number
  chargeType?: string
  chargeAmount?: number
  chargeReason?: string
  remark?: string
  auditorId?: number
  auditorName?: string
  auditTime?: string
  createTime?: string
  /** 制单人（2026-09-23 单据详情口径：显示「制单人 + 审核人」） */
  createByName?: string
  items?: SaleReturnItem[]
}

export function getSaleReturnPage(params: any) {
  return request.get<PageResult<SaleReturn>>('/sale/return/page', { params })
}
export function getSaleReturn(id: number) {
  return request.get<SaleReturn>(`/sale/return/${id}`)
}
export function getSaleReturnItems(id: number) {
  return request.get<SaleReturnItem[]>(`/sale/return/${id}/items`)
}
export function createSaleReturn(data: any) {
  return request.post<void>('/sale/return', data)
}
export function updateSaleReturn(id: number, data: any) {
  return request.put<void>(`/sale/return/${id}`, data)
}
export function auditSaleReturn(id: number) {
  return request.put<void>(`/sale/return/${id}/audit`)
}
export function unAuditSaleReturn(id: number) {
  return request.put<void>(`/sale/return/${id}/un-audit`)
}
export function cancelSaleReturn(id: number) {
  return request.put<void>(`/sale/return/${id}/cancel`)
}
export function deleteSaleReturn(id: number) {
  return request.delete<void>(`/sale/return/${id}`)
}
/** 查询某客户已审核的销售单（供退货关联选择） */
export function getSaleReturnSaleOrders(customerId: number) {
  return request.get<any[]>('/sale/return/sale-orders', { params: { customerId } })
}
/**
 * 来源销售单头（退货页带 ?saleOrderId= 跳转时反查客户/单号）。
 * 期 3（2026-09-19 读隔离）：走退货页自身前缀（原直读 `/inventory/sale/{id}` 需 sale:order）。
 */
export function getSaleReturnSourceOrder(saleOrderId: number) {
  return request.get<any>(`/sale/return/source-order`, { params: { saleOrderId } })
}
/** 查询销售单明细（含可退数量，供退货带入） */
export function getSaleReturnSaleOrderItems(saleOrderId: number) {
  return request.get<any[]>(`/sale/return/sale-order-items`, { params: { saleOrderId } })
}

// ==================== 销售换货单（同品换货，强关联销售单） ====================
export function getSaleExchangePage(params: any) {
  return request.get<PageResult<any>>('/sale/exchange/page', { params })
}
export function getSaleExchange(id: number) {
  return request.get<any>(`/sale/exchange/${id}`)
}
export function createSaleExchange(data: any) {
  return request.post<any>('/sale/exchange', data)
}
export function updateSaleExchange(id: number, data: any) {
  return request.put<any>(`/sale/exchange`, { ...data, id })
}
export function auditSaleExchange(id: number) {
  return request.put<void>(`/sale/exchange/${id}/audit`)
}
export function unAuditSaleExchange(id: number) {
  return request.put<void>(`/sale/exchange/${id}/un-audit`)
}
export function cancelSaleExchange(id: number) {
  return request.put<void>(`/sale/exchange/${id}/cancel`)
}
/** 查询销售单明细（含已售/已退/已换/可换数量，供换货带入；可换量 = 已售 − 已退 − 已换） */
export function getSaleExchangeSaleOrderItems(saleOrderId: number) {
  return request.get<any[]>(`/sale/exchange/sale-order-items`, { params: { saleOrderId } })
}
/**
 * 查询某客户已审核的销售单（换货页「来源销售单」下拉）。
 * 期 3（2026-09-19 读隔离）：走换货页自身前缀（原直读退货页 `/sale/return/sale-orders` 需 sale:return）。
 */
export function getSaleExchangeSaleOrders(customerId: number) {
  return request.get<any[]>('/sale/exchange/sale-orders', { params: { customerId } })
}
/**
 * 来源销售单头（换货页带 ?saleOrderId= 跳转时反查客户/单号/出库仓）。
 * 期 3（2026-09-19 读隔离）：走换货页自身前缀（原直读 `/inventory/sale/{id}` 需 sale:order）。
 */
export function getSaleExchangeSourceOrder(saleOrderId: number) {
  return request.get<any>(`/sale/exchange/source-order`, { params: { saleOrderId } })
}
export function getSaleOutboundItems(id: number) {
  return request.get<SaleOutboundItem[]>(`/inventory/outbound/${id}/items`)
}
export function createSaleOutbound(data: any) {
  return request.post<void>('/inventory/outbound', data)
}
export function updateSaleOutbound(id: number, data: any) {
  return request.put<void>(`/inventory/outbound/${id}`, data)
}
export function auditSaleOutbound(id: number) {
  return request.put<void>(`/inventory/outbound/${id}/audit`)
}
export function cancelSaleOutbound(id: number) {
  return request.put<void>(`/inventory/outbound/${id}/cancel`)
}
export function unAuditSaleOutbound(id: number) {
  return request.put<void>(`/inventory/outbound/${id}/un-audit`)
}

