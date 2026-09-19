# P3b (2026-09-18 full-flow E2E): purchase returns through the frontend only.
#   return #1: from the purchase-order detail page ("发起退货" -> auto-fills supplier/warehouse/lines/canReturn)
#   return #2: manual new return document (supplier + warehouse + product + qty + price)
#   both audited -> stock out + payable reduced. ALL DATA KEPT. ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function PickOptionEndsWith([string]$text, [int]$wait = 0) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const O=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim().endsWith(O));if(li.length){li[0].click();return 'OK'}}return 'NOOPT:'+O+'/dd='+dds.length})()"
  if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
  return (EvalJs $js)
}
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

$retBefore = D (SqlOne 'SELECT COUNT(*) FROM purchase_return')
# rerunnable: only create the 2 planned returns if they are not there yet (keeps data, avoids inflation)
$skipCreate = ($retBefore -ge 2)
# newest audited purchase order -> return from its detail page
$ordId = [int](SqlOne "SELECT id FROM purchase_order WHERE status='AUDITED' ORDER BY id DESC LIMIT 1")
$ordProd = [int](SqlOne ("SELECT product_id FROM purchase_order_item WHERE order_id=" + $ordId + " ORDER BY id LIMIT 1"))
$stockBefore = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $ordProd))
Write-Host ("[BASE] returns=$retBefore order=$ordId product=$ordProd stock=$stockBefore skipCreate=$skipCreate")
Ok ($ordId -gt 0) 'purchase order resolved from DB'
if ($skipCreate) { Write-Host ('NOTE: ' + $retBefore + ' purchase returns already exist -> creation steps skipped (audit/verify only)') }

# ---------------- return #1: from order detail ----------------
if (-not $skipCreate) {
Step 'return #1 via purchase-order detail (发起退货)'
Open ("/inventory/purchase/detail/" + $ordId) 3200
ClearErrs | Out-Null
Write-Host ('click 发起退货: ' + (ClickText (ZH 'btn_launch_return2')))
Start-Sleep -Milliseconds 2800
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
$rows = Rows 0
Write-Host ('prefilled lines=' + $rows.n)
Ok ($rows.n -ge 1) 'return lines auto-filled from purchase order'
if ($rows.n -ge 1) {
  SetRowInput 0 2 '5' | Out-Null    # 退货数量 (inputs: 0 product select, 1 quality select, 2 qty, 3 price)
  Start-Sleep -Milliseconds 400
  SetRowInput 0 3 '10' | Out-Null
  Start-Sleep -Milliseconds 400
}
ClickBtn 'btn_save' | Out-Null
Start-Sleep -Milliseconds 2800
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)'))
$cnt1 = D (SqlOne 'SELECT COUNT(*) FROM purchase_return')
Ok ($cnt1 -eq ($retBefore + 1)) ('return #1 created (db=' + $cnt1 + ')')

# ---------------- return #2: manual ----------------
Step 'return #2 manual add'
Open '/inventory/purchase-return/add' 2800
ClearErrs | Out-Null
SelectLabelContains 'lbl_vendor' ((ZH 'val_vendor') + '2') | Out-Null
Start-Sleep -Milliseconds 1000
SelectLabelContains 'lbl_return_warehouse' (ZH 'wh_finished2') | Out-Null
Start-Sleep -Milliseconds 1000
ClickBtn 'btn_add_detail' | Out-Null
Start-Sleep -Milliseconds 1000
OpenRowSelect 0 0 | Out-Null
Start-Sleep -Milliseconds 1600
Write-Host ('pick product: ' + (PickOptionEndsWith ((ZH 'val_product') + '1')))
Start-Sleep -Milliseconds 900
SetRowInput 0 2 '3' | Out-Null
Start-Sleep -Milliseconds 400
SetRowInput 0 3 '8' | Out-Null
Start-Sleep -Milliseconds 400
ClickBtn 'btn_save' | Out-Null
Start-Sleep -Milliseconds 2800
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)'))
$cnt2 = D (SqlOne 'SELECT COUNT(*) FROM purchase_return')
Ok ($cnt2 -eq ($retBefore + 2)) ('return #2 created (db=' + $cnt2 + ')')
} else {
  $cnt2 = $retBefore
}

