/**
 * 全局属性的类型声明（2026-10-10）：`$mLabel` = 物料展示标签「物料类型 | 物料名称」。
 *
 * <p>注册处见 `main.ts`（`app.config.globalProperties.$mLabel = materialLabel`）。
 * 之所以注册成全局属性：物料名称的展示点有 30 多处（列表/详情/下拉/明细），
 * 若每处都 `import { materialLabel }`，每个文件都要动两处（import + 用法）⇒
 * 全局属性让每个使用点**只改一行** ✓。实现与口径见 `utils/materialLabel.ts`。</p>
 */
declare module 'vue' {
  interface ComponentCustomProperties {
    /** 物料展示标签：`类型 | 名称`；无类型（含后端回传的 `-`）时只返回名称 */
    $mLabel: (row: any) => string
    /** 只要前缀（`类型 | `）；无类型时返回空串 —— 供"名称后面已自带后缀"的场合拼接 */
    $mTypePrefix: (row: any) => string
  }
}

export {}
