# audit-6c3b-empty-columns.ps1
#
# Batch 6c-3b, second layer: for every column of the 26 outsource-related tables, report the ones that are
# ALL-NULL (or all-zero) on live data. Those are "dead column / dead field" candidates (see tools.md #29/#30):
# a column nobody writes stays NULL forever. READ-ONLY. ASCII-only on purpose.

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$env:MYSQL_PWD = 'root'

function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}

$tables = @(
  'outsource_order','outsource_order_product','outsource_order_delivery','outsource_order_close_report',
  'outsource_order_close_report_item','outsource_delivery','outsource_delivery_item','outsource_material',
  'outsource_material_component','outsource_material_return','outsource_material_return_item',
  'outsource_material_return_repair','outsource_material_order','outsource_material_order_item',
  'outsource_return_order','outsource_return_order_item','outsource_return_order_product',
  'outsource_return_order_repair','outsource_other_io','outsource_other_io_item','outsource_stock_loss',
  'outsource_stock_loss_item','outsource_contract_template','bom_snapshot','bom_snapshot_item','supplier_material'
)

Write-Output 'table | rows | ALL-NULL columns'
Write-Output '------|------|-----------------'
$totalEmpty = 0
foreach ($t in $tables) {
  $rows = SqlOne ("SELECT COUNT(*) FROM $t")
  $cols = @(& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='$t' ORDER BY ORDINAL_POSITION" 2>$null)
  if ([int]$rows -eq 0) { Write-Output ("{0} | 0 | (table empty, skipped)" -f $t); continue }
  $empty = @()
  foreach ($c in $cols) {
    $c = "$c".Trim(); if (-not $c) { continue }
    $n = SqlOne ("SELECT COUNT($c) FROM $t")
    if ("$n" -eq '0') { $empty += $c }
  }
  $totalEmpty += $empty.Count
  if ($empty.Count) { Write-Output ("{0} | {1} | {2}" -f $t, $rows, ($empty -join ', ')) }
  else { Write-Output ("{0} | {1} | (none)" -f $t, $rows) }
}
Write-Output ''
Write-Output ("SUMMARY: all-null columns across non-empty tables = {0}" -f $totalEmpty)
