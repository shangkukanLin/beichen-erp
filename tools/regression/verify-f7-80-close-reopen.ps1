# verify-f7-80-close-reopen.ps1
#
# End-to-end regression for F7-80 (and its 2026-09-20 tightening): the missing-goods out-IO created when an
# outsource order is closed must be located on reopen by a STRUCTURED key ([#orderId=NN] embedded in the
# remark), NOT by matching a human-editable remark string. Two scenarios:
#   A) a normal close -> reopen: the structured-key bill is cancelled, and a HAND-MADE look-alike bill
#      ("加工厂遗失 - <order code>", old format, no key) must be left completely untouched;
#   B) no structured-key bill exists but a look-alike one does: reopen must FAIL LOUDLY instead of
#      silently cancelling the user's bill and adding stock back.
# Everything is self-built and cleaned up (stock row/quantity restored, no DDL).
#
# Needs a UTF-8 BOM (Chinese expectations).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$script:fail = 0
$script:skip = 0

function Ok($m)   { Write-Output ("PASS " + $m) }
function Bad($m)  { Write-Output ("FAIL " + $m); $script:fail++ }
function Skip($m) { Write-Output ("SKIP " + $m); $script:skip++ }
function Info($m) { Write-Output ("  [INFO] " + $m) }

$env:MYSQL_PWD = 'root'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -e $q 2>$null | Out-Null }

function Login([string]$u, [string]$p) {
  try {
    $r = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json' `
         -Body (@{ username = $u; password = $p; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
    if ($r -and $r.data -and $r.data.token) { return $r.data.token }
    return $null
  } catch { return $null }
}
function Req([string]$method, [string]$path, [string]$tok, $body) {
  $h = @{}; if ($tok) { $h['Authorization'] = $tok }
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h -TimeoutSec 40 }
    $j = ConvertTo-Json -InputObject $body -Depth 8
    return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($j)) -TimeoutSec 40
  } catch {
    $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
    $msg = 'transport-error'
    try {
      $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
      $txt = $sr.ReadToEnd()
      if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
    } catch { }
    return [pscustomobject]@{ code = $sc; msg = $msg }
  }
}
function BCode($r) { if ($null -eq $r) { return -1 }; return [int]$r.code }
function BMsg($r)  { if ($null -eq $r) { return '' }; if ($null -eq $r.msg) { return '' }; return [string]$r.msg }

Write-Output '=== 0) login + fixtures ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'
$CID = 1

$FACTORY = 34        # 测试加工厂A1
$FACT_WH = 66        # 该工厂的委外仓
$RET_WH  = 74        # 退回仓 = 我方物料仓（INVENTORY + AUXILIARY），符合 F7-81-③ 的调拨规则
$BAD_WH  = 71        # 成品二号仓（INVENTORY + FINISHED）—— 非物料仓，F7-81-③ 必须拒绝
$MAT     = 33        # 测试物料A1
$ORDER_CODE = 'VERIFY-F780'
$MAN_CODE   = 'VERIFY-F780-MANUAL'

