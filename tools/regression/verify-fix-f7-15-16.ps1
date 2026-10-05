# verify-fix-f7-15-16.ps1  (regression for the F7-15 / F7-16 fixes, batch 2)
#
# F7-15 (warehouse-move): item quantity must be > 0 and items must not be empty.
# F7-16 (reclassify):     from/to quality must be a valid ProductQualityType code.
#
# This is the REVERSE of audit-20260919-stock-probe.ps1: the two probes that used to
# succeed must now be rejected, plus positive controls proving the normal flow still works
# (and moves stock in the RIGHT direction), plus a "legacy dirty draft" case that corrupts
# a valid draft via SQL and asserts audit is refused with the status claim rolled back.
# Everything is backed up and restored; final self-check asserts byte-identical state.
# ASCII-only on purpose (PowerShell 5.1 + UTF-8 BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$WH_FROM = 76      # warehouse used as fixture
$WH_TO   = 71      # second warehouse (has the same product)
$PROD    = 61      # fixture product

$env:MYSQL_PWD = 'root'
$script:fails = 0
function Sql([string]$sql) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>&1
  return ($out | Out-String).Trim()
}
function SqlOne([string]$sql) {
  $v = Sql $sql
  if ($v -eq '') { return '' }
  return ($v -split "`n")[0].Trim()
}
function Ok([string]$m)   { Write-Output ("  [OK]   " + $m) }
function Bad([string]$m)  { Write-Output ("  [FAIL] " + $m); $script:fails++ }
function Info([string]$m) { Write-Output ("  [INFO] " + $m) }
function CodeOf($r) { if ($null -eq $r) { return '' } return [string]$r.code }
function MsgOf($r)  { if ($null -eq $r) { return '' } return [string]$r.msg }

# ---------------- login ----------------
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
function MoveBody($qty) {
  return @{ fromWarehouseId = $WH_FROM; toWarehouseId = $WH_TO; moveDate = (Get-Date -Format 'yyyy-MM-dd')
            remark = 'VERIFY-F7-15'; items = @(@{ productId = $PROD; qualityType = 'A'; quantity = $qty }) }
}
function RcBody($from, $to, $qty) {
  return @{ warehouseId = $WH_FROM; reclassifyDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'VERIFY-F7-16'
            items = @(@{ productId = $PROD; fromQuality = $from; toQuality = $to; quantity = $qty }) }
}

# ---------------- pre-clean (idempotent) ----------------
Sql "DELETE l FROM warehouse_stock_log l JOIN inventory_warehouse_move m ON l.related_bill_no=m.code WHERE m.remark='VERIFY-F7-15'" | Out-Null
Sql "DELETE l FROM warehouse_stock_log l JOIN product_reclassify r ON l.related_bill_no=r.code WHERE r.remark='VERIFY-F7-16'" | Out-Null
Sql "DELETE i FROM inventory_warehouse_move_item i JOIN inventory_warehouse_move m ON m.id=i.move_id WHERE m.remark='VERIFY-F7-15'" | Out-Null
Sql "DELETE FROM inventory_warehouse_move WHERE remark='VERIFY-F7-15'" | Out-Null
Sql "DELETE i FROM product_reclassify_item i JOIN product_reclassify r ON r.id=i.reclassify_id WHERE r.remark='VERIFY-F7-16'" | Out-Null
Sql "DELETE FROM product_reclassify WHERE remark='VERIFY-F7-16'" | Out-Null

