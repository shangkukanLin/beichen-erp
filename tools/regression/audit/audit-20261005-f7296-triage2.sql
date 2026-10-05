-- F7-296 triage part 2: characterize the one stock-vs-log mismatch group. Read-only, ASCII only.
SELECT '=== A) the diff group (wh133/prod149/A) broken down by change_type ===' AS section;
SELECT change_type, COUNT(*) AS rows_, SUM(change_quantity) AS sum_qty
FROM warehouse_stock_log WHERE warehouse_id=133 AND product_id=149 AND quality_type='A'
GROUP BY change_type ORDER BY SUM(change_quantity);

SELECT '=== B) the stock row(s) for that group (all stock forms) ===' AS section;
SELECT id, stock_form, quantity, update_time FROM warehouse_stock
WHERE warehouse_id=133 AND product_id=149 AND quality_type='A';

SELECT '=== C) how OLD is the newest log row of that group (historical fixture vs today) ===' AS section;
SELECT MIN(create_time) AS oldest, MAX(create_time) AS newest, COUNT(*) AS cnt
FROM warehouse_stock_log WHERE warehouse_id=133 AND product_id=149 AND quality_type='A';

SELECT '=== D) does the group appear in more than one warehouse/product pair? (top 5 mismatches) ===' AS section;
SELECT s.warehouse_id, s.product_id, s.material_id, s.quality_type, s.sq AS stock_sum, l.lq AS log_sum
FROM (SELECT warehouse_id,product_id,material_id,quality_type,SUM(quantity) sq FROM warehouse_stock GROUP BY 1,2,3,4) s
JOIN (SELECT warehouse_id,product_id,material_id,quality_type,SUM(change_quantity) lq FROM warehouse_stock_log GROUP BY 1,2,3,4) l
  ON s.warehouse_id=l.warehouse_id AND IFNULL(s.product_id,0)=IFNULL(l.product_id,0)
 AND IFNULL(s.material_id,0)=IFNULL(l.material_id,0) AND IFNULL(s.quality_type,'-')=IFNULL(l.quality_type,'-')
WHERE s.sq<>l.lq ORDER BY ABS(s.sq-l.lq) DESC LIMIT 5;
