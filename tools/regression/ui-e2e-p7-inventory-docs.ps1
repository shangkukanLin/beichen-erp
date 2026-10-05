# P7 (2026-09-18 full-flow E2E): finished-goods inventory documents, all through the frontend.
#   1) 其他出入库 3 x IN + 3 x OUT      2) 移仓 3        3) 规格调整 3
#   4) 成品报损 3                        5) 成品盘点 2 (also re-checks I18: the finished-goods take page was empty)
# ALL DATA KEPT; rerunnable (creates only up to the target count). ASCII ONLY.
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
function SetDialogInput([int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const dlg=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!dlg)return 'NODLG';const t=[...dlg.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function DialogRowValues([int]$rowIdx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const dlg=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!dlg)return 'NODLG';const t=[...dlg.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW';return JSON.stringify([...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')].map(e=>e.value))})()"
  return (EvalJs $js)
}
function QtyOf([int]$productId, [int]$wid, [string]$q) {
  # NOTE: do NOT name a parameter $pid — it is a READ-ONLY automatic variable in PowerShell
  # (assigning to it throws SessionStateUnauthorizedAccessException and silently blanks the result)
  return (D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id=" + $productId + " AND warehouse_id=" + $wid + " AND quality_type='" + $q + "'")))
}
function TotalQty([int]$wid) {
  return (D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id IS NOT NULL AND warehouse_id=" + $wid)))
}
function AuditAll([string]$listPath, [string]$table, [string]$codeCol, [string]$stCol) {
  $codes = @(SqlList ("SELECT " + $codeCol + " FROM " + $table + " WHERE " + $stCol + "='DRAFT' ORDER BY id"))
  foreach ($c in $codes) {
    Open $listPath 2800
    $idx = [int](FindRow $c)
    if ($idx -lt 0) { Write-Host ('  (skip) ' + $c + ' row not found in ' + $listPath); continue }
    ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
    Start-Sleep -Milliseconds 1300
    ConfirmBox 1200 | Out-Null
    Start-Sleep -Milliseconds 2800
    $st = SqlOne ("SELECT " + $stCol + " FROM " + $table + " WHERE " + $codeCol + "='" + $c + "'")
    $msg = Txt '.el-message'
    if ($st -eq 'AUDITED') { Ok $true ($table + ' ' + $c + ' audited') }
    # 2026-10-05 F7-290: a not-audited document used to produce no verdict (INFO only) -> the step could not
    # fail. Now it is an explicit SKIP carrying the toast text.
    else { Skip ($table + ' ' + $c + ' not audited: ' + $msg) }
  }
}

# stock-backed product/warehouse + a second finished warehouse
$stkId = SqlOne "SELECT id FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity>20 ORDER BY quantity DESC LIMIT 1"
$whId = [int](SqlOne ("SELECT warehouse_id FROM warehouse_stock WHERE id=" + $stkId))
$prodId = [int](SqlOne ("SELECT product_id FROM warehouse_stock WHERE id=" + $stkId))
$whName = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + $whId)
$prod = SqlOne ("SELECT name FROM product WHERE id=" + $prodId)
# NOTE: 成品仓 = category 'INVENTORY' + type 'FINISHED' (category is NOT 'FINISHED')
$wh2Id = [int](SqlOne ("SELECT id FROM warehouse WHERE warehouse_type='FINISHED' AND id NOT IN (" + $whId + ") ORDER BY id LIMIT 1"))
$wh2Name = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + $wh2Id)
Write-Host ('[SEED] wh=' + $whName + '(' + $whId + ') wh2=' + $wh2Name + '(' + $wh2Id + ') prod=' + $prod + '(' + $prodId + ')')
Ok (($whId -gt 0) -and ($prodId -gt 0) -and ($wh2Id -gt 0)) 'resolved stock-backed product + two finished warehouses'
if (($whId -le 0) -or ($prodId -le 0) -or ($wh2Id -le 0)) { Summary 'P7 inventory docs'; exit 1 }

