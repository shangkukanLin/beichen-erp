-- audit 2026-10-04 batch1 (multi-account cash collection) — READ ONLY probes, zero data change.
-- Answers: (A) schema present? (B) settle_amount == SUM(split rows)? (C) over/under collection?
-- (D) receipt per order idempotent? receipt.amount == SUM(account rows)? (E) receivable legs consistent?
SELECT 'A-SCHEMA' AS part;
SELECT COUNT(*) AS settle_rows FROM sale_order_settle_account;
SELECT COUNT(*) AS sale_orders,
       SUM(settle_amount IS NOT NULL) AS with_settle_amount,
       SUM(settle_type = 'CASH') AS cash_orders,
       SUM(settle_type = 'CASH' AND settle_amount IS NULL) AS cash_without_amount
FROM sale_order;

SELECT 'B-SPLIT-INVARIANT' AS part;
SELECT o.id, o.code, o.settle_type, o.total_amount, o.settle_amount, o.settle_account_id,
       (SELECT COUNT(*) FROM sale_order_settle_account s WHERE s.order_id = o.id) AS rows_cnt,
       (SELECT IFNULL(SUM(s.amount), 0) FROM sale_order_settle_account s WHERE s.order_id = o.id) AS rows_sum
FROM sale_order o
WHERE o.settle_type = 'CASH' OR EXISTS (SELECT 1 FROM sale_order_settle_account s WHERE s.order_id = o.id)
ORDER BY o.id DESC LIMIT 25;

SELECT 'C-BOUNDS' AS part;
SELECT COUNT(*) AS over_collected FROM sale_order
WHERE settle_amount IS NOT NULL AND total_amount IS NOT NULL AND settle_amount > total_amount;
SELECT COUNT(*) AS non_positive_settle FROM sale_order WHERE settle_amount IS NOT NULL AND settle_amount <= 0;
SELECT COUNT(*) AS cash_non_cash_has_rows FROM sale_order o
WHERE o.settle_type <> 'CASH' AND EXISTS (SELECT 1 FROM sale_order_settle_account s WHERE s.order_id = o.id);

SELECT 'D-RECEIPTS-FROM-SALE' AS part;
SELECT r.source_id AS order_id, r.id AS receipt_id, r.status, r.amount, r.account_id,
       (SELECT COUNT(*) FROM finance_receipt_account a WHERE a.receipt_id = r.id) AS acct_rows,
       (SELECT IFNULL(SUM(a.amount), 0) FROM finance_receipt_account a WHERE a.receipt_id = r.id) AS acct_sum
FROM finance_receipt r WHERE r.source_bill_type = 'SALE_ORDER' ORDER BY r.source_id DESC LIMIT 25;

SELECT 'E-IDEMPOTENCY' AS part;
SELECT source_id, COUNT(*) AS non_cancelled FROM finance_receipt
WHERE source_bill_type = 'SALE_ORDER' AND status <> 'CANCELLED' GROUP BY source_id HAVING COUNT(*) > 1;

SELECT 'F-COLUMNS' AS part;
SELECT table_name, column_name, column_type FROM information_schema.columns
WHERE table_schema = 'beichen_erp' AND table_name IN ('finance_receivable', 'finance_receipt', 'finance_receipt_account')
ORDER BY table_name, ordinal_position;

SELECT 'G-RECEIVABLE-LEG' AS part;
SELECT o.id AS order_id, o.code, o.total_amount AS order_total, o.settle_amount,
       r.id AS recv_id, r.total_amount AS recv_total, r.status AS recv_status
FROM sale_order o
LEFT JOIN finance_receivable r ON r.source_bill_type = 'SALE_ORDER' AND r.source_id = o.id
WHERE o.settle_type = 'CASH' ORDER BY o.id DESC LIMIT 25;