# ---------------- audit all DRAFT returns (locate each row by its code) ----------------
# NOTE (bug found & fixed): auditing "row 0" twice is WRONG - after the first audit that row shows
# 反审核 instead of 审核, and ClickRowBtnContains '审计' is a substring match, so the 2nd click
# un-audited the very same document. Locate each row by its (ASCII) code and verify the DB status.
Step 'audit all DRAFT returns (row located by code, status verified in DB)'
function SqlList([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { (($_ -split "`t")[0]).Trim() } | Where-Object { $_ -ne '' })
}
foreach ($c in (SqlList "SELECT code FROM purchase_return WHERE status='DRAFT' ORDER BY id")) {
  Open '/inventory/purchase-return' 2400
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('return row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 900 | Out-Null
    Start-Sleep -Milliseconds 2800
    $st = SqlOne ("SELECT status FROM purchase_return WHERE code='" + $c + "'")
    Write-Host ('  -> status=' + $st)
    Ok ($st -eq 'AUDITED') ('return ' + $c + ' audited')
  }
}
$aud = D (SqlOne 'SELECT COUNT(*) FROM purchase_return WHERE status=''AUDITED''')
$items = D (SqlOne 'SELECT COUNT(*) FROM purchase_return_item')
$stockAfter = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $ordProd))
# aggregated expectations (script is rerunnable -> never hard-code "5" or "2")
$retQty = D (SqlOne ("SELECT COALESCE(SUM(i.quantity),0) FROM purchase_return_item i JOIN purchase_return r ON r.id=i.return_id WHERE r.status='AUDITED' AND i.product_id=" + $ordProd))
$retLogs = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE product_id=" + $ordProd + " AND change_type='RETURN_OUT' AND related_bill_no LIKE 'TH-%'"))
# NOTE: net returned qty must include the un-audit rollback (RETURN_UN_AUDIT). When an order is
# un-audited and re-audited, RETURN_OUT keeps two rows; counting RETURN_OUT alone double counts it
# (measured: -10 plus a +5 rollback => net 5).
$retLogQty = D (SqlOne ("SELECT COALESCE(SUM(-change_quantity),0) FROM warehouse_stock_log WHERE product_id=" + $ordProd + " AND change_type IN ('RETURN_OUT','RETURN_UN_AUDIT')"))
Write-Host ("[DB] returns=$cnt2 audited=$aud items=$items stockBefore=$stockBefore stockAfter=$stockAfter returnedQty=$retQty returnLogs=$retLogs returnLogQty=$retLogQty")
Ok ($aud -eq $cnt2) ('all returns audited (' + $aud + '/' + $cnt2 + ')')
Ok ($items -ge 2) ('return items >= 2 (got ' + $items + ')')
# rerun-safe invariant: stock == PURCHASE_IN qty - RETURN_OUT qty (same product, from the stock log)
$inQty = D (SqlOne ("SELECT COALESCE(SUM(change_quantity),0) FROM warehouse_stock_log WHERE product_id=" + $ordProd + " AND change_type='PURCHASE_IN'"))
Ok (($stockAfter -eq ($inQty - $retLogQty))) ('invariant on ' + $ordProd + ': stock == PURCHASE_IN - RETURN_OUT (' + $stockAfter + ' == ' + $inQty + ' - ' + $retLogQty + ')')
Ok ($retLogs -ge 2) ('RETURN_OUT stock logs with TH- bill no >= 2 (got ' + $retLogs + ')')
Ok (($retLogQty -eq $retQty)) ('stock log quantity matches returned qty (' + $retLogQty + ' = ' + $retQty + ')')
Write-Host ('errs=' + (Errs))
Summary 'P3b purchase returns'
