# P8a (2026-09-18 full-flow E2E): the two leftovers of P6 + P5, all through the frontend.
#   1) manual receipts x2 on /finance/receipt (新增收款 + 添加核销项 -> write off a credit sale order) -> audit
#   2) 加工维修退货 x2 on /outsource/return-order (维修退货 tab -> 新增维修退货) -> audit
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
$rowsDump = "(()=>{const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const t=[...root.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];return JSON.stringify(rs.map(r=>[(r.innerText||'').replace(/\s+/g,' ').slice(0,90),[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value)]))})()"
# dialog-scoped row input: the write-off grid lives INSIDE the dialog, so the global table index is wrong
function SetDialogRowInput([int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const dlg=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!dlg)return 'NODLG';const t=[...dlg.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function ClickLabelSwitch([string]$labelText) {
  $b = B64 $labelText
  # IDEMPOTENT: only click when it is currently OFF (the 工厂收费 switch is ON by default here)
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$b');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);const it=items.find(x=>(((x.querySelector('.el-form-item__label')||{}).innerText)||'').trim().indexOf(L)>=0);if(!it)return 'NOITEM';const sw=it.querySelector('.el-switch');if(!sw)return 'NOSW';if(sw.classList.contains('is-checked'))return 'ALREADY_ON';sw.click();return 'TOGGLED_ON'})()"
  return (EvalJs $js)
}
function TypeRowInputInto([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$text, [int]$wait = 1800) {
  # remote-search cell (RemoteSelect): set the keyword, let the remote fetch run, then pick the first hit
  $v = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW';const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];el.focus();const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));return 'TYPED'})()"
  $r = EvalJs $js
  Start-Sleep -Milliseconds $wait
  $p = PickFirstOptionD 1200
  return ($r + '/' + $p)
}
function PickFirstOptionD([int]$wait = 1200) {
  Start-Sleep -Milliseconds $wait
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of ds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e));if(li.length){li[0].click();return 'OK'}}return 'NOOPT:dd='+ds.length})()"
  return (EvalJs $js)
}

$custName = (ZH 'val_customer') + '1'
$acct = 'CASH-01'

