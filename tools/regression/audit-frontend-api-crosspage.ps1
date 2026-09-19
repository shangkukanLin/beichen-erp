# F3-3 frontend cross-page audit (method aware, mirrors ApiPermGuard.check).
#
# For every view: resolve the calls it makes (imported api functions + direct request calls, with HTTP method),
# map the view to its page code (router path -> sys_menu.perms), then replay ApiPermGuard's decision:
#   EXEMPT            -> always allowed
#   READ_SHARED + GET -> allowed (per-prefix cross-page read whitelist)
#   WRITE_RULES + GET -> allowed => "shared master data" read, counted and reported separately, NEVER a violation
#   RULES             -> the view's own code must be one of the rule's codes (else VIOLATION)
#   WRITE_RULES       -> same, but only for non-GET
# Rules are parsed straight out of ApiPermGuard.java (single source of truth, no drift).
# -Strict ignores READ_SHARED (pretend reads must stay inside the page) => prints the work list for read isolation.
#
# Parsing hardening (2026-09-19): fixes 4 blind spots that silently under-counted calls
#   * nested generics   request.get<PageResult<SaleOrder>>('/x')  -> balanced-bracket scan (was <[^>]*>)
#   * namespace imports import * as api from '@/api/x' + api.fn() -> member calls resolved through apiMap
#   * body bleed        an export function without its own request call used to steal the NEXT function's url
#                       (non-greedy .*? overflow) -> per-function body is now brace matched
#   * deep prefixes     PrefixSet only produced up to 3 segments, so 4+ segment rules could never match
# This file must stay pure ASCII.
$ErrorActionPreference = 'Continue'
$root = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$web = Join-Path $root 'beichen-erp-web\src'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'

# ---------- 0) scanners: index of the closing bracket, and "METHOD url" of the first request call ----------
function IndexOfClose([string]$s, [int]$openIdx, [char]$openCh, [char]$closeCh) {
  $d = 0
  for ($i = $openIdx; $i -lt $s.Length; $i++) {
    if ($s[$i] -eq $openCh) { $d++ } elseif ($s[$i] -eq $closeCh) { $d--; if ($d -eq 0) { return $i } }
  }
  return -1
}
function RequestCall([string]$body) {
  $m = [regex]::Match($body, 'request\.(get|post|put|delete)\s*')
  if (-not $m.Success) { return $null }
  $k = $m.Index + $m.Length
  if ($k -lt $body.Length -and $body[$k] -eq '<') {
    $c = IndexOfClose $body $k '<' '>'
    if ($c -lt 0) { return $null }
    $k = $c + 1
  }
  while ($k -lt $body.Length -and [char]::IsWhiteSpace($body[$k])) { $k++ }
  if ($k -ge $body.Length -or $body[$k] -ne '(') { return $null }
  $c2 = IndexOfClose $body $k '(' ')'
  if ($c2 -lt 0) { return $null }
  $args = $body.Substring($k, $c2 - $k + 1)
  $lit = [regex]::Match($args, '[`"'']([^`"'']+)')
  if (-not $lit.Success) { return $null }
  return $m.Groups[1].Value.ToUpper() + ' ' + $lit.Groups[1].Value
}

# ---------- 1) rules / write rules / exempt / shared-read, from ApiPermGuard.java ----------
$java = Get-Content (Join-Path $root 'beichen-erp-server\src\main\java\com\beichen\erp\config\ApiPermGuard.java') -Raw
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
$postReads = ParseList 'READ_POST_PATHS'

# ---------- 2) api function -> "METHOD url" (brace matched body, balanced generics) ----------
$apiMap = @{}
Get-ChildItem (Join-Path $web 'api\*.ts') | ForEach-Object {
  $t = Get-Content $_.FullName -Raw
  foreach ($m in [regex]::Matches($t, 'export function\s+(\w+)\s*\(')) {
    $po = $m.Index + $m.Length - 1
    $pc = IndexOfClose $t $po '(' ')'
    if ($pc -lt 0) { continue }
    $bo = $t.IndexOf('{', $pc)
    if ($bo -lt 0) { continue }
    $bc = IndexOfClose $t $bo '{' '}'
    if ($bc -lt 0) { continue }
    $call = RequestCall $t.Substring($bo, $bc - $bo + 1)
    if ($call) { $apiMap[$m.Groups[1].Value] = $call }
  }
}
Write-Output ("rules=" + $rules.Count + " writeRules=" + $writeRules.Count + " exempt=" + $exempt.Count + " sharedRead=" + $shared.Count + " postReads=" + $postReads.Count + " apiFns=" + $apiMap.Count)

