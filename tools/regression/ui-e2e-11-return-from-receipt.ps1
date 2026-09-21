# Browser E2E: launch a material RETURN order (outsource material return) from the material receipt pages.
#
#   A) material receipt LIST   (/outsource/material-order/delivery)
#        row button -> /outsource/material-return/add?supplierId=<supplier of that order>
#   B) material receipt DETAIL (/outsource/material-order/delivery/<materialOrderId>)
#        receive-record button -> /outsource/material-return/add?sourceDeliveryId=<record id>
#        then: prefill checks -> save draft -> audit (stock / payable) -> un-audit -> cancel
#
# !! 2026-09-21: the FINISHED-GOODS half of this file is GONE BY DESIGN.
#    /outsource/order/delivery no longer offers a RETURN entry at all (user decision): every receipt
#    row there hangs off an outsourcing work order, so returning goods is posted as a NEGATIVE
#    DEFECT_RETURN record on that very page (a negative receipt reversal, a.k.a. the defect-return
#    button). The guard rails for that live in verify-delivery-menu.ps1 sections 2 and 4:
#    list AND detail must have NO return button, while the detail page must HAVE the defect-return one.
#
# !! FIXTURES ARE DISCOVERED AT RUNTIME (2026-09-21 rewrite). The previous revision hardcoded ids of a
#    database generation that no longer exists (order 29 / delivery 30 / warehouse 37 / supplier 20),
#    so it could never pass again. Now every id/code/name is read from the DB, and the script FAILS
#    LOUDLY when the DB holds no usable receipt record instead of mis-asserting.
#
# ASCII ONLY in this file. Chinese strings come either from ui-e2e-zh.json or from the DB fetched as
# TO_BASE64 (decoded here), so the console never has to carry raw Chinese.
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
# all columns of the first data row (tab separated) - use when one row carries the whole fixture
function SqlRow([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return @() }
  return @((($ls[1]) -split "`t") | ForEach-Object { "$_".Trim() })
}
# decode a TO_BASE64(...) column back into a PowerShell string (keeps Chinese out of the console)
function U([string]$b64) {
  if (-not $b64) { return '' }
  try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64)) } catch { return '' }
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function StockQty([int]$wh, [string]$col, [int]$id) { return (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND $col=$id AND quality_type='GOOD'") }
function PaySum([int]$sid) { return (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND status='UNSETTLED'") }
function MaxId([string]$tbl) { return (SqlOne "SELECT COALESCE(MAX(id),0) FROM $tbl") }
function CurUrl() { return (EvalJs "location.href.replace(location.origin,'')") }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Info($m) { Write-Host ('INFO ' + $m) }
# value of the input in the first row under the column whose header is $headerText
function QtyCellVal([string]$headerText) {
  $b = B64 $headerText
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$b').replace(/\s+/g,'');const vis=e=>e.getClientRects().length>0;for(const t of [...document.querySelectorAll('.el-table')].filter(vis)){const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,'').trim());const si=hs.indexOf(K);if(si<0)continue;const tr=t.querySelector('.el-table__body tbody tr');if(!tr)return 'NOROW';const td=[...tr.querySelectorAll('td')][si];if(!td)return 'NOCELL';const inp=td.querySelector('input');return inp?('val='+inp.value):'NOINPUT'}return 'NOTABLE'})()"
  return (EvalJs $js)
}

# =====================================================================
# fixture discovery (no hardcoded generations)
#   a RECEIVE record that is AUDITED, whose material still has GOOD stock in the warehouse it was
#   received into, and which no live material-return order references yet (so the run is repeatable).
# =====================================================================
$sqlPick = "SELECT d.id, d.code, d.supplier_id, TO_BASE64(sup.name), d.to_warehouse_id, TO_BASE64(w.warehouse_name), i.outsource_material_id, TO_BASE64(m.material_name), LEAST(i.quantity, COALESCE(st.quantity,0)), d.source_order_id, mo.code FROM outsource_delivery d JOIN outsource_delivery_item i ON i.delivery_id = d.id JOIN warehouse_stock st ON st.warehouse_id = d.to_warehouse_id AND st.material_id = i.outsource_material_id AND st.quality_type = 'GOOD' LEFT JOIN supplier sup ON sup.id = d.supplier_id LEFT JOIN warehouse w ON w.id = d.to_warehouse_id LEFT JOIN outsource_material m ON m.id = i.outsource_material_id LEFT JOIN outsource_material_order mo ON mo.id = d.source_order_id WHERE d.delivery_type = 'RECEIVE' AND d.status = 'AUDITED' AND d.to_warehouse_id IS NOT NULL AND st.quantity > 0 AND NOT EXISTS (SELECT 1 FROM outsource_material_return h WHERE h.source_delivery_id = d.id AND h.status <> 'CANCELLED') ORDER BY d.id DESC LIMIT 1"
$f = SqlRow $sqlPick
if ($f.Count -lt 11) {
  Write-Host 'FAIL no usable material receipt record found.'
  Write-Host '     need one with: delivery_type=RECEIVE, status=AUDITED, a material with GOOD stock in its'
  Write-Host '     receiving warehouse, and no live return order referencing it.'
  Write-Host ('RESULT FAIL ui-e2e-11 material return from receipt (PASS=0 FAIL=1)')
  exit 1
}
$delivId  = [int]$f[0]
$delivCode = [string]$f[1]
$supId    = [int]$f[2]
$supName  = U ([string]$f[3])
$srcWh    = [int]$f[4]
$srcWhName = U ([string]$f[5])
$matId    = [int]$f[6]
$matName  = U ([string]$f[7])
$expQty   = [int]$f[8]
$moId     = [int]$f[9]
$moCode   = [string]$f[10]
Info ('fixture: receive record id=' + $delivId + ' code=' + $delivCode + ' -> material order id=' + $moId + ' code=' + $moCode)
Info ('fixture: supplier id=' + $supId + ' name=' + $supName + ' | source warehouse id=' + $srcWh + ' name=' + $srcWhName)
Info ('fixture: material id=' + $matId + ' name=' + $matName + ' | expected default return qty=' + $expQty)

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
Step 'S1 material receipt LIST row -> return order (supplier level)'
Open '/outsource/material-order/delivery' 3000
$t = Rows 0
Ok ($t.n -gt 0) ('S1 material receipt list loaded rows=' + $t.n)
Ok ((BodyHas (ZH 'btn_return')) -eq 'true') 'S1 the list offers the RETURN button'
# pick the first row that carries an order code, and resolve its supplier from the DB
$pick = ''
$pickIdx = -1
for ($i = 0; $i -lt $t.n; $i++) {
  foreach ($c in $t.rows[$i]) { if ($c -match '^MWO-') { $pick = $c; break } }
  if ($pick) { $pickIdx = $i; break }
}
Ok ($pick -ne '') ('S1 found a material order row code=' + $pick)
$pickSup = [int](SqlOne "SELECT supplier_id FROM outsource_material_order WHERE code='$pick'")
Ok ($pickSup -gt 0) ('S1 that order belongs to supplier id=' + $pickSup)
Ok ((ClickRowBtn $pickIdx 'btn_return') -match 'OK') 'S1 clicked the row-level RETURN'
Start-Sleep -Milliseconds 3200
$u = CurUrl
Ok ($u -match ('material-return/add\?supplierId=' + $pickSup)) ('S1 navigated with the supplier id url=' + $u)
Ok ((BodyHas (ZH 'lbl_return_target')) -eq 'true') 'S1 material return add page opened'
$pickSupName = U (SqlOne "SELECT TO_BASE64(name) FROM supplier WHERE id=$pickSup")
Ok ((BodyHas $pickSupName) -eq 'true') ('S1 supplier prefilled=' + $pickSupName)

# =====================================================================
Step 'S2 material receipt DETAIL receive-record row -> create / audit / un-audit / cancel'
$bStock = StockQty $srcWh 'material_id' $matId
$bPay   = PaySum $supId
Info ("BASE warehouse$srcWh.material$matId=" + $bStock + " payable$supId=" + $bPay)

Open ("/outsource/material-order/delivery/$moId") 3200
$ri = [int](FindRow $delivCode)
Ok ($ri -ge 0) ('S2 found the receive record row=' + $ri + ' code=' + $delivCode)
Ok ((ClickRowBtn $ri 'btn_return') -match 'OK') 'S2 clicked the record-level RETURN'
Start-Sleep -Milliseconds 3200
$u = CurUrl
Ok ($u -match ('sourceDeliveryId=' + $delivId)) ('S2 navigated with the source record url=' + $u)
Ok ((BodyHas (ZH 'lbl_return_target')) -eq 'true') 'S2 return add page opened'
Ok ((BodyHas $supName) -eq 'true') ('S2 supplier prefilled=' + $supName)
Ok ((BodyHas $srcWhName) -eq 'true') ('S2 source warehouse prefilled=' + $srcWhName)
Ok ((BodyHas $matName) -eq 'true') ('S2 only the materials of that record are listed=' + $matName)
$qv = QtyCellVal (ZH 'lbl_qty_col_mr')
Ok ($qv -eq ('val=' + $expQty)) ('S2 default qty = ' + $expQty + ' (min(returnable, source stock)), got ' + $qv)

Ok ((ClickBtn 'btn_save_draft') -match 'OK') 'S2 clicked SAVE DRAFT'
Start-Sleep -Milliseconds 3500
$mrId = [int](MaxId 'outsource_material_return')
Ok ($mrId -gt 0) ('S2 material return order created id=' + $mrId)
$r = SqlRow ("SELECT status, supplier_id, from_warehouse_id, source_delivery_id, return_type, material_order_id FROM outsource_material_return WHERE id=$mrId")
Ok (@($r).Count -ge 6) ('S2 row readable in DB (cols=' + @($r).Count + ')')
Ok ([string]$r[0] -eq 'DRAFT') 'S2 status=DRAFT'
Ok ([int]$r[1] -eq $supId) ('S2 supplier persisted=' + $supId)
Ok ([int]$r[2] -eq $srcWh) ('S2 source warehouse persisted=' + $srcWh)
Ok ([int]$r[3] -eq $delivId) ('S2 source_delivery_id persisted=' + $delivId)
Ok ([string]$r[4] -eq 'REFUND') 'S2 default type = REFUND'
Ok ([int]$r[5] -eq $moId) ('S2 linked material order persisted=' + $moId + ' (auto-carried from the record)')

Open ("/outsource/material-return/detail/$mrId") 2800
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S2 clicked AUDIT'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$mrId") -eq 'AUDITED') 'S2 status=AUDITED'
$afterStock = StockQty $srcWh 'material_id' $matId
Ok ((D $afterStock) -eq ((D $bStock) - $expQty)) ('S2 after audit: source warehouse -' + $expQty + ' -> ' + $afterStock)
Ok ((D (PaySum $supId)) -lt (D $bPay)) 'S2 after audit: payable reduced (negative entry)'

Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S2 clicked UN-AUDIT'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (StockQty $srcWh 'material_id' $matId)) -eq (D $bStock)) 'S2 after un-audit: source warehouse rolled back'
Ok ((D (PaySum $supId)) -eq (D $bPay)) 'S2 after un-audit: payable rolled back'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$mrId") -eq 'DRAFT') 'S2 after un-audit: back to DRAFT'

Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S2 clicked CANCEL (frees the returnable qty so the file re-runs)'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2200
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$mrId") -eq 'CANCELLED') 'S2 return order cancelled'

# =====================================================================
Step 'S3 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S3 no page/API errors during the flow'

Summary 'ui-e2e-11 material return entry on receipt pages (data-driven)'
