# ASCII-only. audit v5 - widen the net. The v4 audit that drove the P1/P2 fixes had three blind spots
# that could easily hide remaining misses:
#   1) :fetch that is NOT a plain identifier (inline arrow fn / property access). v4 guessed the fetch
#      name by "last identifier", which can produce garbage keys and hide whole families of dropdowns.
#   2) native el-select whose array is filled via `res.records` (exactly the sale-order account case):
#      the assignment line contains no request/await keyword, so v4 skipped it.
#   3) arrays wrapped in computed(), e.g. accountOptions = computed(() => accounts.value.filter(...)):
#      v4 only looked for ref([])/reactive([]) and never traced the real source.
# Output
#   P1 RemoteSelect with a NON-TRIVIAL :fetch (manual review)
#   P2 native el-select backed by a remote array (traced through computed) WITHOUT an add entry
#   P3 summary
$src = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src'
$files = Get-ChildItem $src -Recurse -Include '*.vue' -File
$idRe = '^[A-Za-z_][A-Za-z0-9_]*$'
$remoteRe = 'await|request\.|Api\(|get[A-Z]|list[A-Z]|fetch|\.records|res\b|axios'

Write-Host 'P1. RemoteSelect whose :fetch is not a plain identifier:'
$p1 = 0
foreach ($f in $files) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  foreach ($m in [regex]::Matches($t, '<RemoteSelect([\s\S]{0,2500}?)(/>|</RemoteSelect>)')) {
    $a = $m.Groups[1].Value
    $fm = [regex]::Match($a, ':fetch="([^"]+)"')
    if (-not $fm.Success) { continue }
    $fx = $fm.Groups[1].Value.Trim()
    if ($fx -match $idRe) { continue }
    $p1++
    $line = (($t.Substring(0, $m.Index) -split "`n").Count)
    $hasAdd = ($a -match 'ADD_MARKER' -or $a -match 'add-route')
    $s = $fx; if ($s.Length -gt 78) { $s = $s.Substring(0, 78) }
    Write-Host ('   ' + ($f.FullName.Substring($src.Length + 1) + ':' + $line).PadRight(52) + 'add=' + $hasAdd.ToString().PadRight(6) + $s)
  }
}
Write-Host ('   total=' + $p1)

Write-Host ''
Write-Host 'P2. native el-select on a REMOTE array (computed traced one level), WITHOUT add entry:'
$p2 = 0
foreach ($f in $files) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  if ($t -notmatch '<el-select') { continue }
  $refs = @{}
  foreach ($m in [regex]::Matches($t, '(?:const|let)\s+([A-Za-z0-9_]+)\s*=\s*(?:ref|reactive)(?:<[\s\S]{0,80}?>)?\(\s*\[\s*\]')) { $refs[$m.Groups[1].Value] = 1 }
  $comp = @{}
  foreach ($m in [regex]::Matches($t, '(?:const|let)\s+([A-Za-z0-9_]+)\s*=\s*computed\(([\s\S]{0,300}?)\)\s*\r?\n')) {
    $inner = @([regex]::Matches($m.Groups[2].Value, '([A-Za-z0-9_]+)\.value') | ForEach-Object { $_.Groups[1].Value })
    $comp[$m.Groups[1].Value] = $inner
  }
  $remote = @{}
  foreach ($nm in $refs.Keys) {
    $as = [regex]::Match($t, [regex]::Escape($nm) + '\.value\s*=[^\r\n]{0,220}')
    if ($as.Success -and $as.Value -match $remoteRe) { $remote[$nm] = ($as.Value -replace '\s+', ' ') }
  }
  foreach ($nm in $comp.Keys) {
    foreach ($r in $comp[$nm]) { if ($remote.ContainsKey($r)) { $remote[$nm] = 'computed-from ' + $r; break } }
  }
  if ($remote.Count -eq 0) { continue }
  $seen = @{}
  foreach ($m in [regex]::Matches($t, '<el-select([\s\S]{0,1500}?)</el-select>')) {
    $blk = $m.Groups[1].Value
    $vf = [regex]::Match($blk, 'v-for="[^"]*\bin\s+([A-Za-z0-9_]+)"')
    if (-not $vf.Success) { continue }
    $nm = $vf.Groups[1].Value
    if (-not $remote.ContainsKey($nm)) { continue }
    # '#footer' must count as handled as well - the fix uses el-select's footer slot, which has no
    # ADD_MARKER value and no add-route attribute (RemoteSelect-only prop).
    if ($blk -match 'ADD_MARKER' -or $blk -match 'add-route' -or $blk -match '#footer') { continue }
    $k = $f.Name + '|' + $nm
    if ($seen.ContainsKey($k)) { continue }
    $seen[$k] = 1
    $p2++
    $line = (($t.Substring(0, $m.Index) -split "`n").Count)
    Write-Host ('   ' + ($f.FullName.Substring($src.Length + 1) + ':' + $line).PadRight(52) + 'arr=' + $nm)
    $s = $remote[$nm]; if ($s.Length -gt 90) { $s = $s.Substring(0, 90) }
    Write-Host ('        ' + $s)
  }
}
Write-Host ('   total=' + $p2)
