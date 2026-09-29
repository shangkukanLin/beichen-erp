# =====================================================================================
# F7-247 static guard (ratchet style) — "aggregate by source_bill_type but forget the status filter".
#
# Scan scope (agreed with the user): the finance module only.
#   beichen-erp-server/src/main/java/com/beichen/erp/finance/**/*.java
# A candidate = a line containing `source_bill_type` whose neighbourhood (that line +- 3 lines)
# never mentions `status`. Candidates are compared against a frozen baseline file, so the guard
# fails only when a NEW suspicious query appears (keeps today's already-reviewed ones quiet).
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-f7-247-status-filter.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-f7-247-status-filter.ps1 -UpdateBaseline
# ASCII-only on purpose (no BOM required).
# =====================================================================================
param([switch]$UpdateBaseline)

$repo = 'C:\Users\75629\CodeBuddy\20260710123705'
$fin = Join-Path $repo 'beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp\finance'
$baselineFile = Join-Path $repo 'beichen-erp\tools\regression\audit\f7-247-status-filter-baseline.txt'

$candidates = @()
Get-ChildItem -LiteralPath $fin -Recurse -Filter *.java | ForEach-Object {
    $rel = $_.FullName.Replace((Join-Path $repo 'beichen-erp') + '\', '')
    $lines = [System.IO.File]::ReadAllLines($_.FullName)
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -notmatch 'source_bill_type') { continue }
        # skip comments/javadoc (the first run's 13 "candidates" were all javadoc lines)
        $t = $line.Trim()
        if ($t.StartsWith('*') -or $t.StartsWith('//') -or $t.StartsWith('/*')) { continue }
        # only SQL-ish lines count (a real query, not prose)
        if ($line -notmatch '(?i)(select|from\s|count\(|@Select|group\s+by|where)') { continue }
        $lo = [Math]::Max(0, $i - 3)
        $hi = [Math]::Min($lines.Count - 1, $i + 3)
        $window = ($lines[$lo..$hi] -join ' ')
        if ($window -notmatch 'status') {
            $candidates += ($rel + ':' + ($i + 1) + '  ' + $line.Trim())
        }
    }
}
$candidates = @($candidates | Sort-Object -Unique)
Write-Host ('### candidate statements (source_bill_type without a nearby status filter) = ' + $candidates.Count)

if ($UpdateBaseline) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    $header = @('# F7-247 baseline (frozen ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ')',
        '# Each line = a finance-module statement that aggregates/filters by source_bill_type without a',
        '# status filter within +-3 lines. Review these once; new entries make the guard fail.')
    [System.IO.File]::WriteAllLines($baselineFile, ($header + $candidates), $enc)
    Write-Host ('### baseline written: ' + $baselineFile)
    $candidates | Select-Object -First 10 | ForEach-Object { Write-Host ('    ' + $_) }
    exit 0
}

if (-not (Test-Path -LiteralPath $baselineFile)) {
    Write-Host ('  FAIL  baseline missing -> run once with -UpdateBaseline after reviewing the list')
    Write-Host 'RESULT: 0 passed, 1 failed'
    exit 1
}
$baseline = @(Get-Content -LiteralPath $baselineFile -Encoding UTF8 | Where-Object { "$_" -notmatch '^#' -and "$_".Trim() -ne '' })
$new = @($candidates | Where-Object { $baseline -notcontains $_ })
$gone = @($baseline | Where-Object { $candidates -notcontains $_ })
Write-Host ('    baseline=' + $baseline.Count + ' | new=' + $new.Count + ' | no-longer-present=' + $gone.Count)
$new | ForEach-Object { Write-Host ('    NEW  ' + $_) }
$gone | Select-Object -First 5 | ForEach-Object { Write-Host ('    gone ' + $_) }
if ($new.Count -eq 0) {
    Write-Host '  PASS  no new source_bill_type aggregate without a status filter'
    Write-Host 'RESULT: 1 passed, 0 failed'
    exit 0
}
Write-Host '  FAIL  new candidate(s) found - review whether a status filter is required'
Write-Host ('RESULT: 0 passed, ' + $new.Count + ' failed')
exit 1
