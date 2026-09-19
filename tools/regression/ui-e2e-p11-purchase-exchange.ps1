# 采购换货单 前端用例（P11，2026-09-18 新增功能：进货业务 → 采购换货单）
# 业务：把向供货商采购的成品退回换新 —— 退回不良品（我方仓出库 + 负向应付冲减）+ 换回良品（入库 + 正向应付）。
#       可换量 = 已购 − 已退 − 已换；两条应付台账净额即差价。
# 全部操作**走前端页面**（同用户要求）：列表 → 新增（选供货商/来源采购单/双向仓 + 明细数量）→ 保存 →
#       超量拦截（前端提示）→ 列表审核 → 反审核 → 再审核；每步都比对数据库（单据/库存/台账/流水）。
# ASCII ONLY（中文经 ui-e2e-zh.json + base64 注入）。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$OUT_WH = 71            # 退回出库仓 = 成品二号仓
$IN_WH = 71             # 换入入库仓 = 成品二号仓（同仓允许）
$PROD = 60              # 明细产品「测试产品A11」（来源采购单 CG-20260918006）
$QTY = 2                # 本次退回/换入数量
$PRICE = 16             # 采购原价
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function StockOf([string]$qt) { return (D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='$qt'"))) }

# 明细行内的数值输入框：按 DOM 顺序为 [退回数量, 退回单价, 换入数量, 换入单价]（品质/备注不是 el-input-number）
function SetRowNumber([int]$rowIdx, [int]$nth, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('.el-input-number input')];if(ins.length<=$nth)return 'NOINPUT:'+ins.length;const el=ins[$nth];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur&&el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function LastToast() {
  return (EvalJs "(()=>{const m=[...document.querySelectorAll('.el-message')];return m.length?((m[m.length-1].innerText||'').replace(/\s+/g,' ').trim()):''})()")
}
# 列表页布局探针：表头文字 + 是否横向溢出。
# 注意：真正的横向滚动容器是 `.el-scrollbar__wrap`（EP 2.x 的 body-wrapper 自身不滚动），
# 早先只测 `.el-table__body-wrapper` 会得到"假通过"（实测 wrap 1035 > 948）。
function ListLayout() {
  return (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return JSON.stringify({err:'NOTABLE'});const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,' ').trim()).filter(x=>x);const w=t.querySelector('.el-scrollbar__wrap')||t.querySelector('.el-table__body-wrapper');return JSON.stringify({head:hs,scroll:w?w.scrollWidth:0,client:w?w.clientWidth:0})})()")
}
Write-Host '--- 0.5) precondition (seeded via API, business flow below is UI-only): DEFECT stock in the return warehouse'
$defNow = StockOf 'DEFECT'
if ($defNow -lt ($QTY + 2)) {
  $lg = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
  $h = @{ Authorization = $lg.data.token }
  $rcId = D (SqlOne "SELECT IFNULL(MAX(id),0) FROM product_reclassify WHERE status='DRAFT' AND remark LIKE 'purchase-exchange verify%'")
  if ($rcId -le 0) {
    $rcBody = @{
      warehouseId = $OUT_WH; reclassifyDate = (Get-Date -Format 'yyyy-MM-dd')
      remark = 'purchase-exchange verify: seed DEFECT stock'
      items = @(@{ productId = $PROD; fromQuality = 'A'; toQuality = 'DEFECT'; quantity = ($QTY + 5) })
    } | ConvertTo-Json -Depth 6
    $rc = Invoke-RestMethod -Uri 'http://localhost:8080/api/inventory/reclassify' -Method Post -Headers $h -ContentType 'application/json' -Body $rcBody
    $rcId = D (SqlOne "SELECT IFNULL(MAX(id),0) FROM product_reclassify WHERE warehouse_id=$OUT_WH AND remark LIKE 'purchase-exchange verify%'")
    Write-Host ('  reclassify created id=' + $rcId + ' code=' + $rc.code)
  }
  $ra = Invoke-RestMethod -Uri "http://localhost:8080/api/inventory/reclassify/$rcId/audit" -Method Put -Headers $h
  start-sleep -Milliseconds 800
  $defNow = StockOf 'DEFECT'
  Write-Host ('  reclassify audited id=' + $rcId + ' code=' + $ra.code + ' -> DEFECT stock=' + $defNow)
}
Ok (($defNow -ge $QTY)) ('DEFECT stock available for the return (DEFECT=' + $defNow + ')')

