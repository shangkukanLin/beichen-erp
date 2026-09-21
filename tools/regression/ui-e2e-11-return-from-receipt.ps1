# Browser E2E: launch a RETURN order from the receipt pages (2026-09-17).
#
# !! 2026-09-21 USER DECISION - the finished-goods half of this file is OBSOLETE BY DESIGN.
#    /outsource/order/delivery must NOT offer a RETURN entry any more: every receipt row there hangs
#    off a 委外加工单, so a return is just "that order received fewer pieces" and is posted as a
#    negative DEFECT_RETURN record (红冲收货 / 退不良) on the very same page. The independent return
#    order (委外加工退货, incl. REPAIR + charge + repair-return) stays reachable from its own menu.
#    => the two blocks that used to click the RETURN button on the finished-goods pages are gone;
#       their replacement guard rails ("list + detail must have NO 退货 button", "detail must have
#       退不良") are frozen in verify-delivery-menu.ps1 sections 2 and 4.
#
# !! STATUS: STALE FIXTURES - every remaining block still references ids of an OLDER database
#    generation (order 29 / delivery 30 / warehouse 37 / supplier 20 / DEL-20260916001 / MWO-...).
#    The current DB was re-seeded (warehouse ids start at 58, outsource_order has 6 rows, no id 29),
#    so THIS FILE CANNOT PASS until those fixtures are re-derived at runtime. Refresh = separate task.
#
#   Covered now: material receipt (/outsource/material-order/delivery)
#      - list row button    (supplier level) -> /outsource/material-return/add?supplierId=
#      - receive record btn                  -> /outsource/material-return/add?sourceDeliveryId=
# Asserts: prefill values, DB persistence (source_delivery_id / order_id), audit side effects
# (stock +/- and payable) and un-audit rollback. The created orders are CANCELLED at the end so the
# script is re-runnable (cancelled orders do not consume the returnable quantity).
# ASCII ONLY in this file - Chinese strings live in ui-e2e-zh.json.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'

function SqlRaw([string]$q) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
}
function SqlOne([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return '' }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function StockQty([int]$wh, [string]$col, [int]$id, [string]$q) {
  return SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND $col=$id AND quality_type='$q'"
}
function PaySum([int]$sid) { return SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND status='UNSETTLED'" }
function MaxId([string]$tbl) { return SqlOne "SELECT COALESCE(MAX(id),0) FROM $tbl" }
function CurUrl() { return (EvalJs "location.href.replace(location.origin,'')") }
function Step($n) { Write-Host ('--- STEP ' + $n) }

# rows (array of arrays of cell text) of the first visible table whose text contains $key.
# input values are appended as ' |val=..' because innerText never contains them.
function RowsOfTable([string]$key) {
  $b = B64 (ZH $key)
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);for(const t of ts){if((t.innerText||'').indexOf(K)<0)continue;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(!rs.length)continue;return JSON.stringify(rs.map(tr=>[...tr.querySelectorAll('td')].map(td=>{const i=td.querySelector('input');const s=(td.innerText||'').replace(/\s+/g,' ').trim();return (i&&i.value!=='')?(s+' |val='+i.value):s})))}return '[]'})()"
  $raw = EvalJs $js
  try { return @($raw | ConvertFrom-Json) } catch { return @() }
}
# index of the first row of table 0 where colA == key1 and colB == key2
function RowIdxByCols([int]$c1, [string]$key1, [int]$c2, [string]$key2) {
  $a = B64 (ZH $key1); $b = B64 (ZH $key2)
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const A=T('$a'),B=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return '-1';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){const tds=[...rs[i].querySelectorAll('td')];const x=(tds[$c1]?tds[$c1].innerText:'').trim();const y=(tds[$c2]?tds[$c2].innerText:'').trim();if(x===A&&y===B)return String(i)}return '-1'})()"
  return (EvalJs $js)
}
# index of the first row of table 0 that has a cell whose text is exactly $bill (e.g. a bill code)
function RowIdxByBill([string]$bill) {
  $b = B64 $bill
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return '-1';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){for(const td of [...rs[i].querySelectorAll('td')]){if((td.innerText||'').trim()===K)return String(i)}}return '-1'})()"
  return (EvalJs $js)
}
# buttons of row $rowIdx of table 0 (diagnostics)
function RowBtns([int]$rowIdx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return 'NOTABLE';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;return JSON.stringify([...rs[$rowIdx].querySelectorAll('button')].filter(vis).map(b=>(b.innerText||'').trim()))})()"
  return (EvalJs $js)
}

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
# !! OBSOLETE (2026-09-21): the finished-goods receipt pages no longer offer the RETURN entry.
#    S1/S2 below click a button that was removed by user decision - do NOT re-enable them; use
#    verify-delivery-menu.ps1 (sections 2 + 4) for the current guard rails instead.
Step 'S1 list-row entry of finished-goods receipt'
Open '/outsource/order/delivery' 2800
$t = Rows 0
Ok ($t.n -gt 0) ('S1 finished-goods receipt list loaded rows=' + $t.n)
Ok ((BodyHas (ZH 'btn_return')) -eq 'true') 'S1 list has the RETURN button'
$idx = [int](FindRow (ZH 'wo7'))
Ok ($idx -ge 0) ('S1 found work-order row idx=' + $idx)
Ok ((ClickRowBtn $idx 'btn_return') -match 'OK') 'S1 clicked row-level RETURN'
Start-Sleep -Milliseconds 3200
$u = CurUrl
Ok ($u -match 'return-order/add\?orderId=29') ('S1 navigated with orderId url=' + $u)
Ok ((BodyHas (ZH 'val_factory2')) -eq 'true') 'S1 factory prefilled=ce-shi-jia-gong-chang-A40'
Ok ((BodyHas (ZH 'product_mftest')) -eq 'true') 'S1 product row prefilled=MFTEST'

