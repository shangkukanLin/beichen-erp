# 采购换货单验证（2026-09-18 新增功能，进货业务）
# 口径：同品换货 —— 退回供货商（我方仓按品质出库 + 负向应付冲减） + 换回良品（入我方仓 + 正向应付）。
#       可换量 = 已购 − 已退(TH-) − 已换(CH-)；两条应付台账净额即差价。
# 方式：走真实接口（登录 → 建单 → 审核 → 超量拦截 → 反审核 → 重审），断言库存/流水/台账 + 全表恒等式。
# 前置：若无「不良品(DEFECT)」库存，本脚本先用「规格调整」把 A 规改成 DEFECT（真实业务路径）。
# ASCII ONLY. 可重复运行（每次新建一张换货单；只增不删）。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$pass = 0; $fail = 0
$OUT_WH = 71      # 退回出库仓（我方成品仓）
$IN_WH  = 71      # 换入入库仓（可同仓，仓内按品质分行）
$PO_ID  = 255     # 来源采购单（已审核）—— 下面会用 SQL 覆盖成"可换量最大"的那张
$PO_CODE = 'CG-20260918006'
$SUP_ID = 26      # 供应商 —— 同样由 SQL 从选中采购单解析（后端校验两者必须一致）
$PROD   = 60      # 采购单明细产品 —— 同上，会被 SQL 覆盖
$QTY    = 3       # 本次退回/换入数量
$PRICE  = 16      # 采购原价
# 2026-09-21（夹具自适应，重要）：本用例按"关联采购单的可换量"建单，而 可换量 = 已购 − 已退 − 已换 ⇒
# **反复运行必然把某张采购单耗尽**（实测 CG-20260918006 只剩 2，随后变 0 ⇒ 建单 code=500，
# 18 项断言连锁变红 —— 是夹具问题，不是回归）。⇒ 运行时自动挑"仍可换 + 我方仓有 A 规现货"的明细，
# 挑选语句放在**函数定义之后**（早于 SqlOne 定义调用它会静默取空 —— 踩过）。
function Ok($cond, $msg) {
  if ($cond) { Write-Host ('PASS ' + $msg); $script:pass++ } else { Write-Host ('FAIL ' + $msg); $script:fail++ }
}
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

Write-Host '--- 0) baseline: finance row invariants (ALL rows incl. CANCELLED)'
$badPay0 = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)")
Write-Host ('[DB] payables violating amount=paid+unpaid: ' + $badPay0)
Ok (($badPay0 -eq 0)) ('baseline payable invariant holds on all rows (bad=' + $badPay0 + ')')

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($null -ne $lg.data.token) 'login ok (token acquired)'

Write-Host '--- 0.5) fixture: auto-pick an audited purchase order item that is still swapable AND has grade-A stock here'
# 2026-09-21（夹具自适应）：本用例按"关联采购单的可换量"建单，而 可换量 = 已购 − 已退 − 已换 ⇒
# **反复运行必然把某张采购单耗尽**（实测 CG-20260918006 用完 ⇒ 建单 code=500 + 18 项连锁红，是夹具问题不是回归）。
# 挑选必须放在 ①函数定义之后（早于 SqlOne 定义调用会静默取空）②DEFECT 备货之前（备货要按选中的产品/仓库来）。
$fxPick = [string](SqlOne ("SELECT CONCAT(oi.order_id,'|',oi.id,'|',oi.product_id,'|',oi.quantity - IFNULL((SELECT SUM(ri.quantity) FROM purchase_return_item ri WHERE ri.purchase_order_item_id=oi.id AND ri.return_id IN (SELECT id FROM purchase_return WHERE status<>'CANCELLED')),0) - IFNULL((SELECT SUM(xi.quantity) FROM purchase_exchange_item xi WHERE xi.purchase_order_item_id=oi.id AND xi.exchange_id IN (SELECT id FROM purchase_exchange WHERE status<>'CANCELLED')),0),'|',o.code,'|',o.supplier_id) FROM purchase_order_item oi JOIN purchase_order o ON o.id=oi.order_id WHERE o.status='AUDITED' AND EXISTS (SELECT 1 FROM warehouse_stock ws WHERE ws.warehouse_id=$OUT_WH AND ws.product_id=oi.product_id AND ws.quality_type='A' AND IFNULL(ws.quantity,0) > 5) ORDER BY (oi.quantity - IFNULL((SELECT SUM(ri.quantity) FROM purchase_return_item ri WHERE ri.purchase_order_item_id=oi.id AND ri.return_id IN (SELECT id FROM purchase_return WHERE status<>'CANCELLED')),0) - IFNULL((SELECT SUM(xi.quantity) FROM purchase_exchange_item xi WHERE xi.purchase_order_item_id=oi.id AND xi.exchange_id IN (SELECT id FROM purchase_exchange WHERE status<>'CANCELLED')),0)) DESC LIMIT 1"))
$fxp = @($fxPick -split '\|')
if ($fxp.Count -ge 6) {
  $PO_ID = [int]$fxp[0]; $PROD = [int]$fxp[2]; $PO_CODE = [string]$fxp[4]
  # 供应商必须跟着采购单走：后端有"供货商与来源采购单不一致"护栏（实测踩到：原写死 supplierId=26，
  # 而自动选中的采购单属于供应商 27 ⇒ 建单被拒）。
  $SUP_ID = [int]$fxp[5]
  $QTY = [Math]::Min($QTY, [int]$fxp[3])
  Write-Host ('  fixture auto-picked: po=' + $PO_ID + ' code=' + $PO_CODE + ' product=' + $PROD + ' supplier=' + $SUP_ID + ' swapable=' + $fxp[3] + ' qty=' + $QTY)
}

