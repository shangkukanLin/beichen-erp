# 销售分析「客户退货率 / 客户换货率」双口径校验（2026-09-15）
# 口径：金额 = 退货额 / 换货额(sum out_amount) ÷ 销售额
#       件数 = 退货件数(sum sale_return_item.quantity) / 换出件数(sum out_quantity) ÷ 销售件数(sum sale_order_item.quantity)
# 归期（2026-09-15 全站统一）：销售/退货/换货**均为建单日** create_time；区间 = 本月（当月 1 日 ~ 今天）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-pie.ps1
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
$d = (Invoke-RestMethod -Uri "$base/sale/analysis?preset=month" -Headers $h).data
$m = $d.metrics
$ms = (Get-Date -Format 'yyyy-MM-01'); $today = (Get-Date -Format 'yyyy-MM-dd')

function SumOf($list) { $s = 0.0; foreach ($r in @($list)) { $s += [double]$r.value }; return $s }

$apiRetAmt = SumOf $d.returnByCustomer
$apiRetQty = SumOf $d.returnByCustomerQty
$apiExchAmt = SumOf $d.exchangeByCustomer
$apiExchQty = SumOf $d.exchangeByCustomerQty
Write-Output ('接口 退货额分片合计 = ' + $apiRetAmt + ' ，退货件数分片合计 = ' + $apiRetQty)
Write-Output ('接口 换货额分片合计 = ' + $apiExchAmt + ' ，换出件数分片合计 = ' + $apiExchQty)
Write-Output ('接口 费率 = 退货额 ' + $m.returnRate + '% / 退货件数 ' + $m.returnRateQty + '% / 换货额 ' + $m.exchangeRate + '% / 换出件数 ' + $m.exchangeRateQty + '%')

$sql = "SELECT 'sale_amount' AS k, IFNULL(SUM(o.total_amount),0) AS v FROM sale_order o WHERE o.status='AUDITED' AND DATE(o.create_time) BETWEEN '$ms' AND '$today' " +
  "UNION ALL SELECT 'sale_qty', IFNULL(SUM(i.quantity),0) FROM sale_order o JOIN sale_order_item i ON i.order_id=o.id WHERE o.status='AUDITED' AND DATE(o.create_time) BETWEEN '$ms' AND '$today' " +
  "UNION ALL SELECT 'ret_amount_pie', IFNULL(SUM(x.v),0) FROM (SELECT r.customer_id, SUM(r.total_amount) AS v FROM sale_return r WHERE r.status='AUDITED' AND DATE(r.create_time) BETWEEN '$ms' AND '$today' GROUP BY r.customer_id) x " +
  "UNION ALL SELECT 'ret_amount_all', IFNULL(SUM(r.total_amount),0) FROM sale_return r WHERE r.status='AUDITED' AND DATE(r.create_time) BETWEEN '$ms' AND '$today' " +
  "UNION ALL SELECT 'ret_qty_pie', IFNULL(SUM(x.v),0) FROM (SELECT r.customer_id, SUM(ri.quantity) AS v FROM sale_return r JOIN sale_return_item ri ON ri.return_id=r.id WHERE r.status='AUDITED' AND DATE(r.create_time) BETWEEN '$ms' AND '$today' GROUP BY r.customer_id) x " +
  "UNION ALL SELECT 'ret_qty_all', IFNULL(SUM(ri.quantity),0) FROM sale_return r JOIN sale_return_item ri ON ri.return_id=r.id WHERE r.status='AUDITED' AND DATE(r.create_time) BETWEEN '$ms' AND '$today' " +
  "UNION ALL SELECT 'exch_amount_pie', IFNULL(SUM(x.v),0) FROM (SELECT e.customer_id, SUM(i.out_amount) AS v FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id=e.id WHERE e.status='AUDITED' AND DATE(e.create_time) BETWEEN '$ms' AND '$today' GROUP BY e.customer_id) x " +
  "UNION ALL SELECT 'exch_amount_all', IFNULL(SUM(i.out_amount),0) FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id=e.id WHERE e.status='AUDITED' AND DATE(e.create_time) BETWEEN '$ms' AND '$today' " +
  "UNION ALL SELECT 'exch_qty_pie', IFNULL(SUM(x.v),0) FROM (SELECT e.customer_id, SUM(i.out_quantity) AS v FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id=e.id WHERE e.status='AUDITED' AND DATE(e.create_time) BETWEEN '$ms' AND '$today' GROUP BY e.customer_id) x " +
  "UNION ALL SELECT 'exch_qty_all', IFNULL(SUM(i.out_quantity),0) FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id=e.id WHERE e.status='AUDITED' AND DATE(e.create_time) BETWEEN '$ms' AND '$today';"
