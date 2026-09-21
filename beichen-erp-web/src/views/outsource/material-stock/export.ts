/**
 * 物料库存情况 —— 导出用的**纯数据拼装**（2026-09-22）。
 *
 * <p><b>为什么单独抽一个文件</b>：导出要出两个视角、第二个还是"按仓库分块"的结构，逻辑不短；
 * 抽成「不依赖 XLSX / 不依赖 Vue」的纯函数后，可以用 node 用例直接断言块结构 / 小计 / 隐藏规则
 * （见 tools/regression/verify-material-stock-export.mjs），不必靠人眼看 Excel 对不对。</p>
 *
 * <p><b>两个视角（同一份「仓库×物料」快照派生 ⇒ 数字必然一致）</b>：
 * ① 按物料跨仓汇总 —— 保持列表页导出原有的样子（一行一个物料）；
 * ② 按仓库分块 —— 每个仓库下有哪些物料、各多少（本次新增的用户诉求）。</p>
 *
 * <p>⚠️ <b>隐藏规则</b>：0 库存可以隐藏，**负库存绝不能隐藏** —— 委外仓允许「缺料强制出库」，
 * 负库存是业务事实而不是账错，必须显式可见（同一口径见 outsource/warehouse-detail.vue 的 negativeItems）。</p>
 */

/** 一行 = 一个（仓库 × 物料）的库存；字段与 /warehouse/stock/material-stock/page 对齐 */
export interface StockRow {
  warehouseId?: number | null
  warehouseName?: string | null
  warehouseCategory?: string | null
  materialId?: number | null
  materialName?: string | null
  materialTypeName?: string | null
  materialTypeSortOrder?: number | null
  unit?: string | null
  qtyGood?: number | null
  qtyDefect?: number | null
  totalQuantity?: number | null
  /** 仅「按物料汇总」形态的行自带（列表页 /material-summary/page），用于导出兜底时保持分布仓库数 */
  warehouseCount?: number | null
}

export interface MergeRange { s: { r: number; c: number }; e: { r: number; c: number } }
export interface ColWidth { wch: number }
export interface SheetSpec {
  name: string
  aoa: (string | number)[][]
  merges: MergeRange[]
  cols: ColWidth[]
}

export interface WarehouseBlock {
  warehouseId: number | null
  warehouseName: string
  isOutsource: boolean
  rows: StockRow[]
  good: number
  defect: number
  total: number
  /** 该仓负库存行数（抬头会标注总数，块内每行也带备注） */
  negative: number
}

export interface SheetOptions {
  /** 隐藏「良品与不良都为 0」的行；负数一律保留。默认 true */
  hideZero?: boolean
  /** 抬头里的导出时间文本；不传则用当前本地时间（便于用例断言固定值） */
  exportTime?: string
}

export interface GroupResult { blocks: WarehouseBlock[]; hiddenZero: number; negative: number }

const OUTSOURCE = 'OUTSOURCE'
const DEFAULT_SORT = 999

function num(v: unknown): number {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? n : 0
}

/** 行的小计：优先用后端回的 totalQuantity，缺失时按 良品 + 不良 兜底 */
export function rowTotal(r: StockRow): number {
  if (r == null) return 0
  return r.totalQuantity != null ? num(r.totalQuantity) : num(r.qtyGood) + num(r.qtyDefect)
}

/**
 * 委外仓判据：category = OUTSOURCE。
 * （自有物料仓是 category=INVENTORY + type=AUXILIARY —— 与列表页仓库下拉的过滤条件同一口径）
 */
export function isOutsourceWarehouse(r: StockRow): boolean {
  return String(r.warehouseCategory ?? '') === OUTSOURCE
}

/**
 * 按仓库分组（供「按仓库明细」sheet）：
 * - **委外仓在前、自有物料仓在后**；同类按 warehouseId 升序 ⇒ 结果可复现
 * - 仓内物料按「物料类型排序位 → 物料名称」排（与「委外仓库详情」的排序口径一致）
 * - hideZero 时隐藏小计为 0 的行并存下条数；**负数一律保留**（并计数）
 * - 某仓可见行为空 ⇒ **整块丢弃**（避免只有零库存的仓库留下空块）
 * - 没有 warehouseId 的行（汇总形态）不参与本视角
 */
