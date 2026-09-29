# F7-202 data cleanup: the 2026-09-18/19 audit-round fixtures still sitting in the live DB.
# ASCII ONLY. DEFAULT IS A DRY RUN -- nothing is written unless you pass -Apply.
#
# Usage
#   powershell -NoProfile -ExecutionPolicy Bypass -File audit-20260929-fin-cleanup-fixtures.ps1
#       -> dry run: prints every row that would change (and writes nothing)
#   powershell -NoProfile -ExecutionPolicy Bypass -File audit-20260929-fin-cleanup-fixtures.ps1 -Apply
#       -> backup to %TEMP%\audit-20260929-fin-cleanup-<ts>.txt, then apply Mode=mark
#   ... -Apply -Mode delete   -> physically remove the fixture rows instead of marking them
#   ... -Apply -ReverseFlows  -> additionally post the reversal flow rows needed so that the cancelled
#                                receipts net to zero (only relevant for the receipts that HAVE income)
#
# What it touches (frozen id list, derived from audit-20260929-fin-batchA-probe.ps1):
#   (1) finance_settlement 61,63,65,67            negative RECEIVE amounts (-5 / -5000) -> CANCELLED
#   (2) finance_receivable 159,160,161,162,163    negative / ADVANCE chain (incl. the 47-char bill_no)
#                                                 -> CANCELLED + amount/unpaid zeroed + remark (I29 caliber)
#   (3) finance_receipt 38,39                     0-amount AUDITED receipts (no flows) -> CANCELLED
#   (4) finance_receipt 40,42,44,46               AUDITED fixtures whose write-off was undone
#                                                 -> CANCELLED (+ reversal flow rows with -ReverseFlows)
# After -Apply it re-checks the invariants (negative settlements / cancelled-with-paid / 47-char ADVANCE).
# 2026-09-29 D-9: this script now also covers the PAYMENT side (batch B):
#   (5) finance_settlement 45,46,50,51  negative NORMAL/PAY rows (payments 7/8/12/13 settling NEGATIVE payables)
#       -> CANCELLED (mark) / deleted; with -RevertNegativeLedgers also restores payables 291-294 to their
#          pre-settlement state (UNSETTLED, paid 0, unpaid = amount) so the negative deduction rows are pending again
#   (6) backfill finance_payable.supplier_type for rows that are NULL although the supplier HAS type labels
#       (root cause fixed in PayableHelper.resolveSupplierType, see F7-217)
param(
  [switch]$Apply,
  [ValidateSet('mark', 'delete')][string]$Mode = 'mark',
  [switch]$ReverseFlows,
  [switch]$RevertNegativeLedgers
)
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$STAMP = Get-Date -Format 'yyyyMMdd-HHmmss'
$BAK = Join-Path $env:TEMP ('audit-20260929-fin-cleanup-' + $STAMP + '.txt')
$SETTLE = '61,63,65,67'
$LEDGER = '159,160,161,162,163'
$RC_ZERO = '38,39'
$RC_MISMATCH = '40,42,44,46'
function Q([string]$sql) {
  foreach ($l in @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null)) {
    if ("$l" -notmatch '^(mysql:|ERROR)') { Write-Host ('    ' + $l) }
  }
}
function Run([string]$sql) { & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e $sql 2>$null | Out-Null }
function Dump([string]$label, [string]$sql) {
  Add-Content -Path $BAK -Value ('-- ' + $label)
  foreach ($l in @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null)) {
    if ("$l" -notmatch '^(mysql:|ERROR)') { Add-Content -Path $BAK -Value $l }
  }
}

Write-Host '=== F7-202 cleanup: 2026-09-18/19 audit fixtures ==='
Write-Host ('mode=' + $Mode + ' apply=' + $Apply + ' reverseFlows=' + $ReverseFlows)

Write-Host ''
Write-Host '(1) negative RECEIVE settlements (finance_settlement)'
Q ('SELECT id, receipt_payment_id, payable_receivable_id, amount, status FROM finance_settlement WHERE id IN (' + $SETTLE + ')')
Write-Host '(2) negative / ADVANCE-chain ledger rows (finance_receivable)'
Q ('SELECT id, bill_no, LENGTH(bill_no) len, amount, paid_amount, unpaid_amount, status FROM finance_receivable WHERE id IN (' + $LEDGER + ') ORDER BY id')
Write-Host '(3) zero-amount AUDITED receipts with no flows'
Q ('SELECT id, code, status, IFNULL(amount,0), (SELECT COUNT(*) FROM finance_cashflow f WHERE f.related_bill_no=r.code) flows FROM finance_receipt r WHERE id IN (' + $RC_ZERO + ')')
Write-Host '(4) AUDITED receipts whose write-off was fully undone'
Q ('SELECT r.id, r.code, r.status, r.amount, (SELECT COUNT(*) FROM finance_cashflow f WHERE f.related_bill_no=r.code AND f.income>0) incomeFlows, (SELECT COUNT(*) FROM finance_receipt_item i WHERE i.receipt_id=r.id) items, (SELECT COUNT(*) FROM finance_settlement s WHERE s.receipt_payment_id=r.id AND s.direction=''RECEIVE'' AND s.status=''NORMAL'') normalSettlements FROM finance_receipt r WHERE r.id IN (' + $RC_MISMATCH + ')')

