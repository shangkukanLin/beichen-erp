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
  [switch]$RevertNegativeLedgers,
  # D-13 (2026-09-29): apply ONE section only, e.g. '-Apply -Sections 7' for the F7-227 opening-flow backfill.
  # Empty = every section. Sections: 1,2,3,4 = batch A fixtures; 5,6 = batch B; 7 = F7-227; 8 = D-14 duplicate names;
  # 9 = F7-250 (cancel 0-yuan active claim receivables); 10 = F7-254 (tag pre-fix stock-loss bills).
  [string]$Sections = ''
)
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$STAMP = Get-Date -Format 'yyyyMMdd-HHmmss'
$BAK = Join-Path $env:TEMP ('audit-20260929-fin-cleanup-' + $STAMP + '.txt')
$Only = if ($Sections) { @($Sections -split '\s*,\s*') } else { @('1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12') }
function Want([string]$n) { return ($Only -contains $n) }
function Act([string]$n, [scriptblock]$b) { if (Want $n) { & $b } }
function S([string]$sql) {
  $l = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) | Where-Object { "$_" -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1
  return ("$l").Trim()
}
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

Write-Host '(9b) 0-yuan ACTIVE claim receivables (F7-250 target)'
Q 'SELECT id, bill_no, status, amount, unpaid_amount FROM finance_receivable WHERE source_bill_type=''OUTSOURCE_RETURN_BACK'' ORDER BY id'
Write-Host '(10b) pre-F7-201 stock-loss bills, no LOSS expense (F7-254 target)'
Q 'SELECT COUNT(*) FROM inventory_stock_loss s WHERE s.id BETWEEN 8 AND 21 AND s.status=''AUDITED'''
Q 'SELECT s.id, s.bill_no, s.status, s.total_amount, IFNULL(s.liable_party,''-''), IFNULL(s.remark,''-'') FROM inventory_stock_loss s WHERE s.id BETWEEN 8 AND 21 AND s.status=''AUDITED'' ORDER BY s.id'
Write-Host '(7) OPENING flows with an empty related_bill_no (F7-227 backfill target)'
Q "SELECT COUNT(*) FROM finance_cashflow WHERE flow_type='OPENING' AND IFNULL(related_bill_no,'')=''"
Q "SELECT cf.id, cf.flow_no, cf.account_id, IFNULL(cf.related_bill_no,'<empty>'), a.account_name, IFNULL(a.account_no,'') FROM finance_cashflow cf JOIN finance_account a ON a.id=cf.account_id WHERE cf.flow_type='OPENING' AND IFNULL(cf.related_bill_no,'')='' ORDER BY cf.id"
Write-Host '(8) duplicate account names (D-14: keep the lowest id, rename the rest, then add uk_account_name)'
Q 'SELECT company_id, account_name, COUNT(*) c, GROUP_CONCAT(id ORDER BY id) ids FROM finance_account GROUP BY company_id, account_name HAVING COUNT(*) > 1'
Q 'SELECT id, company_id, account_name, status FROM finance_account a WHERE EXISTS (SELECT 1 FROM (SELECT * FROM finance_account) b WHERE b.company_id=a.company_id AND b.account_name=a.account_name AND b.id<a.id) ORDER BY id'
Write-Host '(9) bill items whose paid/unpaid drifted from their ledger (F7-240 / D-18)'
Q "SELECT COUNT(*) AS drifted_receivable FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_receivable r ON r.id=i.source_id WHERE b.bill_type='RECEIVABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(r.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(r.unpaid_amount,0))>0.005)"
Q "SELECT COUNT(*) AS drifted_payable FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_payable y ON y.id=i.source_id WHERE b.bill_type='PAYABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(y.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(y.unpaid_amount,0))>0.005)"
Q "SELECT i.id, i.bill_id, i.source_id, i.amount, i.paid_amount, i.unpaid_amount FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_receivable r ON r.id=i.source_id WHERE b.bill_type='RECEIVABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(r.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(r.unpaid_amount,0))>0.005)"
Q "SELECT i.id, i.bill_id, i.source_id, i.amount, i.paid_amount, i.unpaid_amount FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_payable y ON y.id=i.source_id WHERE b.bill_type='PAYABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(y.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(y.unpaid_amount,0))>0.005)"

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
Dump 'finance_cashflow OPENING rows' 'SELECT * FROM finance_cashflow WHERE flow_type = ''OPENING'''
Dump 'finance_bill_item rows to resync (F7-240)' "SELECT i.* FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_receivable r ON r.id=i.source_id WHERE b.bill_type='RECEIVABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(r.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(r.unpaid_amount,0))>0.005)"
Dump 'finance_bill rows before recalc (F7-240)' 'SELECT id, bill_no, total_amount, paid_amount, unpaid_amount FROM finance_bill WHERE EXISTS (SELECT 1 FROM finance_bill_item i WHERE i.bill_id = finance_bill.id)'
Write-Host ''
Write-Host ('backup written: ' + $BAK)