# ---------- 3) router: view file -> route path ----------
$router = Get-Content (Join-Path $web 'router\index.ts') -Raw
$viewRoutes = @{}
foreach ($m in [regex]::Matches($router, "(?s)path:\s*'([^']+)'[^}]*?import\('@/views/([^']+)'")) {
  # key = path relative to src/ (same shape as $rel below => 'views/xxx/yyy.vue')
  $viewRoutes['views/' + $m.Groups[2].Value] = $m.Groups[1].Value
}

# ---------- 4) menu route path -> page code ----------
$menuCodes = @{}
$out = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e "SELECT route_path, perms FROM sys_menu WHERE perms IS NOT NULL AND route_path IS NOT NULL AND route_path<>''" 2>$null
foreach ($line in @($out)) {
  if ("$line" -eq '') { continue }
  $p = "$line" -split "`t"
  if ($p.Count -ge 2) { $menuCodes[($p[0]).TrimStart('/')] = ($p[1]).Trim() }
}

function PrefixSet([string]$url) {
  # every cumulative prefix up to the full path (mirrors ApiPermGuard.under + longest match)
  $s = @($url.TrimStart('/') -split '/')
  $r = @()
  for ($i = 1; $i -le $s.Count; $i++) { $r += '/' + (($s[0..($i - 1)]) -join '/') }
  return $r
}
function LongestOf([string]$url, $map) {
  $best = ''
  foreach ($p in (PrefixSet $url)) { if ($map.ContainsKey($p) -and $p.Length -gt $best.Length) { $best = $p } }
  return $best
}
function StartsWithAny([string]$uri, $list) {
  # segment aware, same as ApiPermGuard.under()
  foreach ($e in $list) { if ($uri -eq $e -or $uri.StartsWith($e + '/')) { return $true } }
  return $false
}

$violations = @()
$masterHits = @()
$checkedViews = 0
$unknownView = 0
$hits = 0
$control = ($args -contains '-Control')
# -Strict: ignore READ_SHARED (pretend reads must stay inside the page) => prints the work list for
# "read isolation". Every strict violation = one cross-page READ that has to be fixed.
$strict = ($args -contains '-Strict')
$viewsWithUrls = 0

