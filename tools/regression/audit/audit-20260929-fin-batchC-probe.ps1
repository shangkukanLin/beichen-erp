# Batch C (accounts / cashflow / expense / invoice) read-only evidence probe, 2026-09-29.
# ASCII ONLY. ZERO data change: every statement is a SELECT. Safe to re-run.
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
function Q([string]$sql) {
  $rows = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
          Where-Object { "$_" -notmatch '^(mysql:|ERROR)' }
  if ($rows.Count -eq 0) { Write-Host '    (no rows)' } else { $rows | ForEach-Object { Write-Host ('    ' + $_) } }
}
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }

Sec '1) account balance is DERIVED (no snapshot column) + one OPENING flow per opening balance'
# NOTE: `finance_account.balance` is @TableField(exist=false) -> assert there is no such COLUMN at all.
Q "SELECT COUNT(*) AS balanceColumns FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='finance_account' AND column_name='balance'"
Q "SELECT COUNT(*) AS accountsWithOpening, COUNT(*) - SUM(CASE WHEN t.c = 1 THEN 1 ELSE 0 END) AS badOpeningFlowCount FROM finance_account a JOIN (SELECT account_id, COUNT(*) c FROM finance_cashflow WHERE flow_type='OPENING' GROUP BY account_id) t ON t.account_id=a.id WHERE IFNULL(a.opening_balance,0) > 0"
Q "SELECT a.id, a.account_name, IFNULL(a.opening_balance,0) opening, IFNULL((SELECT SUM(cf.income-cf.expense) FROM finance_cashflow cf WHERE cf.account_id=a.id),0) flowSum, a.status FROM finance_account a ORDER BY a.id"

Sec '2) flows: orphans / traceability (related_bill_no + related_bill_type)'
Q "SELECT COUNT(*) AS flows, SUM(CASE WHEN IFNULL(related_bill_no,'')='' THEN 1 ELSE 0 END) AS noBillNo, SUM(CASE WHEN IFNULL(related_bill_type,'')='' THEN 1 ELSE 0 END) AS noBillType, SUM(CASE WHEN account_id IS NULL THEN 1 ELSE 0 END) AS noAccount FROM finance_cashflow"
Q "SELECT flow_type, COUNT(*) c, SUM(CASE WHEN IFNULL(related_bill_type,'')='' THEN 1 ELSE 0 END) noType FROM finance_cashflow GROUP BY flow_type ORDER BY flow_type"
Q "SELECT related_bill_type, COUNT(*) c FROM finance_cashflow GROUP BY related_bill_type ORDER BY c DESC"
Q "SELECT id, flow_no, flow_type, related_bill_no, related_bill_type, income, expense FROM finance_cashflow WHERE IFNULL(related_bill_no,'')='' OR IFNULL(related_bill_type,'')='' ORDER BY id LIMIT 10"

Sec '3) duplicate flow_no (the account-opening generator has no conflict retry)'
Q "SELECT COUNT(*) AS dupGroups FROM (SELECT flow_no FROM finance_cashflow GROUP BY flow_no HAVING COUNT(*) > 1) x"
Q "SELECT flow_no, COUNT(*) c FROM finance_cashflow GROUP BY flow_no HAVING COUNT(*) > 1 LIMIT 5"

Sec '4) non-cash expenses (account_id IS NULL) must not appear in the cashflow'
Q "SELECT COUNT(*) AS nonCash FROM finance_expense WHERE account_id IS NULL"
Q "SELECT COUNT(*) AS nonCashWithFlow FROM finance_expense e WHERE e.account_id IS NULL AND EXISTS (SELECT 1 FROM finance_cashflow cf WHERE cf.related_bill_no = e.expense_no)"
Q "SELECT COUNT(*) AS cashNoFlow FROM finance_expense e WHERE e.account_id IS NOT NULL AND e.status='AUDITED' AND NOT EXISTS (SELECT 1 FROM finance_cashflow cf WHERE cf.related_bill_no = e.expense_no)"
Q "SELECT e.id, e.expense_no, e.status, IFNULL(e.account_id,0), e.amount, IFNULL(e.source_bill_type,'') FROM finance_expense WHERE e.account_id IS NULL OR e.source_bill_type IS NOT NULL ORDER BY e.id DESC LIMIT 12"

