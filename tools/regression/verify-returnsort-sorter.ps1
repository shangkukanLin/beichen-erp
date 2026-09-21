# Return-sort operator (sorter) verification -- user request 2026-09-22:
#   "whoever operates it is the one who sorted it", and it must show up in the detail page.
#   Asserts ① create with a SPOOFED sortUserId/sortUserName in the body => the stored/returned sorter must be
#              the LOGGED-IN user (server-side stamping, client value ignored)
#           ② editing (PUT) with another spoofed value => sorter is still the operator
#           ③ DB: return_sort.sort_user_id / sort_user_name are filled
#           ④ UI: the detail page renders the 整理人 label cell with the login name
#           ⑤ cleanup: the test draft is deleted (rerun-safe; drafts do not move stock, only audit does)
#   Fixtures: the source batch MUST come from the pending overview (status SORTABLE), because the service
#   validates "available pending qty in the after-sale warehouse" with the SAME algorithm at create time
#   (verified: picking an arbitrary product fails with "pending qty 1 exceeds available 0").
#   Target A/B/C/defect warehouses are copied from an existing sort doc (already passes validation).
#   ASCII ONLY: all Chinese text goes through ui-e2e-zh.json.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$REMARK = 'AUTO TEST sorter (verify-returnsort-sorter)'
$fail = 0

. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')   # only for ZH/B64/EvalJs/Open (Ok/Summary are redefined below)
function Ok($msg) { Write-Output ("PASS " + $msg) }
function Bad($msg) { Write-Output ("FAIL " + $msg); $script:fail++ }
function EvalJs2($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }

function LoginFlow() {
  Write-Output '(session expired, logging in)'
  $snap = (agent-browser snapshot -i) -join "`n"
  $mu = [regex]::Match($snap, [regex]::Escape((ZH 'lbl_login_user')) + '[^\n]*ref=(e\d+)')
  $mp = [regex]::Match($snap, [regex]::Escape((ZH 'lbl_login_pass')) + '[^\n]*ref=(e\d+)')
  $mb = [regex]::Match($snap, [regex]::Escape((ZH 'btn_login')) + '[^\n]*ref=(e\d+)')
  if ($mu.Success -and $mp.Success -and $mb.Success) {
    agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
    agent-browser wait 3500
  } else { Write-Output 'WARN login form not found in snapshot' }
}
function OpenFresh($url) {
  EvalJs2 "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 3000
  if ((EvalJs2 "'p=' + location.pathname") -match '/login') { LoginFlow; agent-browser open $url | Out-Null; agent-browser wait 3000 }
}

Write-Output '--- 1) API: a spoofed sorter in the body must be ignored'
$lg = Invoke-RestMethod -Uri ($api + '/auth/login') -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$tok = $lg.data.token
$h = @{ Authorization = $tok; 'Content-Type' = 'application/json' }

