/**
 * 数量工具（2026-09-16 用户要求：**物料与产品的数量都是整数**）
 *
 * 口径：
 * - 数量：一律整数（数据库数量列已统一为 DECIMAL(18,0)，前端输入与展示都取整）
 * - 金额 / 单价 / 税率 / 良率 / 损耗率 / 单位用量（比率）：仍保留小数，**不要**用本工具
 *
 * 用法：
 *   import { formatQty, toIntQty } from '@/utils/number'
 *   <span>{{ formatQty(row.quantity) }}</span>
 *   <el-input-number v-model="row.quantity" :precision="0" :step="1" />
 */
/** 数量取整（四舍五入）；非数字返回 0 */
export function toIntQty(v: any): number {
  const n = Number(v)
  return Number.isFinite(n) ? Math.round(n) : 0
}

/** 数量展示：整数文本；空值显示 fallback（默认 '0'） */
export function formatQty(v: any, fallback = '0'): string {
  if (v === null || v === undefined || v === '') return fallback
  const n = Number(v)
  return Number.isFinite(n) ? String(Math.round(n)) : fallback
}

/** 数量输入规范化：把可能带小数的输入即时取整（配合 el-input type="number" 使用） */
export function normalizeQty<T>(row: T, key: keyof T): void {
  ;(row as any)[key] = toIntQty((row as any)[key])
}
