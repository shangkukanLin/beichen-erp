-- F7-296 triage: read-only. Locates the rows behind the two failing DB invariants and the two
-- "could not determine without shell" items reported by the triage subagents. ASCII only.
SELECT '=== 1) stock-vs-log diff row (this is what gate.ps1:72 counts) ===' AS section;
SELECT s.warehouse_id, s.product_id, s.material_id, s.quality_type, s.sq AS stock_sum, l.lq AS log_sum
FROM (SELECT warehouse_id,product_id,material_id,quality_type,SUM(quantity) sq FROM warehouse_stock GROUP BY 1,2,3,4) s
JOIN (SELECT warehouse_id,product_id,material_id,quality_type,SUM(change_quantity) lq FROM warehouse_stock_log GROUP BY 1,2,3,4) l
  ON s.warehouse_id=l.warehouse_id AND IFNULL(s.product_id,0)=IFNULL(l.product_id,0)
 AND IFNULL(s.material_id,0)=IFNULL(l.material_id,0) AND IFNULL(s.quality_type,'-')=IFNULL(l.quality_type,'-')
WHERE s.sq<>l.lq;

SELECT '=== 2) any stock/log group that exists on ONE side only (gate SQL is an inner join => blind to these) ===' AS section;
SELECT l.warehouse_id, l.product_id, l.material_id, l.quality_type, 0 AS stock_sum, l.lq AS log_sum
FROM (SELECT warehouse_id,product_id,material_id,quality_type,SUM(change_quantity) lq FROM warehouse_stock_log GROUP BY 1,2,3,4) l
LEFT JOIN (SELECT warehouse_id,product_id,material_id,quality_type,SUM(quantity) sq FROM warehouse_stock GROUP BY 1,2,3,4) s
  ON s.warehouse_id=l.warehouse_id AND IFNULL(s.product_id,0)=IFNULL(l.product_id,0)
 AND IFNULL(s.material_id,0)=IFNULL(l.material_id,0) AND IFNULL(s.quality_type,'-')=IFNULL(l.quality_type,'-')
WHERE s.warehouse_id IS NULL AND l.lq<>0
ORDER BY ABS(l.lq) DESC LIMIT 10;

SELECT '=== 3) quality_type columns per affected table (the invariant SQL names FOUR tables) ===' AS section;
SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema='beichen_erp' AND table_name IN ('sale_order_item','sale_return_item','sale_exchange_item','warehouse_stock')
  AND column_name LIKE '%quality%' ORDER BY table_name, column_name;

SELECT '=== 4) the invariant as gate runs it (statement 3 of 4 referenced a column that may not exist) ===' AS section;
SELECT (SELECT COUNT(*) FROM sale_order_item   WHERE quality_type IS NOT NULL AND quality_type NOT IN ('A','B','C','DEFECT','PENDING'))
     + (SELECT COUNT(*) FROM sale_return_item  WHERE quality_type IS NOT NULL AND quality_type NOT IN ('A','B','C','DEFECT','PENDING')) AS out_of_range_known_tables;

SELECT '=== 5) company A outsource orders (B8-1 fixture probe) ===' AS section;
SELECT company_id, status, COUNT(*) AS cnt FROM outsource_order GROUP BY company_id, status ORDER BY company_id, status;

SELECT '=== 6) users the rbac suite needs (B8-2) ===' AS section;
SELECT id, username, company_id, status FROM sys_user WHERE username IN ('audit_merch','lin');

SELECT '=== 7) bill fixture: the customer the bill suite hard-codes (B5-4/O-6) ===' AS section;
SELECT id, name FROM customer WHERE id=9;
SELECT id, name FROM customer ORDER BY id LIMIT 5;

SELECT '=== 8) negative-cost sources (B7 "-10 cost" divergence between two report pages) ===' AS section;
SELECT 'finance_cashflow' AS src, COUNT(*) AS rows_neg, SUM(expense) AS sum_neg FROM finance_cashflow WHERE expense < 0;
SELECT 'sale_order_item'  AS src, COUNT(*) AS rows_neg, SUM(cost_price*quantity) AS sum_neg FROM sale_order_item WHERE cost_price < 0;
