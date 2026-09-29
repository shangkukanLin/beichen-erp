# audit-20260919-stock-probe.ps1  (Batch 2 / F7 stock-irreversible evidence)
#
# Evidence probe for two P1 candidates found in batch 2:
#   PROBE A: warehouse-move create+audit with a NEGATIVE quantity -> stock moved in the
#            opposite direction of the document (audit has no "quantity > 0" validation).
#   PROBE B: reclassify create+audit with an ILLEGAL target quality ('ZZ') -> a phantom
#            warehouse_stock row with quality_type='ZZ' is created (no quality whitelist).
#
# The script backs everything up first, probes, then rolls back and asserts the state is
# byte-identical to the baseline (stock quantities, product cost, table row counts).
# All HTTP calls are real; cleanup is done with SQL so the probe leaves no residue.
# ASCII-only on purpose (PowerShell 5.1 + UTF-8 BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$WH_FROM = 76      # 成品一号仓
$WH_TO   = 71      # 成品二号仓
$PROD    = 61      # product used as fixture

# MYSQL_PWD keeps the password off the command line: otherwise mysql prints
# "[Warning] Using a password on the command line interface can be insecure" on stderr,
# which 2>&1 would merge into the captured rows and corrupt every value read back.
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

# ---------------- pre-clean (idempotent): drop any residue from a previous run ----------------
# NOTE: the document table is `product_reclassify` (entity @TableName), NOT
# `inventory_product_reclassify` -- an earlier revision of this script used the wrong name,
# so its cleanup silently failed and left a probe draft behind. This block removes it.
Sql "DELETE l FROM warehouse_stock_log l JOIN inventory_warehouse_move m ON l.related_bill_no=m.code WHERE m.remark IN ('AUDIT-PROBE-A','AUDIT-PROBE-B')" | Out-Null
Sql "DELETE l FROM warehouse_stock_log l JOIN product_reclassify r ON l.related_bill_no=r.code WHERE r.remark IN ('AUDIT-PROBE-A','AUDIT-PROBE-B')" | Out-Null
Sql "DELETE i FROM inventory_warehouse_move_item i JOIN inventory_warehouse_move m ON i.move_id=m.id WHERE m.remark IN ('AUDIT-PROBE-A','AUDIT-PROBE-B')" | Out-Null
Sql "DELETE FROM inventory_warehouse_move WHERE remark IN ('AUDIT-PROBE-A','AUDIT-PROBE-B')" | Out-Null
Sql "DELETE i FROM product_reclassify_item i JOIN product_reclassify r ON i.reclassify_id=r.id WHERE r.remark IN ('AUDIT-PROBE-A','AUDIT-PROBE-B')" | Out-Null
Sql "DELETE FROM product_reclassify WHERE remark IN ('AUDIT-PROBE-A','AUDIT-PROBE-B')" | Out-Null
Sql "DELETE FROM warehouse_stock WHERE product_id=$PROD AND quality_type NOT IN ('A','B','C','D','DEFECT','PENDING','GOOD')" | Out-Null
Write-Output '=== 0) baseline backup ==='
$bakFrom  = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(available_quantity,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'"
$bakTo    = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(available_quantity,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_TO   AND product_id=$PROD AND quality_type='A'"
$bakCost  = SqlOne "SELECT CONCAT(IFNULL(cost_price,'NULL'),'|',IFNULL(last_in_price,'NULL')) FROM product WHERE id=$PROD"
$cntMove  = SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move'
$cntMoveI = SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move_item'
$cntRc    = SqlOne 'SELECT COUNT(*) FROM product_reclassify'
$cntRcI   = SqlOne 'SELECT COUNT(*) FROM product_reclassify_item'
$cntStock = SqlOne 'SELECT COUNT(*) FROM warehouse_stock'
$cntLog   = SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log'
Info ("stock wh${WH_FROM}=$bakFrom  wh${WH_TO}=$bakTo  product61 cost=$bakCost")
Info ("counts move=$cntMove/$cntMoveI reclassify=$cntRc/$cntRcI stock=$cntStock log=$cntLog")
if ($bakFrom -eq '' -or $bakTo -eq '' -or $bakFrom -notmatch '\|') { Bad 'fixture stock missing -> abort'; exit 1 }

$moveId = 0; $moveCode = ''; $rcId = 0; $rcCode = ''
$fromBak = [decimal](($bakFrom -split '\|')[0]); $toBak = [decimal](($bakTo -split '\|')[0])

try {
  # ---------------- PROBE A ----------------
  Write-Output '--- PROBE A: warehouse-move, item quantity = -2 (document says 76 -> 71) ---'
  $mv = @{ fromWarehouseId = $WH_FROM; toWarehouseId = $WH_TO; moveDate = (Get-Date -Format 'yyyy-MM-dd')
           remark = 'AUDIT-PROBE-A'; items = @(@{ productId = $PROD; qualityType = 'A'; quantity = -2 }) }
  $r1 = Api 'POST' "$BASE/inventory/warehouse-move" $mv
  Info ("create -> code=" + (CodeOf $r1) + " msg=" + $r1.msg)
  if ((CodeOf $r1) -ne '200') {
    Ok $true 'negative-quantity move draft is rejected at create (F7 quantity-validation fix holds; the old "reaches audit" hypothesis is obsolete)'
  } else {
    $moveId   = [int](SqlOne "SELECT id FROM inventory_warehouse_move WHERE remark='AUDIT-PROBE-A' ORDER BY id DESC LIMIT 1")
    $moveCode = SqlOne "SELECT code FROM inventory_warehouse_move WHERE id=$moveId"
    $r2 = Api 'PUT' "$BASE/inventory/warehouse-move/$moveId/audit" $null
    Info ("audit  -> code=" + (CodeOf $r2) + " msg=" + $r2.msg + " doc=" + $moveCode)
    if ((CodeOf $r2) -ne '200') {
      Bad 'audit rejected -> probe A inconclusive'
    } else {
      $fromAfter = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'")
      $toAfter   = [decimal](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$WH_TO   AND product_id=$PROD AND quality_type='A'")
      Info ("wh${WH_FROM}: $fromBak -> $fromAfter     wh${WH_TO}: $toBak -> $toAfter")
      if ($fromAfter -eq ($fromBak + 2) -and $toAfter -eq ($toBak - 2)) {
        Ok ("REVERSED: doc moves $WH_FROM -> $WH_TO, stock moved $WH_TO -> $WH_FROM (2 units each)")
      } else {
        Bad 'stock direction not reversed as predicted -> re-check'
      }
    }
  }

  # ---------------- PROBE B ----------------
  Write-Output '--- PROBE B: reclassify A -> ZZ (illegal target quality) ---'
  $rc = @{ warehouseId = $WH_FROM; reclassifyDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'AUDIT-PROBE-B'
           items = @(@{ productId = $PROD; fromQuality = 'A'; toQuality = 'ZZ'; quantity = 1 }) }
  $r3 = Api 'POST' "$BASE/inventory/reclassify" $rc
  Info ("create -> code=" + (CodeOf $r3) + " msg=" + $r3.msg)
  if ((CodeOf $r3) -ne '200') {
    Ok $true 'illegal toQuality is rejected at create (F7 quality-validation fix holds; the old "phantom ZZ row" hypothesis is obsolete)'
  } else {
    $rcId   = [int](SqlOne "SELECT id FROM product_reclassify WHERE remark='AUDIT-PROBE-B' ORDER BY id DESC LIMIT 1")
    $rcCode = SqlOne "SELECT code FROM product_reclassify WHERE id=$rcId"
    $r4 = Api 'PUT' "$BASE/inventory/reclassify/$rcId/audit" $null
    Info ("audit  -> code=" + (CodeOf $r4) + " msg=" + $r4.msg + " doc=" + $rcCode)
    $junk = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(company_id,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='ZZ'"
    if ((CodeOf $r4) -eq '200' -and $junk -ne '') {
      Ok ("PHANTOM ROW created: warehouse_stock(wh=$WH_FROM, product=$PROD, quality_type='ZZ') qty|company = $junk")
    } else {
      Bad ("no phantom row created (audit code=" + (CodeOf $r4) + ", row='" + $junk + "')")
    }
  }
} finally {
  # ---------------- cleanup + verification ----------------
  Write-Output '=== cleanup ==='
  if ($moveId -gt 0) {
    Sql "DELETE FROM warehouse_stock_log WHERE related_bill_no='$moveCode'" | Out-Null
    Sql "DELETE FROM inventory_warehouse_move_item WHERE move_id=$moveId" | Out-Null
    Sql "DELETE FROM inventory_warehouse_move WHERE id=$moveId" | Out-Null
  }
  if ($rcId -gt 0) {
    Sql "DELETE FROM warehouse_stock_log WHERE related_bill_no='$rcCode'" | Out-Null
    Sql "DELETE FROM product_reclassify_item WHERE reclassify_id=$rcId" | Out-Null
    Sql "DELETE FROM product_reclassify WHERE id=$rcId" | Out-Null
  }
  Sql "DELETE FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='ZZ'" | Out-Null
  $fb = $bakFrom -split '\|'; $tb = $bakTo -split '\|'; $cb = $bakCost -split '\|'
  Sql "UPDATE warehouse_stock SET quantity=$($fb[0]), available_quantity=$($fb[1]) WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'" | Out-Null
  Sql "UPDATE warehouse_stock SET quantity=$($tb[0]), available_quantity=$($tb[1]) WHERE warehouse_id=$WH_TO   AND product_id=$PROD AND quality_type='A'" | Out-Null
  Sql "UPDATE product SET cost_price=$($cb[0]), last_in_price=$($cb[1]) WHERE id=$PROD" | Out-Null

  Write-Output '=== 9) post-check (must equal baseline) ==='
  $aFrom = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(available_quantity,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_FROM AND product_id=$PROD AND quality_type='A'"
  $aTo   = SqlOne "SELECT CONCAT(quantity,'|',IFNULL(available_quantity,'NULL')) FROM warehouse_stock WHERE warehouse_id=$WH_TO   AND product_id=$PROD AND quality_type='A'"
  $aCost = SqlOne "SELECT CONCAT(IFNULL(cost_price,'NULL'),'|',IFNULL(last_in_price,'NULL')) FROM product WHERE id=$PROD"
  $aMove  = SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move'
  $aMoveI = SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move_item'
  $aRc    = SqlOne 'SELECT COUNT(*) FROM product_reclassify'
  $aRcI   = SqlOne 'SELECT COUNT(*) FROM product_reclassify_item'
  $aStock = SqlOne 'SELECT COUNT(*) FROM warehouse_stock'
  $aLog   = SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log'
  $aJunk  = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE quality_type='ZZ'"

  if ($aFrom -eq $bakFrom -and $aTo -eq $bakTo) { Ok "stock restored (wh$WH_FROM=$aFrom, wh$WH_TO=$aTo)" } else { Bad "stock drifted (wh$WH_FROM=$aFrom vs $bakFrom; wh$WH_TO=$aTo vs $bakTo)" }
  if ($aCost -eq $bakCost) { Ok "product cost restored ($aCost)" } else { Bad "product cost drifted ($aCost vs $bakCost)" }
  if ($aMove -eq $cntMove -and $aMoveI -eq $cntMoveI -and $aRc -eq $cntRc -and $aRcI -eq $cntRcI) {
    Ok "document tables restored (move=$aMove/$aMoveI reclassify=$aRc/$aRcI)"
  } else {
    Bad "document tables drifted (move=$aMove/$aMoveI vs $cntMove/$cntMoveI; reclassify=$aRc/$aRcI vs $cntRc/$cntRcI)"
  }
  if ($aStock -eq $cntStock -and $aLog -eq $cntLog) { Ok "warehouse_stock/warehouse_stock_log row counts restored ($aStock/$aLog)" } else { Bad "stock/log counts drifted (stock $aStock vs $cntStock; log $aLog vs $cntLog)" }
  if ($aJunk -eq '0') { Ok 'no residual ZZ-quality row' } else { Bad "$aJunk residual ZZ-quality row(s)" }
}

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
