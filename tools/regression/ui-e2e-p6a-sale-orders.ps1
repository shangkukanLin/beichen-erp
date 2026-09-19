# P6a (2026-09-18 full-flow E2E): sale orders x10 (6 cash + 4 credit), create + audit (audit = out of stock = 发货).
#   现金单：审核后系统自动生成**草稿**收款单（P6d 再审核核销）。
# ALL DATA KEPT; rerunnable (skips creation when enough orders exist). ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function SqlList([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { (($_ -split "`t")[0]).Trim() } | Where-Object { $_ -ne '' })
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function RowInputsT([int]$tblIdx, [int]$rowIdx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW';return JSON.stringify([...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')].map(e=>e.value))})()"
  return (EvalJs $js)
}

# ---- 结算方式开关（2026-09-18 用户要求：新增销售单页由下拉改为 el-switch，开=现金 / 关=账期）----
function SettleSwitchInfo {
  $lbl = B64 (ZH 'lbl_settle_type')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$lbl');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);const it=items.find(x=>(((x.querySelector('.el-form-item__label')||{}).innerText)||'').indexOf(L)>=0);if(!it)return 'NOITEM';const sw=it.querySelector('.el-switch');if(!sw)return 'NOSWITCH';return JSON.stringify({checked:sw.classList.contains('is-checked'),text:(sw.innerText||'').replace(/\\s+/g,'')})})()"
  return (EvalJs $js)
}
function ClickSettleSwitch([bool]$wantCash) {
  $info = SettleSwitchInfo
  if ($info -notmatch 'checked') { return ('NOINFO:' + $info) }
  $o = $null
  try { $o = $info | ConvertFrom-Json } catch { return ('BADJSON:' + $info) }
  if ([bool]$o.checked -eq $wantCash) { return ('ALREADY:' + $info) }
  $lbl = B64 (ZH 'lbl_settle_type')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$lbl');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);const it=items.find(x=>(((x.querySelector('.el-form-item__label')||{}).innerText)||'').indexOf(L)>=0);if(!it)return 'NOITEM';const sw=it.querySelector('.el-switch');if(!sw)return 'NOSWITCH';sw.click();return 'OK'})()"
  Start-Sleep -Milliseconds 400
  return ((EvalJs $js) + ' -> ' + (SettleSwitchInfo))
}

$TARGET = 10
$CASH = 6
$custPfx = ZH 'val_customer'
$acct = 'CASH-01'
# stock-driven, ONE-TABLE-AT-A-TIME: the (warehouse, product) pair must come from the SAME stock row,
# otherwise the sale line shows 可用 0 / 不足 and the order cannot be audited.
# NOTE: multi-table JOIN queries in this MySQL silently return nothing when called through the CLI here,
# so we resolve id -> name with plain single-table lookups (the shape that has been reliable all along).
$stkIdx = SqlOne "SELECT id FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity>20 ORDER BY quantity DESC LIMIT 1"
$whId = [int](SqlOne ("SELECT warehouse_id FROM warehouse_stock WHERE id=" + $stkIdx))
$pIdA = [int](SqlOne ("SELECT product_id FROM warehouse_stock WHERE id=" + $stkIdx))
$whProd = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + $whId)
# NOTE: the product table's name column is `name` (NOT product_name) — using the wrong column name makes
# mysql error out and (because we swallow stderr) silently return nothing.
$prodA = SqlOne ("SELECT name FROM product WHERE id=" + $pIdA)
$pIdB = [int](SqlOne ("SELECT product_id FROM warehouse_stock WHERE warehouse_id=" + $whId + " AND quantity>20 AND product_id NOT IN (" + $pIdA + ") ORDER BY quantity DESC LIMIT 1"))
$prodB = $prodA
if ($pIdB -gt 0) { $prodB = SqlOne ("SELECT name FROM product WHERE id=" + $pIdB) }
Write-Host ('[SEED] stock row=' + $stkIdx + ' wh=' + $whProd + '(' + $whId + ') prodA=' + $prodA + '(' + $pIdA + ') prodB=' + $prodB + '(' + $pIdB + ')')
Ok (($whProd -ne '') -and ($prodA -ne '')) 'resolved a stock-backed (warehouse, product) pair'
if (($whProd -eq '') -or ($prodA -eq '')) { Summary 'P6a sale orders + shipping'; exit 1 }
$pairs = New-Object System.Collections.Generic.List[object]
$pairs.Add([pscustomobject]@{ Wh = $whProd; Prod = $prodA })
$pairs.Add([pscustomobject]@{ Wh = $whProd; Prod = $prodB })
$prodIdA = $pIdA
$prodIdB = $pIdB
$qty = 5
$price = 1000
$before = D (SqlOne 'SELECT COUNT(*) FROM sale_order')
$need = [int]([Math]::Max(0, $TARGET - $before))
$stockA = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $prodIdA))
$stockB = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $prodIdB))
Write-Host ('[BASE] sale orders=' + $before + ' needCreate=' + $need + ' stock ' + $prodA + '=' + $stockA + ' ' + $prodB + '=' + $stockB)

