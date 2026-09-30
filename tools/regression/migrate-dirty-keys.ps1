# ASCII-only. P2 migration: replace the hand-written sessionStorage dirty-flag boilerplate in
# LIST pages with the data-freshness bus, WITHOUT touching the writer side (setItem) - the new call
# still honours the legacy key => zero behaviour change.
#
#   onActivated(() => {                                  useDomainRefresh('role', () => {
#     // note                                              // note (kept, re-indented)
#     if (sessionStorage.getItem(K) === '1') {     =>      loadData()
#       sessionStorage.removeItem(K)                     }, SYSTEM_ROLE_DIRTY_KEY)
#       loadData()
#     }
#   })
#
# FIXES after the first attempt broke 26 files (vue-tsc: "Identifier expected"):
#   1) the new import must go AFTER the whole top-level import region. The previous version
#      inserted it after the first line of a MULTI-LINE import (`import {\n  a,\n} from '...'`),
#      which is a syntax error.
#   2) always emit an arrow body. The previous version tried to pass a bare identifier when the
#      body looked like a single statement, but its regex matched `loadData()` (= a CALL) and
#      produced `useDomainRefresh('supplier', loadData(), KEY)`.
#
# Usage:  powershell -File migrate-dirty-keys.ps1            # dry run (plan only)
#         powershell -File migrate-dirty-keys.ps1 -Apply     # write + self-check
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$root = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src\views'
$importLine = "import { useDomainRefresh } from '@/utils/dataFreshness'"

$map = @{
  'SYSTEM_ROLE_DIRTY_KEY'               = 'role'
  'SYSTEM_USER_DIRTY_KEY'               = 'user'
  'SYSTEM_MENU_DIRTY_KEY'               = 'menu'
  'DEV_PROJECT_DIRTY_KEY'               = 'devProject'
  'DEV_MATERIAL_DIRTY_KEY'              = 'devMaterial'
  'SUPPLIER_DIRTY_KEY'                  = 'supplier'
  'SALE_ORDER_DIRTY_KEY'                = 'saleOrder'
  'SALE_RETURN_DIRTY_KEY'               = 'saleReturn'
  'SALE_EXCHANGE_DIRTY_KEY'             = 'saleExchange'
  'PURCHASE_ORDER_DIRTY_KEY'            = 'purchaseOrder'
  'PURCHASE_RETURN_DIRTY_KEY'           = 'purchaseReturn'
  'PURCHASE_EXCHANGE_DIRTY_KEY'         = 'purchaseExchange'
  'OUTSOURCE_ORDER_DIRTY_KEY'           = 'outsourceOrder'
  'OUTSOURCE_DELIVERY_DIRTY_KEY'        = 'outsourceDelivery'
  'OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY'  = 'materialOrder'
  'OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY' = 'outsourceMaterialReturn'
  'OUTSOURCE_RETURN_ORDER_DIRTY_KEY'    = 'outsourceReturnOrder'
  'OUTSOURCE_OTHER_IO_DIRTY_KEY'        = 'outsourceOtherIo'
  'OUTSOURCE_STOCK_LOSS_DIRTY_KEY'      = 'materialStockLoss'
  'INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY'  = 'warehouseMove'
  'INVENTORY_MATERIAL_MOVE_DIRTY_KEY'   = 'materialMove'
  'INVENTORY_OTHER_IO_DIRTY_KEY'        = 'inventoryOtherIo'
  'INVENTORY_RECLASSIFY_DIRTY_KEY'      = 'reclassify'
  'INVENTORY_RETURN_SORT_DIRTY_KEY'     = 'returnSort'
  'INVENTORY_STOCK_LOSS_DIRTY_KEY'      = 'stockLoss'
  'FINANCE_BILL_DIRTY_KEY'              = 'bill'
  'PAYABLE_TRANSFER_DIRTY_KEY'          = 'payableTransfer'
}

$rx = [regex]'(?m)^(?<ind>[ \t]*)onActivated\(\(\)\s*=>\s*\{\s*(?<note>(\r?\n[ \t]*//[^\r\n]*)*)\s*\r?\n[ \t]*if\s*\(\s*sessionStorage\.getItem\(\s*(?<key>[A-Z_]+DIRTY_KEY)\s*\)\s*===\s*.1.\s*\)\s*\{\s*\r?\n\s*sessionStorage\.removeItem\(\s*\k<key>\s*\)\s*\r?\n(?<body>[\s\S]*?)\r?\n[ \t]*\}\s*\r?\n[ \t]*\}\s*\)'

