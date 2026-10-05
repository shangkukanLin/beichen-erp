-- audit 2026-10-04 batch2 - READ ONLY: details of the returns/exchanges linked to sale order 339
SELECT 'SALE-ORDER-339-ITEMS' AS part;
SELECT id AS item_id, order_id, product_id, quantity AS sold, unit_price, amount FROM sale_order_item WHERE order_id = 339;

SELECT 'RETURNS-ON-339' AS part;
SELECT h.id AS return_id, h.code, h.status, h.sale_order_id, h.total_amount,
       i.id AS item_id, i.sale_order_item_id, i.product_id, i.quantity, i.quality_type, i.unit_price
FROM sale_return h JOIN sale_return_item i ON i.return_id = h.id
WHERE h.sale_order_id = 339 ORDER BY h.id, i.id;

SELECT 'EXCHANGES-ON-339' AS part;
SELECT h.id AS exchange_id, h.code, h.status, h.sale_order_id,
       x.id AS item_id, x.sale_order_item_id, x.product_id, x.quantity, x.out_quality_type, x.charge_amount
FROM sale_exchange h JOIN sale_exchange_item x ON x.exchange_id = h.id
WHERE h.sale_order_id = 339 ORDER BY h.id, x.id;

SELECT 'UNLINKED-RETURNS-EXCHANGES' AS part;
SELECT 'return' AS t, h.id, h.code, h.status, i.product_id, i.quantity, i.quality_type
FROM sale_return h JOIN sale_return_item i ON i.return_id = h.id WHERE h.sale_order_id IS NULL
UNION ALL
SELECT 'exchange', h.id, h.code, h.status, x.product_id, x.quantity, x.out_quality_type
FROM sale_exchange h JOIN sale_exchange_item x ON x.exchange_id = h.id WHERE h.sale_order_id IS NULL;
