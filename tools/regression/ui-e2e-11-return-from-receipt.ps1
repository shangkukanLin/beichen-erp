# Browser E2E: launch a material RETURN order (outsource material return) from the material receipt pages.
#
#   A) material receipt LIST   (/outsource/material-order/delivery)
#        row button -> /outsource/material-return/add?supplierId=<supplier of that order>
#   B) material receipt DETAIL (/outsource/material-order/delivery/<materialOrderId>)
#        !! 2026-09-29 FINAL (user decision "去掉行内新增退货"): returns are launched from the DETAIL TOOLBAR
#           「物料退货」 ONLY. The receive-record row keeps 审核 ｜ 反审核 and must NOT offer 新增退货 any more.
#           Background: that row button was the merge target of the old 退不良 + 退货 toolbar entries earlier
#           the same day (both扣同一个退货仓库存 => judged as duplicated functionality). It only narrowed the
#           refundable qty down to one receipt record, while the backend persists NO source delivery id
#           (returnMaterial takes warehouseId + items only), so the dialog's "来源收货单" was display-only
#           => one entry is enough.
#           The flow itself is unchanged: toolbar 物料退货 -> RECEIVE_RETURN **draft** -> audit =>
#           warehouse -qty + order received_quantity -qty + negative payable -> un-audit => rolled back.
#           The old 退货-jump assertions live nowhere any more.
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
# 2026-09-28（P2 口径）：退货退款审核改为生成**对供应商的应收**（不再是负向应付）⇒ 断言须同时看应收侧
function RecvSum([int]$sid) { return (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receivable WHERE subject_type='SUPPLIER' AND supplier_id=$sid AND status='UNSETTLED'") }
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
# S2 (2026-09-29 FINAL, user decision "去掉行内新增退货") -- the DETAIL TOOLBAR 物料退货 is the single return
#     entry; the receive-record row keeps 审核 ｜ 反审核 (the old row-level 退货 that jumped to the
#     material-return ORDER is GONE, and the row-level 新增退货 is GONE with it).
#   New behaviour (the accounting legs are unchanged):
#     create  -> a RECEIVE_RETURN **draft** in outsource_delivery -- NOTHING moves yet
#     audit   -> source warehouse -qty  +  order item received_quantity -qty  +  negative payable
#     un-audit-> everything rolled back (payable ledger cancelled & zeroed)
#   The draft is deleted by this script afterwards (pure test artefact).
# =====================================================================
Step 'S2 detail toolbar: 物料退货 -> draft -> audit (stock/qty/payable) -> un-audit -> delete'
$bStock = D (StockQty $srcWh 'material_id' $matId)
$bPay   = D (PaySum $supId)
$ordItemId = [int](SqlOne ("SELECT item_id FROM outsource_delivery_item WHERE delivery_id=" + $delivId + " AND outsource_material_id=" + $matId + " LIMIT 1"))
$bRecv  = D (SqlOne ("SELECT COALESCE(received_quantity,0) FROM outsource_material_order_item WHERE id=" + $ordItemId))
$unitPrice = D (SqlOne ("SELECT COALESCE(unit_price,0) FROM outsource_material_order_item WHERE id=" + $ordItemId))
Info ('BASE stock=' + $bStock + ' receivedQty=' + $bRecv + ' payable=' + $bPay + ' unitPrice=' + $unitPrice + ' orderItem=' + $ordItemId)

Open ("/outsource/material-order/delivery/$moId") 3200
$ri = [int](FindRow $delivCode)
Ok ($ri -ge 0) ('S2 found the receive record row=' + $ri + ' code=' + $delivCode)
# the (AUDITED) row must offer 反审核 and must NOT offer ANY return entry any more (2026-09-29 FINAL:
# returns moved to the toolbar; the row-level 新增退货 and the old 退货-jump are both gone)
$rowBtnsJs = "(()=>{const vis=e=>e.getClientRects().length>0;const trs=[...document.querySelectorAll('.el-table__body tbody tr')].filter(vis);const tr=trs[$ri];if(!tr)return JSON.stringify(['NOROW']);return JSON.stringify([...tr.querySelectorAll('button')].filter(vis).map(b=>(b.innerText||'').trim()))})()"
$btns = @()
try { $btns = @((EvalJs $rowBtnsJs) | ConvertFrom-Json) } catch { $btns = @() }
# PS5.1：JSON 数组经管道整体返回、@() 会再套一层 ⇒ 显式展开（否则 $btns 只有一个元素 = 整个数组，
# -contains 恒 false，断言假红）
if (($btns.Count -eq 1) -and ($btns[0] -is [array])) { $btns = @($btns[0]) }
Info ('row buttons = ' + ($btns -join ' | '))
Ok ($btns -contains (ZH 'btn_unaudit')) 'S2 the (AUDITED) row offers 反审核'
Ok (-not ($btns -contains (ZH 'btn_new_return'))) 'S2 the row-level 新增退货 is GONE (single entry = the toolbar)'
Ok (-not ($btns -contains (ZH 'btn_return'))) 'S2 the old row-level 退货 entry is gone (no duplicate return path)'
# 2026-09-29（user口径「物料收退详情的收货记录列表需要有详细，参考加工收退的收货记录详情来做」）:
# every row carries 详细 -> the read-only 收货记录详情 page (same shape as /outsource/order/delivery/record/:id)
Ok ($btns -contains (ZH 'btn_detail_record')) 'S2 the row offers 详细 (→ 收货记录详情, mirrors the 加工 side)'
# the toolbar must carry that single return entry (label 物料退货; the old 退不良/退货 toolbar pair is gone)
Ok ((BodyHas (ZH 'btn_material_return')) -eq 'true') 'S2 the detail toolbar offers 物料退货 (the single return entry)'

# 2026-09-29：收货记录表必须**一行显示完**（家规「列表一行显示完、不左右滑动」）。
#   实测发现该表**本来就横滑 100px**（sumCols 1048 > 容器 948）⇒ 按实测重排了列宽；
#   操作列曾为「反审核 + 新增退货」加宽到 124（合计 932），最终口径去掉行内「新增退货」后回退 96
#   （合计 904 ≤ 948）。这里钉住它，避免以后再加按钮又横滑。
$fitJs = "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const wrap=t.querySelector('.el-table__body-wrapper .el-scrollbar__wrap')||t.querySelector('.el-table__body-wrapper');const cols=[...t.querySelectorAll('.el-table__header col')].map(c=>Number(c.getAttribute('width')||0));const ths=[...t.querySelectorAll('.el-table__header th')];const clipped=ths.filter(th=>{const c=th.querySelector('.cell')||th;return c.scrollWidth>c.clientWidth+1}).length;return JSON.stringify({over:wrap?Math.round(wrap.scrollWidth-wrap.clientWidth):-1,sumCols:cols.reduce((a,b)=>a+b,0),wrapW:wrap?Math.round(wrap.clientWidth):0,clipped:clipped,opClip:[...t.querySelectorAll('.el-table__body tbody tr')].map(tr=>[...tr.querySelectorAll('td')].pop()).filter(td=>td&&td.scrollWidth>td.clientWidth+1).length})})()"
$fit = $null
try { $fit = (EvalJs $fitJs) | ConvertFrom-Json } catch { $fit = $null }
if ($null -ne $fit) {
  Info ('record table over=' + $fit.over + ' sumCols=' + $fit.sumCols + ' wrapW=' + $fit.wrapW + ' clipped=' + $fit.clipped + ' opClip=' + $fit.opClip)
  Ok ([int]$fit.over -le 2) ('S2 the receive-record table does not scroll horizontally (over=' + $fit.over + ')')
  Ok ([int]$fit.sumCols -le ([int]$fit.wrapW + 2)) ('S2 its column widths fit the container (' + $fit.sumCols + ' <= ' + $fit.wrapW + ')')
  Ok ([int]$fit.clipped -eq 0) 'S2 no header of that table is clipped'
  # 2026-09-29（行内加「详细」⇒ 操作列 96→110）：**操作列单元格**也不许被裁（该列无 ellipsis，
  #   溢出即真裁切；其余列有 show-overflow-tooltip，故意省略 ⇒ 只查最后一列，避免假 FAIL）
  Ok ([int]$fit.opClip -eq 0) ('S2 the row action cell is not clipped (opClip=' + $fit.opClip + ')')
} else { Bad 'S2 could not measure the receive-record table' }

# 2026-09-29（user口径）: the row-level 详细 opens the read-only 收货记录详情 page
#   (/outsource/material-order/delivery/record/:id) -- created mirroring the 加工 side's record detail
#   (record header + items, creator/auditor shown; the p16 guard also checks the creator/auditor labels).
Ok ((ClickRowBtnContains $ri (ZH 'btn_detail_record')) -match 'OK') 'S2 clicked the row-level 详细'
Start-Sleep -Milliseconds 2600
Ok ((CurUrl) -match ('/outsource/material-order/delivery/record/' + $delivId)) ('S2 详细 opened the record detail page url=' + (CurUrl))
Ok ((BodyHas (ZH 'txt_rec_detail_title')) -eq 'true') 'S2 the record detail page renders the 收货记录详情 title'
Ok ((BodyHas $matName) -eq 'true') ('S2 the record detail shows the fixture material=' + $matName)
Ok ((Errs) -eq '[]') 'S2 the record detail page recorded no JS/API errors'
# back to the receive-record page: the return flow below needs it
Open ("/outsource/material-order/delivery/$moId") 3200
# 2026-09-29（user口径「单号也指到新的收货记录详情」）: the 单号 link used to jump to the legacy
#   物料收发单详情 (/outsource/delivery/detail/:id, still used by 库存流水) -- it now opens the SAME
#   record detail as the row-level 详细 button.
# NOTE: ClickText would hit the wrapping td/div.cell first (both carry the same text and come earlier in
#   document order) -- those have no handler, so the click must target the <a class="bill-link"> itself.
$zCode = B64 $delivCode
$clickCodeJs = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const K=T('$zCode');const vis=e=>e.getClientRects().length>0;const as=[...document.querySelectorAll('a.bill-link')].filter(e=>vis(e)&&(e.innerText||'').trim()===K);if(!as.length)return 'NOLINK:'+K;as[0].click();return 'OK'})()"
Ok ((EvalJs $clickCodeJs) -match 'OK') 'S2 clicked the 单号 link'
Start-Sleep -Milliseconds 2600
Ok ((CurUrl) -match ('/outsource/material-order/delivery/record/' + $delivId)) ('S2 单号 opened the same record detail url=' + (CurUrl))
Open ("/outsource/material-order/delivery/$moId") 3200

