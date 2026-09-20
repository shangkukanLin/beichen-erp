# verify-fix-f7-138.ps1
#
# F7-138 regression (2026-09-20): four "read-then-write" flows that move stock without any atomic guard
# were serialised with a row lock, plus a conditional-delete/update as a second line of defence:
#   - SupplierSettlementServiceImpl.returnMaterials  -> supplier-level FOR UPDATE (same lock its finish() uses);
#   - OutsourceReturnOrderServiceImpl.repairReturn / cancelRepairReturn / close / reOpen
#     -> ReturnOrderMapper.selectForUpdate (and delete/update now check the affected row count);
#   - OutsourceMaterialReturnServiceImpl (same four methods) -> OutsourceMaterialReturnMapper.selectForUpdate.
#
# Two things are asserted here:
#   1) the new @Select("<script>... FOR UPDATE</script>") statements actually PARSE AND RUN (an annotated SQL
#      error would only surface at runtime, never at compile time) - probed with a non-existent id, which
#      must answer "退货单不存在" (a business error) and NOT a database/MyBatis error;
#   2) the concurrent return-materials call now serialises on the supplier lock, so the rejection reason is
#      no longer the uk_code duplicate-key accident.
# Self-built + self-cleaned, no DDL.
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
function SqlAll([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e $q 2>$null
  return @($o | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
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

$SUP = 36; $OUT_WH = 68; $TO_WH = 74
$SNAP_FILE = Join-Path $PSScriptRoot '_f7138-snapshot.json'

Write-Output '=== 0) login + snapshot ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'

$snap = [ordered]@{
  dCount = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery')
  diCount = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery_item')
  slCount = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log')
  wsCount = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock')
  dMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_delivery')
  diMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_delivery_item')
  slMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock_log')
  wsMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock')
  rows68 = @(SqlAll "SELECT CONCAT(id,'|',IFNULL(material_id,0),'|',IFNULL(quality_type,'-'),'|',quantity) FROM warehouse_stock WHERE warehouse_id=$OUT_WH ORDER BY id")
  rows74 = @(SqlAll "SELECT CONCAT(id,'|',IFNULL(material_id,0),'|',IFNULL(quality_type,'-'),'|',quantity) FROM warehouse_stock WHERE warehouse_id=$TO_WH ORDER BY id")
}
$snap | ConvertTo-Json -Depth 6 | Set-Content -Path $SNAP_FILE -Encoding UTF8
$sum68Before = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH"
$sum74Before = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$TO_WH"
Info "before: SUM wh$OUT_WH=$sum68Before wh$TO_WH=$sum74Before (snapshot saved)"

# ---------- 1) the new FOR UPDATE statements must PARSE AND RUN ----------
Write-Output ''
Write-Output '=== 1) the new annotated FOR UPDATE queries actually run (probe with a non-existent id) ==='
# close / re-open / repair-return all call selectForUpdate() as their FIRST statement, so a bad @Select
# would blow up here with a database/MyBatis error instead of the expected "退货单不存在".
$probes = @(
  @{ m = 'PUT';    p = '/outsource/return-order/999999/close';        n = 'product  close' },
  @{ m = 'PUT';    p = '/outsource/return-order/999999/re-open';      n = 'product  re-open' },
  @{ m = 'POST';   p = '/outsource/return-order/999999/repair-return'; n = 'product  repair-return' },
  @{ m = 'PUT';    p = '/outsource/material-return/999999/close';     n = 'material close' },
  @{ m = 'PUT';    p = '/outsource/material-return/999999/re-open';   n = 'material re-open' },
  @{ m = 'POST';   p = '/outsource/material-return/999999/repair-return'; n = 'material repair-return' }
)
foreach ($pr in $probes) {
  $r = Req $pr.m $pr.p $admin (@{ items = @() })
  $msg = BMsg $r
  if ($msg -like '*不存在*') { Ok ("$($pr.n): row-lock query ran and answered a business error ('$msg')") }
  elseif ($msg -match 'Error|SQL|syntax|Exception|MyBatis') { Bad ("$($pr.n): DATABASE ERROR from the new @Select -> $msg") }
  else { Bad ("$($pr.n): unexpected answer -> $msg") }
}
# the delete probe must reach selectById (its guard runs before the lock), still no DB error expected
$rDel = Req 'DELETE' '/outsource/return-order/repair-return/999999' $admin $null
if ((BMsg $rDel) -like '*不存在*') { Ok 'product cancel-repair-return: answered a business error (no DB error)' }
else { Bad ("product cancel-repair-return: unexpected answer -> " + (BMsg $rDel)) }

# ---------- 2) concurrent return-materials must now serialise on the supplier lock ----------
Write-Output ''
Write-Output '=== 2) concurrent return-materials x3 -> must serialise (exactly one succeeds) ==='
$startAt = (Get-Date).AddSeconds(4).ToString('o')
$jobs = @()
foreach ($i in 1..3) {
  $jobs += Start-Job -ScriptBlock {
    param($api, $tok, $sid, $toWh, $runAt)
    $h = @{ Authorization = $tok }
    $b = @{ toWarehouseId = [long]$toWh } | ConvertTo-Json
    while ((Get-Date) -lt [datetime]::Parse($runAt)) { Start-Sleep -Milliseconds 20 }
    try {
      $r = Invoke-RestMethod -Uri "$api/supplier-settlement/$sid/return-materials" -Method Post -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 60
      return @{ code = [int]$r.code; msg = [string]$r.msg }
    } catch {
      $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
      $msg = 'transport-error'
      try {
        $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $txt = $sr.ReadToEnd()
        if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
      } catch { }
      return @{ code = $sc; msg = $msg }
    }
  } -ArgumentList $B, $admin, $SUP, $TO_WH, $startAt
}
$jobs | Wait-Job -Timeout 180 | Out-Null
$res = @($jobs | Receive-Job)
$jobs | Remove-Job -Force -ErrorAction SilentlyContinue

$okCount = @($res | Where-Object { $_.code -eq 200 }).Count
$rejectMsgs = @($res | Where-Object { $_.code -ne 200 } | ForEach-Object { $_.msg })
Info ("concurrent: 200 x$okCount, rejected x" + ($res.Count - $okCount) + " -> " + (($rejectMsgs | Select-Object -First 3) -join ' | '))
if ($okCount -eq 1) { Ok 'exactly one of three concurrent calls was accepted (serialised on the supplier lock)' }
else { Bad ("expected exactly 1 success, got $okCount") }

$viaLock = @($rejectMsgs | Where-Object { $_ -like '*无可退物料*' }).Count
$viaDup = @($rejectMsgs | Where-Object { $_ -like '*单号已被占用*' -or $_ -like '*Duplicate*' }).Count
if ($okCount -eq 1 -and $viaLock -ge 1) {
  Ok ("rejections now come from the LOCK (''无可退物料'' x$viaLock), not from the uk_code duplicate-key accident")
} elseif ($viaDup -ge 1) {
  Bad "rejections still come from the duplicate-key accident - the lock did not serialise the call"
} else { Bad ('unexpected rejection reasons: ' + ($rejectMsgs -join ' | ')) }

$sum68After = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH"
$sum74After = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$TO_WH"
$neg68 = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND quantity<0"
if ([double]$sum68After -eq 0 -and [int]$neg68 -eq 0) { Ok "out-warehouse $OUT_WH drained exactly once (0, no negative rows)" }
else { Bad "out-warehouse $OUT_WH sum=$sum68After negativeRows=$neg68 (expected 0 / 0)" }
$expected74 = [double]$sum74Before + [double]$sum68Before
if ([math]::Abs([double]$sum74After - $expected74) -lt 0.0001) { Ok "target warehouse $TO_WH moved exactly once ($sum74Before -> $sum74After)" }
else { Bad "target warehouse $TO_WH=$sum74After expected $expected74" }

# ---------- 3) sequential control ----------
Write-Output ''
Write-Output '=== 3) sequential control ==='
$rSeq = Req 'POST' "/supplier-settlement/$SUP/return-materials" $admin @{ toWarehouseId = [long]$TO_WH }
if ((BCode $rSeq) -ne 200 -and (BMsg $rSeq) -like '*无可退物料*') { Ok ('sequential repeat refused as expected: ' + (BMsg $rSeq)) }
else { Bad ('sequential repeat behaved unexpectedly: code=' + (BCode $rSeq) + ' msg=' + (BMsg $rSeq)) }

# ---------- 4) rollback ----------
Write-Output ''
Write-Output '=== 4) rollback ==='
$s = Get-Content $SNAP_FILE -Raw | ConvertFrom-Json
SqlExec ("DELETE FROM outsource_delivery_item WHERE id > $($s.diMax)")
SqlExec ("DELETE FROM outsource_delivery WHERE id > $($s.dMax)")
SqlExec ("DELETE FROM warehouse_stock_log WHERE id > $($s.slMax)")
foreach ($row in @($s.rows68)) { $p = $row -split '\|'; SqlExec ("UPDATE warehouse_stock SET quantity=$($p[3]) WHERE id=$($p[0])") }
foreach ($row in @($s.rows74)) { $p = $row -split '\|'; SqlExec ("UPDATE warehouse_stock SET quantity=$($p[3]) WHERE id=$($p[0])") }
SqlExec ("DELETE FROM warehouse_stock WHERE id > $($s.wsMax) AND warehouse_id IN ($OUT_WH,$TO_WH)")

Write-Output ''
Write-Output '=== 5) self-check ==='
$dNow = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery')
$diNow = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery_item')
$slNow = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log')
$wsNow = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock')
if ($dNow -eq $s.dCount -and $diNow -eq $s.diCount -and $slNow -eq $s.slCount -and $wsNow -eq $s.wsCount) {
  Ok ("counts restored: delivery=$dNow item=$diNow stockLog=$slNow stock=$wsNow")
} else {
  Bad ("count mismatch: delivery=$dNow/$($s.dCount) item=$diNow/$($s.diCount) stockLog=$slNow/$($s.slCount) stock=$wsNow/$($s.wsCount)")
}
$sum68Now = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH"
$sum74Now = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$TO_WH"
$rows68Now = @(SqlAll "SELECT CONCAT(id,'|',IFNULL(material_id,0),'|',IFNULL(quality_type,'-'),'|',quantity) FROM warehouse_stock WHERE warehouse_id=$OUT_WH ORDER BY id")
$rows74Now = @(SqlAll "SELECT CONCAT(id,'|',IFNULL(material_id,0),'|',IFNULL(quality_type,'-'),'|',quantity) FROM warehouse_stock WHERE warehouse_id=$TO_WH ORDER BY id")
if ("$sum68Now" -eq "$sum68Before" -and "$sum74Now" -eq "$sum74Before" -and
    ($rows68Now -join ';') -eq (@($s.rows68) -join ';') -and
    ($rows74Now -join ';') -eq (@($s.rows74) -join ';')) {
  Ok 'both warehouses restored row-by-row (counts + amounts + per-row values)'
} else {
  Bad "warehouse state NOT restored: wh$OUT_WH=$sum68Now/$sum68Before wh$TO_WH=$sum74Now/$sum74Before"
}

Write-Output ''
if ($script:fail -eq 0) { Remove-Item $SNAP_FILE -ErrorAction SilentlyContinue; Write-Output ("RESULT PASS (skip=" + $script:skip + ")"); exit 0 }
Write-Output ("RESULT FAIL count=" + $script:fail + " skip=" + $script:skip + " (snapshot kept at _f7138-snapshot.json)")
exit 1
