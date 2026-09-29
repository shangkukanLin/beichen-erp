# =====================================================================================
# F7-248 write-path negative test (D-22①) — 2026-09-30
#
# 目标：用"退 2 换 1 且未填收费"的**草稿**换货单，实证审核护栏
#       （SaleExchangeServiceImpl.audit :437-448，位于所有写库之前 :453+）会**拒绝**且**零副作用**。
#
# 零净变更设计：夹具由本脚本直接 INSERT（只用一次、随后按 id 删除），并逐表计数自检；
#               护栏在写路径之前 ⇒ 被拒时**不应有任何** stock / ledger / 状态变更。
#   备份：mysqldump 先行落盘（%TEMP%/f7-248-fixture-<ts>.sql）；清理后再次核对计数。
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\audit-20260930-sale-exchange-negative.ps1
# =====================================================================================
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DUMP = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysqldump.exe'
$env:MYSQL_PWD = 'root'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705'
$svc = Join-Path $repo 'beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp\sale\service\impl\SaleExchangeServiceImpl.java'
$pass = 0; $fail = 0
function Ok($c, $m) { if ($c) { $script:pass++; Write-Host ('  PASS  ' + $m) } else { $script:fail++; Write-Host ('  FAIL  ' + $m) } }
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function SqlAll([string]$sql) {
    @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
        Where-Object { "$_" -notmatch '^(mysql:|ERROR|Using a password)' }
}
function SqlOne([string]$sql) { return ("$(@(SqlAll $sql) | Select-Object -First 1)").Trim() }
function Counts {
    $m = @{}
    foreach ($t in @('sale_exchange', 'sale_exchange_item', 'finance_receivable', 'warehouse_stock', 'warehouse_stock_log', 'sale_order', 'sale_order_item')) {
        $m[$t] = [int](SqlOne ("SELECT COUNT(*) FROM " + $t))
    }
    return $m
}
function ApiPut($tok, $path) {
    try {
        $p = @{ Uri = ('http://localhost:8080/api' + $path); Method = 'Put'; ContentType = 'application/json' }
        if ($tok) { $p.Headers = @{ Authorization = $tok } }
        return Invoke-RestMethod @p
    } catch {
        $code = [int]$_.Exception.Response.StatusCode.value__
        $body = ''
        try { $body = (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch { }
        return @{ code = if ($code) { $code } else { 500 }; msg = ($body + ' ' + $_.Exception.Message).Trim() }
    }
}

Write-Host '=== F7-248 negative test: draft exchange "return 2 / send 1 without charge" must be rejected ==='

Sec '0) preflight'
Ok (Test-Path -LiteralPath $MYSQL) 'mysql client found'
Ok (Test-Path -LiteralPath $svc) 'SaleExchangeServiceImpl found'
$up = [bool](netstat -ano | Select-String ':8080\s' | Select-String 'LISTENING')
Ok $up 'backend is listening on 8080'

Sec '1) static check: the guard sits BEFORE any write inside audit()'
$lines = [System.IO.File]::ReadAllLines((Resolve-Path $svc))
$auditLine = 0; $guardLine = 0; $firstWrite = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
    $t = $lines[$i].Trim()
    # 跳过注释行：第一版把注释里提到的 saveChargeReceivable 当成了写操作（firstWrite 落到 433）
    if ($t.StartsWith('//') -or $t.StartsWith('*') -or $t.StartsWith('/*')) { continue }
    if ($lines[$i] -match 'public void audit\(') { $auditLine = $i + 1; continue }
    if ($auditLine -gt 0 -and $guardLine -eq 0 -and $lines[$i] -match '无处挂账') { $guardLine = $i + 1; continue }
    if ($auditLine -gt 0 -and $firstWrite -eq 0 -and $lines[$i] -match '(stockService\.changeStock|receivableMapper\.insert|saveChargeReceivable|exchangeMapper\.updateById)\s*\(') { $firstWrite = $i + 1; break }
}
Write-Host ('    audit()=' + $auditLine + '  guard=' + $guardLine + '  firstWrite=' + $firstWrite)
Ok ($auditLine -gt 0 -and $guardLine -gt $auditLine) 'F7-248 guard text is inside audit()'
Ok ($firstWrite -gt $guardLine) ('guard is before the first write (' + $guardLine + ' < ' + $firstWrite + ')')

Sec '2) backup (mysqldump) + baseline counts'
$bk = Join-Path $env:TEMP ('f7-248-fixture-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
# 注意：mysqldump **不接受 `-D`**（第一版用了 -D ⇒ 只产出一行错误）。库名按位置参数给。
$dumpOut = & $DUMP --default-character-set=utf8mb4 -uroot beichen_erp sale_exchange sale_exchange_item 2>&1
[System.IO.File]::WriteAllLines($bk, $dumpOut)
Write-Host ('    backup: ' + $bk + ' (' + $dumpOut.Count + ' lines)')
if ($dumpOut.Count -le 10) { Write-Host ('    dump head: ' + ("$($dumpOut | Select-Object -First 1)").Substring(0, [Math]::Min(140, ("$($dumpOut | Select-Object -First 1)").Length))) }
Ok ($dumpOut.Count -gt 10) 'backup written for the two fixture tables'
# 幂等前置清理：删掉上次运行可能残留的夹具（一次跑崩/异常时也能自愈），确保基线干净
SqlAll "DELETE FROM sale_exchange_item WHERE exchange_id IN (SELECT id FROM sale_exchange WHERE code LIKE 'HH-F7-248-NEG-%')" | Out-Null
SqlAll "DELETE FROM sale_exchange WHERE code LIKE 'HH-F7-248-NEG-%'" | Out-Null
SqlAll "DELETE FROM sale_order_item WHERE order_id IN (SELECT id FROM sale_order WHERE code = 'XS-F7-248-NEG')" | Out-Null
SqlAll "DELETE FROM sale_order WHERE code = 'XS-F7-248-NEG'" | Out-Null
$leftEx = SqlOne "SELECT COUNT(*) FROM sale_exchange WHERE code LIKE 'HH-F7-248-NEG-%'"
$leftSo = SqlOne "SELECT COUNT(*) FROM sale_order WHERE code = 'XS-F7-248-NEG'"
Write-Host ('    pre-clean leftovers: exchange=' + $leftEx + ' saleOrder=' + $leftSo)
$base = Counts
$base.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ('    ' + $_.Key.PadRight(22) + $_.Value) }

Sec '3) pick real references (warehouse/product/sale item) so that ONLY the F7-248 guard can fire'
$ref = @(SqlAll "SELECT warehouse_in_id, warehouse_out_id, customer_id, company_id, sale_order_id, sale_order_code FROM sale_exchange WHERE warehouse_in_id IS NOT NULL ORDER BY id DESC LIMIT 1")
$refParts = ("$($ref | Select-Object -First 1)") -split "`t"
$wIn = $refParts[0]; $wOut = $refParts[1]; $cust = $refParts[2]; $comp = $refParts[3]; $soId = $refParts[4]; $soCode = $refParts[5]
Write-Host ('    warehouse in/out=' + $wIn + '/' + $wOut + ' customer=' + $cust + ' company=' + $comp + ' saleOrder=' + $soCode)
Ok ([bool]$wIn -and [bool]$wOut) 'reference warehouse pair found (both FINISHED, reused from an existing exchange)'

# 候选销售明细：已审核销售单、数量 >= 3（留出余量,避免"可换数量不足"抢先触发）
# 库中已审核销售明细的数量都只有 1 ⇒ 无法满足"退 2"。故按需**自建**一张已审核销售单（数量 2）+ 明细：
# 这样 validateQuantity（可换量 = 已售 − 已退 − 已换）会通过 ⇒ **只有 F7-248 护栏可能拒绝**（判别力 ✓）。
$pid2 = SqlOne ("SELECT id FROM product WHERE company_id = " + $comp + " ORDER BY id DESC LIMIT 1")
Ok ([bool]$pid2) ('fixture product id=' + $pid2)
$pname2 = (SqlOne ("SELECT name FROM product WHERE id = " + $pid2)).Replace("'", "''")
SqlAll ("INSERT INTO sale_order (code, customer_id, warehouse_id, status, settle_type, total_amount, company_id, audit_time) VALUES ('XS-F7-248-NEG', " + $cust + ", " + $wOut + ", 'AUDITED', 'CREDIT', 0, " + $comp + ", NOW())") | Out-Null
$negOrderId = SqlOne "SELECT id FROM sale_order WHERE code = 'XS-F7-248-NEG' ORDER BY id DESC LIMIT 1"
SqlAll ("INSERT INTO sale_order_item (order_id, product_id, quantity, unit_price, amount, company_id) VALUES (" + $negOrderId + ", " + $pid2 + ", 2, 0, 0, " + $comp + ")") | Out-Null
$negItemId = SqlOne ("SELECT id FROM sale_order_item WHERE order_id = " + $negOrderId + " ORDER BY id DESC LIMIT 1")
Ok ([bool]$negOrderId -and [bool]$negItemId) ('fixture sale order + item created (order=' + $negOrderId + ', item=' + $negItemId + ', sold qty=2)')
$cand = @(([string]$negItemId + "`t" + [string]$negOrderId + "`tXS-F7-248-NEG`t" + [string]$pid2 + "`t" + $pname2 + "`t2"))
Write-Host ('    candidate (fixture) items = ' + $cand.Count)

Sec '4) the negative test itself (fixture -> API -> assert -> cleanup, per candidate until the guard fires)'
$tok = $null
try { $tok = (Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body ('{"username":"lin","password":"123","companyId":' + $comp + '}')).data.token } catch { $tok = $null }
Ok ([bool]$tok) 'logged in as the admin'
$guardFired = $false; $seen = @()
foreach ($row in $cand) {
    $p = ("$row") -split "`t"
    # 注意：**不要用 `$pid`** —— PowerShell 的 `$PID` 是只读自动变量（进程号），赋值会抛
    #   SessionStateUnauthorizedAccessException(VariableNotWritable) 并静默用进程号当产品ID（第一版就这样踩了）
    $soiId = $p[0]; $soiOrder = $p[1]; $soiCode = $p[2]; $prodId = $p[3]; $pname = $p[4]
    if (-not $guardFired) {
        # 夹具：草稿换货单 + 一条"退 2 换 1 且未收费"的明细
        SqlAll ("INSERT INTO sale_exchange (code, sale_order_id, sale_order_code, customer_id, warehouse_in_id, warehouse_out_id, exchange_date, status, charge_flag, charge_amount, company_id, create_time) VALUES ('HH-F7-248-NEG-" + $prodId + "', " + $soiOrder + ", '" + $soiCode + "', " + $cust + ", " + $wIn + ", " + $wOut + ", CURDATE(), 'DRAFT', 0, 0, " + $comp + ", NOW())") | Out-Null
        # ⚠️ 拼接必须用括号包住：`SqlOne "sql" + $x + "sql"` 会被解析成 `SqlOne "sql"` 再 `+ $x`（函数只收到第一段，
        # SQL 被截断 ⇒ 查询报错、返回空），第一版就这样导致"夹具已插入但查不到 id"。
        $exId = SqlOne ("SELECT id FROM sale_exchange WHERE code = 'HH-F7-248-NEG-" + $prodId + "' ORDER BY id DESC LIMIT 1")
        Ok ([bool]$exId) ('fixture exchange created (id=' + $exId + ', status=DRAFT, charge=0)')
        if ($exId) {
            SqlAll ("INSERT INTO sale_exchange_item (exchange_id, sale_order_item_id, product_id, product_name, quantity, out_quantity, charge_flag, charge_amount, company_id, create_time) VALUES (" + $exId + ", " + $soiId + ", " + $prodId + ", '" + $pname + "', 2, 1, 0, 0, " + $comp + ", NOW())") | Out-Null
            $itCnt = SqlOne ("SELECT COUNT(*) FROM sale_exchange_item WHERE exchange_id = " + $exId)
            Ok ($itCnt -eq '1') ('fixture item created: return 2 / send 1, charge = 0 (candidate soi=' + $soiId + ')')

            $before = Counts
            $r = ApiPut $tok ('/sale/exchange/' + $exId + '/audit')
            $msg = ("$($r.msg)")
            Write-Host ('    audit -> code=' + $r.code + ' msg=' + $msg.Substring(0, [Math]::Min(160, $msg.Length)))
            $seen += $msg

            # 关键断言：被拒 + 文案是 F7-248 那条（否则说明是别的校验抢先触发 => 不具判别力）
            if ($msg -match '非 1:1|无处挂账') { $guardFired = $true }
            Ok ($msg -match '非 1:1|无处挂账') ('rejected by the F7-248 guard itself (candidate soi=' + $soiId + ')')

            # 零副作用：库存/台账/单据状态都没动
            $after = Counts
            $diff = @()
            foreach ($k in $base.Keys) { if ($after[$k] -ne $before[$k]) { $diff += ($k + ':' + $before[$k] + '->' + $after[$k]) } }
            if ($diff.Count -gt 0) { $diff | ForEach-Object { Write-Host ('      CHANGED ' + $_) } }
            Ok ($diff.Count -eq 0) 'rejection left NO side effects (stock / ledger / counts unchanged)'
            $st = SqlOne ("SELECT status FROM sale_exchange WHERE id = " + $exId)
            Ok ($st -eq 'DRAFT') ('fixture is still DRAFT after the rejected audit (status=' + $st + ')')

            # 清理夹具（按 id 删除：换货单 + 明细 + 自建销售单 + 明细）
            SqlAll ("DELETE FROM sale_exchange_item WHERE exchange_id = " + $exId) | Out-Null
            SqlAll ("DELETE FROM sale_exchange WHERE id = " + $exId) | Out-Null
            SqlAll ("DELETE FROM sale_order_item WHERE order_id = " + $negOrderId) | Out-Null
            SqlAll ("DELETE FROM sale_order WHERE id = " + $negOrderId) | Out-Null
            $gone = SqlOne ("SELECT COUNT(*) FROM sale_exchange WHERE id = " + $exId)
            $gone2 = SqlOne ("SELECT COUNT(*) FROM sale_order WHERE id = " + $negOrderId)
            Ok ($gone -eq '0' -and $gone2 -eq '0') 'fixture deleted (exchange + self-built sale order, cleanup by id)'
        }
    }
}
Ok $guardFired 'the F7-248 guard fired on at least one candidate'
if (-not $guardFired) { Write-Host '    (INCONCLUSIVE: other validations rejected first - messages seen: ' + (($seen | Select-Object -Unique) -join ' | ') + ')' }

Sec '5) net-change self check (must equal the baseline)'
$end = Counts
$drift = @()
foreach ($k in $base.Keys) { if ($end[$k] -ne $base[$k]) { $drift += ($k + ':' + $base[$k] + '->' + $end[$k]) } }
if ($drift.Count -gt 0) { $drift | ForEach-Object { Write-Host ('    DRIFT ' + $_) } }
Ok ($drift.Count -eq 0) 'row counts are back to the baseline -> net change 0'
$fixtures = SqlOne "SELECT COUNT(*) FROM sale_exchange WHERE code LIKE 'HH-F7-248-NEG-%'"
Ok ($fixtures -eq '0') 'no fixture rows left behind'

Write-Host ''
Write-Host ('RESULT: ' + $pass + ' passed, ' + $fail + ' failed')
Write-Host ('backup kept at: ' + $bk)
if ($fail -gt 0) { exit 1 }
