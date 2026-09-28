# P5b (2026-09-18 full-flow E2E): 物料退货 through the frontend only.
#   2 x 退货退款(REFUND) + 1 x 维修返还(REPAIR), each 1 line from 自有物料一号仓 -> audit.
#   asserts: stock out of the source warehouse, **receivable on the supplier side for REFUND**
#   (P2 2026-09-28: 由"负向应付"改为"对供应商的应收") and NO payable for REPAIR without a repair fee.
# ALL DATA KEPT; rerunnable. ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
# -Force : create 3 fresh returns even when the DB already holds 3 (drills the stock-out path for real).
#          Without it the script stays an idempotent rerun and asserts "nothing changed" instead.
$force = ($args -contains '-Force')
# run-scope anchor: separates "created by THIS run" from historical rows
$runStart = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
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
# 2026-09-21: the source warehouse lists EVERY material of that warehouse -- including rows with 可退数量 = 0
# (自有物料一号仓 has 排线 300/300 plus 测试物料A1..A3 all zero). Blindly filling row 0 therefore produced a
# document for a zero-stock material, and the audit was refused with "库存不足" (see backend_diag.log). Fill the
# qty on the FIRST row that really has enough available stock instead (same fix as ui-e2e-14's SetQtyByMaterial).
function SetQtyFirstAvailable([int]$tblIdx, [int]$need, [string]$qtyVal, [string]$priceVal) {
  $q = B64 $qtyVal; $p = B64 $priceVal
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const Q=T('$q'),P=T('$p');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){const cells=[...rs[i].querySelectorAll('td')].map(c=>(c.innerText||'').replace(/\s+/g,'').trim());let avail=-1;for(const c of cells){const n=parseFloat(c);if(!isNaN(n)&&n>avail)avail=n}if(avail<$need)continue;const ins=[...rs[i].querySelectorAll('input:not([type=hidden])')];if(ins.length<1)continue;const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(ins[0],Q);ins[0].dispatchEvent(new Event('input',{bubbles:true}));ins[0].dispatchEvent(new Event('change',{bubbles:true}));if(ins.length>1){s.call(ins[1],P);ins[1].dispatchEvent(new Event('input',{bubbles:true}));ins[1].dispatchEvent(new Event('change',{bubbles:true}))}return 'OK:row'+i+':avail'+avail}return 'NOROW:'+rs.length})()"
  return (EvalJs $js)
}
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
  return (EvalJs $js)
}

$whAux = ZH 'wh_auxA'
$auxId = [int](SqlOne ("SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1"))
$auxStockBefore = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=" + $auxId))
# 2026-09-19 fix: derive the return partners from the DB instead of a hardcoded ZH name (the old literal no
# longer exists in the dropdown, the pick failed silently, the material list stayed empty and the whole
# -Force drill could never create a document).
# 2026-09-21 (user rule "a material return may only go to a 辅料商 or a 供应商, never to a 供货商"): the rows
# created by earlier probes point at 测试供货商A1 (type=product), which the backend now REFUSES -- so only look
# at rows whose partner is still an allowed type, and fall back to any allowed supplier.
$noProd = "NOT EXISTS (SELECT 1 FROM supplier_type_ref t WHERE t.supplier_id=s.id AND t.type_code='product')"
$refundSup = SqlOne ("SELECT s.name FROM outsource_material_return r JOIN supplier s ON s.id=r.supplier_id WHERE r.return_type='REFUND' AND " + $noProd + " ORDER BY r.id DESC LIMIT 1")
$repairSup = SqlOne ("SELECT s.name FROM outsource_material_return r JOIN supplier s ON s.id=r.supplier_id WHERE r.return_type='REPAIR' AND " + $noProd + " ORDER BY r.id DESC LIMIT 1")
if ($refundSup -eq '') { $refundSup = SqlOne ("SELECT s.name FROM supplier s WHERE " + $noProd + " ORDER BY s.id LIMIT 1") }
if ($repairSup -eq '') { $repairSup = SqlOne ("SELECT s.name FROM supplier s WHERE " + $noProd + " ORDER BY s.id LIMIT 1") }
if ($refundSup -eq '') { $refundSup = (ZH 'val_factory') + '1' }
if ($repairSup -eq '') { $repairSup = (ZH 'val_factory') + '1' }
Write-Host ('[PARTNER] refundSup=' + $refundSup + ' repairSup=' + $repairSup)
$before = D (SqlOne 'SELECT COUNT(*) FROM outsource_material_return')
# P2（2026-09-28）：退货退款改写**对供应商的应收** ⇒ 断言只能用"本次增量"
#   （库里还留着改造前的负向应付行与其它套件造的应收行，绝对值不可作为判据）。
$rcvUnpaidBefore = D (SqlOne "SELECT COALESCE(SUM(unpaid_amount),0) FROM finance_receivable WHERE source_bill_type='OUTSOURCE_MATERIAL_RETURN'")
$payOpenBefore = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='OUTSOURCE_MATERIAL_RETURN' AND status='UNSETTLED'")
# P3（2026-09-28）：维修返回填了维修费 ⇒ 生成对供应商的应付；本脚本给维修返回行也填了单价(5) ⇒ 会落账
$repairFeeAmtBefore = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE source_bill_type LIKE 'OUTSOURCE_MATERIAL_REPAIR%' AND status='UNSETTLED'")
# 2026-09-19 fix: the old script created documents only while fewer than 3 existed, yet still asserted
# "aux stock decreased" - on a rerun (needCreate=0) no UI flow ran at all, so that assertion was a
# permanent false red. No new documents in a rerun can only mean "stock must be unchanged".
$need = if ($force) { 3 } else { [int]([Math]::Max(0, 3 - $before)) }
Write-Host ('[BASE] material returns=' + $before + ' needCreate=' + $need + ' force=' + $force + ' auxStock=' + $auxStockBefore)

