import request from '@/utils/request'

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/** 库存汇总行 */
export interface StockRow {
  id?: number
  warehouseId?: number
  warehouseName?: string
  productName?: string
  quantity?: number
}

/** 库存流水行 */
export interface StockLogRow {
  id?: number
  warehouseId?: number
  materialId?: number
  materialName?: string
  spec?: string
  changeType?: string
  changeQuantity?: number
  beforeQuantity?: number
  afterQuantity?: number
  relatedBillNo?: string
  relatedBillType?: string
  createTime?: string
}

export interface BaseItem {
  id?: number
  materialId?: number
  materialName?: string
  spec?: string
  unit?: string
  remark?: string
}



export interface OtherIo {
  id?: number
  code?: string
  warehouseId?: number
  ioType?: string
  ioDate?: string
  status?: string
  remark?: string
}
export interface OtherIoItem extends BaseItem {
  quantity?: number
}

// 库存查询与流水
export function getStockPage(params: any) {
  return request.get<PageResult<StockRow>>('/warehouse/stock/page', { params })
}
export function getStockLog(params: any) {
  return request.get<PageResult<StockLogRow>>('/warehouse/stock/log', { params })
}


// 其他出入库
export function getOtherPage(params: any) {
  return request.get<PageResult<OtherIo>>('/inventory/other/page', { params })
}
export function getOther(id: number) {
  return request.get<OtherIo>(`/inventory/other/${id}`)
}
export function getOtherItems(id: number) {
  return request.get<OtherIoItem[]>(`/inventory/other/${id}/items`)
}
export function createOther(data: any) {
  return request.post<void>('/inventory/other', data)
}
export function updateOther(id: number, data: any) {
  return request.put<void>(`/inventory/other/${id}`, data)
}
export function auditOther(id: number) {
  return request.put<void>(`/inventory/other/${id}/audit`)
}
export function cancelOther(id: number) {
  return request.put<void>(`/inventory/other/${id}/cancel`)
}

// 品质重分类
export interface ReclassifyItem {
  id?: number
  productId?: number
  productName?: string
  unit?: string
  fromQuality: string
  toQuality: string
  quantity?: number
  remark?: string
}

export function getReclassifyPage(params: any) {
  return request.get<PageResult<any>>('/inventory/reclassify/page', { params })
}
export function getReclassify(id: number) {
  return request.get<any>(`/inventory/reclassify/${id}`)
}
export function getReclassifyItems(id: number) {
  return request.get<ReclassifyItem[]>(`/inventory/reclassify/${id}/items`)
}
export function createReclassify(data: any) {
  return request.post<void>('/inventory/reclassify', data)
}
export function updateReclassify(id: number, data: any) {
  return request.put<void>(`/inventory/reclassify/${id}`, data)
}
export function auditReclassify(id: number) {
  return request.put<void>(`/inventory/reclassify/${id}/audit`)
}
/** 反审核（E2：已审核 → CANCELLED 并逆向库存；原由 /cancel 兼任，2026-09-12 拆出） */
export function unAuditReclassify(id: number) {
  return request.put<void>(`/inventory/reclassify/${id}/un-audit`)
}
/** 作废（仅草稿；E2 起 /cancel 不再承担反审核） */
export function cancelReclassify(id: number) {
  return request.put<void>(`/inventory/reclassify/${id}/cancel`)
}

// ==================== 退货整理 ====================
export interface ReturnSortItem {
  id?: number
  productId?: number
  productName?: string
  /** SKU（展示用；待整理清单按 productId 回填） */
  sku?: string
  unit?: string
  totalQuantity?: number
  qtyA?: number
  qtyB?: number
  qtyC?: number
  qtyDefect?: number
  remark?: string
  /**
   * 来源售后待整理批次ID（after_sale_pending.id）：提交后端，退货单/换货退回货品统一的追溯锚点。
   * 取代原 saleReturnItemId（后者只能追溯到销售退货单，换货退回的货品无对应记录）。
   */
  pendingId?: number
  /** [已废弃] 来源销售退货明细ID，追溯统一走 pendingId */
  saleReturnItemId?: number
  /** 本地字段：来源单据类型（SALE_RETURN 销售退货单 / SALE_EXCHANGE 销售换货单），展示用 */
  sourceType?: string
  /** 本地字段：来源单号（退货单号/换货单号），展示用，不提交 */
  sourceCode?: string
  /** 本地字段：来源单据业务日期，展示用，不提交 */
  sourceDate?: string
  /** 本地字段：该批次原始数量（展示用，不提交） */
  batchQuantity?: number
  /** 本地字段：已整理数量（展示用，不提交） */
  sortedQuantity?: number
  /** 本地字段：成品仓（待整理品质）可用库存，仅用于前端可用量校验，不提交后端 */
  available?: number
  /** 本地字段：实物在成品仓的停留天数（按最早待整理入库日期计算），用于超期预警 */
  stayDays?: number
}
export function getReturnSortPage(params: any) {
  return request.get<PageResult<any>>('/inventory/return-sort/page', { params })
}
export function getReturnSort(id: number) {
  return request.get<any>(`/inventory/return-sort/${id}`)
}
export function getReturnSortItems(id: number) {
  return request.get<ReturnSortItem[]>(`/inventory/return-sort/${id}/items`)
}
export function getReturnSortDefectStock(warehouseId: number) {
  return request.get<any[]>(`/inventory/return-sort/defect-stock`, { params: { warehouseId } })
}

