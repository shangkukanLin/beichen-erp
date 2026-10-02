import request from '@/utils/request'

export interface PageResult<T> { records: T[]; total: number; current: number; size: number }

export interface FinanceAccount { id?: number; accountName?: string; accountType?: string; bankName?: string; accountNo?: string; openingBalance?: number; balance?: number; status?: number }
export interface FinanceReceivable { id?: number; billNo?: string; customerId?: number; customerName?: string; subjectType?: string; supplierId?: number; supplierName?: string; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string; status?: string
  /** 制单人（2026-09-23 用户口径：台账只显示制单人，无审核流程 ⇒ 无审核人） */
  createByName?: string }
export interface FinancePayable { id?: number; billNo?: string; supplierId?: number; supplierName?: string; supplierType?: string; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string; status?: string; transferredToReceivable?: number
  /** 制单人（2026-09-23 用户口径：台账只显示制单人，无审核流程 ⇒ 无审核人） */
  createByName?: string }
export interface FinanceCashflow { id?: number; flowNo?: string; accountId?: number; accountName?: string; flowType?: string; relatedBillNo?: string; income?: number; expense?: number; balance?: number; createTime?: string }

export interface FinanceReceipt { id?: number; code?: string; customerId?: number; customerName?: string; subjectType?: string; supplierId?: number; supplierName?: string; accountId?: number; accountName?: string; receiptDate?: string; amount?: number; status?: string; remark?: string
  /** 分款明细条数（2026-09-29 多账户；仅列表接口返回）：>1 时列表「账户」列显示「N 个账户」 */
  accountCount?: number
  /** 制单人 / 审核人（2026-09-23 单据详情口径；收款单无审核流程 ⇒ 审核人通常为空） */
  createByName?: string; auditorName?: string }
export interface FinanceReceiptItem { id?: number; receiptId?: number; receivableId?: number; receivableBillNo?: string; thisAmount?: number; remark?: string }
/**
 * 收款单**分款明细**（2026-09-29 多账户收款）：一行 = 一个账户本次收到的钱。
 * <p>收款金额（主表 amount）= 各行 amount 合计；审核时按行各写一条资金流水（账户余额各自累计），
 * 反审核按行各写一条冲正流水。</p>
 */
export interface FinanceReceiptAccount { id?: number; receiptId?: number; accountId?: number; accountName?: string; amount?: number; remark?: string }
export interface FinancePayment { id?: number; code?: string; supplierId?: number; supplierName?: string; supplierType?: string; accountId?: number; accountName?: string; paymentDate?: string; amount?: number; status?: string; remark?: string; attachUrl?: string
  /** 分款明细条数（2026-09-29 多账户；仅列表接口返回）：>1 时列表「账户」列显示「N 个账户」 */
  accountCount?: number
  /** 制单人 / 审核人（2026-09-23 单据详情口径；付款单无审核流程 ⇒ 审核人通常为空） */
  createByName?: string; auditorName?: string }
export interface FinancePaymentItem { id?: number; paymentId?: number; payableId?: number; payableBillNo?: string; thisAmount?: number; remark?: string }
/**
 * 付款单**分款明细**（2026-09-29 多账户付款，与收款侧 FinanceReceiptAccount 对称）：
 * 一行 = 一个账户本次付出的钱。
 * <p>付款金额（主表 amount）= 各行 amount 合计；审核时按行各写一条资金流水（逐账户校验余额），
 * 反审核按行各写一条冲正流水。</p>
 */
export interface FinancePaymentAccount { id?: number; paymentId?: number; accountId?: number; accountName?: string; amount?: number; remark?: string }
export interface FinanceBill { id?: number; billNo?: string; billType?: string; partnerId?: number; partnerName?: string; periodStart?: string; periodEnd?: string; totalAmount?: number; paidAmount?: number; unpaidAmount?: number; status?: string
  /** 制单人 / 审核人（2026-09-23 单据详情口径；账单无审核流程 ⇒ 审核人通常为空） */
  createByName?: string; auditorName?: string }
export interface FinanceBillItem { id?: number; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string }

export function getAccountPage(params?: any) { return request.get<PageResult<FinanceAccount>>('/finance/account/page', { params }) }
export function createAccount(data: any) { return request.post<void>('/finance/account', data) }
export function updateAccount(data: any) { return request.put<void>('/finance/account', data) }

export function getReceivablePage(params: any) { return request.get<PageResult<FinanceReceivable>>('/finance/receivable/page', { params }) }
/** 未结清应收：客户收款传 customerId；供应商收款（应付转应收）传 supplierId */
export function getUnpaidReceivables(customerId?: number, supplierId?: number) {
  return request.get<FinanceReceivable[]>('/finance/receivable/unpaid', { params: { customerId, supplierId } })
}

