# Batch D read-only probe (2026-09-29): 账单 / 台账↔账单 / 自动任务.
# READ ONLY: every statement is a SELECT. Safe to re-run; touches nothing.
# ASCII ONLY (no BOM -> PS 5.1 would read non-ASCII as GBK).
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function Q([string]$sql) {
  foreach ($l in @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null)) {
    if ("$l" -notmatch '^(mysql:|ERROR)') { Write-Host ('    ' + $l) }
  }
}
Write-Host '=== batch D probe: bill / ledger-bill sync / auto task (read only) ==='

Sec '1) F7-243: bill status distribution, empty bills (no items) and zero-amount bills'
Q "SELECT status, COUNT(*) FROM finance_bill GROUP BY status"
Q "SELECT COUNT(*) AS bills_without_items FROM finance_bill b WHERE NOT EXISTS (SELECT 1 FROM finance_bill_item i WHERE i.bill_id = b.id)"
Q "SELECT COUNT(*) AS zero_total_bills FROM finance_bill WHERE IFNULL(total_amount, 0) = 0"
Q "SELECT b.id, b.bill_no, b.bill_type, b.status, IFNULL(b.total_amount,0) total, IFNULL(b.paid_amount,0) paid, (SELECT COUNT(*) FROM finance_bill_item i WHERE i.bill_id = b.id) items FROM finance_bill b ORDER BY b.id DESC LIMIT 12"

Sec '2) F7-244: bill.partner_name vs master data (client-supplied name?)'
Q "SELECT t.id, t.bill_no, t.partner_id, t.partner_name, t.master_name FROM (SELECT b.id, b.bill_no, b.partner_id, b.partner_name, CASE WHEN b.bill_type='RECEIVABLE' THEN (SELECT c.name FROM customer c WHERE c.id=b.partner_id) ELSE (SELECT s.name FROM supplier s WHERE s.id=b.partner_id) END AS master_name FROM finance_bill b WHERE b.partner_id IS NOT NULL) t WHERE t.master_name IS NOT NULL AND (IFNULL(t.partner_name,'') = '' OR t.partner_name <> t.master_name) LIMIT 15"
Q "SELECT COUNT(*) AS name_mismatch FROM (SELECT b.id, b.partner_name, CASE WHEN b.bill_type='RECEIVABLE' THEN (SELECT c.name FROM customer c WHERE c.id=b.partner_id) ELSE (SELECT s.name FROM supplier s WHERE s.id=b.partner_id) END AS master_name FROM finance_bill b WHERE b.partner_id IS NOT NULL) t WHERE t.master_name IS NOT NULL AND (IFNULL(t.partner_name,'') = '' OR t.partner_name <> t.master_name)"

Sec '3) F7-245: bill item progress vs the ledger it snapshots (RECEIVABLE side, active bills)'
Q "SELECT COUNT(*) AS mismatched_paid FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_receivable r ON r.id=i.source_id WHERE b.bill_type='RECEIVABLE' AND b.status<>'CANCELLED' AND ABS(IFNULL(i.paid_amount,0) - IFNULL(r.paid_amount,0)) > 0.005"
Q "SELECT COUNT(*) AS mismatched_unpaid FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_receivable r ON r.id=i.source_id WHERE b.bill_type='RECEIVABLE' AND b.status<>'CANCELLED' AND ABS(IFNULL(i.unpaid_amount,0) - IFNULL(r.unpaid_amount,0)) > 0.005"
Q "SELECT i.bill_id, i.source_id, i.amount, i.paid_amount, i.unpaid_amount, r.amount ramt, r.paid_amount rpaid, r.unpaid_amount runpaid, r.status rstatus FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_receivable r ON r.id=i.source_id WHERE b.bill_type='RECEIVABLE' AND b.status<>'CANCELLED' LIMIT 12"
Q "SELECT COUNT(*) AS mismatched_paid_payable FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_payable y ON y.id=i.source_id WHERE b.bill_type='PAYABLE' AND b.status<>'CANCELLED' AND ABS(IFNULL(i.paid_amount,0) - IFNULL(y.paid_amount,0)) > 0.005"

Sec '4) F7-241: the same ledger referenced by more than one ACTIVE bill (double billing)'
Q "SELECT i.source_id, COUNT(DISTINCT i.bill_id) c, GROUP_CONCAT(DISTINCT i.bill_id ORDER BY i.bill_id) bills FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id WHERE b.status<>'CANCELLED' GROUP BY i.source_id HAVING COUNT(DISTINCT i.bill_id) > 1 LIMIT 20"
Q "SELECT COUNT(*) AS items_pointing_at_cancelled_bills FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id WHERE b.status='CANCELLED'"

Sec '5) F7-241: receivables the auto task would skip because ANOTHER (already billed) receivable of the same customer is covered'
Q "SELECT COUNT(*) AS overdue_open_receivables FROM finance_receivable r WHERE r.status IN ('UNSETTLED','PARTIAL') AND r.due_date IS NOT NULL AND r.due_date <= CURDATE()"
Q "SELECT COUNT(*) AS overdue_already_covered FROM finance_receivable r WHERE r.status IN ('UNSETTLED','PARTIAL') AND r.due_date IS NOT NULL AND r.due_date <= CURDATE() AND EXISTS (SELECT 1 FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id WHERE i.source_id=r.id AND b.status<>'CANCELLED')"
Q "SELECT c.name, COUNT(*) open_cnt, SUM(CASE WHEN EXISTS (SELECT 1 FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id WHERE i.source_id=r.id AND b.status<>'CANCELLED') THEN 1 ELSE 0 END) covered_cnt FROM finance_receivable r LEFT JOIN customer c ON c.id=r.customer_id WHERE r.status IN ('UNSETTLED','PARTIAL') AND r.due_date IS NOT NULL AND r.due_date<=CURDATE() GROUP BY r.customer_id, c.name ORDER BY open_cnt DESC LIMIT 10"

Sec '6) F7-245: bill totals vs SUM(items) (internal consistency of the snapshot)'
Q "SELECT COUNT(*) AS total_mismatch FROM finance_bill b WHERE IFNULL(b.total_amount,0) <> IFNULL((SELECT SUM(i.amount) FROM finance_bill_item i WHERE i.bill_id=b.id),0)"
Q "SELECT COUNT(*) AS paid_mismatch FROM finance_bill b WHERE IFNULL(b.paid_amount,0) <> IFNULL((SELECT SUM(i.paid_amount) FROM finance_bill_item i WHERE i.bill_id=b.id),0)"
Q "SELECT COUNT(*) AS unpaid_mismatch FROM finance_bill b WHERE IFNULL(b.unpaid_amount,0) <> IFNULL((SELECT SUM(i.unpaid_amount) FROM finance_bill_item i WHERE i.bill_id=b.id),0)"

Sec '7) scale: rows behind the bill/settlement logic'
Q "SELECT (SELECT COUNT(*) FROM finance_bill) bills, (SELECT COUNT(*) FROM finance_bill_item) items, (SELECT COUNT(*) FROM finance_settlement) settlements, (SELECT COUNT(*) FROM finance_receivable) receivables, (SELECT COUNT(*) FROM finance_payable) payables"
Q "SELECT direction, status, COUNT(*) FROM finance_settlement GROUP BY direction, status"
Write-Host ''
Write-Host 'DONE (no writes performed).'
