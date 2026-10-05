-- F7-297 follow-up: verify the new per-dimension snapshot query used by verify-fix-f7-61-69-71-79.
-- Read-only, ASCII only. Two checks:
--   (1) determinism: re-running must yield the same string;
--   (2) sensitivity: the stock map and the log map MUST differ at the one known historical residue
--       dimension (warehouse 133 / product 149 / grade A: stock 97 vs log -3), which proves the query
--       actually reflects discrepancies instead of always returning something equal.
SELECT '=== 1) stock map (SUM quantity per warehouse/product/material/quality) ===' AS section;
SELECT IFNULL(GROUP_CONCAT(k SEPARATOR ';'),'') AS stock_map
FROM (SELECT CONCAT(warehouse_id,'/',IFNULL(product_id,0),'/',IFNULL(material_id,0),'/',IFNULL(quality_type,'-'),'=',SUM(quantity)) k
      FROM warehouse_stock GROUP BY warehouse_id, product_id, material_id, quality_type
      ORDER BY warehouse_id, product_id, material_id, quality_type) t;

SELECT '=== 2) log map (SUM change_quantity, same dimensions) ===' AS section;
SELECT IFNULL(GROUP_CONCAT(k SEPARATOR ';'),'') AS log_map
FROM (SELECT CONCAT(warehouse_id,'/',IFNULL(product_id,0),'/',IFNULL(material_id,0),'/',IFNULL(quality_type,'-'),'=',SUM(change_quantity)) k
      FROM warehouse_stock_log GROUP BY warehouse_id, product_id, material_id, quality_type
      ORDER BY warehouse_id, product_id, material_id, quality_type) t;

SELECT '=== 3) the differing dimension(s) only (the historical residue) ===' AS section;
SELECT s.warehouse_id, s.product_id, s.material_id, s.quality_type, s.sq AS stock_sum, l.lq AS log_sum
FROM (SELECT warehouse_id,product_id,material_id,quality_type,SUM(quantity) sq FROM warehouse_stock GROUP BY 1,2,3,4) s
JOIN (SELECT warehouse_id,product_id,material_id,quality_type,SUM(change_quantity) lq FROM warehouse_stock_log GROUP BY 1,2,3,4) l
  ON s.warehouse_id=l.warehouse_id AND IFNULL(s.product_id,0)=IFNULL(l.product_id,0)
 AND IFNULL(s.material_id,0)=IFNULL(l.material_id,0) AND IFNULL(s.quality_type,'-')=IFNULL(l.quality_type,'-')
WHERE s.sq<>l.lq;
