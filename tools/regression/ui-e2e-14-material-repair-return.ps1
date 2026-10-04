# UI E2E 14: material return two types + repair-return round trip (2026-09-17). ASCII ONLY.
#   S1 list page: type tabs (REFUND / REPAIR) + "new repair-return" button
#   S2 create a REPAIR draft from the tab entry (url carries returnType=REPAIR)
#   S3 audit -> material leaves source warehouse, **repair fee -> payable to the supplier** (P3 2026-09-28)
#   S4 register repair return (DRAFT) then audit it -> material comes back only at AUDIT
#   S5 un-audit the doc is BLOCKED while AUDITED repair-return records exist
#   S6 un-audit the repair record (stock rolled back) -> delete the draft; then un-audit + cancel succeed
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
# P3 (2026-09-28): the repair-return row's 2nd input is the price column = the **repair fee** per unit.
function SetPriceByMaterial([string]$matName, [string]$val) {
  $b = B64 $matName; $v = B64 $val
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$b').replace(/\s+/g,'');const V=T('$v');const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';for(const tr of [...t.querySelectorAll('.el-table__body tbody tr')]){if((tr.innerText||'').replace(/\s+/g,'').indexOf(N)<0)continue;const inps=[...tr.querySelectorAll('input')];if(inps.length<2)return 'NOINPUT';const inp=inps[1];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(inp,V);inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));inp.blur();return 'OK'}return 'NOROW'})()"
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
# 2026-10-03：夹具派不出来（本库没有 GOOD 物料库存 >= 10 的行）时，S2~S6 的"建单→审核→登记维修返回→
#   反审核→作废"整条链路**无法验证**（它会一路红 50 条，全是级联假红）。与 pie 守卫的 Skip 同范式：
#   显式 SKIP 退出并说明原因，别让"缺数据"伪装成"功能坏了"。
#   判断放在 Ok 之前 —— 否则 SKIP 的运行里还会先留一条 FAIL，看日志的人会以为是真问题。
if (($whId -le 0) -or ($matId -le 0) -or ($supId -le 0)) {
  Write-Output 'SKIP 未派生出夹具（需要一条 GOOD 品质、数量 >= 10 的物料库存 + 一个非供货商供应商）⇒ 跳过 S1~S6（本库当前没有这样的库存行）'
  Write-Output 'RESULT SKIP ui-e2e-14 material return two types + repair-return round trip (fixture unavailable in this DB)'
  exit 0
}
Ok (($whId -gt 0) -and ($matId -gt 0) -and ($supId -gt 0) -and ($whName -ne '') -and ($matName -ne '')) 'fixture derived (warehouse + material with GOOD stock + supplier)'

