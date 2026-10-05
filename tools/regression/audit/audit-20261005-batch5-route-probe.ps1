# audit 2026-10-05 batch 5 - UI + route-table verification for F7-280 / F7-281 (READ ONLY).
#   F7-280: /inventory/purchase/add must resolve to ONE page and render (the duplicate registration is gone).
#   F7-281: /company-manage must be reachable for a SUPER admin, and STILL bounced to /403 for a plain admin.
# ASCII ONLY in every printed string (PS 5.1 + no BOM would mangle Chinese literals).
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'ui-e2e-lib.ps1')
Write-Host 'STEP 0: lib loaded; ensuring the default (super-admin) session...'
EnsureLogin

# ---- F7-281 negative-first: a plain admin (admin2) must NOT be let in -------------------------------
$b = '{"username":"admin2","password":"123","companyId":1}'
try { $lg = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 20 } catch { $lg = $null }
if ($null -eq $lg -or [string]$lg.code -ne '200') { Write-Output 'NOTE F7-281: admin2 cannot log in with password 123 -> the non-super-admin negative could not be run' }
else {
  $t2 = [string]$lg.data.token
  EvalJs ("(()=>{localStorage.setItem('beichen_erp_token','" + $t2 + "');return 'SET'})()") | Out-Null
  Open '/company-manage' 2800
  $p2 = (EvalJs 'String(location.pathname)').Trim()
  Write-Host ('  admin2 (admin, NOT super) opening /company-manage -> ' + $p2)
  Ok ($p2 -eq '/403') ('F7-281 negative control: a non-super admin is still bounced to /403 (got ' + $p2 + ')')
}

# ---- F7-281 positive: the super admin stays on the page ---------------------------------------------
EnsureLogin
Open '/company-manage' 2600
ClearErrs | Out-Null
Start-Sleep -Milliseconds 1200
$p1 = (EvalJs 'String(location.pathname)').Trim()
$head = Dec (EvalJs "(()=>{const e=document.querySelector('.page-header__title')||document.querySelector('h1')||document.querySelector('.el-card')||document.body;return btoa(unescape(encodeURIComponent(((e&&e.innerText)||'').trim().slice(0,24))))})()")
Write-Host ('  super admin opening /company-manage -> ' + $p1 + ' ; page text head=[' + $head + ']')
Ok ($p1 -eq '/company-manage') ('F7-281 fixed: the super admin reaches /company-manage (got ' + $p1 + ')')
Ok (((Errs) -eq '[]') -and ($head -ne '')) ('company page renders with content and no JS errors (errors=' + (Errs) + ')')

# ---- F7-280: the purchase add page renders (single registration, resolves to form.vue) ---------------
ClearErrs | Out-Null
Open '/inventory/purchase/add' 2600
Start-Sleep -Milliseconds 1500
$p3 = (EvalJs 'String(location.pathname)').Trim()
$forms = (EvalJs "(()=>{return document.querySelectorAll('.el-form').length})()").Trim()
$btns = (EvalJs "(()=>{const v=e=>e.getClientRects().length>0;return [...document.querySelectorAll('button')].filter(v).length})()").Trim()
Write-Host ('  /inventory/purchase/add -> ' + $p3 + ' ; visible forms=' + $forms + ' ; visible buttons=' + $btns)
Ok (($p3 -eq '/inventory/purchase/add') -and ([int]$forms -ge 1) -and ([int]$btns -ge 1)) ('F7-280 fixed: the purchase add page renders (forms=' + $forms + ', buttons=' + $btns + ')')
Ok ((Errs) -eq '[]') ('purchase add page has no JS errors (errors=' + (Errs) + ')')

# ---- route-table self-check: THIS path must be registered exactly once --------------------------------
$repo = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$routerFile = Join-Path $repo 'beichen-erp-web\src\router\index.ts'
if (-not (Test-Path $routerFile)) { Write-Output ('FAIL router file not found at ' + $routerFile); Write-Output 'RESULT BATCH5-ROUTE FAIL count 1'; exit 1 }
$paths = @(Select-String -Path $routerFile -Pattern "path: '([^']+)'" | ForEach-Object { $_.Matches[0].Groups[1].Value })
$dups = @($paths | Group-Object | Where-Object { $_.Count -gt 1 })
$mine = @($paths | Where-Object { $_ -eq 'inventory/purchase/add' }).Count
Write-Host ('  route paths total=' + $paths.Count + ' ; "inventory/purchase/add" registered ' + $mine + ' time(s) ; other repeated paths=' + $dups.Count)
foreach ($d in $dups) { if ($d.Name -ne 'inventory/purchase/add') { Write-Host ('    (info) repeated path: ' + $d.Name + ' x' + $d.Count) } }
Ok ($mine -eq 1) ('F7-280: inventory/purchase/add is registered exactly once (got ' + $mine + ')')
Summary 'audit F7-280/F7-281 (routes + UI)'
