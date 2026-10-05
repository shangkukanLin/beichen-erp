/**
 * 「来源单号 → 业务单详情」下钻的**单一实现**（2026-10-04 F7-276 收口）。
 *
 * 为什么要抽出来：同一件事在 4 个页面各写了一遍（应收/应付台账页、供应商清算页、账单详情页），
 * 其中委外两类（`OUTSOURCE_DELIVERY` 加工收货 / `OUTSOURCE_EXCESS_LOSS` 超损）的台账 `source_id`
 * **不是加工单 id**（是收货记录 / 结单报表 id），必须用通用单号解析器 `/common/resolve-code` 换成加工单
 * id；也不能直读加工单页（`/outsource/order/page` 需 `outsource:order` 权限，只持 `finance:*` 的用户会 403）。
 * 账单详情页漏了这步 ⇒ 点进去落到"另一张单据"；其余三页早就做了 ⇒ 口径不一致本身就是缺陷来源。
 *
 * 用法：
 * - 模板里判断"可点吗"用 {@link canDrillSource}（缺路由键或无业务单 id 时保持纯文本，不假装可点）；
 * - 点击时用 {@link resolveSourceDetailPath} 取目标路径再 `router.push`。
 */
import request from '@/utils/request'
import { SourceBillDetailRoute } from '@/api/enums'

/** 该来源类型的详情页**路由前缀**（无映射返回空串；类型→前缀走公共表，避免各处自建） */
export function sourceRouteBase(row: any): string {
  return SourceBillDetailRoute[String(row?.sourceBillType || '')] || ''
}

/** 是否可下钻：有路由前缀 **且** 有业务单 id（缺任一项就保持纯文本） */
export function canDrillSource(row: any): boolean {
  return !!sourceRouteBase(row) && row?.sourceId != null
}

/** 委外两类：台账 source_id 是收货记录/结单报表 id，需按**单号**换回加工单 id */
const CODE_RESOLVED_TYPES = ['OUTSOURCE_DELIVERY', 'OUTSOURCE_EXCESS_LOSS']

/**
 * 解析出可直接 `router.push` 的目标路径；解析不出（缺前缀/缺 id/解析后不是加工单）返回空串。
 * 解析失败不抛错也不盲跳 —— 宁可不跳，也不跳错单据。
 */
export async function resolveSourceDetailPath(row: any): Promise<string> {
  const base = sourceRouteBase(row)
  if (!base || row?.sourceId == null) return ''
  let targetId = row.sourceId
  if (CODE_RESOLVED_TYPES.includes(String(row?.sourceBillType))) {
    if (row?.sourceBillNo == null || row.sourceBillNo === '') return ''
    const r: any = await request.get<any, any>('/common/resolve-code', { params: { code: row.sourceBillNo } })
    targetId = r?.type === 'order' ? r.id : undefined
  }
  return targetId == null ? '' : `${base}/${targetId}`
}