$out = (& powershell -NoProfile -ExecutionPolicy Bypass -File .\q.ps1 -Sql $sql) -join "`n"
$db = @{}
foreach ($ln in ($out -split "`r?`n")) { if ($ln -match '^\s*(\S+)\s*\t\s*(-?[\d.]+)\s*$') { $db[$Matches[1]] = [double]$Matches[2] } }
Write-Output ('SQL 销售额 = ' + $db['sale_amount'] + ' ，销售件数 = ' + $db['sale_qty'])
Write-Output ('SQL 退货额 整体/按客户 = ' + $db['ret_amount_all'] + ' / ' + $db['ret_amount_pie'] + ' ；退货件数 整体/按客户 = ' + $db['ret_qty_all'] + ' / ' + $db['ret_qty_pie'])
Write-Output ('SQL 换货额 整体/按客户 = ' + $db['exch_amount_all'] + ' / ' + $db['exch_amount_pie'] + ' ；换出件数 整体/按客户 = ' + $db['exch_qty_all'] + ' / ' + $db['exch_qty_pie'])

$fail = 0
# 注意：函数内用 $script:fail 递增（用 `$fail += ChkNum ...` 会把函数输出整包吞掉，PS 会报 op_Addition）
function ChkNum($name, $a, $b, $tol) {
  if ($null -eq $b) { Write-Output ("FAIL $name：SQL 取不到值"); $script:fail++; return }
  if ([Math]::Abs($a - $b) -le $tol) { Write-Output ("PASS $name：接口 $a = SQL $b") }
  else { Write-Output ("FAIL $name：接口 $a ≠ SQL $b"); $script:fail++ }
}
ChkNum '退货额分片合计' $apiRetAmt $db['ret_amount_pie'] 0.05
ChkNum '退货件数分片合计' $apiRetQty $db['ret_qty_pie'] 0.01
ChkNum '换货额分片合计' $apiExchAmt $db['exch_amount_pie'] 0.05
ChkNum '换出件数分片合计' $apiExchQty $db['exch_qty_pie'] 0.01
ChkNum '总退货率(金额口径)' ([double]$m.returnRate) ([Math]::Round($db['ret_amount_all'] / $db['sale_amount'] * 100, 2)) 0.02
ChkNum '总退货率(件数口径)' ([double]$m.returnRateQty) ([Math]::Round($db['ret_qty_all'] / $db['sale_qty'] * 100, 2)) 0.02
ChkNum '总换货率(金额口径)' ([double]$m.exchangeRate) ([Math]::Round($db['exch_amount_all'] / $db['sale_amount'] * 100, 2)) 0.02
ChkNum '总换货率(件数口径)' ([double]$m.exchangeRateQty) ([Math]::Round($db['exch_qty_all'] / $db['sale_qty'] * 100, 2)) 0.02

foreach ($f in @('returnByCustomer', 'returnByCustomerQty', 'exchangeByCustomer', 'exchangeByCustomerQty')) {
  $n = @($d.$f).Count
  if ($n -gt 0) { Write-Output ("PASS 字段 $f 非空（$n 片）") } else { Write-Output ("WARN 字段 $f 为空（该口径无数据）") }
}
foreach ($f in @('returnRate', 'exchangeRate', 'returnRateQty', 'exchangeRateQty')) {
  if ($m.PSObject.Properties.Name -contains $f) { Write-Output ("PASS metrics.$f = " + $m.$f) } else { Write-Output "FAIL metrics 缺字段 $f"; $fail++ }
}
if ($fail -eq 0) { Write-Output 'RESULT PASS 退货率/换货率 双口径与 SQL 一致' } else { Write-Output ("RESULT FAIL 项数 " + $fail); exit 1 }
