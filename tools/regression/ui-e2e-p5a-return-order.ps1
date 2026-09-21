# P5a (2026-09-18 full-flow E2E): 加工退货 - 不良退货(DEFECT) x2 through the frontend only.
#   #1 linked to a work order (BOM snapshot forced by the order), #2 unlinked (pick snapshot manually).
#   audit -> assert: finished stock out (OUTSOURCE_RETURN_OUT), BOM materials back into the factory warehouse,
#                    negative payable generated.
# ALL DATA KEPT; rerunnable (skips creation when 2 defect returns already exist). ASCII ONLY.
#
# !! OBSOLETE BY DESIGN (2026-09-21, user decision): a DEFECT return DOCUMENT can no longer be created
#    from this page - the button is gone and the backend rejects it ("go to 成品收货"). The action was
#    unified onto the receipt table: with a work order -> that order's 加工退货 red-reversal; without one
#    -> 无单加工退货 on the receipt list. Replacement coverage (API level, data-driven, repeatable):
#      verify-no-order-return.ps1   (create/audit/un-audit/delete + the DEFECT document being rejected)
#      verify-fix-f7-64-74.ps1      (on-order red-reversal symmetry)
#      verify-delivery-menu.ps1 §②/§④/⑨ (the UI entries)
#    Refresh this file only if you deliberately re-open the independent-document path.
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
# NOTE: lib helpers target the LAST visible table; on this page the read-only 「拆解后的退货物料」table
# becomes visible once a snapshot is applied -> the editable product table is table 0. Hence T-variants.
function OpenRowSelectT([int]$tblIdx, [int]$rowIdx, [int]$selIdx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const scs=[...rs[$rowIdx].querySelectorAll('.el-select')];if(scs.length<=$selIdx)return 'NOSELECT:'+scs.length;const inp=scs[$selIdx].querySelector('input');(inp||scs[$selIdx]).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||scs[$selIdx]).click();return 'OK'})()"
  return (EvalJs $js)
}
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
  return (EvalJs $js)
}

$before = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='DEFECT'")
$need = [int]([Math]::Max(0, 3 - $before))
Write-Host ('[BASE] defect returns=' + $before + ' needCreate=' + $need)
if ($need -le 0) { Write-Host 'NOTE: 3 defect returns already exist -> creation skipped' }

if ($need -gt 0) {
  for ($i = 1; $i -le $need; $i++) {
    Step ('defect return #' + $i)
    Open '/outsource/return-order' 3000
    ClearErrs | Out-Null
    # type tabs: click the 不良退货 tab first, then the 新增不良退货 button
    ClickText (ZH 'val_defect_type') | Out-Null
    Start-Sleep -Milliseconds 1200
    Write-Host ('  new defect return: ' + (ClickBtn 'btn_new_defect_return'))
    Start-Sleep -Milliseconds 2400
    Write-Host ('  factory: ' + (SelectLabelContains 'lbl_factory' ((ZH 'val_factory') + '1')))
    Start-Sleep -Milliseconds 1200
    Write-Host ('  out wh: ' + (SelectLabelContains 'lbl_out_finished_wh' (ZH 'pfx_wh')))
    Start-Sleep -Milliseconds 1200
    $linked = ($i -eq 1)
    $prodName = (ZH 'val_proj') + 'E2E1'
    if ($linked) {
      # linked to a work order -> the snapshot is forced by that order (the snapshot select is disabled)
      # NOTE: command-argument mode does NOT evaluate '+' -> the whole SQL must be parenthesised,
      # otherwise SqlOne only receives the first fragment (syntax error -> silent empty result).
      $ordCode = SqlOne ("SELECT o.code FROM outsource_order o WHERE o.factory_id=(SELECT id FROM supplier WHERE name='" + (ZH 'val_factory') + "1') AND o.status='PRODUCING' ORDER BY o.id DESC LIMIT 1")
      # the product dropdown only lists the products that THIS order used -> resolve it from the DB
      $prodName = SqlOne ("SELECT p.product_name FROM outsource_order_product p JOIN outsource_order o ON o.id=p.order_id WHERE o.code='" + $ordCode + "' ORDER BY p.id LIMIT 1")
      Write-Host ('  linked order: ' + $ordCode + ' product=' + $prodName)
      OpenSelect 'lbl_rel_order' | Out-Null
      Start-Sleep -Milliseconds 1500
      Write-Host ('  pick order: ' + (PickOptionContains $ordCode))
      # the product/snapshot lists are loaded from the selected order -> wait for them
      Start-Sleep -Milliseconds 3200
    }
    Write-Host ('  add product row: ' + (ClickBtn 'btn_add_product_plus'))
    Start-Sleep -Milliseconds 1400
    Write-Host ('  open product sel: ' + (OpenRowSelectT 0 0 0))
    Start-Sleep -Milliseconds 1800
    $rp = PickOptionContains $prodName
    Write-Host ('  product pick: ' + $rp)
    Ok ($rp -match 'OK') ('return product picked (' + $rp + ')')
    Start-Sleep -Milliseconds 1200
    if (-not $linked) {
      # unlinked -> pick the BOM snapshot explicitly (v1)
      Write-Host ('  open snapshot sel: ' + (OpenRowSelectT 0 0 1))
      Start-Sleep -Milliseconds 1600
      $rs = PickOptionContains 'v1'
      Write-Host ('  snapshot pick: ' + $rs)
      Ok ($rs -match 'OK') ('BOM snapshot picked (' + $rs + ')')
      Start-Sleep -Milliseconds 900
    } else {
      Write-Host '  (linked: BOM snapshot forced by the order, select skipped)'
    }
    Write-Host ('  open quality sel: ' + (OpenRowSelectT 0 0 2))
    Start-Sleep -Milliseconds 1200
    Write-Host ('  quality pick: ' + (PickOptionContains (ZH 'opt_q_a')))
    Start-Sleep -Milliseconds 900
    Write-Host ('  qty: ' + (SetRowInputT 0 0 3 '10'))
    Start-Sleep -Milliseconds 700
    Write-Host ('  save: ' + (ClickBtn 'btn_save'))
    Start-Sleep -Milliseconds 1400
    $toast = Txt '.el-message'
    Write-Host ('  toast=' + $toast)
    Start-Sleep -Milliseconds 2200
    Write-Host ('  msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)'))
    Ok (($toast.Length -gt 0)) ('save feedback captured (' + $toast + ')')
    $cnt = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='DEFECT'")
    Ok (($cnt -eq ($before + $i))) ('defect return #' + $i + ' created (db=' + $cnt + ')')
  }
}

