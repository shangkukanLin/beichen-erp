# =====================================================================================
# P2 (2026-09-30, pre-launch cleanup): delete the DB-upgrade / legacy-migration code
# from DataInitializer, now that schema.sql alone builds a complete fresh database
# (proven by tools/db/verify-schema-parity.ps1: fresh DB == live DB, 1538 columns / 110 tables).
#
# Removed: every migrate*Xxx() + initDocOperatorColumns() + addColumnIfMissing() and their
#          call lines inside init().  Kept: all seeders + columnExists() (used by assertSchemaReady).
#
# Hard-won details (both cost a wasted run):
#   * mark lines in a HashSet by index and rebuild the file - array element assignment +
#     "collapse blanks" silently produced a 0-line change.
#   * write with the ORIGINAL line ending; [IO.File]::WriteAllLines forces \r\n and turned
#     a clean delete into an 867-line diff.
#   * sanity guard: refuse to write if fewer than 500 lines are marked (a no-op must not pass).
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\strip-datainitializer-migrations.ps1
# =====================================================================================
$ErrorActionPreference = 'Stop'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$path = Join-Path $repo 'beichen-erp-server\src\main\java\com\beichen\erp\config\DataInitializer.java'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$eol = if ($raw.Contains("`r`n")) { "`r`n" } else { "`n" }
$lines = $raw -split "`r?`n"
$before = $lines.Count

$methods = @(
    'initDocOperatorColumns',
    'migrateStockLossLiableParty', 'migrateRepairReturnFee', 'migrateMaterialRepairReturnStatus',
    'migrateReturnOrderRepairStatus', 'migrateMaterialOrderFinisher', 'migrateDashboardTabs',
    'migrateUserMenuMode', 'migrateMaterialMoveQuality', 'migratePurchaseExchangeCharge',
    'migrateSaleItemCharge', 'backfillFirstItemCharge', 'migratePurchaseChargePerProduct',
    'migrateReturnBackSource', 'migrateMaterialRepairOnsiteLeg', 'migrateMaterialOrderReturnedQty',
    'migrateOverReceipt', 'migrateReturnBackPriceManual', 'migrateFinanceExpenseSource',
    'migrateReturnSortSorter', 'addColumnIfMissing'
)

function Get-MethodRange([string[]]$src, [string]$name) {
    for ($i = 0; $i -lt $src.Count; $i++) {
        if ($src[$i] -match ('^\s*(private|public|protected)\s+[\w<>\[\],\s]+\s+' + [regex]::Escape($name) + '\s*\(')) {
            $start = $i
            while ($start -gt 0) {
                $p = $src[$start - 1].Trim()
                if ($p -match '^(\*|/\*|\*/|//|@)') { $start-- } else { break }
            }
            $j = $i
            while ($j -lt $src.Count -and $src[$j] -notmatch '\{') { $j++ }
            if ($j -ge $src.Count) { return $null }
            $depth = 0; $inStr = $false; $inBlock = $false
            for ($k = $j; $k -lt $src.Count; $k++) {
                $line = $src[$k]
                for ($c = 0; $c -lt $line.Length; $c++) {
                    $ch = $line[$c]
                    if ($inStr) { if ($ch -eq '\') { $c++ } elseif ($ch -eq '"') { $inStr = $false }; continue }
                    if ($inBlock) { if ($ch -eq '*' -and ($c + 1) -lt $line.Length -and $line[$c + 1] -eq '/') { $inBlock = $false; $c++ }; continue }
                    if ($ch -eq '"') { $inStr = $true; continue }
                    if ($ch -eq '/' -and ($c + 1) -lt $line.Length -and $line[$c + 1] -eq '/') { break }
                    if ($ch -eq '/' -and ($c + 1) -lt $line.Length -and $line[$c + 1] -eq '*') { $inBlock = $true; $c++; continue }
                    if ($ch -eq '{') { $depth++ }
                    elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { return @($start, $k) } }
                }
            }
            return $null
        }
    }
    return $null
}

$mark = New-Object 'System.Collections.Generic.HashSet[int]'
$done = 0
foreach ($m in $methods) {
    $r = Get-MethodRange $lines $m
    if (-not $r) { Write-Host ('  SKIP   ' + $m.PadRight(34) + '(already gone)'); continue }
    for ($x = $r[0]; $x -le $r[1]; $x++) { [void]$mark.Add($x) }
    $done++
    Write-Host ('  remove ' + $m.PadRight(34) + ' lines ' + ($r[0] + 1) + '-' + ($r[1] + 1) + ' (' + ($r[1] - $r[0] + 1) + ')')
}

# call lines inside init()
$callRx = '^\s*(' + (($methods | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\s*\(\s*\)\s*;\s*$'
$calls = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
    if (-not $mark.Contains($i) -and $lines[$i] -match $callRx) { [void]$mark.Add($i); $calls++ }
}
Write-Host ('  removed method bodies = ' + $done + ' / ' + $methods.Count + ' ; init() call lines = ' + $calls)

if ($mark.Count -lt 400) { throw ('SANITY FAIL: only ' + $mark.Count + ' lines marked for deletion (expected >=400, measured 458) - refusing to write') }

$kept = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $lines.Count; $i++) { if (-not $mark.Contains($i)) { $kept.Add($lines[$i]) } }
$out = ($kept -join $eol)
if (-not $out.EndsWith($eol)) { $out += $eol }

# sanity on the result
foreach ($must in @('void init()', 'initCompany(', 'syncMenus(', 'initRoleMenuPlan(', 'assertSchemaReady(', 'columnExists(')) {
    if ($out -notmatch [regex]::Escape($must)) { throw ('SANITY FAIL: kept-code anchor missing after strip: ' + $must) }
}
if ($out -match '(?m)^\s*migrate[A-Z]\w*\s*\(') { throw 'SANITY FAIL: a migrate* call/method survived' }

[System.IO.File]::WriteAllText($path, $out, $utf8)
Write-Host ''
Write-Host ('lines: ' + $before + ' -> ' + $kept.Count + '  (removed ' + ($before - $kept.Count) + ')')
