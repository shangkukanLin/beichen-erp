# =====================================================================================
# F8-26 fix (settings batch E, 2026-09-30): add v-perm to the write buttons of the 7 system pages.
# ASCII-only on purpose (no BOM needed). Idempotent: skips lines that already carry v-perm,
# reports lines that are not <el-button> instead of touching them.
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\fix-f8-26-system-vperm.ps1 [-DryRun]
# =====================================================================================
param([switch]$DryRun)

$views = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src\views\system'
$map = @{
    'user\index.vue'         = @('460:system:user', '517:system:user', '524:system:user', '527:system:user', '528:system:user', '529:system:user', '535:system:user', '536:system:user')
    'role\index.vue'         = @('282:system:role', '304:system:role', '305:system:role', '306:system:role')
    'menu\index.vue'         = @('218:system:menu', '265:system:menu', '266:system:menu')
    'data-manage\index.vue'  = @('92:system:data-manage')
    'settings\index.vue'     = @('73:system:settings', '85:system:settings')
    'clear-data\index.vue'   = @('71:system:clear-data')
}
$q = [char]39
$inserted = 0; $already = 0; $miss = 0
foreach ($k in $map.Keys) {
    $p = Join-Path $views $k
    if (-not (Test-Path -LiteralPath $p)) { Write-Host ('  NOFILE ' + $k); $miss++; continue }
    $c = Get-Content -LiteralPath $p -Encoding UTF8
    $items = $map[$k] | ForEach-Object { $ps = $_ -split ':'; [pscustomobject]@{ Line = [int]$ps[0]; Code = ($ps[1..($ps.Count - 1)] -join ':') } } | Sort-Object Line -Descending
    $changed = $false
    foreach ($it in $items) {
        $i = $it.Line - 1
        if ($i -lt 0 -or $i -ge $c.Count) { Write-Host ('  MISS(range) ' + $k + ':' + $it.Line); $miss++; continue }
        if ($c[$i] -match 'v-perm') { $already++; continue }
        if ($c[$i] -notmatch '<el-button') { Write-Host ('  MISS(not button) ' + $k + ':' + $it.Line + ' -> ' + $c[$i].Trim().Substring(0, [Math]::Min(80, $c[$i].Trim().Length))); $miss++; continue }
        $c[$i] = $c[$i] -replace '<el-button', ('<el-button v-perm=' + $q + $it.Code + $q)
        $inserted++
        $changed = $true
    }
    if ($changed -and -not $DryRun) { $c | Set-Content -LiteralPath $p -Encoding UTF8 }
}
Write-Host ('### v-perm: inserted=' + $inserted + ' already=' + $already + ' miss=' + $miss + ' (dryRun=' + [bool]$DryRun + ')')
Write-Host '### per-page v-perm hits after the run:'
Get-ChildItem $views -Recurse -Filter *.vue | ForEach-Object {
    $n = @(Select-String -Path $_.FullName -Pattern 'v-perm').Count
    Write-Host ('  ' + $_.FullName.Replace($views + '\', '').PadRight(26) + $n)
}
Write-Host '### whole-frontend v-perm total:'
Write-Host ('  ' + @(Get-ChildItem 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src' -Recurse -Include *.vue,*.ts | Select-String -Pattern 'v-perm').Count)
Write-Host '### CompanyController authorization (decides whether the company page can be v-perm-gated):'
Select-String -Path 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp\system\controller\CompanyController.java' -Pattern '@SaCheckRole|@RequestMapping|class CompanyController' | ForEach-Object { Write-Host ('  ' + $_.LineNumber + ': ' + $_.Line.Trim()) }