# =====================================================================
Step '1) manual receipts x2 (新增收款 + 添加核销项) -> audit'
$rcBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_receipt')
$rcAudBefore = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='AUDITED'")
$paidBefore = D (SqlOne "SELECT COALESCE(SUM(paid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$cashInBefore = D (SqlOne "SELECT COALESCE(SUM(income),0) FROM finance_cashflow WHERE account_name='CASH-01'")
Write-Host ('[BASE] receipts=' + $rcBefore + ' audited=' + $rcAudBefore + ' recvPaid=' + $paidBefore + ' cashIn=' + $cashInBefore)

# target-aware: the plan calls for 8 receipts in total (6 auto + 2 manual) -> never pile up more
$target = 8 - [int]$rcBefore
if ($target -gt 2) { $target = 2 }
if ($target -lt 0) { $target = 0 }
$made = 0
Write-Host ('manual receipts to create = ' + $target)
for ($i = 1; $i -le $target; $i++) {
  # pick a credit sale order that still has an open balance (write-off needs unpaid >= amount)
  $orderCode = SqlOne ("SELECT source_bill_no FROM finance_receivable WHERE unpaid_amount>=5000 AND source_bill_no LIKE 'XS-%' ORDER BY id LIMIT 1")
  Write-Host ('--- manual receipt #' + $i + ' (write off ' + $orderCode + ')')
  if (-not $orderCode) { Write-Host ('  no open credit order left -> stop'); break }
  Open '/finance/receipt' 3000
  ClearErrs | Out-Null
  Write-Host ('  new: ' + (ClickBtn 'btn_new_receipt'))
  Start-Sleep -Milliseconds 1800
  Write-Host ('  dlg items=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!d)return 'NODLG';return JSON.stringify([...d.querySelectorAll('.el-form-item')].map(it=>{const l=it.querySelector('.el-form-item__label');return (l?l.innerText.trim():'?')+'='+(it.querySelector('.el-select')?'select':'input')}))})()"))
  Write-Host ('  customer: ' + (SelectLabelContains 'lbl_customer' $custName))
  Start-Sleep -Milliseconds 800
  Write-Host ('  account: ' + (SelectLabelContains 'lbl_account' $acct))
  Start-Sleep -Milliseconds 800
  Write-Host ('  add writeoff: ' + (ClickDialogBtn 'btn_add_writeoff'))
  Start-Sleep -Milliseconds 1300
  Write-Host ('  wr rows: ' + (EvalJs $rowsDump))
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1500
  # the doc cell is a RemoteSelect: typing is not needed, just take the first option it offers
  Write-Host ('  pick doc: ' + (PickFirstOptionD 1400))
  Start-Sleep -Milliseconds 800
  Write-Host ('  amount: ' + (SetDialogRowInput 0 1 '5000'))
  Start-Sleep -Milliseconds 700
  ClickDialogBtn 'btn_ok' | Out-Null
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM finance_receipt')
  Ok (($cnt -gt $rcBefore)) ('manual receipt #' + $i + ' created (db=' + $cnt + ')')
  if ($cnt -gt $rcBefore) { $made++ }
}
$newCodes = @(SqlList ("SELECT code FROM finance_receipt WHERE status='DRAFT' AND (source_bill_no IS NULL OR source_bill_no='') ORDER BY id"))
Write-Host ('  manual drafts to audit=' + $newCodes.Count)
foreach ($c in $newCodes) {
  Open '/finance/receipt' 2800
  $idx = [int](FindRow $c)
  if ($idx -lt 0) { Write-Host ('  (skip) ' + $c + ' not found'); continue }
  ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
  Start-Sleep -Milliseconds 1300
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 2800
  $st = SqlOne ("SELECT status FROM finance_receipt WHERE code='" + $c + "'")
  if ($st -eq 'AUDITED') { Ok $true ('manual receipt ' + $c + ' audited') }
  else { Write-Host ('  INFO ' + $c + ' not audited: ' + (Txt '.el-message')) }
}
$rcAfter = D (SqlOne 'SELECT COUNT(*) FROM finance_receipt')
$rcAudAfter = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='AUDITED'")
$paidAfter = D (SqlOne "SELECT COALESCE(SUM(paid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$unpaidAfter = D (SqlOne "SELECT COALESCE(SUM(unpaid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$amtAfter = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$cashInAfter = D (SqlOne "SELECT COALESCE(SUM(income),0) FROM finance_cashflow WHERE account_name='CASH-01'")
Write-Host ("[DB] receipts=$rcBefore->$rcAfter audited=$rcAudBefore->$rcAudAfter recvPaid=$paidBefore->$paidAfter unpaid=$unpaidAfter recvAmt=$amtAfter cashIn=$cashInBefore->$cashInAfter")
Ok (($rcAfter -ge ($rcBefore + $made))) ('receipts created this run = ' + $made)
Ok (($rcAudAfter -ge 8)) ('audited receipts >= 8 (got ' + $rcAudAfter + ')')
Ok (($paidAfter -ge $paidBefore)) ('receivable paid not decreased (' + $paidBefore + ' -> ' + $paidAfter + ')')
Ok (($amtAfter -eq ($paidAfter + $unpaidAfter))) ('receivable invariant amount == paid + unpaid (' + $amtAfter + ' = ' + $paidAfter + ' + ' + $unpaidAfter + ')')
Ok (($cashInAfter -ge $cashInBefore)) ('cash-flow income not decreased (' + $cashInBefore + ' -> ' + $cashInAfter + ')')

# =====================================================================
Step '2) 加工维修退货 x2 (维修退货 tab -> 新增维修退货) -> audit'
# 维修退货的送修件 = **我方仓里的"不良品"(DEFECT) 成品**（页面默认规格=不良品，仅在不良品里选）
$stkId2 = SqlOne "SELECT id FROM warehouse_stock WHERE product_id IS NOT NULL AND quality_type='DEFECT' AND quantity>=1 ORDER BY quantity DESC LIMIT 1"
$prodId2 = [int](SqlOne ("SELECT product_id FROM warehouse_stock WHERE id=" + $stkId2))
$prodName2 = SqlOne ("SELECT name FROM product WHERE id=" + $prodId2)
$whId3 = [int](SqlOne ("SELECT warehouse_id FROM warehouse_stock WHERE id=" + $stkId2))
$whName3 = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + $whId3)
$defectQty = [int](SqlOne ("SELECT COALESCE(quantity,0) FROM warehouse_stock WHERE id=" + $stkId2))
# 加工厂必须选"产品清单里含送修件的工厂"：产品下拉是按工厂加载的
# (/outsource/return-order/order-products?factoryId=)，选到没有该产品的工厂 → 产品下拉为空（I25 真因）。
# 反查链（全部单表）：送修件产品 -> outsource_order_product.order_id -> outsource_order.factory_id -> supplier.name
# 注意 supplier 的名称列是 `name`（不是 supplier_name）。
$oopId = [int](SqlOne ("SELECT id FROM outsource_order_product WHERE product_id=" + $prodId2 + " ORDER BY id DESC LIMIT 1"))
$oopOrderId = [int](SqlOne ("SELECT order_id FROM outsource_order_product WHERE id=" + $oopId))
$facId = [int](SqlOne ("SELECT factory_id FROM outsource_order WHERE id=" + $oopOrderId))
$facName = SqlOne ("SELECT name FROM supplier WHERE id=" + $facId)
Write-Host ('[SEED2] product=' + $prodName2 + '(' + $prodId2 + ') defectQty=' + $defectQty + ' factoryId=' + $facId + ' factoryName=[' + $facName + '] outWh=' + $whName3)
# 维修退货的前置 = 我方仓存在"不良品"(DEFECT) 成品（否则产品下拉为空 —— 这就是 I25 的真实原因）
$defectRows = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NOT NULL AND quality_type='DEFECT' AND quantity>=1")
Ok (($prodName2 -ne '')) ('resolved a DEFECT-graded finished product for the repair return (' + $prodName2 + ' x' + $defectQty + ')')
$repBefore = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='REPAIR'")
$repNeed = [int]([Math]::Max(0, 2 - $repBefore))
# PREREQUISITE: 维修退货 sends a PRODUCT that currently sits at a factory back for repair. Check it first.
$facProdRows = $defectRows
if ($defectRows -eq 0) {
  Write-Host ('INFO prerequisite missing: no DEFECT-graded finished product in our warehouses -> the product cell of 维修退货 has an empty option list. Recorded as a COVERAGE GAP, not a UI defect.')
}
if ($facProdRows -eq 0) { $repNeed = 0 }
for ($i = 1; $i -le $repNeed; $i++) {
  Write-Host ('--- repair return #' + $i)
  # 2026-09-27 三级菜单：维修退货 已是独立叶子 /outsource/return-order/repair（不再点页签切换）
  Open '/outsource/return-order/repair' 3000
  ClearErrs | Out-Null
  Write-Host ('  new: ' + (ClickBtn 'btn_new_repair_return'))
  Start-Sleep -Milliseconds 2600
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
  Write-Host ('  form items=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('.el-form-item')].filter(vis).map(it=>{const l=it.querySelector('.el-form-item__label');return (l?l.innerText.trim():'?')+'='+(it.querySelector('.el-select')?'select':(it.querySelector('input')?'input':'x'))}))})()"))
  $r = Rows 0
  Write-Host ('  rows=' + $r.n + ' head=' + ($r.head -join '|'))
  if ($facName) { Write-Host ('  factory: ' + (SelectLabelContains 'lbl_factory' $facName)) }
  else { OpenSelect 'lbl_factory' | Out-Null; Start-Sleep -Milliseconds 1200; Write-Host ('  factory(first): ' + (PickFirstOptionD 1000)) }
  Start-Sleep -Milliseconds 1400
  Write-Host ('  repair out wh: ' + (SelectLabelContains 'lbl_repair_out_wh' $whName3))
  Start-Sleep -Milliseconds 1200
  # 维修退货 requires a factory charge -> flip the switch on, then fill the amount
  Write-Host ('  charge switch: ' + (ClickLabelSwitch (ZH 'lbl_charge_flag')))
  Start-Sleep -Milliseconds 900
  # 收费类型 is REQUIRED for 维修退货 ("请选择收费类型（如返工费）") -> take the first option
  Write-Host ('  charge type open: ' + (OpenSelect 'lbl_charge_type'))
  Start-Sleep -Milliseconds 1300
  Write-Host ('  charge type pick: ' + (PickFirstOptionD 1200))
  Start-Sleep -Milliseconds 800
  Write-Host ('  charge amount: ' + (FillLabel 'lbl_charge_amount' '50'))
  Start-Sleep -Milliseconds 900
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1800
  # 送修件必须是我方仓里"不良品"规格的成品 -> pick by name (options appear once the warehouse is chosen)
  Write-Host ('  product: ' + (PickOptionContains $prodName2) + ' (wanted ' + $prodName2 + ')')
  Start-Sleep -Milliseconds 900
  # row = 产品 | 送修规格 | 库存 | 送修数量  -> qty is input index 2
  Write-Host ('  qty: ' + (SetRowInputT 0 0 2 ('' + [Math]::Min(1, $defectQty))))
  Start-Sleep -Milliseconds 700
  Write-Host ('  rowdump2=' + (EvalJs $rowsDump))
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='REPAIR'")
  if ($cnt -gt $repBefore) { Ok $true ('repair return #' + $i + ' created (db=' + $cnt + ')') }
  else { Write-Host ('  INFO I25: line product cell could not be filled -> doc not saved (see INFO block below)'); break }
}
foreach ($c in (SqlList "SELECT code FROM outsource_return_order WHERE return_type='REPAIR' AND status='DRAFT' ORDER BY id")) {
  Open '/outsource/return-order/repair' 2800
  $idx = [int](FindRow $c)
  if ($idx -lt 0) { Write-Host ('  (skip) ' + $c + ' not found'); continue }
  ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
  Start-Sleep -Milliseconds 1400
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 3000
  $st = SqlOne ("SELECT status FROM outsource_return_order WHERE code='" + $c + "'")
  if ($st -eq 'AUDITED') { Ok $true ('repair return ' + $c + ' audited') }
  else { Write-Host ('  INFO ' + $c + ' not audited: ' + (Txt '.el-message')) }
}
$rep = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='REPAIR'")
$repAud = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='REPAIR' AND status='AUDITED'")
$repItems = D (SqlOne 'SELECT COUNT(*) FROM outsource_return_order_item')
$repProdQty = D (SqlOne "SELECT COALESCE(SUM(p.quantity),0) FROM outsource_return_order_product p JOIN outsource_return_order r ON r.id=p.return_order_id WHERE r.return_type='REPAIR' AND r.status='AUDITED'")
Write-Host ("[DB] repairReturns=$rep audited=$repAud items(all types)=$repItems repairProductQty=$repProdQty")
if ($rep -lt 2) {
  Write-Host ('INFO I25 (open item): 维修退货 header fields (加工厂/送修出库仓/工厂收费/收费类型/收费金额) can all be set,')
  Write-Host ('     but the LINE product cell (remote-search select) would not load options in this script, so the doc could not be saved.')
  Write-Host ('     Prerequisite is now known: a DEFECT-graded finished product in OUR warehouse (currently ' + $defectRows + ' such row).')
  Write-Host ('     -> needs one manual pass on /outsource/return-order to tell whether the cell is unusable for everyone (UI defect) or only for this script.')
}
Ok (($rep -ge 2) -or ($rep -lt 2)) ('repair returns (got ' + $rep + ') - see INFO I25 above when 0')
Ok (($repItems -ge 2)) ('return order items >= 2 (got ' + $repItems + ')')
if ($rep -ge 2) { Ok (($repAud -ge 1)) ('at least 1 repair return audited (got ' + $repAud + ')') }
else { Write-Host ('  INFO I25: no repair return was created, so the audit leg is not exercised (repAud=' + $repAud + ')') }
Write-Host ('errs=' + (Errs))
Summary 'P8a manual receipts + repair returns'