$PAY_SETTLE = '45,46,50,51'
$NEG_LEDGER = '291,292,293,294'
Write-Host '(5) negative NORMAL/PAY settlements (payment side, F7-211)'
Q ('SELECT s.id, s.receipt_payment_id, p.code, p.status, s.payable_receivable_id, y.bill_no, y.amount, y.status, s.amount FROM finance_settlement s JOIN finance_payment p ON p.id=s.receipt_payment_id LEFT JOIN finance_payable y ON y.id=s.payable_receivable_id WHERE s.id IN (' + $PAY_SETTLE + ')')
Write-Host '    -> ledgers to restore with -RevertNegativeLedgers:'
Q ('SELECT id, bill_no, status, amount, paid_amount, unpaid_amount FROM finance_payable WHERE id IN (' + $NEG_LEDGER + ')')
Write-Host '(6) payable rows whose supplier_type is NULL although the supplier HAS labels (F7-217)'
Q 'SELECT COUNT(*) FROM finance_payable p WHERE p.status <> ''CANCELLED'' AND (p.supplier_type IS NULL OR p.supplier_type = '''') AND EXISTS (SELECT 1 FROM supplier_type_ref t WHERE t.supplier_id = p.supplier_id)'
Q 'SELECT p.id, p.bill_no, p.status, p.source_bill_type, (SELECT MIN(t.type_code) FROM supplier_type_ref t WHERE t.supplier_id=p.supplier_id) AS would_set FROM finance_payable p WHERE p.status <> ''CANCELLED'' AND (p.supplier_type IS NULL OR p.supplier_type = '''') AND EXISTS (SELECT 1 FROM supplier_type_ref t WHERE t.supplier_id = p.supplier_id) ORDER BY p.id LIMIT 8'

if (-not $Apply) {
  Write-Host ''
  Write-Host 'DRY RUN: nothing was written. Re-run with -Apply (and optionally -Mode delete / -ReverseFlows).'
  exit 0
}

