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

/**
 * 产品规格（对应后端 `material/common/ProductSpec`，DB 存 code 于 `product.spec_type`）。
 * 2026-09-21 用户要求：原「分类」（自由文本）替换为规格，固定三种取值。
 * 产品管理的新增/编辑**必填**；研发立项自动建产品时可为空（之后在产品管理补填）。
 */
export const ProductSpec = {
  ORIGINAL: 'ORIGINAL',   // 原装
  MATCHED: 'MATCHED',     // 原配
  MODIFIED: 'MODIFIED'    // 改配
} as const

export const ProductSpecLabel: Record<string, string> = {
  [ProductSpec.ORIGINAL]: '原装',
  [ProductSpec.MATCHED]: '原配',
  [ProductSpec.MODIFIED]: '改配'
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
  [OutsourceOrderStatus.FINISHED]: '已结单',
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

/** 物料订单状态文案（2026-09-28 用户口径：RECEIVING 的文案由「收货中」改「生产中」——
 *  与页签、与加工单侧状态命名（PRODUCING=生产中）统一；**只改文案**，code 仍是 RECEIVING，库中数据不动） */
export const MaterialOrderStatusLabel: Record<string, string> = {
  [MaterialOrderStatus.PENDING]: '待审核',
  [MaterialOrderStatus.RECEIVING]: '生产中',
  [MaterialOrderStatus.FINISHED]: '已结单',
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
  DEFECT_RETURN: 'DEFECT_RETURN',
  /** 退货（2026-09-29 用户口径）：把已收物料退回物料商 —— 扣退货仓库存 + 冲减订单已收数量 + 冲减应付 */
  RECEIVE_RETURN: 'RECEIVE_RETURN'
} as const

export const DeliveryTypeLabel: Record<string, string> = {
  [DeliveryType.DELIVERY]: '发料',
  [DeliveryType.RECEIVE]: '收料',
  [DeliveryType.TRANSFER]: '调拨',
  [DeliveryType.RETURN]: '退料',
  [DeliveryType.DEFECT_RETURN]: '退不良',
  [DeliveryType.RECEIVE_RETURN]: '退货'
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

/**
 * 费用类型：DB 存 code，显示映射（finance_expense.expense_type，费用管理页与利润明细页共用）。
 *
 * <p>2026-09-27 新增 <b>RND = 研发支出</b>（用户要求）：**研发物料**「新增物料」时可顺带登记一笔研发支出
 * （走 `POST /api/dev/purchase-item/{id}/rd-expense`，勾选路径自动审核当场扣款、无费用审核权限则落**草稿**）。
 * 2026-09-28（用户口径）：该功能属「研发管理 → 研发物料」，原挂在物料信息管理的入口与端点**已移除**
 * （存量费用单的来源类型 `RD_MATERIAL` 保留，仅作历史追溯）。
 * 后端写入侧用 `finance.common.ExpenseType.RND`，且**写入会被校验**（未知 code 直接拒绝、大小写归一化）；
 * 本映射与后端枚举的常量名一致性由 `web-check.ps1` 的枚举守卫比对（漏加值 ⇒ 守卫 FAIL）。</p>
 */
export const ExpenseTypeLabel: Record<string, string> = {
  OFFICE: '办公费', RENT: '房租水电', SALARY: '工资社保',
  TRANSPORT: '运输费', TRAVEL: '差旅费', ENTERTAIN: '业务招待',
  RND: '研发支出', OTHER: '其他',
  // 2026-09-29（用户口径「报损需要走财务流程」）：内部承担的报损审核后自动生成的损失费用
  // （由报损单带出、**无账户=非资金**：不写资金流水、不扣账户）
  LOSS: '报损损失'
}

/**
 * 报损「损失承担方」（2026-09-29 用户口径「报损需要走财务流程」）：
 * 决定报损单审核后的**财务落点** —— 内部损失⇒「报损损失」费用单（非资金）；
 * 供应商/加工厂承担⇒对供应商的**应收**（索赔）。与后端 `StockLossAccountingHelper.PARTY_*` 一致。
 */
export const LiableParty = { INTERNAL: 'INTERNAL', SUPPLIER: 'SUPPLIER' } as const

export const LiablePartyLabel: Record<string, string> = {
  [LiableParty.INTERNAL]: '内部损失',
  [LiableParty.SUPPLIER]: '供应商/加工厂承担'
}

/** 账户类型：DB 存 code（finance_account.account_type），显示映射（账户管理页 / 首页财务 TAB / 经营分析-资金与往来 共用） */
export const AccountType = {
  CASH: 'cash',
  BANK: 'bank',
  WECHAT: 'wechat',
  ALIPAY: 'alipay'
} as const

export const AccountTypeLabel: Record<string, string> = {
  [AccountType.CASH]: '现金',
  [AccountType.BANK]: '银行',
  [AccountType.WECHAT]: '微信',
  [AccountType.ALIPAY]: '支付宝'
}

/**
 * 账户类型显示文案（大小写容错）。
 * 为何要容错：库里历史上存在大写值（如边界测试直接以 `'BANK'` 调接口写入），
 * 而 UI 新建走的是小写 code（`bank`）；直接按 code 精确查表会让大写值回落成原始枚举串。
 * 未知值原样返回，便于发现新增类型。
 */
export function accountTypeLabel(v?: string | null): string {
  if (v === null || v === undefined || v === '') return '-'
  return AccountTypeLabel[String(v).toLowerCase()] || String(v)
}

/**
 * 销售单结算方式（2026-09-18，**按单记**：同一客户有时现金、有时账期）。
 * 对应后端 `sale/common/SettleType`，DB 存 code。
 * CASH = 审核销售单后自动生成一张**草稿**收款单（挂所选收款账户），人工审核该收款单即完成收款。
 */
export const SettleType = {
  CREDIT: 'CREDIT',
  CASH: 'CASH'
} as const

export const SettleTypeLabel: Record<string, string> = {
  [SettleType.CREDIT]: '账期',
  [SettleType.CASH]: '现金'
}

/** el-tag 的 type 需窄化联合类型（直接给 string 会触发 TS2322） */
export const SettleTypeTag: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  [SettleType.CREDIT]: 'info',
  [SettleType.CASH]: 'success'
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
  // 术语 2026-09-21 统一：原「维修返还」→**维修退货**（与物料退货页的「维修退货」页签、与加工侧同词）
  [DefectHandleType.REPAIR_RETURN]: '维修退货',
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

/** 收货记录状态（对应 DeliveryItemStatus 枚举） */
export const DeliveryItemStatus = {
  NORMAL: 'NORMAL',
  REVERSED: 'REVERSED'
} as const

export const DeliveryItemStatusLabel: Record<string, string> = {
  [DeliveryItemStatus.NORMAL]: '正常',
  [DeliveryItemStatus.REVERSED]: '已回滚'
}

/** 研发项目物料类型（对应 DevMaterialTypeEnum 枚举，DB 存 code） */
export const DevMaterialType = {
  BOARD: 'BOARD',
  SCREEN: 'SCREEN',
  TEST_FIXTURE: 'TEST_FIXTURE',
  TOUCH_BOX: 'TOUCH_BOX',
  DISPLAY_BOX: 'DISPLAY_BOX',
  OTHER: 'OTHER'
} as const

export const DevMaterialTypeLabel: Record<string, string> = {
  [DevMaterialType.BOARD]: '基板',
  [DevMaterialType.SCREEN]: '屏幕',
  [DevMaterialType.TEST_FIXTURE]: '测试架',
  [DevMaterialType.TOUCH_BOX]: '触摸资料盒',
  [DevMaterialType.DISPLAY_BOX]: '显示资料盒',
  [DevMaterialType.OTHER]: '其他'
}

/** 研发物料状态（dev_purchase_item.status，DB 存 code） */
export const DevMaterialStatus = {
  GOOD: 'GOOD',
  DAMAGED: 'DAMAGED',
  USED: 'USED'
} as const

export const DevMaterialStatusLabel: Record<string, string> = {
  [DevMaterialStatus.GOOD]: '完好',
  [DevMaterialStatus.DAMAGED]: '已损坏',
  [DevMaterialStatus.USED]: '已使用'
}

/** 研发图纸文档类型（dev_drawing.doc_type，DB 存 code） */
export const DevDrawingDocType = {
  DRAWING: 'DRAWING',
  STRUCTURE: 'STRUCTURE',
  SPEC: 'SPEC',
  TEST_REPORT: 'TEST_REPORT',
  OTHER: 'OTHER'
} as const

export const DevDrawingDocTypeLabel: Record<string, string> = {
  [DevDrawingDocType.DRAWING]: '排线图',
  [DevDrawingDocType.STRUCTURE]: '结构图',
  [DevDrawingDocType.SPEC]: '规格书',
  [DevDrawingDocType.TEST_REPORT]: '测试报告',
  [DevDrawingDocType.OTHER]: '其他'
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
  OUTSOURCE_DELIVERY: '委外加工收货', OUTSOURCE_MATERIAL_DELIVERY: '委外物料收发',
  OUTSOURCE_RETURN: '委外退料', OUTSOURCE_MATERIAL_RETURN: '委外物料退货',
  OUTSOURCE_RETURN_CHARGE: '委外加工退货收费',
  OUTSOURCE_REPAIR_CHARGE: '委外维修收费',
  // P3（2026-09-28）：物料维修返回的维修费（正向应付，供应商向我方收取），与加工侧的「委外维修收费」分开
  OUTSOURCE_MATERIAL_REPAIR_FEE: '委外物料维修费',
  OUTSOURCE_EXCESS_LOSS: '委外超损',
  OUTSOURCE_RETURN_BACK: '加工返回单',
  // F7-54（2026-09-19）：补齐 6 个后端已定义但前端漏映射的值 ——
  // 缺映射时 sourceBillTypeLabel() 会走 `|| code` 兜底，列表"来源"列直接显示英文 code。
  // 其中 PURCHASE_EXCHANGE_IN / PURCHASE_EXCHANGE_RETURN 在库中各 60 行（采购换货是主力业务）。
  PURCHASE_EXCHANGE_RETURN: '采购换货退回', PURCHASE_EXCHANGE_IN: '采购换货入库',
  // 2026-09-21：采购换货「是否付费=是」时的付费台账（方向：我们向供货商付费 ⇒ 正向应付）
  PURCHASE_EXCHANGE_CHARGE: '采购换货付费',
  // 2026-09-21：采购**退货**「是否付费=是」时的付费台账（同上方向；枚举守卫会校验这行必须在）
  PURCHASE_RETURN_CHARGE: '采购退货付费',
  SALE_EXCHANGE_CHARGE: '销售换货收费', SALE_RETURN_CHARGE: '销售退货收费',
  RETURN_SORT_LOSS: '退货整理折损', PAYABLE_TRANSFER: '应付转应收',
  // F7-53（2026-09-19）：预收/预付台账的来源标记（后端由 SettlementStatus.ADVANCE 归位而来）
  ADVANCE_LEDGER: '预收/预付台账',
  // 2026-09-29（报损走财务流程）：两类报损单 —— 承担方=供应商时是**索赔应收**的来源；
  // 承担方=内部时是**报损损失费用单**的来源（同一类型码两用，靠单据类型区分）
  INVENTORY_STOCK_LOSS: '成品报损',
  OUTSOURCE_STOCK_LOSS: '委外物料报损'
}
export function sourceBillTypeLabel(code?: string) { return code ? (SourceBillTypeLabel[code] || code) : '' }

/**
 * 来源单据类型 **列表短名**（2026-09-26 B9）：
 * 应付列表的「业务场景」列只有 84px，而全称最长「委外加工退货收费」8 字实测需 128px ⇒ 列表列宽装不下。
 * 这里给一份 **≤4 字** 的短名（销退收费/委退收费/换货入库…）供**列表单元格**使用；
 * 筛选下拉、导出、详情页仍用全称 {@link SourceBillTypeLabel}，信息不丢。
 * ⚠️ 新增来源类型时两处都要补（缺失时 `sourceBillTypeShortLabel` 回落全称）。
 */
export const SourceBillTypeShortLabel: Record<string, string> = {
  SALE_ORDER: '销售单', SALE_OUTBOUND: '销售出库', SALE_RETURN: '销售退货',
  SALE_RETURN_CHARGE: '销退收费', SALE_EXCHANGE_CHARGE: '销换收费',
  PURCHASE_ORDER: '采购单', PURCHASE_INBOUND: '采购入库', PURCHASE_RETURN: '采购退货',
  PURCHASE_RETURN_CHARGE: '采退付费', PURCHASE_EXCHANGE_RETURN: '换货退回',
  PURCHASE_EXCHANGE_IN: '换货入库', PURCHASE_EXCHANGE_CHARGE: '换货付费',
  OUTSOURCE_DELIVERY: '委外收货', OUTSOURCE_EXCESS_LOSS: '委外超损',
  OUTSOURCE_MATERIAL_DELIVERY: '物料收发', OUTSOURCE_MATERIAL_RETURN: '物料退货',
  OUTSOURCE_RETURN: '委外退料', OUTSOURCE_RETURN_CHARGE: '委退收费',
  OUTSOURCE_REPAIR_CHARGE: '维修收费', OUTSOURCE_RETURN_BACK: '加工返回',
  OUTSOURCE_MATERIAL_REPAIR_FEE: '物料维修费',
  RETURN_SORT_LOSS: '退货折损', PAYABLE_TRANSFER: '转应收', ADVANCE_LEDGER: '预收预付',
  INVENTORY_STOCK_LOSS: '成品报损', OUTSOURCE_STOCK_LOSS: '物料报损'
}
export function sourceBillTypeShortLabel(code?: string) { return code ? (SourceBillTypeShortLabel[code] || sourceBillTypeLabel(code)) : '' }

/**
 * 往来主体类型（对应后端 SubjectType 枚举）
 * 应收既有客户应收（销售业务），也有供应商应收（应付转应收：退货/超损扣款无货款可抵时向对方收款）
 */
export const SubjectType = {
  CUSTOMER: 'CUSTOMER',
  SUPPLIER: 'SUPPLIER'
} as const

export const SubjectTypeLabel: Record<string, string> = {
  [SubjectType.CUSTOMER]: '客户',
  [SubjectType.SUPPLIER]: '供应商'
}

export const SubjectTypeTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [SubjectType.CUSTOMER]: 'primary',
  [SubjectType.SUPPLIER]: 'warning'
}

/** 应付转应收：新增/编辑/详情/审核后回列表时的脏标志（列表页 onActivated 按需刷新） */
export const PAYABLE_TRANSFER_DIRTY_KEY = 'payableTransferDirty'

/**
 * 2026-09-20（F7-165）：以下映射原先**分散硬编码**在各页面（同一份 label 抄两遍、或散落 4 处），
 * 新增类型/流水种类时容易漏改（改一处、忘一处 ⇒ 静默显示英文 code）。统一集中到此处。
 * 全库口径：**存库用 code（英文），展示用 label（中文）**。
 */

/** 资金流水类型（`finance/cashflow.vue` 的筛选下拉与列表展示共用同一份） */
export const CashFlowTypeLabel: Record<string, string> = {
  RECEIPT: '收款', PAYMENT: '付款', OPENING: '期初',
  RECEIPT_REVERSE: '收款冲正', PAYMENT_REVERSE: '付款冲正',
  EXPENSE: '费用支出', EXPENSE_REVERSE: '费用冲正',
}

/** 发票类型（登记发票：专票/普票/电子专票/电子普票） */
export const InvoiceKindLabel: Record<string, string> = {
  special: '增值税专用发票', normal: '增值税普通发票',
  e_special: '电子专票', e_normal: '电子普票',
}

/** 发票进销方向：销项=我方开出，进项=我方收到 */
export const InvoiceDirectionLabel: Record<string, string> = { SALE: '销项', PURCHASE: '进项' }

/** 发票登记状态 */
export const InvoiceStatusLabel: Record<string, string> = { REGISTERED: '已登记', CANCELLED: '已作废' }

/** 发票状态 → el-tag 的 type */
export const InvoiceStatusTag: Record<string, string> = { REGISTERED: 'success', CANCELLED: 'info' }

/** 应付/应收来源单据类型 → 详情页路由前缀（用于来源单号点击跳转） */
export const SourceBillDetailRoute: Record<string, string> = {
  PURCHASE_ORDER: '/inventory/purchase/detail',
  PURCHASE_RETURN: '/inventory/purchase-return/detail',
  PURCHASE_EXCHANGE_RETURN: '/inventory/purchase-exchange/detail',
  PURCHASE_EXCHANGE_IN: '/inventory/purchase-exchange/detail',
  PURCHASE_EXCHANGE_CHARGE: '/inventory/purchase-exchange/detail',
  OUTSOURCE_DELIVERY: '/outsource/order/detail',
  OUTSOURCE_EXCESS_LOSS: '/outsource/order/detail',
  OUTSOURCE_MATERIAL_DELIVERY: '/outsource/delivery/detail',
  OUTSOURCE_MATERIAL_RETURN: '/outsource/material-return/detail',
  // P3（2026-09-28）：物料维修费应付的来源是**物料维修返回单** ⇒ 链接到物料退货详情（不是加工退货详情）
  OUTSOURCE_MATERIAL_REPAIR_FEE: '/outsource/material-return/detail',
  OUTSOURCE_RETURN: '/outsource/return-order/detail',
  OUTSOURCE_RETURN_CHARGE: '/outsource/return-order/detail',
  OUTSOURCE_REPAIR_CHARGE: '/outsource/return-order/detail',
  // 2026-09-26 B9 修复：这两个前缀原先指向**不存在的列表路径**（/sale/order、/sale/outbound），
  // 消费方又按 `${base}/${id}` 拼 ⇒ 点「来源单号」实际落到 404。改为真正的详情路由。
  SALE_ORDER: '/inventory/sale/detail',
  SALE_OUTBOUND: '/sale/outbound/detail',
  SALE_RETURN: '/sale/return/detail',
  // F7-54（2026-09-19）：补 4 个跳转前缀 —— 缺键时 goSourceDetail 遇 !base 直接 return，
  // 表现为"来源单号"点不动（不是 404）。4 个路由均已在 router/index.ts 核实存在。
  SALE_RETURN_CHARGE: '/sale/return/detail',
  SALE_EXCHANGE_CHARGE: '/sale/exchange/detail',
  RETURN_SORT_LOSS: '/inventory/return-sort/detail',
  PAYABLE_TRANSFER: '/finance/payable-transfer/detail'
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

/**
 * 仓库类型（对应 Warehouse.warehouse_type 字段，DB 存 code）
 * 2026-09-16 方案 A：仓型由 4 种收敛为 **2 种**（辅料仓 / 成品仓）。
 * 原「不良仓(DEFECT)」「售后仓(AFTER_SALE)」已取消：退回品/不良品统一入成品仓，改用**品质**区分
 * （A/B/C 良品、DEFECT 不良、PENDING 待整理）；委外仓（OUTSOURCE）不再写仓型。
 */
export const WarehouseType = {
  AUXILIARY: 'AUXILIARY',  // 辅料仓（自有物料仓）
  FINISHED: 'FINISHED'     // 成品仓
} as const

// 历史值 DEFECT/AFTER_SALE 仅作展示兜底（存量数据/旧单据可能仍带），新代码不要再引用
export const WarehouseTypeLabel: Record<string, string> = {
  [WarehouseType.AUXILIARY]: '辅料仓',
  [WarehouseType.FINISHED]: '成品仓',
  DEFECT: '不良仓（已取消）',
  AFTER_SALE: '售后仓（已取消）'
}

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
  [ProductQualityType.PENDING]: '待整理'
}

export const ProductQualityTypeTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [ProductQualityType.A]: 'success',
  [ProductQualityType.B]: 'primary',
  [ProductQualityType.C]: 'warning',
  [ProductQualityType.DEFECT]: 'danger',
  [ProductQualityType.PENDING]: 'info'
}

