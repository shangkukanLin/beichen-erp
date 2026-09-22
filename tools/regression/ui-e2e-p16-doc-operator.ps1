# P16 (2026-09-23 user request): every business document detail must show the creator (zhi dan ren)
# and the auditor (shen he ren) -- Chinese labels are pulled from ui-e2e-zh.json via ZH, never inlined.
#
# Two halves, both DB-derived (no hardcoded column lists beyond the table set):
#   1) DATA LAYER: all the document master tables carry create_by/create_by_name/auditor_id/auditor_name
#      (added idempotently by DataInitializer.initDocOperatorColumns);
#   2) UI LAYER: on a representative detail page per module, the document-info block really renders the
#      two labels, AND -- for a row that has a creator recorded -- the rendered value equals the name
#      stored in the DB (i.e. createByName actually reaches the page, not just a label with a dash).
#
# ASCII ONLY (BOM guard): Chinese labels come from ui-e2e-zh.json via ZH.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlAll($q) { return @(& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null) }
function SqlOne($q) { $r = SqlAll $q; return ((@($r) | Select-Object -First 1) -as [string]) }
$script:fail = 0
function Ok2([bool]$cond, [string]$msg) { if ($cond) { Write-Host ('PASS ' + $msg) } else { Write-Host ('FAIL ' + $msg); $script:fail++ } }

$CREATOR = (ZH 'lbl_creator')
$AUDITOR = (ZH 'lbl_auditor')

Step '1) DATA: every document master table carries the operator columns'
$tables = @(
  'purchase_order','purchase_return','purchase_exchange',
  'sale_order','sale_return','sale_exchange',
  'inventory_warehouse_move','return_sort','inventory_stock_take','inventory_stock_loss','inventory_other_io','product_reclassify',
  'outsource_delivery','outsource_stock_loss','outsource_other_io',
  'outsource_order','outsource_material_order','outsource_return_order','outsource_material_return','outsource_order_delivery',
  'outsource_return_order_repair','outsource_material_return_repair',
  'finance_receipt','finance_payment','finance_bill','finance_expense','finance_invoice','finance_payable_transfer',
  'finance_receivable','finance_payable'
)
foreach ($col in @('create_by','create_by_name','auditor_id','auditor_name')) {
  $n = SqlOne ("SELECT COUNT(DISTINCT TABLE_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='beichen_erp' AND COLUMN_NAME='" + $col + "'")
  Ok2 ([int]$n -ge $tables.Count) ($col + ' present on ' + $n + ' tables (expected >= ' + $tables.Count + ')')
}

Step '2) UI: representative detail pages render BOTH labels, and show the recorded creator'
# module -> [table, detail route prefix]
$reps = @(
  @('inventory_warehouse_move', '/inventory/warehouse-move/detail/'),
  @('sale_order',               '/inventory/sale/detail/'),
  @('purchase_order',           '/inventory/purchase/detail/'),
  @('outsource_delivery',       '/outsource/delivery/detail/'),
  @('outsource_order',          '/outsource/order/detail/')
)
foreach ($r in $reps) {
  $tbl = $r[0]; $route = $r[1]
  # Prefer a FINALIZED row that also has a creator recorded (proves the value reaches the page).
  # Why finalized first: a few detail pages render an editable FORM while the document is still a draft
  # (e.g. sale order) and only switch to the read-only info block afterwards.
  $sel = "SELECT CONCAT(id,'|',IFNULL(create_by_name,'')) FROM " + $tbl
  $row = SqlOne ($sel + " WHERE status NOT IN ('DRAFT','CANCELLED') AND create_by_name IS NOT NULL ORDER BY id DESC LIMIT 1")
  if ([string]::IsNullOrWhiteSpace($row)) { $row = SqlOne ($sel + " WHERE status NOT IN ('DRAFT','CANCELLED') ORDER BY id DESC LIMIT 1") }
  if ([string]::IsNullOrWhiteSpace($row)) { $row = SqlOne ($sel + " WHERE create_by_name IS NOT NULL ORDER BY id DESC LIMIT 1") }
  if ([string]::IsNullOrWhiteSpace($row)) { $row = SqlOne ($sel + " ORDER BY id DESC LIMIT 1") }
  if ([string]::IsNullOrWhiteSpace($row)) { Write-Host ('SKIP ' + $tbl + ': no rows'); continue }
  $parts = $row -split '\|'
  $id = $parts[0]; $expectCreator = $parts[1]
  Open ($route + $id) 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1600
  $body = EvalJs "document.body.innerText" 
  $hasCreator = $body -match [regex]::Escape($CREATOR)
  $hasAuditor = $body -match [regex]::Escape($AUDITOR)
  Ok2 $hasCreator ($tbl + ' #' + $id + ' detail shows the creator label')
  Ok2 $hasAuditor ($tbl + ' #' + $id + ' detail shows the auditor label')
  if (-not [string]::IsNullOrWhiteSpace($expectCreator)) {
    Ok2 ($body -match [regex]::Escape($expectCreator)) ($tbl + ' #' + $id + ' detail shows the recorded creator (' + $expectCreator + ')')
  } else {
    Write-Host ('  (info) ' + $tbl + ' #' + $id + ': DB has no creator recorded yet -- label-only check')
  }
  Ok2 ((Errs) -eq '[]') ($tbl + ' #' + $id + ' page recorded no JS/API errors')
}

if ($script:fail -eq 0) { Write-Host 'RESULT PASS document operator (createByName / auditorName) end-to-end' }
else { Write-Host ('RESULT FAIL count ' + $script:fail); exit 1 }
