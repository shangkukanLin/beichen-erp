-- audit 2026-10-04 batch1 probe (round 2) — READ ONLY, zero data change.
SELECT 'H-STALE-SETTLE-AMOUNT (non-cash order carrying settle_amount)' AS part;
SELECT COUNT(*) AS non_cash_with_settle_amount FROM sale_order
WHERE settle_type <> 'CASH' AND settle_amount IS NOT NULL;
SELECT id, code, settle_type, total_amount, settle_amount, settle_account_id, status
FROM sale_order WHERE settle_type <> 'CASH' AND settle_amount IS NOT NULL;

SELECT 'I-CASH-NO-SPLIT-BUT-AMOUNT (legacy fallback branch footprint)' AS part;
SELECT o.id, o.code, o.status, o.total_amount, o.settle_amount, o.settle_account_id,
       (SELECT COUNT(*) FROM sale_order_settle_account s WHERE s.order_id = o.id) AS rows_cnt
FROM sale_order o WHERE o.settle_type = 'CASH';

SELECT 'J-RECEIVABLE-LEG (correct columns)' AS part;
SELECT o.id AS order_id, o.code, o.status AS order_status, o.total_amount AS order_total, o.settle_amount,
       r.id AS recv_id, r.bill_no, r.amount AS recv_amount, r.paid_amount, r.unpaid_amount, r.status AS recv_status
FROM sale_order o
LEFT JOIN finance_receivable r ON r.source_bill_type = 'SALE_ORDER' AND r.source_id = o.id
ORDER BY o.id DESC LIMIT 25;

SELECT 'K-RECEIPT-DETAIL-338 (2 split rows)' AS part;
SELECT r.id AS receipt_id, r.code, r.status, r.amount, r.account_id,
       (SELECT COUNT(*) FROM finance_receipt_account a WHERE a.receipt_id = r.id) AS acct_rows
FROM finance_receipt r WHERE r.source_bill_type = 'SALE_ORDER';
SELECT a.receipt_id, a.account_id, a.amount, a.remark FROM finance_receipt_account a
WHERE a.receipt_id IN (SELECT id FROM finance_receipt WHERE source_bill_type = 'SALE_ORDER') ORDER BY a.receipt_id, a.id;

SELECT 'L-CASHFLOW-FOR-THOSE-RECEIPTS' AS part;
SELECT c.id, c.receipt_id, c.account_id, c.amount, c.direction, c.type, c.bill_no
FROM finance_cashflow c
WHERE c.related_bill_no IN (SELECT code FROM finance_receipt WHERE source_bill_type = 'SALE_ORDER')
ORDER BY c.id DESC LIMIT 25;
