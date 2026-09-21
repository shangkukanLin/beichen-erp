# P6b (2026-09-18 full-flow E2E): sale returns x3 + receipt settlement (收款核销).
#   1) /sale/return: 3 returns (2 x MFTESTE2E2, 1 x MFTESTE2E1) -> audit (stock back, receivable reduced)
#   2) /finance/receipt: audit the 6 auto-generated DRAFT receipts (cash sale orders) -> 核销
#      then probe the page for a manual 新增 receipt entry (printed for the next step).
# ALL DATA KEPT; rerunnable. ASCII ONLY.
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
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}

# stock-backed product + warehouse (same resolution style as P6a)
$stkId = SqlOne "SELECT id FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity>20 ORDER BY quantity DESC LIMIT 1"
$whId = [int](SqlOne ("SELECT warehouse_id FROM warehouse_stock WHERE id=" + $stkId))
$pIdA = [int](SqlOne ("SELECT product_id FROM warehouse_stock WHERE id=" + $stkId))
$whName = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + $whId)
$pNameA = SqlOne ("SELECT name FROM product WHERE id=" + $pIdA)
$pIdB = [int](SqlOne ("SELECT product_id FROM warehouse_stock WHERE warehouse_id=" + $whId + " AND quantity>20 AND product_id NOT IN (" + $pIdA + ") ORDER BY quantity DESC LIMIT 1"))
$pNameB = $pNameA
if ($pIdB -gt 0) { $pNameB = SqlOne ("SELECT name FROM product WHERE id=" + $pIdB) }
Write-Host ('[SEED] wh=' + $whName + ' prodA=' + $pNameA + '(' + $pIdA + ') prodB=' + $pNameB + '(' + $pIdB + ')')
$stkABefore = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $pIdA))
$stkBBefore = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $pIdB))
$recvBefore = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$retBefore = D (SqlOne 'SELECT COUNT(*) FROM sale_return')
$rcptAuditedBefore = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='AUDITED'")
Write-Host ('[BASE] sale returns=' + $retBefore + ' receivable=' + $recvBefore + ' stockA=' + $stkABefore + ' stockB=' + $stkBBefore)

Step 'sale returns (up to 5) + audit'
# NOTE: the first 3 returns were created while the qty was written to the WRONG input index (price), so they
# went in with qty=1 each. Rather than delete data we keep them and add 2 more with the corrected index,
# which also proves the fix; the stock-delta invariant below keeps everything honest.
$TARGET_RET = 5
$need = [int]([Math]::Max(0, $TARGET_RET - $retBefore))
$newRetQty = [decimal]0
for ($i = 1; $i -le $need; $i++) {
  $prod = $pNameA
  if ($i -eq 1) { $prod = $pNameB }
  Write-Host ('--- sale return #' + $i + ' (' + $prod + ')')
  Open '/sale/return' 3000
  ClearErrs | Out-Null
  Write-Host ('  new: ' + (ClickBtn 'btn_new_return'))
  Start-Sleep -Milliseconds 2400
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
  Write-Host ('  customer: ' + (SelectLabelContains 'lbl_customer' ((ZH 'val_customer') + '1')))
  Start-Sleep -Milliseconds 900
  Write-Host ('  return wh: ' + (SelectLabelContains 'lbl_return_warehouse' $whName))
  Start-Sleep -Milliseconds 900
  $rb = Rows 0
  if ($rb.n -lt 1) { Write-Host ('  add row: ' + (ClickBtn 'btn_add_detail')); Start-Sleep -Milliseconds 900 }
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1500
  Write-Host ('  product: ' + (PickOptionContains $prod))
  Start-Sleep -Milliseconds 800
  # the return line has NO quality column -> inputs are [product, qty, price] (index 1 = qty)
  Write-Host ('  qty: ' + (SetRowInputT 0 0 1 '5'))
  Start-Sleep -Milliseconds 700
  Write-Host ('  save: ' + (ClickBtn 'btn_save'))
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM sale_return')
  Ok (($cnt -eq ($retBefore + $i))) ('sale return #' + $i + ' created (db=' + $cnt + ')')
  if ($cnt -gt ($retBefore + $i - 1)) { $newRetQty += 5 }
}

foreach ($c in (SqlList "SELECT code FROM sale_return WHERE status='DRAFT' ORDER BY id")) {
  Open '/sale/return' 2800
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('sale return row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('  audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1200 | Out-Null
    Start-Sleep -Milliseconds 3000
    $st = SqlOne ("SELECT status FROM sale_return WHERE code='" + $c + "'")
    Write-Host ('  -> status=' + $st + ' msg=' + (Txt '.el-message'))
    Ok ($st -eq 'AUDITED') ('sale return ' + $c + ' audited')
  }
}

Step 'receipt page anatomy (for the manual-entry step)'
Open '/finance/receipt' 3000
$rbtns = "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('button')].filter(vis).map(b=>(b.innerText||'').trim()).filter(t=>t))})()"
Write-Host ('receipt page buttons=' + (EvalJs $rbtns))