# fixture (a): sortable source batch, taken from the pending overview (same algorithm as the service validation)
$ov = Invoke-RestMethod -Uri ($api + '/inventory/return-sort/pending-overview') -Headers $h
$rows = @()
foreach ($w in @($ov.data.warehouses)) { foreach ($r in @($w.rows)) { $rows += $r } }
$pick = @($rows | Where-Object { $_.status -eq 'SORTABLE' -and [decimal]$_.remainQuantity -gt 0 })[0]
if (-not $pick) { Bad 'no SORTABLE pending batch available to build a draft'; Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
Write-Output ('  fixture pending=' + $pick.pendingId + ' wh=' + $pick.warehouseId + ' product=' + $pick.productId + ' remain=' + $pick.remainQuantity)
$wh = [int]$pick.warehouseId; $prod = [int]$pick.productId; $pendingId = [int]$pick.pendingId

# fixture (b): target warehouses copied from an existing sort doc (passes assertTargetWarehouses)
$tg = SqlOne 'SELECT CONCAT(target_warehouse_a,''|'',target_warehouse_b,''|'',target_warehouse_c,''|'',target_warehouse_defect) FROM return_sort WHERE target_warehouse_a IS NOT NULL ORDER BY id LIMIT 1'
$t = @($tg -split '\|')
if ($t.Count -lt 4) { Bad ('cannot resolve target warehouses from an existing sort doc: ' + $tg); Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
$pa = [int]$t[0]; $pb = [int]$t[1]; $pc = [int]$t[2]; $pd = [int]$t[3]
Write-Output ('  targets A|B|C|def = ' + $tg)

$item = @{ productId = $prod; pendingId = $pendingId; totalQuantity = 1; qtyA = 1; qtyB = 0; qtyC = 0; qtyDefect = 0 }
$payload = @{
  warehouseId = $wh; sortDate = (Get-Date -Format 'yyyy-MM-dd')
  targetWarehouseA = $pa; targetWarehouseB = $pb; targetWarehouseC = $pc; targetWarehouseDefect = $pd
  remark = $REMARK
  sortUserId = 999999; sortUserName = 'HACKER'
  items = @($item)
}
$created = $null
try { $created = Invoke-RestMethod -Uri ($api + '/inventory/return-sort') -Method Post -Headers $h -Body ($payload | ConvertTo-Json -Depth 6) } catch { Bad ('create failed: ' + $_.Exception.Message) }
$newId = [int](SqlOne ("SELECT id FROM return_sort WHERE remark='" + $REMARK + "' ORDER BY id DESC LIMIT 1"))
Write-Output ('  created draft id=' + $newId)
if ($newId -gt 0) { Ok 'a sort draft was created through the API' } else { Bad 'create did not produce a draft (see message above)' }

if ($newId -gt 0) {
  $d = Invoke-RestMethod -Uri ($api + "/inventory/return-sort/$newId") -Headers $h
  Write-Output ('  api sorter = ' + $d.data.sortUserName + ' (id=' + $d.data.sortUserId + ')')
  if ($d.data.sortUserName -eq 'lin') { Ok 'the API reports the LOGGED-IN user as the sorter (spoofed name ignored)' }
  else { Bad ('sorter name is not the logged-in user: ' + $d.data.sortUserName) }
  if ([int]$d.data.sortUserId -ne 999999) { Ok 'the spoofed user id was ignored as well' } else { Bad 'the spoofed user id was stored (server-side stamping broken)' }

  Write-Output '--- 2) API: updating the draft refreshes the sorter to the operator (still not the client value)'
  $payload.sortUserName = 'HACKER2'; $payload.sortUserId = 888888
  try { Invoke-RestMethod -Uri ($api + "/inventory/return-sort/$newId") -Method Put -Headers $h -Body ($payload | ConvertTo-Json -Depth 6) | Out-Null } catch { Bad ('update failed: ' + $_.Exception.Message) }
  $d2 = Invoke-RestMethod -Uri ($api + "/inventory/return-sort/$newId") -Headers $h
  if ($d2.data.sortUserName -eq 'lin') { Ok 'after an edit the sorter is the operator again (client value still ignored)' }
  else { Bad ('sorter after update: ' + $d2.data.sortUserName) }

  Write-Output '--- 3) DB: both columns are filled'
  $dbName = SqlOne ("SELECT IFNULL(sort_user_name,'(null)') FROM return_sort WHERE id=" + $newId)
  $dbUid = SqlOne ("SELECT IFNULL(sort_user_id,0) FROM return_sort WHERE id=" + $newId)
  Write-Output ('  db sorter = ' + $dbName + ' (id=' + $dbUid + ')')
  if ($dbName -eq 'lin' -and [int]$dbUid -gt 0) { Ok 'db columns sort_user_name / sort_user_id are written' }
  else { Bad ('db columns not written: ' + $dbName + '/' + $dbUid) }

  Write-Output '--- 4) UI: the detail page shows the sorter'
  OpenFresh "$base/inventory/return-sort/detail/$newId"
  # NOTE: in el-descriptions the LABEL cell is a td (class contains el-descriptions__label), not a th --
  # a th selector finds nothing (returned NOLABEL). Label/content cells align by DOM order.
  $jsVal = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('" + (B64 (ZH 'lbl_sorter')) + "');const lab=[...document.querySelectorAll('td.el-descriptions__label')];const i=lab.findIndex(c=>(c.innerText||'').trim()===L);if(i<0)return 'NOLABEL';const val=document.querySelectorAll('td.el-descriptions__content')[i];return val?((val.innerText||'').trim()):'NOCELL';})()"
  $shown = EvalJs2 $jsVal
  Write-Output ('  detail sorter cell = ' + $shown)
  # NOTE: agent-browser eval returns JSON, so a string result comes back quoted ("lin") -> match, don't -eq
  if ($shown -match 'lin') { Ok 'the detail page renders the sorter label cell = login user' }
  else { Bad ('detail page sorter cell is: ' + $shown) }

  Write-Output '--- 5) cleanup: delete the test draft'
  try { Invoke-RestMethod -Uri ($api + "/inventory/return-sort/$newId") -Method Delete -Headers $h | Out-Null } catch { Write-Output ('  delete error: ' + $_.Exception.Message) }
  $left = SqlOne ("SELECT COUNT(*) FROM return_sort WHERE id=" + $newId)
  if ($left -eq '0') { Ok 'the test draft was deleted (rerun-safe)' } else { Bad ('test draft still present: ' + $left) }
}

if ($fail -eq 0) { Write-Output 'RESULT PASS return-sort sorter recorded server-side and shown in detail' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
