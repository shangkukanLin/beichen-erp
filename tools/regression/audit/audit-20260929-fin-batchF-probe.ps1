# Batch F read-only probe (2026-09-29): outsource + stock loss -> finance. READ ONLY (SELECT only).
# ASCII ONLY (no BOM/binary risk: PS 5.1 would decode non-ASCII as GBK and can break parsing).
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function Q([string]$sql) {
  foreach ($l in @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null)) {
    if ("$l" -notmatch '^(mysql:|ERROR)') { Write-Host ('    ' + $l) }
  }
}
Write-Host '=== batch F probe: outsource + stock loss (read only) ==='

Sec '0) table inventory (guards the queries below against name drift)'
Q "SELECT table_name FROM information_schema.tables WHERE table_schema='beichen_erp' AND table_name LIKE '%stock_loss%' ORDER BY 1"
Q "SELECT table_name FROM information_schema.tables WHERE table_schema='beichen_erp' AND table_name LIKE 'outsource%' ORDER BY 1 LIMIT 12"
Q "SELECT column_name FROM information_schema.columns WHERE table_schema='beichen_erp' AND table_name='inventory_stock_loss' ORDER BY ordinal_position"

Sec '1) F7-201 loop check: audited loss docs must carry a finance leg (LOSS expense or supplier claim)'
Q "SELECT (SELECT COUNT(*) FROM inventory_stock_loss) inv_docs, (SELECT COUNT(*) FROM outsource_stock_loss) osl_docs"
Q "SELECT source_bill_type, status, COUNT(*) FROM finance_expense WHERE expense_type='LOSS' GROUP BY source_bill_type, status ORDER BY 1,2"
Q "SELECT source_bill_type, status, COUNT(*), IFNULL(SUM(amount),0) FROM finance_receivable WHERE source_bill_type IN ('INVENTORY_STOCK_LOSS','OUTSOURCE_STOCK_LOSS') GROUP BY source_bill_type, status ORDER BY 1,2"
Q "SELECT id, status, IFNULL(total_amount,0) FROM inventory_stock_loss ORDER BY id DESC LIMIT 6"
Q "SELECT id, status, IFNULL(total_amount,0) FROM outsource_stock_loss ORDER BY id DESC LIMIT 6"

Sec '2) LOSS expense shape (must match F7-232: LOSS carries no account)'
Q "SELECT COUNT(*) AS loss_with_account FROM finance_expense WHERE expense_type='LOSS' AND account_id IS NOT NULL"
Q "SELECT status, COUNT(*) FROM finance_expense WHERE expense_type='LOSS' GROUP BY status"

Sec '3) direction: sign per outsource source type'
Q "SELECT source_bill_type, COUNT(*) n, SUM(CASE WHEN IFNULL(amount,0) < 0 THEN 1 ELSE 0 END) neg_rows, SUM(CASE WHEN IFNULL(amount,0) > 0 THEN 1 ELSE 0 END) pos_rows FROM finance_payable WHERE status<>'CANCELLED' AND source_bill_type LIKE 'OUTSOURCE%' GROUP BY source_bill_type ORDER BY 1"
Q "SELECT source_bill_type, COUNT(*) n, SUM(CASE WHEN IFNULL(amount,0) > 0 THEN 1 ELSE 0 END) pos_rows, SUM(CASE WHEN IFNULL(amount,0) < 0 THEN 1 ELSE 0 END) neg_rows FROM finance_receivable WHERE status<>'CANCELLED' AND (source_bill_type LIKE 'OUTSOURCE%' OR subject_type='SUPPLIER') GROUP BY source_bill_type ORDER BY 1"

Sec '4) close-report excess loss vs delivery payable: source granularity (no double posting)'
Q "SELECT source_bill_type, COUNT(DISTINCT source_id) src_docs, COUNT(*) rows_ FROM finance_payable WHERE source_bill_type IN ('OUTSOURCE_EXCESS_LOSS','OUTSOURCE_DELIVERY') AND status<>'CANCELLED' GROUP BY source_bill_type"
Q "SELECT COUNT(*) AS excess_loss_rows_not_negative FROM finance_payable WHERE source_bill_type='OUTSOURCE_EXCESS_LOSS' AND status<>'CANCELLED' AND IFNULL(amount,0) >= 0"
Q "SELECT source_bill_type, COUNT(*) n FROM finance_payable WHERE source_bill_type='OUTSOURCE_REPAIR_CHARGE' AND status<>'CANCELLED' GROUP BY source_bill_type"

Sec '5) cancellation accumulation (price of D1) for outsource + loss sources'
Q "SELECT source_bill_type, COUNT(*) cancelled FROM finance_payable WHERE status='CANCELLED' AND source_bill_type LIKE 'OUTSOURCE%' GROUP BY source_bill_type ORDER BY 2 DESC"
Q "SELECT source_bill_type, COUNT(*) cancelled FROM finance_receivable WHERE status='CANCELLED' GROUP BY source_bill_type ORDER BY 2 DESC LIMIT 8"

Sec '6) scale'
Q "SELECT (SELECT COUNT(*) FROM outsource_order) orders, (SELECT COUNT(*) FROM finance_payable) payables, (SELECT COUNT(*) FROM finance_receivable) receivables"
Write-Host ''
Write-Host 'DONE (no writes performed).'
