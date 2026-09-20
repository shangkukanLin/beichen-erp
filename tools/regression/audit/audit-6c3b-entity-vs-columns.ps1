# audit-6c3b-entity-vs-columns.ps1
#
# Batch 6c-3b (outsource entity layer): two-way diff between each entity's declared fields and the real
# DB columns.
#   - column in table but NO entity field  -> "dead column" candidate (nobody writes it)
#   - entity field (not @TableField(exist=false)) with no matching column -> broken mapping
# Matching rule: an explicit @TableField("x") wins; otherwise camelCase -> snake_case.
# This script is READ-ONLY (no writes anywhere). ASCII-only on purpose (PS 5.1 + BOM trap).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$env:MYSQL_PWD = 'root'
$EntityDir = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp\outsource\entity'

function Sql([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e $q 2>$null
  return @($o)
}
function Snake([string]$s) {
  return ([regex]::Replace($s, '(?<=[a-z0-9])([A-Z])', '_$1')).ToLower()
}

# entity class name -> table name (verified in 6c-3a)
$map = [ordered]@{
  'BomSnapshotItem'                = 'bom_snapshot_item'
  'BomSnapshot'                    = 'bom_snapshot'
  'CloseReportItem'                = 'outsource_order_close_report_item'
  'CloseReport'                    = 'outsource_order_close_report'
  'ContractTemplate'               = 'outsource_contract_template'
  'MaterialOrderItem'              = 'outsource_material_order_item'
  'MaterialOrder'                  = 'outsource_material_order'
  'OutsourceDeliveryItem'          = 'outsource_delivery_item'
  'OutsourceDelivery'              = 'outsource_delivery'
  'OutsourceMaterialComponent'     = 'outsource_material_component'
  'OutsourceMaterial'              = 'outsource_material'
  'OutsourceMaterialReturnItem'    = 'outsource_material_return_item'
  'OutsourceMaterialReturnRepair'  = 'outsource_material_return_repair'
  'OutsourceMaterialReturn'        = 'outsource_material_return'
  'OutsourceOrderDelivery'         = 'outsource_order_delivery'
  'OutsourceOrderMaterial'         = 'outsource_order_material'
  'OutsourceOrderProduct'          = 'outsource_order_product'
  'OutsourceOrder'                 = 'outsource_order'
  'OutsourceOtherIoItem'           = 'outsource_other_io_item'
  'OutsourceOtherIo'               = 'outsource_other_io'
  'OutsourceReturnOrderProduct'    = 'outsource_return_order_product'
  'OutsourceReturnOrderRepair'     = 'outsource_return_order_repair'
  'OutsourceStockLossItem'         = 'outsource_stock_loss_item'
  'OutsourceStockLoss'             = 'outsource_stock_loss'
  'ReturnOrderItem'                = 'outsource_return_order_item'
  'ReturnOrder'                    = 'outsource_return_order'
  'SupplierMaterial'               = 'supplier_material'
}

$totalDead = 0
$totalMissing = 0
$totalFields = 0

foreach ($e in $map.Keys) {
  $path = Join-Path $EntityDir ($e + '.java')
  if (-not (Test-Path $path)) { Write-Output ("=== {0} : FILE NOT FOUND" -f $e); continue }
  $txt = Get-Content -Raw -Encoding UTF8 $path

  # split into "field blocks": each is the annotation lines + the private declaration
  $declared = @{}          # column name -> field name
  $transientFields = @()
  $pattern = '(?m)(?:(@TableField\([^\)]*\)|@TableId\([^\)]*\))\s*)?^\s*private\s+[A-Za-z0-9_<>,\.\[\]\s]+\s+([A-Za-z0-9_]+)\s*;'
  foreach ($m in [regex]::Matches($txt, $pattern)) {
    $anno = $m.Groups[1].Value
    $field = $m.Groups[2].Value
    if ($anno -match 'exist\s*=\s*false') { $transientFields += $field; continue }
    $col = $null
    if ($anno -match '@TableField\("([^"]+)"\)') { $col = $matches[1] }
    if (-not $col) { $col = Snake $field }
    $declared[$col] = $field
  }

  $rows = Sql ("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='" + $map[$e] + "' ORDER BY ORDINAL_POSITION")
  $cols = @($rows | Where-Object { $_ -and $_.Trim() -ne '' } | ForEach-Object { $_.Trim() })

  $dead = @($cols | Where-Object { -not $declared.ContainsKey($_) })
  $missing = @($declared.Keys | Where-Object { $cols -notcontains $_ })

  $totalDead += $dead.Count
  $totalMissing += $missing.Count
  $totalFields += $declared.Count

  Write-Output ("--- {0}  (table={1}, entityFields={2}, dbCols={3})" -f $e, $map[$e], $declared.Count, $cols.Count)
  if ($transientFields.Count) { Write-Output ("    transient (@TableField exist=false): " + ($transientFields -join ', ')) }
  if ($dead.Count)    { Write-Output ("    DEAD COLUMNS (no entity field): " + ($dead -join ', ')) }
  if ($missing.Count) { Write-Output ("    BROKEN MAPPING (entity field, no column): " + ($missing -join ', ')) }
  if (-not $dead.Count -and -not $missing.Count) { Write-Output '    clean' }
}

Write-Output ''
Write-Output ("SUMMARY: entityFields={0} deadColumns={1} brokenMappings={2}" -f $totalFields, $totalDead, $totalMissing)
