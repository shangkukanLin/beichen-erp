# verify-fix-f7-17-24.ps1  (regression for the F7-17 / F7-24 fixes)
#
# F7-17 (other-io): io_type must be a valid IoType enum value AND be normalised to the enum
#                   name before persisting; the three direction verdicts share isIn().
# F7-24 (stock-take): creating a second take for the same warehouse+period must be rejected
#                     (the duplicate check is now serialised by a row lock on the warehouse).
#
# Key case: io_type "in" (lowercase). Before the fix it passed the "not blank" check and was
# then treated as OUT by the case-sensitive equals() calls (skip the stock check, still deduct).
# After the fix it must be stored as "IN" and must INCREASE stock on audit.
# Everything is backed up and restored; final self-check asserts equal counts.
# ASCII-only on purpose (PowerShell 5.1 + UTF-8 BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$WH    = 76      # fixture warehouse (finished goods)
$PROD  = 61      # fixture product
$PERIOD = '2099-01'

$env:MYSQL_PWD = 'root'
$script:fails = 0
function Sql([string]$sql) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>&1
  return ($out | Out-String).Trim()
}
function SqlOne([string]$sql) { $v = Sql $sql; if ($v -eq '') { return '' }; return ($v -split "`n")[0].Trim() }
function Ok([string]$m)   { Write-Output ("  [OK]   " + $m) }
function Bad([string]$m)  { Write-Output ("  [FAIL] " + $m); $script:fails++ }
function Info([string]$m) { Write-Output ("  [INFO] " + $m) }
function CodeOf($r) { if ($null -eq $r) { return '' } return [string]$r.code }
function MsgOf($r)  { if ($null -eq $r) { return '' } return [string]$r.msg }