# =====================================================================
Step 'S2 record-row entry of finished-goods receipt -> create/audit/unaudit/cancel'
$b38    = StockQty 38 'product_id' 48 'A'
$b41m25 = StockQty 41 'material_id' 25 'GOOD'
$b41m31 = StockQty 41 'material_id' 31 'GOOD'
$bpay23 = PaySum 23
Write-Host ("BASE wh38.A.48=" + $b38 + " wh41.m25=" + $b41m25 + " wh41.m31=" + $b41m31 + " payable23=" + $bpay23)

Open '/outsource/order/delivery/29' 3000
$ri = [int](RowIdxByCols 2 'opt_type_deliver' 9 'st_audited')
Ok ($ri -ge 0) ('S2 found audited delivery row ri=' + $ri)
Ok ((ClickRowBtn $ri 'btn_return') -match 'OK') 'S2 clicked record-level RETURN'
Start-Sleep -Milliseconds 3200
$u = CurUrl
Ok ($u -match 'sourceDeliveryId=30') ('S2 navigated with source record url=' + $u)

$pr = RowsOfTable 'lbl_return_spec'
$ptxt = ($pr | ForEach-Object { $_ -join ' ' }) -join ' || '
Write-Host ('S2 product rows: ' + $ptxt)
Ok ($ptxt -match (ZH 'product_mftest')) 'S2 product prefilled=MFTEST'
Ok ($ptxt -match (ZH 'opt_q_a')) 'S2 spec prefilled=A'
Ok ((BodyHas (ZH 'val_factory2')) -eq 'true') 'S2 factory prefilled (header field)'
Ok ($ptxt -match 'val=10') 'S2 qty prefilled=10 (delivered 10 - returned 0)'
Ok ($ptxt -match (' ' + $b38 + ' ')) ('S2 stock shown=' + $b38 + ' (queried by warehouse+product+spec)')
Ok ((BodyHas (ZH 'lbl_out_finished_wh')) -eq 'true') 'S2 field exists: finished-goods out warehouse'
Ok ((BodyHas (ZH 'wh_finished1')) -eq 'true') 'S2 out warehouse prefilled'
# 2026-09-17 new: linked work order dropdown (auto-selected from the receipt) + spec defaults to the one WITH stock
Ok ((BodyHas (ZH 'wo7')) -eq 'true') 'S2 linked work order auto-selected (shown in the dropdown)'
Ok ((BodyHas (ZH 'lbl_stock_cur')) -eq 'true') 'S2 stock column shows the current spec stock'
$specKey = B64 (ZH 'lbl_return_spec')
$specTxt = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$specKey').replace(/\s+/g,'');const vis=e=>e.getClientRects().length>0;for(const t of [...document.querySelectorAll('.el-table')].filter(vis)){const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,'').trim());const si=hs.indexOf(K);if(si<0)continue;const tr=t.querySelector('.el-table__body tbody tr');if(!tr)return 'NOROW';const tds=[...tr.querySelectorAll('td')];return tds[si]?(tds[si].innerText||'').replace(/\s+/g,' ').trim():'NOCELL'}return 'NOTABLE'})()"
Write-Host ('S2 spec cell text=' + $specTxt)
Ok ($specTxt -match (ZH 'spec_qty_regex')) 'S2 spec cell shows the in-stock qty (default spec has stock)'
# 2026-09-17: BOM 来源改为「BOM 快照」（显示 v几，不带加工单号）；关联了加工单时**置灰不可改**
$snapKey = B64 (ZH 'lbl_bom_src')
$snapCell = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$snapKey').replace(/\s+/g,'');const vis=e=>e.getClientRects().length>0;for(const t of [...document.querySelectorAll('.el-table')].filter(vis)){const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,'').trim());const si=hs.indexOf(K);if(si<0)continue;const tr=t.querySelector('.el-table__body tbody tr');if(!tr)return 'NOROW';const td=[...tr.querySelectorAll('td')][si];if(!td)return 'NOCELL';const dis=!!td.querySelector('.el-select__wrapper.is-disabled')||!!td.querySelector('.el-select.is-disabled');return JSON.stringify({text:(td.innerText||'').replace(/\s+/g,' ').trim(),disabled:dis})}return 'NOTABLE'})()"
Write-Host ('S2 BOM-source cell=' + $snapCell)
Ok ($snapCell -match '"text":"v[0-9]') 'S2 BOM source = BOM snapshot version (v-N), not a work order'
Ok ($snapCell -match '"disabled":true') 'S2 BOM source is locked when a work order is linked'