# =====================================================================
Step 'S1 物料售后 leaves: 工厂维修 / 退货退款 (2026-09-29 口径)'
# 2026-09-29 用户口径「目录『物料退货』改名『物料售后』；关联退料不需要了（以后在物料收退做）；
#   下面的子菜单改为 工厂维修 和 退货退款」⇒ 与加工侧 419「加工售后」同范式：
#   · 旧「关联退料」叶子（`/outsource/material-return`）**下线**：菜单 visible=0 保号 + 前端**重定向**到退货退款；
#   · 两个叶子**按类型**分（不再是 2026-09-28 的"关联/无单 + 类型页签"两级模型）：
#       工厂维修 REPAIR `/repair`    页签 = **待返回 | 已返回完 | 已作废**（含「送修/已返回」列）
#       退货退款 REFUND `/unlinked`  页签 = 草稿和已审核 | 已作废（无「关联物料订单」列、无「类型」列）
# ① 旧根地址 = 重定向 ⇒ 落在「退货退款」叶子
Open '/outsource/material-return' 3000
Ok ((CurUrl) -match '/outsource/material-return/unlinked') ('S1 retired 关联退料 leaf redirects to 退货退款 url=' + (CurUrl))
$lkTabsJs = "(()=>{const vis=e=>e.getClientRects().length>0;const it=[...document.querySelectorAll('.el-tabs__item')].filter(vis);return String(it.length)+'||'+it.map(e=>(e.innerText||'').replace(/\s+/g,' ').trim()).join(' | ')})()"
$mrTabsJs = $lkTabsJs
$lkTabs = EvalJs $lkTabsJs
Write-Host ('S1 退货退款 tabs => ' + $lkTabs)
Ok ($lkTabs -match '^2\|\|') ('S1 退货退款 leaf has exactly 2 tabs (status only) (' + $lkTabs + ')')
Ok ($lkTabs -match [regex]::Escape((ZH 'tab_unlinked_active'))) 'S1 tab 草稿和已审核 present'
Ok ($lkTabs -match [regex]::Escape((ZH 'tab_leaf_void'))) 'S1 tab 已作废 present'
Ok ((-not ($lkTabs -match [regex]::Escape((ZH 'opt_type_refund')))) -and (-not ($lkTabs -match [regex]::Escape((ZH 'opt_type_repair'))))) 'S1 退货退款 leaf has NO type tab any more (the type is the leaf)'
Ok (-not ($lkTabs -match [regex]::Escape((ZH 'tab_leaf_effective')))) 'S1 the legacy 有效单据 tab is gone'
$hu = ((Rows 0).head -join '|')
Ok (-not ($hu -match [regex]::Escape((ZH 'lbl_mr_order')))) 'S1 退货退款 leaf has NO linked-order column (关联退料已下线)'
# 2026-09-24（UI 统一·用户口径）：入口文案都压成「新增」⇒ 不能靠文案区分叶子，
#   改为断言「当前叶子上恰好一个『新增』按钮」（一叶一入口）。
#   注：本脚本的 lib 没有 ReadJson，且中文一律走 B64（ASCII ONLY）⇒ 用 EvalJs + 'CNT=' 计数。
$bNew = B64 (ZH 'btn_new_refund')
$cntJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$bNew');const vis=e=>e.getClientRects().length>0;return 'CNT='+[...document.querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim()===t).length})()"
$n1 = EvalJs $cntJs
Ok ($n1 -match 'CNT=1') ('S1 exactly one "new" button on the 退货退款 leaf (' + $n1 + ')')
Ok ((ClickBtn 'btn_new_refund') -match 'OK') 'S1 click "new" on the 退货退款 leaf'
Start-Sleep -Milliseconds 2800
Ok ((CurUrl) -match 'returnType=REFUND') ('S1 refund entry url carries returnType=REFUND url=' + (CurUrl))
$tabU = EvalJs "(()=>{const a=document.querySelector('.tab-item.active .tab-label');return a?(a.innerText||'').trim():'NONE'})()"
Ok ($tabU -eq (ZH 'title_add_unlinked_material')) ('S1 refund entry tab title = 新增退货退款 (' + $tabU + ')')
Ok ((BodyHas (ZH 'lbl_mr_order')) -eq 'false') 'S1 refund entry hides the "link material order" field (关联退料已下线)'
Ok ((ClickBtn 'btn_back') -match 'OK') 'S1 click BACK on the refund-entry add page'
Start-Sleep -Milliseconds 2600
Ok ((CurUrl) -match '/outsource/material-return/unlinked') ('S1 refund entry BACK returns to the 退货退款 leaf url=' + (CurUrl))
# ② 工厂维修叶子：3 个页签（待返回 | 已返回完 | 已作废，2026-09-29 用户口径「需要补返回进度页签」）
#   + 「送修/已返回」列；「新增」把类型（REPAIR）带进新增页。
Open '/outsource/material-return/repair' 3000
$rlTabs = EvalJs $mrTabsJs
Write-Host ('S1 工厂维修 tabs => ' + $rlTabs)
Ok ($rlTabs -match '^3\|\|') ('S1 工厂维修 leaf has exactly 3 tabs (待返回 | 已返回完 | 已作废) (' + $rlTabs + ')')
Ok ($rlTabs -match [regex]::Escape((ZH 'tab_leaf_pending'))) 'S1 tab 待返回 present'
Ok ($rlTabs -match [regex]::Escape((ZH 'tab_leaf_returned'))) 'S1 tab 已返回完 present'
Ok ($rlTabs -match [regex]::Escape((ZH 'tab_leaf_void'))) 'S1 tab 已作废 present'
Ok (((Rows 0).head -join '|') -match [regex]::Escape((ZH 'lbl_mr_sent'))) 'S1 工厂维修 leaf shows the 送修/已返回 column'
Ok (-not (((Rows 0).head -join '|') -match [regex]::Escape((ZH 'col_mr_type')))) 'S1 工厂维修 leaf has NO type column (the type is the leaf)'
$n2 = EvalJs $cntJs
Ok ($n2 -match 'CNT=1') ('S1 exactly one "new" button on the 工厂维修 leaf (' + $n2 + ')')
Ok ((ClickBtn 'btn_new_refund') -match 'OK') 'S1 click "new" on the 工厂维修 leaf'
Start-Sleep -Milliseconds 2800
Ok ((CurUrl) -match 'returnType=REPAIR') ('S1 repair entry url carries returnType=REPAIR url=' + (CurUrl))
$tabR = EvalJs "(()=>{const a=document.querySelector('.tab-item.active .tab-label');return a?(a.innerText||'').trim():'NONE'})()"
Ok ($tabR -eq (ZH 'title_add_repair_material')) ('S1 repair entry tab title = 新增工厂维修 (' + $tabR + ')')
Ok ((BodyHas (ZH 'lbl_mr_repair_supplier')) -eq 'true') 'S1 repair entry switches the supplier label to repair mode'
# 2026-09-29 用户口径「物料售后的工厂维修，不需要关联订单」：维修入口**不得**再出现「关联物料订单」字段
Ok ((BodyHas (ZH 'lbl_mr_order')) -eq 'false') 'S1 repair entry hides the link-order field (工厂维修不关联订单)'
Ok ((ClickBtn 'btn_back') -match 'OK') 'S1 click BACK on the repair-entry add page'
Start-Sleep -Milliseconds 2600
Ok ((CurUrl) -match '/outsource/material-return/repair') ('S1 repair entry BACK returns to the 工厂维修 leaf url=' + (CurUrl))
Ok ((Errs) -eq '[]') 'S1 no errors after visiting the leaves'

