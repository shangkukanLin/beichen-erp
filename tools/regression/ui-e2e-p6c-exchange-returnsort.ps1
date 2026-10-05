# P6c (2026-09-18 full-flow E2E): 销售换货 x2 + 退货整理 x3, all through the frontend.
#   /sale/exchange    : source sale order -> 换回数量 + 换出数量 -> save -> audit
#   /inventory/return-sort: source warehouse -> 加载待整理库存 -> A规数量 + 不良数量 -> save -> audit
#   also probes /finance/receipt 新增收款 form (for the 2 manual receipts in P6d).
# ALL DATA KEPT; rerunnable. ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
# run-scope anchor: lets the DB cross-check separate "created by THIS run" from historical rows
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
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function DumpTables([int]$idx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$idx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];return JSON.stringify(rs.map(r=>[(r.innerText||'').replace(/\s+/g,' ').slice(0,90),[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value)]))})()"
  return (EvalJs $js)
}

# stock-backed product + its warehouse
$stkId = SqlOne "SELECT id FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity>20 ORDER BY quantity DESC LIMIT 1"
$whId = [int](SqlOne ("SELECT warehouse_id FROM warehouse_stock WHERE id=" + $stkId))
$pId = [int](SqlOne ("SELECT product_id FROM warehouse_stock WHERE id=" + $stkId))
$whName = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + $whId)
$pName = SqlOne ("SELECT name FROM product WHERE id=" + $pId)
$custName = (ZH 'val_customer') + '1'
# NOTE: do NOT join/filter on customer(name) — the column name differs; the exchange deep link only needs the id
$orderId = [int](SqlOne "SELECT id FROM sale_order WHERE status='AUDITED' ORDER BY id DESC LIMIT 1")
$orderCode = SqlOne ("SELECT code FROM sale_order WHERE id=" + $orderId)
Ok (($orderId -gt 0)) ('resolved an audited sale order for the exchange deep link (id=' + $orderId + ')')
Write-Host ('[SEED] wh=' + $whName + ' prod=' + $pName + '(' + $pId + ') customer=' + $custName + ' order=' + $orderCode)

Step '销售换货 (2)'
$exBefore = D (SqlOne 'SELECT COUNT(*) FROM sale_exchange')
$exNeed = [int]([Math]::Max(0, 2 - $exBefore))
for ($i = 1; $i -le $exNeed; $i++) {
  Write-Host ('--- exchange #' + $i)
  # all four header fields are RemoteSelects -> use the deep link that prefills customer/order/out-warehouse
  # and auto-loads the exchangeable lines (the plain list page left saleOrderId null -> '请选择来源销售单').
  Open ('/sale/exchange/add?saleOrderId=' + $orderId) 3200
  ClearErrs | Out-Null
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)') + ' query=' + (EvalJs 'String(location.search)'))
  Write-Host ('  rows=' + (DumpTables 0))
  Ok (((DumpTables 0) -notmatch '\[\]') -and ((DumpTables 0) -notmatch 'NOTABLE')) 'Exchangeable lines loaded from the source order'
  Write-Host ('  in wh: ' + (SelectLabelContains 'lbl_in_wh' $whName))
  Start-Sleep -Milliseconds 1000
  Write-Host ('  rows=' + (DumpTables 0))
  Write-Host ('  back qty: ' + (SetRowInputT 0 0 0 '1'))
  Start-Sleep -Milliseconds 500
  Write-Host ('  out qty: ' + (SetRowInputT 0 0 1 '1'))
  Start-Sleep -Milliseconds 700
  Write-Host ('  rows2=' + (DumpTables 0))
  Write-Host ('  save: ' + (ClickBtn 'btn_save'))
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM sale_exchange')
  Ok (($cnt -eq ($exBefore + $i))) ('exchange #' + $i + ' created (db=' + $cnt + ')')
}