export function groupByWarehouse(rows: StockRow[], opts: SheetOptions = {}): GroupResult {
  const hideZero = opts.hideZero !== false
  const groups = new Map<number, WarehouseBlock>()
  let hiddenZero = 0
  for (const r of rows ?? []) {
    if (r == null || r.warehouseId == null) continue
    const t = rowTotal(r)
    if (hideZero && t === 0) { hiddenZero++; continue }
    let g = groups.get(r.warehouseId)
    if (!g) {
      g = {
        warehouseId: r.warehouseId,
        warehouseName: String(r.warehouseName ?? ''),
        isOutsource: isOutsourceWarehouse(r),
        rows: [], good: 0, defect: 0, total: 0, negative: 0,
      }
      groups.set(r.warehouseId, g)
    }
    g.rows.push(r)
    g.good += num(r.qtyGood)
    g.defect += num(r.qtyDefect)
    g.total += t
    if (t < 0) g.negative++
  }

  const blocks = [...groups.values()].filter((b) => b.rows.length > 0)
  for (const b of blocks) {
    b.rows.sort((a, c) =>
      (num(a.materialTypeSortOrder ?? DEFAULT_SORT) - num(c.materialTypeSortOrder ?? DEFAULT_SORT))
      || String(a.materialName ?? '').localeCompare(String(c.materialName ?? ''), 'zh-Hans-CN'))
  }
  blocks.sort((a, c) =>
    (Number(c.isOutsource) - Number(a.isOutsource)) || (num(a.warehouseId) - num(c.warehouseId)))

  return { blocks, hiddenZero, negative: blocks.reduce((s, b) => s + b.negative, 0) }
}

interface MaterialAgg {
  materialId: number | null
  materialTypeName: string
  materialName: string
  unit: string
  good: number
  defect: number
  total: number
  sortOrder: number
  whIds: Set<number>
  whCountFromApi: number | null
}

/**
 * 按物料聚合（供「物料库存汇总」sheet）。
 * ⚠️ 分布仓库数：优先用接口已算好的 warehouseCount（兜底场景）；否则按出现过的 warehouseId 去重计数 ——
 * **含数量为 0 的仓**，这样才与列表页显示的数字一致（列表页的 warehouseCount 就是"有库存行的仓库数"）。
 */
export function aggregateByMaterial(rows: StockRow[]): MaterialAgg[] {
  const map = new Map<string, MaterialAgg>()
  for (const r of rows ?? []) {
    if (r == null) continue
    const key = r.materialId != null ? String(r.materialId) : 'name:' + String(r.materialName ?? '')
    let m = map.get(key)
    if (!m) {
      m = {
        materialId: r.materialId ?? null,
        materialTypeName: String(r.materialTypeName ?? ''),
        materialName: String(r.materialName ?? ''),
        unit: String(r.unit ?? ''),
        good: 0, defect: 0, total: 0,
        sortOrder: DEFAULT_SORT,
        whIds: new Set<number>(),
        whCountFromApi: null,
      }
      map.set(key, m)
    }
    if (!m.materialTypeName && r.materialTypeName) m.materialTypeName = String(r.materialTypeName)
    if (!m.unit && r.unit) m.unit = String(r.unit)
    m.good += num(r.qtyGood)
    m.defect += num(r.qtyDefect)
    m.total += rowTotal(r)
    if (r.materialTypeSortOrder != null) m.sortOrder = Math.min(m.sortOrder, num(r.materialTypeSortOrder))
    if (r.warehouseId != null) m.whIds.add(r.warehouseId)
    if (r.warehouseCount != null) {
      m.whCountFromApi = Math.max(m.whCountFromApi ?? 0, num(r.warehouseCount))
    }
  }
  const list = [...map.values()]
  list.sort((a, c) => (c.total - a.total) || (a.sortOrder - c.sortOrder)
    || a.materialName.localeCompare(c.materialName, 'zh-Hans-CN'))
  return list
}

