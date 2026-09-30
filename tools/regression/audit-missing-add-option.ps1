# ASCII-only. audit v4: which dropdowns are MISSING a "+ add new" entry.
# The user hit this on the sale-order "collection account" field, which is a *native* el-select fed by
# getAccountPage() - so the earlier RemoteSelect-only audit could not see it. This script covers both:
#   P1: <RemoteSelect> without ADD_MARKER (grouped by fetch function)
#   P2: native <el-select v-for=...> backed by a REMOTE array (ref([]) assigned from request/api call)
#       and without ADD_MARKER
# It only reports; classification (master-data vs enum/constant) is done by the reader.
$src = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src'
$files = Get-ChildItem $src -Recurse -Include '*.vue' -File

# ---------------- P1 ----------------
$rows = @{}
$totRS = 0; $addRS = 0
foreach ($f in $files) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  foreach ($m in [regex]::Matches($t, '<RemoteSelect([\s\S]{0,2500}?)(/>|</RemoteSelect>)')) {
    $totRS++
    $a = $m.Groups[1].Value
    # add-route = the component renders a "+ add new" footer entry, so that counts as handled too
    if ($a -match 'ADD_MARKER' -or $a -match 'add-route') { $addRS++; continue }
    $fx = ''
    $fm = [regex]::Match($a, ':fetch="([^"]+)"'); if ($fm.Success) { $fx = $fm.Groups[1].Value }
    $ids = @([regex]::Matches($fx, '[A-Za-z_][A-Za-z0-9_]*') | ForEach-Object { $_.Value } | Where-Object {
        $_.Length -gt 3 -and $_ -notin @('kw', 'string', 'number', 'undefined', 'null', 'true', 'false', 'pageSize', 'name', 'value', 'type', 'items', 'params')
      })
    $key = ''
    if ($ids.Count -gt 0) { $key = $ids[-1] } else { $key = ($fx -replace '\s+', ' ') }
    if ($key.Length -gt 46) { $key = $key.Substring(0, 46) }
    if (-not $rows.ContainsKey($key)) { $rows[$key] = @{ N = 0; First = ''; Sample = $fx } }
    $rows[$key].N++
    if (-not $rows[$key].First) { $rows[$key].First = ($f.FullName.Substring($src.Length + 1) + ':' + (($t.Substring(0, $m.Index) -split "`n").Count)) }
  }
}
Write-Host ('P1. RemoteSelect total=' + $totRS + '  with-add=' + $addRS + '  WITHOUT=' + ($totRS - $addRS) + '  distinct-fetch=' + $rows.Count)
foreach ($k in ($rows.Keys | Sort-Object { -$rows[$_].N })) {
  $s = $rows[$k].Sample; if ($s.Length -gt 66) { $s = $s.Substring(0, 66) }
  Write-Host ('   x' + $rows[$k].N.ToString().PadRight(3) + $k.PadRight(22) + $rows[$k].First)
  Write-Host ('        fetch: ' + $s)
}

# ---------------- P2 ----------------
Write-Host ''
Write-Host 'P2. native el-select backed by a REMOTE array, WITHOUT add entry:'
$n2 = 0
foreach ($f in $files) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  if ($t -notmatch '<el-select') { continue }
  $arrs = @{}
  foreach ($m in [regex]::Matches($t, '(?:const|let)\s+([A-Za-z0-9_]+)\s*=\s*(?:ref|reactive)(?:<[^>]*>)?\(\s*\[\s*\]')) {
    $nm = $m.Groups[1].Value
    $as = [regex]::Match($t, [regex]::Escape($nm) + '\.value\s*=[^\r\n]{0,200}')
    if ($as.Success -and $as.Value -match 'await|request\.|Api\(|get[A-Z]|list[A-Z]|fetch|page\(') {
      $arrs[$nm] = ($as.Value -replace '\s+', ' ')
    }
  }
  if ($arrs.Count -eq 0) { continue }
  $seen = @{}
  foreach ($m in [regex]::Matches($t, '<el-select([\s\S]{0,1500}?)</el-select>')) {
    $blk = $m.Groups[1].Value
    $vf = [regex]::Match($blk, 'v-for="[^"]*\bin\s+([A-Za-z0-9_]+)"')
    if (-not $vf.Success) { continue }
    $nm = $vf.Groups[1].Value
    if (-not $arrs.ContainsKey($nm)) { continue }
    if ($blk -match 'ADD_MARKER') { continue }
    $k = $f.Name + '|' + $nm
    if ($seen.ContainsKey($k)) { continue }
    $seen[$k] = 1
    $n2++
    Write-Host ('   ' + $f.FullName.Substring($src.Length + 1) + '  arr=' + $nm + '  sel-line=' + (($t.Substring(0, $m.Index) -split "`n").Count))
    $s = $arrs[$nm]; if ($s.Length -gt 100) { $s = $s.Substring(0, 100) }
    Write-Host ('        ' + $s)
  }
}
Write-Host ('   total=' + $n2)