Get-ChildItem (Join-Path $web 'views') -Recurse -Filter *.vue | ForEach-Object {
  $rel = $_.FullName.Substring($web.Length + 1).Replace('\', '/')
  $route = $viewRoutes[$rel]
  $text = Get-Content $_.FullName -Raw
  $calls = New-Object System.Collections.Generic.HashSet[string]
  # (a) named imports: import { a, b as c } from '@/api/x'
  foreach ($m in [regex]::Matches($text, "(?s)import\s*\{([^}]*)\}\s*from\s*'@/api/")) {
    foreach ($n in ($m.Groups[1].Value -split ',')) {
      $name = ($n -split ':')[-1].Trim()
      if ($apiMap.ContainsKey($name)) { [void]$calls.Add($apiMap[$name]) }
    }
  }
  # (b) namespace imports: import * as ns from '@/api/x'  =>  ns.fn(...)
  foreach ($m in [regex]::Matches($text, "(?s)import\s*\*\s*as\s+(\w+)\s*from\s*'@/api/")) {
    $ns = $m.Groups[1].Value
    foreach ($c in [regex]::Matches($text, [regex]::Escape($ns) + '\.(\w+)\s*\(')) {
      $fn = $c.Groups[1].Value
      if ($apiMap.ContainsKey($fn)) { [void]$calls.Add($apiMap[$fn]) }
    }
  }
  # (c) direct request calls: request.<verb><generics>('url')
  foreach ($m in [regex]::Matches($text, 'request\.(get|post|put|delete)\s*')) {
    $call = RequestCall $text.Substring($m.Index)
    if ($call) { [void]$calls.Add($call) }
  }
  if ($calls.Count -eq 0) { return }
  $viewsWithUrls++

  $ownCode = ''
  if ($route) {
    $r = $route.TrimStart('/')
    $cands = @($r)
    foreach ($suffix in @('/add', '/edit', '/detail', '/view')) { if ($r.EndsWith($suffix)) { $cands += $r.Substring(0, $r.Length - $suffix.Length) } }
    $seg = @($r -split '/')
    if ($seg.Count -ge 2) { $cands += ($seg[0] + '/' + $seg[1]) }
    $cands += $seg[0]
    foreach ($c in $cands) { if ($menuCodes.ContainsKey($c)) { $ownCode = $menuCodes[$c]; break } }
  }
  if ($ownCode -eq '') { $unknownView++; return }
  $checkedViews++

  foreach ($call in $calls) {
    $method = ($call -split ' ')[0]
    $u = ($call -split ' ', 2)[1]
    if (-not $u.StartsWith('/api')) { $u = '/api' + $u }
    if (StartsWithAny $u $exempt) { continue }
    $isRead = ($method -eq 'GET') -or (($method -eq 'POST') -and ($postReads -contains $u))
    if ($isRead -and (StartsWithAny $u $shared) -and -not $strict) { continue }

    $rp = LongestOf $u $rules
    $wp = ''
    if ($rp -eq '') { $wp = LongestOf $u $writeRules }
    $prefix = if ($rp -ne '') { $rp } else { $wp }
    if ($prefix -eq '') { continue }
    if ($rp -eq '' -and $isRead) {
      # WRITE_RULES + read => shared master data (product/customer/supplier/warehouse/project...)
      if ($wp -ne '') { $masterHits += [pscustomobject]@{ view = $rel; ownCode = $ownCode; prefix = $wp; method = $method; url = $u } }
      continue
    }

    $hits++
    $codes = if ($rp -ne '') { $rules[$rp] } else { $writeRules[$wp] }
    if ($control) {
      $violations += [pscustomobject]@{ view = $rel; route = $route; ownCode = $ownCode; prefix = $prefix; needs = 'CONTROL'; method = $method; url = $u }
      continue
    }
    if ($codes -notcontains $ownCode) {
      $violations += [pscustomobject]@{ view = $rel; route = $route; ownCode = $ownCode; prefix = $prefix; needs = ($codes -join '|'); method = $method; url = $u }
    }
  }
}

Write-Output ("views with calls=$viewsWithUrls  checked=$checkedViews  no-code=$unknownView  checkable calls=$hits  masterData=$($masterHits.Count)  violations=" + $violations.Count)
if ($control) {
  Write-Output ("CONTROL: force-flagged every checkable call => detector saw " + $hits + " calls (non-zero = audit not vacuous)")
}
if ($strict -and $masterHits.Count -gt 0) {
  Write-Output '--- STRICT mode: shared master-data reads (accepted, excluded from violations) ---'
  $masterHits | Group-Object prefix | Sort-Object Count -Descending | ForEach-Object {
    $views = @($_.Group | ForEach-Object { $_.view } | Sort-Object -Unique | Select-Object -First 4) -join ' '
    Write-Output ("MASTER " + $_.Name + "  hits=" + $_.Count + "  views=" + $views)
  }
}
if ($strict -and $violations.Count -gt 0) {
  Write-Output '--- STRICT mode: cross-page READs that would break under full read isolation ---'
  $violations | Group-Object view | Sort-Object Count -Descending | ForEach-Object {
    $items = @($_.Group | ForEach-Object { $_.prefix + '(' + $_.method + ')' } | Sort-Object -Unique) -join ' '
    Write-Output ("VIEW " + $_.Name + "  [code=" + $_.Group[0].ownCode + "]  reads " + $_.Count + " : " + $items)
  }
}
if ($violations.Count -gt 0) {
  if (-not $control -and -not $strict) {
    Write-Output '--- violations grouped by protected prefix ---'
    $violations | Group-Object prefix | Sort-Object Count -Descending | ForEach-Object {
      $codes = @($_.Group | ForEach-Object { $_.ownCode } | Sort-Object -Unique) -join ','
      $views = @($_.Group | ForEach-Object { $_.view } | Sort-Object -Unique | Select-Object -First 3) -join ' '
      Write-Output ("GROUPS " + $_.Name + " needs=" + $_.Group[0].needs + " hits=" + $_.Count + " callerCodes=" + $codes + " views=" + $views)
    }
    Write-Output ("RESULT FRONTEND-AUDIT VIOLATIONS " + $violations.Count)
  } elseif ($strict) {
    Write-Output ("RESULT FRONTEND-AUDIT STRICT " + $violations.Count + " masterData=" + $masterHits.Count)
  } else {
    Write-Output ("RESULT FRONTEND-AUDIT CONTROL " + $violations.Count)
  }
} else {
  Write-Output ("PASS no cross-page API call violates the page-code rules (masterData=" + $masterHits.Count + ")")
  Write-Output ("RESULT FRONTEND-AUDIT PASS masterData=" + $masterHits.Count)
}
exit 0
