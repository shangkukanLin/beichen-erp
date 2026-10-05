-- audit 2026-10-04 batch2 - READ ONLY: footprint of the relaxed upper limit (cumulative over-return/exchange)
SELECT 'ITEM-LINK-COLUMNS' AS part;
SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema = 'beichen_erp' AND table_name IN ('sale_exchange_item', 'purchase_return_item', 'purchase_exchange_item')
  AND (column_name LIKE '%item_id%' OR column_name LIKE '%order%' OR column_name = 'quantity') ORDER BY table_name, ordinal_position;

SELECT 'SALE-ITEM-CUMULATIVE' AS part;
SELECT i.id AS item_id, i.order_id, i.product_id, i.quantity AS sold,
       (SELECT IFNULL(SUM(r.quantity), 0) FROM sale_return_item r JOIN sale_return h ON h.id = r.return_id
        WHERE r.sale_order_item_id = i.id AND h.status = 'AUDITED') AS returned_audited,
       (SELECT IFNULL(SUM(x.quantity), 0) FROM sale_exchange_item x JOIN sale_exchange h2 ON h2.id = x.exchange_id
        WHERE x.sale_order_item_id = i.id AND h2.status = 'AUDITED') AS exchanged_audited
FROM sale_order_item i ORDER BY i.id;

SELECT 'RETURN-EXCHANGE-HEADERS' AS part;
SELECT 'sale_return' AS t, id, code, status, sale_order_id FROM sale_return
UNION ALL SELECT 'sale_exchange', id, code, status, sale_order_id FROM sale_exchange;

SELECT 'MONEY-LEGS-FOR-THOSE' AS part;
SELECT 'receivable' AS t, id, bill_no, source_bill_type, source_id, amount, paid_amount, unpaid_amount, status
FROM finance_receivable WHERE source_bill_type IN ('SALE_RETURN', 'SALE_EXCHANGE') ORDER BY id DESC LIMIT 20;
SELECT 'payable' AS t, id, bill_no, source_bill_type, source_id, amount, paid_amount, unpaid_amount, status
FROM finance_payable WHERE source_bill_type IN ('PURCHASE_RETURN', 'PURCHASE_EXCHANGE') ORDER BY id DESC LIMIT 20;