# NOTE: ClickBtn takes a zh KEY (it resolves it internally), BodyHas/ClickRowBtnContains take raw text
Ok ((ClickBtn 'btn_material_return') -match 'OK') 'S2 clicked the toolbar 物料退货'
Start-Sleep -Milliseconds 2200
# the toolbar entry carries no source receipt => the dialog preselects the FIRST refundable warehouse;
# pin the fixture's own receiving warehouse explicitly (option text = warehouse name) so the stock legs below
# are measured against a known warehouse
Info ('S2 warehouse select = ' + (DialogOpenSelect 0))
Start-Sleep -Milliseconds 900
Info ('S2 warehouse option = ' + (PickOptionContains $srcWhName))
Start-Sleep -Milliseconds 900
Ok ((BodyHas $srcWhName) -eq 'true') ('S2 the dialog warehouse set to the record warehouse=' + $srcWhName)
Ok ((BodyHas $matName) -eq 'true') ('S2 the dialog lists that order material=' + $matName)
# set the return qty of the fixture material to 1 (native value + input/change so Vue's v-model picks it up)
$zMat = B64 $matName
$setQtyJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$zMat');const vis=e=>e.getClientRects().length>0;const dlg=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];if(!dlg)return 'NODLG';for(const tr of dlg.querySelectorAll('.el-table__body tbody tr')){if((tr.innerText||'').indexOf(K)>=0){const inp=tr.querySelector('input');if(!inp)return 'NOINPUT';const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(inp,'1');inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'}}return 'NOROW'})()"
Info ('set return qty = ' + (EvalJs $setQtyJs))
Start-Sleep -Milliseconds 600
Ok ((ClickDialogBtn 'btn_confirm_return') -match 'OK') 'S2 confirmed 新增退货'
Start-Sleep -Milliseconds 2800
$retId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_delivery WHERE delivery_type='RECEIVE_RETURN'")
Ok ($retId -gt 0) ('S2 a 退货 record was created id=' + $retId)
$rr = SqlRow ("SELECT status, delivery_type, to_warehouse_id, source_order_id FROM outsource_delivery WHERE id=$retId")
Ok ([string]$rr[0] -eq 'DRAFT') 'S2 the new 退货 record is a DRAFT (no auto-audit any more)'
Ok ([string]$rr[1] -eq 'RECEIVE_RETURN') 'S2 delivery_type=RECEIVE_RETURN'
Ok ([int]$rr[2] -eq $srcWh) ('S2 return warehouse = the record warehouse=' + $srcWh)
Ok ([int]$rr[3] -eq $moId) ('S2 linked material order=' + $moId)
Ok ((D (SqlOne "SELECT COALESCE(quantity,0) FROM outsource_delivery_item WHERE delivery_id=$retId")) -eq 1) 'S2 return qty persisted = 1'
# a draft must NOT move anything (that is the whole point of "留住草稿，人工审核")
Ok ((D (StockQty $srcWh 'material_id' $matId)) -eq $bStock) 'S2 draft: warehouse stock untouched'
Ok ((D (SqlOne ("SELECT COALESCE(received_quantity,0) FROM outsource_material_order_item WHERE id=" + $ordItemId))) -eq $bRecv) 'S2 draft: order received qty untouched'
Ok ((D (PaySum $supId)) -eq $bPay) 'S2 draft: payable untouched'

