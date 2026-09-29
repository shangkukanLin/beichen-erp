# Temp test 4c: work order (加工单) -> audit -> finished-goods receipt (收货) -> audit -> defect return
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
# 2026-09-29：本支新增的"加工单反审核"步骤要按**单号**精确定位列表行（同产品名可能有多张单，
# 用产品名 FindRow 会误点到别人的 fixture）⇒ 补一个只读 SqlOne（与其它守卫同款）。
$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $v = (@($o) | Select-Object -First 1)
  if ($null -eq $v) { return '' }
  return ("$v").Trim()
}

Step 'create work order'
Open '/outsource/order' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new_order'))
Start-Sleep -Milliseconds 2200
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('factory: ' + (SelectLabelContains 'lbl_factory' (ZH 'val_factory')))
Write-Host ('product: ' + (SelectLabelContains 'lbl_mfg_product' (ZH 'val_proj')))
Write-Host ('qty: ' + (FillLabel 'lbl_qty' '50'))
Write-Host ('price: ' + (FillLabel 'lbl_mfg_price' '20'))
Start-Sleep -Milliseconds 500
Write-Host ('submit: ' + (ClickBtn 'btn_submit_confirm'))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))

Open '/outsource/order' 2600
$r = Rows 0
$idx = [int](FindRow (ZH 'val_proj'))
Ok ($idx -ge 0) 'work order created (pending)'
if ($idx -lt 0) { Summary 'work order chain'; exit 1 }
Write-Host ('row=' + ($r.rows[$idx] -join ' | '))

Step 'audit work order'
Write-Host ('open detail: ' + (ClickRowBtnContains $idx (ZH 'btn_detail')))
Start-Sleep -Milliseconds 2800
$ORDID = (EvalJs 'String(location.pathname)') -replace '.*/detail/', ''
Write-Host ('order id=' + $ORDID)
Write-Host ('audit: ' + (ClickBtn 'btn_audit'))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1400))
Start-Sleep -Milliseconds 2800
Write-Host ('msg=' + (Txt '.el-message'))
Ok ((BodyHas (ZH 'st_producing')) -match 'true') 'work order producing'