if ($need -gt 0) {
  for ($i = 1; $i -le $need; $i++) {
    $typeKey = 'btn_new_refund'
    $supLbl = 'lbl_return_target'
    $typeName = 'REFUND'
    $supName = $refundSup
    if ($i -eq 3) { $typeKey = 'btn_new_repair'; $supLbl = 'lbl_repair_supplier'; $typeName = 'REPAIR'; $supName = $repairSup }
    Step ('material return #' + $i + ' (' + $typeName + ')')
    # 2026-09-27 三级菜单：物料退货 = 关联退料 / 无单退料 / 物料维修退货 三个**独立叶子**（入口随叶子切换）。
    #   本脚本的 REFUND 单**不挂物料订单**（只选供应商 + 源仓 + 料）⇒ 落「无单退料」叶子；
    #   REPAIR 走「物料维修退货」叶子。
    $leafUrl = '/outsource/material-return/unlinked'
    # 2026-09-28（三态）：维修返回不再是独立叶子（/repair 已重定向）⇒ 它在「无单退料」叶子的新增页里选类型；
    #   本脚本的 REPAIR 单不挂订单 ⇒ 走无单叶子（关联叶子会强制要求选订单）。
    if ($typeName -eq 'REPAIR') { $leafUrl = '/outsource/material-return/unlinked' }
    Open $leafUrl 3000
    ClearErrs | Out-Null
    Write-Host ('  open add page: ' + (ClickBtn $typeKey))
    Start-Sleep -Milliseconds 2400
    Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
    if ($typeName -eq 'REPAIR') {
      # 2026-09-28（三态）：先在本页把「退货类型」选成维修返回，供应商字段才切成「维修供应商」
      Write-Host ('  pick type=REPAIR: ' + (SelectLabel 'lbl_mr_type' 'opt_type_repair'))
      Start-Sleep -Milliseconds 1400
    }
    $supPick = SelectLabelContains $supLbl $supName
    Write-Host ('  supplier (' + $supName + '): ' + $supPick)
    Ok ($supPick -match 'OK') ('return partner picked for ' + $typeName)
    Start-Sleep -Milliseconds 1200
    Write-Host ('  source wh: ' + (SelectLabelContains 'lbl_src_wh_out' $whAux))
    Start-Sleep -Milliseconds 2600
    $rows = Rows 0
    Write-Host ('  stock lines=' + $rows.n + ' head=' + ($rows.head -join '|'))
    Ok ($rows.n -ge 1) ('source warehouse materials listed (' + $typeName + ')')
    if ($rows.n -ge 1) {
      # qty 50 + price 5 on the first row that actually has >= 50 available (see the helper's note)
      Write-Host ('  qty+price: ' + (SetQtyFirstAvailable 0 50 '50' '5'))
      Start-Sleep -Milliseconds 600
    }
    Write-Host ('  save: ' + (ClickBtn 'btn_save_draft'))
    Start-Sleep -Milliseconds 1500
    Write-Host ('  toast=' + (Txt '.el-message'))
    Start-Sleep -Milliseconds 2200
    $cnt = D (SqlOne 'SELECT COUNT(*) FROM outsource_material_return')
    Ok (($cnt -eq ($before + $i))) ('material return #' + $i + ' created (db=' + $cnt + ')')
  }
}

