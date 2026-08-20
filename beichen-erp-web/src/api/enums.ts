import request from '@/utils/request'

/** 结单报表状态（对应 CloseReportStatus 枚举） */
export const CloseReportStatus = {
  DRAFT: 'DRAFT',       // 草稿
  FINISHED: 'FINISHED'  // 已结单
} as const

export const CloseReportStatusLabel: Record<string, string> = {
  [CloseReportStatus.DRAFT]: '草稿',
  [CloseReportStatus.FINISHED]: '已结单'
}

/** 委外加工单状态（对应 OutsourceOrderStatus 枚举） */
export const OutsourceOrderStatus = {
  PENDING: 'PENDING',
  PRODUCING: 'PRODUCING',
  FINISHED: 'FINISHED',
  CANCELLED: 'CANCELLED'
} as const

export const OutsourceOrderStatusLabel: Record<string, string> = {
  [OutsourceOrderStatus.PENDING]: '待审核',
  [OutsourceOrderStatus.PRODUCING]: '生产中',
  [OutsourceOrderStatus.FINISHED]: '已完成',
  [OutsourceOrderStatus.CANCELLED]: '已取消'
}

export const OutsourceOrderStatusTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
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

export const MaterialOrderStatusTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
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

export const ProjectStatusTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [ProjectStatus.IN_PROGRESS]: 'primary',
  [ProjectStatus.CLOSED]: 'success',
  [ProjectStatus.CANCELLED]: 'danger'
}

/** Bug状态（对应 BugStatus 枚举） */
export const BugStatus = {
  OPEN: 'OPEN',
  FIXING: 'FIXING',
  FIXED: 'FIXED',
  VERIFIED: 'VERIFIED',
  CLOSED: 'CLOSED'
} as const

export const BugStatusLabel: Record<string, string> = {
  [BugStatus.OPEN]: '待处理',
  [BugStatus.FIXING]: '处理中',
  [BugStatus.FIXED]: '已修复',
  [BugStatus.VERIFIED]: '已验证',
  [BugStatus.CLOSED]: '已关闭'
}

export const BugStatusTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [BugStatus.OPEN]: 'danger',
  [BugStatus.FIXING]: 'warning',
  [BugStatus.FIXED]: 'success',
  [BugStatus.VERIFIED]: 'primary',
  [BugStatus.CLOSED]: 'info'
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

/** 研发项目物料类型（对应 DevMaterialTypeEnum 枚举，存储值为中文标签） */
export const DevMaterialType = {
  BOARD: '基板',
  SCREEN: '屏幕',
  TEST_FIXTURE: '测试架',
  OTHER: '其他'
} as const

export const DevMaterialTypeLabel: Record<string, string> = {
  [DevMaterialType.BOARD]: '基板',
  [DevMaterialType.SCREEN]: '屏幕',
  [DevMaterialType.TEST_FIXTURE]: '测试架',
  [DevMaterialType.OTHER]: '其他'
}

/** 仓库类别（对应 Warehouse 实体 warehouse_category 字段） */
export const WarehouseCategory = {
  INVENTORY: 'INVENTORY', // 自有仓
  OUTSOURCE: 'OUTSOURCE'  // 委外仓
} as const

export const WarehouseCategoryLabel: Record<string, string> = {
  [WarehouseCategory.INVENTORY]: '自有仓库',
  [WarehouseCategory.OUTSOURCE]: '委外仓库'
}

/** 菜单类型（对应 sys_menu 的 menu_type 字段） */
export const MenuType = {
  CATALOG: 'catalog', // 目录
  MENU: 'menu'        // 菜单
} as const

export const MenuTypeLabel: Record<string, string> = {
  [MenuType.CATALOG]: '目录',
  [MenuType.MENU]: '菜单'
}

/** 研发物料存放位置类型（对应 DevMaterialPlaceTypeEnum，存储值为英文 code） */
export const MaterialPlaceType = {
  INVENTORY: 'INVENTORY', // 自有仓库
  OUTSOURCE: 'OUTSOURCE', // 委外仓库
  SUPPLIER: 'SUPPLIER',   // 供应商
  CUSTOMER: 'CUSTOMER',   // 客户
  TEXT: 'TEXT'            // 自定义文本
} as const

export const MaterialPlaceTypeLabel: Record<string, string> = {
  [MaterialPlaceType.INVENTORY]: '自有仓库',
  [MaterialPlaceType.OUTSOURCE]: '委外仓库',
  [MaterialPlaceType.SUPPLIER]: '供应商',
  [MaterialPlaceType.CUSTOMER]: '客户',
  [MaterialPlaceType.TEXT]: '自定义'
}

/** 仓库类型（对应 Warehouse.warehouseType 字段，存储值为中文） */
export const WarehouseType = {
  AUXILIARY: '辅料仓', // 辅料仓
  FINISHED: '成品仓',  // 成品仓
  DEFECT: '不良品仓'   // 不良品仓
} as const

/** 产品品质等级（对应 ProductQualityType 枚举，存储值为 A/B/C/DEFECT） */
export const ProductQualityType = {
  A: 'A',
  B: 'B',
  C: 'C',
  DEFECT: 'DEFECT'
} as const

export const ProductQualityTypeLabel: Record<string, string> = {
  [ProductQualityType.A]: 'A规',
  [ProductQualityType.B]: 'B规',
  [ProductQualityType.C]: 'C规',
  [ProductQualityType.DEFECT]: '不良品'
}

/** 备忘录状态（对应 MemoStatus 枚举） */
export const MemoStatus = {
  OPEN: 'OPEN',      // 未处理
  CLOSED: 'CLOSED'   // 已关闭
} as const

export const MemoStatusLabel: Record<string, string> = {
  [MemoStatus.OPEN]: '未处理',
  [MemoStatus.CLOSED]: '已关闭'
}

/** 结算状态（对应 SettlementStatus 枚举） */
export const SettlementStatus = {
  UNSETTLED: 'UNSETTLED', // 未结算
  PARTIAL: 'PARTIAL',     // 部分结算
  SETTLED: 'SETTLED',     // 已结算
  CANCELLED: 'CANCELLED'  // 已作废
} as const

export const SettlementStatusLabel: Record<string, string> = {
  [SettlementStatus.UNSETTLED]: '未结算',
  [SettlementStatus.PARTIAL]: '部分结算',
  [SettlementStatus.SETTLED]: '已结算',
  [SettlementStatus.CANCELLED]: '已作废'
}

export const SettlementStatusTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [SettlementStatus.UNSETTLED]: 'danger',
  [SettlementStatus.PARTIAL]: 'warning',
  [SettlementStatus.SETTLED]: 'success',
  [SettlementStatus.CANCELLED]: 'info'
}



