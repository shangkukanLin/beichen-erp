-- audit 2026-10-04 · F7-265 audit-side probe — READ ONLY precheck (column names + fixtures)
SELECT 'CASHFLOW-COLUMNS' AS part;
SELECT column_name, column_type FROM information_schema.columns
WHERE table_schema = 'beichen_erp' AND table_name = 'finance_cashflow' ORDER BY ordinal_position;
SELECT 'STOCKLOG-COLUMNS' AS part;
SELECT column_name, column_type FROM information_schema.columns
WHERE table_schema = 'beichen_erp' AND table_name = 'warehouse_stock_log' ORDER BY ordinal_position;
SELECT 'RECEIPT-ITEM-COLUMNS' AS part;
SELECT column_name, column_type FROM information_schema.columns
WHERE table_schema = 'beichen_erp' AND table_name = 'finance_receipt_item' ORDER BY ordinal_position;
SELECT 'SETTLEMENT-COLUMNS' AS part;
SELECT column_name, column_type FROM information_schema.columns
WHERE table_schema = 'beichen_erp' AND table_name = 'finance_settlement' ORDER BY ordinal_position;
SELECT 'STOCK-FIXTURE (company 1, qty >= 5, good quality)' AS part;
SELECT s.warehouse_id, w.warehouse_name, s.product_id, s.quality_type, s.quantity
FROM warehouse_stock s LEFT JOIN warehouse w ON w.id = s.warehouse_id
WHERE s.company_id = 1 AND s.quantity >= 5 AND s.quality_type IN ('A', 'B', 'C')
ORDER BY s.quantity DESC LIMIT 6;
SELECT 'EXISTING-DRAFT-338 (template for the probe order)' AS part;
SELECT id, code, customer_id, warehouse_id, order_date, status, settle_type, settle_account_id, settle_amount, total_amount, company_id
FROM sale_order WHERE id = 338;
SELECT 'PAYABLE/RECEIVABLE-ADVANCE-NO-CONVENTION' AS part;
SELECT id, bill_no, source_bill_type, source_id, amount, paid_amount, unpaid_amount, status
FROM finance_receivable WHERE bill_no LIKE 'YS%' ORDER BY id DESC LIMIT 3;
