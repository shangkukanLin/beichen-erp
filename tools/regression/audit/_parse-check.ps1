# =====================================================================================
# Tiny helper: report PowerShell parse errors of one or more scripts (ASCII only, so it
# can be saved without a BOM and run under PS 5.1 without mojibake).
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\_parse-check.ps1 -Path .\foo.ps1
# Exit code 0 = parses clean, 1 = has errors.
# =====================================================================================
param([Parameter(Mandatory = $true)][string[]]$Path)
$total = 0
foreach ($p in $Path) {
    if (-not (Test-Path -LiteralPath $p)) { Write-Host ("MISSING  " + $p); $total++; continue }
    $tok = $null; $errs = $null
    $full = (Resolve-Path -LiteralPath $p).Path
    [void][System.Management.Automation.Language.Parser]::ParseFile($full, [ref]$tok, [ref]$errs)
    if ($errs.Count -eq 0) {
        Write-Host ("PARSE OK  " + $p)
    }
    else {
        Write-Host ("PARSE FAIL  " + $p + "  (" + $errs.Count + " error(s))")
        $errs | Select-Object -First 8 | ForEach-Object {
            Write-Host ("  line " + $_.Extent.StartLineNumber + " col " + $_.Extent.StartColumnNumber + ": " + $_.Message)
            Write-Host ("    >> " + $_.Extent.Text)
        }
        $total += $errs.Count
    }
}
if ($total -gt 0) { exit 1 }
exit 0