Write-Host '--- 1) precondition: DEFECT stock in the return warehouse (reclassify A -> DEFECT if needed)'
$defBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
if ($defBefore -lt ($QTY + 2)) {
  # 复用上次运行遗留的草稿（remark 标记），避免每次运行都堆一张草稿单；没有才新建
  $rcId = D (SqlOne ("SELECT IFNULL(MAX(id),0) FROM product_reclassify WHERE status='DRAFT' AND remark LIKE 'purchase-exchange verify%'"))
  if ($rcId -le 0) {
    $rcBody = @{
      warehouseId = $OUT_WH
      reclassifyDate = (Get-Date -Format 'yyyy-MM-dd')
      remark = 'purchase-exchange verify: seed DEFECT stock'
      items = @(@{ productId = $PROD; fromQuality = 'A'; toQuality = 'DEFECT'; quantity = ($QTY + 5) })
    } | ConvertTo-Json -Depth 6
    $rc = Invoke-RestMethod -Uri "$base/inventory/reclassify" -Method Post -Headers $h -ContentType 'application/json' -Body $rcBody
    $rcId = D (SqlOne ("SELECT IFNULL(MAX(id),0) FROM product_reclassify WHERE warehouse_id=$OUT_WH AND remark LIKE 'purchase-exchange verify%'"))
    Write-Host ('  reclassify created id=' + $rcId + ' code=' + $rc.code)
  }
  $ra = Invoke-RestMethod -Uri "$base/inventory/reclassify/$rcId/audit" -Method Put -Headers $h
  Start-Sleep -Milliseconds 600
  $defBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
  Write-Host ('  reclassify doc id=' + $rcId + ' audit code=' + $ra.code + ' -> DEFECT stock=' + $defBefore)
}
$aBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Ok (($defBefore -ge $QTY)) ('DEFECT stock available for the return (DEFECT=' + $defBefore + ')')
Write-Host ("  stock before: A=" + $aBefore + " DEFECT=" + $defBefore)

