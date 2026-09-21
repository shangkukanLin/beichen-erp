import request from '@/utils/request'

/** 首页待办与预警：各模块待审核数 + 盘点待办/超期 + 待整理品（成品仓 PENDING）超期待整理 + 超期应收 */
export interface DashboardPending {
  counts?: {
    saleOrder?: number; saleReturn?: number; purchaseOrder?: number; purchaseReturn?: number
    outsourceOrder?: number; materialOrder?: number; warehouseMove?: number; otherIo?: number
    expense?: number; stockTake?: number
  }
  stockTake?: { pending?: number; overdue?: number; period?: string }
  /** 物料仓盘点待办（2026-09-16 新增，与 stockTake 分开口径）：委外仓 + 自有物料仓 */
  materialTake?: { pending?: number; overdue?: number; period?: string }
  returnSort?: { overdue?: number }
  overdueReceivable?: number
}

export function getDashboardPending() {
  return request.get<DashboardPending>('/dashboard/pending')
}