Write-Output '=== 0) baseline ==='
$bFrom = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(available_quantity,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'"
$bTo   = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(available_quantity,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_TO   AND product_id=$PROD AND quality_type='A'"
# B-grade baseline: E2E runs (ui-e2e-7-inventory etc.) may already have produced B stock for this
# product, so the reclassify control below asserts a DELTA, never an absolute value.
$bB    = SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='B'"
if ($bB -eq '') { $bB = '0' }
$cMove  = SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move'
$cRc    = SqlOne 'SELECT COUNT(*) FROM product_reclassify'
$cStock = SqlOne 'SELECT COUNT(*) FROM warehouse_stock'
$cLog   = SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log'
Info "stock wh$WH_FROM=$bFrom  wh$WH_TO=$bTo  B=$bB   counts move=$cMove reclassify=$cRc stock=$cStock log=$cLog"
if ($bFrom -eq '' -or $bTo -eq '') { Bad 'fixture stock missing -> abort'; exit 1 }
$fBak = [decimal](($bFrom -split '\|')[0]); $tBak = [decimal](($bTo -split '\|')[0])

$mvIds = @(); $rcIds = @()

function NewMoveDraft($qty) {                     # returns id or 0
  $r = Api 'POST' "$BASE/inventory/warehouse-move" (MoveBody $qty)
  if ((CodeOf $r) -ne '200') { return @{ id = 0; code = ''; resp = $r } }
  $id = [int](SqlOne "SELECT id FROM inventory_warehouse_move WHERE remark='VERIFY-F7-15' ORDER BY id DESC LIMIT 1")
  return @{ id = $id; code = (SqlOne "SELECT code FROM inventory_warehouse_move WHERE id=$id"); resp = $r }
}
function NewRcDraft($from, $to, $qty) {
  $r = Api 'POST' "$BASE/inventory/reclassify" (RcBody $from $to $qty)
  if ((CodeOf $r) -ne '200') { return @{ id = 0; code = ''; resp = $r } }
  $id = [int](SqlOne "SELECT id FROM product_reclassify WHERE remark='VERIFY-F7-16' ORDER BY id DESC LIMIT 1")
  return @{ id = $id; code = (SqlOne "SELECT code FROM product_reclassify WHERE id=$id"); resp = $r }
}

try {
  # ============ F7-15 ============
  Write-Output '--- F7-15 a) negative quantity must be rejected at create ---'
  $mvBefore = SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move'
  $d = NewMoveDraft (-2)
  if ((CodeOf $d.resp) -eq '200') { Bad 'negative-quantity move was ACCEPTED (fix not effective)'; $mvIds += $d.id }
  else { Ok ("rejected: code=" + (CodeOf $d.resp) + " msg=" + (MsgOf $d.resp)) }
  $mvAfter = SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move'
  if ($mvBefore -eq $mvAfter) { Ok 'no document created for the rejected request' } else { Bad "document was created anyway ($mvBefore -> $mvAfter)" }

  Write-Output '--- F7-15 b) empty item list must be rejected ---'
  $emptyBody = @{ fromWarehouseId = $WH_FROM; toWarehouseId = $WH_TO; moveDate = (Get-Date -Format 'yyyy-MM-dd')
                  remark = 'VERIFY-F7-15'; items = @() }
  $r = Api 'POST' "$BASE/inventory/warehouse-move" $emptyBody
  if ((CodeOf $r) -eq '200') { Bad 'empty-item move was ACCEPTED'; $mvIds += [int](SqlOne "SELECT id FROM inventory_warehouse_move WHERE remark='VERIFY-F7-15' ORDER BY id DESC LIMIT 1") }
  else { Ok ("rejected: code=" + (CodeOf $r) + " msg=" + (MsgOf $r)) }

  Write-Output '--- F7-15 c) positive control: qty=+2 must work and move the RIGHT way ---'
  $d = NewMoveDraft 2
  if ((CodeOf $d.resp) -ne '200') { Bad ("valid move rejected: " + (MsgOf $d.resp)) } else {
    $mvIds += $d.id
    $ra = Api 'PUT' "$BASE/inventory/warehouse-move/$($d.id)/audit" $null
    $af = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'")
    $at = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_TO   AND product_id=$PROD AND quality_type='A'")
    if ((CodeOf $ra) -eq '200' -and $af -eq ($fBak - 2) -and $at -eq ($tBak + 2)) {
      Ok "audit ok, direction correct (wh$WH_FROM $fBak->$af, wh$WH_TO $tBak->$at)"
    } else { Bad ("positive move wrong: code=" + (CodeOf $ra) + " msg=" + (MsgOf $ra) + " wh$WH_FROM=$af wh$WH_TO=$at") }
    $ru = Api 'PUT' "$BASE/inventory/warehouse-move/$($d.id)/un-audit" $null
    $uf = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'")
    if ((CodeOf $ru) -eq '200' -and $uf -eq $fBak) { Ok 'un-audit restored the source warehouse' } else { Bad "un-audit problem: code=" + (CodeOf $ru) + " wh$WH_FROM=$uf (expect $fBak)" }
  }

  Write-Output '--- F7-15 d) legacy dirty DRAFT (SQL-corrupted to -2) must be refused at audit, claim rolled back ---'
  $d = NewMoveDraft 2
  if ($d.id -le 0) { Bad 'could not create the tidy draft for the legacy case' } else {
    $mvIds += $d.id
    Sql "UPDATE inventory_warehouse_move_item SET quantity=-2 WHERE move_id=$($d.id)" | Out-Null
    $ra = Api 'PUT' "$BASE/inventory/warehouse-move/$($d.id)/audit" $null
    $st = SqlOne "SELECT status FROM inventory_warehouse_move WHERE id=$($d.id)"
    $qf = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'")
    if ((CodeOf $ra) -ne '200' -and $st -eq 'DRAFT' -and $qf -eq $fBak) {
      Ok ("audit refused (code=" + (CodeOf $ra) + "), status stayed DRAFT, no stock touched")
    } else { Bad ("legacy dirty draft handled wrong: code=" + (CodeOf $ra) + " status=$st wh$WH_FROM=$qf (expect $fBak)") }
  }

  # ============ F7-16 ============
  Write-Output '--- F7-16 a) illegal target quality ZZ must be rejected at create ---'
  $rcBefore = SqlOne 'SELECT COUNT(*) FROM product_reclassify'
  $r = NewRcDraft 'A' 'ZZ' 1
  if ((CodeOf $r.resp) -eq '200') { Bad 'illegal toQuality was ACCEPTED'; $rcIds += $r.id }
  else { Ok ("rejected: code=" + (CodeOf $r.resp) + " msg=" + (MsgOf $r.resp)) }
  if ($rcBefore -eq (SqlOne 'SELECT COUNT(*) FROM product_reclassify')) { Ok 'no document created for the rejected request' } else { Bad 'document was created anyway' }

  Write-Output '--- F7-16 b) blank / unknown quality must be rejected ---'
  foreach ($pair in @(@('', 'B'), @('A', ''), @('X', 'Y'))) {
    $r = NewRcDraft ($pair[0]) ($pair[1]) 1
    if ((CodeOf $r.resp) -eq '200') { Bad ("accepted illegal pair ('" + $pair[0] + "' -> '" + $pair[1] + "')"); $rcIds += $r.id }
    else { Ok ("('" + $pair[0] + "' -> '" + $pair[1] + "') rejected: " + (MsgOf $r.resp)) }
  }

  Write-Output '--- F7-16 c) positive control: A -> B qty=1 must work, then un-audit ---'
  $r = NewRcDraft 'A' 'B' 1
  if ((CodeOf $r.resp) -ne '200') { Bad ("valid reclassify rejected: " + (MsgOf $r.resp)) } else {
    $rcIds += $r.id
    $ra = Api 'PUT' "$BASE/inventory/reclassify/$($r.id)/audit" $null
    $qa = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'")
    $qb = SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='B'"
    if ((CodeOf $ra) -eq '200' -and $qa -eq ($fBak - 1) -and [decimal]$qb -eq ([decimal]$bB + 1)) {
      Ok "audit ok (A $fBak->$qa, B $bB->$qb)"
    } else { Bad ("positive reclassify wrong: code=" + (CodeOf $ra) + " msg=" + (MsgOf $ra) + " A=$qa (expect " + ($fBak - 1) + ") B=$qb (expect " + ([decimal]$bB + 1) + ")") }
    $ru = Api 'PUT' "$BASE/inventory/reclassify/$($r.id)/un-audit" $null
    $ua = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'")
    if ((CodeOf $ru) -eq '200' -and $ua -eq $fBak) { Ok 'un-audit restored quality A' } else { Bad ("un-audit problem: code=" + (CodeOf $ru) + " A=$ua (expect $fBak)") }
  }

  Write-Output '--- F7-16 d) legacy dirty DRAFT (SQL-corrupted to ZZ) must be refused at audit ---'
  $r = NewRcDraft 'A' 'B' 1
  if ($r.id -le 0) { Bad 'could not create the tidy draft for the legacy case' } else {
    $rcIds += $r.id
    Sql "UPDATE product_reclassify_item SET to_quality='ZZ' WHERE reclassify_id=$($r.id)" | Out-Null
    $ra = Api 'PUT' "$BASE/inventory/reclassify/$($r.id)/audit" $null
    $st = SqlOne "SELECT status FROM product_reclassify WHERE id=$($r.id)"
    $junk = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='ZZ'"
    $qa = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'")
    if ((CodeOf $ra) -ne '200' -and $st -eq 'DRAFT' -and $junk -eq '0' -and $qa -eq $fBak) {
      Ok ("audit refused (code=" + (CodeOf $ra) + "), status stayed DRAFT, no phantom row, A untouched")
    } else { Bad ("legacy dirty draft handled wrong: code=" + (CodeOf $ra) + " status=$st junk=$junk A=$qa") }
  }

  Write-Output '--- global invariant ---'
  $junkAll = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE quality_type NOT IN ('A','B','C','D','DEFECT','PENDING','GOOD')"
  if ($junkAll -eq '0') { Ok 'no illegal quality row in warehouse_stock' } else { Bad "$junkAll illegal quality row(s)" }
} finally {
  Write-Output '=== cleanup ==='
  foreach ($id in ($mvIds | Select-Object -Unique)) {
    if ($id -gt 0) {
      $code = SqlOne "SELECT code FROM inventory_warehouse_move WHERE id=$id"
      if ($code -ne '') { Sql "DELETE FROM warehouse_stock_log WHERE related_bill_no='$code'" | Out-Null }
      Sql "DELETE FROM inventory_warehouse_move_item WHERE move_id=$id" | Out-Null
      Sql "DELETE FROM inventory_warehouse_move WHERE id=$id" | Out-Null
    }
  }
  foreach ($id in ($rcIds | Select-Object -Unique)) {
    if ($id -gt 0) {
      $code = SqlOne "SELECT code FROM product_reclassify WHERE id=$id"
      if ($code -ne '') { Sql "DELETE FROM warehouse_stock_log WHERE related_bill_no='$code'" | Out-Null }
      Sql "DELETE FROM product_reclassify_item WHERE reclassify_id=$id" | Out-Null
      Sql "DELETE FROM product_reclassify WHERE id=$id" | Out-Null
    }
  }
  # residual zero-quantity rows created by the positive reclassify control
  Sql "DELETE FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quantity=0 AND quality_type IN ('B','C')" | Out-Null
  Sql "DELETE FROM warehouse_stock WHERE product_id=$PROD AND quality_type NOT IN ('A','B','C','D','DEFECT','PENDING','GOOD')" | Out-Null
  $fb = $bFrom -split '\|'; $tb = $bTo -split '\|'
  Sql "UPDATE warehouse_stock SET quantity=$($fb[0]), available_quantity=$($fb[1]) WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'" | Out-Null
  Sql "UPDATE warehouse_stock SET quantity=$($tb[0]), available_quantity=$($tb[1]) WHERE warehouse_id=$WH_TO   AND product_id=$PROD AND quality_type='A'" | Out-Null

  Write-Output '=== 9) post-check (must equal baseline) ==='
  $aFrom = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(available_quantity,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'"
  $aTo   = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(available_quantity,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_TO   AND product_id=$PROD AND quality_type='A'"
  $aMove = SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move'
  $aRc   = SqlOne 'SELECT COUNT(*) FROM product_reclassify'
  $aStock= SqlOne 'SELECT COUNT(*) FROM warehouse_stock'
  $aLog  = SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log'
  if ($aFrom -eq $bFrom -and $aTo -eq $bTo) { Ok "stock restored (wh$WH_FROM=$aFrom, wh$WH_TO=$aTo)" } else { Bad "stock drifted ($aFrom vs $bFrom; $aTo vs $bTo)" }
  if ($aMove -eq $cMove -and $aRc -eq $cRc) { Ok "document tables restored (move=$aMove reclassify=$aRc)" } else { Bad "document tables drifted (move $aMove vs $cMove; rc $aRc vs $cRc)" }
  if ($aStock -eq $cStock -and $aLog -eq $cLog) { Ok "warehouse_stock/log counts restored ($aStock/$aLog)" } else { Bad "counts drifted (stock $aStock vs $cStock; log $aLog vs $cLog)" }
}

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
# F7-288 同类：原先失败时进程退出码仍为 0（调用方看退出码会当通过）=> 与 RESULT 文本保持一致
exit $script:fails