Write-Host '--- 0) login + open the purchase-exchange list page'
EnsureLogin
WatchErrors
Open '/inventory/purchase-exchange' 2600
$path0 = EvalJs 'location.pathname'
Ok ($path0 -match '/inventory/purchase-exchange') ('list page rendered (' + $path0 + ')')

Write-Host '--- 0.2) list layout: one line, no horizontal scrolling, removed columns stay out of the list'
$lay = ListLayout
$layObj = $null
try { $layObj = $lay | ConvertFrom-Json } catch { }
if ($layObj -and -not $layObj.err) {
  $hdrList = ($layObj.head -join '|')
  Write-Host ('  list header=' + $hdrList)
  $kSrcPo = ZH 'lbl_src_purchase_order'
  $kNet = ZH 'lbl_pay_net'
  Ok (-not ($hdrList -match $kSrcPo)) ('source purchase order column removed from the list (' + $kSrcPo + ')')
  Ok (-not ($hdrList -match $kNet)) ('payable-net column removed from the list (' + $kNet + ')')
  $keepCols = ($hdrList -match (ZH 'lbl_exchange_summary')) -and ($hdrList -match (ZH 'lbl_vendor')) -and ($hdrList -match (ZH 'lbl_return_out_wh')) -and ($hdrList -match (ZH 'lbl_exchange_in_wh'))
  Ok $keepCols 'key list columns still present'
  $over = [int]$layObj.scroll - [int]$layObj.client
  Write-Host ('  body wrapper scroll/client = ' + $layObj.scroll + '/' + $layObj.client + ' (overflow=' + $over + 'px)')
  Ok ($over -le 2) ('list fits on one screen without horizontal scrolling (overflow=' + $over + 'px)')
} else { Ok $false ('list layout probe failed: ' + $lay) }

Write-Host '--- 1) new document: supplier + source purchase order auto-load details'
$c = ClickBtn 'btn_new_purchase_exchange'
Ok ($c -match 'OK') ('clicked the new button (' + $c + ')')
start-sleep -Milliseconds 2500
$path1 = EvalJs 'location.pathname'
Ok ($path1 -match '/purchase-exchange/add') ('navigated to the add page (' + $path1 + ')')
$r1 = SelectLabelContains 'lbl_vendor' (ZH 'val_vendor1') 1500
Ok ($r1 -match 'OK') ('supplier selected (' + $r1 + ')')
$r2 = SelectLabelContains 'lbl_src_purchase_order' (ZH 'val_po_1') 1800
Ok ($r2 -match 'OK') ('source purchase order selected (' + $r2 + ')')
start-sleep -Milliseconds 2000
$r3 = SelectLabelContains 'lbl_return_out_wh' (ZH 'wh_finished2') 1500
Ok ($r3 -match 'OK') ('return-out warehouse selected (' + $r3 + ')')
$r4 = SelectLabelContains 'lbl_exchange_in_wh' (ZH 'wh_finished2') 1500
Ok ($r4 -match 'OK') ('exchange-in warehouse selected (' + $r4 + ')')

