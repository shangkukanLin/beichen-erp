# E2E data-state snapshot (read-only): prints row counts for every business table.
# Used at P0 (baseline, expect all zeros after a data clear) and P9 (final, prove "add-only, no delete").
# ASCII ONLY. Usage: powershell -File .\e2e-data-state.ps1 [-Out <file>]
param([string]$Out = '')
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$tables = @(
  # master data
  'brand','product','customer','supplier','supplier_material','supplier_product','outsource_material','outsource_material_component','warehouse','material_type','screen_model','outsource_contract_template',
  # R&D
  'dev_project','dev_project_phase','dev_bom','dev_drawing','dev_material_flow','bom_snapshot','bom_snapshot_item',
  # purchase
  'purchase_order','purchase_order_item','purchase_return','purchase_return_item',
  # outsource
  'outsource_material_order','outsource_material_order_item','outsource_order','outsource_order_product','outsource_order_material','outsource_delivery','outsource_delivery_item','outsource_order_delivery','outsource_order_close_report','outsource_order_close_report_item','outsource_return_order','outsource_return_order_item','outsource_return_order_product','outsource_return_order_repair','outsource_material_return','outsource_material_return_item','outsource_material_return_repair','outsource_other_io','outsource_other_io_item','outsource_stock_loss','outsource_stock_loss_item',
  # sale
  'sale_order','sale_order_item','sale_outbound','sale_outbound_item','sale_return','sale_return_item','sale_exchange','sale_exchange_item','return_sort','return_sort_item','after_sale_pending',
  # stock
  'warehouse_stock','warehouse_stock_log','inventory_other_io','inventory_other_io_item','inventory_stock_reclass','inventory_stock_reclass_item','product_reclassify','product_reclassify_item','inventory_warehouse_move','inventory_warehouse_move_item','inventory_stock_take','inventory_stock_take_item','inventory_stock_loss','inventory_stock_loss_item','cost_inbound_log',
  # finance
  'finance_account','finance_receivable','finance_payable','finance_receipt','finance_receipt_item','finance_payment','finance_payment_item','finance_expense','finance_invoice','finance_bill','finance_bill_item','finance_cashflow','finance_settlement','finance_payable_transfer'
)
$sql = ($tables | ForEach-Object { "SELECT '$_' AS tbl, COUNT(*) AS n FROM $_" }) -join ' UNION ALL '
$raw = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $sql 2>$null
$lines = @((@($raw) | ForEach-Object { "$_" }))
$total = 0
# NOTE: variable names are case-insensitive in PS -> must NOT reuse $Out (the param) here
$report = New-Object System.Collections.Generic.List[string]
foreach ($ln in ($lines | Select-Object -Skip 1)) {
  if (-not $ln) { continue }
  $p = ("$ln").Trim() -split "`t"
  if ($p.Count -lt 2) { continue }
  $n = [int]$p[1]
  $total += $n
  $line = '{0,-40} {1,8}' -f $p[0], $n
  $report.Add($line)
}
$report.Add(('-' * 50))
$tline = '{0,-40} {1,8}' -f 'TOTAL BUSINESS ROWS', $total
$report.Add($tline)
$report | ForEach-Object { Write-Output $_ }
if ($Out) { $report | Set-Content -Encoding UTF8 $Out; Write-Output ('written -> ' + $Out) }