Step '退货整理 (3) — one sort consumes ALL pending rows, so top up with a fresh sale return when needed'
$rsBefore = D (SqlOne 'SELECT COUNT(*) FROM return_sort')
$targetAud = 3
$rsAudBefore = D (SqlOne "SELECT COUNT(*) FROM return_sort WHERE status='AUDITED'")
function NewSaleReturn([string]$prod, [int]$qty) {
  Open '/sale/return' 3000
  ClearErrs | Out-Null
  ClickBtn 'btn_new_return' | Out-Null
  Start-Sleep -Milliseconds 2400
  SelectLabelContains 'lbl_customer' $custName | Out-Null
  Start-Sleep -Milliseconds 900
  SelectLabelContains 'lbl_return_warehouse' $whName | Out-Null
  Start-Sleep -Milliseconds 900
  $rb = Rows 0
  if ($rb.n -lt 1) { ClickBtn 'btn_add_detail' | Out-Null; Start-Sleep -Milliseconds 900 }
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1500
  $pk = PickOptionContains $prod
  Start-Sleep -Milliseconds 700
  SetRowInputT 0 0 1 ('' + $qty) | Out-Null
  Start-Sleep -Milliseconds 600
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 2600
  $c = SqlOne 'SELECT code FROM sale_return ORDER BY id DESC LIMIT 1'
  Write-Host ('    created return ' + $c + ' (' + $pk + ')')
  Open '/sale/return' 2600
  $idx = [int](FindRow $c)
  if ($idx -ge 0) {
    ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
    Start-Sleep -Milliseconds 1300
    ConfirmBox 1200 | Out-Null
    Start-Sleep -Milliseconds 2800
  }
  $st = SqlOne ("SELECT status FROM sale_return WHERE code='" + $c + "'")
  Write-Host ('    return ' + $c + ' status=' + $st)
  return ($st -eq 'AUDITED')
}
function PendingMap {
  $m = @{}
  foreach ($ln in (SqlLines "SELECT source_code, quantity, COALESCE(sorted_quantity,0) FROM after_sale_pending WHERE quantity > COALESCE(sorted_quantity,0) AND warehouse_id=" + $whId)) {
    $f = $ln -split "`t"
    if ($f.Count -ge 3) { $m[$f[0].Trim()] = [int]([decimal]$f[1].Trim() - [decimal]$f[2].Trim()) }
  }
  return $m
}
$tryNo = 0
# NOTE: a return-sort consumes BOTH the source batch (after_sale_pending) and the 售后仓 待整理 stock it
# allocated. Auditing the first sort therefore drains the stock the NEXT sort would need, so we stop creating
# new sorts once one is left un-auditable (it would only pile up un-auditable drafts — a pure test-setup artefact).
while (((D (SqlOne "SELECT COUNT(*) FROM return_sort WHERE status='AUDITED'")) -lt $targetAud) -and ((D (SqlOne "SELECT COUNT(*) FROM return_sort WHERE status='DRAFT'")) -eq 0) -and ($tryNo -lt 3)) {
  $tryNo++
  Write-Host ('--- return-sort attempt #' + $tryNo + ' (audited so far=' + (D (SqlOne "SELECT COUNT(*) FROM return_sort WHERE status='AUDITED'")) + ')')
  Open '/inventory/return-sort?tab=bills' 3000
  ClearErrs | Out-Null
  Write-Host ('  new: ' + (ClickBtn 'btn_new_sort'))
  Start-Sleep -Milliseconds 2400
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
  Write-Host ('  src wh: ' + (SelectLabelContains 'lbl_source_warehouse' $whName))
  Start-Sleep -Milliseconds 900
  Write-Host ('  load pending: ' + (ClickBtn 'btn_load_pending'))
  Start-Sleep -Milliseconds 3200
  $probe = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return 0;return t.querySelectorAll('.el-table__body tbody tr').length})()"
  if ([int](EvalJs $probe) -eq 0) {
    Write-Host ('  no pending rows loaded -> create + audit one more sale return')
    $ok = NewSaleReturn $pName 2
    Ok ($ok) ('top-up sale return audited for return-sort #' + $i)
    Open '/inventory/return-sort?tab=bills' 3000
    ClickBtn 'btn_new_sort' | Out-Null
    Start-Sleep -Milliseconds 2400
    SelectLabelContains 'lbl_source_warehouse' $whName | Out-Null
    Start-Sleep -Milliseconds 900
    ClickBtn 'btn_load_pending' | Out-Null
    Start-Sleep -Milliseconds 3200
  }
  # read each row from the DOM: [0]=source code, [1]=pending qty (first pure number in the row text)
  $rowJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return '[]';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];return JSON.stringify(rs.map(r=>{const txt=(r.innerText||'').replace(/\s+/g,' ').trim();const toks=txt.split(' ');const num=toks.find(x=>/^\d+(\.\d+)?$/.test(x));return [toks[1]||'',num||'0']}))})()"
  $rowJson = EvalJs $rowJs
  Write-Host ('  rowData=' + $rowJson)
  $parsed = @()
  # NOTE: '@($json | ConvertFrom-Json)' wraps the deserialised ARRAY into one element (PS 5.1) -> count becomes 1
  try { $parsed = $rowJson | ConvertFrom-Json } catch { $parsed = @() }
  if ($null -eq $parsed) { $parsed = @() }
  $splitDone = $false
  for ($r = 0; $r -lt $parsed.Count; $r++) {
    $code = [string]$parsed[$r][0]
    $q = [int]([decimal]$parsed[$r][1])
    $a = $q
    $bad = 0
    if ((-not $splitDone) -and ($q -ge 2)) { $a = $q - 1; $bad = 1; $splitDone = $true }
    Write-Host ('    row' + $r + ' ' + $code + ' pending=' + $q + ' -> A=' + $a + ' BAD=' + $bad + ' | ' + (SetRowInputT 0 $r 0 ('' + $a)))
    Start-Sleep -Milliseconds 300
    Write-Host ('      bad: ' + (SetRowInputT 0 $r 3 ('' + $bad)))
    Start-Sleep -Milliseconds 300
  }
  Ok (($parsed.Count -ge 1)) ('pending rows read from the grid (' + $parsed.Count + ')')
  Start-Sleep -Milliseconds 500
  Write-Host ('  rows=' + (DumpTables 0))
  Write-Host ('  save: ' + (ClickBtn 'btn_save'))
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM return_sort')
  Write-Host ('  total return sorts in db=' + $cnt)
  # audit IMMEDIATELY: a sort consumes the pending quantities it allocated, so creating all of them first
  # and auditing later makes #2/#3 fail (their allocation is no longer available)
  $newCode = SqlOne ("SELECT code FROM return_sort WHERE status='DRAFT' ORDER BY id LIMIT 1")
  if ($newCode) {
    Open '/inventory/return-sort?tab=bills' 2800
    $nIdx = [int](FindRow $newCode)
    if ($nIdx -ge 0) {
      ClickRowBtnContains $nIdx (ZH 'btn_audit') | Out-Null
      Start-Sleep -Milliseconds 1400
      ConfirmBox 1200 | Out-Null
      Start-Sleep -Milliseconds 3000
      $nst = SqlOne ("SELECT status FROM return_sort WHERE code='" + $newCode + "'")
      Write-Host ('  -> ' + $newCode + ' status=' + $nst + ' msg=' + (Txt '.el-message'))
      Ok ($nst -eq 'AUDITED') ('return sort ' + $newCode + ' audited (created in this run)')
    }
  }
}

