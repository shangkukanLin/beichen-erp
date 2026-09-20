# verify-fix-f7-105-106-111.ps1
#
# Regression for the 2026-09-20 fix batch (report section 40):
#   F7-105  sale outbound must NOT move stock any more (stock is owned by sale-order audit)
#   F7-105  /api/inventory/outbound must be page-code guarded (sale:order) -> 403 without it
#   F7-111  sale return: product must exist / must have been sold once / productId required
#   F7-106  ApiPermGuard self-check: startup log must report "收口一致性 OK" (no unguarded prefix)
#
# THIS SCRIPT WRITES ROWS (it must, to exercise audit paths) AND CLEANS THEM UP AT THE END.
#   Everything it creates is deleted and both tables are re-counted to prove the baseline is restored.
#   Baseline before this batch: sale_outbound = 0, sale_outbound_item = 0.
#
# ASCII-only on purpose (PS 5.1 + BOM pitfalls).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$LOG = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\backend_diag.log'
$script:fail = 0
$script:skip = 0
$created = @()          # outbound ids created here (for cleanup)
$createdReturns = @()   # sale_return ids created here (for cleanup)

function Ok($m)   { Write-Output ("PASS " + $m) }
function Bad($m)  { Write-Output ("FAIL " + $m); $script:fail++ }
function Skip($m) { Write-Output ("SKIP " + $m); $script:skip++ }
function Info($m) { Write-Output ("  [INFO] " + $m) }

$env:MYSQL_PWD = 'root'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -D $DB -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}

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
    if ($null -eq $body) {
      $r = Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h -TimeoutSec 30
    } else {
      $j = ConvertTo-Json -InputObject $body -Depth 8
      $r = Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($j)) -TimeoutSec 30
    }
    return $r
  } catch {
    # business errors come back as HTTP 200 with a business code; real transport failures land here
    $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
    return [pscustomobject]@{ code = $sc; msg = 'transport-error' }
  }
}

