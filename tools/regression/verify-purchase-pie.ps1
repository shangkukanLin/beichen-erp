# 进货分析「直接采购成品 / 委外加工成品入库」两个饼图 —— 接口 vs SQL 直查（2026-09-15）
# 口径：① 直接采购成品 = Σ采购明细 − Σ采购退货明细（净额，采购按审核日、退货按建单日），按产品
#       ② 委外加工成品入库 = Σ(收货数量 × 加工单价)，按成品产品；归期 = **建单日**（2026-09-15 全站统一）；退不良负数自动冲减
# 区间：**动态取"全量已审核单据"的日期跨度**（2026-09-21 修正）
#   原为硬编码 2026-08-01 ~ 2026-09-15 ⇒ 2026-09-18 清库重建后所有单据归期整体漂移到 9/18，
#   区间内接口与 SQL 同为 0（前 6 条互相 PASS），但末尾"与应付台账对账"那条**不带区间过滤**
#   （取全量未作废 OUTSOURCE_DELIVERY 应付）⇒ 0 ≠ 10000，**长期假红**。
#   ⇒ 现取 采购单/采购退货/委外收货（三者均 AUDITED）的 MIN/MAX(DATE(create_time)) 作为区间，
#     保证"区间 = 全量数据"这一前提成立、台账对账语义正确；查不到时回退本月。
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-purchase-pie.ps1
$ErrorActionPreference = 'Continue'
$bnd = (& powershell -NoProfile -ExecutionPolicy Bypass -File .\q.ps1 -Sql "SELECT CAST(MIN(x.d) AS CHAR) AS mn, CAST(MAX(x.d) AS CHAR) AS mx FROM (SELECT DATE(create_time) AS d FROM purchase_order WHERE status = 'AUDITED' UNION ALL SELECT DATE(create_time) FROM purchase_return WHERE status = 'AUDITED' UNION ALL SELECT DATE(create_time) FROM outsource_order_delivery WHERE status = 'AUDITED') x;") -join "`n"
$s = $null; $e = $null
foreach ($ln in ($bnd -split "`r?`n")) {
  if ($ln -match '^\s*(\d{4}-\d{2}-\d{2})\s*\t\s*(\d{4}-\d{2}-\d{2})\s*$') { $s = $Matches[1]; $e = $Matches[2] }
}
if (-not $s -or -not $e) { $s = (Get-Date -Format 'yyyy-MM-01'); $e = (Get-Date -Format 'yyyy-MM-dd'); Write-Output 'WARN 未取到单据日期跨度，回退本月区间' }
Write-Output ('区间（动态）：' + $s + ' ~ ' + $e)
$base = 'http://localhost:8080/api'
$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
$d = (Invoke-RestMethod -Uri "$base/finance/analysis/purchase-analysis?preset=custom&start=$s&end=$e" -Headers $h).data
$t = $d.pieTotals

function SumOf($list) { $sum = 0.0; foreach ($r in @($list)) { $sum += [double]$r.value }; return $sum }
$apiDpAmt = SumOf $d.directPurchaseByProduct
$apiDpQty = SumOf $d.directPurchaseByProductQty
$apiOsAmt = SumOf $d.outsourceInByProduct
$apiOsQty = SumOf $d.outsourceInByProductQty
Write-Output ('接口 直接采购成品：金额分片合计=' + $apiDpAmt + '，件数分片合计=' + $apiDpQty + '，金额净额合计=' + $t.directPurchaseAmount + '，件数净额合计=' + $t.directPurchaseQuantity)
Write-Output ('接口 委外入库：  金额分片合计=' + $apiOsAmt + '，件数分片合计=' + $apiOsQty + '，金额合计=' + $t.outsourceInAmount + '，件数合计=' + $t.outsourceInQuantity)

# SQL 直查：每个产品一行（pid, 值）→ PS 侧算「>0 分片合计」与「全部合计」
function SqlMap($sql) {
  $out = (& powershell -NoProfile -ExecutionPolicy Bypass -File .\q.ps1 -Sql $sql) -join "`n"
  $m = @{}
  foreach ($ln in ($out -split "`r?`n")) {
    if ($ln -match '^\s*(\d+)\s*\t\s*(-?[\d.]+)\s*$') { $m[[int]$Matches[1]] = [double]$Matches[2] }
  }
  return $m
}
function PieTotal($m) {
  $pie = 0.0; $all = 0.0
  foreach ($v in $m.Values) { $all += $v; if ($v -gt 0) { $pie += $v } }
  return @{ pie = [Math]::Round($pie, 2); all = [Math]::Round($all, 2) }
}

