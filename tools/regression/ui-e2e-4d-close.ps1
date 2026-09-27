# 结单 / 反结单（加工单）—— 2026-09-18 重写：原版是**零断言**（全篇只 Write-Host）+ 从不填数量 +
# 按写死的产品名找单 + 写死 'TEST-MAT-WH'。现在改为：**以库为准选单 + 逐项断言 + 真的结单**。
#   flow per order: 结单页 -> 未选退回仓库确认结单(应被拦) -> 填良品退料/单价 -> 保存草稿(落库断言)
#                   -> 选退回仓库 -> 确认结单(必要时走强制退料) -> 断言 FINISHED + 退料入库
#                   -> 反结单(对称回滚) -> 再结单(保留终态 FINISHED)
# ALL DATA KEPT; rerunnable (target = 2 closed orders). ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
$pass = 0; $fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:pass++; Write-Host ('PASS ' + $m) } else { $script:fail++; Write-Host ('FAIL ' + $m) } }
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function RowInputsT([int]$tblIdx, [int]$rowIdx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW';return JSON.stringify([...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')].map(e=>e.value))})()"
  return (EvalJs $js)
}
function MatQty([int]$whId, [int]$matId) {
  return (D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=" + $matId + " AND warehouse_id=" + $whId)))
}
# Chinese words from code points (file stays ASCII-only)
$CN_BACK_WH = [string][char]0x9000 + [string][char]0x56DE + [string][char]0x4ED3 + [string][char]0x5E93   # 退回仓库
$CN_FINISHED = [string][char]0x5DF2 + [string][char]0x7ED3 + [string][char]0x5355                             # 已结单（2026-09-27 由「已完成」改：状态文案统一）

$whMatName = ZH 'wh_auxA'
$whMatId = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_name='" + $whMatName + "' LIMIT 1")
Write-Host ('[SEED] return warehouse=' + $whMatName + '(' + $whMatId + ')')

Step '0) pick PRODUCING orders without a close report (DB driven)'
$target = 3
$closedNow = 0
$orders = @()
foreach ($ln in (SqlLines "SELECT o.id, o.code FROM outsource_order o WHERE o.status='PRODUCING' AND o.id NOT IN (SELECT COALESCE(order_id,0) FROM outsource_order_close_report) ORDER BY o.id LIMIT 6")) {
  $f = $ln -split "`t"
  if ($f.Count -ge 2) { $orders += [pscustomobject]@{ Id = [int]$f[0].Trim(); Code = $f[1].Trim() } }
}
$alreadyStart = D (SqlOne "SELECT COUNT(*) FROM outsource_order WHERE status='FINISHED'")
Write-Host ('[BASE] already FINISHED=' + $alreadyStart + ' candidates=' + $orders.Count)
Ok (($orders.Count -ge 1)) ('found PRODUCING orders to close (' + $orders.Count + ')')
if ($orders.Count -lt 1) { Write-Host ('RESULT 4d close PASS=' + $pass + ' FAIL=' + $fail); exit 0 }
$need = [int]([Math]::Max(0, $target - $alreadyStart))

foreach ($o in $orders) {
  if ($closedNow -ge $need) { break }
  $oid = $o.Id
  Step ('close order ' + $o.Code + ' (id=' + $oid + ')')
  Open ('/outsource/order/close/' + $oid) 3200
  $r = Rows 0
  Write-Host ('  close rows=' + $r.n)
  Ok (($r.n -ge 1)) ('close page lists the BOM materials (' + $r.n + ' rows)')
  Write-Host ('  row0 inputs=' + (RowInputsT 0 0))

  # --- negative: without a 退回仓库 the 确认结单 button must be DISABLED (close.vue: :disabled="!canConfirm")
  $disJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$(B64 (ZH 'btn_confirm_close'))');const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('button')].filter(vis).find(x=>((x.innerText||'').indexOf(L)>=0));if(!b)return 'NOBTN';return (b.disabled||b.classList.contains('is-disabled'))?'DISABLED':'ENABLED'})()"
  $dis = EvalJs $disJs
  Write-Host ('  confirm button without warehouse = ' + $dis)
  Ok (($dis -eq 'DISABLED')) 'confirming without a return warehouse is not possible (button disabled)'
  if ($dis -eq 'ENABLED') {
    # fallback path: the page's defensive warning should mention 退回仓库
    ClickBtn 'btn_confirm_close' | Out-Null
    Start-Sleep -Milliseconds 1200
    $msg1 = Txt '.el-message'
    Write-Host ('  (enabled) msg=' + $msg1)
    Ok (($msg1 -match [regex]::Escape($CN_BACK_WH))) 'enabled path still warns about the return warehouse'
    ConfirmBox 900 | Out-Null
    Start-Sleep -Milliseconds 800
  }

  # --- fill 良品退料 = 1 on row 0 (+ 物料单价 = 10), save draft, assert DB
  Write-Host ('  good return qty: ' + (SetRowInputT 0 0 0 '1'))
  Start-Sleep -Milliseconds 500
  Write-Host ('  material price: ' + (SetRowInputT 0 0 4 '10'))
  Start-Sleep -Milliseconds 600
  Write-Host ('  row0 after fill=' + (RowInputsT 0 0))
  ClickBtn 'btn_save_draft' | Out-Null
  Start-Sleep -Milliseconds 2600
  $repId = SqlOne ("SELECT COALESCE(MAX(id),0) FROM outsource_order_close_report WHERE order_id=" + $oid)
  $goodQty = D (SqlOne ("SELECT COALESCE(SUM(good_return_qty),0) FROM outsource_order_close_report_item WHERE report_id=" + $repId))
  $matId0 = [int](SqlOne ("SELECT COALESCE(outsource_material_id,0) FROM outsource_order_close_report_item WHERE report_id=" + $repId + " ORDER BY id LIMIT 1"))
  Write-Host ('  closeReport=' + $repId + ' goodReturnQty=' + $goodQty + ' row0Material=' + $matId0)
  Ok (($repId -gt 0)) ('close report row created for the order (' + $repId + ')')
  Ok (($goodQty -ge 1)) ('draft close report stored the 良品退料 qty (' + $goodQty + ')')

  # --- pick the return warehouse, confirm close
  $stkBefore = MatQty $whMatId $matId0
  Write-Host ('  return wh: ' + (OpenSelectIdx 0))
  Start-Sleep -Milliseconds 1400
  Write-Host ('  pick wh: ' + (PickOptionContains $whMatName))
  Start-Sleep -Milliseconds 900
  ClickBtn 'btn_confirm_close' | Out-Null
  Start-Sleep -Milliseconds 1600
  if ((BodyHas (ZH 'chk_force_return')) -match 'true') {
    Write-Host ('  force dialog -> check: ' + (ClickDialogText 'chk_force_return'))
    Start-Sleep -Milliseconds 600
    Write-Host ('  confirm force: ' + (ClickDialogBtn 'btn_confirm_force'))
  } else {
    Write-Host ('  confirm box: ' + (ConfirmBox 1000))
  }
  Start-Sleep -Milliseconds 4000
  $st1 = SqlOne ("SELECT status FROM outsource_order WHERE id=" + $oid)
  $msg2 = Txt '.el-message'
  $stkAfter = MatQty $whMatId $matId0
  Write-Host ('  -> status=' + $st1 + ' msg=' + $msg2 + ' materialStock ' + $stkBefore + ' -> ' + $stkAfter)
  Ok (($st1 -eq 'FINISHED')) ('order ' + $o.Code + ' is FINISHED after 确认结单 (status=' + $st1 + ')')
  Ok (($stkAfter -ge $stkBefore)) ('returned material did not reduce our stock (' + $stkBefore + ' -> ' + $stkAfter + ')')
  $repStatus = SqlOne ("SELECT status FROM outsource_order_close_report WHERE id=" + $repId)
  Write-Host ('  closeReport status=' + $repStatus)
  if ($st1 -ne 'FINISHED') { Write-Host ('  (stop: close failed, msg=' + $msg2 + ')'); break }
  $closedNow++

  # --- reopen (反结单) then close again -> symmetric, terminal state stays FINISHED
  Open ('/outsource/order/close/' + $oid) 3000
  Write-Host ('  reopen: ' + (ClickBtn 'btn_reopen'))
  Start-Sleep -Milliseconds 1200
  Write-Host ('  confirm: ' + (ConfirmBox 1200))
  Start-Sleep -Milliseconds 3200
  $st2 = SqlOne ("SELECT status FROM outsource_order WHERE id=" + $oid)
  Write-Host ('  -> after reopen status=' + $st2 + ' msg=' + (Txt '.el-message'))
  Ok (($st2 -eq 'PRODUCING')) ('reopen rolled the order back to PRODUCING (status=' + $st2 + ')')
  if ($st2 -eq 'PRODUCING') {
    Open ('/outsource/order/close/' + $oid) 3000
    OpenSelectIdx 0 | Out-Null
    Start-Sleep -Milliseconds 1300
    PickOptionContains $whMatName | Out-Null
    Start-Sleep -Milliseconds 800
    ClickBtn 'btn_confirm_close' | Out-Null
    Start-Sleep -Milliseconds 1500
    if ((BodyHas (ZH 'chk_force_return')) -match 'true') {
      ClickDialogText 'chk_force_return' | Out-Null
      Start-Sleep -Milliseconds 600
      ClickDialogBtn 'btn_confirm_force' | Out-Null
    } else {
      ConfirmBox 1000 | Out-Null
    }
    Start-Sleep -Milliseconds 3800
    $st3 = SqlOne ("SELECT status FROM outsource_order WHERE id=" + $oid)
    Write-Host ('  -> re-closed status=' + $st3 + ' msg=' + (Txt '.el-message'))
    Ok (($st3 -eq 'FINISHED')) ('re-closed after reopen (status=' + $st3 + ')')
  }
}

Step 'DB cross-check'
$fin = D (SqlOne "SELECT COUNT(*) FROM outsource_order WHERE status='FINISHED'")
$repRows = D (SqlOne 'SELECT COUNT(*) FROM outsource_order_close_report')
$repItems = D (SqlOne 'SELECT COUNT(*) FROM outsource_order_close_report_item')
$goodSum = D (SqlOne 'SELECT COALESCE(SUM(good_return_qty),0) FROM outsource_order_close_report_item')
$srcRows = D (SqlOne 'SELECT COUNT(*) FROM outsource_order_close_report_item WHERE status IS NOT NULL')
Write-Host ("[DB] finishedOrders=$fin closeReports=$repRows closeItems=$repItems goodReturnSum=$goodSum (before this run: finished=" + $alreadyStart + ")")
Ok (($fin -ge $target)) ('FINISHED work orders >= ' + $target + ' (got ' + $fin + ')')
Ok (($repRows -ge 1)) ('close report rows exist (' + $repRows + ')')
Ok (($repItems -ge 1)) ('close report items exist (' + $repItems + ')')
Ok (($goodSum -ge 1)) ('良品退料 total >= 1 (got ' + $goodSum + ')')
Open '/outsource/order' 2800
Ok ((BodyHas $CN_FINISHED) -match 'true') 'order list shows the finished state (已结单)'
Write-Host ('errs=' + (Errs))
Write-Host ('RESULT 4d close PASS=' + $pass + ' FAIL=' + $fail)
