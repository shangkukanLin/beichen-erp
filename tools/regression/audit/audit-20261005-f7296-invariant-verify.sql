-- F7-296 follow-up: verify the corrected quality-enum invariant. Read-only, ASCII only.
SELECT 'before (as gate used to run it: sale_exchange_item.quality_type) -- expect ERROR 1054' AS section;
SELECT 'the corrected invariant (sale_exchange_item.out_quality_type) -- expect 0' AS section;
SELECT (SELECT COUNT(*) FROM warehouse_stock     WHERE quality_type     IS NOT NULL AND quality_type     NOT IN ('A','B','C','DEFECT','PENDING'))
     + (SELECT COUNT(*) FROM sale_order_item     WHERE quality_type     IS NOT NULL AND quality_type     NOT IN ('A','B','C','DEFECT','PENDING'))
     + (SELECT COUNT(*) FROM sale_return_item    WHERE quality_type     IS NOT NULL AND quality_type     NOT IN ('A','B','C','DEFECT','PENDING'))
     + (SELECT COUNT(*) FROM sale_exchange_item  WHERE out_quality_type IS NOT NULL AND out_quality_type NOT IN ('A','B','C','DEFECT','PENDING')) AS out_of_range_total;