export function getPayablePage(params: any) { return request.get<PageResult<FinancePayable>>('/finance/payable/page', { params }) }
export function getUnpaidPayables(supplierId: number) { return request.get<FinancePayable[]>('/finance/payable/unpaid', { params: { supplierId } }) }

// ==================== 期 2（2026-09-19 读隔离）：付款 / 收款页改读「自身前缀」 ====================
/**
 * 应付汇总（付款页 Tab1「供应商汇总」）。原直读 `/finance/payable/supplier-summary`
 * （需 finance:payable）⇒ 只被授予 finance:payment 的用户会 403，现走付款页前缀（后端同一查询实现）。
 */
export function getPaymentPayableSummary() {
  return request.get<any[]>('/finance/payment/payable-summary')
}
/** 应付明细（供应商付款页）。原直读 `/finance/payable/page` */
export function getPaymentPayables(params: any) {
  return request.get<PageResult<FinancePayable>>('/finance/payment/payables', { params })
}
/** 未结清应付（新增付款弹窗的核销下拉）。原直读 `/finance/payable/unpaid` */
export function getPaymentUnpaidPayables(supplierId: number) {
  return request.get<FinancePayable[]>('/finance/payment/unpaid-payables', { params: { supplierId } })
}
/**
 * 未结清应收（收款单「核销明细」下拉）。原直读 `/finance/receivable/unpaid`（需 finance:receivable）
 * ⇒ 只被授予 finance:receipt 的用户会 403，现走收款页前缀（后端同一查询实现）。
 */
export function getReceiptUnpaidReceivables(customerId?: number, supplierId?: number) {
  return request.get<FinanceReceivable[]>('/finance/receipt/unpaid-receivables', { params: { customerId, supplierId } })
}
/**
 * 主体欠款汇总（2026-09-29）：新增收款页 / 详情页草稿态「选完客户/供应商后显示**到期欠款 + 总欠款**」。
 * <p>口径（严格镜像应付侧 `PayableQuery.supplierSummary`）：未结清（UNSETTLED/PARTIAL）且 `amount > 0`
 * —— 天然排除预收/预付台账 ADVANCE 与负数冲减行；**到期 = `due_date` < 今天**（当天到期不算）；
 * **无到期日的单据不计入到期**（`noDueCount/noDueAmount` 供界面解释"为什么到期是 0"）。</p>
 * <p>走收款页前缀（`/finance/receipt/party-summary`）⇒ 只要求 `finance:receipt`，避免跨模块 403。</p>
 */
export interface PartyDebtSummary { subjectType?: string; partyId?: number; unpaidAmount?: number; overdueAmount?: number; noDueAmount?: number; noDueCount?: number; billCount?: number; asOf?: string }
export function getReceiptPartySummary(params: { subjectType?: string; customerId?: number; supplierId?: number }) {
  return request.get<PartyDebtSummary>('/finance/receipt/party-summary', { params })
}
/**
 * 单供应商欠款汇总（2026-09-29）：新增付款页「选完供应商后显示**到期欠款 + 总欠款**」。
 * <p>口径与应付侧 `PayableQuery.supplierSummary`（唯一口径）同源：未结清 + 未付额>0 ⇒ 天然排除预付台账 ADVANCE；
 * **到期 = `due_date` < 今天**（当天到期不算）；**无到期日的单据不计入**（`noDueCount/noDueAmount` 供界面解释）。</p>
 * <p>走付款页前缀（`/finance/payment/party-summary`）⇒ 只要求 `finance:payment`，避免跨模块 403。</p>
 */
export interface PayableDebtSummary { partyId?: number; supplierType?: string; unpaidAmount?: number; overdueAmount?: number; noDueAmount?: number; noDueCount?: number; billCount?: number; asOf?: string }
export function getPaymentPartySummary(params: { supplierId?: number; supplierType?: string }) {
  return request.get<PayableDebtSummary>('/finance/payment/party-summary', { params })
}

export function getCashflowPage(params: any) { return request.get<PageResult<FinanceCashflow>>('/finance/cashflow/page', { params }) }

export interface FinanceExpense { id?: number; expenseNo?: string; expenseType?: string; amount?: number; expenseDate?: string; accountId?: number; accountName?: string; status?: string; remark?: string; createTime?: string; createByName?: string; auditorId?: number; auditorName?: string }
export function getExpensePage(params?: any) { return request.get<PageResult<FinanceExpense>>('/finance/expense/page', { params }) }
// 2026-09-24：补单取（后端 FinanceExpenseController#getById 早已存在，前端此前没有包装）——
// 费用管理详情页要用它加载单据（新增/编辑弹窗原先只走列表数据，不需要）
export function getExpense(id: number) { return request.get<FinanceExpense>(`/finance/expense/${id}`) }
export function createExpense(data: any) { return request.post<void>('/finance/expense', data) }
export function updateExpense(data: any) { return request.put<void>('/finance/expense', data) }
export function auditExpense(id: number) { return request.put<void>(`/finance/expense/${id}/audit`) }
export function unAuditExpense(id: number) { return request.put<void>(`/finance/expense/${id}/un-audit`) }
export function cancelExpense(id: number) { return request.post<void>(`/finance/expense/${id}/cancel`) }