if ($need -gt 0) {
  for ($i = ($before + 1); $i -le $TARGET; $i++) {
    $isCash = ($i -le $CASH)
    $pair = $pairs[($i - 1) % $pairs.Count]
    $prod = $pair.Prod
    $whThis = $pair.Wh
    Step ('sale order #' + $i + ' (' + $(if ($isCash) { 'CASH' } else { 'CREDIT' }) + ', ' + $whThis + '/' + $prod + ')')
    Open '/inventory/sale' 3000
    ClearErrs | Out-Null
    Write-Host ('  new: ' + (ClickBtn 'btn_new'))
    Start-Sleep -Milliseconds 2400
    Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
    Write-Host ('  customer: ' + (SelectLabelContains 'lbl_customer' ($custPfx + $i)))
    Start-Sleep -Milliseconds 900
    Write-Host ('  out wh: ' + (SelectLabelContains 'lbl_out_wh' $whThis))
    Start-Sleep -Milliseconds 900
    if ($isCash) {
      Write-Host ('  settle switch -> cash: ' + (ClickSettleSwitch $true))
      Start-Sleep -Milliseconds 1200
      Write-Host ('  account: ' + (SelectLabelContains 'lbl_settle_account' $acct))
      Start-Sleep -Milliseconds 900
    } else {
      Write-Host ('  settle switch stays credit: ' + (SettleSwitchInfo))
    }
    $rc = Rows 0
    if ($rc.n -lt 1) { Write-Host ('  add row: ' + (ClickBtn 'btn_add_product')); Start-Sleep -Milliseconds 900 }
    OpenRowSelect 0 0 | Out-Null
    Start-Sleep -Milliseconds 1500
    Write-Host ('  product: ' + (PickOptionContains $prod))
    Start-Sleep -Milliseconds 800
    OpenRowSelect 0 1 | Out-Null
    Start-Sleep -Milliseconds 1100
    Write-Host ('  quality: ' + (PickOptionContains (ZH 'opt_q_a')))
    Start-Sleep -Milliseconds 700
    Write-Host ('  qty: ' + (SetRowInputT 0 0 2 ('' + $qty)))
    Start-Sleep -Milliseconds 500
    Write-Host ('  price: ' + (SetRowInputT 0 0 3 ('' + $price)))
    Start-Sleep -Milliseconds 700
    $rowVals = RowInputsT 0 0
    Write-Host ('  row inputs=' + $rowVals)
    Ok (($rowVals -match ([regex]::Escape([string]$price) + '|1000'))) ('sale line price filled (row=' + $rowVals + ')')
    Write-Host ('  save: ' + (ClickBtn 'btn_save'))
    Start-Sleep -Milliseconds 900
    $t1 = Txt '.el-message'
    Write-Host ('  toast@900=' + $t1)
    Start-Sleep -Milliseconds 2000
    $cnt = D (SqlOne 'SELECT COUNT(*) FROM sale_order')
    Ok (($cnt -eq ($before + ($i - $before)))) ('sale order #' + $i + ' created (db=' + $cnt + $(if ($cnt -eq $before) { ', toast=' + $t1 } else { '' }) + ')')
  }
}

Step 'audit DRAFT sale orders (audit = stock out = 发货)'
foreach ($c in (SqlList "SELECT code FROM sale_order WHERE status='DRAFT' ORDER BY id")) {
  Open '/inventory/sale' 3000
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('sale order row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('  audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1300 | Out-Null
    Start-Sleep -Milliseconds 3200
    $st = SqlOne ("SELECT status FROM sale_order WHERE code='" + $c + "'")
    $msg = Txt '.el-message'
    Write-Host ('  -> status=' + $st + ' msg=' + $msg)
    Ok ($st -eq 'AUDITED') ('sale order ' + $c + ' audited' + $(if ($st -ne 'AUDITED') { ' (' + $msg + ')' } else { '' }))
  }
}

