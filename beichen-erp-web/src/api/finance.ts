import request from '@/utils/request'

export interface PageResult<T> { records: T[]; total: number; current: number; size: number }

export interface FinanceAccount { id?: number; accountName?: string; accountType?: string; bankName?: string; accountNo?: string; balance?: number; status?: number }
export interface FinanceReceivable { id?: number; billNo?: string; customerId?: number; customerName?: string; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string; status?: string }
export interface FinancePayable { id?: number; billNo?: string; supplierId?: number; supplierName?: string; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string; status?: string }
export interface FinanceCashflow { id?: number; flowNo?: string; accountId?: number; accountName?: string; flowType?: string; relatedBillNo?: string; income?: number; expense?: number; balance?: number; createTime?: string }

export interface FinanceReceipt { id?: number; code?: string; customerId?: number; customerName?: string; accountId?: number; accountName?: string; receiptDate?: string; amount?: number; status?: string; remark?: string }
export interface FinanceReceiptItem { id?: number; receiptId?: number; receivableId?: number; receivableBillNo?: string; thisAmount?: number; remark?: string }
export interface FinancePayment { id?: number; code?: string; supplierId?: number; supplierName?: string; accountId?: number; accountName?: string; paymentDate?: string; amount?: number; status?: string; remark?: string; attachUrl?: string }
export interface FinancePaymentItem { id?: number; paymentId?: number; payableId?: number; payableBillNo?: string; thisAmount?: number; remark?: string }
export interface FinanceBill { id?: number; billNo?: string; billType?: string; partnerId?: number; partnerName?: string; periodStart?: string; periodEnd?: string; totalAmount?: number; paidAmount?: number; unpaidAmount?: number; status?: string }
export interface FinanceBillItem { id?: number; sourceBillType?: string; sourceBillNo?: string; amount?: number; paidAmount?: number; unpaidAmount?: number; dueDate?: string }

export function getAccountPage(params?: any) { return request.get<unknown, PageResult<FinanceAccount>>('/finance/account/page', { params }) }
export function createAccount(data: any) { return request.post<unknown, void>('/finance/account', data) }
export function updateAccount(data: any) { return request.put<unknown, void>('/finance/account', data) }

export function getReceivablePage(params: any) { return request.get<unknown, PageResult<FinanceReceivable>>('/finance/receivable/page', { params }) }
export function getUnpaidReceivables(customerId: number) { return request.get<unknown, FinanceReceivable[]>('/finance/receivable/unpaid', { params: { customerId } }) }

export function getPayablePage(params: any) { return request.get<unknown, PageResult<FinancePayable>>('/finance/payable/page', { params }) }
export function getUnpaidPayables(supplierId: number) { return request.get<unknown, FinancePayable[]>('/finance/payable/unpaid', { params: { supplierId } }) }

export function getCashflowPage(params: any) { return request.get<unknown, PageResult<FinanceCashflow>>('/finance/cashflow/page', { params }) }

export function getReceiptPage(params: any) { return request.get<unknown, PageResult<FinanceReceipt>>('/finance/receipt/page', { params }) }
export function getReceiptItems(id: number) { return request.get<unknown, FinanceReceiptItem[]>(`/finance/receipt/${id}/items`) }
export function createReceipt(data: any) { return request.post<unknown, void>('/finance/receipt', data) }
export function auditReceipt(id: number) { return request.put<unknown, void>(`/finance/receipt/${id}/audit`) }
export function cancelReceipt(id: number) { return request.put<unknown, void>(`/finance/receipt/${id}/cancel`) }

export function getPaymentPage(params: any) { return request.get<unknown, PageResult<FinancePayment>>('/finance/payment/page', { params }) }
export function getPaymentItems(id: number) { return request.get<unknown, FinancePaymentItem[]>(`/finance/payment/${id}/items`) }
export function createPayment(data: any) { return request.post<unknown, void>('/finance/payment', data) }
export function auditPayment(id: number) { return request.put<unknown, void>(`/finance/payment/${id}/audit`) }
export function cancelPayment(id: number) { return request.put<unknown, void>(`/finance/payment/${id}/cancel`) }

export function getBillPage(params: any) { return request.get<unknown, PageResult<FinanceBill>>('/finance/bill/page', { params }) }
export function getBillItems(id: number) { return request.get<unknown, FinanceBillItem[]>(`/finance/bill/${id}/items`) }
export function generateBill(data: any) { return request.post<unknown, FinanceBill>('/finance/bill/generate', data) }
