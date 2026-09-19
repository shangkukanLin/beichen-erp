# Browser E2E: outsourcing return order has two types now (2026-09-17)
#   S0 data prep: make sure warehouse 38 has >=1 DEFECT unit of MFTEST(48) (repair source; made via return-sort if missing)
#   S1 DEFECT return linked to a work order (via finished-goods receipt record) : type defaults to DEFECT, charging disabled
#   S2 DEFECT return WITHOUT work order (manual) : materials filled from the BOM snapshot, order_id must be NULL
#   S3 REPAIR return: must charge / must NOT link a work order / no material lines; audit = send-out (DEFECT-)
#      plus ONE positive repair-charge payable (no negative return payable)
#   S4 repair back: register the returned goods (A +1) / over-return blocked / revoke the return record
#   S5 un-audit + cancel: stock & payable fully rolled back (a repair order needs all return records revoked first)
# ASCII ONLY. Chinese strings live in ui-e2e-zh.json
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
function RepairPay([int]$sid) { return SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND source_bill_type='OUTSOURCE_REPAIR_CHARGE' AND status='UNSETTLED'" }
function ReturnPay([int]$sid) { return SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND source_bill_type='OUTSOURCE_RETURN' AND status='UNSETTLED'" }
function MaxId([string]$tbl) { return SqlOne "SELECT COALESCE(MAX(id),0) FROM $tbl" }
function OrderField([int]$id, [string]$col) { return SqlOne "SELECT $col FROM outsource_return_order WHERE id=$id" }
function CurUrl() { return (EvalJs "location.href.replace(location.origin,'')") }
function Step($n) { Write-Host ('--- STEP ' + $n) }
# index of the first row of table 0 where colA == key1 and colB == key2 (both are zh keys)
function RowIdxByCols([int]$c1, [string]$key1, [int]$c2, [string]$key2) {
  $a = B64 (ZH $key1); $b = B64 (ZH $key2)
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const A=T('$a'),B=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return '-1';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){const tds=[...rs[i].querySelectorAll('td')];const x=(tds[$c1]?tds[$c1].innerText:'').trim();const y=(tds[$c2]?tds[$c2].innerText:'').trim();if(x===A&&y===B)return String(i)}return '-1'})()"
  return (EvalJs $js)
}
# 1st el-input-number input of row $rowIdx in the visible products table (return qty for DEFECT / sent qty for REPAIR)
function SetRowQty([int]$rowIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const ts=[...root.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('.el-input-number input')];if(!ins.length)return 'NOQTY';const el=ins[0];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
Step 'S0 ensure DEFECT stock for repair test'
$d38 = StockQty 38 'product_id' 48 'DEFECT'
Write-Host ('S0 defect stock now=' + $d38)
if ((D $d38) -lt 1) {
  # use return-sort to turn 1 PENDING after-sale unit into DEFECT (inside warehouse 38)
  Open '/inventory/return-sort?tab=bills' 2600
  Ok ((ClickBtn 'btn_new_sort') -match 'OK') 'S0 open new return-sort'
  Start-Sleep -Milliseconds 2600
  Ok ((SelectLabelText 'lbl_source_wh_rs' (ZH 'wh_finished1')) -match 'OK') 'S0 pick source warehouse'
  Start-Sleep -Milliseconds 1200
  Ok ((ClickBtn 'btn_load_pending') -match 'OK') 'S0 load pending after-sale stock'
  Start-Sleep -Milliseconds 2600
  Ok ((SetRowInput 0 3 '1') -match 'OK') 'S0 set defect qty=1'
  Start-Sleep -Milliseconds 500
  Ok ((SelectLabelText 'lbl_a_wh' (ZH 'wh_finished1')) -match 'OK') 'S0 set A target warehouse'
  Start-Sleep -Milliseconds 800
  Ok ((SelectLabelText 'lbl_b_wh' (ZH 'wh_finished1')) -match 'OK') 'S0 set B target warehouse'
  Start-Sleep -Milliseconds 800
  Ok ((SelectLabelText 'lbl_c_wh' (ZH 'wh_finished1')) -match 'OK') 'S0 set C target warehouse'
  Start-Sleep -Milliseconds 800
  Ok ((SelectLabelText 'lbl_defect_wh' (ZH 'wh_finished1')) -match 'OK') 'S0 set defect target warehouse'
  Start-Sleep -Milliseconds 800
  Ok ((ClickBtn 'btn_save') -match 'OK') 'S0 save return-sort doc'
  Start-Sleep -Milliseconds 3200
  Open '/inventory/return-sort?tab=bills' 2600
  $ri = [int](FindRow (ZH 'st_draft'))
  Ok ($ri -ge 0) ('S0 draft sort doc row=' + $ri)
  Ok ((ClickRowBtnContains $ri (ZH 'btn_audit')) -match 'OK') 'S0 audit return-sort doc'
  ConfirmBox 1500 | Out-Null
  Start-Sleep -Milliseconds 3000
  $d38 = StockQty 38 'product_id' 48 'DEFECT'
  Write-Host ('S0 defect stock after prep=' + $d38)
}
Ok ((D $d38) -ge 1) ('S0 ready: defect stock=' + $d38 + ' (warehouse 38, MFTEST)')

# =====================================================================
Step 'S1 defect return linked to work order (entry from finished-goods receipt)'
Open '/outsource/order/delivery/29' 3000
$ri = [int](RowIdxByCols 2 'opt_type_deliver' 9 'st_audited')
Ok ($ri -ge 0) ('S1 found audited delivery row ri=' + $ri)
Ok ((ClickRowBtn $ri 'btn_return') -match 'OK') 'S1 click record-level RETURN'
Start-Sleep -Milliseconds 3200
Write-Host ('S1 url=' + (CurUrl))
Ok ((BodyHas (ZH 'val_defect_type')) -eq 'true') 'S1 return type = DEFECT (default)'
Ok ((BodyHas (ZH 'txt_defect_no_charge')) -eq 'true') 'S1 charging is disabled for defect return'
Ok ((BodyHas (ZH 'lbl_return_spec')) -eq 'true') 'S1 BOM-based return columns present'
Write-Host ('S1 save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 3500
$s1 = [int](MaxId 'outsource_return_order')
Ok ((OrderField $s1 'return_type') -eq 'DEFECT') ('S1 return_type=DEFECT id=' + $s1)
Ok ((OrderField $s1 'order_id') -eq '29') 'S1 linked work order persisted'
Ok ((OrderField $s1 'charge_flag') -eq '0') 'S1 charge_flag forced 0'
# audit + assert negatives, then roll back and cancel
$bA = StockQty 38 'product_id' 48 'A'
$bm25 = StockQty 41 'material_id' 25 'GOOD'
$bRet = ReturnPay 23
Open ("/outsource/return-order/detail/$s1") 2600
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S1 audit'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Ok ((D (StockQty 38 'product_id' 48 'A')) -eq ((D $bA) - 10)) 'S1 after audit: finished stock -10'
Ok ((D (StockQty 41 'material_id' 25 'GOOD')) -gt (D $bm25)) 'S1 after audit: BOM material returned to factory warehouse'
Ok ((D (ReturnPay 23)) -lt (D $bRet)) 'S1 after audit: payable reduced (negative entry)'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S1 un-audit'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Ok ((D (StockQty 38 'product_id' 48 'A')) -eq (D $bA)) 'S1 after un-audit: stock rolled back'
Ok ((D (ReturnPay 23)) -eq (D $bRet)) 'S1 after un-audit: payable rolled back'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S1 cancel (frees the returnable qty)'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 2000
Ok ((OrderField $s1 'status') -eq 'CANCELLED') 'S1 doc cancelled'

# =====================================================================
Step 'S2 defect return WITHOUT work order (manual, BOM snapshot only)'
Open '/outsource/return-order' 2600
Ok ((ClickBtn 'btn_new_defect_return') -match 'OK') 'S2 click NEW DEFECT return'
Start-Sleep -Milliseconds 2600
Write-Host ('S2 url=' + (CurUrl))
Ok ((BodyHas (ZH 'val_defect_type')) -eq 'true') 'S2 type = DEFECT'
# 2026-09-17：关联加工单由只读文字改为**可清空的加工单下拉**，未选择时显示同一句空态文案（现为 placeholder 属性）
$phKey = B64 (ZH 'ph_not_linked')
$phFound = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const P=T('$phKey');const vis=e=>e.getClientRects().length>0;const attr=[...document.querySelectorAll('input')].filter(vis).some(i=>(i.placeholder||'').indexOf(P)>=0);const txt=((document.body.innerText)||'').indexOf(P)>=0;return String(attr||txt)})()"
Ok ($phFound -eq 'true') 'S2 shows "not linked to work order" (empty linked-order dropdown)'
Ok ((SelectLabelText 'lbl_factory' (ZH 'val_factory2')) -match 'OK') 'S2 pick factory'
Start-Sleep -Milliseconds 1500
Ok ((SelectLabelText 'lbl_out_finished_wh' (ZH 'wh_finished1')) -match 'OK') 'S2 pick finished-goods out warehouse'
Start-Sleep -Milliseconds 1200
Ok ((OpenRowSelect 0 0) -match 'OK') 'S2 open row product select'
Start-Sleep -Milliseconds 1400
Ok ((PickOptionContains (ZH 'product_mftest')) -match 'OK') 'S2 pick product MFTEST'
Start-Sleep -Milliseconds 2200
Ok ((SetRowQty 0 '1') -match 'OK') 'S2 qty=1'
# 2026-09-17: 没关联加工单 → 「BOM来源（BOM快照）」可自选，显示 v几（不带加工单号）
$snapKey = B64 (ZH 'lbl_bom_src')
$snapCell = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$snapKey').replace(/\s+/g,'');const vis=e=>e.getClientRects().length>0;for(const t of [...document.querySelectorAll('.el-table')].filter(vis)){const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,'').trim());const si=hs.indexOf(K);if(si<0)continue;const tr=t.querySelector('.el-table__body tbody tr');if(!tr)return 'NOROW';const td=[...tr.querySelectorAll('td')][si];if(!td)return 'NOCELL';const dis=!!td.querySelector('.el-select__wrapper.is-disabled')||!!td.querySelector('.el-select.is-disabled');return JSON.stringify({text:(td.innerText||'').replace(/\s+/g,' ').trim(),disabled:dis})}return 'NOTABLE'})()"
Write-Host ('S2 BOM-source cell=' + $snapCell)
Ok ($snapCell -match '"text":"v[0-9]') 'S2 BOM source = BOM snapshot version (v-N), not a work order'
Ok ($snapCell -match '"disabled":false') 'S2 BOM source is selectable when no work order is linked'
Start-Sleep -Milliseconds 800
$r2 = Rows 0
Write-Host ('S2 rows: ' + (($r2.rows | ForEach-Object { $_ -join ' ' }) -join ' ~ '))
Write-Host ('S2 save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 3500
$s2 = [int](MaxId 'outsource_return_order')
Ok ((OrderField $s2 'return_type') -eq 'DEFECT') ('S2 return_type=DEFECT id=' + $s2)
Ok ((SqlOne "SELECT (order_id IS NULL) FROM outsource_return_order WHERE id=$s2") -eq '1') 'S2 order_id is NULL (not linked)'
$s2items = [int](SqlOne "SELECT COUNT(*) FROM outsource_return_order_item WHERE return_order_id=$s2")
Write-Host ('S2 items from BOM snapshot=' + $s2items)
Ok ($s2items -ge 1) 'S2 materials auto-filled from BOM snapshot'
$bA2 = StockQty 38 'product_id' 48 'A'
$bRet2 = ReturnPay 23
Open ("/outsource/return-order/detail/$s2") 2600
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S2 audit'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Ok ((D (StockQty 38 'product_id' 48 'A')) -eq ((D $bA2) - 1)) 'S2 after audit: finished stock -1'
Ok ((D (ReturnPay 23)) -lt (D $bRet2)) 'S2 after audit: negative payable created'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S2 un-audit'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Ok ((D (StockQty 38 'product_id' 48 'A')) -eq (D $bA2)) 'S2 after un-audit: stock rolled back'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S2 cancel'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 2000
Ok ((OrderField $s2 'status') -eq 'CANCELLED') 'S2 doc cancelled'

# =====================================================================
Step 'S3 repair return: must charge, must NOT link a work order, no material lines'
$bDef = StockQty 38 'product_id' 48 'DEFECT'
$bRep = RepairPay 23
$bRet3 = ReturnPay 23
Write-Host ('BASE defect=' + $bDef + ' repairPay=' + $bRep + ' returnPay=' + $bRet3)
Open '/outsource/return-order' 2600
Ok ((ClickText (ZH 'val_repair_type')) -match 'OK') 'S3 switch to REPAIR tab'
Start-Sleep -Milliseconds 1800
Ok ((ClickBtn 'btn_new_repair_return') -match 'OK') 'S3 click NEW REPAIR return'
Start-Sleep -Milliseconds 2600
Write-Host ('S3 url=' + (CurUrl))
Ok ((BodyHas (ZH 'lbl_sent_qty')) -eq 'true') 'S3 column label is SENT qty'
Ok ((BodyHas (ZH 'lbl_bom_src')) -eq 'false') 'S3 no BOM-source column for repair'
Ok ((BodyHas (ZH 'lbl_repair_out_wh')) -eq 'true') 'S3 warehouse field is repair-out warehouse'
Ok ((SelectLabelText 'lbl_factory' (ZH 'val_factory2')) -match 'OK') 'S3 pick factory'
Start-Sleep -Milliseconds 1500
Ok ((SelectLabelText 'lbl_repair_out_wh' (ZH 'wh_finished1')) -match 'OK') 'S3 pick repair-out warehouse'
Start-Sleep -Milliseconds 1200
Ok ((OpenRowSelect 0 0) -match 'OK') 'S3 open row product select'
Start-Sleep -Milliseconds 1400
Ok ((PickOptionContains (ZH 'product_mftest')) -match 'OK') 'S3 pick product MFTEST'
Start-Sleep -Milliseconds 2200
Ok ((SetRowQty 0 '1') -match 'OK') 'S3 sent qty=1'
Start-Sleep -Milliseconds 600
Ok ((FillLabel 'lbl_charge_amount' '100') -match 'OK') 'S3 charge amount=100 (charge type defaults to REWORK)'
Start-Sleep -Milliseconds 600
$r3 = Rows 0
Write-Host ('S3 rows: ' + (($r3.rows | ForEach-Object { $_ -join ' ' }) -join ' ~ '))
Write-Host ('S3 save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 3500
$s3 = [int](MaxId 'outsource_return_order')
Ok ((OrderField $s3 'return_type') -eq 'REPAIR') ('S3 return_type=REPAIR id=' + $s3)
Ok ((SqlOne "SELECT (order_id IS NULL) FROM outsource_return_order WHERE id=$s3") -eq '1') 'S3 order_id is NULL'
Ok ((OrderField $s3 'charge_flag') -eq '1') 'S3 charge_flag=1'
Ok ((D (OrderField $s3 'charge_amount')) -eq 100) 'S3 charge_amount=100'
Ok (([int](SqlOne "SELECT COUNT(*) FROM outsource_return_order_item WHERE return_order_id=$s3")) -eq 0) 'S3 no material lines'

Open ("/outsource/return-order/detail/$s3") 2600
Ok ((BodyHas (ZH 'val_repair_type')) -eq 'true') 'S3 detail shows type REPAIR'
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S3 audit (send out for repair)'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Write-Host ('S3 after audit: defect=' + (StockQty 38 'product_id' 48 'DEFECT') + ' repairPay=' + (RepairPay 23) + ' returnPay=' + (ReturnPay 23))
Ok ((D (StockQty 38 'product_id' 48 'DEFECT')) -eq ((D $bDef) - 1)) 'S3 after audit: DEFECT stock -1 (sent to factory)'
Ok ((D (RepairPay 23)) -eq ((D $bRep) + 100)) 'S3 after audit: repair charge payable +100 (positive)'
Ok ((D (ReturnPay 23)) -eq (D $bRet3)) 'S3 after audit: NO negative return payable'

# =====================================================================
Step 'S4 repair back (register the returned goods)'
$bA4 = StockQty 38 'product_id' 48 'A'
Ok ((ClickBtn 'lbl_repair_return') -match 'OK') 'S4 open REGISTER REPAIR RETURN'
Start-Sleep -Milliseconds 1800
Ok ((BodyHas (ZH 'btn_confirm_repair_return')) -eq 'true') 'S4 dialog opened'
Ok ((SelectLabelText 'lbl_repair_wh' (ZH 'wh_finished1')) -match 'OK') 'S4 pick return warehouse'
Start-Sleep -Milliseconds 1200
# 修好回来的规格可以改：把默认（送修时的"不良品"）改成 A规，验证返回品质可控
Ok ((OpenRowSelect 0 0) -match 'OK') 'S4 open row quality select'
Start-Sleep -Milliseconds 1400
Ok ((PickOptionContains (ZH 'opt_q_a')) -match 'OK') 'S4 set returned quality=A'
Start-Sleep -Milliseconds 800
Ok ((SetRowQty 0 '1') -match 'OK') 'S4 returned qty=1'
Start-Sleep -Milliseconds 600
Ok ((ClickBtn 'btn_confirm_repair_return') -match 'OK') 'S4 confirm repair return'
Start-Sleep -Milliseconds 3200
Ok (([int](SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair WHERE return_order_id=$s3")) -eq 1) 'S4 repair-return record created'
Ok ((SqlOne "SELECT quality_type FROM outsource_return_order_repair WHERE return_order_id=$s3 ORDER BY id DESC LIMIT 1") -eq 'A') 'S4 returned quality=A stored'
Ok ((D (StockQty 38 'product_id' 48 'A')) -eq ((D $bA4) + 1)) 'S4 finished stock A +1 (repaired goods back)'
Ok ((D (RepairPay 23)) -eq ((D $bRep) + 100)) 'S4 repair payable unchanged by the return'
Ok ((ClickBtn 'lbl_repair_return') -match 'OK') 'S4 open REGISTER REPAIR RETURN again'
Start-Sleep -Milliseconds 1600
Ok ((BodyHas (ZH 'btn_confirm_repair_return')) -eq 'false') 'S4 no more returnable qty (dialog not opened)'
# un-audit must be blocked while a repair-return record exists
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S4 try un-audit while a return record exists'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 2500
Ok ((OrderField $s3 'status') -eq 'AUDITED') 'S4 un-audit blocked (return record exists)'
Write-Host ('S4 msg=' + (Txt '.el-message'))

# =====================================================================
Step 'S5 roll back and cancel everything'
$recId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_return_order_repair WHERE return_order_id=$s3")
Open ("/outsource/return-order/detail/$s3") 2600
Ok ((ClickRowBtn 0 'btn_revoke') -match 'OK') 'S5 click REVOKE on the repair-return row'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3000
Ok (([int](SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair WHERE id=$recId")) -eq 0) 'S5 repair-return record removed'
Ok ((D (StockQty 38 'product_id' 48 'A')) -eq (D $bA4)) 'S5 stock rolled back by cancel-return'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S5 un-audit repair order'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3000
Ok ((OrderField $s3 'status') -eq 'DRAFT') 'S5 repair order back to DRAFT'
Ok ((D (StockQty 38 'product_id' 48 'DEFECT')) -eq (D $bDef)) 'S5 DEFECT stock restored'
Ok ((D (RepairPay 23)) -eq (D $bRep)) 'S5 repair payable reversed'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S5 cancel repair order'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 2000
Ok ((OrderField $s3 'status') -eq 'CANCELLED') 'S5 repair order cancelled'

$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S6 no page/API errors during the whole flow'

Summary 'ui-e2e-12 outsourcing return types (DEFECT / REPAIR + repair-back)'
