# audit-20260919-saleoutbound-reach.ps1   (batch 8a / F7-105 evidence)
#
# Goal: check whether `/api/inventory/outbound` -- which is NOT registered in any of the three
#       ApiPermGuard tables (EXEMPT / RULES / WRITE_RULES) -- can be called by an account that does
#       NOT hold the matching page code.
#
# Why it matters: SaleOutboundServiceImpl.audit() deducts stock as well, while SaleOrderServiceImpl.audit()
#       already deducts it (SALE_OUT / SALE_ORDER). If the outbound endpoint is reachable, the same goods
#       can be deducted twice (two stock logs: SALE_ORDER + SALE_OUTBOUND).
#
# Discipline: ZERO data change -- only non-existent primary keys (999999) or empty bodies are used.
#       No row is written, no stock is touched. Re-runnable.
#
# Reading the result:
#   A) unregistered prefix  : NOT 403 (ideally a business error such as "outbound not found")
#                             => the prefix is NOT guarded, login is enough.
#   B) registered prefix    : a low-privilege account SHOULD get 403 (negative control proving that
#                             the guard really works, so A's non-403 is not environment noise).
#
# ASCII-only on purpose (PS 5.1 + BOM pitfalls: a UTF-8 file without BOM is parsed as GBK and the
# Chinese literals break the parser).

$ErrorActionPreference = 'Continue'
$BASE = 'http://localhost:8080'

function Info([string]$m)  { Write-Output ("  [INFO] " + $m) }
function Ok([string]$m)    { Write-Output ("  [ OK ] " + $m) }
function Warn2([string]$m) { Write-Output ("  [WARN] " + $m) }

function Login([string]$u, [string]$p) {
  try {
    # NOTE: login requires companyId (multi-company); without it the API answers 400 "select a company".
    $r = Invoke-RestMethod -Uri "$BASE/api/auth/login" -Method Post -ContentType 'application/json' `
         -Body (@{ username = $u; password = $p; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
    if ($r -and $r.data -and $r.data.token) { return $r.data.token }
    return $null
  } catch { return $null }
}

function Call([string]$token, [string]$method, [string]$path, [string]$bodyJson) {
  # sa-token.token-name = Authorization (no "Bearer " prefix; the frontend assigns the raw token).
  $hdr = @{ Authorization = $token }
  try {
    if ($bodyJson) {
      $resp = Invoke-WebRequest -Uri ($BASE + $path) -Method $method -Headers $hdr `
              -ContentType 'application/json' -Body $bodyJson -TimeoutSec 20 -UseBasicParsing
    } else {
      $resp = Invoke-WebRequest -Uri ($BASE + $path) -Method $method -Headers $hdr -TimeoutSec 20 -UseBasicParsing
    }
    return @{ code = [int]$resp.StatusCode; body = [string]$resp.Content }
  } catch {
    $sc = -1
    try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { $sc = -1 }
    $body = ''
    try {
      $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      $body = $sr.ReadToEnd()
    } catch { $body = [string]$_.Exception.Message }
    return @{ code = $sc; body = $body }
  }
}

# Extract the business code from the R envelope {"code":NNN,...}; business errors still come back as
# HTTP 200 (403 is a *business* code here, not an HTTP status).
function Code([string]$body) {
  if ([string]::IsNullOrEmpty($body)) { return -1 }
  $m = [regex]::Match($body, '"code"\s*:\s*(-?\d+)')
  if ($m.Success) { return [int]$m.Groups[1].Value }
  return -1
}

function Short([string]$s) {
  if ([string]::IsNullOrEmpty($s)) { return '' }
  $s = $s -replace "\s+", ' '
  if ($s.Length -gt 220) { return $s.Substring(0, 220) } else { return $s }
}

Write-Output '=== 0) backend alive + login ==='
$alive = $false
try { $h = Invoke-WebRequest -Uri "$BASE/doc.html" -TimeoutSec 10 -UseBasicParsing; $alive = ($h.StatusCode -eq 200) } catch { $alive = $false }
if (-not $alive) { Write-Output '  [FATAL] backend not reachable on 8080 - start it first'; exit 2 }
Ok "backend alive"