# =====================================================================
Step '1) other-io: 3 x IN + 3 x OUT (audited)'
$ioBase = D (SqlOne 'SELECT COUNT(*) FROM inventory_other_io')
$inBefore = TotalQty $whId
$ioNeed = [int]([Math]::Max(0, 6 - $ioBase))
$ioCreatedDelta = [decimal]0
for ($i = 1; $i -le $ioNeed; $i++) {
  $isIn = ($i -le 3)
  $q = 5
  if (-not $isIn) { $q = 2 }
  Write-Host ('--- other-io #' + $i + ' ' + $(if ($isIn) { 'IN' } else { 'OUT' }) + ' qty=' + $q)
  Open '/inventory/other-io/add' 3000
  ClearErrs | Out-Null
  Write-Host ('  wh: ' + (SelectLabelContains 'lbl_warehouse' $whName))
  Start-Sleep -Milliseconds 1100
  $tk = 'opt_io_in'
  if (-not $isIn) { $tk = 'opt_io_out' }
  Write-Host ('  type: ' + (SelectLabelContains 'lbl_io_type' (ZH $tk)))
  Start-Sleep -Milliseconds 1200
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1500
  $pk = PickOptionContains $prod
  Write-Host ('  product: ' + $pk)
  Start-Sleep -Milliseconds 700
  OpenRowSelect 0 1 | Out-Null
  Start-Sleep -Milliseconds 1000
  Write-Host ('  quality: ' + (PickOptionContains (ZH 'opt_q_a')))
  Start-Sleep -Milliseconds 600
  Write-Host ('  qty: ' + (SetRowInputT 0 0 2 ('' + $q)))
  Start-Sleep -Milliseconds 600
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM inventory_other_io')
  Ok (($cnt -eq ($ioBase + $i))) ('other-io #' + $i + ' created (db=' + $cnt + ', product=' + $pk + ')')
  if ($cnt -gt ($ioBase + $i - 1)) { if ($isIn) { $ioCreatedDelta += $q } else { $ioCreatedDelta -= $q } }
}
AuditAll '/inventory/other-io' 'inventory_other_io' 'code' 'status'
$ioAud = D (SqlOne "SELECT COUNT(*) FROM inventory_other_io WHERE status='AUDITED'")
$ioItems = D (SqlOne 'SELECT COUNT(*) FROM inventory_other_io_item')
$ioIn = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM inventory_other_io_item i JOIN inventory_other_io d ON d.id=i.other_io_id WHERE d.io_type='IN'")
$ioOut = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM inventory_other_io_item i JOIN inventory_other_io d ON d.id=i.other_io_id WHERE d.io_type='OUT'")
$inAfter = TotalQty $whId
Write-Host ("[DB] otherIo docs=" + (D (SqlOne 'SELECT COUNT(*) FROM inventory_other_io')) + " audited=$ioAud items=$ioItems inQty=$ioIn outQty=$ioOut stock=$inBefore->$inAfter")
Ok (($ioAud -ge 6)) ('other-io audited >= 6 (got ' + $ioAud + ')')
Ok (($ioItems -ge 6)) ('other-io items >= 6 (got ' + $ioItems + ')')
Ok ((($inAfter - $inBefore) -eq $ioCreatedDelta)) ('stock change == IN - OUT of docs created this run (' + ($inAfter - $inBefore) + ' = ' + $ioCreatedDelta + ')')