Step 'audit all DRAFT material returns (row located by code)'
foreach ($c in (SqlList "SELECT code FROM outsource_material_return WHERE status='DRAFT' ORDER BY id")) {
  # 2026-09-27 三级菜单：三个叶子各自成页 ⇒ 按单据自身的 type + 是否挂订单决定去哪个叶子找它
  #   （三个叶子的默认页签都含草稿：「有效单据」=DRAFT+AUDITED；维修叶子「待返回」含草稿）
  $rt = SqlOne ("SELECT CONCAT(return_type,'|',IFNULL(material_order_id,0)) FROM outsource_material_return WHERE code='" + $c + "'")
  $leafUrl = '/outsource/material-return'
  # 2026-09-28（三态）：**类型不再决定叶子** —— 未挂订单(|0) 都在「无单退料」，挂了订单的在「关联退料」
  #   （维修叶子已下线，旧地址 /repair 会重定向到关联叶子 ⇒ 未挂单的维修返回在那里找不到行）
  if ($rt -like '*|0') { $leafUrl = '/outsource/material-return/unlinked' }
  Open $leafUrl 2800
  Write-Host ('[' + $c + '] type=' + $rt + ' leaf=' + $leafUrl)
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('material return row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1200 | Out-Null
    Start-Sleep -Milliseconds 3000
    $st = SqlOne ("SELECT status FROM outsource_material_return WHERE code='" + $c + "'")
    Write-Host ('  -> status=' + $st)
    Ok ($st -eq 'AUDITED') ('material return ' + $c + ' audited')
  }
}

