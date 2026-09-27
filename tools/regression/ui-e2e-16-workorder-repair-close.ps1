# UI E2E 16: work-order REPAIR return progress + close (2026-09-17). ASCII ONLY.
#   S1 list tabs: REPAIR tab has "sent/returned" column (no linked-order column); DEFECT tab keeps it
#   S2 create a REPAIR order (sent 1) -> audit: DEFECT stock -1, positive repair-charge payable
#   S3 detail: sent/returned shown ; close hidden while unreturned > 0 (click is not offered)
#   S4 register the 1 back (quality A) -> close button appears
#   S5 list row shows "1 / 1"
#   S6 close -> status 已结案, register/reopen button switch, un-audit hidden
#   S7 list: status column shows 已结案 ; progress filter CLOSED vs PENDING_RETURN
#   S8 reopen -> closed_flag back to 0
#   S9 roll back: revoke return, un-audit, cancel doc
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
function MaxId([string]$tbl) { return SqlOne "SELECT COALESCE(MAX(id),0) FROM $tbl" }
function OrderField([int]$id, [string]$col) { return SqlOne "SELECT $col FROM outsource_return_order WHERE id=$id" }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function HasBtn([string]$key) {
  $b = B64 (ZH $key)
  return (EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$b');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()")
}
function SetRowQty([int]$rowIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const ts=[...root.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('.el-input-number input')];if(!ins.length)return 'NOQTY';const el=ins[0];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}

function SqlRow([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return @() }
  return (($ls[1] -split "`t") | ForEach-Object { "$_".Trim() })
}
# business API through the page session (same trick as verify-close-settle-e2e): keeps the login state.
function Api([string]$method, [string]$path, $obj) {
  if ($obj) {
    $json = ConvertTo-Json -InputObject $obj -Compress -Depth 8
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
    $bp = ", body: new TextDecoder().decode(Uint8Array.from(atob('$b64'), c=>c.charCodeAt(0))), headers: { 'Content-Type':'application/json', Authorization: localStorage.getItem('beichen_erp_token') }"
  } else {
    $bp = ", headers: { Authorization: localStorage.getItem('beichen_erp_token') }"
  }
  $js = "fetch('$path',{method:'$method'$bp}).then(r=>r.text()).catch(e=>'ERR:'+e)"
  $raw = (EvalJs "(async()=>{return await $js})()") -replace '^"|"$', ''
  $raw = $raw.Replace('\"', '"')
  $m = [regex]::Match($raw, '"code"\s*:\s*(\d+)')
  $msg = [regex]::Match($raw, '"(?:message|msg)"\s*:\s*"([^"]*)"')
  return [pscustomobject]@{
    Code = $(if ($m.Success) { [int]$m.Groups[1].Value } else { -1 })
    Msg  = $(if ($msg.Success) { $msg.Groups[1].Value } else { '' })
    Raw  = $raw
  }
}

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
# 2026-09-21: fixtures are derived at runtime. The old hardcoded set (warehouse 38 + product 48 + factory
#   payable 23 + val_factory2 "A40" + wh_finished1) no longer matches the DB (no factory named A40 exists),
#   so the flow died at "pick factory" and everything downstream.
#   What this file needs is one CONSISTENT tuple: the product must be one the chosen factory has actually
#   produced (the add page only offers products that appear in that factory's work orders), and a FINISHED
#   warehouse must hold DEFECT stock of it (that stock is what gets sent out for repair).
$fx = SqlRow "SELECT o.factory_id, sup.name, op.product_id, p.name, w.id, w.warehouse_name FROM outsource_order o JOIN outsource_order_product op ON op.order_id=o.id JOIN supplier sup ON sup.id=o.factory_id JOIN product p ON p.id=op.product_id JOIN (SELECT id, warehouse_name FROM warehouse WHERE warehouse_type='FINISHED' ORDER BY id LIMIT 1) w ON 1=1 ORDER BY op.id DESC LIMIT 1"
$facId = [int]$fx[0]; $facName = "$($fx[1])"; $prodId = [int]$fx[2]; $prodName = "$($fx[3])"; $whId = [int]$fx[4]; $whName = "$($fx[5])"
Write-Host ('FIXTURE factory=' + $facId + ' (' + $facName + ') product=' + $prodId + ' (' + $prodName + ') wh=' + $whId + ' (' + $whName + ')')
Ok (($facId -gt 0) -and ($prodId -gt 0) -and ($whId -gt 0) -and ($facName -ne '') -and ($prodName -ne '')) 'fixture derived (factory + a product it has made + a FINISHED warehouse)'

# =====================================================================
# S0: seed the missing precondition. Today's data has DEFECT stock only for a product that NO factory has
#   ever made, so the tuple above has no DEFECT stock at all. Create it through the normal business path
#   (inventory other-io IN, quality DEFECT) and roll the same document back in S11 => the file stays rerunnable.
Step 'S0 seed precondition: DEFECT stock of a factory-made product'
$defBefore = D (StockQty $whId 'product_id' $prodId 'DEFECT')
$io = Api 'POST' '/api/inventory/other' @{ warehouseId = $whId; ioType = 'IN'; ioDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'ui-e2e-16 seed (cancelled at the end)'; items = @(@{ productId = $prodId; quantity = 1; qualityType = 'DEFECT'; remark = 'e2e seed' }) }
Ok ($io.Code -eq 200) ('S0 create other-io IN -> ' + $io.Code + ' ' + $io.Msg)
$ioId = [int](SqlOne 'SELECT COALESCE(MAX(id),0) FROM inventory_other_io')
Ok ($ioId -gt 0) ('S0 other-io id=' + $ioId)
$ia = Api 'PUT' ("/api/inventory/other/$ioId/audit") $null
Ok ($ia.Code -eq 200) ('S0 audit other-io -> ' + $ia.Code + ' ' + $ia.Msg)
Write-Host ('S0 defect stock wh' + $whId + '.p' + $prodId + '=' + (StockQty $whId 'product_id' $prodId 'DEFECT') + ' (was ' + $defBefore + ')')
Ok ((D (StockQty $whId 'product_id' $prodId 'DEFECT')) -eq ($defBefore + 1)) 'S0 seeded DEFECT stock +1'

# =====================================================================
# 2026-09-27 三级菜单：维修退货 / 关联退货 已拆成**独立叶子**（不再是同页两个页签）
#   ⇒ 断言改为分别直达两个叶子路由；页签现在是状态维度（待返回 | 已返回完 | 已作废），且标签带数量角标。
Step 'S1 leaf pages: column sets differ by leaf'
Open '/outsource/return-order/repair' 3000
$h1 = ((Rows 0).head -join '|')
Write-Host ('S1 repair head=' + $h1)
Ok ($h1 -match [regex]::Escape((ZH 'txt_mr_sent_returned'))) 'S1 REPAIR leaf has "sent/returned" column'
Ok (-not ($h1 -match [regex]::Escape((ZH 'txt_ro_col_order')))) 'S1 REPAIR leaf has NO linked-order column'
Ok ((BodyHas (ZH 'tab_leaf_pending')) -eq 'true') 'S1 REPAIR leaf has tab 待返回'
Ok ((BodyHas (ZH 'tab_leaf_returned')) -eq 'true') 'S1 REPAIR leaf has tab 已返回完'
Open '/outsource/return-order' 2600
$h2 = ((Rows 0).head -join '|')
Write-Host ('S1 linked head=' + $h2)
Ok ($h2 -match [regex]::Escape((ZH 'txt_ro_col_order'))) 'S1 linked leaf keeps linked-order column'
Ok (-not ($h2 -match [regex]::Escape((ZH 'txt_mr_sent_returned')))) 'S1 linked leaf has no sent/returned column'

# =====================================================================
Step 'S2 create REPAIR order (sent 1) and audit'
$bDef = D (StockQty $whId 'product_id' $prodId 'DEFECT')
$bPay = D (RepairPay $facId)
Write-Host ('BASE defect=' + $bDef + ' repairPay=' + $bPay)
Ok ((D $bDef) -ge 1) 'S2 precondition: DEFECT stock available'
Open '/outsource/return-order/repair' 2800
Ok ((ClickBtn 'btn_new_repair_return') -match 'OK') 'S2 click NEW REPAIR return'
Start-Sleep -Milliseconds 3000
Ok ((SelectLabelText 'lbl_factory' $facName) -match 'OK') ('S2 pick factory=' + $facName)
Start-Sleep -Milliseconds 1500
Ok ((SelectLabelText 'lbl_repair_out_wh' $whName) -match 'OK') ('S2 pick repair-out warehouse=' + $whName)
Start-Sleep -Milliseconds 1400
Ok ((OpenRowSelect 0 0) -match 'OK') 'S2 open row product select'
Start-Sleep -Milliseconds 1400
Ok ((PickOptionContains $prodName) -match 'OK') ('S2 pick product=' + $prodName)
Start-Sleep -Milliseconds 2200
Ok ((SetRowQty 0 '1') -match 'OK') 'S2 sent qty=1'
Start-Sleep -Milliseconds 600
Ok ((FillLabel 'lbl_charge_amount' '50') -match 'OK') 'S2 charge amount=50'
Start-Sleep -Milliseconds 600
Ok ((ClickBtn 'btn_save') -match 'OK') 'S2 save'
Start-Sleep -Milliseconds 3400
$rid = [int](MaxId 'outsource_return_order')
Ok ((OrderField $rid 'return_type') -eq 'REPAIR') ('S2 return_type=REPAIR id=' + $rid)
Ok ((D (OrderField $rid 'charge_amount')) -eq 50) 'S2 charge_amount=50 persisted'

Open ("/outsource/return-order/detail/$rid") 2800
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S2 audit'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3200
Write-Host ('S2 after audit: defect=' + (StockQty $whId 'product_id' $prodId 'DEFECT') + ' repairPay=' + (RepairPay $facId))
Ok ((D (StockQty $whId 'product_id' $prodId 'DEFECT')) -eq ($bDef - 1)) 'S2 after audit: DEFECT stock -1 (sent out)'
Ok ((D (RepairPay $facId)) -eq ($bPay + 50)) 'S2 after audit: repair charge payable +50'
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$rid") -eq 'AUDITED') 'S2 status=AUDITED'

# =====================================================================
Step 'S3 detail: sent/returned + close hidden while unreturned > 0'
Ok ((BodyHas (ZH 'txt_mr_unreturned')) -eq 'true') 'S3 detail shows "unreturned"'
Ok ((HasBtn 'btn_mr_close') -eq 'false') 'S3 close button hidden while unreturned > 0'
Ok ((HasBtn 'btn_mr_reopen') -eq 'false') 'S3 reopen button absent before close'

# =====================================================================
Step 'S4 register the 1 back (quality A)'
$bA = D (StockQty $whId 'product_id' $prodId 'A')
Ok ((ClickBtn 'lbl_repair_return') -match 'OK') 'S4 open register dialog'
Start-Sleep -Milliseconds 1900
Ok ((SelectLabelText 'lbl_repair_wh' $whName) -match 'OK') ('S4 pick return warehouse=' + $whName)
Start-Sleep -Milliseconds 1400
Ok ((OpenRowSelect 0 0) -match 'OK') 'S4 open row quality select'
Start-Sleep -Milliseconds 1400
Ok ((PickOptionContains (ZH 'opt_q_a')) -match 'OK') 'S4 returned quality=A'
Start-Sleep -Milliseconds 800
Ok ((SetRowQty 0 '1') -match 'OK') 'S4 returned qty=1'
Start-Sleep -Milliseconds 600
Ok ((ClickBtn 'btn_confirm_repair_return') -match 'OK') 'S4 confirm'
Start-Sleep -Milliseconds 3400
Ok (([int](SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair WHERE return_order_id=$rid")) -eq 1) 'S4 one repair-return record'
Ok ((D (StockQty $whId 'product_id' $prodId 'A')) -eq ($bA + 1)) 'S4 A stock +1 (repaired goods back)'
Ok ((HasBtn 'btn_mr_close') -eq 'true') 'S4 close button appears (unreturned = 0)'

# =====================================================================
Step 'S5 list row shows sent/returned'
# 2026-09-27：默认页签是「待返回」（未送完），本单在 S4 已全部送回 ⇒ 必须切到「已返回完」才看得到
Open '/outsource/return-order/repair' 2800
Write-Host ('S5 switch to 已返回完: ' + (ClickTabIdx 1))
Start-Sleep -Milliseconds 2400
$code = OrderField $rid 'code'
$ri = [int](FindRow $code)
Ok ($ri -ge 0) ('S5 found row index=' + $ri + ' code=' + $code)
Write-Host ('S5 sent/ret=' + (CellText $ri 2) + ' status=' + (CellText $ri 5))
Ok ((CellText $ri 2) -match '^1\s*/\s*1') ('S5 sent/returned cell = ' + (CellText $ri 2))
# 列序：退货单号|加工厂|送修/已返回|退货/送修内容|工厂收费|**状态**|操作 ⇒ 状态是第 5 列（0 基）；
# 2026-09-27 修：原写 6 读到的其实是**操作列**（'详情/结案'），断言靠"结案≠已结案"侥幸通过。
Ok ((CellText $ri 5) -notmatch [regex]::Escape((ZH 'txt_ro_closed'))) 'S5 status not closed yet'

# =====================================================================
Step 'S6 close'
Open ("/outsource/return-order/detail/$rid") 2800
Ok ((ClickBtn 'btn_mr_close') -match 'OK') 'S6 click close'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((SqlOne "SELECT closed_flag FROM outsource_return_order WHERE id=$rid") -eq '1') 'S6 closed_flag=1'
Ok ((SqlOne "SELECT closed_by FROM outsource_return_order WHERE id=$rid") -ne '') 'S6 closed_by recorded'
Ok ((HasBtn 'lbl_repair_return') -eq 'false') 'S6 register button hidden after close'
Ok ((HasBtn 'btn_mr_reopen') -eq 'true') 'S6 reopen button present after close'
Ok ((HasBtn 'btn_unaudit') -eq 'false') 'S6 un-audit hidden after close'

# =====================================================================
Step 'S7 list: closed status + progress tabs'
# 2026-09-27 三级菜单：「返回进度」下拉已由**页签**承担 ——
#   默认「待返回」(progress=OPEN：草稿 ∪ 送修未送完) 不含已全部送回的单；全部送回的落「已返回完」(progress=RETURNED)。
Open '/outsource/return-order/repair' 2800
Ok ([int](FindRow $code) -lt 0) 'S7 closed doc not listed under the default tab (待返回)'
Write-Host ('S7 switch to 已返回完: ' + (ClickTabIdx 1))
Start-Sleep -Milliseconds 2400
$ri7 = [int](FindRow $code)
Ok ($ri7 -ge 0) 'S7 closed doc listed under tab 已返回完'
Ok ((CellText $ri7 5) -match [regex]::Escape((ZH 'txt_ro_closed'))) ('S7 status cell = ' + (CellText $ri7 5))
Ok ((ClickRowBtnContains $ri7 (ZH 'btn_mr_reopen')) -match 'OK') 'S7 reopen action shown in list row'

# =====================================================================
Step 'S8 reopen'
Open ("/outsource/return-order/detail/$rid") 2800
Ok ((ClickBtn 'btn_mr_reopen') -match 'OK') 'S8 click reopen'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((SqlOne "SELECT closed_flag FROM outsource_return_order WHERE id=$rid") -eq '0') 'S8 closed_flag back to 0'
Ok ((HasBtn 'btn_mr_close') -eq 'true') 'S8 close button back (unreturned = 0)'
Ok ((HasBtn 'btn_unaudit') -eq 'true') 'S8 un-audit available again'

# =====================================================================
Step 'S9 roll back: revoke return, un-audit, cancel'
Ok ((ClickRowBtn 0 'btn_revoke') -match 'OK') 'S9 revoke the return record'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok (([int](SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair WHERE return_order_id=$rid")) -eq 0) 'S9 repair-return record removed'
Ok ((D (StockQty $whId 'product_id' $prodId 'A')) -eq $bA) 'S9 A stock rolled back'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S9 un-audit'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$rid") -eq 'DRAFT') 'S9 back to DRAFT'
Ok ((D (StockQty $whId 'product_id' $prodId 'DEFECT')) -eq $bDef) 'S9 DEFECT stock restored'
Ok ((D (RepairPay $facId)) -eq $bPay) 'S9 repair payable reversed'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S9 cancel doc'
ConfirmBox 1400 | Out-Null
Start-Sleep -Milliseconds 2200
Ok ((OrderField $rid 'status') -eq 'CANCELLED') 'S9 doc cancelled'

# =====================================================================
Step 'S10 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S10 no page/API errors during the whole flow'

# =====================================================================
# S11: roll the seeded stock back (see S0) so the file can be re-run from a clean slate.
Step 'S11 roll back the seeded stock'
$iu = Api 'PUT' ("/api/inventory/other/$ioId/un-audit") $null
Ok ($iu.Code -eq 200) ('S11 un-audit the seeded doc -> ' + $iu.Code + ' ' + $iu.Msg)
$ic = Api 'PUT' ("/api/inventory/other/$ioId/cancel") $null
Ok ($ic.Code -eq 200) ('S11 cancel the seeded doc -> ' + $ic.Code + ' ' + $ic.Msg)
Ok ((D (StockQty $whId 'product_id' $prodId 'DEFECT')) -eq $defBefore) ('S11 seeded stock rolled back (' + (StockQty $whId 'product_id' $prodId 'DEFECT') + ')')


Summary 'ui-e2e-16 work-order repair return progress + close'
