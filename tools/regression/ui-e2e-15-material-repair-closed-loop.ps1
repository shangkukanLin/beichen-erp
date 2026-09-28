# UI E2E 15: ORDER material return closed loop (2026-09-28 three-type rework). ASCII ONLY.
#   Replaces the old "repair return linked to an UNFINISHED order" scenario: that combination is now
#   REJECTED by the backend (MaterialReturnType.checkOrderStatus) -- a RECEIVING order may only be used
#   for 订单退料 (ORDER), while 退货退款/维修返回 may only link a FINISHED order (or no order at all).
#   S1 list: linked leaf has the unified tabs (有效单据/已返回完/已作废) + the new type column
#   S2 add page: pick supplier -> pick the RECEIVING order -> type AUTO-LOCKS to 订单退料 (disabled) + hint
#   S3 create draft -> DB keeps return_type=ORDER + material_order_id, nothing deducted yet
#   S4 audit -> source warehouse -3, order received -3 AND order_returned_qty +3, payable untouched
#   S5 detail: linked order shown, type 订单退料, NO repair-return / close buttons (order return is not a repair)
#   S6 un-audit -> order received +3 back, order_returned_qty 0, stock restored, deducted_flag cleared
#   S7 cancel doc -> CANCELLED
#   S8 no errors
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
function StockQty([int]$wh, [int]$id) {
  return SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND material_id=$id AND quality_type='GOOD'"
}
function OrderRecv([int]$itemId) { return SqlOne "SELECT received_quantity FROM outsource_material_order_item WHERE id=$itemId" }
function OrderReturned([int]$itemId) { return SqlOne "SELECT COALESCE(order_returned_qty,0) FROM outsource_material_order_item WHERE id=$itemId" }
function PaySum([int]$sid) { return SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND status='UNSETTLED'" }
function MaxId([string]$tbl) { return SqlOne "SELECT COALESCE(MAX(id),0) FROM $tbl" }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function HasBtn([string]$key) {
  $b = B64 (ZH $key)
  return (EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$b');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()")
}
function SqlRow([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return @() }
  return (($ls[1] -split "`t") | ForEach-Object { "$_".Trim() })
}
# The add page lists EVERY material of the picked warehouse, so "row 0" is not necessarily the material we
# care about -- set the qty on the row whose material name matches instead.
function SetQtyByMaterial([string]$matName, [string]$val) {
  $b = B64 $matName; $v = B64 $val
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$b').replace(/\s+/g,'');const V=T('$v');const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';for(const tr of [...t.querySelectorAll('.el-table__body tbody tr')]){if((tr.innerText||'').replace(/\s+/g,'').indexOf(N)<0)continue;const inp=tr.querySelector('input');if(!inp)return 'NOINPUT';const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(inp,V);inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));inp.blur();return 'OK'}return 'NOROW'})()"
  return (EvalJs $js)
}

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
# 2026-09-28: one consistent fixture tuple -- the order must be in RECEIVING (that is the only status that
#   accepts 订单退料), it must own an item whose material really holds GOOD stock in the warehouse we pick,
#   the order must belong to the supplier we pick (the page only offers the chosen supplier's orders) and the
#   item needs received >= 5 so the -3 deduction is visible.
$fx = SqlRow "SELECT mo.id, mo.code, moi.id, moi.outsource_material_id, moi.received_quantity, COALESCE(moi.order_returned_qty,0), st.warehouse_id, w.warehouse_name, m.material_name, st.quantity, COALESCE(sup.id,0), COALESCE(sup.name,'') FROM outsource_material_order mo JOIN outsource_material_order_item moi ON moi.order_id=mo.id JOIN warehouse_stock st ON st.material_id=moi.outsource_material_id AND st.quality_type='GOOD' AND st.quantity >= 10 JOIN warehouse w ON w.id=st.warehouse_id JOIN outsource_material m ON m.id=moi.outsource_material_id LEFT JOIN supplier sup ON sup.id=mo.supplier_id WHERE mo.status='RECEIVING' AND moi.received_quantity >= 5 ORDER BY st.quantity DESC LIMIT 1"
$orderId = [int]$fx[0]; $orderCode = "$($fx[1])"; $itemId = [int]$fx[2]; $matId = [int]$fx[3]
$whId = [int]$fx[6]; $whName = "$($fx[7])"; $matName = "$($fx[8])"; $supId = [int]$fx[10]; $supName = "$($fx[11])"
Write-Host ('FIXTURE order=' + $orderId + ' (' + $orderCode + ') item=' + $itemId + ' material=' + $matId + ' (' + $matName + ') wh=' + $whId + ' (' + $whName + ') supplier=' + $supId + ' (' + $supName + ')')
Ok (($orderId -gt 0) -and ($itemId -gt 0) -and ($matId -gt 0) -and ($whId -gt 0) -and ($supId -gt 0) -and ($whName -ne '') -and ($matName -ne '')) 'fixture derived (RECEIVING order + its item + material with GOOD stock + supplier)'

