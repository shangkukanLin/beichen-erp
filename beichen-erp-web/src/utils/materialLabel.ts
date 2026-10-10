/**
 * 物料展示标签：`物料类型 | 物料名称`（2026-10-10 用户口径）。
 *
 * <p><b>为什么需要它</b>：物料类型不是枚举，而是各公司自维护的主数据（`material_type` 表），
 * 物料只存 `materialTypeId`；重名物料（如不同厂家同规格的"排线"）靠名称根本分不出来 ⇒
 * 展示时一律把类型放到名称前面。格式**照抄产品侧的 {@code productLabel}（`SKU | 名称`）**，
 * 保持两个模块观感一致 ✓。</p>
 *
 * <p><b>无类型时只显示名称</b>（用户口径）：现库存在没有类型的物料（历史种子数据），
 * 加"未分类"标记会让列表很吵 ⇒ 直接回退成纯名称 ✓。⚠️ 后端 `getMaterialTypeNameById(...)`
 * 对空类型返回的是**字符串 `"-"`**（不是 null）⇒ 这里必须把 `-` / `—` / 空串都当"无类型"处理，
 * 否则会拼出 `- | 名称` ✗。</p>
 *
 * <p>入参兼容三种形态：分页行 / 下拉 option / 明细行，字段名可能是 `materialName` 或 `name`。</p>
 */
export function materialLabel(row: any): string {
  if (!row) return ''
  const name = row.materialName ?? row.name ?? ''
  return typePrefix(row) + name
}

/** 只取前缀（`类型 | `），无类型时返回空串 —— 供"客户已有后缀"的场合自行拼接（如 `名称（单位）`）。 */
export function materialTypePrefix(row: any): string {
  return typePrefix(row)
}

function typePrefix(row: any): string {
  const t = row?.materialTypeName
  if (t == null) return ''
  const s = String(t).trim()
  // 后端对空类型回 "-"；历史数据里也可能出现全角破折号或空串
  if (s === '' || s === '-' || s === '—' || s === '－') return ''
  return s + ' | '
}
