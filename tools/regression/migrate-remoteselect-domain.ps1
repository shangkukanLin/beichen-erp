# ASCII-only. P3: declare the data domain on every <RemoteSelect> so its session cache is
# invalidated when that domain is written elsewhere.
#
#   <RemoteSelect :fetch="fetchBrands" ... />  =>  <RemoteSelect :fetch="fetchBrands" domain="brand" ... />
#
# FIX (2nd attempt): the previous version matched tag attributes with `[^>]*?`, which terminates at
# the '>' inside an arrow function (e.g. :fetch="(kw: string) => fetchMaterialsByType(kw, t)") and
# therefore inserted `domain="..."` in the middle of the expression, breaking 55+ files with
# "TS1003 Identifier expected". Tags are now located by a char scan that is aware of quoted values.
#
# Usage:  powershell -File migrate-remoteselect-domain.ps1
#         powershell -File migrate-remoteselect-domain.ps1 -Apply
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$root = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src'

$rules = @(
  @{ re = 'materialType|matType|fetchTypes'; dom = 'materialType' },
  @{ re = 'fetchProjects|Project';          dom = 'devProject' },
  @{ re = 'fetchFactories|Factor';          dom = 'vendor' },
  @{ re = 'fetchPurchaseOrders|PurchaseOrder'; dom = 'purchaseOrder' },
  @{ re = 'fetchSaleOrders|SaleOrder';      dom = 'saleOrder' },
  @{ re = 'material|Material';              dom = 'material' },
  @{ re = 'warehouse|Warehouse';            dom = 'warehouse' },
  @{ re = 'brand|Brand';                    dom = 'brand' },
  @{ re = 'product|Product';                dom = 'product' },
  @{ re = 'customer|Customer';              dom = 'customer' },
  @{ re = 'supplier|Supplier';              dom = 'supplier' },
  @{ re = 'vendor|Vendor';                  dom = 'vendor' },
  @{ re = 'role|Role';                      dom = 'role' },
  @{ re = 'user|User';                      dom = 'user' }
)

# Locate <RemoteSelect ...> spans, honouring quoted attribute values (so '=>' inside a value is safe)
function Find-Tags([string]$text) {
  $out = New-Object System.Collections.Generic.List[object]
  $i = 0
  while ($true) {
    $i = $text.IndexOf('<RemoteSelect', $i)
    if ($i -lt 0) { break }
    $j = $i + 13
    $inQ = [char]0
    while ($j -lt $text.Length) {
      $c = $text[$j]
      if ($inQ -ne [char]0) { if ($c -eq $inQ) { $inQ = [char]0 } }
      elseif ($c -eq '"' -or $c -eq "'") { $inQ = $c }
      elseif ($c -eq '>') { break }
      $j++
    }
    if ($j -ge $text.Length) { break }
    $out.Add([pscustomobject]@{ Index = $i; Length = ($j - $i + 1) })
    $i = $j + 1
  }
  return $out
}

$rxFetch = [regex]':fetch="(?<f>[A-Za-z_$][\w$.]*)"'
$plan = New-Object System.Collections.Generic.List[object]
foreach ($f in (Get-ChildItem $root -Recurse -Filter '*.vue' -File)) {
  $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
  $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
  $off = if ($hasBom) { 3 } else { 0 }
  $text = (New-Object System.Text.UTF8Encoding($false)).GetString($bytes, $off, $bytes.Length - $off)
  $adds = New-Object System.Collections.Generic.List[object]
  $skips = New-Object System.Collections.Generic.List[string]
  foreach ($t in (Find-Tags $text)) {
    $tag = $text.Substring($t.Index, $t.Length)
    if ($tag -match '\bdomain=') { continue }
    $fm = $rxFetch.Match($tag)
    if (-not $fm.Success) { $skips.Add('no :fetch'); continue }
    $fn = $fm.Groups['f'].Value
    $dom = $null
    foreach ($r in $rules) { if ($fn -match $r.re) { $dom = $r.dom; break } }
    if (-not $dom) { $skips.Add($fn); continue }
    $adds.Add([pscustomobject]@{ Index = $t.Index; Length = $t.Length; Fetch = $fn; Domain = $dom })
  }
  if ($adds.Count -gt 0 -or $skips.Count -gt 0) {
    $plan.Add([pscustomobject]@{ Name = $f.Name; Path = $f.FullName; HasBom = $hasBom; Text = $text; Adds = $adds; Skips = @($skips | Sort-Object -Unique) })
  }
}

$totalAdds = 0; $totalSkips = 0
foreach ($p in $plan) { $totalAdds += $p.Adds.Count; $totalSkips += $p.Skips.Count }
Write-Host ('files = ' + $plan.Count + ' ; tags to annotate = ' + $totalAdds + ' ; unmapped tags = ' + $totalSkips)
if (-not $Apply) { Write-Host 'DRY RUN only. Re-run with -Apply to write.'; exit 0 }

$changed = 0
foreach ($p in $plan) {
  if ($p.Adds.Count -eq 0) { continue }
  $text = $p.Text
  foreach ($a in ($p.Adds | Sort-Object -Property Index -Descending)) {
    $tag = $text.Substring($a.Index, $a.Length)
    $m2 = [regex]::Match($tag, '^(?<body>[\s\S]*?)(?<close>/?>)$')
    if (-not $m2.Success) { continue }
    $newTag = $m2.Groups['body'].Value.TrimEnd() + ' domain="' + $a.Domain + '" ' + $m2.Groups['close'].Value
    $text = $text.Substring(0, $a.Index) + $newTag + $text.Substring($a.Index + $a.Length)
    # verify the rewritten tag is still balanced (same number of quotes as before + 2)
    $q1 = ($tag.ToCharArray() | Where-Object { $_ -eq '"' }).Count
    $q2 = ($newTag.ToCharArray() | Where-Object { $_ -eq '"' }).Count
    if ($q2 -ne ($q1 + 2)) { Write-Host ('  WARN quote mismatch in ' + $p.Name + ' (' + $q1 + ' -> ' + $q2 + ')') }
  }
  [System.IO.File]::WriteAllText($p.Path, $text, (New-Object System.Text.UTF8Encoding($p.HasBom)))
  $changed++
}
Write-Host ('files changed = ' + $changed)
