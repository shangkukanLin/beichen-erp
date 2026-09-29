# =====================================================================================
# Normalise v-perm quoting to the project convention:  v-perm="'module:page'"  (single quotes
# INSIDE double quotes). Writing v-perm='module:page' makes vue-tsc fail with TS1003.
# ASCII-only (no BOM needed). Idempotent.
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\fix-vperm-quotes.ps1 [<dir> ...]
# =====================================================================================
param([string[]]$Dir)

if (-not $Dir -or $Dir.Count -eq 0) {
    $Dir = @('C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src\views')
}
$enc = New-Object System.Text.UTF8Encoding($false)
$fixed = 0; $scanned = 0
foreach ($d in $Dir) {
    Get-ChildItem -LiteralPath $d -Recurse -Filter *.vue | ForEach-Object {
        $scanned++
        $txt = [System.IO.File]::ReadAllText($_.FullName)
        $new = [regex]::Replace($txt, "v-perm='([^']+)'", 'v-perm="''$1''"')
        if ($new -ne $txt) {
            [System.IO.File]::WriteAllText($_.FullName, $new, $enc)
            $fixed++
            Write-Host ('  fixed  ' + $_.FullName)
        }
    }
}
Write-Host ('### scanned=' + $scanned + ' fixed=' + $fixed)
Write-Host '### remaining bad-quote occurrences (must be 0):'
$bad = 0
foreach ($d in $Dir) {
    $bad += @(Get-ChildItem -LiteralPath $d -Recurse -Filter *.vue | Select-String -Pattern "v-perm='").Count
}
Write-Host ('  ' + $bad)