# ---------- apply ----------
if ($Mode -eq 'delete') {
  Act '1' { Run ('DELETE FROM finance_settlement WHERE id IN (' + $SETTLE + ')') }
  Act '5' { Run ('DELETE FROM finance_settlement WHERE id IN (' + $PAY_SETTLE + ')') }
  if (Want '5') {
    if ($RevertNegativeLedgers) {
      Run ('UPDATE finance_payable SET status=''UNSETTLED'', paid_amount=0, unpaid_amount=amount WHERE id IN (' + $NEG_LEDGER + ')')
      Write-Host 'negative payables 291-294 restored to UNSETTLED (paid 0, unpaid = amount)'
    }
  }
  Act '2' { Run ('DELETE FROM finance_receivable WHERE id IN (' + $LEDGER + ')') }
  Act '3' {
    Run ('DELETE FROM finance_receipt_item WHERE receipt_id IN (' + $RC_ZERO + ')')
    Run ('DELETE FROM finance_receipt_account WHERE receipt_id IN (' + $RC_ZERO + ')')
    Run ('DELETE FROM finance_cashflow WHERE related_bill_no IN (SELECT code FROM finance_receipt WHERE id IN (' + $RC_ZERO + '))')
    Run ('DELETE FROM finance_receipt WHERE id IN (' + $RC_ZERO + ')')
  }
  Act '4' {
    Run ('DELETE FROM finance_receipt_item WHERE receipt_id IN (' + $RC_MISMATCH + ')')
    Run ('DELETE FROM finance_receipt_account WHERE receipt_id IN (' + $RC_MISMATCH + ')')
    Run ('DELETE FROM finance_cashflow WHERE related_bill_no IN (SELECT code FROM finance_receipt WHERE id IN (' + $RC_MISMATCH + '))')
    Run ('DELETE FROM finance_receipt WHERE id IN (' + $RC_MISMATCH + ')')
  }
  Write-Host 'mode=delete applied (rows physically removed; backup kept)'
} else {
  Act '1' { Run ('UPDATE finance_settlement SET status=''CANCELLED'' WHERE id IN (' + $SETTLE + ')') }
  Act '5' { Run ('UPDATE finance_settlement SET status=''CANCELLED'' WHERE id IN (' + $PAY_SETTLE + ')') }
  if (Want '5') {
    if ($RevertNegativeLedgers) {
      Run ('UPDATE finance_payable SET status=''UNSETTLED'', paid_amount=0, unpaid_amount=amount WHERE id IN (' + $NEG_LEDGER + ')')
      Write-Host 'negative payables 291-294 restored to UNSETTLED (paid 0, unpaid = amount)'
    } else {
      Write-Host 'NOTE: payables 291-294 still show a NEGATIVE paid_amount (their invalid settlement was just cancelled).'
      Write-Host '      Re-run with -RevertNegativeLedgers to restore them to UNSETTLED with paid 0.'
    }
  }
  Act '2' { Run ('UPDATE finance_receivable SET status=''CANCELLED'', amount=0, paid_amount=0, unpaid_amount=0, remark=CONCAT(IFNULL(remark,''''),'' [F7-202 audit-fixture cleanup 20260929]'') WHERE id IN (' + $LEDGER + ')') }
  Act '3' { Run ('UPDATE finance_receipt SET status=''CANCELLED'', remark=CONCAT(IFNULL(remark,''''),'' [F7-202 audit-fixture cleanup 20260929]'') WHERE id IN (' + $RC_ZERO + ')') }
  Act '4' { Run ('UPDATE finance_receipt SET status=''CANCELLED'', remark=CONCAT(IFNULL(remark,''''),'' [F7-202 audit-fixture cleanup 20260929]'') WHERE id IN (' + $RC_MISMATCH + ')') }
  if ((Want '4') -and $ReverseFlows) {
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
  } elseif (Want '4') {
    Write-Host 'NOTE: receipts 40,42,44,46 still carry their original income flows -> run with -ReverseFlows'
    Write-Host '      if you want them to net to zero (invariant: CANCELLED => net flow 0).'
  }
  Write-Host 'mode=mark applied'
  # ---- F7-217 backfill: fill supplier_type from the authoritative label table ----
  # 口径与 FinancePaymentServiceImpl.resolveSupplierType / 修好后的 PayableHelper 一致：min(type_code)。
  Act '6' {
    Run @'
UPDATE finance_payable p
SET p.supplier_type = (SELECT MIN(t.type_code) FROM supplier_type_ref t WHERE t.supplier_id = p.supplier_id)
WHERE p.status <> 'CANCELLED' AND (p.supplier_type IS NULL OR p.supplier_type = '')
  AND EXISTS (SELECT 1 FROM supplier_type_ref t2 WHERE t2.supplier_id = p.supplier_id)
'@
    Write-Host 'supplier_type backfilled for payable rows that had labels but no type (F7-217)'
  }
  # ---- D-18 (F7-240): resync bill items from their ledger, then recalc the bill totals ----
  # 与 F7-240 的代码口径一致：**台账是唯一真相**（明细 paid/unpaid 直接取台账值），
  # 明细变了必须同步重算账单主表（口径同 recalcBill：三项均为明细之和）。
  Act '9' {
    Run @'
UPDATE finance_bill_item i
JOIN finance_bill b ON b.id = i.bill_id
JOIN finance_receivable r ON r.id = i.source_id
SET i.paid_amount = IFNULL(r.paid_amount, 0), i.unpaid_amount = IFNULL(r.unpaid_amount, 0)
WHERE b.bill_type = 'RECEIVABLE' AND b.status <> 'CANCELLED'
  AND (ABS(IFNULL(i.paid_amount,0) - IFNULL(r.paid_amount,0)) > 0.005
    OR ABS(IFNULL(i.unpaid_amount,0) - IFNULL(r.unpaid_amount,0)) > 0.005)
'@
    Run @'
UPDATE finance_bill_item i
JOIN finance_bill b ON b.id = i.bill_id
JOIN finance_payable y ON y.id = i.source_id
SET i.paid_amount = IFNULL(y.paid_amount, 0), i.unpaid_amount = IFNULL(y.unpaid_amount, 0)
WHERE b.bill_type = 'PAYABLE' AND b.status <> 'CANCELLED'
  AND (ABS(IFNULL(i.paid_amount,0) - IFNULL(y.paid_amount,0)) > 0.005
    OR ABS(IFNULL(i.unpaid_amount,0) - IFNULL(y.unpaid_amount,0)) > 0.005)
'@
    Run @'
UPDATE finance_bill b
SET b.total_amount  = (SELECT IFNULL(SUM(i.amount),0)        FROM finance_bill_item i WHERE i.bill_id = b.id),
    b.paid_amount   = (SELECT IFNULL(SUM(i.paid_amount),0)   FROM finance_bill_item i WHERE i.bill_id = b.id),
    b.unpaid_amount = (SELECT IFNULL(SUM(i.unpaid_amount),0) FROM finance_bill_item i WHERE i.bill_id = b.id)
WHERE EXISTS (SELECT 1 FROM finance_bill_item i WHERE i.bill_id = b.id)
'@
    Write-Host 'bill items resynced from ledgers + bill totals recalculated (F7-240 / D-18)'
  }
  # ---- D-14: duplicate account names -> rename the 2nd+ rows of each group to "<name> #<id>" ----
  # Needed before the unique index can exist: ALTER TABLE finance_account ADD UNIQUE KEY uk_account_name
  # (company_id, account_name)  -- see docs/report 4.5. The derived table snapshot keeps the comparison
  # stable while rows are being rewritten.
  Act '8' {
    $dup = S 'SELECT COUNT(*) FROM finance_account a WHERE EXISTS (SELECT 1 FROM (SELECT * FROM finance_account) b WHERE b.company_id=a.company_id AND b.account_name=a.account_name AND b.id<a.id)'
    if ($dup -and [int]$dup -gt 0) {
      Run @'
UPDATE finance_account a
SET a.account_name = CONCAT(a.account_name, ' #', a.id)
WHERE EXISTS (
  SELECT 1 FROM (SELECT * FROM finance_account) b
  WHERE b.company_id = a.company_id AND b.account_name = a.account_name AND b.id < a.id
)
'@
      Write-Host ('duplicate account names renamed to "<name> #<id>" (D-14): ' + $dup + ' row(s)')
    } else {
      Write-Host 'no duplicate account names (D-14): nothing to rename'
    }
    $idx = S "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='beichen_erp' AND table_name='finance_account' AND index_name='uk_account_name'"
    if ($idx -eq '0') {
      Run 'ALTER TABLE finance_account ADD UNIQUE KEY uk_account_name (company_id, account_name)'
      Write-Host 'unique index uk_account_name (company_id, account_name) added (D-14)'
    } else {
      Write-Host 'unique index uk_account_name already present (D-14)'
    }
  }
  # ---- F7-250 (batch F fix): 0-yuan ACTIVE claim receivables from outsource return-backs ----
  # Code guard added 2026-09-30 (upsertReceivable returns early when amount<=0); these rows predate it.
  # Cancelling (not deleting) keeps the audit trail while removing the "0 元却不结清" ambiguity.
  Act '9' {
    Run @'
UPDATE finance_receivable
SET status = 'CANCELLED',
    remark = CONCAT(IFNULL(remark,''), ' [F7-250 0金额赔料应收，20260930作废]')
WHERE source_bill_type = 'OUTSOURCE_RETURN_BACK'
  AND status <> 'CANCELLED' AND IFNULL(amount,0) = 0
'@
    Write-Host '0-yuan active claim receivables cancelled (F7-250)'
  }
  # ---- F7-254 (batch F fix): pre-F7-201 stock-loss bills have NO financial leg ----
  # Option chosen: **tag** (do NOT fabricate retroactive vouchers). Creating LOSS expense rows for
  # historical bills is a business call (needs amount/date/remarks sign-off) - see report D-23/D-25.
  Act '10' {
    Run @'
UPDATE inventory_stock_loss
SET remark = CONCAT(IFNULL(remark,''), ' [F7-254 修复前历史单：审核时点早于报损财务化上线]')
WHERE id BETWEEN 8 AND 21 AND status = 'AUDITED'
  AND IFNULL(remark,'') NOT LIKE '%F7-254%'
'@
    Write-Host 'pre-fix stock-loss bills tagged (F7-254)'
  }
  # ---- F7-254 backfill (D-25 ①, 2026-09-30): create the missing LOSS vouchers for the pre-fix bills ----
  # 口径 = **与线上代码同源**（StockLossAccountingHelper.upsertLossExpense + FinanceExpenseServiceImpl.genNo）：
  #   expense_type=LOSS · amount=total_amount · expense_date=loss_date · account_id/account_name=NULL(非资金) ·
  #   source_bill_type=INVENTORY_STOCK_LOSS · source_id=报损单 id · source_bill_no=报损单号 · status=AUDITED ·
  #   expense_no = 'FY-' + yyyyMMdd(loss_date) + 3 位当日序号（在既有 FY-<date>### 之后顺延）·
  #   create_by/name = 该单审核人。金额 ≤ 0 的**不补建**（与 F7-250 的新护栏一致 ⇒ 6 张 0 元单自然跳过）。
  # 回滚：DELETE FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id BETWEEN 8 AND 21
  #       AND remark LIKE '%F7-254 修复前历史单补建%';
  Act '11' {
    Run @'
INSERT INTO finance_expense
  (expense_no, expense_type, amount, expense_date, account_id, account_name, remark,
   source_bill_type, source_id, source_bill_no, status, company_id, create_by, create_by_name,
   create_time, update_time)
SELECT CONCAT('FY-', DATE_FORMAT(x.loss_date, '%Y%m%d'), LPAD(
         (SELECT IFNULL(MAX(CAST(RIGHT(e.expense_no, 3) AS UNSIGNED)), 0) FROM finance_expense e
           WHERE e.expense_no LIKE CONCAT('FY-', DATE_FORMAT(x.loss_date, '%Y%m%d'), '%')) + x.rn, 3, '0')),
       'LOSS', x.total_amount, x.loss_date, NULL, NULL,
       CONCAT('报损单 ', x.code, '（承担方：内部损失）[F7-254 修复前历史单补建]'),
       'INVENTORY_STOCK_LOSS', x.id, x.code, 'AUDITED', x.company_id, x.auditor_id, x.auditor_name,
       NOW(), NOW()
FROM (SELECT s.id, s.code, s.loss_date, s.total_amount, s.company_id, s.auditor_id, s.auditor_name,
             ROW_NUMBER() OVER (PARTITION BY s.loss_date ORDER BY s.id) AS rn
      FROM inventory_stock_loss s
      WHERE s.id BETWEEN 8 AND 21 AND s.status = 'AUDITED'
        AND IFNULL(s.total_amount, 0) > 0
        AND NOT EXISTS (SELECT 1 FROM finance_expense e2
                        WHERE e2.source_bill_type = 'INVENTORY_STOCK_LOSS' AND e2.source_id = s.id)) x
'@
    Write-Host 'missing LOSS vouchers created for pre-fix stock-loss bills (F7-254 backfill)'
  }
  # ---- F7-263 (batch H): tag the 3 legacy doubled-ADVANCE receivable numbers - LEAVE THE NUMBERS ALONE ----
  # 归一化（改成 XTH-20260918003-ADVANCE）会与 id 161 **撞 uk_bill_no(company_id, bill_no)** ⇒ 只留痕：
  # 在这 3 行（均 CANCELLED）的备注追加说明，单号保持原样，避免后续误做归一化而引入唯一键冲突。
  Act '12' {
    Run @'
UPDATE finance_receivable
SET remark = CONCAT(IFNULL(remark,''), ' [F7-263 历史预收单号叠加残留，20260930 留痕：不重编号以免撞 uk_bill_no]')
WHERE bill_no LIKE '%-ADVANCE-ADVANCE%' AND IFNULL(remark,'') NOT LIKE '%F7-263%'
'@
    Write-Host 'legacy doubled-ADVANCE receivable numbers tagged (F7-263, numbers untouched)'
  }
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
Write-Host 'opening flows with an empty related_bill_no (F7-227):'
Q 'SELECT COUNT(*) FROM finance_cashflow WHERE flow_type=''OPENING'' AND IFNULL(related_bill_no,'''')='''''
Write-Host 'bill items whose paid/unpaid drifted from their ledger (F7-240):'
Q "SELECT (SELECT COUNT(*) FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_receivable r ON r.id=i.source_id WHERE b.bill_type='RECEIVABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(r.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(r.unpaid_amount,0))>0.005)) + (SELECT COUNT(*) FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_payable y ON y.id=i.source_id WHERE b.bill_type='PAYABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(y.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(y.unpaid_amount,0))>0.005))"
Write-Host 'done.'
