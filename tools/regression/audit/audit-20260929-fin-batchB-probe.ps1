# Batch B (payment chain / payable -> receivable transfer) read-only evidence probe, 2026-09-29.
# ASCII ONLY. ZERO data change: every statement below is a SELECT. Safe to re-run.
# Prints one block per invariant so the report can cite exact counts.
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
function Q([string]$sql) {
  $rows = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
          Where-Object { "$_" -notmatch '^(mysql:|ERROR)' }
  if ($rows.Count -eq 0) { Write-Host '    (no rows)' } else { $rows | ForEach-Object { Write-Host ('    ' + $_) } }
}
# NOTE: do NOT name this helper "H" -- `h` is a built-in alias for Get-History in PS 5.1, so the
# function gets shadowed and every section header silently turns into a Get-History binding error.
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }

Sec '1) payment.amount must equal SUM(split rows)   [mirror of batch A / A1]'
Q "SELECT COUNT(*) AS bad FROM finance_payment p WHERE IFNULL(p.amount,0) <> IFNULL((SELECT SUM(a.amount) FROM finance_payment_account a WHERE a.payment_id=p.id),0)"
Q "SELECT p.id, p.code, p.status, IFNULL(p.amount,0) amt, IFNULL((SELECT SUM(a.amount) FROM finance_payment_account a WHERE a.payment_id=p.id),0) splitSum FROM finance_payment p WHERE IFNULL(p.amount,0) <> IFNULL((SELECT SUM(a.amount) FROM finance_payment_account a WHERE a.payment_id=p.id),0) ORDER BY p.id"
Sec '1b) main account_id/account_name must be the FIRST split row snapshot'
Q "SELECT COUNT(*) AS mismatch FROM finance_payment p JOIN finance_payment_account a ON a.payment_id=p.id WHERE a.id=(SELECT MIN(x.id) FROM finance_payment_account x WHERE x.payment_id=p.id) AND (IFNULL(p.account_id,0) <> IFNULL(a.account_id,0) OR IFNULL(p.account_name,'') <> IFNULL(a.account_name,''))"

Sec '2) payable ledger identity: amount = paid + unpaid (incl. CANCELLED rows, I29)'
Q "SELECT COUNT(*) AS bad FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)"
Q "SELECT id, bill_no, status, amount, paid_amount, unpaid_amount FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0) ORDER BY id LIMIT 20"

Sec '3) AUDITED payments: settled total (SUM items) must be <= amount   [candidate: audit lacks assertSettledWithinPaid]'
Q "SELECT COUNT(*) AS bad FROM finance_payment p WHERE p.status='AUDITED' AND IFNULL((SELECT SUM(i.this_amount) FROM finance_payment_item i WHERE i.payment_id=p.id),0) > IFNULL(p.amount,0)"
Q "SELECT p.id, p.code, p.amount, IFNULL((SELECT SUM(i.this_amount) FROM finance_payment_item i WHERE i.payment_id=p.id),0) itemSum FROM finance_payment p WHERE p.status='AUDITED' AND IFNULL((SELECT SUM(i.this_amount) FROM finance_payment_item i WHERE i.payment_id=p.id),0) > IFNULL(p.amount,0) ORDER BY p.id LIMIT 10"

Sec '4) negative NORMAL PAY settlements (mirror of batch A / A13)'
Q "SELECT COUNT(*) AS negativeRows FROM finance_settlement WHERE direction='PAY' AND status='NORMAL' AND amount < 0"
Q "SELECT id, receipt_payment_id, payable_receivable_id, amount, source_type, status FROM finance_settlement WHERE direction='PAY' AND amount < 0 ORDER BY id LIMIT 10"

Sec '5) ADVANCE payable rows: shape (bill_no length / stacking / source_bill_type values)'
Q "SELECT COUNT(*) AS advanceRows, SUM(CASE WHEN LENGTH(bill_no) > 40 THEN 1 ELSE 0 END) AS longBillNo, SUM(CASE WHEN bill_no LIKE '%-ADVANCE-ADVANCE%' THEN 1 ELSE 0 END) AS stacked FROM finance_payable WHERE status='ADVANCE'"
Q "SELECT source_bill_type, COUNT(*) c FROM finance_payable WHERE status='ADVANCE' GROUP BY source_bill_type"
Q "SELECT id, bill_no, LENGTH(bill_no) len, source_bill_type, source_bill_no, source_id, amount, status FROM finance_payable WHERE status='ADVANCE' ORDER BY id DESC LIMIT 8"

Sec '6) orphan ADVANCE ledgers: source_id points to a payment that no longer exists'
Q "SELECT COUNT(*) AS orphans FROM finance_payable WHERE status='ADVANCE' AND source_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM finance_payment p WHERE p.id=finance_payable.source_id)"
Sec '6b) cancelled payment still leaving an ACTIVE advance (un-audit asymmetry)'
Q "SELECT COUNT(*) AS leaked FROM finance_payable a JOIN finance_payment p ON p.id=a.source_id WHERE p.status IN ('DRAFT','CANCELLED') AND a.status='ADVANCE'"
Q "SELECT a.id, a.bill_no, a.amount, a.status, p.id paymentId, p.code, p.status paymentStatus FROM finance_payable a JOIN finance_payment p ON p.id=a.source_id WHERE p.status IN ('DRAFT','CANCELLED') AND a.status='ADVANCE' ORDER BY a.id LIMIT 10"

