# Browser E2E: the outsourcing RETURN order carries two types (DEFECT / REPAIR).
#
#   S1 **DISABLED 2026-09-21**: it used to create a DEFECT return DOCUMENT from this page. Per the user
#      decision that path was unified into the finished-goods receipt (with a work order -> that order's
#      加工退货 red-reversal; without one -> 无单加工退货 on the receipt list). The block is kept but
#      wrapped in `if ($false)`; durable coverage now lives in verify-no-order-return.ps1,
#      verify-delivery-menu.ps1 (sections 2/4/9) and verify-fix-f7-64-74.ps1.
#   S2 the two types differ on the FORM: REPAIR must hide/lock the work-order link, hide the BOM
#      column, turn the work-order field off and REQUIRE a factory charge; DEFECT forbids the charge.
#
#   NOTE: the REPAIR end-to-end (send the goods out, book the factory charge, register the repaired
#   goods back, close / reopen) is a separate file: ui-e2e-16-workorder-repair-close.ps1. That one
#   still runs on hardcoded fixtures of the old database generation, so it does not pass either -
#   refresh it the same way (discover every id at runtime) when it is needed.
#
# !! FIXTURES ARE DISCOVERED AT RUNTIME (2026-09-21 rewrite). The previous revision hardcoded ids of a
#    database generation that no longer exists (warehouse 38/41, order 29, delivery 30, product 48),
#    so it could never pass again. Now every id/code/name is read from the DB; if nothing suitable
#    exists the script FAILS LOUDLY instead of mis-asserting.
#
# ASCII ONLY. Chinese strings come either from ui-e2e-zh.json or from the DB fetched as TO_BASE64.
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
function SqlRow([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return @() }
  return @((($ls[1]) -split "`t") | ForEach-Object { "$_".Trim() })
}
function U([string]$b64) {
  if (-not $b64) { return '' }
  try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64)) } catch { return '' }
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function StockQty([int]$wh, [string]$col, [int]$id, [string]$q) { return (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND $col=$id AND quality_type='$q'") }
function PaySum([int]$sid) { return (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND status='UNSETTLED'") }
function ReturnPay([int]$sid) { return (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND source_bill_type='OUTSOURCE_RETURN' AND status='UNSETTLED'") }
function MaxId([string]$tbl) { return (SqlOne "SELECT COALESCE(MAX(id),0) FROM $tbl") }
function CurUrl() { return (EvalJs "location.href.replace(location.origin,'')") }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Info($m) { Write-Host ('INFO ' + $m) }
function BodyHasTxt([string]$text) { return (BodyHas $text) }
# ---- column-scoped helpers: find the column by its HEADER TEXT, then read/write the first row's cell
function CellQty([string]$headerText) {
  $b = B64 $headerText
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$b').replace(/\s+/g,'');const vis=e=>e.getClientRects().length>0;for(const t of [...document.querySelectorAll('.el-table')].filter(vis)){const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,'').trim());const si=hs.indexOf(K);if(si<0)continue;const tr=t.querySelector('.el-table__body tbody tr');if(!tr)return 'NOROW';const td=[...tr.querySelectorAll('td')][si];if(!td)return 'NOCELL';const inp=td.querySelector('input');return inp?('val='+inp.value):'NOINPUT'}return 'NOTABLE'})()"
  return (EvalJs $js)
}
function CellTextOf([string]$headerText) {
  # read the CELL TEXT (not the input value): Element Plus renders a non-filterable select's chosen
  # label in a span, so reading input.value returns '' even though the selection is there.
  $b = B64 $headerText
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$b').replace(/\s+/g,'');const vis=e=>e.getClientRects().length>0;for(const t of [...document.querySelectorAll('.el-table')].filter(vis)){const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,'').trim());const si=hs.indexOf(K);if(si<0)continue;const tr=t.querySelector('.el-table__body tbody tr');if(!tr)return 'NOROW';const td=[...tr.querySelectorAll('td')][si];if(!td)return 'NOCELL';return (td.innerText||'').replace(/\s+/g,' ').trim()}return 'NOTABLE'})()"
  return (EvalJs $js)
}
function SetCellQty([string]$headerText, [string]$value) {
  $b = B64 $headerText; $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$b').replace(/\s+/g,'');const V=T('$v');const vis=e=>e.getClientRects().length>0;for(const t of [...document.querySelectorAll('.el-table')].filter(vis)){const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,'').trim());const si=hs.indexOf(K);if(si<0)continue;const tr=t.querySelector('.el-table__body tbody tr');if(!tr)return 'NOROW';const td=[...tr.querySelectorAll('td')][si];if(!td)return 'NOCELL';const inp=td.querySelector('input');if(!inp)return 'NOINPUT';const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(inp,V);inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));inp.blur();return 'OK'})()"
  return (EvalJs $js)
}
# does any column header of the visible tables contain $headerText ? (used for "column must not exist")
function HasCol([string]$headerText) {
  $b = B64 $headerText
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$b').replace(/\s+/g,'');const vis=e=>e.getClientRects().length>0;const hs=[...document.querySelectorAll('.el-table')].filter(vis).flatMap(t=>[...t.querySelectorAll('.el-table__header th')]).map(th=>(th.innerText||'').replace(/\s+/g,'').trim());return String(hs.some(h=>h.indexOf(K)>=0))})()"
  return (EvalJs $js)
}
# is the form-item whose label is $labelText rendered at all?
function HasLabel([string]$labelText) {
  $b = B64 $labelText
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$b').replace(/[\s*:]/g,'');const vis=e=>e.getClientRects().length>0;return String([...document.querySelectorAll('.el-form-item')].filter(vis).some(it=>{const lab=it.querySelector('.el-form-item__label');return lab&&(lab.innerText||'').replace(/[\s*:]/g,'')===L}))})()"
  return (EvalJs $js)
}
# headers of every visible table (diagnostics: tells you which column text to look for)
function DumpHeads() {
  return (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('.el-table')].filter(vis).map(t=>[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,' ').trim())))})()")
}
# click the switch inside the "factory charge" form-item (el-switch has no button element)
function ClickChargeSwitch() {
  $b = B64 (ZH 'lbl_factory_charge')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$b').replace(/[\s*:]/g,'');const vis=e=>e.getClientRects().length>0;for(const it of [...document.querySelectorAll('.el-form-item')].filter(vis)){const lab=it.querySelector('.el-form-item__label');if(!lab)continue;if((lab.innerText||'').replace(/[\s*:]/g,'')!==L)continue;const sw=it.querySelector('.el-switch');if(!sw)return 'NOSWITCH';sw.click();return 'OK'}return 'NOLABEL'})()"
  return (EvalJs $js)
}

# =====================================================================
# fixture discovery: a work order whose product master has A-grade stock in one of OUR finished-goods
# warehouses and owns a BOM snapshot (those three facts are exactly what the return form needs).
# =====================================================================
$sqlPick = "SELECT o.id, o.code, o.factory_id, TO_BASE64(s.name), p.product_id, TO_BASE64(p.product_name), w.id, TO_BASE64(w.warehouse_name), st.quantity, b.id, b.bom_version FROM outsource_order o JOIN outsource_order_product p ON p.order_id = o.id JOIN warehouse_stock st ON st.product_id = p.product_id AND st.quality_type = 'A' JOIN warehouse w ON w.id = st.warehouse_id AND w.warehouse_type = 'FINISHED' JOIN bom_snapshot b ON b.product_master_id = p.product_id LEFT JOIN supplier s ON s.id = o.factory_id WHERE o.status IN ('PRODUCING','FINISHED') AND st.quantity >= 50 AND o.factory_id IS NOT NULL ORDER BY o.id LIMIT 1"
$f = SqlRow $sqlPick
if ($f.Count -lt 11) {
  # 2026-10-05 F7-294: fixture missing => SKIP + exit 0 (see ui-e2e-11 for the rationale).
  Write-Host 'SKIP no usable work order found.'
  Write-Host '     need: a work order whose product master has A-grade stock (>=50) in one of our finished-goods'
  Write-Host '     warehouses AND has a BOM snapshot (that is what the return form prefills from).'
  Write-Host 'RESULT SKIP ui-e2e-12 return order types (fixture missing, nothing verified)'
  exit 0
}
$orderId   = [int]$f[0]
$orderCode = [string]$f[1]
$factoryId = [int]$f[2]
$factoryName = U ([string]$f[3])
$masterId  = [int]$f[4]
$productName = U ([string]$f[5])
$outWh     = [int]$f[6]
$outWhName = U ([string]$f[7])
$outStock  = [int]$f[8]
$snapId    = [int]$f[9]
$snapVer   = [string]$f[10]
$factoryWh = [int](SqlOne "SELECT id FROM warehouse WHERE factory_id=$factoryId AND warehouse_category='OUTSOURCE' ORDER BY id LIMIT 1")
# NB: never pass GROUP_CONCAT through the console (PowerShell mangles the quoting) - read one row per
# material instead, which is also what the assertions below want.
$mats = @()
foreach ($line in ((SqlRaw "SELECT outsource_material_id, quantity_per_set FROM bom_snapshot_item WHERE snapshot_id=$snapId ORDER BY id") -split "`n" | Select-Object -Skip 1)) {
  $cells = @(("$line").Trim() -split "`t")
  if (@($cells).Count -ge 2 -and $cells[0] -ne '') { $mats += , @([int]$cells[0], [double]$cells[1]) }
}
$matIds = @(); $matPer = @()
foreach ($m in $mats) { $matIds += [int]$m[0]; $matPer += [double]$m[1] }
Info ('fixture: order id=' + $orderId + ' code=' + $orderCode + ' factory id=' + $factoryId + ' name=' + $factoryName)
Info ('fixture: product master=' + $masterId + ' name=' + $productName + ' | out warehouse id=' + $outWh + ' name=' + $outWhName + ' A-stock=' + $outStock)
Info ('fixture: BOM snapshot id=' + $snapId + ' v' + $snapVer + ' materials=' + (($matIds | ForEach-Object { 'm' + $_ }) -join ',') + ' | factory outsource warehouse=' + $factoryWh)
if ($factoryWh -le 0 -or @($matIds).Count -eq 0) {
  # 2026-10-05 F7-294: fixture incomplete (no factory outsource warehouse / no BOM items) => SKIP, not a red.
  Write-Host 'SKIP the discovered order has no factory outsource warehouse or no BOM snapshot items.'
  Write-Host 'RESULT SKIP ui-e2e-12 return order types (fixture incomplete, nothing verified)'
  exit 0
}
$retQty = 10

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
# S1 DISABLED 2026-09-21（用户口径）：加工退货不再由「独立退货单」承担 —— 该入口已从
# /outsource/return-order 页面移除，且后端会拒绝新建 DEFECT 单据（提示去成品收货办理）。
# 替代覆盖：
#   ① 有加工单 → 成品收货页的「加工退货」（红冲）—— verify-delivery-menu §④ + verify-fix-f7-64-74
#   ② 无加工单 → 成品收货列表的「无单加工退货」—— verify-no-order-return.ps1 + verify-delivery-menu §⑨
# 保留原文用 if ($false) 包住仅供对照（**不要再打开它**）。
Write-Host 'S1 SKIPPED: the DEFECT return-DOCUMENT entry was removed on 2026-09-21 (see the header)'
if ($false) {
$bOut   = StockQty $outWh 'product_id' $masterId 'A'
$bPay   = PaySum $factoryId
$m25 = [int]((($matsDesc -split ',')[0]) -split ':')[0]
$m31 = if ((($matsDesc -split ',').Count) -ge 2) { [int]((($matsDesc -split ',')[1]) -split ':')[0] } else { 0 }
$bMats = @()
foreach ($mid in $matIds) { $bMats += [double](StockQty $factoryWh 'material_id' $mid 'GOOD') }
# the audit books a NEGATIVE OUTSOURCE_RETURN payable, so "the entry exists" is a COUNT question -
# summing signs would be fooled by older rows left over from previous runs.
$bRetCnt = [int](SqlOne "SELECT COUNT(*) FROM finance_payable WHERE supplier_id=$factoryId AND source_bill_type='OUTSOURCE_RETURN' AND status='UNSETTLED'")
Info ("BASE product$masterId.A.wh$outWh=" + $bOut + " payable$factoryId=" + $bPay + " factory materials=" + (($bMats | ForEach-Object { "$_" }) -join ',') + " unsettledReturnPayables=" + $bRetCnt)

Open '/outsource/return-order' 3000
Ok ((ClickBtn 'btn_new_defect_return') -match 'OK') 'S1 clicked NEW DEFECT return'
Start-Sleep -Milliseconds 2600
Ok ((CurUrl) -match 'returnType=DEFECT') ('S1 url carries the type url=' + (CurUrl))
Ok ((BodyHas (ZH 'val_defect_type')) -eq 'true') 'S1 form type = DEFECT'
Ok ((BodyHasTxt (ZH 'txt_defect_no_charge')) -eq 'true') 'S1 the defect hint says the factory does not charge us'

Ok ((OpenSelect 'lbl_factory') -match 'OK') 'S1 opened the factory select'
Start-Sleep -Milliseconds 1200
Ok ((PickOptionB64 (B64 $factoryName)) -match 'OK') ('S1 picked the factory=' + $factoryName)
Start-Sleep -Milliseconds 1600
Ok ((OpenSelect 'lbl_out_finished_wh') -match 'OK') 'S1 opened the out-warehouse select'
Start-Sleep -Milliseconds 1200
Ok ((PickOptionB64 (B64 $outWhName)) -match 'OK') ('S1 picked the out warehouse=' + $outWhName)
Start-Sleep -Milliseconds 1600
Ok ((HasLabel (ZH 'lbl_rel_order')) -eq 'true') 'S1 the work-order link field is offered for DEFECT'

Ok ((ClickBtn 'btn_add_product_plus') -match 'OK') 'S1 clicked ADD PRODUCT'
Start-Sleep -Milliseconds 1200
# row 0: product select -> (link the order HERE so the snapshot locks) -> spec -> qty
Ok ((OpenRowSelect 0 0) -match 'OK') 'S1 opened the product select of row 0'
Start-Sleep -Milliseconds 1200
Ok ((PickOptionB64 (B64 $productName)) -match 'OK') ('S1 picked the product=' + $productName)
Start-Sleep -Milliseconds 1800
Info ('row headers = ' + (DumpHeads))
# The work order must be linked AFTER the row has a product: onLinkedOrderChange only walks rows that
# already carry one, and that is what locks the BOM source to the order's own snapshot.
Ok ((OpenSelect 'lbl_rel_order') -match 'OK') 'S1 opened the work-order select'
Start-Sleep -Milliseconds 1200
Ok ((PickOptionContains $orderCode) -match 'OK') ('S1 linked the work order=' + $orderCode)
Start-Sleep -Milliseconds 2200
$snapCell = CellTextOf (ZH 'lbl_bom_src')
Info ('after linking the order, BOM-source cell=' + $snapCell)
Ok ($snapCell -match ('v' + $snapVer)) ('S1 BOM source auto-filled from the linked order (v' + $snapVer + '), cell=' + $snapCell)
Ok ((OpenRowSelect 0 2) -match 'OK') 'S1 opened the spec select of row 0'
Start-Sleep -Milliseconds 1200
Ok ((PickOptionContains (ZH 'opt_q_a')) -match 'OK') 'S1 picked spec A'
Start-Sleep -Milliseconds 1200
$qtyRaw = SetCellQty (ZH 'lbl_return_qty') ([string]$retQty)
if ($qtyRaw -notmatch 'OK') { $qtyRaw = SetRowInput 0 3 ([string]$retQty) }
Ok ($qtyRaw -match 'OK') ('S1 set the return qty=' + $retQty + ' raw=' + $qtyRaw)
$qtyBack = CellQty (ZH 'lbl_return_qty')
Ok ($qtyBack -eq ('val=' + $retQty)) ('S1 the row reads back qty=' + $retQty + ', cell=' + $qtyBack)
Start-Sleep -Milliseconds 900
$prevRid = [int](MaxId 'outsource_return_order')
Ok ((ClickBtn 'btn_save') -match 'OK') 'S1 clicked SAVE'
Start-Sleep -Milliseconds 3500

$rid = [int](MaxId 'outsource_return_order')
Info ('before save max id=' + $prevRid + ' / after save max id=' + $rid + ' / last message=' + (Txt '.el-message'))
Ok ($rid -gt $prevRid) ('S1 a NEW return order was created id=' + $rid)
if ($rid -le $prevRid) {
  # fail fast: without a fresh row every later assertion would silently judge an OLD record instead
  Write-Host ('FAIL S1 aborted - the form created nothing (last message=' + (Txt '.el-message') + ')')
  Write-Host ('RESULT FAIL ui-e2e-12 return order types (PASS=' + $script:PASS + ' FAIL=' + ($script:FAIL + 1) + ')')
  exit 1
}
$r = SqlRow ("SELECT status, return_type, order_id, charge_flag, warehouse_id, factory_id FROM outsource_return_order WHERE id=$rid")
Ok (@($r).Count -ge 6) ('S1 row readable in DB (cols=' + @($r).Count + ')')
Ok ([string]$r[0] -eq 'DRAFT') 'S1 status=DRAFT'
Ok ([string]$r[1] -eq 'DEFECT') 'S1 return_type=DEFECT (default)'
Ok ([int]$r[2] -eq $orderId) ('S1 linked work order persisted=' + $orderId)
Ok ([string]$r[3] -eq '0') 'S1 charge_flag forced to 0 (a defect return never charges us)'
Ok ([int]$r[4] -eq $outWh) ('S1 out warehouse persisted=' + $outWh)
$rp = SqlRow ("SELECT quality_type, quantity, bom_snapshot_id, product_id FROM outsource_return_order_product WHERE return_order_id=$rid LIMIT 1")
Ok ([string]$rp[0] -eq 'A') 'S1 product row spec=A'
Ok ([int]$rp[1] -eq $retQty) ('S1 product row qty=' + $retQty)
Ok ([int]$rp[2] -eq $snapId) ('S1 product row carries the BOM snapshot=' + $snapId)
Ok ((SqlOne "SELECT COUNT(*) FROM outsource_return_order_item WHERE return_order_id=$rid") -gt 0) 'S1 material lines were split out of the snapshot'

Open ("/outsource/return-order/detail/$rid") 2600
Ok ((BodyHas $orderCode) -eq 'true') ('S1 detail shows the linked work order=' + $orderCode)
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S1 clicked AUDIT'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$rid") -eq 'AUDITED') 'S1 status=AUDITED'
$afterOut = StockQty $outWh 'product_id' $masterId 'A'
Ok ((D $afterOut) -eq ((D $bOut) - $retQty)) ('S1 after audit: finished goods -' + $retQty + ' -> ' + $afterOut)
for ($i = 0; $i -lt @($matIds).Count; $i++) {
  $gain = $retQty * [double]$matPer[$i]
  $expectM = [double]$bMats[$i] + $gain
  $gotM = [double](StockQty $factoryWh 'material_id' $matIds[$i] 'GOOD')
  Ok ([math]::Abs($gotM - $expectM) -lt 0.001) ('S1 after audit: factory material ' + $matIds[$i] + ' (+' + $gain + ') -> ' + $gotM + ' expected ' + $expectM)
}
Ok ((D (PaySum $factoryId)) -lt (D $bPay)) 'S1 after audit: payable reduced (the returned material was credited back)'
Ok ([int](SqlOne "SELECT COUNT(*) FROM finance_payable WHERE supplier_id=$factoryId AND source_bill_type='OUTSOURCE_RETURN' AND status='UNSETTLED'") -gt $bRetCnt) 'S1 after audit: a new OUTSOURCE_RETURN payable entry exists'

Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S1 clicked UN-AUDIT'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (StockQty $outWh 'product_id' $masterId 'A')) -eq (D $bOut)) 'S1 after un-audit: finished goods rolled back'
for ($i = 0; $i -lt @($matIds).Count; $i++) {
  $gotM = [double](StockQty $factoryWh 'material_id' $matIds[$i] 'GOOD')
  Ok ([math]::Abs($gotM - [double]$bMats[$i]) -lt 0.001) ('S1 after un-audit: factory material ' + $matIds[$i] + ' rolled back to ' + $gotM)
}
Ok ((D (PaySum $factoryId)) -eq (D $bPay)) 'S1 after un-audit: payable rolled back'
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$rid") -eq 'DRAFT') 'S1 after un-audit: back to DRAFT'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S1 clicked CANCEL (frees the returnable qty so the file re-runs)'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2200
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$rid") -eq 'CANCELLED') 'S1 return order cancelled'
}   # end of the DISABLED S1 block (2026-09-21)

# =====================================================================
Step 'S2 the two types differ on the FORM'
# 显式指定类型：本页默认已改为**维修退货**（加工退货已统一到成品收货），但 ?returnType=DEFECT
# 仍会按 DEFECT 渲染表单（存量加工退货草稿编辑用）—— 这里就是要对照两种类型的表单差异。
Open '/outsource/return-order/add?returnType=DEFECT' 3000
Ok ((HasLabel (ZH 'lbl_rel_order')) -eq 'true') 'S2 DEFECT: the work-order link field exists'
Ok ((HasCol 'BOM') -eq 'true') 'S2 DEFECT: the BOM column exists'
Ok ((HasLabel (ZH 'lbl_factory_charge')) -eq 'true') 'S2 DEFECT: the factory-charge field is offered (but is disabled)'
Ok ((OpenSelect 'lbl_mr_type') -match 'OK') 'S2 opened the type select'
Start-Sleep -Milliseconds 1200
Ok ((PickOptionB64 (B64 (ZH 'val_repair_type'))) -match 'OK') 'S2 switched to REPAIR'
Start-Sleep -Milliseconds 1600
Ok ((BodyHasTxt (ZH 'txt_repair_hint')) -eq 'true') 'S2 REPAIR: the hint switched to the repair wording'
Ok ((HasLabel (ZH 'lbl_rel_order')) -eq 'false') 'S2 REPAIR: the work-order link field is gone'
Ok ((HasCol 'BOM') -eq 'false') 'S2 REPAIR: the BOM column is gone (no material is returned)'
Ok ((HasLabel (ZH 'lbl_send_out_wh')) -eq 'true') 'S2 REPAIR: the out warehouse is relabelled to the repair wording'
# fill the two fields the validator checks FIRST, then save without a charge: the charge rule must fire
Ok ((OpenSelect 'lbl_factory') -match 'OK') 'S2 opened the factory select'
Start-Sleep -Milliseconds 1200
Ok ((PickOptionB64 (B64 $factoryName)) -match 'OK') ('S2 picked the factory=' + $factoryName)
Start-Sleep -Milliseconds 1600
Ok ((OpenSelect 'lbl_send_out_wh') -match 'OK') 'S2 opened the repair out-warehouse select'
Start-Sleep -Milliseconds 1200
Ok ((PickOptionB64 (B64 $outWhName)) -match 'OK') ('S2 picked the out warehouse=' + $outWhName)
Start-Sleep -Milliseconds 1600
# switching the type turned the charge on automatically - turn it OFF again so the "REPAIR must be
# charged" rule itself is what rejects the form (not the amount rule)
Ok ((ClickChargeSwitch) -match 'OK') 'S2 turned the factory charge off'
Start-Sleep -Milliseconds 800
$prevMax = [int](MaxId 'outsource_return_order')
Ok ((ClickBtn 'btn_save') -match 'OK') 'S2 clicked SAVE without a charge'
Start-Sleep -Milliseconds 2200
$msg = Txt '.el-message'
Ok ($msg -match (ZH 'msg_repair_need_charge')) ('S2 REPAIR is rejected without a charge, msg=' + $msg)
Ok (([int](MaxId 'outsource_return_order') -le $prevMax)) 'S2 nothing was persisted (the form was rejected)'

# =====================================================================
Step 'S3 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S3 no page/API errors during the flow'

Summary 'ui-e2e-12 return order types (data-driven)'
