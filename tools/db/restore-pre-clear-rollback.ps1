# =====================================================================================
# Restore company data from a ClearController rollback point.
#
# Why not POST /api/system/import-data: that endpoint REPLACES the whole database and
# CLEARS every table that is not in the file ("clearedNotInBackup"). A clear-rollback
# file only contains the 100 company-scoped business tables - sys_* / screen_model are
# NOT in it, so importing would wipe users/menus/roles/screen-model.
# -> restore directly with SQL instead (FK checks off, single transaction, per-table counts).
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\restore-pre-clear-rollback.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\restore-pre-clear-rollback.ps1 -File <path.json> -DryRun
# =====================================================================================
param(
    [string]$File = '',
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$db = 'beichen_erp'
$dir = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\uploads\clear-backup\company-1'

if (-not $File) {
    # pick the newest rollback that still contains real content (the later tiny ones are post-wipe dryRuns)
    $cand = Get-ChildItem $dir -Filter 'pre_clear_company_rollback-*.json' | Where-Object { $_.Length -gt 1MB } | Sort-Object LastWriteTime -Descending
    if (-not $cand) { throw ('no pre-clear rollback file (>1MB) found in ' + $dir) }
    $File = $cand[0].FullName
}
Write-Host ('restore source: ' + $File)
$json = [System.IO.File]::ReadAllText($File, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
Write-Host ('  exportInfo: ' + ($json.exportInfo | ConvertTo-Json -Compress))

function SqlQuote($v) {
    if ($null -eq $v) { return 'NULL' }
    if ($v -is [bool]) { if ($v) { return '1' } else { return '0' } }
    if ($v -is [int] -or $v -is [long] -or $v -is [double] -or $v -is [decimal]) {
        $d = [double]$v
        if ([math]::Floor($d) -eq $d -and [math]::Abs($d) -lt 1e15) { return ([long]$d).ToString() }
        return $d.ToString([System.Globalization.CultureInfo]::InvariantCulture)
    }
    $s = [string]$v
    return "'" + ($s -replace '\\', '\\\\' -replace "'", "''") + "'"
}

$tables = $json.tables.PSObject.Properties.Name
Write-Host ('tables in file: ' + $tables.Count)

# Generated columns must not appear in the INSERT column list (MySQL 3105).
# finance_invoice.active_invoice_no is one - it aborted the whole transaction on the first run.
# NOTE: match ONLY 'VIRTUAL/STORED GENERATED' - the earlier '%GENERATED%' also swallowed
# 'DEFAULT_GENERATED' (every create_time/update_time with DEFAULT CURRENT_TIMESTAMP), which
# silently rewrote all timestamps to "now" instead of restoring the backup values.
$genCols = @{}
foreach ($row in @(& $MYSQL --default-character-set=utf8mb4 -uroot -N -B -D $db -e "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='$db' AND (extra LIKE '%VIRTUAL GENERATED%' OR extra LIKE '%STORED GENERATED%')" 2>$null)) {
    $p = "$row" -split "`t"
    if ($p.Count -ge 2) { $genCols[($p[0] + '.' + $p[1])] = 1 }
}
Write-Host ('generated columns excluded: ' + $genCols.Count + ' (' + (($genCols.Keys) -join ', ') + ')')
$sql = New-Object System.Collections.Generic.List[string]
$sql.Add('SET NAMES utf8mb4;')
$sql.Add('SET FOREIGN_KEY_CHECKS=0;')
$sql.Add('START TRANSACTION;')
$planned = @{}
foreach ($t in $tables) {
    $rows = @($json.tables.$t)
    if ($rows.Count -eq 0) { continue }
    $cols = @($rows[0].PSObject.Properties.Name | Where-Object { -not $genCols.ContainsKey($t + '.' + $_) })
    $colList = ($cols | ForEach-Object { '`' + $_ + '`' }) -join ', '
    # replace-in-place: the business tables are empty anyway; seed tables get reset to the backup state
    $sql.Add('DELETE FROM `' + $t + '`;')
    $chunk = 500
    for ($i = 0; $i -lt $rows.Count; $i += $chunk) {
        $vals = New-Object System.Collections.Generic.List[string]
        for ($r = $i; $r -lt [Math]::Min($i + $chunk, $rows.Count); $r++) {
            $tuple = ($cols | ForEach-Object { SqlQuote $rows[$r].$_ }) -join ', '
            $vals.Add('(' + $tuple + ')')
        }
        $sql.Add('INSERT INTO `' + $t + '` (' + $colList + ') VALUES ' + ($vals -join ',') + ';')
    }
    $planned[$t] = $rows.Count
}
$sql.Add('COMMIT;')
$sql.Add('SET FOREIGN_KEY_CHECKS=1;')

$total = ($planned.Values | Measure-Object -Sum).Sum
Write-Host ('rows to restore: ' + $total + ' across ' + $planned.Keys.Count + ' tables')
$planned.GetEnumerator() | Sort-Object Name | Select-Object -First 12 | ForEach-Object { Write-Host ('   ' + $_.Key.PadRight(38) + $_.Value) }

if ($DryRun) {
    $out = Join-Path $env:TEMP ('restore-pre-clear-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
    [System.IO.File]::WriteAllLines($out, $sql, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ('DRY RUN: sql written to ' + $out + ' (' + $sql.Count + ' statements) - nothing executed')
    exit 0
}

# The wiped business tables are empty, but a few tables in the file are SEED tables that the
# startup seeder re-populated right after the wipe (material_type / dev_phase_template /
# outsource_contract_template / sys_operation_log ...). Restoring faithfully therefore means
# "DELETE the target table, then insert the backup rows" - which is also idempotent, so the
# script can be re-run safely.
$nonEmpty = @()
foreach ($t in $planned.Keys) {
    $n = @(& $MYSQL --default-character-set=utf8mb4 -uroot -N -B -D $db -e ('SELECT COUNT(*) FROM `' + $t + '`') 2>$null) | Select-Object -First 1
    if ([int]$n -gt 0) { $nonEmpty += ($t + '=' + $n) }
}
if ($nonEmpty.Count -gt 0) {
    Write-Host ('note: ' + $nonEmpty.Count + ' target table(s) already hold rows and will be REPLACED (seed tables re-populated after the wipe):')
    $nonEmpty | Select-Object -First 8 | ForEach-Object { Write-Host ('   ' + $_) }
}
# DELETE runs inside the same transaction as the INSERTs (see the statement list below).

$path = Join-Path $env:TEMP ('restore-pre-clear-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
[System.IO.File]::WriteAllLines($path, $sql, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ('sql file: ' + $path + ' (' + [Math]::Round((Get-Item $path).Length / 1MB, 2) + ' MB, ' + $sql.Count + ' statements)')
$out = & cmd /c "`"$MYSQL`" --default-character-set=utf8mb4 -uroot $db < `"$path`" 2>&1"
$errs = @($out | Where-Object { $_ -match '(?i)ERROR' })
Write-Host ('load errors: ' + $errs.Count)
$errs | Select-Object -First 5 | ForEach-Object { Write-Host ('   ' + $_) }

Write-Host 'verification (restored vs backup plan):'
$bad = 0
foreach ($t in $planned.Keys) {
    $n = [int](@(& $MYSQL --default-character-set=utf8mb4 -uroot -N -B -D $db -e ('SELECT COUNT(*) FROM `' + $t + '`') 2>$null) | Select-Object -First 1)
    if ($n -ne $planned[$t]) { $bad++; Write-Host ('   MISMATCH ' + $t.PadRight(38) + 'now=' + $n + ' expected=' + $planned[$t]) }
}
Write-Host ('table count mismatches: ' + $bad)
if ($bad -eq 0 -and $errs.Count -eq 0) { Write-Host 'RESULT: restore OK' } else { Write-Host 'RESULT: restore INCOMPLETE - inspect above'; exit 1 }