# =====================================================================
Step 'S1 linked leaf: unified tabs + type column'
Open '/outsource/material-return' 3000
$r1 = Rows 0
$head = ($r1.head -join '|')
Write-Host ('S1 head=' + $head)
Ok ((BodyHas (ZH 'tab_leaf_effective')) -eq 'true') 'S1 tab 有效单据 present'
Ok ((BodyHas (ZH 'tab_leaf_returned')) -eq 'true') 'S1 tab 已返回完 present'
Ok ((BodyHas (ZH 'tab_leaf_void')) -eq 'true') 'S1 tab 已作废 present'
Ok ($head -match [regex]::Escape((ZH 'col_mr_type'))) 'S1 type column present (types share a leaf now)'
Ok ((BodyHas (ZH 'txt_mr_sent_returned')) -eq 'false') 'S1 dedicated sent/returned column is gone (merged into the type column)'
Ok ((Errs) -eq '[]') 'S1 no errors'

# =====================================================================
Step 'S2 add page: linked leaf -> RECEIVING order -> type auto-locks to 订单退料'
Ok ((ClickBtn 'btn_new_refund') -match 'OK') 'S2 click "new" on the 关联退料 leaf'
Start-Sleep -Milliseconds 2800
Ok ((CurUrl) -match 'linked=WITH_ORDER') ('S2 url carries linked=WITH_ORDER url=' + (CurUrl))
Ok ((BodyHas (ZH 'lbl_mr_order')) -eq 'true') 'S2 field "link material order" present'
# NOTE: the type is still the default (退货退款) until an order is picked, so the supplier field is labelled
# 「退回对象」 (only 维修返回 switches it to 维修供应商) -- select it by that label.
Ok ((SelectLabelText 'lbl_return_target' $supName) -match 'OK') ('S2 pick supplier=' + $supName)
Start-Sleep -Milliseconds 1600
Ok ((SelectLabelContains 'lbl_mr_order' $orderCode 2200) -match 'OK') ('S2 pick the RECEIVING material order ' + $orderCode)
Start-Sleep -Milliseconds 1800
Ok ((BodyHas (ZH 'txt_mr_order_locked')) -eq 'true') 'S2 hint: unfinished order -> only 订单退料 is possible'
$bT = B64 (ZH 'lbl_mr_type')
$disJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$bT');const vis=e=>e.getClientRects().length>0;const it=[...document.querySelectorAll('.el-form-item')].filter(vis).find(e=>{const l=e.querySelector('.el-form-item__label');return l&&(l.innerText||'').trim().indexOf(t)>=0});if(!it)return 'NOITEM';const inp=it.querySelector('input');const dis=!!(it.querySelector('.is-disabled')||(inp&&inp.disabled));return 'DIS='+String(dis)+'|TXT='+(it.innerText||'').replace(/\s+/g,' ').trim()})()"
$dis = EvalJs $disJs
Write-Host ('S2 type field => ' + $dis)
Ok ($dis -match 'DIS=true') ('S2 type select is locked (disabled) while the order is unfinished (' + $dis + ')')
Ok ((BodyHas (ZH 'opt_type_order')) -eq 'true') 'S2 locked type displays 订单退料'

