# F3-3 role x module matrix (method aware, rules parsed from ApiPermGuard.java).
#   GET  on a rule prefix: blocked iff (role holds none of the codes) and (prefix is not READ_SHARED)
#   POST on a rule prefix: blocked iff (role holds none of the codes)              [writes are always closed]
# Expectations are derived from each role's real menu grants, so a mismatch means either over-blocking
# (a page the role owns gets 403) or a bypass (a page the role lost still answers).
# This file must stay pure ASCII.
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$guard = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp\config\ApiPermGuard.java'
$fail = 0
$skip = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function Skip($m) { Write-Output ("SKIP " + $m); $script:skip++ }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }
function LoginFull([string]$u, [string]$p) {
  try {
    $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":1}'
    return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b))
  } catch { return $null }
}
function Req([string]$method, [string]$path, [string]$tok, $body) {
  # $path is a guard-style path ('/api/xxx'); $api already ends with /api
  if ($path.StartsWith('/api')) { $path = $path.Substring(4) }
  $h = @{}; if ($tok) { $h['Authorization'] = $tok }
  try {
    if ($method -eq 'GET') { $r = Invoke-RestMethod -Uri "$api$path" -Method Get -Headers $h; return "$($r.code)" }
    $json = if ($null -eq $body) { '{}' } else { ConvertTo-Json -InputObject $body -Depth 6 }
    $r = Invoke-RestMethod -Uri "$api$path" -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
    return "$($r.code)"
  } catch {
    $sc = 'ERR'
    try { $sc = "HTTP$($_.Exception.Response.StatusCode.value__)" } catch { }
    return $sc
  }
}

