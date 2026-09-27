# UI E2E 14: material return two types + repair-return round trip (2026-09-17). ASCII ONLY.
#   S1 list page: type tabs (REFUND / REPAIR) + "new repair-return" button
#   S2 create a REPAIR draft from the tab entry (url carries returnType=REPAIR)
#   S3 audit -> material leaves source warehouse, **payable untouched** (repair != refund)
#   S4 register repair return -> material comes back into the warehouse
#   S5 un-audit is BLOCKED while repair-return records exist
#   S6 cancel repair return -> stock rolled back; then un-audit + cancel succeed
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
# 2026-09-21: fixtures are now derived at runtime. The old hardcoded set (supplier 捷鹤 / warehouse 捷鹤仓 /
#   warehouse 37 + material 25 / payable supplier 20) no longer exists -- the suppliers in the DB are named
#   测试供货商Ax, so the script died at "S2 pick supplier". Pick a (warehouse, material) pair that REALLY
#   holds GOOD stock of at least 10, plus a supplier (the material-return form does not tie the two).
#   NOTE (same day, user rule "a material return may only go to a 辅料商 or a 供应商, never to a 供货商"):
#   the supplier must NOT carry the product type -- MIN(id) would hand us 测试供货商A1 (type=product), which
#   the backend now refuses.
$fx = SqlRow "SELECT st.warehouse_id, st.material_id, w.warehouse_name, m.material_name, st.quantity, sup.id, sup.name FROM warehouse_stock st JOIN warehouse w ON w.id=st.warehouse_id JOIN outsource_material m ON m.id=st.material_id JOIN supplier sup ON sup.id=(SELECT MIN(s2.id) FROM supplier s2 WHERE NOT EXISTS (SELECT 1 FROM supplier_type_ref r WHERE r.supplier_id=s2.id AND r.type_code='product')) WHERE st.material_id IS NOT NULL AND st.quality_type='GOOD' AND st.quantity >= 10 ORDER BY st.quantity DESC LIMIT 1"
$whId = [int]$fx[0]; $matId = [int]$fx[1]; $whName = "$($fx[2])"; $matName = "$($fx[3])"
$supId = [int]$fx[5]; $supName = "$($fx[6])"
Write-Host ('FIXTURE wh=' + $whId + ' (' + $whName + ') material=' + $matId + ' (' + $matName + ') supplier=' + $supId + ' (' + $supName + ')')
Ok (($whId -gt 0) -and ($matId -gt 0) -and ($supId -gt 0) -and ($whName -ne '') -and ($matName -ne '')) 'fixture derived (warehouse + material with GOOD stock + supplier)'

# =====================================================================
Step 'S1 material-return leaves: tabs + one entry per leaf'
# 2026-09-27 三级菜单：物料退货 拆成两个**独立叶子**（退料 / 维修退货），新增入口随叶子切换。
Open '/outsource/material-return' 3000
Ok ((BodyHas (ZH 'tab_leaf_effective')) -eq 'true') 'S1 退料 leaf has tab 有效单据'
Ok ((BodyHas (ZH 'tab_leaf_void')) -eq 'true') 'S1 退料 leaf has tab 已作废单据'
# 2026-09-24（UI 统一·用户口径）：两个入口文案都压成「新增」⇒ 不能靠文案区分叶子，
#   改为断言「当前叶子上恰好一个『新增』按钮」（两入口互斥 ⇒ 退料叶子看不到维修入口）
#   注：本脚本的 lib 没有 ReadJson，且中文一律走 B64（ASCII ONLY）⇒ 用 EvalJs + 'CNT=' 计数。
$bNew = B64 (ZH 'btn_new_refund')
$cntJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$bNew');const vis=e=>e.getClientRects().length>0;return 'CNT='+[...document.querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim()===t).length})()"
$n1 = EvalJs $cntJs
Ok ($n1 -match 'CNT=1') ('S1 exactly one "new" button on the 退料 leaf (' + $n1 + ')')
Open '/outsource/material-return/repair' 2600
Ok ((BodyHas (ZH 'tab_leaf_pending')) -eq 'true') 'S1 维修退货 leaf has tab 待返回'
Ok ((BodyHas (ZH 'tab_leaf_returned')) -eq 'true') 'S1 维修退货 leaf has tab 已返回完'
$n2 = EvalJs $cntJs
Ok ($n2 -match 'CNT=1') ('S1 exactly one "new" button on the 维修退货 leaf (' + $n2 + ')')
Ok ((Errs) -eq '[]') 'S1 no errors after visiting both leaves'

