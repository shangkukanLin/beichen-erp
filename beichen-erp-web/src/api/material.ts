import request from '@/utils/request'

/** 委外加工单状态（对应 OutsourceOrderStatus 枚举） */
export const OutsourceOrderStatus = {
  PENDING: 'PENDING',
  PRODUCING: 'PRODUCING',
  FINISHED: 'FINISHED',
  CANCELLED: 'CANCELLED'
} as const

export const OutsourceOrderStatusLabel: Record<string, string> = {
  [OutsourceOrderStatus.PENDING]: '待确认',
  [OutsourceOrderStatus.PRODUCING]: '生产中',
  [OutsourceOrderStatus.FINISHED]: '已完成',
  [OutsourceOrderStatus.CANCELLED]: '已取消'
}

export const OutsourceOrderStatusTag: Record<string, string> = {
  [OutsourceOrderStatus.PENDING]: 'info',
  [OutsourceOrderStatus.PRODUCING]: 'primary',
  [OutsourceOrderStatus.FINISHED]: 'success',
  [OutsourceOrderStatus.CANCELLED]: 'danger'
}

/** 物料订单状态（对应 MaterialOrderStatus 枚举） */
export const MaterialOrderStatus = {
  PENDING: 'PENDING',
  RECEIVING: 'RECEIVING',
  FINISHED: 'FINISHED',
  CANCELLED: 'CANCELLED'
} as const

export const MaterialOrderStatusLabel: Record<string, string> = {
  [MaterialOrderStatus.PENDING]: '待确认',
  [MaterialOrderStatus.RECEIVING]: '收货中',
  [MaterialOrderStatus.FINISHED]: '已完成',
  [MaterialOrderStatus.CANCELLED]: '已取消'
}

export const MaterialOrderStatusTag: Record<string, string> = {
  [MaterialOrderStatus.PENDING]: 'info',
  [MaterialOrderStatus.RECEIVING]: 'warning',
  [MaterialOrderStatus.FINISHED]: 'success',
  [MaterialOrderStatus.CANCELLED]: 'danger'
}

/** 发货/退货状态（对应 DeliveryStatus 枚举） */
export const DeliveryStatus = {
  CONFIRMED: 'CONFIRMED',
  CANCELLED: 'CANCELLED'
} as const

export const DeliveryStatusLabel: Record<string, string> = {
  [DeliveryStatus.CONFIRMED]: '已确认',
  [DeliveryStatus.CANCELLED]: '已取消'
}

/** 物料订单类型（对应 OrderType 枚举） */
export const OrderType = {
  PURCHASE: 'PURCHASE',
  OUTSOURCE: 'OUTSOURCE'
} as const

export const OrderTypeLabel: Record<string, string> = {
  [OrderType.PURCHASE]: '采购',
  [OrderType.OUTSOURCE]: '委外'
}

/** 退不良处理方式（对应 DefectHandleType 枚举） */
export const DefectHandleType = {
  REPAIR_RETURN: 'REPAIR_RETURN',
  CASH_REFUND: 'CASH_REFUND'
} as const

export const DefectHandleTypeLabel: Record<string, string> = {
  [DefectHandleType.REPAIR_RETURN]: '维修返还',
  [DefectHandleType.CASH_REFUND]: '折现退款'
}

export interface MaterialQueryParams {
  pageNum?: number
  pageSize?: number
  name?: string
  status?: string
}

export interface Material {
  id?: number | string
  code?: string
  name: string
  brandId?: number
  category?: string
  spec?: string
  unit?: string
  safetyStock?: number
  currentStock?: number
  status: string
  remark?: string
}

/** 物料简要信息 */
export interface MaterialBrief {
  id?: number | string
  code: string
  name: string
  spec?: string
  unit?: string
  category?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export function getMaterialPage(params: MaterialQueryParams) {
  return request.get<unknown, PageResult<Material>>('/product/page', { params })
}

export function getMaterial(id: number | string) {
  return request.get<unknown, Material>(`/product/${id}`)
}

export function addMaterial(data: Material) {
  return request.post<unknown, void>('/material', data)
}

export function updateMaterial(data: Material) {
  return request.put<unknown, void>('/material', data)
}

export function deleteMaterial(id: number | string) {
  return request.delete<unknown, void>(`/product/${id}`)
}


