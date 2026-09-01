import request from '@/utils/request'

/** 首页待办与预警：各模块待审核数 + 盘点待办/超期 + 售后仓超期待整理 + 超期应收 */
export interface DashboardPending {
  counts?: {
    saleOrder?: number; saleReturn?: number; purchaseOrder?: number; purchaseReturn?: number
    outsourceOrder?: number; materialOrder?: number; warehouseMove?: number; otherIo?: number
    expense?: number; stockTake?: number
  }
  stockTake?: { pending?: number; overdue?: number; period?: string }
  returnSort?: { overdue?: number }
  overdueReceivable?: number
}

export function getDashboardPending() {
  return request.get<DashboardPending>('/dashboard/pending')
}
