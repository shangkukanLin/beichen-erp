import { ElMessageBox } from 'element-plus'

/**
 * 「账户余额不足，需用户确认后才继续」的统一口径（2026-10-09 用户需求：
 * 「扣款时如果余额不足，就给用户提示，用户确认后可通过」）。
 *
 * 后端在**动账之前**拦下并返回业务码 {@link OVERDRAFT_CODE}（HTTP 200 + `code=409`，本仓业务码约定）
 * ⇒ 没弹确认框就**绝不可能**扣成负数，也不会出现"提示了但钱其实已经扣了"。
 * 用户点「确认继续」后，调用方带 `allowOverdraft=true` **重发同一次请求**：
 * 后端放行、允许账户扣成负数，并在**资金流水备注**里留痕（可查是谁、当时余额多少、扣成了多少）。
 *
 * 提示文案**直接用后端给的原文**（含当前余额 / 本次扣款 / 扣后余额）—— 用户要能看见"会透支多少"，
 * 而不是只看到一句"余额不足"。
 */
export const OVERDRAFT_CODE = 409

/** 该错误是不是"余额不足需确认"（其它失败已由 request.ts 拦截器统一提示，调用方不必处理） */
export function isOverdraftNeedConfirm(e: any): boolean {
  return e?.code === OVERDRAFT_CODE
}

/** 弹确认框：true = 用户确认继续（调用方带 allowOverdraft 重试）；false = 取消（调用方直接 return） */
export async function confirmOverdraft(msg?: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(
      msg || '账户余额不足，继续将把账户扣成负数（透支）。是否继续？',
      '余额不足确认',
      { type: 'warning', confirmButtonText: '确认继续', cancelButtonText: '取消', distinguishCancelAndClose: true },
    )
    return true
  } catch {
    return false
  }
}