# =====================================================================
Step 'S3 create draft (order linked, nothing deducted yet)'
$bItemRecv = D (OrderRecv $itemId)
$bItemReturned = D (OrderReturned $itemId)
$bStock = D (StockQty $whId $matId)
$bpay = PaySum $supId
Write-Host ('BASE item' + $itemId + ' recv=' + $bItemRecv + ' orderReturned=' + $bItemReturned + ' wh' + $whId + '.m' + $matId + '=' + $bStock + ' payable' + $supId + '=' + $bpay)
Ok ((SelectLabelText 'lbl_src_wh_out' $whName) -match 'OK') ('S3 pick source warehouse=' + $whName)
Start-Sleep -Milliseconds 2200
Ok ((SetQtyByMaterial $matName '3') -match 'OK') ('S3 qty=3 on material ' + $matName)
# still locked at submit time (guards against the type drifting back to the default while filling the form)
Write-Host ('S3 just before save => type field : ' + (EvalJs $disJs))
Ok ((EvalJs $disJs) -match 'DIS=true') 'S3 type still locked as 订单退料 right before saving'
Ok ((ClickBtn 'btn_save_draft') -match 'OK') 'S3 save draft'
Start-Sleep -Milliseconds 3400
Write-Host ('S3 POST body => ' + (EvalJs "String(window.__lastPost).slice(0,400)"))
$rid = [int](MaxId 'outsource_material_return')
Ok ($rid -gt 0) ('S3 created id=' + $rid)
Ok ((D (SqlOne "SELECT material_order_id FROM outsource_material_return WHERE id=$rid")) -eq $orderId) 'S3 linked material order persisted'
Ok ((SqlOne "SELECT return_type FROM outsource_material_return WHERE id=$rid") -eq 'ORDER') 'S3 return_type=ORDER persisted (auto-selected by order status)'
Ok ((SqlOne "SELECT deducted_flag FROM outsource_material_return WHERE id=$rid") -eq '0') 'S3 deducted_flag=0 before audit'
Ok ((D (OrderRecv $itemId)) -eq $bItemRecv) 'S3 order received qty untouched before audit'
Ok ((D (OrderReturned $itemId)) -eq $bItemReturned) 'S3 order returned qty untouched before audit'

# =====================================================================
Step 'S4 audit (RECEIVING order -> deduct received + accumulate order-returned; no payable)'
Open ("/outsource/material-return/detail/$rid") 2800
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S4 audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3400
Write-Host ('S4 item' + $itemId + ' recv=' + (OrderRecv $itemId) + ' orderReturned=' + (OrderReturned $itemId) + ' wh' + $whId + '.m' + $matId + '=' + (StockQty $whId $matId))
Ok ((SqlOne "SELECT deducted_flag FROM outsource_material_return WHERE id=$rid") -eq '1') 'S4 deducted_flag=1 frozen'
Ok ((D (OrderRecv $itemId)) -eq ($bItemRecv - 3)) 'S4 order received qty -3 (net = received - returned)'
Ok ((D (OrderReturned $itemId)) -eq ($bItemReturned + 3)) 'S4 order_returned_qty +3 (permanent, does not come back)'
Ok ((D (StockQty $whId $matId)) -eq ($bStock - 3)) 'S4 source warehouse -3'
Ok ((D (PaySum $supId)) -eq (D $bpay)) 'S4 payable UNTOUCHED (order return has no finance leg)'

# =====================================================================
Step 'S5 detail: type 订单退料 + linked order, and NO repair-only actions'
Ok ((BodyHas $orderCode) -eq 'true') 'S5 linked order code shown'
Ok ((BodyHas (ZH 'opt_type_order')) -eq 'true') 'S5 type shown as 订单退料'
Ok ((HasBtn 'btn_repair_return') -eq 'false') 'S5 repair-return button absent (not a repair doc)'
Ok ((HasBtn 'btn_mr_close') -eq 'false') 'S5 close button absent (no return tracking for 订单退料)'

# =====================================================================
Step 'S6 un-audit -> order deduction rolled back'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S6 un-audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3400
Write-Host ('S6 item' + $itemId + ' recv=' + (OrderRecv $itemId) + ' orderReturned=' + (OrderReturned $itemId) + ' wh' + $whId + '.m' + $matId + '=' + (StockQty $whId $matId))
Ok ((D (OrderRecv $itemId)) -eq $bItemRecv) 'S6 order received qty restored'
Ok ((D (OrderReturned $itemId)) -eq $bItemReturned) 'S6 order_returned_qty back to its base value'
Ok ((D (StockQty $whId $matId)) -eq $bStock) 'S6 source warehouse restored'
Ok ((SqlOne "SELECT deducted_flag FROM outsource_material_return WHERE id=$rid") -eq '0') 'S6 deducted_flag cleared'

# =====================================================================
Step 'S7 cancel doc'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S7 cancel doc'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2400
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'CANCELLED') 'S7 doc cancelled'

# =====================================================================
Step 'S8 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S8 no page/API errors during the flow'

Summary 'ui-e2e-15 material ORDER return closed loop (linked RECEIVING order)'
