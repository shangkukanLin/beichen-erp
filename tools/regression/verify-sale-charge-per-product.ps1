# 销售退单 / 销售换货单：**逐产品收费**（用户口径 2026-09-21「销售退单和销售换货单应该都有付费，
#   而且付费需要精确到产品上」）的耐久 API 用例。口径 A：一张单据一条应收，金额 = Σ明细行收费，
#   remark 逐产品；反审核按 单号-FEE 全额冲销。
#
#   覆盖：①退单——逐行不同收费 ⇒ 单据 = Σ、审核后应收 = Σ 且 remark 含产品名与类型、反审核冲销干净
#         ②负例——某行填了金额但没选类型 ⇒ 建单被拒（中文提示）+ 不落库
#         ③换货——逐行收费同样成立
#         ④兼容——只填单据级金额（旧前端形状）⇒ 自动落到第一条明细，不丢钱
#   自清理：本脚本造的草稿一律走 API 作废；夹具全部运行时从库里取。
#   ASCII ONLY（中文只从接口/数据库回读，断言用 [char] 或中文字符类正则）。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) | Select-Object -First 1)
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function CallPost([string]$url, $h, [string]$body) {
  try { return Invoke-RestMethod -Uri $url -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) }
  catch { return $null }
}
function CallPut([string]$url, $h) {
  try { return Invoke-RestMethod -Uri $url -Method Put -Headers $h } catch { return $null }
}
function MsgOf($r) { if ($null -eq $r) { return '' }; return ([string]$r.msg) }

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ([bool]$lg.data.token) 'logged in'

