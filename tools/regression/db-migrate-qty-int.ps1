# 数量列迁移：DECIMAL(18,4) → DECIMAL(18,0)（2026-09-16 用户要求：物料与产品数量都是整数）
#
# 只改"数量"列；单价/金额/税率/良率/损耗率/单位用量等**比率与金额列保持小数**。
# 生成 DDL 时保留原有 NULL/NOT NULL 与 DEFAULT，避免 MODIFY 丢约束。
# 排除历史备份表（表名含 bak）。
#
# 用法：powershell -File db-migrate-qty-int.ps1 -DryRun     # 只打印将执行的语句
#       powershell -File db-migrate-qty-int.ps1             # 真正执行
param([switch]$DryRun, [switch]$SyncSchema)
$ErrorActionPreference = 'Continue'
$mysql = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function Q([string]$sql) { & $mysql --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $sql 2>$null }
if ($SyncSchema) {
    # 同步建库脚本里的数量列类型（schema.sql / sql/init.sql）：列名含 quantity/qty 的才改，
    # 明确排除 quantity_per_set（单套用量＝比率，保留小数）与金额/单价/税率列
    $enc = New-Object System.Text.UTF8Encoding($false)
    foreach ($f in @('C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\resources\schema.sql',
                     'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\sql\init.sql')) {
        if (-not (Test-Path $f)) { Write-Host ("跳过（文件不存在）：" + $f); continue }
        $lines = [System.IO.File]::ReadAllLines($f, $enc)
        $n = 0
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $l = $lines[$i]
            if ($l -notmatch 'DECIMAL\(18,4\)') { continue }
            if ($l -match 'quantity_per_set') { continue }
            if ($l -match '(?i)^\s*[a-z0-9_]*q(uan)?tity[a-z0-9_]*\s+DECIMAL\(18,4\)' -or
                $l -match '(?i)^\s*[a-z0-9_]*qty[a-z0-9_]*\s+DECIMAL\(18,4\)' -or
                $l -match '(?i)^\s*safety_stock\s+DECIMAL\(18,4\)') {
                $lines[$i] = $l -replace 'DECIMAL\(18,4\)', 'DECIMAL(18,0)'
                $n++
            }
        }
        [System.IO.File]::WriteAllLines($f, $lines, $enc)
        Write-Host ("同步 " + (Split-Path $f -Leaf) + "：" + $n + " 列改为 DECIMAL(18,0)")
    }
    exit 0
}
$cols = @('quantity', 'available_quantity', 'change_quantity', 'before_quantity', 'after_quantity',
    'a_qty', 'b_qty', 'c_qty', 'defect_qty', 'demand_quantity',
    'order_quantity', 'received_quantity', 'defect_returned_qty',
    'good_return_qty', 'defect_return_qty', 'shipped_quantity', 'excess_loss_qty', 'factory_retain_qty', 'missing_qty', 'returned_quantity',
    'actual_quantity', 'book_quantity', 'diff_quantity', 'total_quantity', 'sorted_quantity',
    'qty_a', 'qty_b', 'qty_c', 'qty_defect', 'out_quantity', 'safety_stock')
$in = ($cols | ForEach-Object { "'$_'" }) -join ','
$sql = "SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE, IFNULL(COLUMN_DEFAULT,'~NULL~'), COLUMN_TYPE FROM information_schema.COLUMNS " +
"WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE='decimal' AND NUMERIC_SCALE=" + 4 +
" AND INSTR(TABLE_NAME,'bak')=0 AND COLUMN_NAME IN ($in) ORDER BY TABLE_NAME, COLUMN_NAME"
$rows = @(Q $sql) | Where-Object { $_ }
$stmts = @()
foreach ($r in $rows) {
    $p = ($r -split "`t")
    $t = $p[0]; $c = $p[1]; $nullable = $p[2]; $def = $p[3]
    $nullPart = if ($nullable -eq 'NO') { ' NOT NULL' } else { ' NULL' }
    $defPart = if ($def -eq '~NULL~') { if ($nullable -eq 'NO') { '' } else { ' DEFAULT NULL' } } else { " DEFAULT $def" }
    $stmts += "ALTER TABLE $t MODIFY COLUMN $c DECIMAL(18,0)$nullPart$defPart"
}
Write-Host ("目标列数 = " + $stmts.Count)
if ($DryRun) { $stmts | ForEach-Object { Write-Host $_ }; exit 0 }
$ok = 0
foreach ($s in $stmts) { Q "$s;" | Out-Null; Write-Host ("OK  " + $s); $ok++ }
Write-Host ("完成，已执行 " + $ok + " 条")