# =====================================================================
Step 'S2 create REPAIR draft (entry from the 工厂维修 leaf carries returnType=REPAIR)'
$bStock = D (StockQty $whId 'material_id' $matId 'GOOD')
$bpay = PaySum $supId
Write-Host ('BASE wh' + $whId + '.m' + $matId + '=' + $bStock + ' payable' + $supId + '=' + $bpay)
# 2026-09-29（叶子=类型）：工厂维修有**自己的叶子**（`/repair`）⇒ 直接进该叶子点「新增」，
#   类型由入口带进新增页（returnType=REPAIR）；不再需要"切类型页签"那一步。
Open '/outsource/material-return/repair' 2800
Ok ((ClickBtn 'btn_new_refund') -match 'OK') 'S2 click "new" on the 工厂维修 leaf'
Start-Sleep -Milliseconds 2800
Ok ((CurUrl) -match 'returnType=REPAIR') ('S2 url carries returnType=REPAIR url=' + (CurUrl))
Ok ((SelectLabel 'lbl_mr_type' 'opt_type_repair') -match 'OK') 'S2 pick type=工厂维修'
Start-Sleep -Milliseconds 1500
$tabR = EvalJs "(()=>{const a=document.querySelector('.tab-item.active .tab-label');return a?(a.innerText||'').trim():'NONE'})()"
Ok ($tabR -eq (ZH 'title_add_repair_material')) ('S2 tab title follows the picked type = 新增维修返回 (' + $tabR + ')')
Ok ((BodyHas (ZH 'lbl_mr_repair_supplier')) -eq 'true') 'S2 supplier label switched to repair mode'
Ok ((SelectLabelText 'lbl_mr_repair_supplier' $supName) -match 'OK') ('S2 pick supplier=' + $supName)
Start-Sleep -Milliseconds 1400
Ok ((SelectLabelText 'lbl_src_wh_out' $whName) -match 'OK') ('S2 pick source warehouse=' + $whName)
Start-Sleep -Milliseconds 2200
Ok ((SetQtyByMaterial $matName '3') -match 'OK') ('S2 qty=3 on material ' + $matName)
# P3（2026-09-28）：维修返回的单价 = **维修费**（供应商报价）⇒ 填 5/件 ⇒ 审核应生成 15 的对供应商应付
Ok ((SetPriceByMaterial $matName '5') -match 'OK') ('S2 repair fee unit price=5 on material ' + $matName)
Ok ((ClickBtn 'btn_save_draft') -match 'OK') 'S2 save draft'
Start-Sleep -Milliseconds 3400
$rid = [int](MaxId 'outsource_material_return')
Ok ($rid -gt 0) ('S2 created material return id=' + $rid)
Ok ((SqlOne "SELECT return_type FROM outsource_material_return WHERE id=$rid") -eq 'REPAIR') 'S2 type=REPAIR persisted'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'DRAFT') 'S2 status=DRAFT'
# 2026-09-29 用户口径「工厂维修不需要关联订单」：新单不挂物料订单（字段已不渲染 ⇒ 落库必为 NULL）
Ok ((SqlOne "SELECT COALESCE(material_order_id,0) FROM outsource_material_return WHERE id=$rid") -eq '0') 'S2 repair draft carries NO linked material order'
# P3（2026-09-28）：维修返回的单价/金额落库 = 维修费（**不是** FIFO 货值）
Ok ((D (SqlOne "SELECT COALESCE(unit_price,0) FROM outsource_material_return_item WHERE return_order_id=$rid ORDER BY id LIMIT 1")) -eq 5) 'S2 repair fee unit price = 5 persisted (P3)'
Ok ((D (SqlOne "SELECT COALESCE(amount,0) FROM outsource_material_return_item WHERE return_order_id=$rid ORDER BY id LIMIT 1")) -eq 15) 'S2 repair fee amount = 3 x 5 = 15 persisted (P3)'