Sec '7) transfer chain: transferred_to_receivable=1 rows vs their transfer doc + supplier receivable'
Q "SELECT COUNT(*) AS marked FROM finance_payable WHERE IFNULL(transferred_to_receivable,0)=1"
Q "SELECT COUNT(*) AS markedWithoutAuditedTransfer FROM finance_payable p WHERE IFNULL(p.transferred_to_receivable,0)=1 AND NOT EXISTS (SELECT 1 FROM payable_transfer t WHERE t.payable_id=p.id AND t.status='AUDITED')"
Q "SELECT COUNT(*) AS markedWithoutReceivable FROM finance_payable p WHERE IFNULL(p.transferred_to_receivable,0)=1 AND NOT EXISTS (SELECT 1 FROM finance_receivable r WHERE r.source_bill_type='PAYABLE_TRANSFER' AND r.source_id IN (SELECT t.id FROM payable_transfer t WHERE t.payable_id=p.id))"
Q "SELECT t.id, t.code, t.status, t.payable_id, t.amount, r.bill_no recvBill, r.status recvStatus FROM payable_transfer t LEFT JOIN finance_receivable r ON r.bill_no=t.code ORDER BY t.id DESC LIMIT 8"
Sec '7b) one payable transferred by more than one AUDITED transfer doc'
Q "SELECT payable_id, COUNT(*) c FROM payable_transfer WHERE status='AUDITED' GROUP BY payable_id HAVING COUNT(*) > 1"

Sec '8) same ledger referenced by bill items of MORE THAN ONE bill (bill-progress double count risk)'
Q "SELECT COUNT(*) AS multiBillPayables FROM (SELECT source_id FROM finance_bill_item WHERE source_id IS NOT NULL GROUP BY source_id HAVING COUNT(DISTINCT bill_id) > 1) x"
Q "SELECT source_id, COUNT(DISTINCT bill_id) bills FROM finance_bill_item WHERE source_id IS NOT NULL GROUP BY source_id HAVING COUNT(DISTINCT bill_id) > 1 LIMIT 10"

Sec '9) payments that used a DISABLED account (library evidence for the missing status guard)'
Q "SELECT COUNT(*) AS disabledUsed FROM finance_payment p WHERE EXISTS (SELECT 1 FROM finance_payment_account a JOIN finance_account fa ON fa.id=a.account_id WHERE a.payment_id=p.id AND fa.status=0)"
Q "SELECT COUNT(*) AS disabledUsedLegacy FROM finance_payment p JOIN finance_account fa ON fa.id=p.account_id WHERE fa.status=0"

Sec '10) zero-amount payment artefacts (mirror of batch A / F7-205/206 library evidence)'
Q "SELECT COUNT(*) AS zeroPayments FROM finance_payment WHERE IFNULL(amount,0)=0"
Q "SELECT id, code, status, amount FROM finance_payment WHERE IFNULL(amount,0)=0 ORDER BY id LIMIT 10"
Q "SELECT COUNT(*) AS zeroItems FROM finance_payment_item WHERE IFNULL(this_amount,0) <= 0"
Q "SELECT i.id, i.payment_id, p.code, p.status, IFNULL(i.payable_id,0), i.this_amount FROM finance_payment_item i JOIN finance_payment p ON p.id=i.payment_id WHERE IFNULL(i.this_amount,0) <= 0 LIMIT 10"

Sec '11) supplier_type missing / stale on payment and payable rows'
Q "SELECT COUNT(*) AS paymentNoType FROM finance_payment WHERE (supplier_type IS NULL OR supplier_type='') AND status <> 'CANCELLED'"
Q "SELECT COUNT(*) AS payableNoType FROM finance_payable WHERE status <> 'CANCELLED' AND (supplier_type IS NULL OR supplier_type='')"
Q "SELECT p.id, p.code, p.supplier_id, p.supplier_type FROM finance_payment p WHERE (p.supplier_type IS NULL OR p.supplier_type='') AND p.status <> 'CANCELLED' ORDER BY p.id DESC LIMIT 10"

Sec '12) items pointing to a payable of ANOTHER supplier (F7-32 guard retro-check)'
Q "SELECT COUNT(*) AS crossSupplier FROM finance_payment_item i JOIN finance_payment p ON p.id=i.payment_id JOIN finance_payable y ON y.id=i.payable_id WHERE p.supplier_id <> y.supplier_id"

Sec '13) role/permission matrix for the mirrored endpoints (finance:payable vs finance:payment)'
Q "SELECT p.perm_code, COUNT(DISTINCT r.id) roles FROM sys_role_perm rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.perm_id WHERE p.perm_code LIKE 'finance:%' GROUP BY p.perm_code ORDER BY p.perm_code"
Q "SELECT r.id, r.role_name, GROUP_CONCAT(p.perm_code ORDER BY p.perm_code) perms FROM sys_role r JOIN sys_role_perm rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.perm_id WHERE p.perm_code LIKE 'finance:%' GROUP BY r.id, r.role_name HAVING perms LIKE '%finance:payable%' AND perms NOT LIKE '%finance:payment%'"
Q "SELECT r.id, r.role_name, GROUP_CONCAT(p.perm_code ORDER BY p.perm_code) perms FROM sys_role r JOIN sys_role_perm rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.perm_id WHERE p.perm_code LIKE 'finance:%' GROUP BY r.id, r.role_name HAVING perms LIKE '%finance:payment%' AND perms NOT LIKE '%finance:payable%'"

Sec '14) counts (shape of the current dataset)'
Q "SELECT (SELECT COUNT(*) FROM finance_payment) payments, (SELECT COUNT(*) FROM finance_payment_item) items, (SELECT COUNT(*) FROM finance_payment_account) splitRows, (SELECT COUNT(*) FROM finance_payable) payables, (SELECT COUNT(*) FROM payable_transfer) transfers, (SELECT COUNT(*) FROM finance_settlement WHERE direction='PAY') paySettlements"
Write-Host ''
Write-Host 'DONE (read-only probe, nothing was written)'