# ===================== A) 销售退单 =====================
Write-Host '--- A) sale return: per-product charge'
$cust = [int](SqlOne "SELECT id FROM customer ORDER BY id LIMIT 1")
$wh = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_type='FINISHED' ORDER BY id LIMIT 1")
$p1 = [int](SqlOne "SELECT product_id FROM warehouse_stock_log WHERE change_type='SALE_OUT' AND product_id IS NOT NULL ORDER BY id DESC LIMIT 1")
Write-Host ("  fixtures: cust=$cust wh=$wh product=$p1")
if ($cust -gt 0 -and $wh -gt 0 -and $p1 -gt 0) {
  $body = @{ customerId = $cust; warehouseId = $wh; returnDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'verify per-product charge'
    items = @(
      @{ productId = $p1; qualityType = 'PENDING'; quantity = 1; unitPrice = 10; chargeAmount = 12; chargeType = 'SERVICE' },
      @{ productId = $p1; qualityType = 'PENDING'; quantity = 1; unitPrice = 5 }
    ) } | ConvertTo-Json -Depth 6
  $r = CallPost "$base/sale/return" $h $body
  $rid = [int]$r.data.id; $rcode = [string]$r.data.code
  Write-Host ("  create: code=$($r.code) id=$rid chargeFlag=$($r.data.chargeFlag) chargeAmount=$($r.data.chargeAmount)")
  Ok (($r.code -eq 200) -or ($r.code -eq 0)) 'sale return created with a per-item charge'
  $it1 = D (SqlOne "SELECT IFNULL(charge_amount,0) FROM sale_return_item WHERE return_id=$rid ORDER BY id LIMIT 1")
  $it2 = D (SqlOne "SELECT IFNULL(charge_amount,0) FROM sale_return_item WHERE return_id=$rid ORDER BY id DESC LIMIT 1")
  $it1type = SqlOne "SELECT charge_type FROM sale_return_item WHERE return_id=$rid ORDER BY id LIMIT 1"
  Ok (($it1 -eq 12)) 'item 1 keeps its own charge (12)'
  Ok (($it2 -eq 0)) 'item 2 stays uncharged (0)'
  Ok (($it1type -eq 'SERVICE')) 'item 1 keeps its own charge type (SERVICE)'
  $docAmt = D (SqlOne "SELECT IFNULL(charge_amount,0) FROM sale_return WHERE id=$rid")
  $docFlag = D (SqlOne "SELECT IFNULL(charge_flag,0) FROM sale_return WHERE id=$rid")
  Ok (($docAmt -eq 12)) 'document charge = SUM(items) = 12'
  Ok (($docFlag -eq 1)) 'document charge_flag = 1'
  $a = CallPut "$base/sale/return/$rid/audit" $h
  Start-Sleep -Milliseconds 900
  Write-Host ("  audit: code=$($a.code) msg=$($a.msg)")
  $feeAmt = D (SqlOne "SELECT amount FROM finance_receivable WHERE source_bill_type='SALE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'")
  $feeRemark = SqlOne "SELECT remark FROM finance_receivable WHERE source_bill_type='SALE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'"
  $prodName = SqlOne "SELECT name FROM product WHERE id=$p1"
  Write-Host ("  receivable: amount=$feeAmt remark=$feeRemark")
  Ok (($feeAmt -eq 12)) 'receivable amount = SUM(items) = 12'
  Ok (([string]$feeRemark).Contains([string]$prodName) -and ([string]$feeRemark).Contains('SERVICE')) 'receivable remark lists product + type (per-product, 口径 A)'
  Ok ((D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE source_bill_type='SALE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'")) -eq 1) 'exactly ONE fee receivable per document (per-product detail lives in its remark)'
  $u = CallPut "$base/sale/return/$rid/un-audit" $h
  Start-Sleep -Milliseconds 800
  Ok ((D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE source_bill_no='$rcode' AND status<>'CANCELLED'")) -eq 0) 'un-audit reverses the fee receivable'
  CallPut "$base/sale/return/$rid/cancel" $h | Out-Null
  Ok ((SqlOne "SELECT status FROM sale_return WHERE id=$rid") -eq 'CANCELLED') 'probe document cancelled (trace kept)'

  # 负例：某行填金额但没选类型（且没有批量类型）⇒ 建单必须被拒
  Write-Host '--- B) negative: an item with an amount but no charge type'
  $badBody = @{ customerId = $cust; warehouseId = $wh; returnDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'verify per-product charge negative'
    items = @(@{ productId = $p1; qualityType = 'PENDING'; quantity = 1; unitPrice = 10; chargeAmount = 5 }) } | ConvertTo-Json -Depth 6
  $before = D (SqlOne "SELECT COUNT(*) FROM sale_return")
  $bad = CallPost "$base/sale/return" $h $badBody
  $badMsg = MsgOf $bad
  $after = D (SqlOne "SELECT COUNT(*) FROM sale_return")
  Write-Host ("  item-without-type -> code=$($bad.code) msg=$badMsg")
  Ok (($bad.code -ne 200) -or ($bad.code -eq $null)) 'an item charge without a type is refused'
  Ok ($badMsg -match '[\u4e00-\u9fa5]') 'the refusal is explained in words'
  Ok (($after -eq $before)) 'nothing was persisted for the refused document'

  # 兼容：只填单据级金额（旧前端形状）⇒ 落到第一条明细，不丢钱
  Write-Host '--- C) backward compatibility: document-level amount only'
  $legacy = @{ customerId = $cust; warehouseId = $wh; returnDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'verify per-product charge legacy'
    chargeFlag = 1; chargeType = 'OTHER'; chargeAmount = 9
    items = @(@{ productId = $p1; qualityType = 'PENDING'; quantity = 1; unitPrice = 10 }) } | ConvertTo-Json -Depth 6
  $lr = CallPost "$base/sale/return" $h $legacy
  $lrid = [int]$lr.data.id
  Write-Host ("  legacy doc-level only: code=$($lr.code) id=$lrid chargeAmount=$($lr.data.chargeAmount)")
  Ok (($lr.code -eq 200) -or ($lr.code -eq 0)) 'a legacy document-level-only charge is still accepted'
  Ok ((D (SqlOne "SELECT IFNULL(charge_amount,0) FROM sale_return_item WHERE return_id=$lrid")) -eq 9) 'the legacy amount landed on the first item (nothing lost)'
  Ok ((D (SqlOne "SELECT IFNULL(charge_amount,0) FROM sale_return WHERE id=$lrid")) -eq 9) 'document charge equals it (SUM invariant holds)'
  CallPut "$base/sale/return/$lrid/cancel" $h | Out-Null
} else { Ok $false 'sale-return fixtures missing' }

# ===================== D) 销售换货单 =====================
Write-Host '--- D) sale exchange: per-product charge'
$soi = [int](SqlOne "SELECT oi.id FROM sale_order_item oi JOIN sale_order o ON o.id=oi.order_id WHERE o.status='AUDITED' ORDER BY oi.id LIMIT 1")
$soOrder = [int](SqlOne "SELECT order_id FROM sale_order_item WHERE id=$soi")
$scust = [int](SqlOne "SELECT customer_id FROM sale_order WHERE id=$soOrder")
$swh = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_type='FINISHED' ORDER BY id LIMIT 1")
$sprod = [int](SqlOne "SELECT product_id FROM sale_order_item WHERE id=$soi")
Write-Host ("  fixtures: order=$soOrder item=$soi product=$sprod cust=$scust wh=$swh")
if ($soOrder -gt 0 -and $swh -gt 0 -and $sprod -gt 0) {
  $ebody = @{ saleOrderId = $soOrder; customerId = $scust; warehouseInId = $swh; warehouseOutId = $swh
    exchangeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'verify per-product charge exchange'
    items = @(@{ saleOrderItemId = $soi; productId = $sprod; quantity = 1; unitPrice = 10
                 outQuantity = 1; outUnitPrice = 10; outQualityType = 'A'
                 chargeAmount = 7; chargeType = 'DIFF' }) } | ConvertTo-Json -Depth 6
  $e = CallPost "$base/sale/exchange" $h $ebody
  $xid = [int]$e.data.id
  Write-Host ("  create: code=$($e.code) id=$xid chargeAmount=$($e.data.chargeAmount)")
  Ok (($e.code -eq 200) -or ($e.code -eq 0)) 'sale exchange created with a per-item charge'
  Ok ((D (SqlOne "SELECT IFNULL(charge_amount,0) FROM sale_exchange_item WHERE exchange_id=$xid")) -eq 7) 'exchange item keeps its own charge (7)'
  Ok ((SqlOne "SELECT charge_type FROM sale_exchange_item WHERE exchange_id=$xid") -eq 'DIFF') 'exchange item keeps its own type (DIFF)'
  Ok ((D (SqlOne "SELECT IFNULL(charge_amount,0) FROM sale_exchange WHERE id=$xid")) -eq 7) 'exchange document charge = SUM(items) = 7'
  CallPut "$base/sale/exchange/$xid/cancel" $h | Out-Null
  Ok ((SqlOne "SELECT status FROM sale_exchange WHERE id=$xid") -eq 'CANCELLED') 'exchange probe document cancelled'
} else { Ok $false 'sale-exchange fixtures missing' }

Write-Host ''
if ($fail -eq 0) { Write-Host 'RESULT PASS sale per-product charge (return + exchange)' } else { Write-Host ('RESULT FAIL sale per-product charge, failures=' + $fail); exit 1 }
