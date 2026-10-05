/**
 * 库存取数（退换货四页的单一实现）—— 2026-10-04 审核 F7-271 收口。
 *
 * 背景：销售退货 / 销售换货 / 采购退货 / 采购换货四页各自 hand-roll 了一遍
 * `/warehouse/stock/page` + 客户端过滤 + 求和，且四处细节并不一致（品质传不传、pageSize、
 * filter 宽严），与后端 `stockService.getQuantity(warehouse, product, quality)` 构成
 * **同一业务量的两处实现** —— 口径漂移的温床。现收口到本文件。
 *
 * 口径（与后端审核时的 `changeStock` 缺省形态一致）：
 *   · 只统计 `stockForm === 'MATERIAL'` 的库存行；同一「仓 + 产品 + 品质」还可能有
 *     `PRODUCT_DEFECT` / `PRODUCT_REPAIR` 等其它形态的行，混加会高估上限；
 *   · **品质由调用方显式给出**，且要与审核实际使用的品质同源：
 *     销售退货 = 明细品质（后端缺省 PENDING）、销售换货 = 换出品质（后端缺省 A）、
 *     采购换货 = 退回品质（后端缺省 DEFECT）、采购退货 = 明细品质。
 *
 * 返回 `undefined` = 未知（参数不全或查询失败）⇒ 调用方显示占位符、**不阻塞录入**（不改动既有交互）。
 */
import request from '@/utils/request'

export interface StockPageRow {
  stockForm?: string
  qualityType?: string
  quantity?: number | string
}

/** 实时库存：该「仓库 + 产品 + 品质」下 stockForm=MATERIAL 的数量合计；未知返回 undefined */
export async function fetchStockQty(
  warehouseId?: number | string | null,
  productId?: number | string | null,
  qualityType?: string | null
): Promise<number | undefined> {
  if (!warehouseId || !productId) return undefined
  try {
    const params: Record<string, unknown> = { warehouseId, productId, pageSize: 500 }
    if (qualityType) params.qualityType = qualityType
    const res: any = await request.get<any, any>('/warehouse/stock/page', { params })
    const rows: StockPageRow[] = res?.records || []
    return rows
      .filter((x: StockPageRow) => !x.stockForm || x.stockForm === 'MATERIAL')
      .reduce((s: number, x: StockPageRow) => s + (Number(x.quantity) || 0), 0)
  } catch {
    return undefined
  }
}

/**
 * 明细行的数量上限：有来源 ⇒ `min(来源数量, 库存)`（与后端 `validate*Quantity` 同式）；
 * 无来源 ⇒ 库存；库存未知 ⇒ `undefined`（前端不设上限，交给后端拦 —— 与本改造前行为一致）。
 */
export function stockLimit(
  sourceQty: number | string | null | undefined,
  stock: number | undefined
): number | undefined {
  if (stock === undefined) return undefined
  return sourceQty == null ? stock : Math.min(Number(sourceQty), stock)
}