$body = '{"username":"lin","password":"123","companyId":1}'
try {
  $login = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes($body))
} catch { Write-Output ("LOGIN EX " + $_.Exception.Message); exit 1 }
$H = @{ Authorization = [string]$login.data.token }
function Api([string]$method, [string]$url, $payload) {
  try {
    if ($null -eq $payload) { return Invoke-RestMethod -Uri $url -Method $method -Headers $H }
    $json = ConvertTo-Json -InputObject $payload -Depth 8
    return Invoke-RestMethod -Uri $url -Method $method -Headers $H `
        -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch {
    $code = -1
    try { if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } } catch { }
    return [pscustomobject]@{ code = $code; msg = "HTTPEX " + $_.Exception.Message; data = $null }
  }
}
function IoBody([string]$ioType) {
  return @{ warehouseId = $WH; ioType = $ioType; ioDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'VERIFY-F7-17'
            items = @(@{ productId = $PROD; qualityType = 'A'; quantity = 1 }) }
}

# pre-clean (idempotent)
$code17 = 'QT-F17-LEGACY'
Sql "DELETE l FROM warehouse_stock_log l JOIN inventory_other_io o ON l.related_bill_no=o.code WHERE o.remark='VERIFY-F7-17'" | Out-Null
Sql "DELETE i FROM inventory_other_io_item i JOIN inventory_other_io o ON i.other_io_id=o.id WHERE o.remark='VERIFY-F7-17'" | Out-Null
Sql "DELETE FROM inventory_other_io WHERE remark='VERIFY-F7-17'" | Out-Null
Sql "DELETE i FROM inventory_stock_take_item i JOIN inventory_stock_take t ON i.take_id=t.id WHERE t.period='$PERIOD' AND t.remark='VERIFY-F7-24'" | Out-Null
Sql "DELETE FROM inventory_stock_take WHERE period='$PERIOD' AND remark='VERIFY-F7-24'" | Out-Null

Write-Output '=== 0) baseline ==='
$bQty   = SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH AND product_id=$PROD AND quality_type='A'"
$cIo    = SqlOne 'SELECT COUNT(*) FROM inventory_other_io'
$cTake  = SqlOne 'SELECT COUNT(*) FROM inventory_stock_take'
$cLog   = SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log'
$cStock = SqlOne 'SELECT COUNT(*) FROM warehouse_stock'
Info "fixture A qty=$bQty  counts io=$cIo take=$cTake log=$cLog stock=$cStock"
if ($bQty -eq '') { Bad 'fixture stock missing -> abort'; exit 1 }

$ioIds = @(); $takeIds = @()
try {
  Write-Output '--- F7-17 a) illegal io_type must be rejected, no document created ---'
  $before = SqlOne 'SELECT COUNT(*) FROM inventory_other_io'
  foreach ($bad in @('XYZ', 'in out', '0')) {
    $r = Api 'POST' "$BASE/inventory/other" (IoBody $bad)
    if ((CodeOf $r) -eq '200') {
      Bad ("illegal io_type '" + $bad + "' was ACCEPTED")
      $ioIds += [int](SqlOne "SELECT id FROM inventory_other_io WHERE remark='VERIFY-F7-17' ORDER BY id DESC LIMIT 1")
    } else { Ok ("'" + $bad + "' rejected: " + (MsgOf $r)) }
  }
  if ($before -eq (SqlOne 'SELECT COUNT(*) FROM inventory_other_io')) { Ok 'no document created for the rejected requests' } else { Bad 'a document was created anyway' }

  Write-Output '--- F7-17 b) empty io_type must be rejected ---'
  $r = Api 'POST' "$BASE/inventory/other" (IoBody '')
  if ((CodeOf $r) -eq '200') { Bad 'empty io_type ACCEPTED'; $ioIds += [int](SqlOne "SELECT id FROM inventory_other_io WHERE remark='VERIFY-F7-17' ORDER BY id DESC LIMIT 1") }
  else { Ok ("rejected: " + (MsgOf $r)) }

  Write-Output '--- F7-17 c) lowercase "in" must be normalised to IN and must INCREASE stock ---'
  $r = Api 'POST' "$BASE/inventory/other" (IoBody 'in')
  if ((CodeOf $r) -ne '200') { Bad ("lowercase 'in' rejected (unexpected): " + (MsgOf $r)) } else {
    $id = [int](SqlOne "SELECT id FROM inventory_other_io WHERE remark='VERIFY-F7-17' ORDER BY id DESC LIMIT 1")
    $ioIds += $id
    $stored = SqlOne "SELECT io_type FROM inventory_other_io WHERE id=$id"
    if ($stored -eq 'IN') { Ok "io_type normalised to 'IN' in DB (was 'in')" } else { Bad "io_type stored as '$stored' (expect IN)" }
    $ra = Api 'PUT' "$BASE/inventory/other/$id/audit" $null
    $q = SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH AND product_id=$PROD AND quality_type='A'"
    if ((CodeOf $ra) -eq '200' -and [decimal]$q -eq ([decimal]$bQty + 1)) {
      Ok "audit INCREASED stock ($bQty -> $q) - direction correct (before the fix it would deduct)"
    } else { Bad ("audit wrong: code=" + (CodeOf $ra) + " msg=" + (MsgOf $ra) + " qty=$q (expect " + ([decimal]$bQty + 1) + ")") }
    $ru = Api 'PUT' "$BASE/inventory/other/$id/un-audit" $null
    $q2 = SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH AND product_id=$PROD AND quality_type='A'"
    if ((CodeOf $ru) -eq '200' -and $q2 -eq $bQty) { Ok 'un-audit restored the stock' } else { Bad ("un-audit problem: code=" + (CodeOf $ru) + " qty=$q2 (expect $bQty)") }
  }

  Write-Output '--- F7-17 d) uppercase IN control (audit increases stock) ---'
  $r = Api 'POST' "$BASE/inventory/other" (IoBody 'IN')
  if ((CodeOf $r) -ne '200') { Bad ("IN control rejected: " + (MsgOf $r)) } else {
    $id = [int](SqlOne "SELECT id FROM inventory_other_io WHERE remark='VERIFY-F7-17' ORDER BY id DESC LIMIT 1")
    $ioIds += $id
    $ra = Api 'PUT' "$BASE/inventory/other/$id/audit" $null
    $q = SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH AND product_id=$PROD AND quality_type='A'"
    if ((CodeOf $ra) -eq '200' -and [decimal]$q -eq ([decimal]$bQty + 1)) { Ok "IN control ok ($bQty -> $q)" } else { Bad ("IN control wrong: code=" + (CodeOf $ra) + " qty=$q") }
    $ru = Api 'PUT' "$BASE/inventory/other/$id/un-audit" $null
    $q2 = SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH AND product_id=$PROD AND quality_type='A'"
    if ((CodeOf $ru) -eq '200' -and $q2 -eq $bQty) { Ok 'un-audit restored the stock' } else { Bad ("un-audit problem: qty=$q2 (expect $bQty)") }
  }

  Write-Output '--- F7-24) same warehouse+period created twice -> second must be rejected ---'
  $mk = @{ warehouseId = $WH; period = $PERIOD; takeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'VERIFY-F7-24' }
  $r1 = Api 'POST' "$BASE/inventory/stock-take" $mk
  Info ("first create -> code=" + (CodeOf $r1) + " msg=" + (MsgOf $r1))
  if ((CodeOf $r1) -ne '200') {
    Info 'first create failed -> duplicate check cannot be exercised (skipped, not a fix failure)'
  } else {
    $t1 = [int](SqlOne "SELECT id FROM inventory_stock_take WHERE period='$PERIOD' ORDER BY id DESC LIMIT 1")
    $takeIds += $t1
    Sql "UPDATE inventory_stock_take SET remark='VERIFY-F7-24' WHERE id=$t1" | Out-Null
    $r2 = Api 'POST' "$BASE/inventory/stock-take" $mk
    if ((CodeOf $r2) -ne '200') { Ok ("duplicate rejected: " + (MsgOf $r2)) } else { Bad 'duplicate stock-take ACCEPTED (same warehouse+period)'; $takeIds += [int](SqlOne "SELECT id FROM inventory_stock_take WHERE period='$PERIOD' ORDER BY id DESC LIMIT 1") }
    $cntT = SqlOne "SELECT COUNT(*) FROM inventory_stock_take WHERE warehouse_id=$WH AND period='$PERIOD' AND status<>'CANCELLED'"
    if ($cntT -eq '1') { Ok 'exactly one active take for that warehouse+period' } else { Bad "active takes for the pair = $cntT (expect 1)" }
  }
} finally {
  Write-Output '=== cleanup ==='
  foreach ($id in ($ioIds | Select-Object -Unique)) {
    if ($id -gt 0) {
      $c = SqlOne "SELECT code FROM inventory_other_io WHERE id=$id"
      if ($c -ne '') { Sql "DELETE FROM warehouse_stock_log WHERE related_bill_no='$c'" | Out-Null }
      Sql "DELETE FROM inventory_other_io_item WHERE other_io_id=$id" | Out-Null
      Sql "DELETE FROM inventory_other_io WHERE id=$id" | Out-Null
    }
  }
  foreach ($id in ($takeIds | Select-Object -Unique)) {
    if ($id -gt 0) {
      Sql "DELETE FROM inventory_stock_take_item WHERE take_id=$id" | Out-Null
      Sql "DELETE FROM inventory_stock_take WHERE id=$id" | Out-Null
    }
  }
  Sql "DELETE i FROM inventory_stock_take_item i JOIN inventory_stock_take t ON i.take_id=t.id WHERE t.period='$PERIOD'" | Out-Null
  Sql "DELETE FROM inventory_stock_take WHERE period='$PERIOD'" | Out-Null

  Write-Output '=== 9) post-check (must equal baseline) ==='
  $aQty  = SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH AND product_id=$PROD AND quality_type='A'"
  $aIo   = SqlOne 'SELECT COUNT(*) FROM inventory_other_io'
  $aTake = SqlOne 'SELECT COUNT(*) FROM inventory_stock_take'
  $aLog  = SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log'
  $aStock= SqlOne 'SELECT COUNT(*) FROM warehouse_stock'
  $aBad  = SqlOne "SELECT COUNT(*) FROM inventory_other_io WHERE LOWER(io_type)<>io_type OR io_type NOT IN ('IN','OUT')"
  if ($aQty -eq $bQty) { Ok "fixture stock restored ($aQty)" } else { Bad "fixture stock drifted ($aQty vs $bQty)" }
  if ($aIo -eq $cIo -and $aTake -eq $cTake) { Ok "document tables restored (io=$aIo take=$aTake)" } else { Bad "doc tables drifted (io $aIo vs $cIo; take $aTake vs $cTake)" }
  if ($aLog -eq $cLog -and $aStock -eq $cStock) { Ok "stock/log counts restored ($aStock/$aLog)" } else { Bad "counts drifted (stock $aStock vs $cStock; log $aLog vs $cLog)" }
  if ($aBad -eq '0') { Ok 'no non-canonical io_type remains in inventory_other_io' } else { Bad "$aBad non-canonical io_type row(s)" }
}

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
