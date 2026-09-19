# P9a (2026-09-18): 物料盘点 x2 through the frontend (the planned item that was still missing).
#   /outsource/material-stock-take  (same StockTakePanel as the finished-goods page, scope=MATERIAL)
#   flow: 新建盘点(仓库+月份+日期) -> 录入实盘 -> 保存实盘 -> 审核
#   The 实盘 column is the ONLY input-number of the row (index 0); 备注 is the second input.
#   take #1 intentionally books a real difference (row0 = 账面 - 1) so the stock movement is verifiable.
# ALL DATA KEPT; rerunnable (one take per warehouse per month). ASCII ONLY.
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
function MatStock([int]$whId) {
  return (D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=" + $whId)))
}
function SetDialogRowInput2([int]$rowIdx, [int]$inputIdx, [string]$value) {
  # dialog-scoped: the 实盘 column is the FIRST input-number of the row (index 0), 备注 is index 1
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const dlg=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!dlg)return 'NODLG';const t=[...dlg.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function BookQty([int]$whId) {
  return (D (SqlOne ("SELECT COALESCE(quantity,0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=" + $whId + " ORDER BY quantity DESC LIMIT 1")))
}

# 物料盘点 scope = 委外仓 + 自有物料仓（与页面下拉口径一致）
$matScopeWhere = "warehouse_category='OUTSOURCE' OR (warehouse_category='INVENTORY' AND warehouse_type='AUXILIARY')"
$matWhIds = @()
foreach ($ln in (SqlLines ("SELECT id FROM warehouse WHERE " + $matScopeWhere))) { $matWhIds += [int]$ln.Trim() }
$matInList = ($matWhIds -join ',')
# pick material warehouses that hold material stock
$matWhs = @()
foreach ($ln in (SqlLines "SELECT w.id, w.warehouse_name, COALESCE(SUM(s.quantity),0) q FROM warehouse w JOIN warehouse_stock s ON s.warehouse_id=w.id WHERE s.material_id IS NOT NULL GROUP BY w.id, w.warehouse_name HAVING q > 0 ORDER BY q DESC LIMIT 6")) {
  $f = $ln -split "`t"
  if ($f.Count -ge 3) { $matWhs += [pscustomobject]@{ Id = [int]$f[0].Trim(); Name = $f[1].Trim(); Qty = [decimal]$f[2].Trim() } }
}
Write-Host ('[SEED] material warehouses with stock = ' + $matWhs.Count + ' first=' + $(if ($matWhs.Count) { $matWhs[0].Name + '/' + $matWhs[0].Qty } else { '-' }))
Ok (($matWhs.Count -ge 2)) ('at least two material warehouses hold stock (' + $matWhs.Count + ')')
if ($matWhs.Count -lt 2) { Write-Host ('RESULT material take PASS=' + $pass + ' FAIL=' + $fail); exit 1 }

$TARGET = 3
$doneBefore = D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take WHERE warehouse_id IN (" + $matInList + ")"))
$need = [int]([Math]::Max(0, $TARGET - $doneBefore))
Write-Host ('[BASE] material-scope takes already created = ' + $doneBefore + ' target=' + $TARGET + ' need=' + $need)

# a warehouse can hold only ONE take per month -> only iterate over warehouses that have none yet
$cands = @()
foreach ($w in $matWhs) {
  $has = D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take WHERE warehouse_id=" + $w.Id))
  if ($has -eq 0) { $cands += $w } else { Write-Host ('  (skip ' + $w.Name + ': already has a take)') }
}
Write-Host ('  candidate warehouses without a take = ' + $cands.Count)
if ($cands.Count -lt $need) { $need = $cands.Count }
$made = 0
for ($i = 1; $i -le $need; $i++) {
  $wh = $cands[$i - 1]
  $stockBefore = MatStock $wh.Id
  # the LAST new take books a real difference (盘亏 1 on the biggest material) to prove the adjustment path
  $delta = 0
  if ($i -eq $need) { $delta = -1 }
  Step ('material take #' + ($made + 1) + ' @ ' + $wh.Name + ' delta(row0)=' + $delta)
  Open '/outsource/material-stock-take' 3000
  ClearErrs | Out-Null
  Write-Host ('  new take: ' + (ClickBtn 'btn_new_take'))
  Start-Sleep -Milliseconds 1800
  Write-Host ('  open wh sel: ' + (DialogOpenSelect 0))
  Start-Sleep -Milliseconds 1500
  Write-Host ('  wh: ' + (PickOptionContains $wh.Name))
  Start-Sleep -Milliseconds 700
  Write-Host ('  ok: ' + (ClickDialogBtn 'btn_ok'))
  Start-Sleep -Milliseconds 3000
  Write-Host ('  toast=' + (Txt '.el-message'))
  $code = SqlOne 'SELECT take_no FROM inventory_stock_take ORDER BY id DESC LIMIT 1'
  Write-Host ('  new takeNo=' + $code)
  Open '/outsource/material-stock-take' 2800
  $idx = [int](FindRow $code)
  if ($idx -lt 0) { $idx = 0 }
  Write-Host ('  enter actual: ' + (ClickRowBtnContains $idx (ZH 'btn_enter_take')))
  Start-Sleep -Milliseconds 3000
  if ($delta -ne 0) {
    $book = BookQty $wh.Id
    Write-Host ('  book(row0)=' + $book + ' -> actual=' + ($book + $delta) + ' : ' + (SetDialogRowInput2 0 0 ('' + ($book + $delta))))
  } else {
    Write-Host ('  (all rows keep the pre-filled 实盘 = 账面)')
  }
  Start-Sleep -Milliseconds 900
  Write-Host ('  save actual: ' + (ClickDialogBtn 'btn_save_take'))
  Start-Sleep -Milliseconds 3000
  Write-Host ('  toast=' + (Txt '.el-message'))
  $st = SqlOne ("SELECT status FROM inventory_stock_take WHERE take_no='" + $code + "'")
  Ok (($st -eq 'DRAFT')) ('take ' + $code + ' has a real 实盘 recorded (status=' + $st + ')')
  Open '/outsource/material-stock-take' 2800
  $idx2 = [int](FindRow $code)
  if ($idx2 -lt 0) { $idx2 = 0 }
  Write-Host ('  audit: ' + (ClickRowBtnContains $idx2 (ZH 'btn_audit')))
  Start-Sleep -Milliseconds 1400
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 3000
  $st2 = SqlOne ("SELECT status FROM inventory_stock_take WHERE take_no='" + $code + "'")
  $stockAfter = MatStock $wh.Id
  Write-Host ('  -> ' + $code + ' status=' + $st2 + ' msg=' + (Txt '.el-message') + ' stock ' + $stockBefore + ' -> ' + $stockAfter)
  Ok (($st2 -eq 'AUDITED')) ('material take ' + $code + ' audited')
  Ok ((($stockAfter - $stockBefore) -eq $delta)) ('stock moved exactly by the booked difference (' + ($stockAfter - $stockBefore) + ' = ' + $delta + ')')
  $made++
}

Step 'DB cross-check'
$inList2 = $matInList
$matTakes = D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take WHERE warehouse_id IN (" + $inList2 + ")"))
$matAud = D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take WHERE status='AUDITED' AND warehouse_id IN (" + $inList2 + ")"))
$items = D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take_item i JOIN inventory_stock_take t ON t.id=i.take_id WHERE t.warehouse_id IN (" + $inList2 + ")"))
$bad = D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take_item i JOIN inventory_stock_take t ON t.id=i.take_id WHERE t.warehouse_id IN (" + $inList2 + ") AND i.actual_quantity IS NOT NULL AND ABS(i.diff_quantity - (i.actual_quantity - i.book_quantity)) > 0.01"))
$noActual = D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take_item i JOIN inventory_stock_take t ON t.id=i.take_id WHERE t.warehouse_id IN (" + $inList2 + ") AND i.actual_quantity IS NULL"))
Write-Host ("[DB] materialTakes=$matTakes audited=$matAud items=$items diffMismatch=$bad itemsWithoutActual=$noActual")
Ok (($matAud -ge 2)) ('material-warehouse takes audited >= 2 (got ' + $matAud + ')')
Ok (($items -ge 2)) ('material take items >= 2 (got ' + $items + ')')
Ok (($bad -eq 0)) ('every material take item satisfies diff == actual - book (mismatches=' + $bad + ')')
Ok (($noActual -eq 0)) ('every material take item has an actual qty recorded (missing=' + $noActual + ')')
Write-Host ('errs=' + (Errs))
Write-Host ('RESULT material take PASS=' + $pass + ' FAIL=' + $fail)
