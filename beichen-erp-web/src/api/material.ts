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

/** 出入库类型（对应 IoType 枚举） */
export const IoType = {
  IN: 'IN',
  OUT: 'OUT'
} as const

export const IoTypeLabel: Record<string, string> = {
  [IoType.IN]: '入库',
  [IoType.OUT]: '出库'
}

/** 收发类型（对应 DeliveryType 枚举） */
export const DeliveryType = {
  DELIVERY: 'DELIVERY',
  RECEIVE: 'RECEIVE',
  TRANSFER: 'TRANSFER',
  RETURN: 'RETURN',
  DEFECT_RETURN: 'DEFECT_RETURN'
} as const

export const DeliveryTypeLabel: Record<string, string> = {
  [DeliveryType.DELIVERY]: '发料',
  [DeliveryType.RECEIVE]: '收料',
  [DeliveryType.TRANSFER]: '调拨',
  [DeliveryType.RETURN]: '退料',
  [DeliveryType.DEFECT_RETURN]: '退不良'
}

/** 财务账单类型（对应 BillType 枚举） */
export const BillType = {
  RECEIVABLE: 'RECEIVABLE',
  PAYABLE: 'PAYABLE'
} as const

export const BillTypeLabel: Record<string, string> = {
  [BillType.RECEIVABLE]: '应收',
  [BillType.PAYABLE]: '应付'
}

/** 项目时间线状态（对应 TimelineStatus 枚举） */
export const TimelineStatus = {
  NOT_STARTED: 'NOT_STARTED',
  IN_PROGRESS: 'IN_PROGRESS',
  FINISHED: 'FINISHED',
  SKIPPED: 'SKIPPED'
} as const

export const TimelineStatusLabel: Record<string, string> = {
  [TimelineStatus.NOT_STARTED]: '未开始',
  [TimelineStatus.IN_PROGRESS]: '进行中',
  [TimelineStatus.FINISHED]: '已完成',
  [TimelineStatus.SKIPPED]: '已跳过'
}

/** 研发项目状态（对应 ProjectStatus 枚举） */
export const ProjectStatus = {
  IN_PROGRESS: 'IN_PROGRESS',
  CLOSED: 'CLOSED',
  CANCELLED: 'CANCELLED'
} as const

export const ProjectStatusLabel: Record<string, string> = {
  [ProjectStatus.IN_PROGRESS]: '进行中',
  [ProjectStatus.CLOSED]: '已结项',
  [ProjectStatus.CANCELLED]: '已取消'
}

export const ProjectStatusTag: Record<string, string> = {
  [ProjectStatus.IN_PROGRESS]: 'primary',
  [ProjectStatus.CLOSED]: 'success',
  [ProjectStatus.CANCELLED]: 'danger'
}

/** Bug严重程度（对应 SeverityType 枚举） */
export const SeverityType = {
  CRITICAL: 'CRITICAL',
  MAJOR: 'MAJOR',
  NORMAL: 'NORMAL',
  MINOR: 'MINOR'
} as const

export const SeverityTypeLabel: Record<string, string> = {
  [SeverityType.CRITICAL]: '致命',
  [SeverityType.MAJOR]: '严重',
  [SeverityType.NORMAL]: '一般',
  [SeverityType.MINOR]: '轻微'
}

/** Bug类型（对应 BugTypeEnum 枚举） */
export const BugTypeEnum = {
  DISPLAY: 'DISPLAY',
  TOUCH: 'TOUCH',
  STRUCTURE: 'STRUCTURE'
} as const

export const BugTypeEnumLabel: Record<string, string> = {
  [BugTypeEnum.DISPLAY]: '显示',
  [BugTypeEnum.TOUCH]: '触摸',
  [BugTypeEnum.STRUCTURE]: '结构'
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

/** 品质类型（对应 QualityType 枚举） */
export const QualityType = {
  GOOD: 'GOOD',
  DEFECT: 'DEFECT'
} as const

export const QualityTypeLabel: Record<string, string> = {
  [QualityType.GOOD]: '良品',
  [QualityType.DEFECT]: '不良品'
}

/** 交货记录状态（对应 DeliveryItemStatus 枚举） */
export const DeliveryItemStatus = {
  NORMAL: 'NORMAL',
  REVERSED: 'REVERSED'
} as const

export const DeliveryItemStatusLabel: Record<string, string> = {
  [DeliveryItemStatus.NORMAL]: '正常',
  [DeliveryItemStatus.REVERSED]: '已回滚'
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


