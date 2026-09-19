# 销售分析「客户换货率」饼图 —— 接口 vs SQL 直查（2026-09-15）
# 口径（2026-09-15 全站统一）：已审核销售换货单，按**建单日**归期；金额 = 明细「换出金额」Σ sale_exchange_item.out_amount
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-exchange.ps1
$ErrorActionPreference = 'Continue'

# 1) 接口值（本月区间）
$base = 'http://localhost:8080/api'
$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
$d = (Invoke-RestMethod -Uri "$base/sale/analysis?preset=month" -Headers $h).data
$ex = @($d.exchangeByCustomer)
$rate = $d.metrics.exchangeRate
$sumApi = 0.0
foreach ($r in $ex) { $sumApi += [double]$r.value }
Write-Output ('接口 exchangeByCustomer 片数 = ' + $ex.Count + '，分片合计 = ' + $sumApi)
foreach ($r in $ex) { Write-Output ('   - ' + $r.name + ' = ' + $r.value) }
Write-Output ('接口 metrics.exchangeRate = ' + $rate + ' %')

# 2) SQL 直查（本月 = 当月 1 日 ~ 今天，归期 = 审核日）
$ms = (Get-Date -Format 'yyyy-MM-01')
$today = (Get-Date -Format 'yyyy-MM-dd')
$sql = "SELECT 'TOTAL' AS k, IFNULL(SUM(i.out_amount),0) AS v FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id = e.id WHERE e.status = 'AUDITED' AND DATE(e.create_time) BETWEEN '$ms' AND '$today' UNION ALL SELECT CONCAT('C', e.customer_id), IFNULL(SUM(i.out_amount),0) FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id = e.id WHERE e.status = 'AUDITED' AND DATE(e.create_time) BETWEEN '$ms' AND '$today' GROUP BY e.customer_id;"
$out = (& powershell -NoProfile -ExecutionPolicy Bypass -File .\q.ps1 -Sql $sql) -join "`n"
$map = @{}
foreach ($ln in ($out -split "`r?`n")) {
  if ($ln -match '^\s*(\S+)\s*\t\s*(-?[\d.]+)\s*$') { $map[$Matches[1]] = [double]$Matches[2] }
}
$sumDb = if ($null -eq $map['TOTAL']) { -1.0 } else { [double]$map['TOTAL'] }
$custCnt = (@($map.Keys | Where-Object { $_ -like 'C*' })).Count
Write-Output ('SQL 换出金额合计 = ' + $sumDb + '，客户数 = ' + $custCnt)

# 3) 断言
$fail = 0
if ($ex.Count -gt 0) { Write-Output 'PASS 饼图非空（不再是空图）' } else { Write-Output 'FAIL 饼图仍为空'; $fail++ }
if ($ex.Count -eq $custCnt) { Write-Output 'PASS 片数 = SQL 客户数' } else { Write-Output ("FAIL 片数 {0} ≠ SQL 客户数 {1}" -f $ex.Count, $custCnt); $fail++ }
if ([Math]::Abs($sumApi - $sumDb) -lt 0.01) { Write-Output 'PASS 分片合计 = SQL 换出金额合计' } else { Write-Output ('FAIL 分片合计 ' + $sumApi + ' ≠ SQL ' + $sumDb); $fail++ }
if ([double]$rate -gt 0) { Write-Output 'PASS 标题总换货率 > 0' } else { Write-Output 'FAIL 标题总换货率仍为 0'; $fail++ }
# 费率精度：2026-09-15 起应为 2 位小数（1 位会把 0.06% 显示成 0.1%）
$raw = (Invoke-WebRequest -Uri "$base/sale/analysis?preset=month" -Headers $h -UseBasicParsing).Content
if ($raw -match '"exchangeRate":\s*(-?\d+\.\d{2})\b') { Write-Output ('PASS 换货率保留 2 位小数 = ' + $Matches[1] + ' %') } else { Write-Output 'FAIL 换货率未按 2 位小数返回'; $fail++ }
if ($raw -match '"returnRate":\s*(-?\d+\.\d{2})\b') { Write-Output ('PASS 退货率保留 2 位小数 = ' + $Matches[1] + ' %') } else { Write-Output 'FAIL 退货率未按 2 位小数返回'; $fail++ }
if (@($d.returnByCustomer).Count -gt 0) { Write-Output 'PASS 客户退货率 未受波及（仍有分片）' } else { Write-Output 'WARN 客户退货率 为空（请复核）' }
if ($fail -eq 0) { Write-Output 'RESULT PASS 客户换货率已按「明细换出金额」口径恢复' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