$tb = Rows 0
if ($tb -and $tb.rows -and $tb.rows.Count -gt 0) {
  $hdr = ($tb.head -join '|')
  Write-Host ('  detail header=' + $hdr)
  Ok ($hdr -match (ZH 'lbl_can_exchange')) 'detail table shows the can-exchange column'
  Ok ($hdr -match (ZH 'lbl_in_qty')) 'detail table shows the exchange-in column'
  $row0 = ($tb.rows[0] -join '|')
  Write-Host ('  detail row=' + $row0)
  $prodName = ZH 'val_prod_po'
  Ok ($row0 -match $prodName) ('detail auto-loaded from the purchase order (' + $prodName + ')')
  # 明细默认带出该采购单的**全部**产品行；本用例只操作第一行（测试产品A11），其余行用「删除」按钮移除
  # （否则其他产品没有不良品库存，审核会被正确的库存校验拦下）
  $nRows = [int]$tb.n
  if ($nRows -gt 1) {
    for ($k = $nRows - 1; $k -ge 1; $k--) { ClickRowBtn $k 'btn_del' | Out-Null; start-sleep -Milliseconds 500 }
    $tb2 = Rows 0
    Write-Host ('  detail rows after cleanup=' + $tb2.n)
    Ok (([int]$tb2.n -eq 1)) 'extra purchase-order rows removed via the delete button'
  } else { Ok $true 'only one purchase-order row (nothing to remove)' }
} else { Ok $false 'details were auto-loaded from the purchase order' }

Write-Host '--- 2) guard: over-quantity is blocked in the page (client-side, can-exchange)'
$g1 = SetRowNumber 0 0 '99999'
Ok ($g1 -match 'OK') ('set an over quantity (' + $g1 + ')')
$s1 = ClickBtn 'btn_save'
start-sleep -Milliseconds 1500
$t1 = LastToast
Write-Host ('  toast=' + $t1)
$kwCan = ZH 'lbl_can_exchange'
Ok ($t1 -match $kwCan) ('over-quantity rejected with a can-exchange hint (' + $t1 + ')')

Write-Host '--- 3) save a valid draft (return 2 DEFECT, exchange-in 2 A)'
$before = D (SqlOne "SELECT COUNT(*) FROM purchase_exchange")
$g2 = SetRowNumber 0 0 "$QTY"
$g3 = SetRowNumber 0 2 "$QTY"
Ok (($g2 -match 'OK') -and ($g3 -match 'OK')) ('quantities set to ' + $QTY + ' (' + $g2 + '/' + $g3 + ')')
$s2 = ClickBtn 'btn_save'
Ok ($s2 -match 'OK') ('save clicked (' + $s2 + ')')
start-sleep -Milliseconds 2600
$after = D (SqlOne "SELECT COUNT(*) FROM purchase_exchange")
Ok (($after -eq ($before + 1))) ('exactly one new document created (' + $before + ' -> ' + $after + ')')
$xid = [int](D (SqlOne "SELECT IFNULL(MAX(id),0) FROM purchase_exchange"))
$xcode = SqlOne ("SELECT code FROM purchase_exchange WHERE id=$xid")
$stDraft = SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")
Write-Host ('  new doc id=' + $xid + ' code=' + $xcode + ' status=' + $stDraft)
Ok ($xcode.StartsWith('CH-')) ('prefix CH- used (' + $xcode + ')')
Ok (($stDraft -eq 'DRAFT')) ('saved as DRAFT (' + $stDraft + ')')
$qtyDb = D (SqlOne ("SELECT quantity FROM purchase_exchange_item WHERE exchange_id=$xid"))
$inQtyDb = D (SqlOne ("SELECT in_quantity FROM purchase_exchange_item WHERE exchange_id=$xid"))
$retQt = SqlOne ("SELECT quality_type FROM purchase_exchange_item WHERE exchange_id=$xid")
$inQt = SqlOne ("SELECT in_quality_type FROM purchase_exchange_item WHERE exchange_id=$xid")
Write-Host ('  detail: return=' + $qtyDb + '/' + $retQt + ' in=' + $inQtyDb + '/' + $inQt)
Ok (($qtyDb -eq $QTY) -and ($inQtyDb -eq $QTY)) 'detail quantities persisted as entered'
Ok (($retQt -eq 'DEFECT') -and ($inQt -eq 'A')) 'default qualities kept (return DEFECT / exchange-in A)'

