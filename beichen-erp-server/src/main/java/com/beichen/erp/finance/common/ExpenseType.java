package com.beichen.erp.finance.common;

/**
 * 费用类型枚举（{@code finance_expense.expense_type}，2026-09-27 新增）。
 *
 * <p>此前费用类型只有前端一份映射（{@code api/enums.ts} 的 {@code ExpenseTypeLabel}），后端 {@code validate()}
 * 只校验非空 ⇒ **写错前端 code 就是脏数据**（清单页会直接显示英文 code，与 F7-53 的教训同源）。
 * 现把类型收敛到本枚举：</p>
 * <ul>
 *   <li>写入侧：{@link #normalize(String)} 校验并**归一化大小写**（历史/脚本里出现过小写 {@code office}），
 *       未知值直接拒绝；</li>
 *   <li>跨模块写入（物料页「登记研发支出」）用 {@link #RND}，不再散落字面量；</li>
 *   <li>前端 Label 映射由 {@code web-check.ps1} 的枚举守卫比对**常量名一致性**（前端漏加值 ⇒ 守卫 FAIL）。</li>
 * </ul>
 */
public enum ExpenseType {

    /** 办公费 */
    OFFICE("办公费"),
    /** 房租水电 */
    RENT("房租水电"),
    /** 工资社保 */
    SALARY("工资社保"),
    /** 运输费 */
    TRANSPORT("运输费"),
    /** 差旅费 */
    TRAVEL("差旅费"),
    /** 业务招待 */
    ENTERTAIN("业务招待"),
    /** 研发支出（2026-09-27 新增：物料页新增物料时可顺带登记） */
    RND("研发支出"),
    /**
     * 报损损失（2026-09-29 用户口径「报损需要走财务流程」）：
     * 内部承担的报损（成品报损 / 委外物料报损）审核后自动登记的费用类型。
     * <p>⚠️ 这类费用单由业务单据带出（{@code source_bill_type} 非空）且 **account_id 为空 = 非资金费用**：
     * 审核<b>不写资金流水、不扣账户</b>（存货损失不产生现金流出），反审核也不写冲正流水。</p>
     */
    LOSS("报损损失"),
    /** 其他 */
    OTHER("其他");

    private final String label;

    ExpenseType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 按 code 反查（**忽略大小写**，兼容历史/脚本里的小写值）；未命中返回 {@code null}。 */
    public static ExpenseType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (ExpenseType t : values()) {
            if (t.name().equalsIgnoreCase(code.trim())) return t;
        }
        return null;
    }

    /**
     * 校验并归一化费用类型（写入口径，{@code FinanceExpenseServiceImpl.validate} 调用）。
     *
     * @return 规范 code（枚举常量名，大写）
     * @throws IllegalArgumentException 未知类型（消息里列出全部合法 code，便于前端/脚本自查）
     */
    public static String normalize(String code) {
        ExpenseType t = fromCode(code);
        if (t == null) {
            StringBuilder sb = new StringBuilder();
            for (ExpenseType v : values()) { if (sb.length() > 0) sb.append('/'); sb.append(v.name()); }
            throw new IllegalArgumentException("费用类型不合法：" + code + "（合法值：" + sb + "）");
        }
        return t.getCode();
    }
}
