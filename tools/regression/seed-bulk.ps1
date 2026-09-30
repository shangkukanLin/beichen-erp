# =====================================================================================
# seed-bulk.ps1  (2026-09-30)  把主线业务单据补到每类 N 条
#
# 原理：mainline-seed.ps1 的每个单据段都是"当前条数 < Count 才补 1 条"（幂等），
# 所以反复调用它即可逐步补齐。本脚本负责循环调用 + 每轮核对条数 + 超时保护。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\seed-bulk.ps1                 # 每类 10 条
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\seed-bulk.ps1 -Count 3        # 每类 3 条
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\seed-bulk.ps1 -NoFast         # 关掉直连加速（回到有界调用）
#
# 说明：AB_FAST=1 时 agent-browser 调用直连（每次省 ~2s），代价是失去单次调用超时，
#       因此本脚本给每一轮设置 RoundTimeoutSec 上限，超时就杀掉这一轮（下一轮会继续补）。
# =====================================================================================
param(
    [int]$Count = 10,
    [int]$MaxRounds = 30,
    [int]$RoundTimeoutSec = 900,
    [switch]$NoFast
)
$ErrorActionPreference = 'Continue'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $repo
$env:MYSQL_PWD = 'root'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$logDir = Join-Path $env:TEMP 'seed-bulk'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# 业务单据表（不含基础数据）
$targets = [ordered]@{
    purchase = 'purchase_order'
    otherin  = 'inventory_other_io'
    sale     = 'sale_order'
    move     = 'inventory_warehouse_move'
    loss     = 'inventory_stock_loss'
    'return' = 'sale_return'
    receipt  = 'finance_receipt'
    payment  = 'finance_payment'
    expense  = 'finance_expense'
    invoice  = 'finance_invoice'
    project  = 'dev_project'
    matio    = 'outsource_other_io'
    matmove  = 'inventory_material_move'
    matloss  = 'outsource_stock_loss'
}
function Count1([string]$table) {
    $v = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e ("SELECT COUNT(*) FROM " + $table) 2>$null
    return [int]("$v".Trim())
}
function Show-Progress([string]$tag) {
    $line = @()
    foreach ($k in $targets.Keys) { $line += ($k + '=' + (Count1 $targets[$k]) + '/' + $Count) }
    Write-Host ('  [' + $tag + '] ' + ($line -join '  '))
}
if (-not $NoFast) { $env:AB_FAST = '1' } else { $env:AB_FAST = '' }

Write-Host ('=== seed-bulk: target ' + $Count + ' per doc type ===')
Show-Progress 'start'
for ($round = 1; $round -le $MaxRounds; $round++) {
    $done = $true
    foreach ($k in $targets.Keys) { if ((Count1 $targets[$k]) -lt $Count) { $done = $false; break } }
    if ($done) { Write-Host 'ALL TARGETS REACHED'; break }

    $log = Join-Path $logDir ('round-' + $round.ToString('00') + '.log')
    Write-Host ('')
    Write-Host ('--- round ' + $round + ' @' + (Get-Date -Format 'HH:mm:ss') + '  log=' + $log + ' ---')
    $p = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', '.\tools\regression\mainline-seed.ps1', '-Count', "$Count") -WorkingDirectory $repo -RedirectStandardOutput $log -RedirectStandardError (Join-Path $logDir 'round.err.log') -PassThru -WindowStyle Hidden
    $exited = $p.WaitForExit($RoundTimeoutSec * 1000)
    if (-not $exited) {
        Write-Host ('  TIMEOUT after ' + $RoundTimeoutSec + 's -> kill this round')
        try { $p.Kill() } catch { }
        Start-Sleep -Seconds 3
    }
    if (Test-Path $log) {
        Get-Content $log -Encoding Default | Select-String -Pattern '^###|PASS |FAIL |RESULT|msgs=|rows |status=|AB-TIMEOUT' | Select-Object -Last 26 | ForEach-Object { Write-Host ('  | ' + $_.Line.Trim()) }
    }
    Show-Progress ('round ' + $round + ' done')
}
Write-Host ''
Write-Host '=== final ==='
Show-Progress 'final'
