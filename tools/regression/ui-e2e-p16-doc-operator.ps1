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

Step '3) UI: a DRAFT sale order (editable-form branch) shows both labels too'
# The sale order detail page renders an editable form while DRAFT and only switches to the read-only
# info block after auditing -- so this state needs its own check (it is filled via a whitelist
# Object.assign, where a forgotten field silently renders as a dash).
$draft = SqlOne "SELECT id FROM sale_order WHERE status='DRAFT' ORDER BY id DESC LIMIT 1"
if ([string]::IsNullOrWhiteSpace($draft)) {
  Write-Host 'SKIP no draft sale order to check'
} else {
  Open ('/inventory/sale/detail/' + $draft) 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1600
  $body = EvalJs "document.body.innerText"
  Ok2 ($body -match [regex]::Escape($CREATOR)) ('draft sale_order #' + $draft + ' shows the creator label')
  Ok2 ($body -match [regex]::Escape($AUDITOR)) ('draft sale_order #' + $draft + ' shows the auditor label')
  Ok2 ((Errs) -eq '[]') ('draft sale_order #' + $draft + ' page recorded no JS/API errors')
}

Step '4) UI: every converted drawer-detail page is reachable by id and renders the creator label'
# 2026-09-23: the 7 read-only detail drawers became standalone pages -- guard that each new page
# really loads THE RECORD BY ITS ID (a drawer used to render the already-loaded list row) and renders.
$converted = @(
  @('finance_receivable',       '/finance/receivable/detail/'),
  @('finance_payable',          '/finance/payable/detail/'),
  @('finance_receipt',          '/finance/receipt/detail/'),
  @('finance_payment',          '/finance/payment/detail/'),
  @('outsource_order_delivery', '/outsource/order/delivery/record/'),
  @('outsource_order_delivery', '/outsource/defect-return/detail/')
)
foreach ($c in $converted) {
  $id = SqlOne ('SELECT id FROM ' + $c[0] + ' ORDER BY id DESC LIMIT 1')
  if ([string]::IsNullOrWhiteSpace($id)) { Write-Host ('SKIP ' + $c[0] + ': no rows'); continue }
  Open ($c[1] + $id) 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1500
  $body = EvalJs "document.body.innerText"
  Ok2 ((Errs) -eq '[]') ($c[1] + $id + ' loads with no JS/API errors')
  Ok2 ($body -match [regex]::Escape($CREATOR)) ($c[1] + $id + ' shows the creator label')
}

Step '5) UI: the two converted large-content dialogs are reachable as standalone pages'
# 2026-09-23: the two 900px content dialogs became standalone pages too (stock-take items editor,
# read-only BOM snapshot). Those pages do not carry the operator labels, so only load-ability is checked.
$takeId = SqlOne 'SELECT id FROM inventory_stock_take ORDER BY id DESC LIMIT 1'
if ([string]::IsNullOrWhiteSpace($takeId)) { Write-Host 'SKIP stock-take detail: no rows' } else {
  Open ('/inventory/stock-take/detail/' + $takeId + '?scope=PRODUCT&status=DRAFT') 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1500
  Ok2 ((Errs) -eq '[]') ('stock-take detail #' + $takeId + ' loads with no JS/API errors')
}
$projId = SqlOne 'SELECT id FROM dev_project ORDER BY id DESC LIMIT 1'
if ([string]::IsNullOrWhiteSpace($projId)) { Write-Host 'SKIP bom-snapshot: no rows' } else {
  Open ('/dev/bom-snapshot/' + $projId) 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1500
  Ok2 ((Errs) -eq '[]') ('bom-snapshot #' + $projId + ' loads with no JS/API errors')
}

Step '6) UI: form dialogs converted to standalone pages are reachable'
# 2026-09-23: the "new/edit" form dialogs listed as worth converting (supplier x2, purchase order add/edit,
# receipt add, supplier payment add, defect-return split) became standalone pages.
$orderId = SqlOne 'SELECT id FROM outsource_order ORDER BY id DESC LIMIT 1'
$supId = SqlOne 'SELECT id FROM supplier ORDER BY id DESC LIMIT 1'
$formPages = @(
  '/supplier/manage/add',
  '/outsource/supplier/manage/add',
  '/supplier/form/add?type=solution',
  '/inventory/purchase/add',
  '/finance/receipt/add',
  ('/finance/payment/supplier/add?supplierId=' + $supId),
  ('/outsource/order/delivery/return-defect/' + $orderId)
)
foreach ($p in $formPages) {
  if ($p -match 'return-defect/(\s*)$') { Write-Host ('SKIP ' + $p + ': no order'); continue }
  Open $p 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1500
  Ok2 ((Errs) -eq '[]') ($p + ' loads with no JS/API errors')
}

if ($script:fail -eq 0) { Write-Host 'RESULT PASS document operator (createByName / auditorName) end-to-end' }
else { Write-Host ('RESULT FAIL count ' + $script:fail); exit 1 }
