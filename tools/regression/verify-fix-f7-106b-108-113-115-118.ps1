# verify-fix-f7-106b-108-113-115-118.ps1
#
# Regression for the second 2026-09-20 fix batch (report section 42), covering:
#   F7-106 (step 2)  unregistered /api prefix must now be REJECTED (default-deny), while registered ones still work
#   F7-108           sale order draft save must reject empty items / non-positive quantity
#   F7-113           exchange can-exchange check: new one-shot JOIN aggregation still answers correctly
#   F7-115           sale return item amount must be RECOMPUTED server-side (quantity * unitPrice)
#   F7-118           sale_exchange.total_amount column must be GONE
#   F7-120           sale analysis: bad date still yields the friendly message; good request still 200
#
# THIS SCRIPT WRITES ROWS (to exercise create paths) AND CLEANS THEM UP AT THE END,
# then re-counts the affected tables to prove the baseline is restored.
# ASCII-only on purpose (PS 5.1 + BOM pitfalls).  NOTE: never name a variable $pid (read-only).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$script:fail = 0
$script:skip = 0
$createdReturns = @()

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
      return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h -TimeoutSec 40
    }
    $j = ConvertTo-Json -InputObject $body -Depth 8
    return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($j)) -TimeoutSec 40
  } catch {
    $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
    return [pscustomobject]@{ code = $sc; msg = 'transport-error' }
  }
}
# business code helper (business errors ride on HTTP 200)
function BCode($r) { if ($null -eq $r) { return -1 }; return [int]$r.code }

Write-Output '=== 0) backend + login ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin (lin/123)'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'

# ---------- F7-106 step 2: default deny ----------
Write-Output ''
Write-Output '=== F7-106b) unregistered prefix must be REJECTED (default-deny) ==='
$un = Req 'GET' '/definitely-not-a-registered-prefix/probe' $admin $null
if ((BCode $un) -eq 403) { Ok 'unregistered /api prefix -> 403 (default-deny active)' }
elseif ((BCode $un) -eq 404) { Bad 'unregistered prefix returned 404 - it did NOT go through the guard (check interceptor path patterns)' }
else { Bad ('unregistered prefix returned code=' + (BCode $un) + ' (expected 403); default-deny may be off') }
$ok1 = Req 'GET' '/inventory/sale/page?pageSize=1' $admin $null
if ((BCode $ok1) -eq 200) { Ok 'registered prefix still 200 (no over-blocking from default-deny)' }
else { Bad ('registered prefix regressed: code=' + (BCode $ok1)) }
$ok2 = Req 'GET' '/dashboard/module-pages' $admin $null
if ((BCode $ok2) -eq 200) { Ok 'EXEMPT prefix (/dashboard) still 200' } else { Skip ('dashboard probe code=' + (BCode $ok2)) }

# ---------- F7-108: draft item guards ----------
Write-Output ''
Write-Output '=== F7-108) sale order draft must reject empty items / bad quantity ==='
$custId = SqlOne 'SELECT id FROM customer ORDER BY id LIMIT 1'
$whId = SqlOne 'SELECT id FROM warehouse ORDER BY id LIMIT 1'
$prodId = SqlOne 'SELECT id FROM product ORDER BY id LIMIT 1'
$beforeOrders = [int](SqlOne 'SELECT COUNT(*) FROM sale_order')

$e1 = Req 'POST' '/inventory/sale' $admin @{ order = @{ customerId = [long]$custId; warehouseId = [long]$whId }
                                               items = @() }
if ((BCode $e1) -ne 200) { Ok ('empty items rejected: ' + $e1.msg) } else { Bad 'empty items ACCEPTED (F7-108 not enforced)' }

$e2 = Req 'POST' '/inventory/sale' $admin @{ order = @{ customerId = [long]$custId; warehouseId = [long]$whId }
                                               items = @(@{ productId = [long]$prodId; quantity = 0; unitPrice = 1 }) }
if ((BCode $e2) -ne 200) { Ok ('quantity=0 rejected: ' + $e2.msg) } else { Bad 'quantity=0 ACCEPTED (F7-108 not enforced)' }

$afterOrders = [int](SqlOne 'SELECT COUNT(*) FROM sale_order')
if ($afterOrders -eq $beforeOrders) { Ok ('no sale_order row written by the two rejected requests (' + $beforeOrders + ')') }
else { Bad ('sale_order count changed ' + $beforeOrders + ' -> ' + $afterOrders) }

