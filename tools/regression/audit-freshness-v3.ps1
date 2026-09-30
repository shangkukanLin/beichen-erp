# ASCII-only. Freshness coverage audit v3 (read-only).
# Beyond "is the write prefix registered", this checks the whole freshness chain:
#   A. domain set vs producers (URL_DOMAIN) vs consumers (domain="x" / useDomainRefresh)
#      A1 dead domains: declared but nothing ever bumps them
#      A2 typos: referenced somewhere but not declared
#      A3 nobody-listens: bumped but no consumer
#   B. <RemoteSelect> tags lacking domain="..." (their session cache can never be invalidated)
#   C. list pages (views/**/index.vue) with no refresh strategy at all
#   D. writes that bypass the request wrapper / other caches
$web = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web'
$src = Join-Path $web 'src'
$df = [System.IO.File]::ReadAllText((Join-Path $src 'utils\dataFreshness.ts'))

function LnOf([string]$t, [int]$i) { return (($t.Substring(0, $i) -split "`n").Count) }

$files = Get-ChildItem $src -Recurse -Include '*.vue', '*.ts', '*.tsx' -File

# ---- A ----
$doms = @{}
foreach ($m in [regex]::Matches($df, "export type Domain\s*=([\s\S]*?)\r?\n\r?\n")) {
  foreach ($d in [regex]::Matches($m.Groups[1].Value, "'([A-Za-z0-9_]+)'")) { $doms[$d.Groups[1].Value] = 1 }
}
foreach ($m in [regex]::Matches($df, "ALL_DOMAINS[^=]*=\s*\[([^\]]*)\]")) {
  foreach ($d in [regex]::Matches($m.Groups[1].Value, "'([A-Za-z0-9_]+)'")) { $doms[$d.Groups[1].Value] = 1 }
}
$prods = @{}
foreach ($m in [regex]::Matches($df, ",\s*'([A-Za-z0-9_]+)'\]")) {
  # WRITE_METHODS = ['post','put','delete','patch'] would otherwise be mistaken for an undeclared domain
  if ($m.Groups[1].Value -ne 'patch') { $prods[$m.Groups[1].Value] = 1 }
}
# DOMAIN_DEPS keys also count as "known" domains
foreach ($m in [regex]::Matches($df, "^\s{2}([A-Za-z0-9_]+):\s*\[", 'Multiline')) { $prods[$m.Groups[1].Value] = 1 }

$cons = @{}
$consAt = @{}
foreach ($f in $files) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  foreach ($m in [regex]::Matches($t, 'domain="([A-Za-z0-9_]+)"')) {
    $cons[$m.Groups[1].Value] = 1
    if (-not $consAt.ContainsKey($m.Groups[1].Value)) { $consAt[$m.Groups[1].Value] = @() }
    $consAt[$m.Groups[1].Value] += ($f.Name + ':' + (LnOf $t $m.Index))
  }
  foreach ($m in [regex]::Matches($t, "useDomainRefresh\(\s*'([A-Za-z0-9_]+)'")) { $cons[$m.Groups[1].Value] = 1 }
  foreach ($m in [regex]::Matches($t, "createDomainWatcher\(\s*'([A-Za-z0-9_]+)'")) { $cons[$m.Groups[1].Value] = 1 }
}

Write-Host ('A. declared=' + $doms.Count + ' producers=' + $prods.Count + ' consumers=' + $cons.Count)
Write-Host 'A1. declared but NO producer (never bumped => stale forever):'
foreach ($d in ($doms.Keys | Sort-Object)) {
  if (-not $prods.ContainsKey($d)) {
    $w = ''
    if ($consAt.ContainsKey($d)) { $w = '  <== HAS CONSUMER: ' + (($consAt[$d] | Select-Object -First 4) -join ', ') }
    Write-Host ('    ' + $d.PadRight(24) + $w)
  }
}
Write-Host 'A2. referenced but NOT declared (typo):'
foreach ($k in (($cons.Keys + $prods.Keys) | Sort-Object -Unique)) {
  if (-not $doms.ContainsKey($k)) { Write-Host ('    ' + $k) }
}
Write-Host 'A3. producer but NO consumer (bumped, nobody listens):'
foreach ($d in ($prods.Keys | Sort-Object)) { if (-not $cons.ContainsKey($d) -and $doms.ContainsKey($d)) { Write-Host ('    ' + $d) } }

# ---- B ----
Write-Host 'B. RemoteSelect: total / without domain='
$tot = 0; $bad = @()
foreach ($f in $files) {
  if ($f.Extension -ne '.vue') { continue }
  $t = [System.IO.File]::ReadAllText($f.FullName)
  foreach ($m in [regex]::Matches($t, '<RemoteSelect([\s\S]{0,1500}?)(/>|</RemoteSelect>)')) {
    $tot++
    $a = $m.Groups[1].Value
    if ($a -notmatch 'domain=') {
      $fx = ''
      $fm = [regex]::Match($a, ':fetch="([^"]+)"'); if ($fm.Success) { $fx = $fm.Groups[1].Value }
      $bad += ($f.Name + ':' + (LnOf $t $m.Index) + '  fetch=' + $fx)
    }
  }
}
Write-Host ('    total=' + $tot + ' without-domain=' + $bad.Count)
$bad | ForEach-Object { Write-Host ('    ' + $_) }

# ---- C ----
Write-Host 'C. views/**/index.vue with NO refresh strategy:'
$no = @()
foreach ($f in (Get-ChildItem (Join-Path $src 'views') -Recurse -Filter 'index.vue' -File)) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  if ($t -notmatch 'useDomainRefresh' -and $t -notmatch 'onActivated' -and $t -notmatch 'createDomainWatcher') {
    $no += $f.FullName.Substring($src.Length + 1)
  }
}
Write-Host ('    count=' + $no.Count)
$no | ForEach-Object { Write-Host ('    ' + $_) }

# ---- D ----
Write-Host 'D. writes bypassing request wrapper / other caches:'
$hits = @()
foreach ($f in $files) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  foreach ($m in [regex]::Matches($t, "method\s*:\s*['\x22](post|put|delete|patch)['\x22]")) { $hits += ($f.Name + ':' + (LnOf $t $m.Index) + '  ' + $m.Value) }
  foreach ($m in [regex]::Matches($t, "axios\.[a-z]+\(")) { $hits += ($f.Name + ':' + (LnOf $t $m.Index) + '  ' + $m.Value) }
  foreach ($m in [regex]::Matches($t, "sessionStorage\.(get|set)Item\(\s*['\x22]([^'\x22]+)")) { $hits += ($f.Name + ':' + (LnOf $t $m.Index) + '  ' + $m.Value) }
}
$hits = $hits | Sort-Object -Unique
Write-Host ('    hits=' + $hits.Count)
$hits | Select-Object -First 25 | ForEach-Object { Write-Host ('    ' + $_) }