Step 'DB cross-check'
$orders = D (SqlOne 'SELECT COUNT(*) FROM sale_order')
$audited = D (SqlOne "SELECT COUNT(*) FROM sale_order WHERE status='AUDITED'")
$cashAudited = D (SqlOne "SELECT COUNT(*) FROM sale_order WHERE status='AUDITED' AND settle_type='CASH'")
$creditAudited = D (SqlOne "SELECT COUNT(*) FROM sale_order WHERE status='AUDITED' AND (settle_type IS NULL OR settle_type='CREDIT')")
$items = D (SqlOne 'SELECT COUNT(*) FROM sale_order_item')
$qtySum = D (SqlOne 'SELECT COALESCE(SUM(quantity),0) FROM sale_order_item')
$amountSum = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM sale_order WHERE status='AUDITED'")
$autoReceipts = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE source_bill_no LIKE 'XS-%'")
$autoReceiptDraft = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE source_bill_no LIKE 'XS-%' AND status='DRAFT'")
$autoReceiptAmt = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receipt WHERE source_bill_no LIKE 'XS-%'")
$recvAmt = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$recvRows = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$outLogs = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no LIKE 'XS-%'")
$stockA2 = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $prodIdA))
$stockB2 = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $prodIdB))
Write-Host ("[DB] orders=$orders audited=$audited cash=$cashAudited credit=$creditAudited items=$items qty=$qtySum amount=$amountSum")
Write-Host ("[DB] autoReceipts=$autoReceipts (draft=$autoReceiptDraft amt=$autoReceiptAmt) receivableRows=$recvRows recvAmt=$recvAmt outLogs=$outLogs")
Ok (($orders -ge $TARGET)) ('sale orders >= 10 (got ' + $orders + ')')
Ok (($audited -eq $orders)) 'all sale orders audited (= 发货完成)'
Ok (($cashAudited -ge 6)) ('cash-settled orders >= 6 (got ' + $cashAudited + ')')
Ok (($creditAudited -ge 4)) ('credit-settled orders >= 4 (got ' + $creditAudited + ')')
Ok (($items -ge $TARGET)) ('sale items >= 10 (got ' + $items + ')')
Ok (($qtySum -ge ($TARGET * $qty))) ('sold quantity >= ' + ($TARGET * $qty) + ' (got ' + $qtySum + ')')
Ok (($autoReceipts -ge 6)) ('auto draft receipts for cash orders >= 6 (got ' + $autoReceipts + ')')
# 现金单的自动收款单：DRAFT（旧口径遗留）/ AUDITED（现行"立刻到账"）/ CANCELLED（反审核冲正后的留痕）都正常；
# 关键护栏是**同一销售单不允许存在 >1 张有效收款单**（防重复挂账）。
$badRc = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE source_bill_no LIKE 'XS-%' AND status NOT IN ('DRAFT','AUDITED','CANCELLED')")
Ok (($badRc -eq 0)) ('no auto receipt in an unexpected state (bad=' + $badRc + ')')
$dupRc = D (SqlOne "SELECT COUNT(*) FROM (SELECT source_bill_no FROM finance_receipt WHERE source_bill_no LIKE 'XS-%' AND status IN ('DRAFT','AUDITED') GROUP BY source_bill_no HAVING COUNT(*)>1) t")
Ok (($dupRc -eq 0)) ('no cash order carries more than one active receipt (dup=' + $dupRc + ')')
Ok (($recvRows -eq $orders)) ('one receivable per sale order (got ' + $recvRows + ')')
Ok (($recvAmt -eq $amountSum)) ('receivable == sale amount (' + $recvAmt + ' = ' + $amountSum + ')')
Ok (($outLogs -ge $orders)) ('sale stock-out logs >= orders (got ' + $outLogs + ')')
Write-Host ('[STOCK] ' + $prodA + ' ' + $stockA + ' -> ' + $stockA2 + ' | ' + $prodB + ' ' + $stockB + ' -> ' + $stockB2)
# 可重跑口径：只比对"本轮真正新建订单"发出的数量（skipCreate 时该值为 0 -> 库存不变才是正确结果）
$shippedThisRun = [decimal](($orders - $before) * $qty)
$deltaAB = ($stockA - $stockA2) + ($stockB - $stockB2)
Ok (($deltaAB -eq $shippedThisRun)) ('stock decrease == qty shipped by this run (' + $deltaAB + ' = ' + $shippedThisRun + ')')
Write-Host ('errs=' + (Errs))
Summary 'P6a sale orders + shipping'
