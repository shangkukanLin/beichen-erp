# RETIRED 2026-09-24 -- manual material-delivery doc (outsource/delivery) was removed.
# The list/add pages were deleted and POST/PUT create+update endpoints now always reject;
# the replacement is the material warehouse-move doc: /inventory/material-move (backend
# /api/inventory/material-move), covered by verify-material-move.ps1. This script targets the
# removed entry, so it is kept for history only and must NOT be re-added to any suite.

# P4b (2026-09-18 full-flow E2E): prepare the BOM line material, then issue materials to the factories.
#   step 0: the project BOMs use an auto-created 排线 material (排线-MFTESTE2E1/2) that has no stock yet ->
#           buy + receive it via 物料订单 so that issuing never has to go negative.
#   step 1: 8 material-issue docs (物料收发单-发料): 自有物料一号仓 -> <factory>委外仓库, 4 docs per factory.
# ALL DATA KEPT; rerunnable. ASCII ONLY (material/type names resolved from DB, Chinese comes from zh.json).
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
$whAux = ZH 'wh_auxA'
$matBase = ZH 'val_material'
$typeKey = @{}
$typeKey[(ZH 'opt_mt_glass')] = 'opt_mt_glass'
$typeKey[(ZH 'opt_mt_line')] = 'opt_mt_line'
$typeKey[(ZH 'opt_drv')] = 'opt_drv'
function TypeKeyOf([int]$mid) {
  $tn = SqlOne ("SELECT t.type_name FROM material_type t JOIN outsource_material m ON m.material_type_id=t.id WHERE m.id=" + $mid)
  if ($typeKey.ContainsKey($tn)) { return $typeKey[$tn] }
  return ''
}
function StockInAux([int]$mid) {
  return D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=" + $mid + " AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
}

# ---------------- step 0: stock the auto-created 排线 materials (BOM lines) ----------------
Step 'ensure 排线 (project BOM) material stock'
$lineNames = @{}
foreach ($f in @(1, 2)) {
  $mn = SqlOne ("SELECT material_name FROM outsource_material WHERE material_name LIKE '%MFTESTE2E" + $f + "' LIMIT 1")
  if (-not $mn) { Write-Host ('  WARN no 排线 material for project ' + $f); continue }
  $lineNames[$f] = $mn
  $mid = [int](SqlOne ("SELECT id FROM outsource_material WHERE material_name='" + $mn + "'"))
  $have = StockInAux $mid
  Write-Host ('  ' + $mn + ' (id=' + $mid + ') own-wh stock=' + $have)
  if ($have -ge 200) { Write-Host '  already stocked -> skip'; continue }
  Open '/outsource/material-order' 2600
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 2000
  SelectLabelContains 'lbl_supplier' ((ZH 'val_factory') + $f) | Out-Null
  Start-Sleep -Milliseconds 1000
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1300
  PickOptionContains (ZH 'opt_mt_line') | Out-Null
  Start-Sleep -Milliseconds 900
  OpenRowSelect 0 1 | Out-Null
  Start-Sleep -Milliseconds 1500
  $rp = PickOptionEndsWith $mn
  Write-Host ('  pick 排线 material: ' + $rp)
  Ok ($rp -match 'OK') ('排线 material picked for order (' + $mn + ')')
  Start-Sleep -Milliseconds 600
  SetRowInput 0 2 '600' | Out-Null
  SetRowInput 0 3 '5' | Out-Null
  Start-Sleep -Milliseconds 500
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 2800
  $c = SqlOne 'SELECT code FROM outsource_material_order ORDER BY id DESC LIMIT 1'
  Open '/outsource/material-order' 2400
  $idx = [int](FindRow $c)
  if ($idx -ge 0) {
    ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1000 | Out-Null
    Start-Sleep -Milliseconds 2600
  }
  Open '/outsource/material-order/delivery' 3000
  $idx2 = [int](FindRow $c)
  if ($idx2 -ge 0) {
    ClickRowBtnContains $idx2 (ZH 'btn_receive') | Out-Null
    Start-Sleep -Milliseconds 3000
    DialogOpenSelect 0 | Out-Null
    Start-Sleep -Milliseconds 1400
    PickOptionContains $whAux | Out-Null
    Start-Sleep -Milliseconds 700
    DialogSetInput 1 '600' | Out-Null
    Start-Sleep -Milliseconds 700
    ClickDialogBtn 'btn_confirm_deliver' | Out-Null
    Start-Sleep -Milliseconds 3000
  }
  $have2 = StockInAux $mid
  Write-Host ('  ' + $mn + ' stock after receipt=' + $have2)
  Ok (($have2 -ge 200)) ('排线 material stocked in own warehouse (' + $mn + ' = ' + $have2 + ')')
}