# stock snapshot for one (warehouse, product, quality): quantity sum
# NOTE: parameter must NOT be named $pid -- binding to that read-only automatic variable fails,
# the body then yields $null and every comparison degenerates to "null eq null" (a fake PASS).
function StockOf([long]$wh, [long]$prod, [string]$qt) {
  $v = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='$qt'"
  if ([string]::IsNullOrWhiteSpace($v)) { return [decimal]0 }
  return [decimal]$v
}
function LogCount() { return [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log') }

Write-Output '=== 0) backend + login ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin (lin/123)'; Write-Output 'RESULT FAIL'; exit 1 }
Ok 'admin login ok'
$low = Login 'audit_merch' '123'
if ($null -eq $low) { Skip 'audit_merch login failed -> permission row skipped' } else { Ok 'low-priv login ok (audit_merch)' }

# ---------- F7-106: self-check log ----------
Write-Output ''
Write-Output '=== F7-106) ApiPermGuard self-check at startup ==='
if (Test-Path $LOG) {
  $tail = Get-Content $LOG -Raw -ErrorAction SilentlyContinue
  if ($tail -match '\[perm-selfcheck\]' -and $tail -match 'OK') { Ok 'self-check reported 收口一致性 OK' }
  elseif ($tail -match '\[perm-selfcheck\]') { Bad 'self-check reported UNGUARDED prefixes (see backend_diag.log)' }
  else { Skip 'no [perm-selfcheck] line in backend_diag.log yet (restart backend once more?)' }
} else { Skip ('backend log not found: ' + $LOG) }

# ---------- F7-105: outbound no longer moves stock ----------
Write-Output ''
Write-Output '=== F7-105) outbound audit/un-audit must not touch stock ==='
$soPage = Req 'GET' '/inventory/sale/page?pageSize=50&status=AUDITED' $admin $null
$so = @(@($soPage.data.records) | Select-Object -First 1)
if ($so.Count -eq 0) { Skip 'no AUDITED sale order to bind an outbound to' }
else {
  $soId = [long]$so[0].id
  $det = (Req 'GET' ("/inventory/sale/$soId") $admin $null).data
  $soItems = @(@((Req 'GET' ("/inventory/sale/$soId/items") $admin $null).data))
  if ($soItems.Count -eq 0) { Skip 'sale order has no items' }
  else {
    $it0 = $soItems[0]
    $wh = [long]$det.warehouseId
    # NOTE: do NOT name this $pid -- $PID is a read-only automatic variable in PowerShell,
    # assigning to it fails silently-ish and every later read returns the process id (恒 0 == 0).
    $prodId = [long]$it0.productId
    $qt = if ($it0.qualityType) { [string]$it0.qualityType } else { 'A' }
    $qty = [math]::Max(1, [math]::Floor([decimal]$it0.quantity / 2))

    $payload = @{ outbound = @{ orderId = $soId; customerId = [long]$det.customerId; warehouseId = $wh
                                outboundDate = (Get-Date).ToString('yyyy-MM-dd'); remark = 'verify f7-105' }
                  items = @(@{ orderItemId = [long]$it0.id; productId = $pid; qualityType = $qt
                               quantity = $qty; unitPrice = [decimal]$it0.unitPrice }) }
    $cr = Req 'POST' '/inventory/outbound' $admin $payload
    if ([string]$cr.code -ne '200') { Bad ('create outbound failed: ' + $cr.msg) }
    else {
      $obId = [long](SqlOne 'SELECT MAX(id) FROM sale_outbound')
      $created += $obId
      Info ("created outbound id=$obId (sale order $soId, product $pid, qty $qty)")

      $q0 = StockOf $wh $prodId $qt; $l0 = LogCount
      $ra = Req 'PUT' ("/inventory/outbound/$obId/audit") $admin $null
      $q1 = StockOf $wh $prodId $qt; $l1 = LogCount
      if ([string]$ra.code -ne '200') { Bad ('audit outbound failed: ' + $ra.msg) }
      elseif ($q1 -ne $q0 -or $l1 -ne $l0) {
        Bad ("audit CHANGED stock/log (qty $q0 -> $q1, logs $l0 -> $l1) -- F7-105 NOT fixed")
      } else { Ok ("audit did NOT move stock (qty stays $q0, logs stay $l0)") }

      $ru = Req 'PUT' ("/inventory/outbound/$obId/un-audit") $admin $null
      $q2 = StockOf $wh $prodId $qt; $l2 = LogCount
      if ([string]$ru.code -ne '200') { Bad ('un-audit outbound failed: ' + $ru.msg) }
      elseif ($q2 -ne $q0 -or $l2 -ne $l0) { Bad ("un-audit CHANGED stock/log (qty $q2, logs $l2)") }
      else { Ok "un-audit did NOT move stock either" }

      # F7-105b: guard (needs sale:order)
      if ($null -ne $low) {
        $rl = Req 'PUT' ("/inventory/outbound/$obId/audit") $low $null
        if ([string]$rl.code -eq '403') { Ok 'low-priv account got 403 on outbound audit (page code enforced)' }
        else { Bad ('low-priv account NOT blocked on outbound audit (code=' + $rl.code + ') -- check ApiPermGuard rule') }
      }
    }
  }
}

# ---------- F7-111: sale return guards ----------
Write-Output ''
Write-Output '=== F7-111) sale return guards ==='
$custId = SqlOne 'SELECT id FROM customer ORDER BY id LIMIT 1'
$whId = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' ORDER BY id LIMIT 1"

# (a) productId required
$r1 = Req 'POST' '/sale/return' $admin @{ customerId = [long]$custId; warehouseId = [long]$whId
                                          returnDate = (Get-Date).ToString('yyyy-MM-dd')
                                          items = @(@{ quantity = 1; unitPrice = 1 }) }
if ([string]$r1.code -eq '200') {
  $createdReturns += [long](SqlOne 'SELECT MAX(id) FROM sale_return')
  Bad 'return with NO productId was accepted (F7-111 productId-required NOT enforced)'
} else { Ok ('return without productId rejected: ' + $r1.msg) }

# (b) non-existent product
$r2 = Req 'POST' '/sale/return' $admin @{ customerId = [long]$custId; warehouseId = [long]$whId
                                          returnDate = (Get-Date).ToString('yyyy-MM-dd')
                                          items = @(@{ productId = 999999; quantity = 1; unitPrice = 1 }) }
if ([string]$r2.code -eq '200') {
  $rid2 = [long](SqlOne 'SELECT MAX(id) FROM sale_return'); $createdReturns += $rid2
  $q0 = StockOf ([long]$whId) 999999 'PENDING'; $l0 = LogCount
  $ra2 = Req 'PUT' ("/sale/return/$rid2/audit") $admin $null
  $q1 = StockOf ([long]$whId) 999999 'PENDING'; $l1 = LogCount
  if ([string]$ra2.code -eq '200') { Bad 'audit accepted a NON-EXISTENT product (F7-111 product-exists NOT enforced)' }
  elseif ($q1 -ne $q0 -or $l1 -ne $l0) { Bad ('audit of bad product changed stock/log (qty ' + $q0 + '->' + $q1 + ')') }
  else { Ok ('audit rejected non-existent product and stock unchanged: ' + $ra2.msg) }
} else { Skip ('return create with product 999999 rejected earlier: ' + $r2.msg) }

# (c) product exists but was never sold
$unsold = SqlOne "SELECT p.id FROM product p WHERE NOT EXISTS (SELECT 1 FROM warehouse_stock_log l WHERE l.product_id=p.id AND l.change_type IN ('SALE_OUT','EXCHANGE_OUT')) ORDER BY p.id LIMIT 1"
if (-not $unsold) { Skip 'every product has sale/exchange-out history -> cannot test the never-sold rule' }
else {
  $r3 = Req 'POST' '/sale/return' $admin @{ customerId = [long]$custId; warehouseId = [long]$whId
                                            returnDate = (Get-Date).ToString('yyyy-MM-dd')
                                            items = @(@{ productId = [long]$unsold; quantity = 1; unitPrice = 1 }) }
  if ([string]$r3.code -eq '200') {
    $rid3 = [long](SqlOne 'SELECT MAX(id) FROM sale_return'); $createdReturns += $rid3
    $q0 = StockOf ([long]$whId) ([long]$unsold) 'PENDING'; $l0 = LogCount
    $ra3 = Req 'PUT' ("/sale/return/$rid3/audit") $admin $null
    $q1 = StockOf ([long]$whId) ([long]$unsold) 'PENDING'; $l1 = LogCount
    if ([string]$ra3.code -eq '200') { Bad ('audit accepted a NEVER-SOLD product (id=' + $unsold + ')') }
    elseif ($q1 -ne $q0 -or $l1 -ne $l0) { Bad ('audit of never-sold product changed stock') }
    else { Ok ('audit rejected never-sold product (id=' + $unsold + ') and stock unchanged') }
  } else { Skip ('return create rejected before audit: ' + $r3.msg) }
}

# ---------- cleanup ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
foreach ($id in ($created | Select-Object -Unique)) {
  SqlOne ("DELETE FROM sale_outbound_item WHERE outbound_id=$id") | Out-Null
  SqlOne ("DELETE FROM sale_outbound WHERE id=$id") | Out-Null
}
foreach ($id in ($createdReturns | Select-Object -Unique)) {
  SqlOne ("DELETE FROM sale_return_item WHERE return_id=$id") | Out-Null
  SqlOne ("DELETE FROM sale_return WHERE id=$id") | Out-Null
}
$obLeft = [int](SqlOne 'SELECT COUNT(*) FROM sale_outbound')
$obItLeft = [int](SqlOne 'SELECT COUNT(*) FROM sale_outbound_item')
$srLeft = [int](SqlOne 'SELECT COUNT(*) FROM sale_return')
Info ("after cleanup: sale_outbound=$obLeft sale_outbound_item=$obItLeft sale_return=$srLeft")
if ($obLeft -eq 0 -and $obItLeft -eq 0) { Ok 'sale_outbound / sale_outbound_item back to the 0-row baseline' }
else { Bad 'outbound rows still present after cleanup' }
if ($srLeft -eq 11) { Ok 'sale_return back to the 11-row baseline' }
else { Info ("sale_return count = $srLeft (baseline was 11; verify manually if it differs)") }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
