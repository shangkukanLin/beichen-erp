import request from '@/utils/request'

export interface PageResult<T> { records: T[]; total: number; current: number; size: number }

export interface FinanceAccount { id?: number; accountName?: string; accountType?: string; bankName?: string; accountNo?: string; openingBalance?: number; balance?: number; status?: number }
export interface FinanceReceivable { id?: number; billNo?: string; customerId?: number; customerName?: string; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string; status?: string }
export interface FinancePayable { id?: number; billNo?: string; supplierId?: number; supplierName?: string; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string; status?: string }
export interface FinanceCashflow { id?: number; flowNo?: string; accountId?: number; accountName?: string; flowType?: string; relatedBillNo?: string; income?: number; expense?: number; balance?: number; createTime?: string }

export interface FinanceReceipt { id?: number; code?: string; customerId?: number; customerName?: string; accountId?: number; accountName?: string; receiptDate?: string; amount?: number; status?: string; remark?: string }
export interface FinanceReceiptItem { id?: number; receiptId?: number; receivableId?: number; receivableBillNo?: string; thisAmount?: number; remark?: string }
export interface FinancePayment { id?: number; code?: string; supplierId?: number; supplierName?: string; accountId?: number; accountName?: string; paymentDate?: string; amount?: number; status?: string; remark?: string; attachUrl?: string }
export interface FinancePaymentItem { id?: number; paymentId?: number; payableId?: number; payableBillNo?: string; thisAmount?: number; remark?: string }
export interface FinanceBill { id?: number; billNo?: string; billType?: string; partnerId?: number; partnerName?: string; periodStart?: string; periodEnd?: string; totalAmount?: number; paidAmount?: number; unpaidAmount?: number; status?: string }
export interface FinanceBillItem { id?: number; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string }

export function getAccountPage(params?: any) { return request.get<PageResult<FinanceAccount>>('/finance/account/page', { params }) }
export function createAccount(data: any) { return request.post<void>('/finance/account', data) }
export function updateAccount(data: any) { return request.put<void>('/finance/account', data) }

export function getReceivablePage(params: any) { return request.get<PageResult<FinanceReceivable>>('/finance/receivable/page', { params }) }
export function getUnpaidReceivables(customerId: number) { return request.get<FinanceReceivable[]>('/finance/receivable/unpaid', { params: { customerId } }) }

export function getPayablePage(params: any) { return request.get<PageResult<FinancePayable>>('/finance/payable/page', { params }) }
export function getUnpaidPayables(supplierId: number) { return request.get<FinancePayable[]>('/finance/payable/unpaid', { params: { supplierId } }) }

export function getCashflowPage(params: any) { return request.get<PageResult<FinanceCashflow>>('/finance/cashflow/page', { params }) }

export interface FinanceExpense { id?: number; expenseNo?: string; expenseType?: string; amount?: number; expenseDate?: string; accountId?: number; accountName?: string; status?: string; remark?: string; createTime?: string }
export function getExpensePage(params?: any) { return request.get<PageResult<FinanceExpense>>('/finance/expense/page', { params }) }
export function createExpense(data: any) { return request.post<void>('/finance/expense', data) }
export function updateExpense(data: any) { return request.put<void>('/finance/expense', data) }
export function auditExpense(id: number) { return request.post<void>(`/finance/expense/${id}/audit`) }
export function unAuditExpense(id: number) { return request.post<void>(`/finance/expense/${id}/unAudit`) }
export function cancelExpense(id: number) { return request.post<void>(`/finance/expense/${id}/cancel`) }

// 发票管理（税务口径：销项/进项发票登记，登记即生效，作废仅标记）
export interface FinanceInvoice { id?: number; invoiceNo?: string; direction?: string; invoiceKind?: string; invoiceDate?: string; partnerName?: string; amount?: number; taxRate?: number; taxAmount?: number; totalAmount?: number; sourceBillCode?: string; remark?: string; status?: string; createTime?: string }
export function getInvoicePage(params?: any) { return request.get<PageResult<FinanceInvoice>>('/finance/invoice/page', { params }) }
export function createInvoice(data: any) { return request.post<void>('/finance/invoice', data) }
export function updateInvoice(data: any) { return request.put<void>('/finance/invoice', data) }
export function cancelInvoice(id: number) { return request.post<void>(`/finance/invoice/${id}/cancel`) }

export function getReceiptPage(params: any) { return request.get<PageResult<FinanceReceipt>>('/finance/receipt/page', { params }) }
export function getReceiptItems(id: number) { return request.get<FinanceReceiptItem[]>(`/finance/receipt/${id}/items`) }
export function createReceipt(data: any) { return request.post<void>('/finance/receipt', data) }
export function auditReceipt(id: number) { return request.put<void>(`/finance/receipt/${id}/audit`) }
export function cancelReceipt(id: number) { return request.put<void>(`/finance/receipt/${id}/cancel`) }
export function unAuditReceipt(id: number) { return request.put<void>(`/finance/receipt/${id}/un-audit`) }

export function getPaymentPage(params: any) { return request.get<PageResult<FinancePayment>>('/finance/payment/page', { params }) }
export function getPaymentItems(id: number) { return request.get<FinancePaymentItem[]>(`/finance/payment/${id}/items`) }
export function createPayment(data: any) { return request.post<void>('/finance/payment', data) }
export function auditPayment(id: number) { return request.put<void>(`/finance/payment/${id}/audit`) }
export function cancelPayment(id: number) { return request.put<void>(`/finance/payment/${id}/cancel`) }
export function unAuditPayment(id: number) { return request.put<void>(`/finance/payment/${id}/un-audit`) }

export function getBillPage(params: any) { return request.get<PageResult<FinanceBill>>('/finance/bill/page', { params }) }
export function getBill(id: number) { return request.get<FinanceBill>(`/finance/bill/${id}`) }
export function getBillItems(id: number) { return request.get<FinanceBillItem[]>(`/finance/bill/${id}/items`) }
export function generateBill(data: any) { return request.post<FinanceBill>('/finance/bill/generate', data) }
export function auditBill(id: number) { return request.post<void>(`/finance/bill/${id}/audit`) }
export function unAuditBill(id: number) { return request.post<void>(`/finance/bill/${id}/unAudit`) }
export function cancelBill(id: number) { return request.post<void>(`/finance/bill/${id}/cancel`) }

