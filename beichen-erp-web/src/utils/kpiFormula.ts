/**
 * 经营类 KPI 的「计算公式」文案（唯一来源，供卡片悬停问号展示）。
 *
 * 使用方：
 *   - 首页「经营总览」  `views/dashboard/index.vue`
 *   - 经营分析「经营概览」 `views/analysis/overview.vue`
 *
 * ⚠️ 口径变更时**只改这里**，并以后端 `FinanceAnalysisServiceImpl` 的注释为准
 * （2026-09-15 抽公共模块，避免两页各自维护导致漏改一处）。
 */
export const KPI_FORMULA = {
  sale: '销售金额 = 已审核销售单金额 − 销售退货金额 + 退货折损收款\n（销售按审核日、退货与折损按建单日归期）',
  purchase: '采购支出 = 已审核采购单金额 − 采购退货金额\n（采购按审核日、退货按建单日归期）\n注：采购入库属资产、不计入损益，故与净利润不互减',
  expense: '费用支出 = 已审核费用单金额（按费用日期归期）\n不含销售成本（销售成本已在净利润中扣减）',
  profit: '净利润 = 销售金额 − 销售成本 − 费用支出\n销售成本 = 销售出库成本 − 退货冲回成本\n（净销售数量 × 产品当前移动加权成本价）',
  margin: '净利率 = 净利润 ÷ 销售金额 × 100%\n（销售金额为 0 时不计算，显示 -）',
  /** 无「进货业务」权限时的采购卡提示 */
  noAuth: '无「进货业务」权限，不展示采购数据'
} as const

/** 「本年累计」卡片的区间前缀（固定 1/1 ~ 今天） */
export const YEAR_PREFIX = '本年 1 月 1 日 ~ 今天：'

/** 净利率（%）= 净利润 ÷ 销售金额 × 100；销售金额为 0 时返回 null（显示 "-"） */
export function marginPct(profit?: any, sale?: any): number | null {
  const s = Number(sale) || 0
  if (s === 0) return null
  return ((Number(profit) || 0) / s) * 100
}

/** 百分比展示：null → "-"，否则保留 2 位小数 + % */
export function fmtPct(v: number | null) { return v == null ? '-' : v.toFixed(2) + '%' }
