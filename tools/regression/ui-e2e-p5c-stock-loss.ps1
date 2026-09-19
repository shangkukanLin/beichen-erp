# P5c (2026-09-18 full-flow E2E): 委外物料报损 x3 through the frontend only.
#   /outsource/stock-loss -> 新增报损单 -> 仓库 + 添加物料 + 报损数量 -> save -> audit.
#   asserts: 3 docs audited, own-warehouse material stock decreased, stock logs written.
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
function PickOptionEndsWith([string]$text, [int]$wait = 0) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const O=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim().endsWith(O));if(li.length){li[0].click();return 'OK'}}return 'NOOPT:'+O+'/dd='+dds.length})()"
  if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
  return (EvalJs $js)
}
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
  return (EvalJs $js)
}

$whAux = ZH 'wh_auxA'
$auxId = [int](SqlOne ("SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1"))
$matName = (ZH 'val_material') + '1'
$matId = [int](SqlOne ("SELECT id FROM outsource_material WHERE material_name='" + $matName + "' LIMIT 1"))
$stockBefore = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=" + $matId + " AND warehouse_id=" + $auxId))
$before = D (SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss')
$need = [int]([Math]::Max(0, 3 - $before))
# rerun-safe: only the documents created in THIS run may change the stock (20 pcs each)
$createdQty = [int]($need * 20)
Write-Host ('[BASE] material losses=' + $before + ' needCreate=' + $need + ' expectStockDelta=-' + $createdQty + ' ' + $matName + ' stock=' + $stockBefore)

if ($need -gt 0) {
  for ($i = 1; $i -le $need; $i++) {
    Step ('material loss #' + $i)
    Open '/outsource/stock-loss' 3000
    ClearErrs | Out-Null
    Write-Host ('  new: ' + (ClickBtn 'btn_new_loss'))
    Start-Sleep -Milliseconds 2400
    Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
    Write-Host ('  warehouse: ' + (SelectLabelContains 'lbl_warehouse' $whAux))
    Start-Sleep -Milliseconds 1400
    $add = ClickBtn 'btn_add_material_plus'
    if ($add -notmatch 'OK') { $add = ClickBtn 'btn_add_material' }
    Write-Host ('  add material row: ' + $add)
    Start-Sleep -Milliseconds 1200
    OpenRowSelect 0 0 | Out-Null
    Start-Sleep -Milliseconds 1600
    $rp = PickOptionEndsWith $matName
    Write-Host ('  material pick: ' + $rp)
    Ok ($rp -match 'OK') ('loss material picked (' + $matName + ')')
    Start-Sleep -Milliseconds 800
    Write-Host ('  qty: ' + (SetRowInputT 0 0 1 '20'))
    Start-Sleep -Milliseconds 600
    Write-Host ('  price: ' + (SetRowInputT 0 0 2 '10'))
    Start-Sleep -Milliseconds 600
    Write-Host ('  save: ' + (ClickBtn 'btn_save'))
    Start-Sleep -Milliseconds 1500
    Write-Host ('  toast=' + (Txt '.el-message'))
    Start-Sleep -Milliseconds 2200
    $cnt = D (SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss')
    Ok (($cnt -eq ($before + $i))) ('material loss #' + $i + ' created (db=' + $cnt + ')')
  }
}

Step 'audit all DRAFT material losses (row located by code)'
foreach ($c in (SqlList "SELECT code FROM outsource_stock_loss WHERE status='DRAFT' ORDER BY id")) {
  Open '/outsource/stock-loss' 2800
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('material loss row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1200 | Out-Null
    Start-Sleep -Milliseconds 3000
    $st = SqlOne ("SELECT status FROM outsource_stock_loss WHERE code='" + $c + "'")
    Write-Host ('  -> status=' + $st)
    Ok ($st -eq 'AUDITED') ('material loss ' + $c + ' audited')
  }
}

Step 'DB cross-check'
$docs = D (SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss')
$aud = D (SqlOne "SELECT COUNT(*) FROM outsource_stock_loss WHERE status='AUDITED'")
$items = D (SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss_item')
$lossQty = D (SqlOne 'SELECT COALESCE(SUM(quantity),0) FROM outsource_stock_loss_item')
$stockAfter = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=" + $matId + " AND warehouse_id=" + $auxId))
$logs = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE material_id=" + $matId + " AND warehouse_id=" + $auxId + " AND related_bill_no LIKE 'WBS-%'"))
Write-Host ("[DB] docs=$docs audited=$aud items=$items lossQty=$lossQty stock=$stockBefore->$stockAfter wbsLogs=$logs")
Ok (($docs -ge 3)) ('material losses >= 3 (got ' + $docs + ')')
Ok (($aud -eq $docs)) 'all material losses audited'
Ok (($items -ge 3)) ('loss items >= 3 (got ' + $items + ')')
Ok (($lossQty -ge 60)) ('loss quantity >= 60 (got ' + $lossQty + ')')
Ok (($stockAfter -eq ($stockBefore - $createdQty))) ('own warehouse stock == before - qty created this run (' + $stockBefore + ' - ' + $createdQty + ' = ' + $stockAfter + ')')
Ok (($logs -ge 3)) ('loss stock logs with BS- bill no >= 3 (got ' + $logs + ')')
Write-Host ('errs=' + (Errs))
Summary 'P5c material stock loss'