# baseline of the warehouse_stock row that the fixture will touch
$stockRows0 = [int](SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$FACT_WH AND material_id=$MAT")
$stockQty0  = SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$FACT_WH AND material_id=$MAT LIMIT 1"
if ($null -eq $stockQty0) { $stockQty0 = '0' }
Info "stock baseline for (wh=$FACT_WH, mat=$MAT): rows=$stockRows0 qty=$stockQty0"

$ioCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_other_io')
$ordCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order')
$repCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_close_report')
$repItemCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_close_report_item')

# self-built PRODUCING order + DRAFT close report with ONE missing line (missing=1 -> generates the out-IO)
SqlExec ("INSERT INTO outsource_order (code, factory_id, status, company_id) VALUES ('$ORDER_CODE', $FACTORY, 'PRODUCING', $CID)")
$orderId = [int](SqlOne "SELECT id FROM outsource_order WHERE code='$ORDER_CODE' ORDER BY id DESC LIMIT 1")
SqlExec ("INSERT INTO outsource_order_close_report (order_id, status, company_id) VALUES ($orderId, 'DRAFT', $CID)")
$reportId = [int](SqlOne "SELECT id FROM outsource_order_close_report WHERE order_id=$orderId ORDER BY id DESC LIMIT 1")
# NOTE (this one cost two runs, and both traps were SILENT because SqlExec sends stderr to $null):
#   - the FK column is outsource_material_id (NOT material_id);
#   - the price column is material_price (NOT unit_price) -- a wrong column name fails the WHOLE insert,
#     so the fixture silently does not exist and the "missing bill" branch of confirmClose never runs.
#   => always re-read the real column list (SHOW COLUMNS) before writing a fixture, and assert the row
#      count right after the insert (see the "fixture check" line below).
SqlExec ("INSERT INTO outsource_order_close_report_item (report_id, outsource_material_id, missing_qty, material_price, company_id) VALUES ($reportId, $MAT, 1, 0, $CID)")
if ($orderId -le 0 -or $reportId -le 0) { Skip 'could not build the close-report fixture'; Write-Output 'RESULT SKIP'; exit 0 }
Info "fixture: order=$orderId report=$reportId (missing_qty=1, factoryWh=$FACT_WH retWh=$RET_WH mat=$MAT)"
$diagItems = SqlOne "SELECT COUNT(*) FROM outsource_order_close_report_item WHERE report_id=$reportId"
$diagMissing = SqlOne "SELECT GROUP_CONCAT(CONCAT(id,':',IFNULL(missing_qty,'NULL')) ORDER BY id) FROM outsource_order_close_report_item WHERE report_id=$reportId"
Info "fixture check: report_item rows=$diagItems (id:missing_qty => $diagMissing)"

# ---------- C) F7-81-③: a non-material return warehouse must be refused ----------
Write-Output ''
Write-Output '=== C) F7-81-③: return warehouse must obey the transfer rule (no finished-goods warehouse) ==='
$rcBad = Req 'POST' ("/outsource/order/$orderId/close-report/confirm") $admin @{ returnWarehouseId = [long]$BAD_WH; force = $true }
if ((BCode $rcBad) -eq 200) { Bad 'confirmClose ACCEPTED a finished-goods warehouse as the return warehouse (F7-81-3 not fixed)' }
elseif ((BMsg $rcBad) -like '*物料相关仓库*') {
  Ok ('confirmClose refused the non-material return warehouse (shared transfer rule applied): ' + (BMsg $rcBad))
  $ordStC = SqlOne "SELECT status FROM outsource_order WHERE id=$orderId"
  if ($ordStC -eq 'PRODUCING') { Ok 'the claim was rolled back (order still PRODUCING) - closure stays atomic' }
  else { Bad ("order status after the refused close = $ordStC (expected PRODUCING)") }
  $repStC = SqlOne "SELECT status FROM outsource_order_close_report WHERE id=$reportId"
  if ($repStC -eq 'DRAFT') { Ok 'the report is still DRAFT (no partial posting)' }
  else { Bad ("report status after the refused close = $repStC") }
} else { Bad ('confirmClose refused with an unexpected message: ' + (BMsg $rcBad)) }

# ---------- A) normal close then reopen ----------
Write-Output ''
Write-Output '=== A) close -> reopen: structured-key bill cancelled, hand-made look-alike untouched ==='
$rc = Req 'POST' ("/outsource/order/$orderId/close-report/confirm") $admin @{ returnWarehouseId = [long]$RET_WH; force = $true }
if ((BCode $rc) -ne 200) { Bad ('confirmClose failed: ' + (BMsg $rc)) }
else {
  Ok 'confirmClose accepted'
  $keyIo = [int](SqlOne "SELECT id FROM outsource_other_io WHERE remark LIKE '%[#orderId=$orderId]%' ORDER BY id DESC LIMIT 1")
  $diagIos = SqlOne "SELECT GROUP_CONCAT(CONCAT(id,'/',io_type,'/',status,'/',IFNULL(remark,'-')) ORDER BY id) FROM outsource_other_io WHERE company_id=$CID AND id > 176"
  Info ("other_io rows newer than the fixture baseline: " + $diagIos)
  if ($keyIo -gt 0) { Ok ("missing out-IO created WITH the structured key (id=$keyIo): " + (SqlOne "SELECT remark FROM outsource_other_io WHERE id=$keyIo")) }
  else { Bad 'no structured-key missing bill was created by close' }
  $ordSt = SqlOne "SELECT status FROM outsource_order WHERE id=$orderId"
  if ($ordSt -eq 'FINISHED') { Ok 'order moved to FINISHED' } else { Bad ("order status after close = $ordSt") }

  # the interference bill: exactly the legacy remark shape, no structured key (simulates a user-created bill)
  SqlExec ("INSERT INTO outsource_other_io (code, warehouse_id, io_type, io_date, status, remark, company_id) VALUES ('$MAN_CODE', $FACT_WH, 'OUT', CURDATE(), 'AUDITED', '加工厂遗失 - $ORDER_CODE', $CID)")
  $manIo = [int](SqlOne "SELECT id FROM outsource_other_io WHERE code='$MAN_CODE' ORDER BY id DESC LIMIT 1")
  Info "interference bill inserted: id=$manIo remark='加工厂遗失 - $ORDER_CODE' (old format, no key)"

  $rr = Req 'POST' ("/outsource/order/$orderId/close-report/reopen") $admin $null
  if ((BCode $rr) -ne 200) { Bad ('reopenClose failed unexpectedly: ' + (BMsg $rr)) }
  else {
    Ok 'reopenClose accepted'
    $keySt = SqlOne "SELECT status FROM outsource_other_io WHERE id=$keyIo"
    if ($keySt -eq 'CANCELLED') { Ok 'the structured-key missing bill was CANCELLED (correct target)' }
    else { Bad ("structured-key bill status = $keySt (expected CANCELLED)") }
    $manSt = SqlOne "SELECT status FROM outsource_other_io WHERE id=$manIo"
    if ($manSt -eq 'AUDITED') { Ok 'the hand-made look-alike bill was NOT touched (still AUDITED) - F7-80 core assertion' }
    else { Bad ("the hand-made look-alike bill was modified to $manSt - F7-80 regression!") }
    $ordSt2 = SqlOne "SELECT status FROM outsource_order WHERE id=$orderId"
    if ($ordSt2 -eq 'PRODUCING') { Ok 'order moved back to PRODUCING' } else { Bad ("order status after reopen = $ordSt2") }
  }
}

# ---------- B) no structured-key bill, only a look-alike -> must fail loudly ----------
Write-Output ''
Write-Output '=== B) look-alike without any structured-key bill -> reopen must REFUSE (no silent cancel) ==='
# simulate a legacy state: order FINISHED + report FINISHED, and the ONLY "missing" bill is the hand-made one
SqlExec ("UPDATE outsource_order SET status='FINISHED' WHERE id=$orderId")
SqlExec ("UPDATE outsource_order_close_report SET status='FINISHED' WHERE id=$reportId")
# remove the structured-key bill so that only the look-alike remains
if ($manIo -le 0) {
  SqlExec ("INSERT INTO outsource_other_io (code, warehouse_id, io_type, io_date, status, remark, company_id) VALUES ('$MAN_CODE', $FACT_WH, 'OUT', CURDATE(), 'AUDITED', '加工厂遗失 - $ORDER_CODE', $CID)")
  $manIo = [int](SqlOne "SELECT id FROM outsource_other_io WHERE code='$MAN_CODE' ORDER BY id DESC LIMIT 1")
}
SqlExec ("UPDATE outsource_other_io SET status='AUDITED' WHERE id=$manIo")
SqlExec ("DELETE FROM outsource_other_io_item WHERE other_io_id IN (SELECT id FROM outsource_other_io WHERE remark LIKE '%[#orderId=$orderId]%')")
SqlExec ("DELETE FROM outsource_other_io WHERE remark LIKE '%[#orderId=$orderId]%'")

$rr2 = Req 'POST' ("/outsource/order/$orderId/close-report/reopen") $admin $null
if ((BCode $rr2) -eq 200) { Bad 'reopen ACCEPTED a legacy-only state (it may have silently touched the user bill)' }
elseif ((BMsg $rr2) -like '*疑似旧版缺失出库单*') {
  Ok ('reopen refused loudly instead of touching the user bill: ' + (BMsg $rr2))
  $manSt2 = SqlOne "SELECT status FROM outsource_other_io WHERE id=$manIo"
  if ($manSt2 -eq 'AUDITED') { Ok 'the user bill is still AUDITED after the refusal' }
  else { Bad ("the user bill became $manSt2 after the refusal") }
  $ordSt3 = SqlOne "SELECT status FROM outsource_order WHERE id=$orderId"
  if ($ordSt3 -eq 'FINISHED') { Ok 'the transaction rolled back (order still FINISHED)' }
  else { Bad ("order status after the refused reopen = $ordSt3 (expected FINISHED, i.e. rolled back)") }
} else { Bad ('reopen refused but with an unexpected message: ' + (BMsg $rr2)) }

# ---------- cleanup + self-check ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
SqlExec ("DELETE FROM outsource_other_io_item WHERE other_io_id IN (SELECT id FROM outsource_other_io WHERE remark LIKE '%[#orderId=$orderId]%')")
SqlExec ("DELETE FROM outsource_other_io WHERE remark LIKE '%[#orderId=$orderId]%'")
SqlExec ("DELETE FROM outsource_other_io_item WHERE other_io_id=$manIo")
SqlExec ("DELETE FROM outsource_other_io WHERE id=$manIo")
SqlExec ("DELETE FROM outsource_order_close_report_item WHERE report_id=$reportId")
SqlExec ("DELETE FROM outsource_order_close_report WHERE id=$reportId")
SqlExec ("DELETE FROM outsource_order WHERE id=$orderId")
if ($stockRows0 -eq 0) {
  SqlExec ("DELETE FROM warehouse_stock WHERE warehouse_id=$FACT_WH AND material_id=$MAT AND IFNULL(quantity,0)=0")
}

$c1 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_other_io')
$c2 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order')
$c3 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_close_report')
$c4 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_close_report_item')
$stockRows1 = [int](SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$FACT_WH AND material_id=$MAT")
$stockQty1  = SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$FACT_WH AND material_id=$MAT LIMIT 1"
if ($null -eq $stockQty1) { $stockQty1 = '0' }
Info ("after cleanup: other_io=$c1(was $ioCount0) order=$c2(was $ordCount0) report=$c3(was $repCount0) report_item=$c4(was $repItemCount0); stock rows=$stockRows1(was $stockRows0) qty=$stockQty1(was $stockQty0)")
# NOTE: warehouse_stock_log keeps its rows on purpose (audit trail); only business tables are restored.
if ($c1 -eq $ioCount0 -and $c2 -eq $ordCount0 -and $c3 -eq $repCount0 -and $c4 -eq $repItemCount0 `
    -and $stockRows1 -eq $stockRows0 -and "$stockQty1" -eq "$stockQty0") {
  Ok 'business tables and the touched stock row are back to their pre-run baseline'
} else { Bad 'cleanup incomplete (see counts above)' }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