/** 物料的分布仓库数（见 aggregateByMaterial 的口径说明） */
export function warehouseCountOf(m: MaterialAgg): number {
  return m.whCountFromApi != null ? m.whCountFromApi : m.whIds.size
}

/**
 * 拼出导出文件的所有 sheet：
 * ①「物料库存汇总」（行序同列表页：总库存降序）
 * ②「按仓库明细」（按仓库分块：仓库抬头 → 表头 → 物料行 → 该仓小计 → 空行）——
 *    没有可分的仓库（例如导出兜底时只有汇总形态数据）时**不产出该 sheet**。
 */
export function buildMaterialSheets(rows: StockRow[], opts: SheetOptions = {}): SheetSpec[] {
  const time = opts.exportTime ?? new Date().toLocaleString('zh-CN')
  const sheets: SheetSpec[] = []

  const mats = aggregateByMaterial(rows)
  const h1 = ['物料类型', '物料名称', '单位', '良品', '不良', '总库存', '分布仓库']
  const aoa1: (string | number)[][] = [
    [`物料库存汇总（导出时间：${time}，共 ${mats.length} 行）`],
    [],
    h1,
  ]
  for (const m of mats) {
    aoa1.push([m.materialTypeName || '—', m.materialName, m.unit || '—',
      m.good, m.defect, m.total, warehouseCountOf(m)])
  }
  sheets.push({
    name: '物料库存汇总',
    aoa: aoa1,
    merges: [{ s: { r: 0, c: 0 }, e: { r: 0, c: h1.length - 1 } }],
    cols: [{ wch: 16 }, { wch: 24 }, { wch: 8 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 }],
  })

  const g = groupByWarehouse(rows, opts)
  if (g.blocks.length > 0) {
    const h2 = ['物料类型', '物料名称', '单位', '良品', '不良', '小计', '备注']
    const lastCol = h2.length - 1
    const visibleRows = g.blocks.reduce((s, b) => s + b.rows.length, 0)
    const notes: string[] = []
    if (g.hiddenZero > 0) notes.push(`已隐藏 0 库存 ${g.hiddenZero} 条`)
    if (g.negative > 0) notes.push(`含负库存 ${g.negative} 条（缺料强制出库所致，非账错）`)
    const aoa2: (string | number)[][] = [
      ['物料库存（按仓库）'],
      [`导出时间：${time}　共 ${g.blocks.length} 个仓库 / ${visibleRows} 条` + (notes.length ? `（${notes.join('；')}）` : '')],
      [],
    ]
    const merges: MergeRange[] = [
      { s: { r: 0, c: 0 }, e: { r: 0, c: lastCol } },
      { s: { r: 1, c: 0 }, e: { r: 1, c: lastCol } },
    ]
    for (const b of g.blocks) {
      const titleRow = aoa2.length
      aoa2.push([`${b.warehouseName}（${b.isOutsource ? '委外仓' : '自有物料仓'}）`])
      merges.push({ s: { r: titleRow, c: 0 }, e: { r: titleRow, c: lastCol } })
      aoa2.push(h2)
      for (const r of b.rows) {
        const t = rowTotal(r)
        aoa2.push([String(r.materialTypeName ?? '—'), String(r.materialName ?? ''), String(r.unit ?? '—'),
          num(r.qtyGood), num(r.qtyDefect), t, t < 0 ? '负库存（缺料强制出库）' : ''])
      }
      aoa2.push(['小计', '', '', b.good, b.defect, b.total, ''])
      aoa2.push([])
    }
    sheets.push({
      name: '按仓库明细',
      aoa: aoa2,
      merges,
      cols: [{ wch: 14 }, { wch: 24 }, { wch: 8 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 26 }],
    })
  }

  return sheets
}
