# ASCII-only. Coverage audit for URL_DOMAIN, v2.
#
# Why v2: the first version scanned ONLY write requests (post/put/delete). That missed the case the
# user just hit: customer endpoints live under /inventory/customer (see api/customer.ts:46/50/54) while
# the table only registered /customer - so writes never bumped the `customer` domain (and, worse, the
# customer LIST stopped refreshing because P2 switched it from "refresh on every activate" to
# "refresh only when the domain changed").
#
# v2 scans get+post+put+delete and reports each uncovered prefix with its write/read counts:
#   writes > 0  => REAL gap: the 'list should refresh' signal is lost
#   writes = 0  => likely an alias prefix of a registered domain (review by hand)
$web = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web'
$dfPath = Join-Path $web 'src\utils\dataFreshness.ts'
$df = [System.IO.File]::ReadAllText($dfPath)

# NOTE: the optional `<...>` matters a lot - api/*.ts calls look like
#   request.post<void>('/inventory/customer', data)
# and without it every typed call (i.e. most of the api layer) was silently skipped, which made the
# earlier run report a false "all writes covered".
$rx = [regex]'request\.(?<m>get|post|put|delete)\s*(?:<[^>()]*>)?\s*\(\s*(?<q>[\x27\x22\x60])(?<p>[^\x27\x22\x60]+)\k<q>'
$stat = @{}
foreach ($f in (Get-ChildItem (Join-Path $web 'src') -Recurse -Include '*.ts', '*.vue' -File)) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  foreach ($m in $rx.Matches($t)) {
    $p = $m.Groups['p'].Value -replace '\$\{[^}]*\}', '1' -replace '\{[^}]*\}', '1'
    if ($p -notmatch '^/') { continue }
    $segs = @($p.TrimStart('/') -split '/' | Where-Object { $_ -ne '' })
    if ($segs.Count -eq 0) { continue }
    $pfx = @()
    foreach ($s in $segs) { if ($s -match '^\d+$') { break }; $pfx += $s }
    if ($pfx.Count -eq 0) { continue }
    $key = '/' + ($pfx -join '/')
    if (-not $stat.ContainsKey($key)) { $stat[$key] = [pscustomobject]@{ W = 0; R = 0; Sample = $f.Name } }
    if ($m.Groups['m'].Value -eq 'get') { $stat[$key].R++ } else { $stat[$key].W++ }
  }
}

# prefixes registered in URL_DOMAIN (parse the literal '\/seg' sequences) + textual fallback
$registered = @()
foreach ($m in [regex]::Matches($df, '\^\\\/((?:[A-Za-z0-9\-]+\\\/)*[A-Za-z0-9\-]+)')) {
  $registered += '/' + ($m.Groups[1].Value -replace '\\\/', '/')
}
# ALL_TRIGGERS in dataFreshness.ts: writing these invalidates every domain, so they are "covered" too.
$registered += '/company/switch', '/system/import-data', '/system/clear-company-data'

$covered = @(); $uncovered = @()
foreach ($k in ($stat.Keys | Sort-Object)) {
  $hit = $false
  foreach ($r in $registered) { if ($k -eq $r -or $k.StartsWith($r + '/')) { $hit = $true; break } }
  if (-not $hit) { $esc = $k -replace '/', '\/'; if ($df.Contains($esc)) { $hit = $true } }
  if ($hit) { $covered += $k } else { $uncovered += $k }
}

Write-Host ('prefixes in code = ' + $stat.Count + '  (covered=' + $covered.Count + ' uncovered=' + $uncovered.Count + ')')
Write-Host ''
Write-Host '=== UNCOVERED with WRITES (real gaps - list refresh will be lost) ==='
foreach ($k in ($uncovered | Sort-Object { -$stat[$_].W })) {
  if ($stat[$k].W -gt 0) { Write-Host ('  ' + $k.PadRight(36) + ' writes=' + $stat[$k].W + ' reads=' + $stat[$k].R + '  e.g. ' + $stat[$k].Sample) }
}
Write-Host ''
Write-Host '=== UNCOVERED reads-only (likely alias prefixes - review) ==='
foreach ($k in ($uncovered | Sort-Object)) {
  if ($stat[$k].W -eq 0) { Write-Host ('  ' + $k.PadRight(36) + ' reads=' + $stat[$k].R + '  e.g. ' + $stat[$k].Sample) }
}