/**
 * 销售单可售品质（2026-09-21 用户口径：「销售单产品明细的品质，**不能有不良品和待整理**」）
 * ⇒ 只允许 **A规/B规/C规** 良品（DEFECT 不良品、PENDING 待整理一律不可售）。
 *
 * <p>与后端 `SaleOrderServiceImpl#assertItemQualitySellable` **同一口径**：前端过滤下拉只是体验，
 * 服务层在「保存草稿」与「审核」两处兜底（直调接口 / 编辑历史草稿都绕不过）。</p>
 * <p>注意：只有**销售单**受此约束 —— 退货/换货/整理单等场景仍然需要不良品与待整理。</p>
 */
export const SALE_SELLABLE_QUALITY_TYPES: string[] = [
  ProductQualityType.A, ProductQualityType.B, ProductQualityType.C
]

/** 该品质是否可销售（空值按 A规 处理 ⇒ true；不良品/待整理 ⇒ false） */
export function isSellableQuality(code?: string | null): boolean {
  return SALE_SELLABLE_QUALITY_TYPES.includes(code || ProductQualityType.A)
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
 * 销售**退货**单收费类型（对应后端 sale/common/SaleReturnChargeType，存储值 COVER_SCRATCH/OTHER）
 * 2026-10-02（用户口径）：退单只保留「盖板划伤」与「其他」两项 —— 此前退货单借用上面的
 * ExchangeChargeType（服务费/品质差价/全额货值/其他），与退单实际场景（外观损伤责任判定）不符；
 * 拆开后改退单的选项不再牵连销售换货单。切换前库里 4 张退货单的 charge_type 全为 NULL，无历史值。
 */
export const SaleReturnChargeType = {
  COVER_SCRATCH: 'COVER_SCRATCH',  // 盖板划伤
  OTHER: 'OTHER'                   // 其他
} as const

export const SaleReturnChargeTypeLabel: Record<string, string> = {
  [SaleReturnChargeType.COVER_SCRATCH]: '盖板划伤',
  [SaleReturnChargeType.OTHER]: '其他'
}

/**
 * 采购换货单付费类型（对应后端 purchase/common/PurchaseChargeType，存储值 SERVICE/DIFF/FULL/OTHER）
 * ⚠️ 方向与上面的销售侧 ExchangeChargeType 相反：这里是「**我们向供货商付费**」（审核生成正向应付），
 * 销售侧是「向客户收费」（审核生成正向应收）。两侧各自独立映射，避免方向混用。
 */
export const PurchaseChargeType = {
  SERVICE: 'SERVICE',      // 服务费（供货商收取的换货/翻新手续费等）
  DIFF: 'DIFF',            // 品质差价（换入品质更好，我方补差价）
  FULL: 'FULL',            // 全额货值（按货值全额付费换新）
  OTHER: 'OTHER'           // 其他
} as const

export const PurchaseChargeTypeLabel: Record<string, string> = {
  [PurchaseChargeType.SERVICE]: '服务费',
  [PurchaseChargeType.DIFF]: '品质差价',
  [PurchaseChargeType.FULL]: '全额货值',
  [PurchaseChargeType.OTHER]: '其他'
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

/**
 * 委外加工退货类型（对应后端 OutsourceReturnType 枚举，2026-09-17）
 * <p>DEFECT 加工退货：工厂交货后发现不良退回工厂 —— 可关联加工单（也可不关联）、**禁止向工厂收费**；
 * REPAIR 维修退货：客户使用后退回我方的售后品推给工厂维修 —— **不关联加工单**、**必须由工厂收费**、修好走「维修返回」。</p>
 */
export const OutsourceReturnType = {
  DEFECT: 'DEFECT',   // 加工退货
  REPAIR: 'REPAIR'    // 维修退货
} as const

export const OutsourceReturnTypeLabel: Record<string, string> = {
  [OutsourceReturnType.DEFECT]: '加工退货',
  [OutsourceReturnType.REPAIR]: '维修退货'
}

export const OutsourceReturnTypeTag: Record<string, 'warning' | 'primary'> = {
  [OutsourceReturnType.DEFECT]: 'warning',
  [OutsourceReturnType.REPAIR]: 'primary'
}

/** 售后待整理批次来源单据类型（对应 AfterSaleSourceType 枚举） */
export const AfterSaleSourceType = {
  SALE_RETURN: 'SALE_RETURN',     // 销售退货单
  SALE_EXCHANGE: 'SALE_EXCHANGE'  // 销售换货单
} as const

export const AfterSaleSourceTypeLabel: Record<string, string> = {
  [AfterSaleSourceType.SALE_RETURN]: '销售退货单',
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
  CANCELLED: 'CANCELLED', // 已冲回
  ADVANCE: 'ADVANCE'      // F7-54（2026-09-19）：预收/预付台账（多收/多付形成的负数台账）
} as const

export const SettlementStatusLabel: Record<string, string> = {
  [SettlementStatus.UNSETTLED]: '未结算',
  [SettlementStatus.PARTIAL]: '部分结算',
  [SettlementStatus.SETTLED]: '已结算',
  [SettlementStatus.CANCELLED]: '已冲回',
  [SettlementStatus.ADVANCE]: '预收/预付'
}

export const SettlementStatusTag: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  [SettlementStatus.UNSETTLED]: 'danger',
  [SettlementStatus.PARTIAL]: 'warning',
  [SettlementStatus.SETTLED]: 'success',
  [SettlementStatus.CANCELLED]: 'info',
  [SettlementStatus.ADVANCE]: 'warning'
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

/**
 * 委外物料退货类型（对应后端 MaterialReturnType 枚举）
 * <p>2026-09-17 定稿两类型 → <b>2026-09-28 按用户口径扩为三态</b>：</p>
 * - `ORDER` **订单退料**：仅**关联订单且订单未结单**；审核 = 扣源仓 + 扣该订单出货/收料数量
 *   （`order_returned_qty`，永久），不动账务、不跟踪返回
 * - `REFUND` **退货退款**：无单，或关联**已结单**订单；审核 = 扣源仓 +（P2 起）生成**对供应商的应收**
 * - `REPAIR` **维修返回**：无单，或关联**已结单**订单；审核 = 扣源仓送修 → 登记维修返回入库 → 可结案
 *   （术语 2026-09-28 统一：原「维修退货」→**维修返回**，与用户口径同词）
 * <p>⚠️ 类型与订单状态的绑定由后端强校验（`MaterialReturnType.checkOrderStatus`），前端只做体验层引导。
 * 历史值 `MATERIAL` 由后端归一成 REFUND，前端只需兜底显示。</p>
 */
export const MaterialReturnType = {
  ORDER: 'ORDER',
  REFUND: 'REFUND',
  REPAIR: 'REPAIR'
} as const

export const MaterialReturnTypeLabel: Record<string, string> = {
  [MaterialReturnType.ORDER]: '订单退料',
  [MaterialReturnType.REFUND]: '退货退款',
  // 2026-09-29 用户口径：类型文案「维修返回」→「**工厂维修**」（与三级菜单/叶子逐字同名）
  [MaterialReturnType.REPAIR]: '工厂维修',
  // 历史值兜底（旧枚举"物料商退货"= 退货退款）
  MATERIAL: '退货退款'
}

export const MaterialReturnTypeTag: Record<string, 'warning' | 'primary' | 'success'> = {
  [MaterialReturnType.ORDER]: 'primary',
  [MaterialReturnType.REFUND]: 'warning',
  [MaterialReturnType.REPAIR]: 'success'
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
/* ===== 以下 4 组为 2026-09-14 新增：原先后端回中文，现统一「接口只回 code、前端映射」 ===== */

/** 库存流水变动类型（对应后端 StockChangeType 枚举，DB 存 code；原由后端回 changeTypeLabel） */
export const StockChangeType = {
  // 采购
  PURCHASE_IN: 'PURCHASE_IN', PURCHASE_UN_AUDIT: 'PURCHASE_UN_AUDIT',
  RETURN_OUT: 'RETURN_OUT', RETURN_UN_AUDIT: 'RETURN_UN_AUDIT',
  // 采购换货（2026-09-18 新增）：退回出库 / 换入入库 / 反审核
  PURCHASE_EXCHANGE_OUT: 'PURCHASE_EXCHANGE_OUT', PURCHASE_EXCHANGE_IN: 'PURCHASE_EXCHANGE_IN',
  PURCHASE_EXCHANGE_UN_AUDIT: 'PURCHASE_EXCHANGE_UN_AUDIT',
  // 销售
  SALE_OUT: 'SALE_OUT', SALE_OUT_UN_AUDIT: 'SALE_OUT_UN_AUDIT',
  SALE_RETURN_IN: 'SALE_RETURN_IN', SALE_RETURN_UN_AUDIT: 'SALE_RETURN_UN_AUDIT',
  EXCHANGE_IN: 'EXCHANGE_IN', EXCHANGE_OUT: 'EXCHANGE_OUT', EXCHANGE_UN_AUDIT: 'EXCHANGE_UN_AUDIT',
  // 移仓
  MOVE_OUT: 'MOVE_OUT', MOVE_IN: 'MOVE_IN',
  // 其他出入库
  OTHER_IN: 'OTHER_IN', OTHER_OUT: 'OTHER_OUT', CANCEL_IN: 'CANCEL_IN', CANCEL_OUT: 'CANCEL_OUT',
  // 委外
  OUTSOURCE_DELIVERY_OUT: 'OUTSOURCE_DELIVERY_OUT', OUTSOURCE_FINISH_IN: 'OUTSOURCE_FINISH_IN',
  OUTSOURCE_RETURN_OUT: 'OUTSOURCE_RETURN_OUT', OUTSOURCE_RETURN_OUT_UN_AUDIT: 'OUTSOURCE_RETURN_OUT_UN_AUDIT',
  OUTSOURCE_DEFECT_RETURN: 'OUTSOURCE_DEFECT_RETURN', OUTSOURCE_ROLLBACK: 'OUTSOURCE_ROLLBACK',
  OUTSOURCE_CONSUME: 'OUTSOURCE_CONSUME', CANCEL_OUTSOURCE_CONSUME: 'CANCEL_OUTSOURCE_CONSUME',
  OUTSOURCE_DEFECT_RETURN_UN_AUDIT: 'OUTSOURCE_DEFECT_RETURN_UN_AUDIT',
  // 工厂售后（原无单加工退货；2026-09-25 P1-1）：成品（加工退货）入委外仓 / 反审核核销
  OUTSOURCE_DEFECT_IN: 'OUTSOURCE_DEFECT_IN', CANCEL_OUTSOURCE_DEFECT_IN: 'CANCEL_OUTSOURCE_DEFECT_IN',
  // 加工返回单（2026-09-25 P1-2）：核销在厂成品 / 修好回仓 / 实际用料出仓（及其反审核）
  OUTSOURCE_BACK_CONSUME: 'OUTSOURCE_BACK_CONSUME', CANCEL_OUTSOURCE_BACK_CONSUME: 'CANCEL_OUTSOURCE_BACK_CONSUME',
  OUTSOURCE_BACK_IN: 'OUTSOURCE_BACK_IN', CANCEL_OUTSOURCE_BACK_IN: 'CANCEL_OUTSOURCE_BACK_IN',
  OUTSOURCE_BACK_MATERIAL: 'OUTSOURCE_BACK_MATERIAL', CANCEL_OUTSOURCE_BACK_MATERIAL: 'CANCEL_OUTSOURCE_BACK_MATERIAL',
  // 维修退货送修转移（2026-09-25 P2-1）：成品（维修退货）入/核销委外仓 + 维修用料出仓/恢复
  OUTSOURCE_REPAIR_STOCK_IN: 'OUTSOURCE_REPAIR_STOCK_IN', CANCEL_OUTSOURCE_REPAIR_STOCK_IN: 'CANCEL_OUTSOURCE_REPAIR_STOCK_IN',
  OUTSOURCE_REPAIR_MATERIAL: 'OUTSOURCE_REPAIR_MATERIAL', CANCEL_OUTSOURCE_REPAIR_MATERIAL: 'CANCEL_OUTSOURCE_REPAIR_MATERIAL',
  // 委外维修退货（2026-09-17）：送修出库 / 反审核回补 / 维修返回入库 / 撤销返回
  OUTSOURCE_REPAIR_OUT: 'OUTSOURCE_REPAIR_OUT', OUTSOURCE_REPAIR_OUT_UN_AUDIT: 'OUTSOURCE_REPAIR_OUT_UN_AUDIT',
  OUTSOURCE_REPAIR_IN: 'OUTSOURCE_REPAIR_IN', CANCEL_OUTSOURCE_REPAIR_IN: 'CANCEL_OUTSOURCE_REPAIR_IN',
  OUTSOURCE_CANCEL_DELIVERY: 'OUTSOURCE_CANCEL_DELIVERY', OUTSOURCE_EDIT_ROLLBACK: 'OUTSOURCE_EDIT_ROLLBACK',
  DELIVERY_OUT: 'DELIVERY_OUT', DELIVERY_IN: 'DELIVERY_IN',
  TRANSFER_OUT: 'TRANSFER_OUT', TRANSFER_IN: 'TRANSFER_IN',
  /** 已停用（C5 起统一用 OUTSOURCE_CANCEL_DELIVERY），仅兼容历史流水展示 */
  CANCEL_DELIVERY: 'CANCEL_DELIVERY',
  CANCEL_TRANSFER_OUT: 'CANCEL_TRANSFER_OUT', CANCEL_TRANSFER_IN: 'CANCEL_TRANSFER_IN',
  RETURN_IN: 'RETURN_IN', CANCEL_RETURN_IN: 'CANCEL_RETURN_IN',
  RECEIVE_IN: 'RECEIVE_IN', DEFECT_OUT: 'DEFECT_OUT',
  CANCEL_RECEIVE_IN: 'CANCEL_RECEIVE_IN', CANCEL_DEFECT_OUT: 'CANCEL_DEFECT_OUT',
  OUTSOURCE_COMPONENT_CONSUME: 'OUTSOURCE_COMPONENT_CONSUME',
  CANCEL_OUTSOURCE_COMPONENT_CONSUME: 'CANCEL_OUTSOURCE_COMPONENT_CONSUME',
  MATERIAL_RETURN_OUT: 'MATERIAL_RETURN_OUT', CANCEL_MATERIAL_RETURN_OUT: 'CANCEL_MATERIAL_RETURN_OUT',
  // 委外物料维修（2026-09-17）：送修出库 / 反审核回补 / 维修返回入库 / 撤销返回
  MATERIAL_REPAIR_OUT: 'MATERIAL_REPAIR_OUT', MATERIAL_REPAIR_OUT_UN_AUDIT: 'MATERIAL_REPAIR_OUT_UN_AUDIT',
  MATERIAL_REPAIR_IN: 'MATERIAL_REPAIR_IN', CANCEL_MATERIAL_REPAIR_IN: 'CANCEL_MATERIAL_REPAIR_IN',
  // 物料形态化（2026-09-25）：送修转移进供应商仓 / 核销恢复 + 维修补料耗用/恢复
  MATERIAL_REPAIR_STOCK_IN: 'MATERIAL_REPAIR_STOCK_IN', CANCEL_MATERIAL_REPAIR_STOCK_IN: 'CANCEL_MATERIAL_REPAIR_STOCK_IN',
  MATERIAL_REPAIR_COMPONENT: 'MATERIAL_REPAIR_COMPONENT', CANCEL_MATERIAL_REPAIR_COMPONENT: 'CANCEL_MATERIAL_REPAIR_COMPONENT',
  // 供应商清算
  SETTLEMENT_RETURN_IN: 'SETTLEMENT_RETURN_IN', SETTLEMENT_RETURN_OUT: 'SETTLEMENT_RETURN_OUT',
  // 初始化
  INIT: 'INIT',
  // 品质重分类
  RECLASSIFY_OUT: 'RECLASSIFY_OUT', RECLASSIFY_IN: 'RECLASSIFY_IN',
  CANCEL_RECLASSIFY_OUT: 'CANCEL_RECLASSIFY_OUT', CANCEL_RECLASSIFY_IN: 'CANCEL_RECLASSIFY_IN',
  // 盘点
  STOCK_TAKE_IN: 'STOCK_TAKE_IN', STOCK_TAKE_OUT: 'STOCK_TAKE_OUT',
  // 退货整理
  RETURN_SORT_OUT: 'RETURN_SORT_OUT', RETURN_SORT_IN: 'RETURN_SORT_IN',
  CANCEL_RETURN_SORT_OUT: 'CANCEL_RETURN_SORT_OUT', CANCEL_RETURN_SORT_IN: 'CANCEL_RETURN_SORT_IN',
  // 报损
  LOSS_OUT: 'LOSS_OUT', CANCEL_LOSS_OUT: 'CANCEL_LOSS_OUT'
} as const

export const StockChangeTypeLabel: Record<string, string> = {
  [StockChangeType.PURCHASE_IN]: '采购入库',
  [StockChangeType.PURCHASE_UN_AUDIT]: '采购反审核',
  [StockChangeType.RETURN_OUT]: '退货出库',
  [StockChangeType.RETURN_UN_AUDIT]: '退货反审核',
  [StockChangeType.PURCHASE_EXCHANGE_OUT]: '采购换货退回出库',
  [StockChangeType.PURCHASE_EXCHANGE_IN]: '采购换货入库',
  [StockChangeType.PURCHASE_EXCHANGE_UN_AUDIT]: '采购换货反审核',
  [StockChangeType.SALE_OUT]: '销售出库',
  [StockChangeType.SALE_OUT_UN_AUDIT]: '销售反审核',
  [StockChangeType.SALE_RETURN_IN]: '销售退货入库',
  [StockChangeType.SALE_RETURN_UN_AUDIT]: '销售退货反审核',
  [StockChangeType.EXCHANGE_IN]: '换货退回入库',
  [StockChangeType.EXCHANGE_OUT]: '换货出库',
  [StockChangeType.EXCHANGE_UN_AUDIT]: '换货反审核',
  [StockChangeType.MOVE_OUT]: '移仓出库',
  [StockChangeType.MOVE_IN]: '移仓入库',
  [StockChangeType.OTHER_IN]: '其他入库',
  [StockChangeType.OTHER_OUT]: '其他出库',
  [StockChangeType.CANCEL_IN]: '取消入库',
  [StockChangeType.CANCEL_OUT]: '取消出库',
  [StockChangeType.OUTSOURCE_DELIVERY_OUT]: '委外发料出库',
  [StockChangeType.OUTSOURCE_FINISH_IN]: '委外收货入库',
  [StockChangeType.OUTSOURCE_RETURN_OUT]: '委外退料出',
  [StockChangeType.OUTSOURCE_RETURN_OUT_UN_AUDIT]: '委外退料出反审核',
  [StockChangeType.OUTSOURCE_DEFECT_RETURN]: '委外退不良',
  [StockChangeType.OUTSOURCE_ROLLBACK]: '收货回滚',
  [StockChangeType.OUTSOURCE_CONSUME]: '收货扣料',
  [StockChangeType.CANCEL_OUTSOURCE_CONSUME]: '取消收货扣料',
  [StockChangeType.OUTSOURCE_DEFECT_RETURN_UN_AUDIT]: '委外退不良反审核',
  [StockChangeType.OUTSOURCE_DEFECT_IN]: '成品加工退货入委外仓',
  [StockChangeType.CANCEL_OUTSOURCE_DEFECT_IN]: '核销成品加工退货',
  [StockChangeType.OUTSOURCE_BACK_CONSUME]: '返回单核销在厂成品',
  [StockChangeType.CANCEL_OUTSOURCE_BACK_CONSUME]: '返回单恢复在厂成品',
  [StockChangeType.OUTSOURCE_BACK_IN]: '修好成品回仓',
  [StockChangeType.CANCEL_OUTSOURCE_BACK_IN]: '修好成品扣回',
  [StockChangeType.OUTSOURCE_BACK_MATERIAL]: '返回单用料出仓',
  [StockChangeType.CANCEL_OUTSOURCE_BACK_MATERIAL]: '返回单用料恢复',
  // 2026-09-29 用户口径：加工侧「成品维修退货」叶子改文案「客户售后」⇒ 相关库存变动标签同步（物料侧另有自己的标签）
  [StockChangeType.OUTSOURCE_REPAIR_STOCK_IN]: '客户售后入委外仓',
  [StockChangeType.CANCEL_OUTSOURCE_REPAIR_STOCK_IN]: '核销客户售后',
  [StockChangeType.OUTSOURCE_REPAIR_MATERIAL]: '维修用料出仓',
  [StockChangeType.CANCEL_OUTSOURCE_REPAIR_MATERIAL]: '维修用料恢复',
  [StockChangeType.OUTSOURCE_REPAIR_OUT]: '委外维修出库',
  [StockChangeType.OUTSOURCE_REPAIR_OUT_UN_AUDIT]: '委外维修出库反审核',
  [StockChangeType.OUTSOURCE_REPAIR_IN]: '委外维修入库',
  [StockChangeType.CANCEL_OUTSOURCE_REPAIR_IN]: '取消委外维修入库',
  [StockChangeType.OUTSOURCE_CANCEL_DELIVERY]: '取消发料',
  [StockChangeType.OUTSOURCE_EDIT_ROLLBACK]: '编辑回滚-发料',
  [StockChangeType.DELIVERY_OUT]: '发料出',
  [StockChangeType.DELIVERY_IN]: '发料入',
  [StockChangeType.TRANSFER_OUT]: '调拨出',
  [StockChangeType.TRANSFER_IN]: '调拨入',
  [StockChangeType.CANCEL_DELIVERY]: '取消发料',
  [StockChangeType.CANCEL_TRANSFER_OUT]: '取消调拨出',
  [StockChangeType.CANCEL_TRANSFER_IN]: '取消调拨入',
  [StockChangeType.RETURN_IN]: '退料入',
  [StockChangeType.CANCEL_RETURN_IN]: '取消退料入',
  [StockChangeType.RECEIVE_IN]: '物料收货入',
  [StockChangeType.DEFECT_OUT]: '物料退不良出',
  [StockChangeType.CANCEL_RECEIVE_IN]: '取消物料收货入',
  [StockChangeType.CANCEL_DEFECT_OUT]: '取消物料退不良出',
  [StockChangeType.OUTSOURCE_COMPONENT_CONSUME]: '委外收货扣子物料',
  [StockChangeType.CANCEL_OUTSOURCE_COMPONENT_CONSUME]: '退审恢复子物料',
  [StockChangeType.MATERIAL_RETURN_OUT]: '委外物料退货出',
  [StockChangeType.CANCEL_MATERIAL_RETURN_OUT]: '取消委外物料退货出',
  [StockChangeType.MATERIAL_REPAIR_OUT]: '委外物料维修出',
  [StockChangeType.MATERIAL_REPAIR_OUT_UN_AUDIT]: '委外物料维修出反审核',
  [StockChangeType.MATERIAL_REPAIR_IN]: '委外物料维修入',
  [StockChangeType.CANCEL_MATERIAL_REPAIR_IN]: '取消委外物料维修入',
  [StockChangeType.MATERIAL_REPAIR_STOCK_IN]: '物料维修送修入供应商仓',
  [StockChangeType.CANCEL_MATERIAL_REPAIR_STOCK_IN]: '核销物料维修送修',
  [StockChangeType.MATERIAL_REPAIR_COMPONENT]: '维修补料耗用',
  [StockChangeType.CANCEL_MATERIAL_REPAIR_COMPONENT]: '维修补料恢复',
  [StockChangeType.SETTLEMENT_RETURN_IN]: '清算退料入',
  [StockChangeType.SETTLEMENT_RETURN_OUT]: '清算退料出',
  [StockChangeType.INIT]: '期初导入',
  [StockChangeType.RECLASSIFY_OUT]: '重分类出',
  [StockChangeType.RECLASSIFY_IN]: '重分类入',
  [StockChangeType.CANCEL_RECLASSIFY_OUT]: '取消重分类出',
  [StockChangeType.CANCEL_RECLASSIFY_IN]: '取消重分类入',
  [StockChangeType.STOCK_TAKE_IN]: '盘点盘盈',
  [StockChangeType.STOCK_TAKE_OUT]: '盘点盘亏',
  [StockChangeType.RETURN_SORT_OUT]: '退货整理出',
  [StockChangeType.RETURN_SORT_IN]: '退货整理入',
  [StockChangeType.CANCEL_RETURN_SORT_OUT]: '取消退货整理出',
  [StockChangeType.CANCEL_RETURN_SORT_IN]: '取消退货整理入',
  [StockChangeType.LOSS_OUT]: '报损出库',
  [StockChangeType.CANCEL_LOSS_OUT]: '取消报损出'
}

/**
 * 把 code→label 映射转成下拉选项数组（`{ code, label }`）—— 统一入口。
 * 用途：口径为「接口只回 code、前端映射中文」后，各页下拉不再请求后端字典接口，直接由此生成。
 */
export function codeLabelOptions(map: Record<string, string>): { code: string; label: string }[] {
  return Object.entries(map).map(([code, label]) => ({ code, label }))
}

/**
 * 变动类型 tag 颜色（按 code 判定，**不再依赖中文字符串 includes**，避免后端改文案就失效）
 * 入库/恢复类=绿，出库/退货/扣减类=红，其余=灰
 */
export function stockChangeTypeTag(code?: string): 'success' | 'danger' | 'info' {
  if (!code) return 'info'
  if (code.endsWith('_IN') || code.includes('ROLLBACK')) return 'success'
  if (code.endsWith('_OUT') || code.includes('RETURN') || code.includes('CONSUME') || code.includes('LOSS')) return 'danger'
  return 'info'
}

/** 库存流水关联单据类型（对应后端 RelatedBillType 枚举，DB 存 code；原由后端回 relatedBillTypeLabel） */
export const RelatedBillType = {
  PURCHASE_ORDER: 'PURCHASE_ORDER', PURCHASE_INBOUND: 'PURCHASE_INBOUND', PURCHASE_RETURN: 'PURCHASE_RETURN',
  PURCHASE_EXCHANGE: 'PURCHASE_EXCHANGE',
  SALE_ORDER: 'SALE_ORDER', SALE_OUTBOUND: 'SALE_OUTBOUND', SALE_RETURN: 'SALE_RETURN', SALE_EXCHANGE: 'SALE_EXCHANGE',
  WAREHOUSE_MOVE: 'WAREHOUSE_MOVE', WAREHOUSE_MOVE_UN_AUDIT: 'WAREHOUSE_MOVE_UN_AUDIT',
  OTHER_IO: 'OTHER_IO',
  OUTSOURCE_DELIVERY: 'OUTSOURCE_DELIVERY', OUTSOURCE_RETURN: 'OUTSOURCE_RETURN', OUTSOURCE_ORDER: 'OUTSOURCE_ORDER',
  OUTSOURCE_DEFECT: 'OUTSOURCE_DEFECT', OUTSOURCE_MATERIAL_RETURN: 'OUTSOURCE_MATERIAL_RETURN',
  OUTSOURCE_REPAIR: 'OUTSOURCE_REPAIR',
  OUTSOURCE_MATERIAL_REPAIR: 'OUTSOURCE_MATERIAL_REPAIR',
  MATERIAL_IO: 'MATERIAL_IO', SUPPLIER_SETTLEMENT: 'SUPPLIER_SETTLEMENT',
  PRODUCT_RECLASSIFY: 'PRODUCT_RECLASSIFY', RETURN_SORT: 'RETURN_SORT', STOCK_TAKE: 'STOCK_TAKE',
  INVENTORY_STOCK_LOSS: 'INVENTORY_STOCK_LOSS', OUTSOURCE_STOCK_LOSS: 'OUTSOURCE_STOCK_LOSS'
} as const

export const RelatedBillTypeLabel: Record<string, string> = {
  [RelatedBillType.PURCHASE_ORDER]: '采购单',
  [RelatedBillType.PURCHASE_INBOUND]: '采购入库',
  [RelatedBillType.PURCHASE_RETURN]: '采购退货单',
  [RelatedBillType.PURCHASE_EXCHANGE]: '采购换货单',
  [RelatedBillType.SALE_ORDER]: '销售单',
  [RelatedBillType.SALE_OUTBOUND]: '销售出库',
  [RelatedBillType.SALE_RETURN]: '销售退货单',
  [RelatedBillType.SALE_EXCHANGE]: '销售换货单',
  [RelatedBillType.WAREHOUSE_MOVE]: '移仓单',
  [RelatedBillType.WAREHOUSE_MOVE_UN_AUDIT]: '移仓单(反审核)',
  [RelatedBillType.OTHER_IO]: '其他出入库',
  [RelatedBillType.OUTSOURCE_DELIVERY]: '委外发料',
  [RelatedBillType.OUTSOURCE_RETURN]: '委外退货',
  [RelatedBillType.OUTSOURCE_ORDER]: '委外加工',
  [RelatedBillType.OUTSOURCE_DEFECT]: '退不良',
  [RelatedBillType.OUTSOURCE_MATERIAL_RETURN]: '委外物料退货',
  [RelatedBillType.OUTSOURCE_REPAIR]: '委外维修退货',
  [RelatedBillType.OUTSOURCE_MATERIAL_REPAIR]: '委外物料维修',
  [RelatedBillType.MATERIAL_IO]: '物料收发',
  [RelatedBillType.SUPPLIER_SETTLEMENT]: '供应商清算',
  [RelatedBillType.PRODUCT_RECLASSIFY]: '品质重分类',
  [RelatedBillType.RETURN_SORT]: '退货整理',
  [RelatedBillType.STOCK_TAKE]: '库存盘点',
  [RelatedBillType.INVENTORY_STOCK_LOSS]: '成品报损',
  [RelatedBillType.OUTSOURCE_STOCK_LOSS]: '物料报损'
}

/** 报损原因（对应后端 LossReason 枚举，DB 存 code；成品报损与委外物料报损共用） */
export const LossReason = {
  DAMAGE: 'DAMAGE', EXPIRED: 'EXPIRED', LOST: 'LOST', QUALITY: 'QUALITY', OTHER: 'OTHER'
} as const

export const LossReasonLabel: Record<string, string> = {
  [LossReason.DAMAGE]: '破损',
  [LossReason.EXPIRED]: '变质过期',
  [LossReason.LOST]: '丢失',
  [LossReason.QUALITY]: '质量不合格',
  [LossReason.OTHER]: '其他'
}

/* 2026-09-15：BizType / BizTypeLabel 已随「经营分析 → 利润表」下线一并删除（仅该页明细钻取使用） */

export const INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY = 'inventoryWarehouseMoveListDirty'
/** 物料移仓单列表脏标志（2026-09-24 新增，与成品移仓各自独立） */
export const INVENTORY_MATERIAL_MOVE_DIRTY_KEY = 'inventoryMaterialMoveListDirty'
export const INVENTORY_RECLASSIFY_DIRTY_KEY = 'inventoryReclassifyListDirty'
// 2026-09-20（F7-174/F7-184）补齐：以下列表原先只有 onMounted、不带脏标志，
// 从新增/详情返回（keep-alive 复用组件）时不会重新拉取，列表会停留在旧数据。
export const INVENTORY_RETURN_SORT_DIRTY_KEY = 'inventoryReturnSortListDirty'
export const INVENTORY_STOCK_LOSS_DIRTY_KEY = 'inventoryStockLossListDirty'

/** 物料报损（委外仓/辅料仓）：详情页保存/审核后回列表时的脏标志。
 *  2026-09-24 补：此前只有成品报损有脏标志，物料报损列表在 keep-alive 内返回时不会刷新（保存后看到旧数据）。
 *  现在详情页（草稿就地编辑）与新增页统一置该标志，列表 onActivated 按需重拉。 */
export const OUTSOURCE_STOCK_LOSS_DIRTY_KEY = 'outsourceStockLossListDirty'
export const SYSTEM_MENU_DIRTY_KEY = 'systemMenuListDirty'
export const SYSTEM_ROLE_DIRTY_KEY = 'systemRoleListDirty'
export const SYSTEM_USER_DIRTY_KEY = 'systemUserListDirty'
export const FINANCE_BILL_DIRTY_KEY = 'financeBillListDirty'
export const DEV_PROJECT_DIRTY_KEY = 'devProjectListDirty'
export const DEV_MATERIAL_DIRTY_KEY = 'devMaterialListDirty'
export const SUPPLIER_DIRTY_KEY = 'supplierListDirty'
export const SALE_EXCHANGE_DIRTY_KEY = 'saleExchangeListDirty'
export const PURCHASE_EXCHANGE_DIRTY_KEY = 'purchaseExchangeListDirty'