Step 'audit the auto-generated DRAFT receipts (收款核销)'
$drafts = @(SqlList "SELECT code FROM finance_receipt WHERE status='DRAFT' ORDER BY id")
Write-Host ('draft receipts=' + $drafts.Count)
$auditedOk = 0
foreach ($rc in $drafts) {
  Open '/finance/receipt' 2800
  $idx = [int](FindRow $rc)
  if ($idx -lt 0) { Ok $false ('receipt row not found: ' + $rc); continue }
  Write-Host ('  audit ' + $rc + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
  Start-Sleep -Milliseconds 1300
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 2800
  $st = SqlOne ("SELECT status FROM finance_receipt WHERE code='" + $rc + "'")
  Write-Host ('  -> ' + $rc + ' status=' + $st + ' msg=' + (Txt '.el-message'))
  if ($st -eq 'AUDITED') { $auditedOk++ } else { Ok $false ('receipt ' + $rc + ' audited') }
}
# rerun-safe: count receipts audited in this run PLUS those already audited before it
Ok ((($auditedOk + [int]$rcptAuditedBefore) -ge 6)) ('receipts audited >= 6 (this run=' + $auditedOk + ' + before=' + $rcptAuditedBefore + ')')

Step 'DB cross-check'
$rets = D (SqlOne 'SELECT COUNT(*) FROM sale_return')
$retAud = D (SqlOne "SELECT COUNT(*) FROM sale_return WHERE status='AUDITED'")
$retItems = D (SqlOne 'SELECT COUNT(*) FROM sale_return_item')
$retQty = D (SqlOne 'SELECT COALESCE(SUM(quantity),0) FROM sale_return_item')
$stkAAfter = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $pIdA))
$stkBAfter = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $pIdB))
$recvAmt = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$recvPaid = D (SqlOne "SELECT COALESCE(SUM(paid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$recvUnpaid = D (SqlOne "SELECT COALESCE(SUM(unpaid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$rcptAudited = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='AUDITED'")
$rcptAmt = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receipt WHERE status='AUDITED'")
$retLogs = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no LIKE 'XTH-%'")
# NOTE: finance_account has opening_balance only (the live balance is computed) -> check the cash-flow table
$cashIn = D (SqlOne "SELECT COALESCE(SUM(income),0) FROM finance_cashflow WHERE account_name='CASH-01'")
$cashFlows = D (SqlOne "SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no LIKE 'SK-%'")
$stkDelta = ($stkAAfter - $stkABefore) + ($stkBAfter - $stkBBefore)
Write-Host ("[DB] returns=$rets audited=$retAud items=$retItems qty=$retQty newRetQty(thisRun)=$newRetQty stockA=$stkABefore->$stkAAfter stockB=$stkBBefore->$stkBAfter delta=$stkDelta")
Write-Host ("[DB] receivableAmt=$recvAmt paid=$recvPaid unpaid=$recvUnpaid auditedReceipts=$rcptAudited receiptAmt=$rcptAmt returnLogs(XTH-)=$retLogs cashIn(CASH-01)=$cashIn cashflows(SK-)=$cashFlows")
Ok (($rets -ge 5)) ('sale returns >= 5 (got ' + $rets + ')')
# 2026-09-21: 口径修正 —— 只统计"有效单据"（排除 CANCELLED）。作废是业务正常动作（例如其它回归脚本
# 建的探针单据、或本页手工录入后作废），把它们算成"未审核"会让本断言被无关数据带红。
$retLive = D (SqlOne "SELECT COUNT(*) FROM sale_return WHERE status<>'CANCELLED'")
$retAudLive = D (SqlOne "SELECT COUNT(*) FROM sale_return WHERE status='AUDITED'")
Ok (($retAudLive -eq $retLive)) ('all live sale returns audited (' + $retAudLive + '/' + $retLive + ', cancelled excluded)')
Ok (($retQty -ge 13)) ('returned qty >= 13 (got ' + $retQty + ')')
Ok (($stkDelta -eq $newRetQty)) ('stock restored == qty created this run (' + $stkDelta + ' = ' + $newRetQty + ')')
Ok (($retLogs -ge $rets)) ('return stock-out/in logs with XTH- bill no >= returns (got ' + $retLogs + ')')
Ok (($rcptAudited -ge 6)) ('audited receipts >= 6 (got ' + $rcptAudited + ')')
Ok (($recvPaid -gt 0)) ('receivable paid_amount > 0 (got ' + $recvPaid + ') — 核销生效')
Ok (($recvAmt -eq ($recvPaid + $recvUnpaid))) ('receivable amount == paid + unpaid (' + $recvAmt + ' = ' + $recvPaid + ' + ' + $recvUnpaid + ')')
Ok (($cashflows -ge 6)) ('cash-flow rows written by receipts >= 6 (got ' + $cashFlows + ')')
Ok (($cashIn -ge 30000)) ('CASH-01 cash income >= 30000 (got ' + $cashIn + ')')
Write-Host ('errs=' + (Errs))
Summary 'P6b sale returns + receipts'
