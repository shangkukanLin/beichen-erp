# Batch G read-only probe (2026-09-30): data layer / permissions / analysis dashboard.
# READ ONLY: every statement is a SELECT, plus a few GET API calls (analysis aggregates)
# and one login attempt. Nothing is written.
# ASCII ONLY (PS 5.1 decodes non-ASCII files as GBK; Chinese needles come from code points).
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$base = 'http://localhost:8080/api'
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function Q([string]$sql) {
  foreach ($l in @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null)) {
    if ("$l" -notmatch '^(mysql:|ERROR)') { Write-Host ('    ' + $l) }
  }
}
Write-Host '=== batch G probe: data layer / permission / analysis (read only) ==='

Sec '1) unique keys in the finance block: which ones carry company_id?'
Q "SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) cols FROM information_schema.statistics WHERE table_schema='beichen_erp' AND table_name LIKE 'finance_%' AND non_unique=0 GROUP BY table_name, index_name ORDER BY table_name, index_name"
Q "SELECT COUNT(*) AS finance_tables_without_company_id FROM (SELECT t.table_name FROM information_schema.tables t WHERE t.table_schema='beichen_erp' AND t.table_name LIKE 'finance_%' AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema=t.table_schema AND c.table_name=t.table_name AND c.column_name='company_id')) x"
Q "SELECT COUNT(*) AS companies, (SELECT COUNT(DISTINCT company_id) FROM finance_cashflow) companies_with_flows FROM sys_company"

Sec '2) F7-256: analysis "unpaid" caliber vs the ledger-page caliber (negative rows)'
Q "SELECT 'analysis_style' k, SUM(CASE WHEN status IN ('UNSETTLED','PARTIAL') THEN IFNULL(unpaid_amount,0) ELSE 0 END) v FROM finance_receivable WHERE status<>'CANCELLED' UNION ALL SELECT 'ledger_page_style', SUM(IFNULL(unpaid_amount,0)) FROM finance_receivable WHERE status NOT IN ('SETTLED','CANCELLED','ADVANCE') AND IFNULL(amount,0)>0 UNION ALL SELECT 'negative_open_rows', SUM(IFNULL(unpaid_amount,0)) FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(amount,0)<=0"
Q "SELECT 'analysis_style' k, SUM(CASE WHEN status IN ('UNSETTLED','PARTIAL') AND IFNULL(transferred_to_receivable,0)<>1 THEN IFNULL(unpaid_amount,0) ELSE 0 END) v FROM finance_payable WHERE status<>'CANCELLED' UNION ALL SELECT 'ledger_page_style', SUM(IFNULL(unpaid_amount,0)) FROM finance_payable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(transferred_to_receivable,0)<>1 AND IFNULL(amount,0)>0"
Q "SELECT id, bill_no, status, amount, unpaid_amount, IFNULL(source_bill_type,'-') FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(amount,0)<=0 LIMIT 8"

Sec '3) F7-256: aging buckets under both calibers'
Q "SELECT SUM(CASE WHEN IFNULL(due_date,CURDATE())>=CURDATE() THEN unpaid_amount ELSE 0 END) not_due_incl_neg, SUM(CASE WHEN IFNULL(due_date,CURDATE())<CURDATE() THEN unpaid_amount ELSE 0 END) overdue_incl_neg FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL')"
Q "SELECT SUM(CASE WHEN IFNULL(due_date,CURDATE())>=CURDATE() THEN unpaid_amount ELSE 0 END) not_due_pos_only, SUM(CASE WHEN IFNULL(due_date,CURDATE())<CURDATE() THEN unpaid_amount ELSE 0 END) overdue_pos_only FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(amount,0)>0"

Sec '4) REFUTE CHECK: is the account balance caliber really "sum of flows" (opening = a flow)?'
Q "SELECT flow_type, COUNT(*) n, SUM(IFNULL(income,0)) income_sum, SUM(IFNULL(expense,0)) expense_sum FROM finance_cashflow GROUP BY flow_type ORDER BY 1"
Q "SELECT a.id, a.account_name, a.opening_balance, IFNULL((SELECT SUM(c.income-c.expense) FROM finance_cashflow c WHERE c.account_id=a.id),0) flow_balance FROM finance_account a ORDER BY a.id LIMIT 6"

Sec '5) F7-255: who enforces the analysis menu codes? (analysis prefixes live in EXEMPT)'
Q "SELECT id, IFNULL(perm,'-') FROM sys_menu WHERE perm LIKE 'analysis:%' ORDER BY id"
Q "SELECT COUNT(*) AS users_with_analysis_menu FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE m.perm LIKE 'analysis:%'"
Q "SELECT u.id, u.username, COUNT(DISTINCT m.perm) perms, SUM(CASE WHEN m.perm LIKE 'analysis:%' THEN 1 ELSE 0 END) analysis_perms, SUM(CASE WHEN m.perm LIKE 'finance:%' THEN 1 ELSE 0 END) finance_perms FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.id LEFT JOIN sys_role_menu rm ON rm.role_id=ur.role_id LEFT JOIN sys_menu m ON m.id=rm.menu_id GROUP BY u.id, u.username ORDER BY u.id"

Sec '6) F7-259: is the dashboard pageSize=200 truncation live?'
Q "SELECT (SELECT COUNT(*) FROM product) products, (SELECT COUNT(*) FROM warehouse_stock) stock_rows, (SELECT COUNT(*) FROM finance_account) accounts"
Q "SELECT table_name FROM information_schema.tables WHERE table_schema='beichen_erp' AND table_name LIKE '%stock%' ORDER BY 1"

Sec '7) scale behind the analysis dashboards'
Q "SELECT (SELECT COUNT(*) FROM sale_order) sale_orders, (SELECT COUNT(*) FROM purchase_order) purchase_orders, (SELECT COUNT(*) FROM sale_order_item) sale_items, (SELECT COUNT(*) FROM finance_cashflow) flows"
Write-Host ''
Write-Host 'DONE (no writes performed).'