Ok ((ClickBtn 'btn_save') -match 'OK') 'S2 clicked SAVE'
Start-Sleep -Milliseconds 3500
$retId = [int](MaxId 'outsource_return_order')
Ok ($retId -gt 0) ('S2 return order created id=' + $retId)
Write-Host ('S2 DB: ' + (SqlRaw "SELECT code,status,order_id,source_delivery_id,warehouse_id,factory_id FROM outsource_return_order WHERE id=$retId"))
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$retId") -eq 'DRAFT') 'S2 status=DRAFT'
Ok ((SqlOne "SELECT source_delivery_id FROM outsource_return_order WHERE id=$retId") -eq '30') 'S2 source_delivery_id persisted=30'
Ok ((SqlOne "SELECT order_id FROM outsource_return_order WHERE id=$retId") -eq '29') 'S2 order_id persisted=29 (was always empty before)'
# 2026-09-17: 退货成品行落「所用 BOM 快照」，可追溯
$savedSnap = SqlOne "SELECT bom_snapshot_id FROM outsource_return_order_product WHERE return_order_id=$retId LIMIT 1"
Write-Host ('S2 product bom_snapshot_id=' + $savedSnap)
Ok (-not [string]::IsNullOrWhiteSpace($savedSnap)) 'S2 product row persisted bom_snapshot_id'

Open ("/outsource/return-order/detail/$retId") 2600
Ok ((BodyHas (ZH 'lbl_src_delivery_item')) -eq 'true') 'S2 detail shows the source receipt record'
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S2 clicked AUDIT'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Write-Host ('S2 after audit: wh38.A.48=' + (StockQty 38 'product_id' 48 'A') + ' wh41.m25=' + (StockQty 41 'material_id' 25 'GOOD') + ' wh41.m31=' + (StockQty 41 'material_id' 31 'GOOD') + ' payable23=' + (PaySum 23))
Ok ((D (StockQty 38 'product_id' 48 'A')) -eq ((D $b38) - 10)) 'S2 after audit: finished-goods stock -10'
Ok ((D (StockQty 41 'material_id' 25 'GOOD')) -eq ((D $b41m25) + 10)) 'S2 after audit: factory outsource warehouse material25 +10 (BOM returned)'
Ok ((D (StockQty 41 'material_id' 31 'GOOD')) -eq ((D $b41m31) + 10)) 'S2 after audit: factory outsource warehouse material31 +10 (BOM returned)'
Ok ((D (PaySum 23)) -lt (D $bpay23)) 'S2 after audit: payable reduced (negative entry)'
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$retId") -eq 'AUDITED') 'S2 status=AUDITED'

Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S2 clicked UN-AUDIT'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Ok ((D (StockQty 38 'product_id' 48 'A')) -eq (D $b38)) 'S2 after un-audit: finished-goods stock rolled back'
Ok ((D (StockQty 41 'material_id' 25 'GOOD')) -eq (D $b41m25)) 'S2 after un-audit: material25 rolled back'
Ok ((D (StockQty 41 'material_id' 31 'GOOD')) -eq (D $b41m31)) 'S2 after un-audit: material31 rolled back'
Ok ((D (PaySum 23)) -eq (D $bpay23)) 'S2 after un-audit: payable rolled back'
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$retId") -eq 'DRAFT') 'S2 after un-audit: back to DRAFT'

Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S2 clicked CANCEL (frees the returnable qty so the script can re-run)'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 2000
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$retId") -eq 'CANCELLED') 'S2 order cancelled'

# =====================================================================
Step 'S3 record-row entry of material receipt -> create/audit/unaudit/cancel'
$b37    = StockQty 37 'material_id' 25 'GOOD'
$bpay20 = PaySum 20
Write-Host ("BASE wh37.m25=" + $b37 + " payable20=" + $bpay20)

