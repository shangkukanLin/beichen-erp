-- F7-296 follow-up: WHICH values are flagged by the quality-enum invariant? Read-only, ASCII only.
SELECT 'stock rows by quality_type (all values, note any label-as-code like GOOD/D)' AS section;
SELECT quality_type, COUNT(*) AS rows_, SUM(quantity) AS qty FROM warehouse_stock GROUP BY quality_type ORDER BY quality_type;

SELECT 'exchange items by out_quality_type' AS section;
SELECT out_quality_type, COUNT(*) AS rows_ FROM sale_exchange_item GROUP BY out_quality_type ORDER BY out_quality_type;

SELECT 'the flagged rows themselves (not in A/B/C/DEFECT/PENDING)' AS section;
SELECT 'warehouse_stock' AS src, id, warehouse_id, product_id, material_id, quality_type, quantity, update_time
FROM warehouse_stock WHERE quality_type IS NOT NULL AND quality_type NOT IN ('A','B','C','DEFECT','PENDING')
UNION ALL
SELECT 'sale_exchange_item', id, NULL, NULL, NULL, out_quality_type, NULL, NULL
FROM sale_exchange_item WHERE out_quality_type IS NOT NULL AND out_quality_type NOT IN ('A','B','C','DEFECT','PENDING');

SELECT 'is D a legitimate grade anywhere else? (order/return items)' AS section;
SELECT 'sale_order_item' AS src, quality_type, COUNT(*) FROM sale_order_item GROUP BY quality_type
UNION ALL SELECT 'sale_return_item', quality_type, COUNT(*) FROM sale_return_item GROUP BY quality_type;