Step 'top-up negative outsource-warehouse stock (business workaround for finding I17)'
# I17: a defect return linked to a work order pushes the BOM materials back into the factory warehouse,
# but that path uses the STRICT stock guard (quantity+delta>=0) -> a negative row blocks even the inbound
# movement. The business answer is 补料 (materials other-io IN), which is what we do here.
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
$neg = @(SqlLines "SELECT w.warehouse_name, m.material_name, s.quantity, t.type_name FROM warehouse_stock s JOIN warehouse w ON w.id=s.warehouse_id JOIN outsource_material m ON m.id=s.material_id JOIN material_type t ON t.id=m.material_type_id WHERE w.warehouse_category='OUTSOURCE' AND s.quantity < 0")
Write-Host ('negative outsource stock rows = ' + $neg.Count)
$typeMap = @{}
$typeMap[(ZH 'opt_mt_glass')] = 'opt_mt_glass'
$typeMap[(ZH 'opt_mt_line')] = 'opt_mt_line'
$typeMap[(ZH 'opt_drv')] = 'opt_drv'
foreach ($ln in $neg) {
  $p = $ln -split "`t"
  $whName = $p[0].Trim(); $matName = $p[1].Trim(); $qty = [decimal]$p[2].Trim(); $tName = $p[3].Trim()
  $add = [int]([Math]::Abs($qty)) + 300
  $tKey = ''
  if ($typeMap.ContainsKey($tName)) { $tKey = $typeMap[$tName] }
  Write-Host ('  top-up ' + $matName + ' (' + $tName + ' -> ' + $tKey + ') in ' + $whName + ' by ' + $add)
  $codeBefore = SqlOne 'SELECT COALESCE(MAX(id),0) FROM outsource_other_io'
  Open '/outsource/other-io/add' 3000
  ClearErrs | Out-Null
  Write-Host ('    wh: ' + (SelectLabelContains 'lbl_warehouse' $whName))
  Start-Sleep -Milliseconds 1300
  # material rows are filtered by TYPE -> must pick the type first (this is what failed before)
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1400
  $rt = PickOptionContains (ZH $tKey)
  Write-Host ('    type pick: ' + $rt)
  Start-Sleep -Milliseconds 900
  OpenRowSelect 0 1 | Out-Null
  Start-Sleep -Milliseconds 1600
  $rp = PickOptionEndsWith $matName
  Write-Host ('    material pick: ' + $rp)
  Ok ((($rp -match 'OK') -and ($rt -match 'OK'))) ('top-up material picked (' + $matName + ')')
  Start-Sleep -Milliseconds 700
  SetRowInput 0 3 ('' + $add) | Out-Null
  Start-Sleep -Milliseconds 600
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 1400
  Write-Host ('    toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 1600
  $c = SqlOne 'SELECT code FROM outsource_other_io ORDER BY id DESC LIMIT 1'
  $codeAfter = SqlOne 'SELECT COALESCE(MAX(id),0) FROM outsource_other_io'
  Ok (([decimal]$codeAfter -gt [decimal]$codeBefore)) ('top-up doc created (' + $codeBefore + ' -> ' + $codeAfter + ', ' + $c + ')')
  # guard against silently creating an EMPTY line doc (that happened while the pick helper was missing)
  $lineOk = D (SqlOne ("SELECT COUNT(*) FROM outsource_other_io_item i JOIN outsource_other_io d ON d.id=i.other_io_id WHERE d.id=" + $codeAfter + " AND i.outsource_material_id IS NOT NULL"))
  Ok (($lineOk -ge 1)) ('top-up doc has a real material line (' + $lineOk + ')')
  Open '/outsource/other-io' 2600
  $idx = [int](FindRow $c)
  if ($idx -ge 0) {
    ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
    Start-Sleep -Milliseconds 1300
    ConfirmBox 1100 | Out-Null
    Start-Sleep -Milliseconds 2600
  }
  $left = D (SqlOne ("SELECT COALESCE(MIN(quantity),0) FROM warehouse_stock s JOIN warehouse w ON w.id=s.warehouse_id JOIN outsource_material m ON m.id=s.material_id WHERE w.warehouse_category='OUTSOURCE' AND m.material_name='" + $matName + "'"))
  Write-Host ('    min stock now = ' + $left + ' (doc ' + $c + ')')
}
$negLeft = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock s JOIN warehouse w ON w.id=s.warehouse_id WHERE w.warehouse_category='OUTSOURCE' AND s.quantity < 0")
Ok (($negLeft -eq 0)) ('no negative outsource stock left (' + $negLeft + ')')

Step 'audit all DRAFT defect returns (row located by code)'
foreach ($c in (SqlList "SELECT code FROM outsource_return_order WHERE return_type='DEFECT' AND status='DRAFT' ORDER BY id")) {
  Open '/outsource/return-order' 2800
  ClickText (ZH 'val_defect_type') | Out-Null
  Start-Sleep -Milliseconds 1200
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('defect return row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1200 | Out-Null
    Start-Sleep -Milliseconds 3000
    $st = SqlOne ("SELECT status FROM outsource_return_order WHERE code='" + $c + "'")
    Write-Host ('  -> status=' + $st)
    Ok ($st -eq 'AUDITED') ('defect return ' + $c + ' audited')
  }
}

Step 'DB cross-check'
$ret = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='DEFECT'")
$retAud = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='DEFECT' AND status='AUDITED'")
$retItems = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order_item i JOIN outsource_return_order r ON r.id=i.return_order_id WHERE r.return_type='DEFECT'")
$qty = D (SqlOne "SELECT COALESCE(SUM(p.quantity),0) FROM outsource_return_order_product p JOIN outsource_return_order r ON r.id=p.return_order_id WHERE r.return_type='DEFECT' AND r.status='AUDITED'")
$outLogs = D (SqlOne "SELECT COALESCE(SUM(-change_quantity),0) FROM warehouse_stock_log WHERE change_type='OUTSOURCE_RETURN_OUT'")
$matBack = D (SqlOne "SELECT COALESCE(SUM(change_quantity),0) FROM warehouse_stock_log WHERE change_type='RETURN_IN' AND change_quantity > 0")
$negPay = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE source_bill_type LIKE 'OUTSOURCE_RETURN%'")
Write-Host ("[DB] defectReturns=$ret audited=$retAud items=$retItems returnedQty=$qty returnOutLogs=$outLogs materialBackLogs=$matBack returnPayableSum=$negPay")
Ok (($ret -ge 3)) ('defect returns >= 3 (got ' + $ret + ')')
# CANCELLED docs are void (they were never audited) -> the real invariant is "no defect return left draft".
# (2026-09-18: the old form compared COUNT(all) == COUNT(audited) and went red once other scripts had
#  cancelled a few defect returns - a test-asset issue, not a product defect.)
$retOpen = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='DEFECT' AND status NOT IN ('AUDITED','CANCELLED')")
Ok (($retOpen -eq 0)) ('no defect return left unaudited (cancelled counted as void, open=' + $retOpen + ')')
Ok (($retAud -ge 3)) ('audited defect returns >= 3 (got ' + $retAud + ')')
Ok (($retItems -ge 1)) ('BOM material lines written back >= 1 (got ' + $retItems + ')')
Ok (($qty -gt 0)) ('returned finished-goods qty > 0 (got ' + $qty + ')')
Ok (($outLogs -ge $qty)) ('finished goods left stock by OUTSOURCE_RETURN_OUT (' + $outLogs + ' >= ' + $qty + ')')
Ok (($matBack -gt 0)) ('BOM materials returned into the factory warehouse (got ' + $matBack + ')')
Ok (($negPay -lt 0)) ('negative payable generated for the return (sum=' + $negPay + ')')
Write-Host ('errs=' + (Errs))
Summary 'P5a defect return orders'