# =====================================================================
Step 'S3 audit: material out, payable untouched'
Open ("/outsource/material-return/detail/$rid") 2800
Ok ((BodyHas (ZH 'txt_repair_records')) -eq 'true') 'S3 repair-return card shown for REPAIR'
# 2026-09-29 用户口径「工厂维修不需要关联订单」：详情页（草稿形态）同样不显示该字段
Ok ((BodyHas (ZH 'lbl_mr_order')) -eq 'false') 'S3 repair detail (draft) hides the link-order field'
# 注意：卡片空态提示文案里也含"登记维修返回"字样，故必须判断**按钮元素**是否存在，不能用 BodyHas
$rrKey = B64 (ZH 'btn_repair_return')
$hasRR = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$rrKey');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()"
Ok ($hasRR -eq 'false') 'S3 repair-return button hidden before audit'
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S3 audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Write-Host ('S3 after audit wh' + $whId + '.m' + $matId + '=' + (StockQty $whId 'material_id' $matId 'GOOD') + ' payable' + $supId + '=' + (PaySum $supId))
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq ($bStock - 3)) 'S3 source warehouse -3 (sent out for repair)'
# P3（2026-09-28）：维修返回按**维修费**生成对供应商的**正向应付**（供应商向我方收维修费）
Ok ((D (PaySum $supId)) -eq ((D $bpay) + 15)) 'S3 payable +15 = repair fee (3 x 5) generated for the supplier'
Ok ((SqlOne "SELECT COALESCE(source_bill_type,'') FROM finance_payable WHERE source_id=$rid AND source_bill_type='OUTSOURCE_MATERIAL_REPAIR_FEE' AND status='UNSETTLED' ORDER BY id DESC LIMIT 1") -eq 'OUTSOURCE_MATERIAL_REPAIR_FEE') 'S3 repair fee payable carries its own source type (not the generic repair charge)'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'AUDITED') 'S3 status=AUDITED'
$hasRR2 = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$rrKey');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()"
Ok ($hasRR2 -eq 'true') 'S3 repair-return button appears after audit'

