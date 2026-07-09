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

export interface StockTake {
  id?: number
  code?: string
  warehouseId?: number
  takeDate?: string
  status?: string
  remark?: string
}
export interface StockTakeItem extends BaseItem {
  bookQuantity?: number
  actualQuantity?: number
  profitLossQuantity?: number
}

export interface Transfer {
  id?: number
  code?: string
  fromWarehouseId?: number
  toWarehouseId?: number
  transferDate?: string
  status?: string
  remark?: string
}
export interface TransferItem extends BaseItem {
  quantity?: number
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

// 仓库
export function getWarehouseOptions() {
  return request.get('/inventory/warehouse/page', { params: { pageSize: 200 } })
}

// 库存查询与流水
export function getStockPage(params: any) {
  return request.get<unknown, PageResult<StockRow>>('/inventory/stock/page', { params })
}
export function getStockLog(params: any) {
  return request.get<unknown, PageResult<StockLogRow>>('/inventory/stock/log', { params })
}

// 盘点
export function getStockTakePage(params: any) {
  return request.get<unknown, PageResult<StockTake>>('/inventory/take/page', { params })
}
export function getStockTake(id: number) {
  return request.get<unknown, StockTake>(`/inventory/take/${id}`)
}
export function getStockTakeItems(id: number) {
  return request.get<unknown, StockTakeItem[]>(`/inventory/take/${id}/items`)
}
export function createStockTake(data: any) {
  return request.post<unknown, void>('/inventory/take', data)
}
export function updateStockTake(id: number, data: any) {
  return request.put<unknown, void>(`/inventory/take/${id}`, data)
}
export function auditStockTake(id: number) {
  return request.put<unknown, void>(`/inventory/take/${id}/audit`)
}
export function cancelStockTake(id: number) {
  return request.put<unknown, void>(`/inventory/take/${id}/cancel`)
}

// 调拨
export function getTransferPage(params: any) {
  return request.get<unknown, PageResult<Transfer>>('/inventory/transfer/page', { params })
}
export function getTransfer(id: number) {
  return request.get<unknown, Transfer>(`/inventory/transfer/${id}`)
}
export function getTransferItems(id: number) {
  return request.get<unknown, TransferItem[]>(`/inventory/transfer/${id}/items`)
}
export function createTransfer(data: any) {
  return request.post<unknown, void>('/inventory/transfer', data)
}
export function updateTransfer(id: number, data: any) {
  return request.put<unknown, void>(`/inventory/transfer/${id}`, data)
}
export function auditTransfer(id: number) {
  return request.put<unknown, void>(`/inventory/transfer/${id}/audit`)
}
export function cancelTransfer(id: number) {
  return request.put<unknown, void>(`/inventory/transfer/${id}/cancel`)
}

// 其他出入库
export function getOtherPage(params: any) {
  return request.get<unknown, PageResult<OtherIo>>('/inventory/other/page', { params })
}
export function getOther(id: number) {
  return request.get<unknown, OtherIo>(`/inventory/other/${id}`)
}
export function getOtherItems(id: number) {
  return request.get<unknown, OtherIoItem[]>(`/inventory/other/${id}/items`)
}
export function createOther(data: any) {
  return request.post<unknown, void>('/inventory/other', data)
}
export function updateOther(id: number, data: any) {
  return request.put<unknown, void>(`/inventory/other/${id}`, data)
}
export function auditOther(id: number) {
  return request.put<unknown, void>(`/inventory/other/${id}/audit`)
}
export function cancelOther(id: number) {
  return request.put<unknown, void>(`/inventory/other/${id}/cancel`)
}