Open '/outsource/material-order/delivery/6' 3200
# pick a deterministic record: bill DEL-20260916001 (material25 x20 into warehouse 37)
$ri = [int](RowIdxByBill (ZH 'del45'))
Write-Host ('S3 rows=' + ((Rows 0).n) + ' billRow=' + $ri + ' btns=' + (RowBtns $ri))
Ok ($ri -ge 0) ('S3 found receive bill row ri=' + $ri)
Ok ((RowBtns $ri) -match (ZH 'btn_return')) 'S3 that row offers the RETURN button (only audited RECEIVE rows do)'
Ok ((ClickRowBtn $ri 'btn_return') -match 'OK') 'S3 clicked record-level RETURN'
Start-Sleep -Milliseconds 3200
$u = CurUrl
Ok ($u -match 'material-return/add\?sourceDeliveryId=45') ('S3 navigated with source receive bill url=' + $u)
Ok ((BodyHas (ZH 'val_supplier_jh')) -eq 'true') 'S3 supplier prefilled=JieHe'
Ok ((BodyHas (ZH 'wh_jiehe')) -eq 'true') 'S3 source warehouse prefilled'

$mr = RowsOfTable 'lbl_qty_col_mr'
$mtxt = ($mr | ForEach-Object { $_ -join ' ' }) -join ' || '
Write-Host ('S3 return lines: ' + $mtxt)
Ok ($mtxt -match (ZH 'mat_drv_rm')) 'S3 only the materials of that receive bill are listed=RM692E5'
Ok ($mtxt -match 'val=12') 'S3 default qty=min(returnable 20, stock 12)=12'

Ok ((ClickBtn 'btn_save_draft') -match 'OK') 'S3 clicked SAVE DRAFT'
Start-Sleep -Milliseconds 3500
$mrId = [int](MaxId 'outsource_material_return')
Ok ($mrId -gt 0) ('S3 material return order created id=' + $mrId)
Write-Host ('S3 DB: ' + (SqlRaw "SELECT code,status,supplier_id,from_warehouse_id,source_delivery_id FROM outsource_material_return WHERE id=$mrId"))
Ok ((SqlOne "SELECT source_delivery_id FROM outsource_material_return WHERE id=$mrId") -eq '45') 'S3 source_delivery_id persisted=45'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$mrId") -eq 'DRAFT') 'S3 status=DRAFT'

Open ("/outsource/material-return/detail/$mrId") 2600
Ok ((BodyHas (ZH 'lbl_src_receive_item')) -eq 'true') 'S3 detail shows the source receive bill'
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S3 clicked AUDIT'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Write-Host ('S3 after audit: wh37.m25=' + (StockQty 37 'material_id' 25 'GOOD') + ' payable20=' + (PaySum 20))
Ok ((D (StockQty 37 'material_id' 25 'GOOD')) -eq ((D $b37) - 12)) 'S3 after audit: source warehouse -12'
Ok ((D (PaySum 20)) -lt (D $bpay20)) 'S3 after audit: payable reduced (negative entry)'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$mrId") -eq 'AUDITED') 'S3 status=AUDITED'

Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S3 clicked UN-AUDIT'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Ok ((D (StockQty 37 'material_id' 25 'GOOD')) -eq (D $b37)) 'S3 after un-audit: source warehouse rolled back'
Ok ((D (PaySum 20)) -eq (D $bpay20)) 'S3 after un-audit: payable rolled back'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$mrId") -eq 'DRAFT') 'S3 after un-audit: back to DRAFT'

Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S3 clicked CANCEL (frees the returnable qty so the script can re-run)'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 2000
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$mrId") -eq 'CANCELLED') 'S3 order cancelled'

# =====================================================================
Step 'S4 list-row entry of material receipt'
Open '/outsource/material-order/delivery' 2800
$t = Rows 0
Ok ($t.n -gt 0) ('S4 material receipt list loaded rows=' + $t.n)
Ok ((BodyHas (ZH 'btn_return')) -eq 'true') 'S4 list has the RETURN button'
$idx = [int](FindRow (ZH 'mwo1'))
Ok ($idx -ge 0) ('S4 found material-order row idx=' + $idx)
Ok ((ClickRowBtn $idx 'btn_return') -match 'OK') 'S4 clicked row-level RETURN'
Start-Sleep -Milliseconds 3200
$u = CurUrl
Ok ($u -match 'material-return/add\?supplierId=20') ('S4 navigated with supplier url=' + $u)
Ok ((BodyHas (ZH 'lbl_return_target')) -eq 'true') 'S4 material return add page opened'

# =====================================================================
Step 'S5 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S5 no page/API errors during the flow'

Summary 'ui-e2e-11 RETURN entry on receipt pages (finished-goods / material)'
