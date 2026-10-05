-- audit 2026-10-04 batch2 (return/exchange rework) - READ ONLY precheck
SELECT 'A-PERM-CODES-601-603-605' AS part;
SELECT id, parent_id, menu_type, perms, visible FROM sys_menu
WHERE id IN (601, 603, 605) OR parent_id IN (601, 603, 605) ORDER BY parent_id, id;

SELECT 'B-SCHEMA-RETURN-EXCHANGE-ITEMS' AS part;
SELECT table_name, column_name, column_type FROM information_schema.columns
WHERE table_schema = 'beichen_erp'
  AND table_name IN ('sale_return', 'sale_return_item', 'sale_exchange', 'sale_exchange_item',
                     'purchase_return', 'purchase_return_item', 'purchase_exchange', 'purchase_exchange_item')
ORDER BY table_name, ordinal_position;

SELECT 'C-ROWS' AS part;
SELECT (SELECT COUNT(*) FROM sale_return) AS sale_returns, (SELECT COUNT(*) FROM sale_return_item) AS sale_return_items,
       (SELECT COUNT(*) FROM sale_exchange) AS sale_exchanges, (SELECT COUNT(*) FROM sale_exchange_item) AS sale_exchange_items,
       (SELECT COUNT(*) FROM purchase_return) AS pur_returns, (SELECT COUNT(*) FROM purchase_return_item) AS pur_return_items,
       (SELECT COUNT(*) FROM purchase_exchange) AS pur_exchanges, (SELECT COUNT(*) FROM purchase_exchange_item) AS pur_exchange_items;