# =====================================================================
Step '2) warehouse move x3 (this warehouse -> 2nd finished warehouse)'
$mvBase = D (SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move')
$fromBefore = TotalQty $whId
$toBefore = TotalQty $wh2Id
$mvNeed = [int]([Math]::Max(0, 3 - $mvBase))
for ($i = 1; $i -le $mvNeed; $i++) {
  Write-Host ('--- move #' + $i)
  Open '/inventory/warehouse-move/add' 3000
  ClearErrs | Out-Null
  Write-Host ('  from: ' + (SelectLabelContains 'lbl_out_warehouse' $whName))
  Start-Sleep -Milliseconds 900
  Write-Host ('  to: ' + (SelectLabelContains 'lbl_in_wh_move' $wh2Name))
  Start-Sleep -Milliseconds 1100
  ClickBtn 'btn_add_detail' | Out-Null
  Start-Sleep -Milliseconds 1000
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1500
  Write-Host ('  product: ' + (PickOptionContains $prod))
  Start-Sleep -Milliseconds 700
  OpenRowSelect 0 1 | Out-Null
  Start-Sleep -Milliseconds 1000
  Write-Host ('  quality: ' + (PickOptionContains (ZH 'opt_q_a')))
  Start-Sleep -Milliseconds 600
  Write-Host ('  qty: ' + (SetRowInputT 0 0 2 '3'))
  Start-Sleep -Milliseconds 600
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move')
  Ok (($cnt -eq ($mvBase + $i))) ('warehouse move #' + $i + ' created (db=' + $cnt + ')')
}
AuditAll '/inventory/warehouse-move' 'inventory_warehouse_move' 'code' 'status'
$mvAud = D (SqlOne "SELECT COUNT(*) FROM inventory_warehouse_move WHERE status='AUDITED'")
$fromAfter = TotalQty $whId
$toAfter = TotalQty $wh2Id
Write-Host ("[DB] moves audited=$mvAud from=$fromBefore->$fromAfter to=$toBefore->$toAfter")
Ok (($mvAud -ge 3)) ('moves audited >= 3 (got ' + $mvAud + ')')
Ok ((($fromBefore - $fromAfter) -eq ($toAfter - $toBefore))) ('moved qty equal on both sides (' + ($fromBefore - $fromAfter) + ' vs ' + ($toAfter - $toBefore) + ')')

# =====================================================================
Step '3) quality reclass A->B x3 in the first warehouse'
# NOTE: /inventory/reclassify writes to **product_reclassify** (inventory_stock_reclass is a legacy empty table)
$rcBase = D (SqlOne 'SELECT COUNT(*) FROM product_reclassify')
$aBefore = QtyOf $prodId $whId 'A'
$bBefore = QtyOf $prodId $whId 'B'
$rcNeed = [int]([Math]::Max(0, 3 - $rcBase))
for ($i = 1; $i -le $rcNeed; $i++) {
  Write-Host ('--- reclass #' + $i)
  Open '/inventory/reclassify/add' 3000
  ClearErrs | Out-Null
  Write-Host ('  wh: ' + (SelectLabelContains 'lbl_warehouse' $whName))
  Start-Sleep -Milliseconds 1100
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1500
  Write-Host ('  product: ' + (PickOptionContains $prod))
  Start-Sleep -Milliseconds 700
  OpenRowSelect 0 1 | Out-Null
  Start-Sleep -Milliseconds 1000
  Write-Host ('  source quality: ' + (PickOptionContains (ZH 'opt_q_a')))
  Start-Sleep -Milliseconds 600
  OpenRowSelect 0 2 | Out-Null
  Start-Sleep -Milliseconds 1000
  Write-Host ('  target quality: ' + (PickOptionContains (ZH 'opt_q_b')))
  Start-Sleep -Milliseconds 600
  Write-Host ('  qty: ' + (SetRowInputT 0 0 3 '2'))
  Start-Sleep -Milliseconds 600
  Write-Host ('  row inputs=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];const r=t.querySelectorAll('.el-table__body tbody tr')[0];return (r.innerText||'').replace(/\s+/g,' ')+' INPUTS='+[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value).join('/')})()"))
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM product_reclassify')
  Ok (($cnt -eq ($rcBase + $i))) ('reclass #' + $i + ' created (db=' + $cnt + ')')
}
AuditAll '/inventory/reclassify' 'product_reclassify' 'code' 'status'
$rcAud = D (SqlOne "SELECT COUNT(*) FROM product_reclassify WHERE status='AUDITED'")
$aAfter = QtyOf $prodId $whId 'A'
$bAfter = QtyOf $prodId $whId 'B'
$rcQty = D (SqlOne 'SELECT COALESCE(SUM(quantity),0) FROM product_reclassify_item')
Write-Host ("[DB] reclass audited=$rcAud totalQty=$rcQty A=$aBefore->$aAfter B=$bBefore->$bAfter")
Ok (($rcAud -ge 3)) ('reclass audited >= 3 (got ' + $rcAud + ')')
Ok ((($aBefore - $aAfter) -eq ($bAfter - $bBefore))) ('A decreased == B increased (' + ($aBefore - $aAfter) + ' vs ' + ($bAfter - $bBefore) + ')')
# 2026-09-22 fix: the previous assertion was cumulative for the (product, warehouse) pair picked by
# "the row with the largest stock" -- but the creation loop above is skipped once 3 docs exist
# ($rcNeed = 0), and that pair changes between runs => on a long-lived DB the pair has no B at all
# and the assertion was a permanent false red (unrelated to any code change).
# Now we assert what THIS RUN actually did (the delta), and state the skipped case explicitly.
if ($rcNeed -gt 0) {
  Ok (($bAfter - $bBefore) -ge 6) ('this run moved >= 6 into quality B (delta=' + ($bAfter - $bBefore) + ')')
} else {
  Write-Host ('  INFO reclass creation skipped (already ' + $rcBase + ' docs) -> cumulative B check not applicable; B delta this run = ' + ($bAfter - $bBefore))
}

# =====================================================================
Step '4) finished-goods stock loss x3 (reason=branken)'
$lsBase = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_loss')
$lsStockBefore = TotalQty $whId
$lsNeed = [int]([Math]::Max(0, 3 - $lsBase))
$lsNewQty = [decimal]($lsNeed * 1)
for ($i = 1; $i -le $lsNeed; $i++) {
  Write-Host ('--- loss #' + $i)
  Open '/inventory/stock-loss/add' 3000
  ClearErrs | Out-Null
  Write-Host ('  wh: ' + (SelectLabelContains 'lbl_warehouse' $whName))
  Start-Sleep -Milliseconds 1100
  Write-Host ('  reason: ' + (SelectLabel 'lbl_loss_reason' 'opt_loss_broken'))
  Start-Sleep -Milliseconds 900
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1500
  Write-Host ('  product: ' + (PickOptionContains $prod))
  Start-Sleep -Milliseconds 700
  Write-Host ('  qty: ' + (SetRowInputT 0 0 2 '1'))
  Start-Sleep -Milliseconds 600
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_loss')
  Ok (($cnt -eq ($lsBase + $i))) ('stock loss #' + $i + ' created (db=' + $cnt + ')')
}
AuditAll '/inventory/stock-loss' 'inventory_stock_loss' 'code' 'status'
$lsAud = D (SqlOne "SELECT COUNT(*) FROM inventory_stock_loss WHERE status='AUDITED'")
$lsStockAfter = TotalQty $whId
$lsQty = D (SqlOne 'SELECT COALESCE(SUM(quantity),0) FROM inventory_stock_loss_item')
Write-Host ("[DB] losses audited=$lsAud totalLossQty=$lsQty stock=$lsStockBefore->$lsStockAfter")
Ok (($lsAud -ge 3)) ('stock losses audited >= 3 (got ' + $lsAud + ')')
Ok ((($lsStockBefore - $lsStockAfter) -eq $lsNewQty)) ('stock decreased by the losses created this run (' + ($lsStockBefore - $lsStockAfter) + ' = ' + $lsNewQty + ')')

# =====================================================================
Step '5) finished-goods stock take x2 (also re-checks I18: the take page used to show 0 rows)'
Open '/inventory/stock-take' 3000
$takeRows0 = (Rows 0).n
Write-Host ('  take page rows BEFORE = ' + $takeRows0 + ' (I18: previously 0 even though finished warehouses exist)')
$tkBase = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_take')
$tkNeed = [int]([Math]::Max(0, 2 - $tkBase))
for ($i = 1; $i -le $tkNeed; $i++) {
  Write-Host ('--- take #' + $i)
  # a warehouse can have only ONE take per month -> pick a finished warehouse that has no take yet
  $tkWh = $whName
  if ((D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take WHERE warehouse_id=" + $whId))) -gt 0) { $tkWh = $wh2Name }
  Open '/inventory/stock-take' 3000
  ClearErrs | Out-Null
  ClickBtn 'btn_new_take' | Out-Null
  Start-Sleep -Milliseconds 1600
  Write-Host ('  open wh sel: ' + (DialogOpenSelect 0))
  Start-Sleep -Milliseconds 1500
  Write-Host ('  wh: ' + (PickOptionContains $tkWh))
  Start-Sleep -Milliseconds 700
  ClickDialogBtn 'btn_ok' | Out-Null
  Start-Sleep -Milliseconds 2800
  Write-Host ('  toast=' + (Txt '.el-message'))
  $r = Rows 0
  Write-Host ('  take rows=' + $r.n)
  $tkCode = SqlOne 'SELECT take_no FROM inventory_stock_take ORDER BY id DESC LIMIT 1'
  Write-Host ('  new take=' + $tkCode)
  $idx = [int](FindRow $tkCode)
  if ($idx -lt 0) { $idx = 0 }
  Write-Host ('  enter actual: ' + (ClickRowBtnContains $idx (ZH 'btn_enter_take')))
  Start-Sleep -Milliseconds 2800
  Write-Host ('  dialog inputs BEFORE=' + (DialogRowValues 0))
  $book = [decimal]2
  try { $book = [decimal]((($(DialogRowValues 0) | ConvertFrom-Json)[1])) } catch { $book = [decimal]2 }
  $actual = $book
  if ($i -eq 1) { $actual = $book + 2 } else { $actual = $book - 1 }
  if ($actual -lt 0) { $actual = 0 }
  Write-Host ('  set actual=' + $actual + ' (book=' + $book + ') -> ' + (SetDialogInput 0 1 ('' + $actual)))
  Start-Sleep -Milliseconds 700
  Write-Host ('  dialog inputs AFTER=' + (DialogRowValues 0))
  ClickDialogBtn 'btn_save_take' | Out-Null
  Start-Sleep -Milliseconds 2800
  Write-Host ('  toast=' + (Txt '.el-message'))
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_take')
  Ok (($cnt -eq ($tkBase + $i))) ('stock take #' + $i + ' created (db=' + $cnt + ')')
}
AuditAll '/inventory/stock-take' 'inventory_stock_take' 'take_no' 'status'
$tkAud = D (SqlOne "SELECT COUNT(*) FROM inventory_stock_take WHERE status='AUDITED'")
$tkItems = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_take_item')
$tkMatched = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_take_item WHERE actual_quantity IS NOT NULL AND diff_quantity = (actual_quantity - book_quantity)')
$tkTotal = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_take_item WHERE actual_quantity IS NOT NULL')
$finalStock = TotalQty $whId
$finalActual = D (SqlOne ("SELECT COALESCE(SUM(actual_quantity),0) FROM inventory_stock_take_item i JOIN inventory_stock_take t ON t.id=i.take_id WHERE t.status='AUDITED' AND i.product_id=" + $prodId + " AND t.warehouse_id=" + $whId))
Open '/inventory/stock-take' 3000
$takeRows1 = (Rows 0).n
Write-Host ("[DB] takes audited=$tkAud items=$tkItems diffConsistent=$tkMatched/$tkTotal")
Write-Host ("[DB] stock now=$finalStock ; sum(actual of audited takes, this product+wh)=$finalActual ; take page rows AFTER=$takeRows1")
Ok (($tkAud -ge 2)) ('stock takes audited >= 2 (got ' + $tkAud + ')')
Ok (($tkItems -ge 2)) ('stock take items >= 2 (got ' + $tkItems + ')')
Ok (($tkMatched -eq $tkTotal)) ('every take item diff == actual - book (' + $tkMatched + '/' + $tkTotal + ')')
Ok (($takeRows1 -gt 0)) ('I18 re-check: finished-goods take page now lists rows (' + $takeRows0 + ' -> ' + $takeRows1 + ')')
Write-Host ('errs=' + (Errs))
Summary 'P7 inventory documents'