Sec '5) expense ledger shape: DRAFT/CANCELLED cash expenses must net to 0; AUDITED must NOT net to 0'
Q "SELECT e.status, COUNT(*) c, SUM(CASE WHEN e.account_id IS NOT NULL AND IFNULL((SELECT SUM(cf.income-cf.expense) FROM finance_cashflow cf WHERE cf.related_bill_no=e.expense_no),0) <> 0 THEN 1 ELSE 0 END) nonZeroNet FROM finance_expense e WHERE e.status <> 'AUDITED' GROUP BY e.status"
Q "SELECT COUNT(*) AS auditedCashWithZeroNet FROM finance_expense e WHERE e.status='AUDITED' AND e.account_id IS NOT NULL AND IFNULL((SELECT SUM(cf.income-cf.expense) FROM finance_cashflow cf WHERE cf.related_bill_no=e.expense_no),0) = 0"
Q "SELECT e.id, e.expense_no, e.status, e.amount, IFNULL((SELECT SUM(cf.income-cf.expense) FROM finance_cashflow cf WHERE cf.related_bill_no=e.expense_no),0) netFlow FROM finance_expense e WHERE e.account_id IS NOT NULL ORDER BY e.id DESC LIMIT 10"

Sec '6) invoices: numbering uniqueness / linkage'
Q "SELECT COUNT(*) AS invoices, SUM(CASE WHEN IFNULL(source_bill_code,'')='' THEN 1 ELSE 0 END) AS noSource, SUM(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END) AS cancelled FROM finance_invoice"
Q "SELECT COUNT(*) AS dupInvoiceNo FROM (SELECT company_id, invoice_no FROM finance_invoice WHERE status <> 'CANCELLED' GROUP BY company_id, invoice_no HAVING COUNT(*) > 1) x"
Q "SELECT COUNT(*) AS badAmounts FROM finance_invoice WHERE IFNULL(amount,0) + IFNULL(tax_amount,0) <> IFNULL(total_amount,0)"
Q "SELECT COUNT(*) AS badTotal FROM finance_invoice WHERE IFNULL(total_amount,0) < 0 OR IFNULL(amount,0) < 0"
Q "SELECT direction, invoice_kind, COUNT(*) c FROM finance_invoice GROUP BY direction, invoice_kind"
Q "SELECT COUNT(*) AS sourceMatchesBill FROM finance_invoice i WHERE IFNULL(i.source_bill_code,'') <> '' AND EXISTS (SELECT 1 FROM finance_bill b WHERE b.bill_no = i.source_bill_code)"

Sec '7) account rename / disable impact (snapshot names vs live names)'
Q "SELECT COUNT(*) AS disabledAccounts FROM finance_account WHERE status=0"
Q "SELECT COUNT(*) AS renamedSnapshotRows FROM finance_cashflow cf JOIN finance_account a ON a.id=cf.account_id WHERE IFNULL(cf.account_name,'') <> IFNULL(a.account_name,'')"
Q "SELECT DISTINCT cf.account_id, cf.account_name snapshot, a.account_name live FROM finance_cashflow cf JOIN finance_account a ON a.id=cf.account_id WHERE IFNULL(cf.account_name,'') <> IFNULL(a.account_name,'') LIMIT 10"

Sec '8) dataset shape'
Q "SELECT (SELECT COUNT(*) FROM finance_account) accounts, (SELECT COUNT(*) FROM finance_cashflow) flows, (SELECT COUNT(*) FROM finance_expense) expenses, (SELECT COUNT(*) FROM finance_invoice) invoices"
Write-Host ''
Write-Host 'DONE (read-only probe, nothing was written)'