$admin = Login 'lin' '123'
if (-not $admin) { Write-Output '  [FATAL] admin (lin/123) login failed'; exit 2 }
Ok ("admin login ok (token len " + $admin.Length + ")")

$low = Login 'audit_merch' '123'
if ($low) { Ok ("low-priv login ok: audit_merch (token len " + $low.Length + ")") }
else { Warn2 "audit_merch login failed (different account/password) - low-priv rows will be skipped" }

Write-Output ''
Write-Output '=== A) UNREGISTERED prefix /api/inventory/outbound (write endpoints; id 999999 missing) ==='
$a1 = Call $admin 'Put' '/api/inventory/outbound/999999/audit' ''
Info ("admin   PUT /999999/audit    -> HTTP " + $a1.code + " | " + (Short $a1.body))
$a2 = Call $admin 'Put' '/api/inventory/outbound/999999/un-audit' ''
Info ("admin   PUT /999999/un-audit -> HTTP " + $a2.code + " | " + (Short $a2.body))
$a3 = Call $admin 'Put' '/api/inventory/outbound/999999/cancel' ''
Info ("admin   PUT /999999/cancel   -> HTTP " + $a3.code + " | " + (Short $a3.body))
$b1 = $null; $b2 = $null
if ($low) {
  $b1 = Call $low 'Put' '/api/inventory/outbound/999999/audit' ''
  Info ("lowpriv PUT /999999/audit    -> HTTP " + $b1.code + " | " + (Short $b1.body))
  # NOTE: the POST-create probe was REMOVED after the first run -- it was a REAL WRITE: the low-priv
  # account created a DRAFT outbound (customer 999999 / warehouse 999999, code CK-20260919001).
  # The row was deleted right away and both tables were verified back to their 0-row baseline.
  # Lesson: "non-existent primary key" only works for *existing-key* endpoints; a create endpoint
  # writes even with fake ids. This script must stay read-only.
  Info "lowpriv POST /            -> skipped (would create a row; see note above)"
}

Write-Output ''
Write-Output '=== B) NEGATIVE CONTROL /api/sale/return (REGISTERED in RULES, page code sale:return) ==='
$c1 = $null
if ($low) {
  $c1 = Call $low 'Put' '/api/sale/return/999999/audit' ''
  Info ("lowpriv PUT /999999/audit -> HTTP " + $c1.code + " | " + (Short $c1.body))
} else { Warn2 "skip (no low-priv account)" }
$c2 = Call $admin 'Put' '/api/sale/return/999999/audit' ''
Info ("admin   PUT /999999/audit -> HTTP " + $c2.code + " | " + (Short $c2.body))

Write-Output ''
Write-Output '=== C) verdict (business code, not HTTP status) ==='
$c_a1 = Code $a1.body
$c_b1 = if ($b1) { Code $b1.body } else { $null }
$c_c1 = if ($c1) { Code $c1.body } else { $null }
Info ("business codes: admin/outbound=" + $c_a1 + " lowpriv/outbound=" + $c_b1 + " lowpriv/return=" + $c_c1)
if ($c_a1 -eq 403) {
  Warn2 "A returned 403 => outbound IS blocked; F7-105 'reachable by any logged-in user' does NOT hold - re-check"
} else {
  Ok ("A is not 403 (business code " + $c_a1 + ") => prefix is NOT page-code guarded; the request reaches the business layer")
  if ($null -ne $c_b1 -and $c_b1 -ne 403) { Ok ("low-priv account also reaches it (business code " + $c_b1 + ") => MISSING GUARD CONFIRMED") }
}
if ($null -ne $c_c1) {
  if ($c_c1 -eq 403) { Ok "B low-priv account got 403 on the REGISTERED prefix => the guard works (negative control holds)" }
  else { Warn2 ("B low-priv account did not get 403 (code " + $c_c1 + ") => control inconclusive") }
}

Write-Output ''
Write-Output '  [NOTE] No real deduction was performed (to avoid polluting stock). The finding rests on'
Write-Output '         "endpoint reachable" + "code path is deterministic". For end-to-end proof: create a DRAFT'
Write-Output '         outbound bound to an AUDITED sale order, audit it, then compare the reconciliation diff'
Write-Output '         (this DOES deduct stock - only do it on a restorable database).'