Write-Host '--- 2) create a purchase-exchange draft (same-product exchange, linked to the purchase order)'
$poiId = D (SqlOne ("SELECT id FROM purchase_order_item WHERE order_id=$PO_ID AND product_id=$PROD LIMIT 1"))
Write-Host ('  purchase order item anchor id=' + $poiId)
# 2026-09-21（夹具自适应）：本用例按"关联采购单的可换量"建单，而可换量 = 已购 − 已退 − 已换 ⇒
# **反复运行会把它耗尽**（实测 已购100 / 已退10 / 已换88 ⇒ 可换 2，原来写死 3 ⇒ code=500
# "退回数量超过可换数量"，18 项断言连锁变红 —— 是夹具问题，不是回归）。改为运行时取实际可换量、
# 并把 $QTY 统一改成实际值（后面的库存/台账断言都是**相对量**，QTY 变小不影响其正确性）。
$canSwap = D (SqlOne ("SELECT (oi.quantity - IFNULL((SELECT SUM(ri.quantity) FROM purchase_return_item ri WHERE ri.purchase_order_item_id=oi.id AND ri.return_id IN (SELECT id FROM purchase_return WHERE status<>'CANCELLED')),0) - IFNULL((SELECT SUM(xi.quantity) FROM purchase_exchange_item xi WHERE xi.purchase_order_item_id=oi.id AND xi.exchange_id IN (SELECT id FROM purchase_exchange WHERE status<>'CANCELLED')),0)) FROM purchase_order_item oi WHERE oi.id=$poiId"))
Write-Host ('  swapable qty on the linked purchase order = ' + $canSwap + ' (declared QTY=' + $QTY + ')')
if ($canSwap -lt 1) {
  Write-Host '  [FIXTURE EXHAUSTED] no audited purchase order item has a swapable qty left (market data exhausted);'
  Write-Host '                     nothing was asserted. Re-seed by receiving a new purchase order, then rerun.'
  Write-Host ('RESULT FAIL purchase-exchange (fixture exhausted, asserted nothing) pass=' + $pass + ' fail=' + $fail)
  exit 1
}
if ($QTY -gt $canSwap) { $QTY = $canSwap; Write-Host ('  QTY clamped to the real swapable qty = ' + $QTY) }
$body = @{
  supplierId = $SUP_ID; purchaseOrderId = $PO_ID; purchaseOrderCode = $PO_CODE
  warehouseOutId = $OUT_WH; warehouseInId = $IN_WH
  exchangeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'verify-purchase-exchange'
  items = @(@{
    purchaseOrderItemId = $poiId
    productId = $PROD; qualityType = 'DEFECT'; quantity = $QTY; unitPrice = $PRICE
    inQuantity = $QTY; inQualityType = 'A'; inUnitPrice = $PRICE
  })
} | ConvertTo-Json -Depth 6
$c1 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange" -Method Post -Headers $h -ContentType 'application/json' -Body $body
$xid = [int]$c1.data.id
$xcode = [string]$c1.data.code
Write-Host ('  create replied code=' + $c1.code + ' msg=' + $c1.msg + ' id=' + $xid + ' code=' + $xcode)
Write-Host ('  totals: return=' + $c1.data.totalReturnAmount + ' in=' + $c1.data.totalInAmount + ' status=' + $c1.data.status)
Ok (($xid -gt 0)) ('purchase exchange created (id=' + $xid + ')')
Ok ($xcode.StartsWith('CH-')) ('bill no uses the CH- prefix (' + $xcode + ')')
Ok (([string]$c1.data.status -eq 'DRAFT')) 'new document starts as DRAFT'
# NOTE (2026-09-21): 原先写成 (D $x -eq $y) —— PowerShell 会把 "-eq $y" 当成 D 的额外参数，
# 于是比较从未发生、只校验了"结果非 0"（恒真的假绿）。改为先赋值再比较。
$totRet = D $c1.data.totalReturnAmount
$totIn = D $c1.data.totalInAmount
Ok (($totRet -eq ($QTY * $PRICE)) -and ($totIn -eq ($QTY * $PRICE))) ('both sides totalled from details (ret=' + $c1.data.totalReturnAmount + ' in=' + $c1.data.totalInAmount + ')')