# =====================================================================
Step 'S4 register repair return (draft) then audit it'
Ok ((ClickBtn 'btn_repair_return') -match 'OK') 'S4 open register dialog'
Start-Sleep -Milliseconds 1800
Ok ((SetRowInput 0 0 '3') -match 'OK') 'S4 repair qty=3'
Ok ((ClickDialogBtn 'btn_repair_confirm') -match 'OK') 'S4 confirm'
Start-Sleep -Milliseconds 3400
Write-Host ('S4 after register wh' + $whId + '.m' + $matId + '=' + (StockQty $whId 'material_id' $matId 'GOOD'))
# 2026-09-28（用户口径「登记返回需要审核和反审核」）：登记只建**草稿** ⇒ 物料**不**回仓、记录为 DRAFT
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq ($bStock - 3)) 'S4 registration (draft) moved NO stock (material still sent out)'
$rrStatus = SqlOne "SELECT status FROM outsource_material_return_repair WHERE return_order_id=$rid ORDER BY id DESC LIMIT 1"
Ok ($rrStatus -eq 'DRAFT') ('S4 repair-return record saved as DRAFT (' + $rrStatus + ')')
Ok ((ClickRowBtn 0 'btn_audit') -match 'OK') 'S4 audit the repair-return record (legs run here now)'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3400
Write-Host ('S4 after audit wh' + $whId + '.m' + $matId + '=' + (StockQty $whId 'material_id' $matId 'GOOD'))
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq $bStock) 'S4 material back into source warehouse (at AUDIT)'
$rc = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE return_order_id=$rid")
Ok ($rc -eq 1) 'S4 repair-return record persisted'
Ok ((SqlOne "SELECT status FROM outsource_material_return_repair WHERE return_order_id=$rid") -eq 'AUDITED') 'S4 repair-return record is AUDITED after the audit step'

# =====================================================================
Step 'S5 un-audit blocked while repair-return exists'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S5 click un-audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2800
Write-Host ('S5 msg=' + (Txt '.el-message'))
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'AUDITED') 'S5 still AUDITED (blocked by repair records)'

# =====================================================================
Step 'S6 un-audit the repair record, delete the draft, then un-audit + cancel the doc'
$rcId = [int](SqlOne "SELECT id FROM outsource_material_return_repair WHERE return_order_id=$rid LIMIT 1")
Ok ($rcId -gt 0) ('S6 repair record id=' + $rcId)
Open ("/outsource/material-return/detail/$rid") 2800
# 2026-09-28：已审核的返回不能直接删 —— 先「反审核」（对称逆回 + 留痕，记录回草稿），再删草稿
Ok ((ClickRowBtn 0 'btn_unaudit') -match 'OK') 'S6 un-audit the repair record'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq ($bStock - 3)) 'S6 un-audit rolled the stock back to the sent-out state'
Ok ((SqlOne "SELECT status FROM outsource_material_return_repair WHERE id=$rcId") -eq 'DRAFT') 'S6 the record is back to DRAFT (kept: un-audit leaves an audit trail)'
Ok ((ClickRowBtn 0 'btn_delete') -match 'OK') 'S6 delete the draft record'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ([int](SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE return_order_id=$rid") -eq 0) 'S6 draft record removed'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S6 un-audit now allowed'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (StockQty $whId 'material_id' $matId 'GOOD')) -eq $bStock) 'S6 source warehouse fully restored'
Ok ((D (PaySum $supId)) -eq (D $bpay)) 'S6 repair fee payable rolled back by un-audit'
Ok ((SqlOne "SELECT COALESCE(status,'') FROM finance_payable WHERE source_id=$rid AND source_bill_type='OUTSOURCE_MATERIAL_REPAIR_FEE' ORDER BY id DESC LIMIT 1") -eq 'CANCELLED') 'S6 repair fee payable row cancelled (kept for the audit trail)'
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