// 发票管理（税务口径：销项/进项发票登记，登记即生效，作废仅标记）
export interface FinanceInvoice { id?: number; invoiceNo?: string; direction?: string; invoiceKind?: string; invoiceDate?: string; partnerName?: string; amount?: number; taxRate?: number; taxAmount?: number; totalAmount?: number; sourceBillCode?: string; remark?: string; status?: string; createTime?: string }
export function getInvoicePage(params?: any) { return request.get<PageResult<FinanceInvoice>>('/finance/invoice/page', { params }) }
export function createInvoice(data: any) { return request.post<void>('/finance/invoice', data) }
export function updateInvoice(data: any) { return request.put<void>('/finance/invoice', data) }
export function cancelInvoice(id: number) { return request.post<void>(`/finance/invoice/${id}/cancel`) }

export function getReceiptPage(params: any) { return request.get<PageResult<FinanceReceipt>>('/finance/receipt/page', { params }) }
export function getReceiptItems(id: number) { return request.get<FinanceReceiptItem[]>(`/finance/receipt/${id}/items`) }
/** 分款明细（2026-09-29 多账户）：详情页「收款账户」卡片 / 草稿态就地编辑都用它 */
export function getReceiptAccounts(id: number) { return request.get<FinanceReceiptAccount[]>(`/finance/receipt/${id}/accounts`) }
/**
 * 建单：`{ receipt:{…}, accounts:[{accountId,amount,remark}], items:[{receivableId,thisAmount}] }`。
 * <p>2026-09-29：`accounts` 支撑多账户分款；`items` 可为空（核销开关关闭 = 只记收款，未核销差额落预收台账）。</p>
 */
export function createReceipt(data: any) { return request.post<void>('/finance/receipt', data) }
/** 草稿就地修改（2026-09-29 用户口径「加草稿可编辑」）：payload 与建单一致，分款/核销明细整体替换 */
export function updateReceipt(id: number, data: any) { return request.put<void>(`/finance/receipt/${id}`, data) }
export function auditReceipt(id: number) { return request.put<void>(`/finance/receipt/${id}/audit`) }
export function cancelReceipt(id: number) { return request.put<void>(`/finance/receipt/${id}/cancel`) }
export function unAuditReceipt(id: number) { return request.put<void>(`/finance/receipt/${id}/un-audit`) }

export function getPaymentPage(params: any) { return request.get<PageResult<FinancePayment>>('/finance/payment/page', { params }) }
export function getPaymentItems(id: number) { return request.get<FinancePaymentItem[]>(`/finance/payment/${id}/items`) }
/** 分款明细（2026-09-29 多账户付款）：详情页「付款账户」卡片 / 草稿态就地编辑都用它 */
export function getPaymentAccounts(id: number) { return request.get<FinancePaymentAccount[]>(`/finance/payment/${id}/accounts`) }
/**
 * 建单：`{ payment:{…}, accounts:[{accountId,amount,remark}], items:[{payableId,thisAmount}] }`。
 * <p>2026-09-29：`accounts` 支撑多账户分款；`items` 可为空（核销开关关闭 = 只记付款，未核销差额落预付台账）。</p>
 */
export function createPayment(data: any) { return request.post<void>('/finance/payment', data) }
/** 草稿就地修改（2026-09-29）：payload 与建单一致，分款/核销明细整体替换 */
export function updatePayment(id: number, data: any) { return request.put<void>(`/finance/payment/${id}`, data) }
export function auditPayment(id: number) { return request.put<void>(`/finance/payment/${id}/audit`) }
export function cancelPayment(id: number) { return request.put<void>(`/finance/payment/${id}/cancel`) }
export function unAuditPayment(id: number) { return request.put<void>(`/finance/payment/${id}/un-audit`) }

export function getBillPage(params: any) { return request.get<PageResult<FinanceBill>>('/finance/bill/page', { params }) }
export function getBill(id: number) { return request.get<FinanceBill>(`/finance/bill/${id}`) }
export function getBillItems(id: number) { return request.get<FinanceBillItem[]>(`/finance/bill/${id}/items`) }
export function generateBill(data: any) { return request.post<FinanceBill>('/finance/bill/generate', data) }
export function auditBill(id: number) { return request.put<void>(`/finance/bill/${id}/audit`) }
export function unAuditBill(id: number) { return request.put<void>(`/finance/bill/${id}/un-audit`) }
export function cancelBill(id: number) { return request.post<void>(`/finance/bill/${id}/cancel`) }

