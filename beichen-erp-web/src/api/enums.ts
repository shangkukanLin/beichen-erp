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
  [OutsourceOrderStatus.CANCELLED]: '已作废'
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
  [MaterialOrderStatus.PENDING]: '待审核',
  [MaterialOrderStatus.RECEIVING]: '收货中',
  [MaterialOrderStatus.FINISHED]: '已完成',
  [MaterialOrderStatus.CANCELLED]: '已作废'
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

/** 项目项目阶段状态（对应 PhaseStatus 枚举） */
export const PhaseStatus = {
  NOT_STARTED: 'NOT_STARTED',
  IN_PROGRESS: 'IN_PROGRESS',
  FINISHED: 'FINISHED',
  SKIPPED: 'SKIPPED'
} as const

export const PhaseStatusLabel: Record<string, string> = {
  [PhaseStatus.NOT_STARTED]: '未开始',
  [PhaseStatus.IN_PROGRESS]: '进行中',
  [PhaseStatus.FINISHED]: '已完成',
  [PhaseStatus.SKIPPED]: '已跳过'
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

/** 应付/应收来源单据类型（对应后端 SourceBillType 枚举） */
export const SourceBillTypeLabel: Record<string, string> = {
  SALE_OUTBOUND: '销售出库', SALE_ORDER: '销售单',
  PURCHASE_ORDER: '采购单', PURCHASE_INBOUND: '采购入库',
  PURCHASE_RETURN: '采购退货单', SALE_RETURN: '销售退货',
  OUTSOURCE_DELIVERY: '委外加工交货', OUTSOURCE_MATERIAL_DELIVERY: '委外物料收发',
  OUTSOURCE_RETURN: '委外退料', OUTSOURCE_MATERIAL_RETURN: '委外物料退货',
  OUTSOURCE_EXCESS_LOSS: '委外超损'
}
export function sourceBillTypeLabel(code?: string) { return code ? (SourceBillTypeLabel[code] || code) : '' }

/** 应付/应收来源单据类型 → 详情页路由前缀（用于来源单号点击跳转） */
export const SourceBillDetailRoute: Record<string, string> = {
  PURCHASE_ORDER: '/inventory/purchase/detail',
  PURCHASE_RETURN: '/inventory/purchase-return/detail',
  OUTSOURCE_DELIVERY: '/outsource/order/detail',
  OUTSOURCE_EXCESS_LOSS: '/outsource/order/detail',
  OUTSOURCE_MATERIAL_DELIVERY: '/outsource/delivery/detail',
  OUTSOURCE_MATERIAL_RETURN: '/outsource/material-return/detail',
  OUTSOURCE_RETURN: '/outsource/return-order/detail',
  SALE_ORDER: '/sale/order',
  SALE_OUTBOUND: '/sale/outbound',
  SALE_RETURN: '/sale/return/detail'
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

/** 仓库类型（对应 Warehouse.warehouseType 字段，存储值为中文，须与仓库创建选项一致） */
export const WarehouseType = {
  AUXILIARY: '辅料仓',  // 辅料仓
  FINISHED: '成品仓',   // 成品仓
  DEFECT: '不良仓',     // 不良仓
  AFTER_SALE: '售后仓'  // 售后仓
} as const

/** 产品品质等级（对应 ProductQualityType 枚举，存储值为 A/B/C/DEFECT/PENDING） */
export const ProductQualityType = {
  A: 'A',
  B: 'B',
  C: 'C',
  DEFECT: 'DEFECT',
  PENDING: 'PENDING'
} as const

export const ProductQualityTypeLabel: Record<string, string> = {
  [ProductQualityType.A]: 'A规',
  [ProductQualityType.B]: 'B规',
  [ProductQualityType.C]: 'C规',
  [ProductQualityType.DEFECT]: '不良品',
  [ProductQualityType.PENDING]: '待分类'
}

export const ProductQualityTypeTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [ProductQualityType.A]: 'success',
  [ProductQualityType.B]: 'primary',
  [ProductQualityType.C]: 'warning',
  [ProductQualityType.DEFECT]: 'danger',
  [ProductQualityType.PENDING]: 'info'
}

/** 销售换货单收费类型（对应 ExchangeChargeType 枚举，存储值为 SERVICE/DIFF/FULL/OTHER） */
export const ExchangeChargeType = {
  SERVICE: 'SERVICE',      // 服务费（客户人为损坏、超出保修等）
  DIFF: 'DIFF',            // 品质差价（同品换货：换出品质与原销售品质不同）
  FULL: 'FULL',            // 全额货值（客户原因换货）
  OTHER: 'OTHER'           // 其他
} as const

export const ExchangeChargeTypeLabel: Record<string, string> = {
  [ExchangeChargeType.SERVICE]: '服务费',
  [ExchangeChargeType.DIFF]: '品质差价',
  [ExchangeChargeType.FULL]: '全额货值',
  [ExchangeChargeType.OTHER]: '其他'
}

/**
 * 委外加工退货收费类型（对应 OutsourceChargeType 枚举，存储值为 REWORK/FREIGHT/INSPECTION/EXCESS_LOSS/OTHER）
 * 注意：与销售侧 ExchangeChargeType 方向相反——这里是「加工厂向我方收取」，审核生成正向应付。
 */
export const OutsourceChargeType = {
  REWORK: 'REWORK',              // 返工费
  FREIGHT: 'FREIGHT',            // 运费
  INSPECTION: 'INSPECTION',      // 检测费
  EXCESS_LOSS: 'EXCESS_LOSS',    // 超损赔偿
  OTHER: 'OTHER'                 // 其他
} as const

export const OutsourceChargeTypeLabel: Record<string, string> = {
  [OutsourceChargeType.REWORK]: '返工费',
  [OutsourceChargeType.FREIGHT]: '运费',
  [OutsourceChargeType.INSPECTION]: '检测费',
  [OutsourceChargeType.EXCESS_LOSS]: '超损赔偿',
  [OutsourceChargeType.OTHER]: '其他'
}

/** 售后待整理批次来源单据类型（对应 AfterSaleSourceType 枚举） */
export const AfterSaleSourceType = {
  SALE_RETURN: 'SALE_RETURN',     // 销售退单
  SALE_EXCHANGE: 'SALE_EXCHANGE'  // 销售换货单
} as const

export const AfterSaleSourceTypeLabel: Record<string, string> = {
  [AfterSaleSourceType.SALE_RETURN]: '销售退单',
  [AfterSaleSourceType.SALE_EXCHANGE]: '销售换货单'
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

/** 通用单据状态（对应 DocStatus 枚举：草稿/已审核/已作废） */
export const DocStatus = {
  DRAFT: 'DRAFT',
  AUDITED: 'AUDITED',
  CANCELLED: 'CANCELLED'
} as const

export const DocStatusLabel: Record<string, string> = {
  [DocStatus.DRAFT]: '草稿',
  [DocStatus.AUDITED]: '已审核',
  [DocStatus.CANCELLED]: '已作废'
}

export const DocStatusTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [DocStatus.DRAFT]: 'info',
  [DocStatus.AUDITED]: 'success',
  [DocStatus.CANCELLED]: 'danger'
}

/** 委外物料退货类型（对应 MaterialReturnType 枚举） */
export const MaterialReturnType = {
  MATERIAL: 'MATERIAL',
  PRODUCT: 'PRODUCT'
} as const

export const MaterialReturnTypeLabel: Record<string, string> = {
  [MaterialReturnType.MATERIAL]: '物料商退货',
  [MaterialReturnType.PRODUCT]: '成品商退货'
}

/** 委外物料订单列表脏标志：详情/新增页数据变动后置位，列表页 onActivated 据此按需刷新 */
export const OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY = 'outsourceMaterialOrderListDirty'

/** 各业务列表页脏标志键：对应详情/新增/编辑页数据变动后置位，列表页 onActivated 据此按需刷新 */
export const SALE_RETURN_DIRTY_KEY = 'saleReturnListDirty'
export const PURCHASE_ORDER_DIRTY_KEY = 'purchaseOrderListDirty'
export const PURCHASE_RETURN_DIRTY_KEY = 'purchaseReturnListDirty'
export const OUTSOURCE_ORDER_DIRTY_KEY = 'outsourceOrderListDirty'
export const OUTSOURCE_DELIVERY_DIRTY_KEY = 'outsourceDeliveryListDirty'
export const OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY = 'outsourceMaterialReturnListDirty'
export const OUTSOURCE_RETURN_ORDER_DIRTY_KEY = 'outsourceReturnOrderListDirty'
export const OUTSOURCE_OTHER_IO_DIRTY_KEY = 'outsourceOtherIoListDirty'
export const INVENTORY_OTHER_IO_DIRTY_KEY = 'inventoryOtherIoListDirty'
export const INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY = 'inventoryWarehouseMoveListDirty'
export const INVENTORY_RECLASSIFY_DIRTY_KEY = 'inventoryReclassifyListDirty'
export const DEV_PROJECT_DIRTY_KEY = 'devProjectListDirty'
export const DEV_MATERIAL_DIRTY_KEY = 'devMaterialListDirty'
export const SUPPLIER_DIRTY_KEY = 'supplierListDirty'
export const SALE_EXCHANGE_DIRTY_KEY = 'saleExchangeListDirty'



