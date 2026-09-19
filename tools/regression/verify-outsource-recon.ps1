# Outsourcing reconciliation probe - READ ONLY, ASCII OUTPUT ONLY (English column aliases).
# Usage: powershell -NoProfile -File verify-outsource-recon.ps1
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'

function Sql([string]$q, [string]$title) {
  Write-Host ''
  Write-Host ('===== ' + $title)
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  if (-not $o) { Write-Host '(no rows)'; return }
  @($o) | ForEach-Object { Write-Host $_ }
}

Sql "SELECT 'outsource_order' t, COUNT(*) n FROM outsource_order UNION ALL SELECT 'outsource_order_product', COUNT(*) FROM outsource_order_product UNION ALL SELECT 'outsource_order_delivery', COUNT(*) FROM outsource_order_delivery UNION ALL SELECT 'outsource_return_order', COUNT(*) FROM outsource_return_order UNION ALL SELECT 'outsource_return_order_repair', COUNT(*) FROM outsource_return_order_repair UNION ALL SELECT 'outsource_material_order', COUNT(*) FROM outsource_material_order UNION ALL SELECT 'outsource_material_order_item', COUNT(*) FROM outsource_material_order_item UNION ALL SELECT 'outsource_material_return', COUNT(*) FROM outsource_material_return UNION ALL SELECT 'outsource_material_return_repair', COUNT(*) FROM outsource_material_return_repair UNION ALL SELECT 'outsource_delivery', COUNT(*) FROM outsource_delivery UNION ALL SELECT 'outsource_delivery_item', COUNT(*) FROM outsource_delivery_item UNION ALL SELECT 'outsource_other_io', COUNT(*) FROM outsource_other_io UNION ALL SELECT 'outsource_stock_loss', COUNT(*) FROM outsource_stock_loss UNION ALL SELECT 'outsource_order_close_report', COUNT(*) FROM outsource_order_close_report UNION ALL SELECT 'bom_snapshot', COUNT(*) FROM bom_snapshot UNION ALL SELECT 'bom_snapshot_item', COUNT(*) FROM bom_snapshot_item" 'TABLE COUNTS'

Sql "SELECT status, COUNT(*) n FROM outsource_order GROUP BY status ORDER BY status" 'OUTSOURCE_ORDER BY STATUS'
Sql "SELECT status, COUNT(*) n FROM outsource_material_order GROUP BY status ORDER BY status" 'MATERIAL_ORDER BY STATUS'
Sql "SELECT return_type, status, COUNT(*) n, SUM(closed_flag) closed FROM outsource_return_order GROUP BY return_type, status ORDER BY return_type, status" 'RETURN_ORDER BY TYPE/STATUS'
Sql "SELECT return_type, status, COUNT(*) n, SUM(closed_flag) closed FROM outsource_material_return GROUP BY return_type, status ORDER BY return_type, status" 'MATERIAL_RETURN BY TYPE/STATUS'

# (1) finished-goods stock vs stock log
Sql "SELECT s.warehouse_id, s.product_id, s.quality_type, s.quantity stock_qty, COALESCE(l.qty,0) log_qty, s.quantity - COALESCE(l.qty,0) diff FROM warehouse_stock s LEFT JOIN (SELECT warehouse_id, product_id, quality_type, SUM(change_quantity) qty FROM warehouse_stock_log WHERE product_id IS NOT NULL GROUP BY warehouse_id, product_id, quality_type) l ON l.warehouse_id=s.warehouse_id AND l.product_id=s.product_id AND l.quality_type=s.quality_type WHERE s.product_id IS NOT NULL AND s.quantity <> COALESCE(l.qty,0)" '[1] PRODUCT STOCK vs LOG (diff rows)'

# (2) material stock vs stock log
Sql "SELECT s.warehouse_id, s.material_id, s.quantity stock_qty, COALESCE(l.qty,0) log_qty, s.quantity - COALESCE(l.qty,0) diff FROM warehouse_stock s LEFT JOIN (SELECT warehouse_id, material_id, SUM(change_quantity) qty FROM warehouse_stock_log WHERE material_id IS NOT NULL GROUP BY warehouse_id, material_id) l ON l.warehouse_id=s.warehouse_id AND l.material_id=s.material_id WHERE s.material_id IS NOT NULL AND s.quantity <> COALESCE(l.qty,0)" '[2] MATERIAL STOCK vs LOG (diff rows)'

# (3) stock log change_type distribution (find non-enum / chinese values)
Sql "SELECT change_type, COUNT(*) n FROM warehouse_stock_log WHERE change_type IS NULL OR change_type NOT REGEXP '^[A-Z_]+$' GROUP BY change_type" '[3] LOG change_type NOT ENUM-LIKE (should be empty)'

# (4) payables by source_bill_type, non cancelled
Sql "SELECT source_bill_type, status, COUNT(*) n, SUM(amount) amount FROM finance_payable WHERE supplier_id IS NOT NULL GROUP BY source_bill_type, status ORDER BY source_bill_type, status" '[4] PAYABLE by source_bill_type/status'

# (5) docs NOT audited but having payables (should be empty)
Sql "SELECT p.id, p.bill_no, p.source_bill_type, p.status, p.amount FROM finance_payable p WHERE p.source_bill_type IN ('OUTSOURCE_ORDER','OUTSOURCE_RETURN','OUTSOURCE_RETURN_CHARGE','OUTSOURCE_REPAIR_CHARGE','OUTSOURCE_MATERIAL_RETURN','OUTSOURCE_MATERIAL_DELIVERY','OUTSOURCE_EXCESS_LOSS') AND p.status <> 'CANCELLED' ORDER BY p.id DESC LIMIT 20" '[5] NON-CANCELLED OUTSOURCE PAYABLES (latest 20)'

# (6) bom_snapshot vs product rows coverage
Sql "SELECT COUNT(*) product_rows, SUM(bom_snapshot_id IS NOT NULL) with_snapshot FROM outsource_order_product" '[6] ORDER PRODUCT ROWS vs SNAPSHOT'
Sql "SELECT s.kind, COUNT(*) n, SUM(s.item_count) items FROM bom_snapshot s GROUP BY s.kind" '[7] SNAPSHOT by kind'

# (8) material order received vs receipts (per item)
Sql "SELECT i.id, i.order_id, i.outsource_material_id, i.order_quantity ordered, i.received_quantity received, i.defect_returned_qty defect_ret, i.repair_returned_qty repairing FROM outsource_material_order_item i ORDER BY i.id" '[8] MATERIAL ORDER ITEMS'

# (9) work-order product rows: planned vs delivered vs closed
Sql "SELECT p.id, p.order_id, p.product_name, p.quantity planned, p.bom_snapshot_id FROM outsource_order_product p ORDER BY p.order_id, p.id" '[9] ORDER PRODUCT ROWS'
Write-Host ''
Write-Host 'RECON DONE'
