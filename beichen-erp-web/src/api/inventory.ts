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