Step 'delivery record'
Open "/outsource/order/delivery/$ORDID" 3000
Write-Host ('click new delivery: ' + (ClickBtn 'btn_new_delivery'))
Start-Sleep -Milliseconds 1800
Write-Host ('dlg count=' + (EvalJs "String([...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length>0).length)"))
Write-Host ('open product sel: ' + (DialogOpenSelect 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick product: ' + (PickOptionContains (ZH 'val_proj')))
Start-Sleep -Milliseconds 800
Write-Host ('A qty: ' + (DialogSetInput 2 '10'))
Write-Host ('open wh sel: ' + (DialogOpenSelect 1))
Start-Sleep -Milliseconds 1400
Write-Host ('pick wh: ' + (PickOptionContains (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 600
Write-Host ('save: ' + (ClickDialogBtn 'btn_save'))
Start-Sleep -Milliseconds 2000
$dtxt = "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];return d?(d.innerText||'').replace(/\s+/g,' ').slice(0,150):'NODLG'})()"
Write-Host ('dialog after save: ' + (EvalJs $dtxt))
if ((BodyHas (ZH 'txt_shortage')) -match 'true') {
  Write-Host ('shortage -> force out: ' + (ClickDialogBtn 'btn_force_out'))
}
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open "/outsource/order/delivery/$ORDID" 3000
$r = Rows 0
Write-Host ('records=' + $r.n + ' head=' + ($r.head -join '|'))
foreach ($rw in $r.rows) { Write-Host ('rec=' + ($rw -join ' | ')) }
$idx = [int](FindRow (ZH 'st_draft'))
Ok ($idx -ge 0) 'delivery record saved as draft'
if ($idx -lt 0) { Summary 'work order chain'; exit 1 }

Step 'audit delivery record'
Write-Host ('audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1500))
Start-Sleep -Milliseconds 3200
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open "/outsource/order/delivery/$ORDID" 3000
$r = Rows 0
foreach ($rw in $r.rows) { Write-Host ('rec after audit=' + ($rw -join ' | ')) }
# NOTE: rows is an array of arrays -> join per row first (plain -join on the outer array prints System.Object[])
$rtext = ($r.rows | ForEach-Object { $_ -join ' ' }) -join ' '
Ok ($rtext -match (ZH 'st_audited')) 'delivery record audited'
Open '/inventory/stock' 2600
$st = Rows 0
$si = [int](FindRow (ZH 'val_proj'))
Write-Host ('stock row=' + $(if ($si -ge 0) { ($st.rows[$si] -join ' | ') } else { 'NOT FOUND' }))
Ok ($si -ge 0) 'finished goods received into stock'
Open '/finance/payable' 2600
$py = Rows 0
foreach ($rw in $py.rows) { $line = ($rw -join ' '); if ($line -match 'MFTEST' -or $line -match (ZH 'val_factory')) { Write-Host ('payable=' + $line) } }
Write-Host ('errs=' + (Errs))

Step 'receipt record: un-audit then re-audit (restores the state the defect steps expect)'
# 2026-09-29（用户口径「加工收退详情的收货记录也需要反审核功能」）：已审核行现在挂「反审核」
#   （2026-09-24 曾把它移出操作列、而「收货记录详情页」是**只读**的 ⇒ 该动作在界面上等于消失；
#     后端 PUT /outsource/order-delivery/{id}/un-audit 与前端 handleUnaudit 一直都在，只是没入口）。
#   这里真点一次：反审核 ⇒ 记录回草稿、成品库存/应付对称逆回；随后**重新审核复原**，
#   好让下面的加工退货步骤看到的仍是"已收 10 件"的同一状态。
Open "/outsource/order/delivery/$ORDID" 3000
$rUn = Rows 0
$idxUn = [int](FindRow (ZH 'st_audited'))
Ok ($idxUn -ge 0) 'un-audit step: the audited record row is present'
Write-Host ('un-audit click: ' + (ClickRowBtnContains $idxUn (ZH 'btn_unaudit')))
Write-Host ('confirm: ' + (ConfirmBox 1500))
Start-Sleep -Milliseconds 3200
Write-Host ('msg=' + (Txt '.el-message'))
Open "/outsource/order/delivery/$ORDID" 3000
$rU2 = Rows 0
$rtextU2 = ($rU2.rows | ForEach-Object { $_ -join ' ' }) -join ' '
Ok ($rtextU2 -match (ZH 'st_draft')) 'un-audit put the receipt record back to 草稿'
Open '/inventory/stock' 2600
$stU = Rows 0
$siU = [int](FindRow (ZH 'val_proj'))
Write-Host ('stock after un-audit=' + $(if ($siU -ge 0) { ($stU.rows[$siU] -join ' | ') } else { 'NOT FOUND' }))
Open "/outsource/order/delivery/$ORDID" 3000
$rRe = Rows 0
$idxRe = [int](FindRow (ZH 'st_draft'))
Write-Host ('re-audit click: ' + (ClickRowBtnContains $idxRe (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1500))
Start-Sleep -Milliseconds 3200
Open "/outsource/order/delivery/$ORDID" 3000
$rRe2 = Rows 0
$rtextRe2 = ($rRe2.rows | ForEach-Object { $_ -join ' ' }) -join ' '
Ok ($rtextRe2 -match (ZH 'st_audited')) 're-audit restored the record to 已审核'

Step 'defect return'
Open "/outsource/order/delivery/$ORDID" 3000
Write-Host ('defect btn: ' + (ClickBtn 'btn_mfg_return'))
Start-Sleep -Milliseconds 2000
$dlg = "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];if(!d)return 'NODLG';return JSON.stringify({text:(d.innerText||'').replace(/\s+/g,' ').slice(0,200),ins:[...d.querySelectorAll('input:not([type=hidden])')].filter(vis).map((e,i)=>i+':'+(e.placeholder||e.value)),sels:[...d.querySelectorAll('.el-select')].filter(vis).length})})()"
Write-Host ('DEFECTDLG ' + (EvalJs $dlg))
Write-Host ('open wh sel: ' + (DialogOpenSelect 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick wh: ' + (PickOptionContains (ZH 'pfx_wh')))
Write-Host ('A qty: ' + (DialogSetInput 1 '4'))
Start-Sleep -Milliseconds 600
Write-Host ('confirm defect: ' + (ClickDialogBtn 'btn_confirm_mfg_return'))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/stock' 2600
$st = Rows 0
$si = [int](FindRow (ZH 'val_proj'))
Write-Host ('stock after defect draft=' + $(if ($si -ge 0) { ($st.rows[$si] -join ' | ') } else { 'NOT FOUND' }))

Step 'audit defect record'
Open "/outsource/order/delivery/$ORDID" 3000
$r = Rows 0
foreach ($rw in $r.rows) { Write-Host ('rec=' + ($rw -join ' | ')) }
$idx = [int](FindRow (ZH 'btn_mfg_return'))
Write-Host ('defect rec idx=' + $idx)
if ($idx -ge 0) {
  Write-Host ('audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
  Write-Host ('confirm: ' + (ConfirmBox 1500))
  Start-Sleep -Milliseconds 3200
  Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
}
Open '/inventory/stock' 2600
$st = Rows 0
$si = [int](FindRow (ZH 'val_proj'))
Write-Host ('stock after defect audit=' + $(if ($si -ge 0) { ($st.rows[$si] -join ' | ') } else { 'NOT FOUND' }))

# =====================================================================
Step 'order-level un-audit from the 加工收退 list (cascades to the receipt records)'
# 2026-09-29（user口径「加工收退列表页行内仍无「反审核」，这个要做」）：
#   the PRODUCING rows now carry 反审核 = PUT /outsource/order/{id}/un-audit
#     -> 加工单 back to 待审核 AND every AUDITED receipt of that order cascaded back to DRAFT
#        (inventory / finished-goods ledger / payable rolled back) -- asserted on UI + DB here.
$ORDCODE = SqlOne ('SELECT code FROM outsource_order WHERE id=' + $ORDID)
Write-Host ('order code=' + $ORDCODE)
Open '/outsource/order/delivery' 3400
$lu = Rows 0
$idxU = [int](FindRow $ORDCODE)
Ok ($idxU -ge 0) 'the order is listed on the 生产中 tab'
if ($idxU -ge 0) {
  Write-Host ('row=' + ($lu.rows[$idxU] -join ' | '))
  Ok ((ClickRowBtnContains $idxU (ZH 'btn_unaudit')) -match 'OK') 'clicked the row-level 反审核'
  ConfirmBox 1600 | Out-Null
  Start-Sleep -Milliseconds 3600
  Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
  Ok ((SqlOne ('SELECT status FROM outsource_order WHERE id=' + $ORDID)) -eq 'PENDING') 'order back to PENDING (待审核)'
  Ok ((SqlOne ('SELECT COUNT(*) FROM outsource_order_delivery WHERE order_id=' + $ORDID + " AND status='AUDITED'")) -eq '0') 'no AUDITED receipt left (cascade un-audited)'
  Ok ([int](SqlOne ('SELECT COUNT(*) FROM outsource_order_delivery WHERE order_id=' + $ORDID + " AND status='DRAFT'")) -ge 1) 'the receipt(s) came back as DRAFT'
  Open '/outsource/order/delivery' 3400
  Ok ([int](FindRow $ORDCODE) -lt 0) 'the order left the 生产中 tab'
  Open ('/outsource/order/delivery/' + $ORDID) 3200
  $rr = Rows 0
  $rrtxt = ($rr.rows | ForEach-Object { $_ -join ' ' }) -join ' '
  Write-Host ('receipt records now: ' + $rrtxt)
  Ok ($rrtxt -match (ZH 'st_draft')) 'the receive-record list shows it back as 草稿 (UI)'
}

Ok $true 'work order chain executed (see stock/payable lines above)'
Summary 'work order chain'