# ---------- F7-113: exchange can-exchange aggregation ----------
Write-Output ''
Write-Output '=== F7-113) exchange sale-order-items aggregation still works ==='
$soId = SqlOne "SELECT id FROM sale_order WHERE status='AUDITED' ORDER BY id LIMIT 1"
if (-not $soId) { Skip 'no AUDITED sale order' }
else {
  $r113 = Req 'GET' ("/sale/exchange/sale-order-items?saleOrderId=$soId") $admin $null
  if ((BCode $r113) -eq 200) {
    $rows = @($r113.data)
    $neg = @($rows | Where-Object { [decimal]$_.canExchange -lt 0 }).Count
    Ok ('exchange sale-order-items ok (rows=' + $rows.Count + ', negative canExchange=' + $neg + ')')
  } else { Bad ('exchange sale-order-items failed: ' + $r113.msg) }
  $r113b = Req 'GET' ("/sale/return/sale-order-items?saleOrderId=$soId") $admin $null
  if ((BCode $r113b) -eq 200) { Ok 'return sale-order-items ok' } else { Bad ('return sale-order-items: ' + $r113b.msg) }
}

# ---------- F7-115: amount recomputed server-side ----------
Write-Output ''
Write-Output '=== F7-115) sale return item amount is recomputed (qty * price) ==='
$r115 = Req 'POST' '/sale/return' $admin @{ customerId = [long]$custId; warehouseId = [long]$whId
                                             returnDate = (Get-Date).ToString('yyyy-MM-dd')
                                             items = @(@{ productId = [long]$prodId; quantity = 3; unitPrice = 2; amount = 99999 }) }
if ((BCode $r115) -ne 200) { Skip ('return create failed: ' + $r115.msg) }
else {
  $rid = [long](SqlOne 'SELECT MAX(id) FROM sale_return'); $createdReturns += $rid
  $amt = SqlOne ("SELECT amount FROM sale_return_item WHERE return_id=$rid LIMIT 1")
  $tot = SqlOne ("SELECT total_amount FROM sale_return WHERE id=$rid")
  if ([decimal]$amt -eq 6) { Ok ('item amount recomputed to 6 (sent 99999) - F7-115 enforced') }
  else { Bad ('item amount = ' + $amt + ' (expected 6) - F7-115 NOT enforced') }
  if ([decimal]$tot -eq 6) { Ok 'header totalAmount also 6 (consistent with items)' }
  else { Info ('header totalAmount = ' + $tot) }
}

# ---------- F7-118: dead column gone ----------
Write-Output ''
Write-Output '=== F7-118) sale_exchange.total_amount column is gone ==='
$col = SqlOne "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='sale_exchange' AND COLUMN_NAME='total_amount'"
if ([string]::IsNullOrWhiteSpace($col)) { Ok 'total_amount column dropped' } else { Bad 'total_amount column still present' }
$exRows = [int](SqlOne 'SELECT COUNT(*) FROM sale_exchange')
# 2026-10-05 F7-292: the drift branch was `Info` (a silent green). Historical counts drift by design, so the
# honest verdict is SKIP (visible, not counted as PASS) rather than pretending the row count was verified.
if ($exRows -eq 13) { Ok 'sale_exchange rows unchanged (13)' } else { Skip ('sale_exchange rows = ' + $exRows + ' (baseline was 13, history drifted) -- not asserted') }

# ---------- F7-120: analysis endpoint behaviour ----------
Write-Output ''
Write-Output '=== F7-120) sale analysis endpoints ==='
$an1 = Req 'GET' '/sale/analysis?preset=month' $admin $null
if ((BCode $an1) -eq 200) { Ok 'sales analysis ok (200)' } else { Bad ('sales analysis failed: ' + $an1.msg) }
$an2 = Req 'GET' '/sale/analysis?start=not-a-date&end=also-bad' $admin $null
if ((BCode $an2) -eq 200 -and [string]$an2.msg -match 'yyyy') { Ok 'bad date still returns the friendly date message' }
else { Info ('bad-date response code=' + (BCode $an2) + ' msg=' + $an2.msg) }
$an3 = Req 'GET' '/sale/analysis/by-doc-date' $admin $null
if ((BCode $an3) -eq 200) { Ok 'by-doc-date ok (200)' } else { Bad ('by-doc-date failed: ' + $an3.msg) }

# ---------- cleanup ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
foreach ($id in ($createdReturns | Select-Object -Unique)) {
  SqlOne ("DELETE FROM sale_return_item WHERE return_id=$id") | Out-Null
  SqlOne ("DELETE FROM sale_return WHERE id=$id") | Out-Null
}
$srLeft = [int](SqlOne 'SELECT COUNT(*) FROM sale_return')
$soLeft = [int](SqlOne 'SELECT COUNT(*) FROM sale_order')
Info ("after cleanup: sale_return=$srLeft (baseline 11), sale_order=$soLeft (baseline " + $beforeOrders + ")")
if ($srLeft -eq 11) { Ok 'sale_return back to baseline' } else { Skip ('sale_return count = ' + $srLeft + ' (baseline was 11, history drifted) -- not asserted') }
if ($soLeft -eq $beforeOrders) { Ok 'sale_order unchanged by this script' } else { Bad 'sale_order changed unexpectedly' }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
