# ASCII-only. P3-1: coverage audit for URL_DOMAIN.
# Since P2-d the refresh signal depends ONLY on the bus (writers no longer set the legacy keys), so a
# write endpoint whose URL is not mapped to a domain silently loses its "list should refresh" signal.
# This script scans every request.post/put/delete path in the web app and reports the path prefixes
# that dataFreshness.ts does NOT mention.
#
# The check is deliberately textual: it looks for the escaped prefix ("\/sale\/order") inside
# dataFreshness.ts. Anything flagged must be reviewed by hand (the table may express a prefix with an
# optional group, e.g. /^\/(inventory\/)?warehouse(\/|$)/, which a naive lookup misses).
$web = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web'
$dfPath = Join-Path $web 'src\utils\dataFreshness.ts'
$df = [System.IO.File]::ReadAllText($dfPath)

$rx = [regex]'request\.(post|put|delete)\s*\(\s*(?<q>[\x27\x22\x60])(?<p>[^\x27\x22\x60]+)\k<q>'
$calls = @{}
$samples = @{}
foreach ($f in (Get-ChildItem (Join-Path $web 'src') -Recurse -Include '*.ts', '*.vue' -File)) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  foreach ($m in $rx.Matches($t)) {
    $p = $m.Groups['p'].Value -replace '\$\{[^}]*\}', '1' -replace '\{[^}]*\}', '1'
    if ($p -notmatch '^/') { continue }
    $segs = @($p.TrimStart('/') -split '/' | Where-Object { $_ -ne '' })
    if ($segs.Count -eq 0) { continue }
    # Take every leading segment up to the first pure-numeric one, so /brand/12 counts as /brand
    # (the previous fixed "first two segments" rule reported /brand/1 as uncovered - a false alarm).
    $pfx = @()
    foreach ($s in $segs) { if ($s -match '^\d+$') { break }; $pfx += $s }
    if ($pfx.Count -eq 0) { continue }
    $prefix = '/' + ($pfx -join '/')
    if (-not $calls.ContainsKey($prefix)) { $calls[$prefix] = 0; $samples[$prefix] = $f.Name }
    $calls[$prefix]++
  }
}

$covered = @(); $uncovered = @()
# Extract the prefixes the table actually registers (e.g. "^\/dev\/file" => "/dev/file") and match by
# PREFIX. The previous version required the whole prefix to appear literally, so a deeper endpoint
# like /dev/file/upload was reported uncovered even though /dev/file already covers it.
$registered = @()
foreach ($m in [regex]::Matches($df, '\^\\\/((?:[A-Za-z0-9\-]+\\\/)*[A-Za-z0-9\-]+)')) {
  $registered += '/' + ($m.Groups[1].Value -replace '\\\/', '/')
}
Write-Host ('registered prefixes parsed from dataFreshness.ts = ' + $registered.Count)
foreach ($k in ($calls.Keys | Sort-Object)) {
  $hit = $false
  foreach ($r in $registered) { if ($k -eq $r -or $k.StartsWith($r + '/')) { $hit = $true; break } }
  # fallback: some rows express the prefix with an optional group (e.g. /^\/(inventory\/)?warehouse(\/|$)/)
  # which the simple extractor above cannot parse; a plain textual hit is enough there.
  if (-not $hit) {
    $esc = $k -replace '/', '\/'
    if ($df.Contains($esc)) { $hit = $true }
  }
  if ($hit) { $covered += $k } else { $uncovered += $k }
}

Write-Host ('write-endpoint prefixes found = ' + $calls.Count + '  (covered=' + $covered.Count + ' uncovered=' + $uncovered.Count + ')')
Write-Host ''
Write-Host '=== NOT covered by URL_DOMAIN (review these) ==='
foreach ($k in $uncovered) { Write-Host ('  ' + $k.PadRight(34) + 'x' + $calls[$k] + '   e.g. ' + $samples[$k]) }
Write-Host ''
Write-Host '=== covered (sanity) ==='
Write-Host ('  ' + (($covered | Sort-Object) -join '  '))