# ---------------- step 1: 8 issue docs ----------------
Step 'material issues (发料) to factories'
$issueBefore = D (SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE delivery_type='DELIVERY'")
$skip = ($issueBefore -ge 8)
if ($skip) { Write-Host ('NOTE: ' + $issueBefore + ' issue docs already exist -> creation skipped') }
$glass = $matBase + '1'
$drv = $matBase + '3'
$plan = @(
  @{ f = 1; name = $glass; q = 400 },
  @{ f = 1; name = $drv; q = 400 },
  @{ f = 1; name = $lineNames[1]; q = 300 },
  @{ f = 1; name = $matBase + '2'; q = 200 },
  @{ f = 2; name = $glass; q = 400 },
  @{ f = 2; name = $drv; q = 400 },
  @{ f = 2; name = $lineNames[2]; q = 300 },
  @{ f = 2; name = $matBase + '2'; q = 200 }
)
$k = 0
if (-not $skip) {
  foreach ($pl in $plan) {
    $k++
    if (-not $pl.name) { Write-Host ('  WARN missing material name for plan #' + $k); continue }
    $mid = [int](SqlOne ("SELECT id FROM outsource_material WHERE material_name='" + $pl.name + "' LIMIT 1"))
    $tkey = TypeKeyOf $mid
    Write-Host ('issue#' + $k + ': factory ' + $pl.f + ' material ' + $pl.name + ' (id=' + $mid + ', type=' + $tkey + ') qty ' + $pl.q)
    Open '/outsource/delivery' 2600
    ClearErrs | Out-Null
    ClickBtn 'btn_new' | Out-Null
    Start-Sleep -Milliseconds 2000
    OpenSelectIdx 1 | Out-Null
    Start-Sleep -Milliseconds 1600
    Write-Host ('  factory: ' + (PickOptionContains ((ZH 'val_factory') + $pl.f)))
    Start-Sleep -Milliseconds 900
    OpenSelectIdx 2 | Out-Null
    Start-Sleep -Milliseconds 1400
    Write-Host ('  from wh: ' + (PickOptionContains $whAux))
    Start-Sleep -Milliseconds 900
    OpenSelectIdx 3 | Out-Null
    Start-Sleep -Milliseconds 1400
    Write-Host ('  to wh: ' + (PickOptionContains ((ZH 'val_factory') + $pl.f)))
    Start-Sleep -Milliseconds 900
    ClickBtn 'btn_add_material_plus' | Out-Null
    Start-Sleep -Milliseconds 1000
    OpenRowSelect 0 0 | Out-Null
    Start-Sleep -Milliseconds 1300
    Write-Host ('  type: ' + (PickOptionContains (ZH $tkey)))
    Start-Sleep -Milliseconds 900
    OpenRowSelect 0 1 | Out-Null
    Start-Sleep -Milliseconds 1500
    $rp = PickOptionEndsWith $pl.name
    Write-Host ('  material pick: ' + $rp)
    Ok (($rp -match 'OK') -and ($mid -gt 0)) ('issue material picked (' + $pl.name + ')')
    Start-Sleep -Milliseconds 600
    SetRowInput 0 3 ('' + $pl.q) | Out-Null
    Start-Sleep -Milliseconds 500
    ClickBtn 'btn_submit_confirm' | Out-Null
    Start-Sleep -Milliseconds 2800
    Write-Host ('  msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)'))
    $cnt = D (SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE delivery_type='DELIVERY'")
    Ok (($cnt -eq ($issueBefore + $k))) ('issue doc #' + $k + ' created (db=' + $cnt + ')')
  }
}

Step 'audit all DRAFT issue docs (row located by code)'
foreach ($c in (SqlList "SELECT code FROM outsource_delivery WHERE delivery_type='DELIVERY' AND status='DRAFT' ORDER BY id")) {
  Open '/outsource/delivery' 2400
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('issue doc row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1100 | Out-Null
    Start-Sleep -Milliseconds 2800
    $st = SqlOne ("SELECT status FROM outsource_delivery WHERE code='" + $c + "'")
    Write-Host ('  -> status=' + $st)
    Ok ($st -eq 'AUDITED') ('issue doc ' + $c + ' audited')
  }
}

Step 'DB cross-check'
$issues = D (SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE delivery_type='DELIVERY'")
$issuesAud = D (SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE delivery_type='DELIVERY' AND status='AUDITED'")
$issueItems = D (SqlOne "SELECT COUNT(*) FROM outsource_delivery_item i JOIN outsource_delivery d ON d.id=i.delivery_id WHERE d.delivery_type='DELIVERY'")
$auxStock = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
$outStock = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock s JOIN warehouse w ON w.id=s.warehouse_id WHERE s.material_id IS NOT NULL AND w.warehouse_category='OUTSOURCE'")
$outRows = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock s JOIN warehouse w ON w.id=s.warehouse_id WHERE s.material_id IS NOT NULL AND w.warehouse_category='OUTSOURCE'")
Write-Host ("[DB] issues=$issues audited=$issuesAud items=$issueItems ownStock=$auxStock outsourceStock=$outStock outsourceRows=$outRows")
Ok (($issues -ge 8)) ('issue docs >= 8 (got ' + $issues + ')')
Ok (($issuesAud -eq $issues)) 'all issue docs audited'
Ok (($issueItems -ge 8)) ('issue items >= 8 (got ' + $issueItems + ')')
Ok (($outRows -ge 3)) ('outsource warehouses hold >= 3 material rows (got ' + $outRows + ')')
Ok (($outStock -gt 0)) ('outsource material stock > 0 (got ' + $outStock + ')')
Write-Host ('errs=' + (Errs))
Summary 'P4b material issue'
