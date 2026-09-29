# Batch A (settings audit, 2026-09-30): permission probe for the settings surface.
# READ ONLY: logins + GET requests only (no writes). Prints HTTP/business code per endpoint for two accounts.
# ASCII-only on purpose.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
function Lg($u, $p) {
  try { return (Invoke-RestMethod -Uri ($base + '/auth/login') -Method Post -ContentType 'application/json' `
      -Body ('{"username":"' + $u + '","password":"' + $p + '","companyId":1}')).data.token } catch { return $null }
}
function G($tok, $path) {
  if (-not $tok) { return 'no-token' }
  try {
    $r = Invoke-RestMethod -Uri ($base + $path) -Headers @{ Authorization = $tok }
    $c = if ($null -ne $r.code) { $r.code } else { 200 }
    $n = 0
    if ($r.data -is [System.Collections.IEnumerable] -and $r.data -isnot [string]) { $n = @($r.data).Count }
    elseif ($null -ne $r.data -and $r.data.PSObject.Properties['total']) { $n = $r.data.total }
    return ('code=' + $c + ' rows=' + $n)
  } catch { return ('HTTP ' + $_.Exception.Response.StatusCode.value__) }
}
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }

Write-Host '=== settings permission probe (read only) ==='
$admin = Lg 'lin' '123'
$low = Lg 'perm_test' '123'
$nobody = Lg 'perm_test' 'wrong-password'
Write-Host ('admin=' + [bool]$admin + '  low(perm_test, sales)= ' + [bool]$low + '  bad-password-login=' + [bool]$nobody)

$paths = @(
  '/settings/company',
  '/settings/params',
  '/settings/logs?pageNum=1&pageSize=5',
  '/company/list',
  '/company/1',
  '/system/menu/tree/user',
  '/system/menu/tree',
  '/system/dashboard-tabs/mine',
  '/system/user/page?pageNum=1&pageSize=5',
  '/system/role/page?pageNum=1&pageSize=5',
  '/system/role/enabled',
  '/system/user/1/menus',
  '/system/user/default-dashboard-tabs?roleIds=1'
)
foreach ($p in $paths) {
  Write-Host ('  ' + $p.PadRight(52) + ' admin=' + (G $admin $p).PadRight(18) + ' low=' + (G $low $p))
}

Sec 'content shape check: does /system/menu/tree/user leak the FULL menu tree for a low-privilege user?'
try {
  $r = Invoke-RestMethod -Uri ($base + '/system/menu/tree/user') -Headers @{ Authorization = $low }
  $all = @($r.data)
  Write-Host ('    top-level nodes returned = ' + $all.Count)
  foreach ($n in $all) { Write-Host ('      - id=' + $n.id + ' name=' + $n.menuName + ' path=' + $n.routePath) }
} catch { Write-Host ('    err: ' + $_.Exception.Message) }

Sec 'content shape check: /company/list - which fields are exposed to whom (F8-01 fix check)'
function ShapeCom($tok, $label) {
  try {
    $r = Invoke-RestMethod -Uri ($base + '/company/list') -Headers @{ Authorization = $tok }
    $rows = @($r.data)
    Write-Host ('    ' + $label + ': rows=' + $rows.Count)
    foreach ($c in $rows) {
      $props = @($c.PSObject.Properties | Where-Object { $null -ne $_.Value } | ForEach-Object { $_.Name })
      Write-Host ('      id=' + $c.id + ' name=' + $c.companyName + ' nonNullFields=[' + ($props -join ',') + ']')
    }
  } catch { Write-Host ('    ' + $label + ': err ' + $_.Exception.Message) }
}
ShapeCom $admin 'admin(super_admin)'
ShapeCom $low 'low(perm_test)'
ShapeCom 'invalid-token' 'anonymous'

Sec 'NO-TOKEN probe: which of the guard-excluded paths are reachable without logging in?'
foreach ($p in @('/company/list', '/company/admin/verify', '/system/menu/tree/user', '/settings/params', '/settings/logs?pageNum=1&pageSize=5', '/system/user/page?pageNum=1&pageSize=5')) {
  try {
    $r = Invoke-RestMethod -Uri ($base + $p) -Headers @{ Authorization = 'invalid-token' }
    Write-Host ('  ' + $p.PadRight(46) + ' => code=' + $r.code + ' msg=' + $r.msg)
  } catch { Write-Host ('  ' + $p.PadRight(46) + ' => HTTP ' + $_.Exception.Response.StatusCode.value__) }
}

Sec 'F8-04 negative case: repeated failures on /company/admin/verify must lock the account'
# Uses a NON-EXISTENT username on purpose: nothing real gets locked, and no row is written.
$body = '{"username":"__probe_no_such_user__","password":"x"}'
for ($i = 1; $i -le 6; $i++) {
  try {
    $r = Invoke-RestMethod -Uri ($base + '/company/admin/verify') -Method Post -ContentType 'application/json' -Body $body
    Write-Host ('    attempt ' + $i + ' => code=' + $r.code + ' msg=' + $r.msg)
  } catch {
    try {
      $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      Write-Host ('    attempt ' + $i + ' => HTTP ' + $_.Exception.Response.StatusCode.value__ + ' ' + $sr.ReadToEnd())
    } catch { Write-Host ('    attempt ' + $i + ' => HTTP ' + $_.Exception.Response.StatusCode.value__) }
  }
}

Write-Host ''
Write-Host 'DONE (no writes performed).'