# =====================================================================
Step 'S2 create REPAIR draft'
$bStock = D (StockQty $whId 'material_id' $matId 'GOOD')
$bpay = PaySum $supId
Write-Host ('BASE wh' + $whId + '.m' + $matId + '=' + $bStock + ' payable' + $supId + '=' + $bpay)
Ok ((ClickBtn 'btn_new_mr_repair') -match 'OK') 'S2 click "new repair-return"'
Start-Sleep -Milliseconds 3000
Ok ((CurUrl) -match 'returnType=REPAIR') ('S2 url carries returnType=REPAIR url=' + (CurUrl))
Ok ((BodyHas (ZH 'lbl_mr_repair_supplier')) -eq 'true') 'S2 supplier label switched to repair mode'
Ok ((SelectLabelText 'lbl_mr_repair_supplier' $supName) -match 'OK') ('S2 pick supplier=' + $supName)
Start-Sleep -Milliseconds 1400
Ok ((SelectLabelText 'lbl_src_wh_out' $whName) -match 'OK') ('S2 pick source warehouse=' + $whName)
Start-Sleep -Milliseconds 2200
Ok ((SetQtyByMaterial $matName '3') -match 'OK') ('S2 qty=3 on material ' + $matName)
Ok ((ClickBtn 'btn_save_draft') -match 'OK') 'S2 save draft'
Start-Sleep -Milliseconds 3400
$rid = [int](MaxId 'outsource_material_return')
Ok ($rid -gt 0) ('S2 created material return id=' + $rid)
Ok ((SqlOne "SELECT return_type FROM outsource_material_return WHERE id=$rid") -eq 'REPAIR') 'S2 type=REPAIR persisted'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'DRAFT') 'S2 status=DRAFT'

# =====================================================================
Step 'S3 audit: material out, payable untouched'
Open ("/outsource/material-return/detail/$rid") 2800
Ok ((BodyHas (ZH 'txt_repair_records')) -eq 'true') 'S3 repair-return card shown for REPAIR'
# 注意：卡片空态提示文案里也含"登记维修返回"字样，故必须判断**按钮元素**是否存在，不能用 BodyHas
$rrKey = B64 (ZH 'btn_repair_return')
$hasRR = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$rrKey');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()"
Ok ($hasRR -eq 'false') 'S3 repair-return button hidden before audit'
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S3 audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Write-Host ('S3 after audit wh' + $whId + '.m' + $matId + '=' + (StockQty $whId 'material_id' $matId 'GOOD') + ' payable' + $supId + '=' + (PaySum $supId))
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq ($bStock - 3)) 'S3 source warehouse -3 (sent out for repair)'
Ok ((D (PaySum $supId)) -eq (D $bpay)) 'S3 payable UNCHANGED (repair return does not offset payable)'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'AUDITED') 'S3 status=AUDITED'
$hasRR2 = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$rrKey');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()"
Ok ($hasRR2 -eq 'true') 'S3 repair-return button appears after audit'

# =====================================================================
Step 'S4 register repair return: material comes back'
Ok ((ClickBtn 'btn_repair_return') -match 'OK') 'S4 open register dialog'
Start-Sleep -Milliseconds 1800
Ok ((SetRowInput 0 0 '3') -match 'OK') 'S4 repair qty=3'
Ok ((ClickDialogBtn 'btn_repair_confirm') -match 'OK') 'S4 confirm'
Start-Sleep -Milliseconds 3400
Write-Host ('S4 after receive wh' + $whId + '.m' + $matId + '=' + (StockQty $whId 'material_id' $matId 'GOOD'))
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq $bStock) 'S4 material back into source warehouse'
$rc = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE return_order_id=$rid")
Ok ($rc -eq 1) 'S4 repair-return record persisted'

# =====================================================================
Step 'S5 un-audit blocked while repair-return exists'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S5 click un-audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2800
Write-Host ('S5 msg=' + (Txt '.el-message'))
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'AUDITED') 'S5 still AUDITED (blocked by repair records)'

# =====================================================================
Step 'S6 cancel repair return, then un-audit + cancel'
$rcId = [int](SqlOne "SELECT id FROM outsource_material_return_repair WHERE return_order_id=$rid LIMIT 1")
Ok ($rcId -gt 0) ('S6 repair record id=' + $rcId)
Open ("/outsource/material-return/detail/$rid") 2800
Ok ((ClickRowBtn 0 'btn_revoke') -match 'OK') 'S6 click 撤销 on repair record'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq ($bStock - 3)) 'S6 stock rolled back to sent-out state'
Ok ([int](SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE return_order_id=$rid") -eq 0) 'S6 repair record removed'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S6 un-audit now allowed'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq $bStock) 'S6 source warehouse fully restored'
Ok ((D (PaySum $supId)) -eq (D $bpay)) 'S6 payable still unchanged'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S6 cancel doc'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2200
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'CANCELLED') 'S6 doc cancelled'

# =====================================================================
Step 'S7 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S7 no page/API errors during the flow'

Summary 'ui-e2e-14 material return two types + repair-return round trip'