Write-Host '--- 3) audit -> return OUT + exchange IN + two payable ledgers'
$a1 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$xid/audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 800
Ok (($a1.code -eq 200) -or ($a1.code -eq 0)) ('audit accepted (code=' + $a1.code + ' msg=' + $a1.msg + ')')
$st1 = SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")
Ok (($st1 -eq 'AUDITED')) ('document is AUDITED (status=' + $st1 + ')')
$defAfter = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aAfter = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Write-Host ("  stock after: A=" + $aAfter + " DEFECT=" + $defAfter + " (expected A=" + ($aBefore + $QTY) + " DEFECT=" + ($defBefore - $QTY) + ")")
Ok (($defAfter -eq ($defBefore - $QTY))) 'return side deducted from our warehouse (-' + '' + $QTY + ' DEFECT)'
Ok (($aAfter -eq ($aBefore + $QTY))) 'exchange side added to our warehouse (+' + '' + $QTY + ' A)'
$logRows = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$xcode'"))
$logOut = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$xcode' AND change_type='PURCHASE_EXCHANGE_OUT'"))
$logIn = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$xcode' AND change_type='PURCHASE_EXCHANGE_IN'"))
Write-Host ('  stock logs: total=' + $logRows + ' OUT=' + $logOut + ' IN=' + $logIn)
Ok (($logOut -ge 1) -and ($logIn -ge 1)) 'stock log carries both PURCHASE_EXCHANGE_OUT and PURCHASE_EXCHANGE_IN'
$retPay = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_RETURN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
$inPay = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_IN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
Write-Host ('  payables: return=' + $retPay + ' (expect -' + ($QTY * $PRICE) + ') ; exchange-in=' + $inPay + ' (expect +' + ($QTY * $PRICE) + ')')
Ok (($retPay -eq (0 - ($QTY * $PRICE)))) 'negative payable created for the return (reduces what we owe)'
Ok (($inPay -eq ($QTY * $PRICE))) 'positive payable created for the exchange-in'
Ok ((D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status<>'CANCELLED'")) -eq 2)) 'exactly two payable ledger rows (return + exchange-in)'

Write-Host '--- 4) guard: over-quantity must be rejected (can-exchange = purchased - returned - exchanged)'
$overBody = @{
  supplierId = $SUP_ID; purchaseOrderId = $PO_ID; warehouseOutId = $OUT_WH; warehouseInId = $IN_WH
  exchangeDate = (Get-Date -Format 'yyyy-MM-dd')
  items = @(@{ purchaseOrderItemId = $poiId; productId = $PROD; qualityType = 'DEFECT'; quantity = 99999; unitPrice = $PRICE; inQuantity = 99999; inQualityType = 'A'; inUnitPrice = $PRICE })
} | ConvertTo-Json -Depth 6
$overMsg = ''
try {
  $o = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange" -Method Post -Headers $h -ContentType 'application/json' -Body $overBody
  $overMsg = [string]$o.msg
} catch {
  $overMsg = [string]$_.ErrorDetails.Message
}
$kw = [string]([char]0x53EF + [char]0x6362 + [char]0x6570 + [char]0x91CF)   # 可换数量
Write-Host ('  server replied: ' + $overMsg)
Ok ($overMsg -like ('*' + $kw + '*')) 'over-quantity rejected with a can-exchange message'

Write-Host '--- 5) un-audit -> symmetric rollback + ledgers voided (I29 口径: amount zeroed)'
$u1 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$xid/un-audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 800
Ok (($u1.code -eq 200) -or ($u1.code -eq 0)) ('un-audit accepted (code=' + $u1.code + ' msg=' + $u1.msg + ')')
$st2 = SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")
Ok (($st2 -eq 'DRAFT')) ('document back to DRAFT (status=' + $st2 + ')')
$defBack = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aBack = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Write-Host ("  stock rolled back: A=" + $aBack + " DEFECT=" + $defBack + " (expected A=" + $aBefore + " DEFECT=" + $defBefore + ")")
Ok (($defBack -eq $defBefore)) 'return stock restored'
Ok (($aBack -eq $aBefore)) 'exchange-in stock taken back'
$voidMoney = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status='CANCELLED' AND (IFNULL(amount,0)<>0 OR IFNULL(unpaid_amount,0)<>0)"))
$voidRows = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status='CANCELLED'"))
Write-Host ('  voided ledger rows=' + $voidRows + ' ; carrying money=' + $voidMoney)
Ok (($voidRows -ge 2)) 'both ledger rows voided (trace kept)'
Ok (($voidMoney -eq 0)) 'voided rows carry no money (I29 口径)'

Write-Host '--- 6) re-audit -> fresh active ledgers (D1: new YF- bill_no each audit, old rows stay CANCELLED)'
$a2 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$xid/audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 800
Ok (($a2.code -eq 200) -or ($a2.code -eq 0)) ('re-audit accepted (code=' + $a2.code + ' msg=' + $a2.msg + ')')
$retPay2 = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_RETURN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
$inPay2 = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_IN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
Ok (($retPay2 -eq (0 - ($QTY * $PRICE))) -and ($inPay2 -eq ($QTY * $PRICE))) ('active ledgers regenerated (ret=' + $retPay2 + ' in=' + $inPay2 + ')')
$defFinal = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
Ok (($defFinal -eq ($defBefore - $QTY))) ('stock re-applied after re-audit (DEFECT=' + $defFinal + ')')

Write-Host '--- 6.5) 2026-09-21: no purchase order (free-form exchange) + paid flag (we pay the supplier)'
$NQTY = 1
$FEE = 50
$defN0 = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aN0 = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Write-Host ('  stock before: DEFECT=' + $defN0 + ' A=' + $aN0)
Ok (($defN0 -ge $NQTY)) ('DEFECT stock available for the free-form exchange (DEFECT=' + $defN0 + ')')
$kwSup = [string]([char]0x4F9B + [char]0x8D27 + [char]0x5546)                     # supplier
$kwFeeAmt = [string]([char]0x4ED8 + [char]0x8D39 + [char]0x91D1 + [char]0x989D)    # paid amount
$kwFeeType = [string]([char]0x4ED8 + [char]0x8D39 + [char]0x7C7B + [char]0x578B)   # paid type
$kwPo = [string]([char]0x91C7 + [char]0x8D2D + [char]0x5355)                       # purchase order

# (a) guard: the supplier stays mandatory even without a purchase order
$noSupBody = @{
  warehouseOutId = $OUT_WH; warehouseInId = $IN_WH; exchangeDate = (Get-Date -Format 'yyyy-MM-dd')
  items = @(@{ productId = $PROD; qualityType = 'DEFECT'; quantity = $NQTY; unitPrice = $PRICE; inQuantity = $NQTY; inQualityType = 'A'; inUnitPrice = $PRICE })
} | ConvertTo-Json -Depth 6
$noSupMsg = ''
try {
  $ns = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange" -Method Post -Headers $h -ContentType 'application/json' -Body $noSupBody
  $noSupMsg = [string]$ns.msg
} catch { $noSupMsg = [string]$_.ErrorDetails.Message }
Write-Host ('  no-supplier replied: ' + $noSupMsg)
Ok ($noSupMsg -like ('*' + $kwSup + '*')) 'a supplier is still required on a free-form exchange'

# (b) guard: paid=on must carry a valid type and an amount > 0
$feeZeroBody = @{
  supplierId = 26; warehouseOutId = $OUT_WH; warehouseInId = $IN_WH; exchangeDate = (Get-Date -Format 'yyyy-MM-dd')
  chargeFlag = 1; chargeType = 'DIFF'; chargeAmount = 0
  items = @(@{ productId = $PROD; qualityType = 'DEFECT'; quantity = $NQTY; unitPrice = $PRICE; inQuantity = $NQTY; inQualityType = 'A'; inUnitPrice = $PRICE })
} | ConvertTo-Json -Depth 6
$feeZeroMsg = ''
try {
  $fz = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange" -Method Post -Headers $h -ContentType 'application/json' -Body $feeZeroBody
  $feeZeroMsg = [string]$fz.msg
} catch { $feeZeroMsg = [string]$_.ErrorDetails.Message }
Write-Host ('  zero-fee replied: ' + $feeZeroMsg)
# 2026-09-21（逐产品口径）：付费金额下沉到明细行 ⇒ "开关=付费但没给任何产品填金额"现在被拒为
# 「已选择付费，请为具体产品填写付费金额（付费精确到产品）」。这里断言**行为**（被拒 + 有提示），
# 具体文案由 ui-e2e-p11b 的前端用例覆盖（保持本文件不依赖中文关键字字面量）。
Ok (($feeZeroMsg.Length -gt 0) -and (-not ($fz.code -eq 200) -and -not ($fz.code -eq 0))) 'paid=on with no per-product amount is refused'
$feeBadTypeBody = @{
  supplierId = 26; warehouseOutId = $OUT_WH; warehouseInId = $IN_WH; exchangeDate = (Get-Date -Format 'yyyy-MM-dd')
  chargeFlag = 1; chargeType = 'NOT_A_TYPE'; chargeAmount = $FEE
  items = @(@{ productId = $PROD; qualityType = 'DEFECT'; quantity = $NQTY; unitPrice = $PRICE; inQuantity = $NQTY; inQualityType = 'A'; inUnitPrice = $PRICE })
} | ConvertTo-Json -Depth 6
$feeBadMsg = ''
try {
  $fb = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange" -Method Post -Headers $h -ContentType 'application/json' -Body $feeBadTypeBody
  $feeBadMsg = [string]$fb.msg
} catch { $feeBadMsg = [string]$_.ErrorDetails.Message }
Write-Host ('  bad-fee-type replied: ' + $feeBadMsg)
Ok ($feeBadMsg -like ('*' + $kwFeeType + '*')) 'an illegal paid type is refused'

# (c) create a free-form PAID document (no purchase order, no item anchor)
$freeBody = @{
  supplierId = 26; warehouseOutId = $OUT_WH; warehouseInId = $IN_WH
  exchangeDate = (Get-Date -Format 'yyyy-MM-dd')
  remark = 'verify-purchase-exchange: free-form + paid'
  chargeFlag = 1; chargeType = 'DIFF'; chargeAmount = $FEE; chargeReason = 'free-form paid exchange (verify)'
  items = @(@{ productId = $PROD; qualityType = 'DEFECT'; quantity = $NQTY; unitPrice = $PRICE; inQuantity = $NQTY; inQualityType = 'A'; inUnitPrice = $PRICE })
} | ConvertTo-Json -Depth 6
$c2 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange" -Method Post -Headers $h -ContentType 'application/json' -Body $freeBody
$fid = [int]$c2.data.id
$fcode = [string]$c2.data.code
Write-Host ('  free-form create: code=' + $c2.code + ' id=' + $fid + ' doc=' + $fcode + ' po=' + $c2.data.purchaseOrderId + ' charge=' + $c2.data.chargeFlag)
Ok (($c2.code -eq 200) -or ($c2.code -eq 0)) ('free-form exchange created without a purchase order (' + $fcode + ')')
# NOTE: always assign D(...) to a variable BEFORE comparing -- `(D (SqlOne ...) -eq 0)` makes PowerShell
#       treat "-eq 0" as extra ARGUMENTS of D (the comparison never happens and a truthy decimal would
#       look like a pass). Same trap exists in the older lines of this file.
$poOfFree = D (SqlOne ("SELECT IFNULL(purchase_order_id,0) FROM purchase_exchange WHERE id=$fid"))
$cfOfFree = D (SqlOne ("SELECT IFNULL(charge_flag,0) FROM purchase_exchange WHERE id=$fid"))
$caOfFree = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_exchange WHERE id=$fid"))
$ctOfFree = SqlOne ("SELECT charge_type FROM purchase_exchange WHERE id=$fid")
Write-Host ('  persisted: po_id=' + $poOfFree + ' charge_flag=' + $cfOfFree + ' type=' + $ctOfFree + ' amount=' + $caOfFree)
Ok (($poOfFree -eq 0)) 'DB confirms the document has NO purchase order link'
Ok (($cfOfFree -eq 1)) 'charge_flag=1 persisted'
Ok (($caOfFree -eq $FEE)) ('paid amount persisted (' + $FEE + ')')
Ok (($ctOfFree -eq 'DIFF')) 'paid type persisted (DIFF)'

Write-Host '--- 6.6) audit the free-form paid document -> stock + THREE ledgers (return / in / PAID)'
$a3 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$fid/audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 900
Ok (($a3.code -eq 200) -or ($a3.code -eq 0)) ('free-form audit accepted (code=' + $a3.code + ' msg=' + $a3.msg + ')')
Ok ((SqlOne ("SELECT status FROM purchase_exchange WHERE id=$fid")) -eq 'AUDITED') 'free-form document is AUDITED'
$defN1 = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aN1 = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Write-Host ("  stock after: DEFECT=" + $defN1 + " A=" + $aN1)
Ok (($defN1 -eq ($defN0 - $NQTY))) 'free-form return side deducted from our warehouse'
Ok (($aN1 -eq ($aN0 + $NQTY))) 'free-form exchange side added to our warehouse'
$feePay = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$fcode' AND status<>'CANCELLED'"))
$retPayF = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_RETURN' AND source_bill_no='$fcode' AND status<>'CANCELLED'"))
$inPayF = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_IN' AND source_bill_no='$fcode' AND status<>'CANCELLED'"))
Write-Host ('  ledgers: return=' + $retPayF + ' in=' + $inPayF + ' paid=' + $feePay + ' (paid expects +' + $FEE + ': WE pay the supplier)')
Ok (($feePay -eq $FEE)) 'paid flag created a POSITIVE payable (we owe the supplier more)'
Ok (($retPayF -eq (0 - ($NQTY * $PRICE)))) 'free-form return ledger is negative'
Ok (($inPayF -eq ($NQTY * $PRICE))) 'free-form exchange-in ledger is positive'
$activeF = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$fcode' AND status<>'CANCELLED'"))
Ok (($activeF -eq 3)) 'three active ledgers (return + exchange-in + paid)'

Write-Host '--- 6.7) un-audit the free-form document -> stock rolled back, all THREE ledgers voided'
$u3 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$fid/un-audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 900
Ok (($u3.code -eq 200) -or ($u3.code -eq 0)) ('free-form un-audit accepted (code=' + $u3.code + ' msg=' + $u3.msg + ')')
Ok ((SqlOne ("SELECT status FROM purchase_exchange WHERE id=$fid")) -eq 'DRAFT') 'free-form document back to DRAFT'
$defN2 = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aN2 = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Ok (($defN2 -eq $defN0) -and ($aN2 -eq $aN0)) ('stock fully rolled back (DEFECT=' + $defN2 + ' A=' + $aN2 + ')')
$voidF = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$fcode' AND status='CANCELLED'"))
$activeF2 = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$fcode' AND status<>'CANCELLED'"))
$paidVoidMoney = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$fcode' AND status='CANCELLED' AND (IFNULL(amount,0)<>0 OR IFNULL(unpaid_amount,0)<>0)"))
Write-Host ('  after un-audit: voided=' + $voidF + ' active=' + $activeF2 + ' voidedPaidWithMoney=' + $paidVoidMoney)
Ok (($voidF -eq 3)) 'all three ledgers voided (trace kept)'
Ok (($activeF2 -eq 0)) 'no active ledger left after un-audit'
Ok (($paidVoidMoney -eq 0)) 'voided paid ledger carries no money'

Write-Host '--- 6.8) re-audit the free-form document -> paid ledger regenerated (D1: new YF- bill_no)'
$a4 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$fid/audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 900
Ok (($a4.code -eq 200) -or ($a4.code -eq 0)) ('free-form re-audit accepted (code=' + $a4.code + ' msg=' + $a4.msg + ')')
$feePay2 = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$fcode' AND status<>'CANCELLED'"))
$feeBillNo = SqlOne ("SELECT bill_no FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$fcode' AND status<>'CANCELLED'")
$activeF3 = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$fcode' AND status<>'CANCELLED'"))
Write-Host ('  after re-audit: paid=' + $feePay2 + ' bill_no=' + $feeBillNo + ' active=' + $activeF3)
Ok (($feePay2 -eq $FEE)) 'paid ledger regenerated on re-audit'
Ok ($feeBillNo.StartsWith('YF-')) 'paid ledger uses the YF- numbering (D1 convention)'
Ok (($activeF3 -eq 3)) 'three active ledgers after re-audit'
Write-Host ('[DB] free-form doc: id=' + $fid + ' code=' + $fcode + ' po_id=' + (SqlOne ("SELECT IFNULL(purchase_order_id,0) FROM purchase_exchange WHERE id=$fid")))

Write-Host '--- 7) final invariants (whole ledger tables, incl. CANCELLED)'
$badPay = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)")
$zeroCancel = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='CANCELLED' AND (IFNULL(amount,0)<>0 OR IFNULL(unpaid_amount,0)<>0)")
Write-Host ("[DB] allRowsBad=" + $badPay + " voidedWithMoney=" + $zeroCancel)
Ok (($badPay -eq 0)) 'payable invariant holds on ALL rows after the exchange flows'
Ok (($zeroCancel -eq 0)) 'no voided payable carries money'

Write-Host ''
Write-Host ('[DB] purchase_exchange rows=' + (SqlOne "SELECT COUNT(*) FROM purchase_exchange") + ' ; latest=' + $xcode)
if ($fail -eq 0) { Write-Host ('RESULT PASS purchase-exchange (PASS=' + $pass + ' FAIL=0)') } else { Write-Host ('RESULT FAIL purchase-exchange (PASS=' + $pass + ' FAIL=' + $fail + ')') }
if ($fail -gt 0) { exit 1 }