# ---------- backup ----------
Set-Content -Path $BAK -Value ('-- F7-202 cleanup backup, ' + $STAMP)
Dump 'finance_settlement rows' ('SELECT * FROM finance_settlement WHERE id IN (' + $SETTLE + ')')
Dump 'finance_receivable rows' ('SELECT * FROM finance_receivable WHERE id IN (' + $LEDGER + ')')
Dump 'finance_receipt rows' ('SELECT * FROM finance_receipt WHERE id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + ')')
Dump 'finance_receipt_item rows' ('SELECT * FROM finance_receipt_item WHERE receipt_id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + ')')
Dump 'finance_receipt_account rows' ('SELECT * FROM finance_receipt_account WHERE receipt_id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + ')')
Dump 'finance_cashflow rows' ('SELECT * FROM finance_cashflow WHERE related_bill_no IN (SELECT code FROM finance_receipt WHERE id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + '))')
Dump 'finance_settlement PAY rows' ('SELECT * FROM finance_settlement WHERE id IN (' + $PAY_SETTLE + ')')
Dump 'finance_payable restored rows' ('SELECT * FROM finance_payable WHERE id IN (' + $NEG_LEDGER + ')')
Dump 'finance_payable rows needing supplier_type' 'SELECT id, bill_no, status, source_bill_type, supplier_id, supplier_type FROM finance_payable WHERE status <> ''CANCELLED'' AND (supplier_type IS NULL OR supplier_type = '''')'
Write-Host ''
Write-Host ('backup written: ' + $BAK)

# ---------- apply ----------
if ($Mode -eq 'delete') {
  Run ('DELETE FROM finance_settlement WHERE id IN (' + $SETTLE + ',' + $PAY_SETTLE + ')')
  if ($RevertNegativeLedgers) {
    Run ('UPDATE finance_payable SET status=''UNSETTLED'', paid_amount=0, unpaid_amount=amount WHERE id IN (' + $NEG_LEDGER + ')')
    Write-Host 'negative payables 291-294 restored to UNSETTLED (paid 0, unpaid = amount)'
  }
  Run ('DELETE FROM finance_receivable WHERE id IN (' + $LEDGER + ')')
  Run ('DELETE FROM finance_receipt_item WHERE receipt_id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + ')')
  Run ('DELETE FROM finance_receipt_account WHERE receipt_id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + ')')
  Run ('DELETE FROM finance_cashflow WHERE related_bill_no IN (SELECT code FROM finance_receipt WHERE id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + '))')
  Run ('DELETE FROM finance_receipt WHERE id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + ')')
  Write-Host 'mode=delete applied (rows physically removed; backup kept)'
} else {
  Run ('UPDATE finance_settlement SET status=''CANCELLED'' WHERE id IN (' + $SETTLE + ')')
  Run ('UPDATE finance_settlement SET status=''CANCELLED'' WHERE id IN (' + $PAY_SETTLE + ')')
  if ($RevertNegativeLedgers) {
    Run ('UPDATE finance_payable SET status=''UNSETTLED'', paid_amount=0, unpaid_amount=amount WHERE id IN (' + $NEG_LEDGER + ')')
    Write-Host 'negative payables 291-294 restored to UNSETTLED (paid 0, unpaid = amount)'
  } else {
    Write-Host 'NOTE: payables 291-294 still show a NEGATIVE paid_amount (their invalid settlement was just cancelled).'
    Write-Host '      Re-run with -RevertNegativeLedgers to restore them to UNSETTLED with paid 0.'
  }
  Run ('UPDATE finance_receivable SET status=''CANCELLED'', amount=0, paid_amount=0, unpaid_amount=0, remark=CONCAT(IFNULL(remark,''''),'' [F7-202 audit-fixture cleanup 20260929]'') WHERE id IN (' + $LEDGER + ')')
  $reversal = ''
  if ($ReverseFlows) {
    $reversal = ' ; replaced by four reversal-flow inserts below'
  }
  Run ('UPDATE finance_receipt SET status=''CANCELLED'', remark=CONCAT(IFNULL(remark,''''),'' [F7-202 audit-fixture cleanup 20260929]'') WHERE id IN (' + $RC_ZERO + ',' + $RC_MISMATCH + ')')
  if ($ReverseFlows) {
    Run @'
INSERT INTO finance_cashflow (flow_no, account_id, account_name, flow_type, related_bill_no, related_bill_type, income, expense, remark, company_id, create_time)
SELECT CONCAT('FL-REV-', r.id), r.account_id, r.account_name, 'RECEIPT_REVERSE', r.code, 'RECEIPT', 0, r.amount,
       'F7-202 audit-fixture cleanup: reverse the 9.18 fixture income', r.company_id, NOW()
FROM finance_receipt r
WHERE r.id IN (40,42,44,46) AND NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM finance_cashflow) f
  WHERE f.related_bill_no = r.code AND f.flow_type = 'RECEIPT_REVERSE')
'@
    Write-Host 'reversal flow rows inserted for 40,42,44,46 (so cancelled receipts net to zero)'
  } else {
    Write-Host 'NOTE: receipts 40,42,44,46 still carry their original income flows -> run with -ReverseFlows'
    Write-Host '      if you want them to net to zero (invariant: CANCELLED => net flow 0).'
  }
  Write-Host 'mode=mark applied'
  # ---- F7-217 backfill: fill supplier_type from the authoritative label table ----
  # 口径与 FinancePaymentServiceImpl.resolveSupplierType / 修好后的 PayableHelper 一致：min(type_code)。
  Run @'
UPDATE finance_payable p
SET p.supplier_type = (SELECT MIN(t.type_code) FROM supplier_type_ref t WHERE t.supplier_id = p.supplier_id)
WHERE p.status <> 'CANCELLED' AND (p.supplier_type IS NULL OR p.supplier_type = '')
  AND EXISTS (SELECT 1 FROM supplier_type_ref t2 WHERE t2.supplier_id = p.supplier_id)
'@
  Write-Host 'supplier_type backfilled for payable rows that had labels but no type (F7-217)'
}

# ---------- self check ----------
Write-Host ''
Write-Host '=== self check (violations should be 0 after -Apply) ==='
Write-Host 'negative NORMAL RECEIVE settlements (status filter matters: the cleanup marks them CANCELLED):'
Q 'SELECT COUNT(*) FROM finance_settlement WHERE direction=''RECEIVE'' AND status=''NORMAL'' AND amount < 0'
Write-Host 'CANCELLED receipts whose net flow <> 0 (needs -ReverseFlows):'
Q 'SELECT COUNT(*) FROM finance_receipt r WHERE r.status=''CANCELLED'' AND IFNULL((SELECT SUM(income)-SUM(expense) FROM finance_cashflow f WHERE f.related_bill_no=r.code),0) <> 0'
Write-Host 'ledger rows that are not ADVANCE yet unpaid < 0:'
Q 'SELECT COUNT(*) FROM finance_receivable WHERE status <> ''ADVANCE'' AND IFNULL(unpaid_amount,0) < 0 AND amount > 0'
Write-Host 'ADVANCE bill_no longer than 40 chars:'
Q 'SELECT COUNT(*) FROM finance_receivable WHERE status=''ADVANCE'' AND LENGTH(bill_no) > 40'
Write-Host 'negative NORMAL PAY settlements (F7-211):'
Q 'SELECT COUNT(*) FROM finance_settlement WHERE direction=''PAY'' AND status=''NORMAL'' AND amount < 0'
Write-Host 'payables with labels but NULL supplier_type (F7-217):'
Q 'SELECT COUNT(*) FROM finance_payable p WHERE p.status <> ''CANCELLED'' AND (p.supplier_type IS NULL OR p.supplier_type = '''') AND EXISTS (SELECT 1 FROM supplier_type_ref t WHERE t.supplier_id = p.supplier_id)'
Write-Host 'done.'