Ok ((ClickRowBtnContains 0 (ZH 'btn_audit')) -match 'OK') 'S2 clicked 审核 on the new 退货 row'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2800
Ok ((SqlOne "SELECT status FROM outsource_delivery WHERE id=$retId") -eq 'AUDITED') 'S2 after audit: status=AUDITED'
Ok ((D (StockQty $srcWh 'material_id' $matId)) -eq ($bStock - 1)) ('S2 after audit: warehouse stock -1 -> ' + (StockQty $srcWh 'material_id' $matId))
Ok (((D (SqlOne ("SELECT COALESCE(received_quantity,0) FROM outsource_material_order_item WHERE id=" + $ordItemId))) -eq ($bRecv - 1))) ('S2 after audit: order received qty -1 -> ' + (SqlOne ("SELECT COALESCE(received_quantity,0) FROM outsource_material_order_item WHERE id=" + $ordItemId)))
Ok ((D (PaySum $supId)) -eq ($bPay - $unitPrice)) ('S2 after audit: negative payable posted (-' + $unitPrice + ') -> ' + (PaySum $supId))

Ok ((ClickRowBtnContains 0 (ZH 'btn_unaudit')) -match 'OK') 'S2 clicked 反审核 on the same row'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2800
Ok ((D (StockQty $srcWh 'material_id' $matId)) -eq $bStock) 'S2 after un-audit: warehouse stock rolled back'
Ok ((D (SqlOne ("SELECT COALESCE(received_quantity,0) FROM outsource_material_order_item WHERE id=" + $ordItemId))) -eq $bRecv) 'S2 after un-audit: order received qty rolled back'
Ok ((D (PaySum $supId)) -eq $bPay) 'S2 after un-audit: payable rolled back (ledger cancelled)'
Ok ((SqlOne "SELECT status FROM outsource_delivery WHERE id=$retId") -eq 'DRAFT') 'S2 after un-audit: back to DRAFT'

# cleanup: the 退货 draft is a pure test artefact (nothing left to roll back after un-audit)
SqlRaw ("DELETE FROM outsource_delivery_item WHERE delivery_id=$retId") | Out-Null
SqlRaw ("DELETE FROM outsource_delivery WHERE id=$retId") | Out-Null
Ok ((SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE id=$retId") -eq '0') 'S2 cleanup: the test 退货 record was deleted'

# =====================================================================
Step 'S3 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S3 no page/API errors during the flow'

Summary 'ui-e2e-11 material return entry on receipt pages (data-driven)'
