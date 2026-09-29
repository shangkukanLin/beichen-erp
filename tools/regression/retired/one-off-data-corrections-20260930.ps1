# =====================================================================================
# RETIRED (2026-09-30) — 从 audit-20260929-fin-cleanup-fixtures.ps1 拆出的两段"一次性数据订正"
#
# 退役理由（用户口径：「未上线 ⇒ 旧数据不需要兼容」+《数据库演化约定》）：
#   这两段都是**为已存在的存量数据**做的一次性 UPDATE，属于"数据迁移"而非测试夹具：
#     - 原 §14：把 sys_menu id=908 的文案 清空数据 → 清空本公司数据
#               （新库由 DataInitializer.syncMenus 直接播种新文案，无需订正）
#     - 原 §17：给历史已审核报损单打标注 [D-1 历史已审核报损，不追溯生成凭证]
#               （D-1 口径：历史单据不追溯；新库不存在"历史单据"）
#
# 本文件**仅供留痕与查阅**：默认不执行任何语句；确需在某个遗留库上复现当年动作时，
# 请显式加 -Apply，并先自行备份（原脚本自带 %TEMP% 备份）。
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\one-off-data-corrections-20260930.ps1            # 只打印
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\one-off-data-corrections-20260930.ps1 -Apply     # 执行
# =====================================================================================
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$STAMP = Get-Date -Format 'yyyyMMdd-HHmmss'

$stmt14 = @'
UPDATE sys_menu
SET menu_name = '清空本公司数据'
WHERE id = 908 AND menu_name = '清空数据'
'@

$stmt17a = @'
UPDATE inventory_stock_loss
SET remark = CONCAT(IFNULL(remark,''), ' [D-1 历史已审核报损，20260930 口径：不追溯生成凭证]')
WHERE status = 'AUDITED' AND IFNULL(remark,'') NOT LIKE '%D-1%'
'@

$stmt17b = @'
UPDATE outsource_stock_loss
SET remark = CONCAT(IFNULL(remark,''), ' [D-1 历史已审核报损，20260930 口径：不追溯生成凭证]')
WHERE status = 'AUDITED' AND IFNULL(remark,'') NOT LIKE '%D-1%'
'@

foreach ($item in @(@('14 menu label', $stmt14), @('17a inventory_stock_loss tag', $stmt17a), @('17b outsource_stock_loss tag', $stmt17b))) {
    Write-Host ('--- ' + $item[0])
    if (-not $Apply) {
        Write-Host '    (dry) not executed; add -Apply to run'
        Write-Host ('    ' + $item[1].Trim().Split("`n")[0])
        continue
    }
    $bak = Join-Path $env:TEMP ('one-off-correction-' + ($item[0] -replace '[^\w]', '_') + '-' + $STAMP + '.txt')
    [System.IO.File]::WriteAllText($bak, $item[1], (New-Object System.Text.UTF8Encoding($false)))
    & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e $item[1] 2>&1 |
        Where-Object { "$_" -notmatch 'Using a password' } | ForEach-Object { Write-Host ('    ' + $_) }
    Write-Host ('    executed (statement archived at ' + $bak + ')')
}
