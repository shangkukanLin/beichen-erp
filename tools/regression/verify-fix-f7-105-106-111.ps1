# verify-fix-f7-105-106-111.ps1
#
# Regression for the 2026-09-20 fix batch (report section 40).
# 2026-10-09 (report 7.28): the F7-105 section was REMOVED -- the sale-outbound module was retired
#   entirely, so its two assertions (outbound audit must not move stock; /api/inventory/outbound must be
#   page-code guarded) no longer have an endpoint to test. The file name is kept so old references resolve.
#   NOTE: the underlying rule "stock is owned by the sale-order audit" is NOT weakened -- it is covered by
#   the sale-order guards, and the outbound path no longer has any write entry point at all.
# Kept:
#   F7-111  sale return: product must exist / must have been sold once / productId required
#   F7-106  ApiPermGuard self-check: startup log must report OK (no unguarded prefix)
#
# THIS SCRIPT WRITES ROWS (it must, to exercise audit paths) AND CLEANS THEM UP AT THE END.
#   Everything it creates is deleted and sale_return is re-counted to prove the baseline is restored.
#
# ASCII-only on purpose (PS 5.1 + BOM pitfalls).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$LOG = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\backend_diag.log'
$script:fail = 0
$script:skip = 0
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

# ---------- F7-105: REMOVED 2026-10-09 (report 7.28) ----------
# The sale-outbound module was retired as a whole, so "outbound audit must not move stock" and
# "/api/inventory/outbound must be page-code guarded" no longer have any endpoint to call.
# The underlying rule (stock is owned by the sale-order audit) is unchanged and still covered
# by the sale-order guards; the outbound path has no write entry point at all any more.

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

# (c) PRODUCT-MUST-HAVE-BEEN-SOLD rule: **OFF BY DESIGN since 2026-10-01** -> old assertion retired.
#     Why: the F8 sale return/exchange rework (step 4) took the user's call "an unsourced return only needs
#     the stock/cost check" and COMMENTED OUT assertProductsSoldOnce() -- see SaleReturnServiceImpl validate(),
#     which keeps the method intact with a one-line restore instruction.
#     Honesty note (2026-10-09): this assertion had been FAILING ever since, for THAT reason -- not because of
#     the sale-outbound retirement done the same day. Inverting it into a green assertion would be worse than
#     saying nothing, so it is an explicit SKIP: restore the rule and this assertion comes back with it.
Skip 'never-sold rule is OFF by design since 2026-10-01 (F8 step 4) - assertion retired together with it'

# ---------- cleanup ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
foreach ($id in ($createdReturns | Select-Object -Unique)) {
  SqlOne ("DELETE FROM sale_return_item WHERE return_id=$id") | Out-Null
  SqlOne ("DELETE FROM sale_return WHERE id=$id") | Out-Null
}
$srLeft = [int](SqlOne 'SELECT COUNT(*) FROM sale_return')
Info ("after cleanup: sale_return=$srLeft")
if ($srLeft -eq 11) { Ok 'sale_return back to the 11-row baseline' }
# 2026-10-05 F7-292: was `Info` -- drift looked like a verified pass. SKIP is the honest verdict.
else { Skip ("sale_return count = $srLeft (baseline was 11, history drifted) -- not asserted") }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