Write-Host '--- 4) audit from the list row (UI) -> stock moves + two payable ledgers'
$defBefore = StockOf 'DEFECT'
$aBefore = StockOf 'A'
Open '/inventory/purchase-exchange' 2600
$ri = FindRow $xcode
Ok (([int]$ri -ge 0)) ('new document found in the list (row=' + $ri + ')')
$ac = ClickRowBtn ([int]$ri) 'btn_audit'
Ok ($ac -match 'OK') ('audit button clicked (' + $ac + ')')
ConfirmBox 1200 | Out-Null
start-sleep -Milliseconds 2200
$stA = SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")
Ok (($stA -eq 'AUDITED')) ('document AUDITED via UI (status=' + $stA + ')')
$defAfter = StockOf 'DEFECT'
$aAfter = StockOf 'A'
Write-Host ("  stock: DEFECT " + $defBefore + ' -> ' + $defAfter + ' ; A ' + $aBefore + ' -> ' + $aAfter)
Ok (($defAfter -eq ($defBefore - $QTY))) 'return side left our warehouse (-2 DEFECT)'
Ok (($aAfter -eq ($aBefore + $QTY))) 'exchange-in landed in our warehouse (+2 A)'
$pRet = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_RETURN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
$pIn = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_IN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
Write-Host ('  payables: return=' + $pRet + ' in=' + $pIn)
Ok (($pRet -eq (0 - ($QTY * $PRICE)))) 'negative payable for the return (-32)'
Ok (($pIn -eq ($QTY * $PRICE))) 'positive payable for the exchange-in (+32)'
$logs = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$xcode'"))
Ok (($logs -ge 2)) ('two stock logs written (' + $logs + ')')

Write-Host '--- 5) un-audit from the list row (UI) -> symmetric rollback'
Open '/inventory/purchase-exchange' 2600
$ri2 = FindRow $xcode
Write-Host ('  row for un-audit=' + $ri2)
$uc = ClickRowBtn ([int]$ri2) 'btn_unaudit'
Ok ($uc -match 'OK') ('un-audit button clicked (' + $uc + ')')
ConfirmBox 1200 | Out-Null
start-sleep -Milliseconds 2200
$stU = SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")
Ok (($stU -eq 'DRAFT')) ('document back to DRAFT (status=' + $stU + ')')
Ok ((StockOf 'DEFECT') -eq $defBefore) 'return stock restored'
Ok ((StockOf 'A') -eq $aBefore) 'exchange-in stock taken back'
$void = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status='CANCELLED' AND IFNULL(amount,0)=0"))
Ok (($void -ge 2)) ('both ledgers voided with zeroed amounts (' + $void + ')')

Write-Host '--- 6) audit again -> fresh ledgers (bill number reused, no unique-key clash)'
Open '/inventory/purchase-exchange' 2600
$ri3 = FindRow $xcode
Write-Host ('  row for re-audit=' + $ri3)
$ac2 = ClickRowBtn ([int]$ri3) 'btn_audit'
ConfirmBox 1200 | Out-Null
start-sleep -Milliseconds 2200
$stA2 = SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")
Ok (($stA2 -eq 'AUDITED')) ('re-audited successfully (status=' + $stA2 + ')')
$pRet2 = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_RETURN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
Ok (($pRet2 -eq (0 - ($QTY * $PRICE)))) ('active ledger regenerated (return=' + $pRet2 + ')')

Write-Host '--- 7) no JS errors on the exchange pages'
$errs = Errs
Write-Host ('  errs=' + $errs)
Ok ($errs -notmatch 'JSERR') 'no JS runtime errors captured'

Write-Host '--- 8) detail page keeps the source purchase order + payable net (moved off the list)'
Open "/inventory/purchase-exchange/detail/$xid" 2600
Ok ((BodyHas (ZH 'lbl_src_purchase_order')) -eq 'True') 'detail page shows the source purchase order'
Ok ((BodyHas (ZH 'lbl_pay_net')) -eq 'True') 'detail page shows the payable net (return / exchange-in / difference)'

Write-Host ('  final: id=' + $xid + ' code=' + $xcode + ' status=' + (SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")))
Summary 'purchase-exchange (P11)'
