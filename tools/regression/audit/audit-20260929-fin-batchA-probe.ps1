# Audit probe (batch A - receipt chain). READ-ONLY: every query is a SELECT; nothing is written.
#
# v2 (2026-09-29): A5/A6 in v1 were mis-designed by me and produced false hits -- fixed here:
#   * A5 must only look at POSITIVE-amount receivables (negative ledger rows are returns/adjustments) and
#     must aggregate per receivable (not per receipt).
#   * A6 must compare the NET flow (income - expense) per bill, because a re-audited receipt legitimately
#     has several income rows plus the reverse rows from previous un-audits.
# Invariants checked:
#   A1 main amount = SUM(split accounts)          A2 write-off total <= receipt amount
#   A3 every receipt has >= 1 split row          A4 (guard) write-off > amount
#   A5 over-payment vs the ADVANCE row           A6 net cashflow per receipt (AUDITED=amount, DRAFT=0)
#   A7 ledger amount = paid + unpaid             A8 CANCELLED rows carrying paid
#   A9 ADVANCE inventory                         A10 negative unpaid rows
#   A11 tenant column NULL                       A12 duplicate codes
#   A13 settlements total = items total          A14 settlement rows = item rows
#   A15 status vs amounts (drift)                A16 receipts with amount NULL/0
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
function Q([string]$title, [string]$sql) {
  Write-Host ''
  Write-Host ('=== ' + $title + ' ===')
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null
  foreach ($l in @($out)) { Write-Host ('  ' + $l) }
  if (@($out).Count -eq 0) { Write-Host '  (no rows)' }
}

Write-Host '### batch A receipt-chain invariant sweep v2 (read-only) ###'

Q 'A5 over-payment on POSITIVE receivables vs its ADVANCE row' @'
SELECT r.id, r.bill_no, r.amount, s.settled, s.rcpts receipts, (s.settled - r.amount) over_paid,
       (SELECT COUNT(*) FROM finance_receivable a WHERE a.bill_no LIKE CONCAT(r.bill_no, '-ADVANCE%')) adv_rows,
       IFNULL((SELECT SUM(a.amount) FROM finance_receivable a WHERE a.bill_no LIKE CONCAT(r.bill_no, '-ADVANCE%')), 0) adv_sum
FROM finance_receivable r
JOIN (SELECT payable_receivable_id, SUM(amount) settled, COUNT(DISTINCT receipt_payment_id) rcpts
      FROM finance_settlement WHERE direction='RECEIVE' AND status='NORMAL'
      GROUP BY payable_receivable_id) s ON s.payable_receivable_id = r.id
WHERE r.amount > 0 AND s.settled > r.amount
ORDER BY over_paid DESC LIMIT 20
'@

Q 'A6 NET cashflow per receipt (AUDITED => net = amount ; DRAFT => net = 0)' @'
SELECT r.id, r.code, r.status, r.amount, f.income, f.expense, (f.income - f.expense) net
FROM finance_receipt r
JOIN (SELECT related_bill_no, SUM(income) income, SUM(expense) expense FROM finance_cashflow
      WHERE related_bill_type='RECEIPT' GROUP BY related_bill_no) f ON f.related_bill_no = r.code
WHERE (r.status='AUDITED' AND (f.income - f.expense) <> IFNULL(r.amount,0))
   OR (r.status='DRAFT'   AND (f.income - f.expense) <> 0)
   OR (r.status='CANCELLED' AND (f.income - f.expense) <> 0)
ORDER BY r.id LIMIT 20
'@

Q 'A13 settlements total vs items total, per receipt' @'
SELECT r.id, r.code, r.status, r.amount,
       IFNULL(i.s,0) item_sum, IFNULL(st.s,0) settlement_sum
FROM finance_receipt r
LEFT JOIN (SELECT receipt_id, SUM(this_amount) s FROM finance_receipt_item GROUP BY receipt_id) i ON i.receipt_id = r.id
LEFT JOIN (SELECT receipt_payment_id, SUM(amount) s FROM finance_settlement
           WHERE direction='RECEIVE' AND status='NORMAL' GROUP BY receipt_payment_id) st ON st.receipt_payment_id = r.id
WHERE IFNULL(i.s,0) <> IFNULL(st.s,0)
ORDER BY r.id LIMIT 20
'@

Q 'A14 settlement row count vs item row count, per receipt' @'
SELECT r.id, r.code, r.status,
       (SELECT COUNT(*) FROM finance_receipt_item i WHERE i.receipt_id = r.id) items,
       (SELECT COUNT(*) FROM finance_settlement s WHERE s.receipt_payment_id = r.id
          AND s.direction='RECEIVE' AND s.status='NORMAL') settlements
FROM finance_receipt r
WHERE (SELECT COUNT(*) FROM finance_receipt_item i WHERE i.receipt_id = r.id)
   <> (SELECT COUNT(*) FROM finance_settlement s WHERE s.receipt_payment_id = r.id
          AND s.direction='RECEIVE' AND s.status='NORMAL')
ORDER BY r.id LIMIT 20
'@

Q 'A15 ledger status drift (SETTLED with unpaid<>0 / UNSETTLED with paid<>0 / PARTIAL with unpaid=0)' @'
SELECT id, bill_no, amount, paid_amount, unpaid_amount, status
FROM finance_receivable
WHERE (status='SETTLED'   AND IFNULL(unpaid_amount,0) <> 0)
   OR (status='UNSETTLED' AND IFNULL(paid_amount,0) <> 0 AND IFNULL(amount,0) > 0)
   OR (status='PARTIAL'   AND (IFNULL(unpaid_amount,0) <= 0 OR IFNULL(paid_amount,0) <= 0))
ORDER BY id LIMIT 20
'@

Q 'A16 receipts with amount NULL / 0 (payload amount ignored => zero-value receipt?)' @'
SELECT r.id, r.code, r.status, r.amount, IFNULL(r.account_id,0) account_id,
       IFNULL((SELECT SUM(a.amount) FROM finance_receipt_account a WHERE a.receipt_id = r.id),0) acc_sum,
       IFNULL((SELECT SUM(i.this_amount) FROM finance_receipt_item i WHERE i.receipt_id = r.id),0) item_sum,
       IFNULL(r.source_bill_type,'-') sbt, IFNULL(r.source_bill_no,'-') sbn
FROM finance_receipt r
WHERE r.amount IS NULL OR r.amount = 0
ORDER BY r.id LIMIT 20
'@

Q 'A9 ADVANCE inventory (re-check after v2 fixes)' @'
SELECT id, bill_no, LENGTH(bill_no) len, amount, unpaid_amount, IFNULL(source_id,0) sid
FROM finance_receivable WHERE status='ADVANCE' ORDER BY id DESC LIMIT 20
'@

Write-Host ''
Write-Host '### sweep v2 done (all queries read-only) ###'
