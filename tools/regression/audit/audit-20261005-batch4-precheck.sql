-- audit 2026-10-05 batch 4 (滞销/呆滞 + 产品分析) - READ ONLY precheck
-- Q1: which change types exist in the stock log, and how many are positive?
SELECT '--- Q1 stock log change types ---' AS section;
SELECT change_type, COUNT(*) AS c,
       SUM(change_quantity > 0) AS positive_rows,
       SUM(change_quantity < 0) AS negative_rows,
       SUM(change_quantity = 0) AS zero_rows
  FROM warehouse_stock_log
 GROUP BY change_type
 ORDER BY c DESC;

-- Q2: the mapper takes MIN(create_time) over change_quantity > 0 as "first inbound".
--     If a product's earliest positive row is NOT an inbound-ish type (e.g. an un-audit rollback or a move-in),
--     the "stock age / first-in date" is derived from something that was never an inbound event.
SELECT '--- Q2 type of the earliest positive log row, per product ---' AS section;
SELECT l.change_type AS first_positive_type, COUNT(*) AS products
  FROM (SELECT product_id, MIN(create_time) AS mt
          FROM warehouse_stock_log
         WHERE change_quantity > 0 AND product_id IS NOT NULL
         GROUP BY product_id) f
  JOIN warehouse_stock_log l
    ON l.product_id = f.product_id AND l.create_time = f.mt
 WHERE l.change_quantity > 0
 GROUP BY l.change_type
 ORDER BY products DESC;

-- Q3: how many products currently hold stock (the denominator of 滞销占比)
SELECT '--- Q3 products with stock (five-bucket denominator) ---' AS section;
SELECT COUNT(DISTINCT product_id) AS products_with_stock, COUNT(*) AS stock_rows
  FROM warehouse_stock WHERE product_id IS NOT NULL;

-- Q4: sale activity fixture - products whose last audited sale is older than 15 days (should appear as stagnant)
SELECT '--- Q4 last audited sale per product ---' AS section;
SELECT i.product_id, DATE_FORMAT(MAX(o.create_time), '%Y-%m-%d') AS last_sale_day
  FROM sale_order o JOIN sale_order_item i ON i.order_id = o.id
 WHERE o.status = 'AUDITED' AND i.product_id IS NOT NULL
 GROUP BY i.product_id
 ORDER BY last_sale_day
 LIMIT 10;