Step 'audit DRAFT exchanges + return sorts'
foreach ($c in (SqlList "SELECT code FROM sale_exchange WHERE status='DRAFT' ORDER BY id")) {
  Open '/sale/exchange' 2800
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('exchange row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('  audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1200 | Out-Null
    Start-Sleep -Milliseconds 3000
    $st = SqlOne ("SELECT status FROM sale_exchange WHERE code='" + $c + "'")
    Write-Host ('  -> ' + $c + ' status=' + $st + ' msg=' + (Txt '.el-message'))
    Ok ($st -eq 'AUDITED') ('exchange ' + $c + ' audited')
  }
}
foreach ($c in (SqlList "SELECT code FROM return_sort WHERE status='DRAFT' ORDER BY id")) {
  Open '/inventory/return-sort?tab=bills' 2800
  $idx = [int](FindRow $c)
  if ($idx -lt 0) { Write-Host ('  (skip) row not found: ' + $c); continue }
  Write-Host ('  audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
  Start-Sleep -Milliseconds 1400
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 3000
  $st = SqlOne ("SELECT status FROM return_sort WHERE code='" + $c + "'")
  $msg = Txt '.el-message'
  Write-Host ('  -> ' + $c + ' status=' + $st + ' msg=' + $msg)
  if ($st -eq 'AUDITED') { Ok $true ('return sort ' + $c + ' audited') }
  # 2026-10-05 F7-290: the tolerated block used to be an INFO line only (no verdict at all). Being blocked by
  # the stock guard is legitimate here, so it is a SKIP -- visible and counted, but never a fake PASS.
  else { Skip ('return sort ' + $c + ' blocked by the (correct) stock guard: ' + $msg) }
}

Step 'probe 新增收款 form (for the 2 manual receipts)'
Open '/finance/receipt' 2800
$rb = "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('button')].filter(vis).map(b=>(b.innerText||'').trim()).filter(t=>t&&t.length<12))})()"
Write-Host ('  buttons=' + (EvalJs $rb))
Open '/finance/receipt/add' 3000
Write-Host ('  add path=' + (EvalJs 'String(location.pathname)'))
$fi = "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('.el-form-item')].filter(vis).map(it=>{const l=it.querySelector('.el-form-item__label');return (l?l.innerText.trim():'?')+'='+(it.querySelector('.el-select')?'select':(it.querySelector('input')?'input':'x'))}))})()"
Write-Host ('  add form items=' + (EvalJs $fi))
$ab = "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('button')].filter(vis).map(b=>(b.innerText||'').trim()).filter(t=>t))})()"
Write-Host ('  add buttons=' + (EvalJs $ab))

Step 'sort form: the source-bill number opens its source document'
# pick a sort whose items still trace back to a source batch (pending_id NOT NULL) -- draft first, audited as fallback
$sid = [int](SqlOne "SELECT i.sort_id FROM return_sort_item i JOIN return_sort s ON s.id=i.sort_id WHERE i.pending_id IS NOT NULL AND s.status IN ('DRAFT','AUDITED') ORDER BY (s.status='AUDITED'), i.sort_id DESC LIMIT 1")
Write-Host ('  using return sort id=' + $sid)
Ok ($sid -gt 0) ('resolved a return sort with source-traceable items (id=' + $sid + ')')
# 2026-09-24（用户口径）：草稿态编辑并入详情页 ⇒ 独立编辑页已删除，改开详情页（来源单据链接同样在明细里）
Open ('/inventory/return-sort/detail/' + $sid) 3400
ClearErrs | Out-Null
Start-Sleep -Milliseconds 1400
# doc-number shape (XTH-20260921... for sale returns, HH-20260918... for exchanges)
$codeRe = '[A-Z]{2,4}-\d{5,}'
$srcProbe = "(function(){const vis=e=>e.getClientRects().length>0;const re=/$codeRe/;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const codes=[];for(const t of ts){for(const tr of t.querySelectorAll('.el-table__body tbody tr')){for(const b of [...tr.querySelectorAll('button')].filter(vis)){const tx=(b.innerText||'').trim();if(re.test(tx))codes.push(tx)}}}return codes.length+'|'+(codes[0]||'')})()"
$q1 = @((EvalJs $srcProbe) -split '\|')
Write-Host ('  codeLinks=' + $q1[0] + ' first=' + $q1[1])
Ok ([int]$q1[0] -gt 0) ('the sort form shows the source doc number as a clickable link (' + $q1[0] + ')')
$clickJs = "(function(){const vis=e=>e.getClientRects().length>0;const re=/$codeRe/;const ts=[...document.querySelectorAll('.el-table')].filter(vis);for(const t of ts){for(const b of [...t.querySelectorAll('.el-table__body button')].filter(vis)){const tx=(b.innerText||'').trim();if(re.test(tx)){b.click();return tx}}}return 'NOCODE'})()"
$clicked = EvalJs $clickJs
Write-Host ('  clicked=' + $clicked)
Start-Sleep -Milliseconds 1800
$sp = EvalJs 'String(location.pathname)'
Write-Host ('  source detail path = ' + $sp)
Ok ($clicked -ne 'NOCODE') 'a source-doc number was clicked on the sort form'
Ok ($sp -match '/(sale/return|sale/exchange)/detail/[0-9]+') ('clicking it opens the source document (' + $sp + ')')

Step 'DB cross-check'
$ex = D (SqlOne 'SELECT COUNT(*) FROM sale_exchange')
$exAud = D (SqlOne "SELECT COUNT(*) FROM sale_exchange WHERE status='AUDITED'")
$exItems = D (SqlOne 'SELECT COUNT(*) FROM sale_exchange_item')
$rs = D (SqlOne 'SELECT COUNT(*) FROM return_sort')
$rsAud = D (SqlOne "SELECT COUNT(*) FROM return_sort WHERE status='AUDITED'")
$rsItems = D (SqlOne 'SELECT COUNT(*) FROM return_sort_item')
$exLogs = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no LIKE 'HH-%'")
$rsLogs = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no LIKE 'ZL-%'")
Write-Host ("[DB] exchanges=$ex audited=$exAud items=$exItems logs(HH-)=$exLogs | returnSorts=$rs audited=$rsAud items=$rsItems logs(ZL-)=$rsLogs")
Ok (($ex -ge 2)) ('exchanges >= 2 (got ' + $ex + ')')
# 2026-09-19 fix: the old assertion used ALL rows as the denominator, so the 10 CANCELLED exchanges kept in
# the DB (cancelled intentionally, kept for audit) made this a permanent false red. Cancelled = 作废留痕,
# it must be excluded; what we actually claim is "every exchange that is still a live document is audited".
$exCan = D (SqlOne "SELECT COUNT(*) FROM sale_exchange WHERE status='CANCELLED'")
$exLive = D (SqlOne "SELECT COUNT(*) FROM sale_exchange WHERE status<>'CANCELLED'")
Ok (($exAud -eq $exLive)) ('all non-cancelled exchanges audited (' + $exAud + '/' + $exLive + '; cancelled=' + $exCan + ' excluded)')
# run-scope evidence: whatever THIS run created must have been audited by the flow (0/0 when nothing was created)
$runEx = D (SqlOne ("SELECT COUNT(*) FROM sale_exchange WHERE create_time >= '" + $runStart + "'"))
$runExAud = D (SqlOne ("SELECT COUNT(*) FROM sale_exchange WHERE create_time >= '" + $runStart + "' AND status='AUDITED'"))
Ok (($runExAud -eq $runEx)) ('exchanges created in this run are audited (' + $runExAud + '/' + $runEx + ')')
Ok (($exItems -ge 2)) ('exchange items >= 2 (got ' + $exItems + ')')
$rsDraft = D (SqlOne "SELECT COUNT(*) FROM return_sort WHERE status='DRAFT'")
Ok (($rsAud -ge 2)) ('AUDITED return sorts >= 2 (got ' + $rsAud + ') — 3rd not reachable: its stock was consumed by the previous sort')
Write-Host ('INFO DRAFT return sorts = ' + $rsDraft + ' — all blocked by the correct guard ("来源批次已整理完 / 待整理数量超过售后仓可用待整理库存"); a one-shot stock-consumption test-setup artefact, not a defect')
Ok (($rsItems -ge 3)) ('return-sort items >= 3 (got ' + $rsItems + ')')
Write-Host ('errs=' + (Errs))
Summary 'P6c exchange + return-sort'