# ---------- rules from the guard ----------
$java = Get-Content $guard -Raw
function ParseRules([string]$marker) {
  $map = @{}
  foreach ($m in [regex]::Matches($java, [regex]::Escape($marker) + '\("([^"]+)",([^)]*)\)')) {
    $map[$m.Groups[1].Value] = @([regex]::Matches($m.Groups[2].Value, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
  }
  return $map
}
function ParseList([string]$name) {
  $block = [regex]::Match($java, "(?s)$name\s*=\s*List\.of\((.*?)\);").Groups[1].Value
  return @([regex]::Matches($block, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
}
$rules = ParseRules 'rule'
$writeRules = ParseRules 'writeRule'
$exempt = ParseList 'EXEMPT'
$shared = ParseList 'READ_SHARED'
Write-Output ("guard: rules=" + $rules.Count + " writeRules=" + $writeRules.Count + " exempt=" + $exempt.Count + " sharedRead=" + $shared.Count)

# prefixes whose list endpoint is not /page
$probePath = @{ '/api/outsource/order-delivery' = '/order-page?page=1&size=1' }
# write probes: POST to the controller root (create endpoint); empty body must fail validation, never a write
$writeProbe = @('/api/inventory/purchase-exchange', '/api/warehouse', '/api/product')

$roles = @('dev_engineer', 'sales', 'warehouse', 'merchandiser', 'finance')
$admin = LoginFull 'lin' '123'
if ($null -eq $admin -or [string]$admin.code -ne '200') { Bad 'cannot login as admin'; Write-Output 'RESULT API-PERM-MATRIX FAIL count 1'; exit 1 }
$tokA = [string]$admin.data.token
$whBefore = SqlOne "SELECT COUNT(*) FROM warehouse"
$prodBefore = SqlOne "SELECT COUNT(*) FROM product"

$total = 0
foreach ($role in $roles) {
  $rid = SqlOne "SELECT id FROM sys_role WHERE role_code='$role'"
  if ($rid -eq '') { Bad ("role not found: $role"); continue }
  $uname = "perm_m_$role"
  SqlExec "DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE u.username='$uname'; DELETE FROM sys_user WHERE username='$uname';"
  $body = '{"username":"' + $uname + '","password":"123","status":1}'
  try { Invoke-RestMethod -Uri "$api/system/user" -Method Post -Headers @{ Authorization = $tokA } -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) | Out-Null } catch { }
  $uid = SqlOne "SELECT id FROM sys_user WHERE username='$uname'"
  if ($uid -eq '') { Bad ("cannot create probe user for role $role"); continue }
  SqlExec "INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES ($uid, $rid);"
  $lg = LoginFull $uname '123'
  if ($null -eq $lg -or [string]$lg.code -ne '200') { Bad ("cannot login as $uname"); continue }
  $perms = @($lg.data.userInfo.perms)
  $tokU = [string]$lg.data.token
  $allowed = 0; $blocked = 0; $badRole = 0

  foreach ($p in $rules.Keys) {
    $codes = $rules[$p]
    $holds = $false
    foreach ($c in $codes) { if ($perms -contains $c) { $holds = $true } }
    $isShared = ($shared -contains $p)
    $path = if ($probePath.ContainsKey($p)) { $p + $probePath[$p] } else { "$p/page?pageNum=1&pageSize=1" }
    $code = Req 'GET' $path $tokU $null
    $total++
    if ($holds -or $isShared) {
      $allowed++
      if ($code -eq '403') { Bad ("$role holds/read-shares $($codes -join '|') but GET $p -> 403 (over-blocking)"); $badRole++ }
    } else {
      $blocked++
      if ($code -ne '403') { Bad ("$role lacks $($codes -join '|') but GET $p -> $code (not blocked)"); $badRole++ }
    }
  }
  # write probes: writes are never read-shared
  foreach ($p in $writeProbe) {
    $codes = if ($rules.ContainsKey($p)) { $rules[$p] } elseif ($writeRules.ContainsKey($p)) { $writeRules[$p] } else { @() }
    if ($codes.Count -eq 0) { continue }
    $holds = $false
    foreach ($c in $codes) { if ($perms -contains $c) { $holds = $true } }
    $code = Req 'POST' $p $tokU @{}
    $total++
    if ($holds) {
      if ($code -eq '403') { Bad ("$role holds $($codes -join '|') but POST $p -> 403 (over-blocking)"); $badRole++ }
    } else {
      if ($code -eq '403') { $blocked++ } elseif ($code -match '^HTTP(404|405)$') { Skip ("$role POST $p -> $code (no create handler at that path, cannot verify)"); } else { Bad ("$role lacks $($codes -join '|') but POST $p -> $code (write not blocked)"); $badRole++ }
    }
  }
  if ($badRole -eq 0) { Ok ("role $role : GET allowed=$allowed blocked=$blocked, writes checked, 0 mismatches (perms=" + $perms.Count + ")") }
  SqlExec "DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE u.username='$uname'; DELETE um FROM sys_user_menu um JOIN sys_user u ON u.id=um.user_id WHERE u.username='$uname'; DELETE FROM sys_user WHERE username='$uname';"
}
$left = SqlOne "SELECT COUNT(*) FROM sys_user WHERE username LIKE 'perm_m_%'"
if ($left -eq '0') { Ok 'probe users cleaned up' } else { Bad ("probe users left: $left") }
$whAfter = SqlOne "SELECT COUNT(*) FROM warehouse"
$prodAfter = SqlOne "SELECT COUNT(*) FROM product"
if ("$whBefore" -eq "$whAfter") { Ok ("write probes wrote nothing (warehouse $whBefore -> $whAfter)") } else { Bad ("write probe polluted warehouse: $whBefore -> $whAfter") }
if ("$prodBefore" -eq "$prodAfter") { Ok ("write probes wrote nothing (product $prodBefore -> $prodAfter)") } else { Bad ("write probe polluted product: $prodBefore -> $prodAfter") }

Write-Output ("checked " + $total + " role x prefix combinations (skipped " + $skip + ")")
if ($fail -eq 0) { Write-Output 'RESULT API-PERM-MATRIX PASS' } else { Write-Output ("RESULT API-PERM-MATRIX FAIL count " + $fail) }
exit $fail