$dpAmtMap = SqlMap "SELECT x.pid, SUM(x.v) AS net FROM (SELECT i.product_id AS pid, i.amount AS v FROM purchase_order o JOIN purchase_order_item i ON i.order_id = o.id WHERE o.status = 'AUDITED' AND DATE(o.create_time) BETWEEN '$s' AND '$e' UNION ALL SELECT i.product_id, -i.amount FROM purchase_return r JOIN purchase_return_item i ON i.return_id = r.id WHERE r.status = 'AUDITED' AND DATE(r.create_time) BETWEEN '$s' AND '$e') x GROUP BY x.pid;"
$dpQtyMap = SqlMap "SELECT x.pid, SUM(x.v) AS net FROM (SELECT i.product_id AS pid, i.quantity AS v FROM purchase_order o JOIN purchase_order_item i ON i.order_id = o.id WHERE o.status = 'AUDITED' AND DATE(o.create_time) BETWEEN '$s' AND '$e' UNION ALL SELECT i.product_id, -i.quantity FROM purchase_return r JOIN purchase_return_item i ON i.return_id = r.id WHERE r.status = 'AUDITED' AND DATE(r.create_time) BETWEEN '$s' AND '$e') x GROUP BY x.pid;"
$osAmtMap = SqlMap "SELECT od.product_master_id, SUM(od.quantity * IFNULL(op.unit_price, 0)) AS v FROM outsource_order_delivery od LEFT JOIN outsource_order_product op ON op.id = od.product_id WHERE od.status = 'AUDITED' AND DATE(od.create_time) BETWEEN '$s' AND '$e' GROUP BY od.product_master_id;"
$osQtyMap = SqlMap "SELECT od.product_master_id, SUM(od.quantity) AS v FROM outsource_order_delivery od WHERE od.status = 'AUDITED' AND DATE(od.create_time) BETWEEN '$s' AND '$e' GROUP BY od.product_master_id;"

$dpA = PieTotal $dpAmtMap; $dpQ = PieTotal $dpQtyMap; $osA = PieTotal $osAmtMap; $osQ = PieTotal $osQtyMap
Write-Output ('SQL  直接采购成品：金额分片=' + $dpA.pie + ' 净额=' + $dpA.all + '，件数分片=' + $dpQ.pie + ' 净额=' + $dpQ.all + '（产品数 ' + $dpAmtMap.Count + '）')
Write-Output ('SQL  委外入库：  金额=' + $osA.all + '，件数=' + $osQ.all + '（产品数 ' + $osAmtMap.Count + '）')

$fail = 0
function ChkNum($name, $a, $b, $tol) {
  if ([Math]::Abs([double]$a - [double]$b) -le $tol) { Write-Output ("PASS $name：接口 $a = SQL $b") }
  else { Write-Output ("FAIL $name：接口 $a ≠ SQL $b"); $script:fail++ }
}
ChkNum '直接采购成品·金额分片合计' $apiDpAmt $dpA.pie 0.05
ChkNum '直接采购成品·金额净额合计' $t.directPurchaseAmount $dpA.all 0.05
ChkNum '直接采购成品·件数分片合计' $apiDpQty $dpQ.pie 0.01
ChkNum '直接采购成品·件数净额合计' $t.directPurchaseQuantity $dpQ.all 0.01
ChkNum '委外入库·金额合计' $apiOsAmt $osA.all 0.05
ChkNum '委外入库·件数合计' $apiOsQty $osQ.all 0.01

# 交叉对账：委外入库金额应 = 应付台账（委外加工收货，未作废）
$ap = SqlMap "SELECT 1 AS k, IFNULL(SUM(amount), 0) AS v FROM finance_payable WHERE source_bill_type = 'OUTSOURCE_DELIVERY' AND status <> 'CANCELLED';"
$apAmt = if ($ap.ContainsKey(1)) { [Math]::Round($ap[1], 2) } else { $null }
if ($null -ne $apAmt) { ChkNum '委外入库金额 与应付台账对账' $osA.all $apAmt 0.05 }
if ($apiDpAmt -ge 0 -and $apiOsAmt -ge 0) { Write-Output 'PASS 两个饼图分片合计均非负' } else { Write-Output 'FAIL 出现负分片'; $fail++ }

# 本月区间下委外应为空（该饼图空态场景）
$dm = (Invoke-RestMethod -Uri "$base/finance/analysis/purchase-analysis?preset=month" -Headers $h).data
$dmAmt = SumOf $dm.outsourceInByProduct
if ($dmAmt -eq 0) { Write-Output 'PASS 本月区间 委外入库为空（页面走空态占位）' } else { Write-Output ('INFO 本月 委外入库金额 = ' + $dmAmt) }

if ($fail -eq 0) { Write-Output 'RESULT PASS 两个饼图与 SQL/台账一致' } else { Write-Output ("RESULT FAIL 项数 " + $fail); exit 1 }
