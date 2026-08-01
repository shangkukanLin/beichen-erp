/** 通用单据状态（对应 DocStatus 枚举） */
export const DocStatus = {
  DRAFT: 'DRAFT',
  AUDITED: 'AUDITED',
  CANCELLED: 'CANCELLED'
} as const

export const DocStatusLabel: Record<string, string> = {
  [DocStatus.DRAFT]: '草稿',
  [DocStatus.AUDITED]: '已审核',
  [DocStatus.CANCELLED]: '已作废'
}

export const DocStatusTag: Record<string, string> = {
  [DocStatus.DRAFT]: 'info',
  [DocStatus.AUDITED]: 'success',
  [DocStatus.CANCELLED]: 'danger'
}