/**
 * 待整理总览（2026-09-19 退货整理页优化）：跨**自有成品仓**列出待整理批次与其账实差额。
 * <p>行 status 三态：SORTABLE 可整理 · SHORTAGE 实物不足（账实不符，需盘库）·
 * CLEARED 已整理完（默认不返回，includeCleared=true 时返回）。</p>
 */
export interface ReturnSortPendingRow {
  pendingId?: number
  warehouseId?: number
  status?: 'SORTABLE' | 'SHORTAGE' | 'CLEARED' | string
  /** 部分可整理：实物少于批次剩余量 */
  partial?: boolean
  sourceType?: string
  sourceTypeLabel?: string
  sourceCode?: string
  sourceDate?: string
  /** 来源单据ID（sale_return.id / sale_exchange.id）—— 供总览「来源单据」单号点击跳来源详情 */
  sourceId?: number
  customerId?: number
  customerName?: string
  productId?: number
  sku?: string
  productName?: string
  unit?: string
  /** 本次可整理数量（FIFO 分配到的实物量） */
  quantity?: number
  totalQuantity?: number
  sortedQuantity?: number
  remainQuantity?: number
  stayDays?: number
  overdue?: boolean
}
export interface ReturnSortPendingOverview {
  asOf?: string
  stayAlertDays?: number
  warehouses?: { warehouseId?: number; warehouseName?: string; rows?: ReturnSortPendingRow[]; batchCount?: number; sortableCount?: number; shortageCount?: number; clearedCount?: number; sortableQuantity?: number; remainQuantity?: number; oldestStayDays?: number; overdueCount?: number }[]
  summary?: { warehouseCount?: number; batchCount?: number; sortableCount?: number; shortageCount?: number; clearedCount?: number; sortableQuantity?: number; remainQuantity?: number; overdueCount?: number }
}
export function getReturnSortPendingOverview(params?: { includeCleared?: boolean }) {
  return request.get<ReturnSortPendingOverview>('/inventory/return-sort/pending-overview', { params })
}

/**
 * 批量生成整理草稿：勾选多个来源批次，服务端按 (仓库, 客户) 各生成一张草稿，
 * 分选数量按 defaultQuality 整批预置（默认 A，可进编辑页再调整）。
 * **不要传 warehouseId** —— 源仓库由勾选的批次决定。
 */
export function batchCreateReturnSortDrafts(data: any) {
  return request.post<any>('/inventory/return-sort/batch-draft', data)
}
export function createReturnSort(data: any) {
  return request.post<void>('/inventory/return-sort', data)
}
export function updateReturnSort(id: number, data: any) {
  return request.put<void>(`/inventory/return-sort/${id}`, data)
}
export function auditReturnSort(id: number) {
  return request.put<void>(`/inventory/return-sort/${id}/audit`)
}
/** 反审核（E2：退货整理的 cancel 本就是反审核语义，调用统一走 canonical /un-audit；后端保留 /cancel 别名） */
export function cancelReturnSort(id: number) {
  return request.put<void>(`/inventory/return-sort/${id}/un-audit`)
}
export function deleteReturnSort(id: number) {
  return request.delete<void>(`/inventory/return-sort/${id}`)
}

// ==================== 库存盘点（每月每仓一次） ====================
export interface StockTake { id?: number; takeNo?: string; warehouseId?: number; warehouseName?: string; period?: string; takeDate?: string; status?: string; remark?: string; itemCount?: number; diffCount?: number; diffSum?: number }
export interface StockTakeItem { id?: number; takeId?: number; productId?: number; productName?: string; sku?: string; materialId?: number; materialName?: string; qualityType?: string; unit?: string; bookQuantity?: number; actualQuantity?: number; diffQuantity?: number; remark?: string }
/** 仓库盘点看板行：当月是否已盘点 + 超期天数 + 上次盘点日期 */
export interface StockTakeStatus { warehouseId?: number; warehouseName?: string; warehouseType?: string; warehouseCategory?: string; period?: string; taken?: boolean; lastTakeDate?: string; dueDate?: string; overdueDays?: number; remind?: boolean }
/**
 * 各仓库当月盘点状态与超期天数（**仓库管理页**「本月盘点 / 上次盘点」两列；传 scope=PRODUCT|MATERIAL 过滤范围）。
 * 期 2（2026-09-19 读隔离）：改走 `/warehouse` 前缀（原直读 `/inventory/stock-take/status`，需 stock:stock-take
 * ⇒ 只被授予 stock:warehouse 的用户会 403）。后端复用盘点模块的同一查询，口径不变。
 */
export function getWarehouseTakeStatus(params?: any) {
  return request.get<StockTakeStatus[]>('/warehouse/stock-take-status', { params })
}
export function getStockTakePage(params?: any) {
  return request.get<PageResult<StockTake>>('/inventory/stock-take/page', { params })
}
export function getStockTakeItems(id: number) {
  return request.get<StockTakeItem[]>(`/inventory/stock-take/${id}/items`)
}
export function createStockTake(data: any) {
  return request.post<StockTake>('/inventory/stock-take', data)
}
export function saveStockTakeItems(id: number, items: StockTakeItem[]) {
  return request.put<void>(`/inventory/stock-take/${id}/items`, items)
}
export function auditStockTake(id: number) {
  return request.put<void>(`/inventory/stock-take/${id}/audit`)
}
export function unAuditStockTake(id: number) {
  return request.put<void>(`/inventory/stock-take/${id}/un-audit`)
}
export function cancelStockTake(id: number) {
  return request.post<void>(`/inventory/stock-take/${id}/cancel`)
}