Step 'DB cross-check'
$ret = D (SqlOne 'SELECT COUNT(*) FROM outsource_material_return')
$aud = D (SqlOne "SELECT COUNT(*) FROM outsource_material_return WHERE status='AUDITED'")
$refundAud = D (SqlOne "SELECT COUNT(*) FROM outsource_material_return WHERE status='AUDITED' AND return_type='REFUND'")
$repairAud = D (SqlOne "SELECT COUNT(*) FROM outsource_material_return WHERE status='AUDITED' AND return_type='REPAIR'")
$auxAfter = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=" + $auxId))
$refundOut = D (SqlOne "SELECT COALESCE(SUM(-change_quantity),0) FROM warehouse_stock_log WHERE change_type='MATERIAL_RETURN_OUT'")
$repairOut = D (SqlOne "SELECT COALESCE(SUM(-change_quantity),0) FROM warehouse_stock_log WHERE change_type='MATERIAL_REPAIR_OUT'")
# P2（2026-09-28）：退货退款的落账方向已改为应收（原来断言"负向应付"）—— 见脚本头注释。
$refundRcv = D (SqlOne "SELECT COALESCE(SUM(unpaid_amount),0) FROM finance_receivable WHERE source_bill_type='OUTSOURCE_MATERIAL_RETURN'")
$refundPayOpen = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='OUTSOURCE_MATERIAL_RETURN' AND status='UNSETTLED'")
# P3（2026-09-28）：维修费应付只在"填了维修费"时产生 ⇒ 只看**未冲销**的金额（别的套件会故意造维修费单，如 ui-e2e-14）
$repairFeeAmt = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE source_bill_type LIKE 'OUTSOURCE_MATERIAL_REPAIR%' AND status='UNSETTLED'")
Write-Host ("[DB] returns=$ret audited=$aud refund=$refundAud repair=$repairAud auxStock=$auxStockBefore->$auxAfter refundOut=$refundOut repairOut=$repairOut refundRcvUnpaid=$rcvUnpaidBefore->$refundRcv refundOpenPayables=$payOpenBefore->$refundPayOpen repairFeeOpen=$repairFeeAmtBefore->$repairFeeAmt")
Ok (($ret -ge 3)) ('material returns >= 3 (got ' + $ret + ')')
# 2026-09-21: the table may also hold **CANCELLED** historical documents (other suites cancel their own
# drafts so they can re-run), so "everything is AUDITED" was wrong. What this sweep actually guarantees is
# that no DRAFT is left behind => assert audited + cancelled == total.
$cancelledRet = D (SqlOne "SELECT COUNT(*) FROM outsource_material_return WHERE status='CANCELLED'")
Ok ((($aud + $cancelledRet) -eq $ret)) ('all non-cancelled material returns audited (audited=' + $aud + ' cancelled=' + $cancelledRet + ' total=' + $ret + ')')
Ok (($refundAud -ge 2)) ('refund returns >= 2 (got ' + $refundAud + ')')
Ok (($repairAud -ge 1)) ('repair returns >= 1 (got ' + $repairAud + ')')
Ok (($refundOut -ge 100)) ('refund stock-out qty >= 100 (got ' + $refundOut + ')')
Ok (($repairOut -ge 50)) ('repair stock-out qty >= 50 (got ' + $repairOut + ')')
# 与库存断言同范式：本次运行没建新单（needCreate=0 的幂等重跑）时，应收只能"不变"
if ($need -gt 0) {
  Ok (($refundRcv -gt $rcvUnpaidBefore)) ('refund generated receivables on the supplier side (unpaid ' + $rcvUnpaidBefore + ' -> ' + $refundRcv + ')')
} else {
  Ok (($refundRcv -eq $rcvUnpaidBefore)) ('rerun with needCreate=0: supplier receivables unchanged by design (' + $rcvUnpaidBefore + ' -> ' + $refundRcv + ')')
}
Ok (($refundPayOpen -eq $payOpenBefore)) ('refund no longer creates payables (open rows ' + $payOpenBefore + ' -> ' + $refundPayOpen + ')')
if ($need -gt 0) {
  Ok (($repairFeeAmt -gt $repairFeeAmtBefore)) ('repair fee (5/unit) generated a supplier payable (open amount ' + $repairFeeAmtBefore + ' -> ' + $repairFeeAmt + ') — P3')
} else {
  Ok (($repairFeeAmt -eq $repairFeeAmtBefore)) ('rerun with needCreate=0: repair-fee payables unchanged by design (' + $repairFeeAmtBefore + ' -> ' + $repairFeeAmt + ')')
}
if ($need -gt 0) {
  Ok (($auxAfter -lt $auxStockBefore)) ('own warehouse material decreased (' + $auxStockBefore + ' -> ' + $auxAfter + ')')
} else {
  Ok (($auxAfter -eq $auxStockBefore)) ('rerun with needCreate=0: own warehouse material unchanged by design (' + $auxStockBefore + ' -> ' + $auxAfter + ')')
}
# run-scope evidence (works with and without -Force): the documents THIS run created must have written
# their own stock-out log rows. Historical sums above cannot prove anything about the current run.
$runRet = D (SqlOne ("SELECT COUNT(*) FROM outsource_material_return WHERE create_time >= '" + $runStart + "'"))
$runOut = D (SqlOne ("SELECT COALESCE(SUM(-change_quantity),0) FROM warehouse_stock_log WHERE change_type IN ('MATERIAL_RETURN_OUT','MATERIAL_REPAIR_OUT') AND (related_bill_id IN (SELECT id FROM outsource_material_return WHERE create_time >= '" + $runStart + "') OR related_bill_no IN (SELECT code FROM outsource_material_return WHERE create_time >= '" + $runStart + "'))"))
if ($runRet -gt 0) {
  Ok (($runOut -gt 0)) ('this run created ' + $runRet + ' return(s); their stock-out log sum = ' + $runOut)
} else {
  Ok $true 'no material return created in this run (idempotent rerun) - run-scope stock-out evidence not applicable'
}
Write-Host ('errs=' + (Errs))
Summary 'P5b material returns'