# find the line index AFTER which a new top-level import should be inserted
function ImportInsertIndex([string[]]$lines) {
  $lastFrom = -1
  $inImport = $false
  $lim = [Math]::Min(150, $lines.Count - 1)
  for ($i = 0; $i -le $lim; $i++) {
    $t = $lines[$i].Trim()
    if (-not $inImport) {
      if ($t -match '^import\b') {
        if ($t -match "from\s+'[^']+'\s*;?\s*$" -or $t -match "^import\s+'") { $lastFrom = $i }
        else { $inImport = $true }
      }
      elseif ($lastFrom -ge 0 -and $t -ne '') { break }
    }
    else {
      if ($t -match "from\s+'[^']+'\s*;?\s*$") { $lastFrom = $i; $inImport = $false }
    }
  }
  return $lastFrom
}

$plan = New-Object System.Collections.Generic.List[object]
foreach ($f in (Get-ChildItem $root -Recurse -Filter '*.vue' -File)) {
  $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
  $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
  $off = if ($hasBom) { 3 } else { 0 }
  $text = (New-Object System.Text.UTF8Encoding($false)).GetString($bytes, $off, $bytes.Length - $off)
  $hits = $rx.Matches($text)
  if ($hits.Count -eq 0) { continue }
  $unknown = @()
  foreach ($m in $hits) { if (-not $map.ContainsKey($m.Groups['key'].Value)) { $unknown += $m.Groups['key'].Value } }
  $plan.Add([pscustomobject]@{
      Name = $f.Name; Path = $f.FullName; Hits = $hits.Count; HasBom = $hasBom
      Unknown = ($unknown -join ','); Text = $text; Matches = $hits
    })
}

Write-Host ('files with legacy boilerplate = ' + $plan.Count + ' ; sites = ' + (($plan | Measure-Object -Property Hits -Sum).Sum))
foreach ($p in $plan) {
  $keys = ($p.Matches | ForEach-Object { $_.Groups['key'].Value } | Sort-Object -Unique) -join ','
  $flag = if ($p.Unknown) { '  <-- UNMAPPED: ' + $p.Unknown } else { '' }
  Write-Host ('  ' + $p.Name.PadRight(34) + ' x' + $p.Hits + '  ' + $keys + $flag)
}
if (-not $Apply) { Write-Host ''; Write-Host 'DRY RUN only. Re-run with -Apply to write.'; exit 0 }

$changed = 0; $failed = 0
foreach ($p in $plan) {
  if ($p.Unknown) { Write-Host ('  SKIP (unmapped): ' + $p.Name); $failed++; continue }
  $new = $rx.Replace($p.Text, {
      param($m)
      $ind = $m.Groups['ind'].Value
      $key = $m.Groups['key'].Value
      $dom = $map[$key]
      $note = $m.Groups['note'].Value.Trim("`r", "`n")
      $body = $m.Groups['body'].Value.TrimEnd()
      $code = $ind + "useDomainRefresh('" + $dom + "', () => {" + "`r`n" + $body + "`r`n" + $ind + "}, " + $key + ")"
      if ($note -ne '') { return ($ind + $note.TrimStart() + "`r`n" + $code) }
      return $code
    })
  if ($new -notmatch [regex]::Escape($importLine)) {
    $lines = $new -split "`r`n"
    $at = ImportInsertIndex $lines
    if ($at -lt 0) { Write-Host ('  SKIP (no import region): ' + $p.Name); $failed++; continue }
    $new = (@($lines[0..$at]) + @($importLine) + @($lines[($at + 1)..($lines.Count - 1)])) -join "`r`n"
  }
  if ($rx.IsMatch($new)) { Write-Host ('  FAIL (boilerplate remains): ' + $p.Name); $failed++; continue }
  [System.IO.File]::WriteAllText($p.Path, $new, (New-Object System.Text.UTF8Encoding($p.HasBom)))
  $changed++
  Write-Host ('  migrated ' + $p.Name.PadRight(34) + ' x' + $p.Hits)
}
Write-Host ''
Write-Host ('migrated files = ' + $changed + ' ; skipped/failed = ' + $failed)
