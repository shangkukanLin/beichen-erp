# ASCII-only. P2-d: writer side of the dirty-flag mechanism.
#   sessionStorage.setItem(SALE_ORDER_DIRTY_KEY, '1')   =>   invalidate('saleOrder')
# Why replace instead of delete: the request layer already invalidates the domain for every write
# that goes through axios AND matches URL_DOMAIN, but a write whose URL is not (yet) mapped would
# silently lose its refresh signal if we simply removed the line. invalidate() bumps the domain
# version directly, so the signal is preserved independently of the URL table.
# Idempotent, encoding-aware, dry-run by default. Leaves the DIRTY_KEY imports in place (they become
# unused for files that have no list-page consumer - harmless, and easy to clean up later).
#
# Usage:  powershell -File migrate-dirty-writers.ps1            # dry run
#         powershell -File migrate-dirty-writers.ps1 -Apply     # write
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$root = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src'
$importLine = "import { invalidate } from '@/utils/dataFreshness'"
$importBoth = "import { invalidate, useDomainRefresh } from '@/utils/dataFreshness'"
$importOld = "import { useDomainRefresh } from '@/utils/dataFreshness'"

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

$rx = [regex]"sessionStorage\.setItem\(\s*(?<key>[A-Z_]+DIRTY_KEY)\s*,\s*['\`"]1['\`"]\s*\)"

function ImportInsertIndex([string[]]$lines) {
  $lastFrom = -1; $inImport = $false
  # NOTE: the window used to be 150 lines, but several files keep their <script setup> far below a
  # long <template> (e.g. inventory/product-stock has it at line 110, others 200+), which made the
  # function return -1 and the file get skipped. Scan the whole file instead.
  $lim = $lines.Count - 1
  for ($i = 0; $i -le $lim; $i++) {
    $t = $lines[$i].Trim()
    if (-not $inImport) {
      if ($t -match '^import\b') {
        if ($t -match "from\s+'[^']+'\s*;?\s*$" -or $t -match "^import\s+'") { $lastFrom = $i }
        else { $inImport = $true }
      }
      elseif ($lastFrom -ge 0 -and $t -ne '') { break }
    }
    else { if ($t -match "from\s+'[^']+'\s*;?\s*$") { $lastFrom = $i; $inImport = $false } }
  }
  return $lastFrom
}

$plan = New-Object System.Collections.Generic.List[object]
foreach ($f in (Get-ChildItem $root -Recurse -Include '*.vue', '*.ts' -File)) {
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
      Unknown = ($unknown -join ','); Text = $text
    })
}

$total = ($plan | Measure-Object -Property Hits -Sum).Sum
Write-Host ('files = ' + $plan.Count + ' ; writer sites = ' + $total)
foreach ($p in $plan) {
  $flag = if ($p.Unknown) { '  <-- UNMAPPED: ' + $p.Unknown } else { '' }
  Write-Host ('  ' + $p.Name.PadRight(30) + ' x' + $p.Hits + $flag)
}
if (-not $Apply) { Write-Host ''; Write-Host 'DRY RUN only. Re-run with -Apply to write.'; exit 0 }

$changed = 0; $failed = 0; $replaced = 0
foreach ($p in $plan) {
  if ($p.Unknown) { Write-Host ('  SKIP (unmapped): ' + $p.Name); $failed++; continue }
  $n = $rx.Matches($p.Text).Count
  $new = $rx.Replace($p.Text, { param($m) "invalidate('" + $map[$m.Groups['key'].Value] + "')" })
  # import: merge into the existing dataFreshness import when present
  if ($new -match [regex]::Escape($importBoth)) { }
  elseif ($new -match [regex]::Escape($importOld)) { $new = $new -replace [regex]::Escape($importOld), $importBoth }
  elseif ($new -notmatch [regex]::Escape($importLine)) {
    $lines = $new -split "`r`n"
    $at = ImportInsertIndex $lines
    if ($at -lt 0) { Write-Host ('  SKIP (no import region): ' + $p.Name); $failed++; continue }
    $new = (@($lines[0..$at]) + @($importLine) + @($lines[($at + 1)..($lines.Count - 1)])) -join "`r`n"
  }
  if ($rx.IsMatch($new)) { Write-Host ('  FAIL (writer remains): ' + $p.Name); $failed++; continue }
  [System.IO.File]::WriteAllText($p.Path, $new, (New-Object System.Text.UTF8Encoding($p.HasBom)))
  $changed++; $replaced += $n
  Write-Host ('  migrated ' + $p.Name.PadRight(30) + ' x' + $n)
}
Write-Host ''
Write-Host ('files migrated = ' + $changed + ' ; sites replaced = ' + $replaced + ' ; skipped/failed = ' + $failed)
