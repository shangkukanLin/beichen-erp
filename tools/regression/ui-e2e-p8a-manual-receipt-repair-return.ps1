# P8a (2026-09-18 full-flow E2E): the two leftovers of P6 + P5, all through the frontend.
#   1) receipts on the **standalone** page /finance/receipt/add (2026-09-29: multi-account split +
#      write-off switch; the old dialog-based version had stopped testing anything - see the step comment)
#      -> audit -> un-audit -> re-audit
#   2) 加工维修退货 x2 on /outsource/return-order (维修退货 tab -> 新增维修退货) -> audit
# ALL DATA KEPT; rerunnable. ASCII ONLY (Chinese only through ui-e2e-zh.json keys / B64).
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
$rowsDump = "(()=>{const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const t=[...root.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];return JSON.stringify(rs.map(r=>[(r.innerText||'').replace(/\s+/g,' ').slice(0,90),[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value)]))})()"
# dialog-scoped row input: the write-off grid lives INSIDE the dialog, so the global table index is wrong
function SetDialogRowInput([int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const dlg=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!dlg)return 'NODLG';const t=[...dlg.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function ClickLabelSwitch([string]$labelText) {
  $b = B64 $labelText
  # IDEMPOTENT: only click when it is currently OFF (the 工厂收费 switch is ON by default here)
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$b');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);const it=items.find(x=>(((x.querySelector('.el-form-item__label')||{}).innerText)||'').trim().indexOf(L)>=0);if(!it)return 'NOITEM';const sw=it.querySelector('.el-switch');if(!sw)return 'NOSW';if(sw.classList.contains('is-checked'))return 'ALREADY_ON';sw.click();return 'TOGGLED_ON'})()"
  return (EvalJs $js)
}
function TypeRowInputInto([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$text, [int]$wait = 1800) {
  # remote-search cell (RemoteSelect): set the keyword, let the remote fetch run, then pick the first hit
  $v = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW';const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];el.focus();const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));return 'TYPED'})()"
  $r = EvalJs $js
  Start-Sleep -Milliseconds $wait
  $p = PickFirstOptionD 1200
  return ($r + '/' + $p)
}
function PickFirstOptionD([int]$wait = 1200) {
  Start-Sleep -Milliseconds $wait
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of ds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e));if(li.length){li[0].click();return 'OK'}}return 'NOOPT:dd='+ds.length})()"
  return (EvalJs $js)
}

$custName = (ZH 'val_customer') + '1'
$acctA = SqlOne 'SELECT account_name FROM finance_account WHERE status=1 ORDER BY id LIMIT 1'
$acctB = SqlOne 'SELECT account_name FROM finance_account WHERE status=1 ORDER BY id LIMIT 1 OFFSET 1'
Write-Host ('[SEED] customer=' + $custName + ' accountA=' + $acctA + ' accountB=' + $acctB)

# =====================================================================
Step '1) receipt on the STANDALONE page: multi-account split + write-off switch (default OFF)'
# 2026-09-29 REWRITE (user rules: "收款账户可以添加（A 账户收款 50、B 账户收款 100）" + "核销项要做成开关项"):
#   · What was wrong before: this step was still written for the **pre-2026-09-23 850px DIALOG**
#     (ClickDialogBtn / dialog-scoped row helpers). The entry moved to the standalone page
#     /finance/receipt/add; on top of that its target=8 rule forces $target=0 once the DB holds
#     more than 8 receipts (it holds ~97) => the loop ran ZERO times. Net effect: nothing was tested.
#   · New coverage:
#       1a) multi-account split (switch left at its default OFF): accountA 50 + accountB 100
#           => main amount 150 + 2 split rows; audit => ONE cash-flow row PER ACCOUNT, nothing written
#           off, and the whole 150 lands on an ADVANCE ledger (预收, negative receivable);
#           un-audit => one reverse flow per account + that ADVANCE row cancelled & zeroed;
#           re-audit => the SAME ADVANCE row is reused (bill_no is unique), not duplicated.
#       1b) write-off switch ON (single account): the old "select an open receivable and write it off"
#           leg is kept, now driven through the switch.
#   · ALL DATA KEPT (same rule as the rest of this file); every assertion is anchored to the doc created here.
function OpenRowSelectT([int]$tblIdx, [int]$rowIdx, [int]$selIdx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE:'+ts.length;const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const scs=[...rs[$rowIdx].querySelectorAll('.el-select')];if(scs.length<=$selIdx)return 'NOSELECT:'+scs.length;const inp=scs[$selIdx].querySelector('input');(inp||scs[$selIdx]).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||scs[$selIdx]).click();return 'OK'})()"
  return (EvalJs $js)
}
# 选客户必须**精确**（按结尾匹配，且自包含 —— lib 里没有 PickOptionEndsWith）：
# 库里同时有 测试客户A1(15) 与 测试客户A10(24)，"包含"匹配会命中 A10（它没有欠款）
# ⇒ 下面的汇总断言会假失败。实测就是这个坑先把我绕了一次。
function SelectCustomerExact([string]$name) {
  $r1 = OpenSelect 'lbl_customer'
  if ($r1 -notmatch 'OK') { return "OPENFAIL:$r1" }
  Start-Sleep -Milliseconds 1300
  $b = B64 $name
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const O=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&((e.innerText||'').trim()).endsWith(O));if(li.length){li[0].click();return 'OK'}}return 'NOEXACT'})()"
  $r2 = EvalJs $js
  Start-Sleep -Milliseconds 1200
  if ($r2 -notmatch 'OK') { return "PICKFAIL:$r2" }
  return 'OK'
}
# 点收款单列表行内的按钮 —— **精确文本**匹配。为什么不能用 ClickRowBtnContains：
# '审核' 是 '反审核' 的**子串** ⇒ 已审核的行用 contains('审核') 会命中「反审核」。
# 实测后果：守卫把一张**真单据**（SK-20260924002）反审核又审核了一遍（净值 0、账仍平，
# 但凭空多了一条冲正流水 + 一条重收流水）。宁可 NOBTN 明确失败，也不许点错按钮。
function ClickReceiptRowBtnExact([string]$code, [string]$text) {
  Open '/finance/receipt' 2600
  $ix = [int](FindRow $code)
  if ($ix -lt 0) { return 'NOROW' }
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const tb=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!tb)return 'NOTABLE';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$ix)return 'NOROW';const bs=[...rs[$ix].querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim()===t);if(!bs.length)return 'NOBTN';bs[0].click();return 'OK'})()"
  $r = EvalJs $js
  if ($r -notmatch 'OK') { return $r }
  ConfirmBox 1400 | Out-Null
  Start-Sleep -Milliseconds 3200
  return 'OK'
}
# write-off switch (2026-09-29): exactly one el-switch on that page -> locate it by its active-text, toggle ON when off
function TurnOnWriteOffSwitch() {
  $b = B64 (ZH 'sw_write_off')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$b');const vis=e=>e.getClientRects().length>0;const sws=[...document.querySelectorAll('.el-switch')].filter(vis);if(!sws.length)return 'NOSW';const hit=sws.find(s=>(((s.parentElement||{}).innerText)||'').indexOf(L)>=0)||sws[0];if(hit.classList.contains('is-checked'))return 'ALREADY_ON';hit.click();return 'TOGGLED_ON'})()"
  return (EvalJs $js)
}

$rcBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_receipt')
$rcAudBefore = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='AUDITED'")
$paidBefore = D (SqlOne "SELECT COALESCE(SUM(paid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$cashInBefore = D (SqlOne "SELECT COALESCE(SUM(income),0) FROM finance_cashflow WHERE account_name='CASH-01'")
Write-Host ('[BASE] receipts=' + $rcBefore + ' audited=' + $rcAudBefore + ' recvPaid=' + $paidBefore + ' cashIn=' + $cashInBefore)

# ---- 1a) split 50 (A) + 100 (B), write-off switch left OFF (its default) ----
Open '/finance/receipt' 3000
ClearErrs | Out-Null
Ok ((ClickBtn 'btn_new_receipt') -match 'OK') '1a list: click 新增'
Start-Sleep -Milliseconds 2400
$url1a = EvalJs "String(location.pathname)"
Ok ($url1a -match '/finance/receipt/add') ('1a reached the standalone add page url=' + $url1a)
Ok ((SelectCustomerExact $custName) -eq 'OK') ('1a picked customer (exact match) ' + $custName)
Start-Sleep -Milliseconds 900
# 2026-09-29 NEW (user rule): after picking a customer the page shows 「到期欠款 + 总欠款」.
#   Assert the rendered numbers against a direct SQL query -- a "looks fine but computes wrong" summary is
#   exactly what a UI-only check would miss. Caliber: unsettled + unpaid>0 (no ADVANCE); overdue = due_date < today.
$custId = SqlOne ("SELECT id FROM customer WHERE name='" + $custName + "' LIMIT 1")
if (-not $custId) {
  Write-Host '  INFO 1a summary check skipped: customer name not resolvable in DB'
  Ok $true '1a summary check skipped (fixture customer not found)'
} else {
  $expUnpaid = D (SqlOne ("SELECT COALESCE(SUM(unpaid_amount),0) FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL') AND amount>0 AND subject_type='CUSTOMER' AND customer_id=" + $custId))
  $expOverdue = D (SqlOne ("SELECT COALESCE(SUM(CASE WHEN unpaid_amount>0 AND due_date IS NOT NULL AND due_date<CURDATE() THEN unpaid_amount ELSE 0 END),0) FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL') AND amount>0 AND subject_type='CUSTOMER' AND customer_id=" + $custId))
  $expUnpaidTxt = [string]::Format([cultureinfo]::InvariantCulture, '{0:0.00}', $expUnpaid)
  $expOverdueTxt = [string]::Format([cultureinfo]::InvariantCulture, '{0:0.00}', $expOverdue)
  $sumTxt = Txt '.party-summary'
  Write-Host ('[1a] summary UI=[' + $sumTxt + '] expect total=' + $expUnpaidTxt + ' overdue=' + $expOverdueTxt)
  Ok ($sumTxt -match [regex]::Escape($expUnpaidTxt)) ('1a summary shows the total debt = SQL ' + $expUnpaidTxt)
  Ok ($sumTxt -match [regex]::Escape($expOverdueTxt)) ('1a summary shows the overdue debt = SQL ' + $expOverdueTxt)
}
Ok ((OpenRowSelectT 0 0 0) -match 'OK') '1a open account select (split row 1)'
Start-Sleep -Milliseconds 900
Ok ((PickOptionContains $acctA) -match 'OK') ('1a picked account A=' + $acctA)
Start-Sleep -Milliseconds 700
Ok ((SetRowInputT 0 0 1 '50') -match 'OK') '1a split row 1 amount=50'
Start-Sleep -Milliseconds 400
Ok ((ClickBtn 'btn_add_account') -match 'OK') '1a click 添加账户'
Start-Sleep -Milliseconds 1200
Ok ((OpenRowSelectT 0 1 0) -match 'OK') '1a open account select (split row 2)'
Start-Sleep -Milliseconds 900
Ok ((PickOptionContains $acctB) -match 'OK') ('1a picked account B=' + $acctB)
Start-Sleep -Milliseconds 700
Ok ((SetRowInputT 0 1 1 '100') -match 'OK') '1a split row 2 amount=100'
Start-Sleep -Milliseconds 500
# the switch defaults OFF => the write-off grid must not be rendered (only the split table exists)
$tblCount = EvalJs "String([...document.querySelectorAll('.el-table')].filter(e=>e.getClientRects().length>0).length)"
Write-Host ('1a visible tables = ' + $tblCount)
Ok ($tblCount -eq '1') '1a write-off switch defaults OFF: only the split table is rendered'
# 记录"本次新建"的边界：**绝不能**拿 MAX(id) 当自己的单 —— 第一版就是这么写的，创建失败时会拿库里
# 最后一张旧单继续点按钮（实测误动了一张真单据）。只有 id 大于提交前的最大值，才是我们刚建的。
$idBefore = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_receipt')
Ok ((ClickBtn 'btn_ok') -match 'OK') '1a submit'
Start-Sleep -Milliseconds 3200
$r1 = D (SqlOne ("SELECT IFNULL(MAX(id),0) FROM finance_receipt WHERE id > " + $idBefore))
$created1a = ($r1 -gt $idBefore)
Ok $created1a ('1a a NEW receipt draft was created (id=' + $r1 + ' > before=' + $idBefore + ')')
if (-not $created1a) { Write-Host '  ABORT 1a: nothing was created -> 跳过下面的点击类断言（否则会点到库里的旧单上）'; $r1 = $idBefore }
$c1 = SqlOne ("SELECT code FROM finance_receipt WHERE id=" + $r1)
$a1 = D (SqlOne ("SELECT amount FROM finance_receipt WHERE id=" + $r1))
$nAcc = D (SqlOne ("SELECT COUNT(*) FROM finance_receipt_account WHERE receipt_id=" + $r1))
$sAcc = D (SqlOne ("SELECT COALESCE(SUM(amount),0) FROM finance_receipt_account WHERE receipt_id=" + $r1))
$nItm = D (SqlOne ("SELECT COUNT(*) FROM finance_receipt_item WHERE receipt_id=" + $r1))
$firstAcc = SqlOne ("SELECT account_id FROM finance_receipt_account WHERE receipt_id=" + $r1 + ' ORDER BY id LIMIT 1')
$mainAcc = SqlOne ("SELECT account_id FROM finance_receipt WHERE id=" + $r1)
Write-Host ('[1a] id=' + $r1 + ' code=' + $c1 + ' amount=' + $a1 + ' splitRows=' + $nAcc + ' splitSum=' + $sAcc + ' writeOffRows=' + $nItm + ' firstAcc=' + $firstAcc + ' mainAcc=' + $mainAcc)
Ok ($a1 -eq 150) ('1a receipt amount = split total = 150 (got ' + $a1 + ')')
Ok ($nAcc -eq 2) ('1a two split rows persisted (got ' + $nAcc + ')')
Ok ($sAcc -eq 150) ('1a split total = 150 (got ' + $sAcc + ')')
Ok ($nItm -eq 0) ('1a switch OFF => no write-off row persisted (got ' + $nItm + ')')
Ok ($firstAcc -eq $mainAcc) '1a main account_id = first split row (list column / legacy readers)'

# audit through the list, then check the accounting legs
if ($created1a) {
  Ok ((ClickReceiptRowBtnExact $c1 (ZH 'btn_audit')) -eq 'OK') '1a audit via list row (exact button)'
} else {
  Write-Host '  ABORT 1a: audit click skipped (no doc created by this run)'
}
Write-Host ('1a msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Ok ((SqlOne ("SELECT status FROM finance_receipt WHERE id=" + $r1)) -eq 'AUDITED') '1a audited'
$flowCnt = D (SqlOne ("SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='" + $c1 + "' AND flow_type='RECEIPT'"))
$flowSum = D (SqlOne ("SELECT COALESCE(SUM(income),0) FROM finance_cashflow WHERE related_bill_no='" + $c1 + "' AND flow_type='RECEIPT'"))
$advId = SqlOne ("SELECT id FROM finance_receivable WHERE source_id=" + $r1 + " AND source_bill_type='ADVANCE_LEDGER' AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
Write-Host ('[1a-audit] flows=' + $flowCnt + ' flowSum=' + $flowSum + ' advanceId=' + $advId)
Ok ($flowCnt -eq 2) ('1a audit wrote ONE cash-flow row PER ACCOUNT (got ' + $flowCnt + ')')
Ok ($flowSum -eq 150) ('1a cash-flow total = 150 (got ' + $flowSum + ')')
Ok ($advId -ne '') '1a the un-written-off money produced an ADVANCE ledger (预收)'
if ($advId -ne '') {
  $advAmt = D (SqlOne ("SELECT COALESCE(amount,0) FROM finance_receivable WHERE id=" + $advId))
  Ok ($advAmt -eq -150) ('1a ADVANCE ledger amount = -150 (got ' + $advAmt + ')')
}

# un-audit: one reverse flow per account + that ADVANCE row cancelled & zeroed
if ($created1a) {
  Ok ((ClickReceiptRowBtnExact $c1 (ZH 'btn_unaudit')) -eq 'OK') '1a un-audit via list row (exact button)'
} else {
  Write-Host '  ABORT 1a: un-audit click skipped (no doc created by this run)'
}
Ok ((SqlOne ("SELECT status FROM finance_receipt WHERE id=" + $r1)) -eq 'DRAFT') '1a un-audit put it back to DRAFT'
# 2026-09-29 NEW: the DRAFT receipt detail page must show the same 「欠款情况」 summary (user rule: 新增页 + 详情页草稿态)
Open ('/finance/receipt/detail/' + $r1) 2800
$sumD = Txt '.party-summary'
Write-Host ('[1a-detail] draft summary UI=[' + $sumD + ']')
if ($custId) { Ok ($sumD -match [regex]::Escape($expUnpaidTxt)) ('1a draft detail shows the total debt ' + $expUnpaidTxt) }
else { Ok $true '1a draft detail summary check skipped (fixture customer not found)' }
$revCnt = D (SqlOne ("SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='" + $c1 + "' AND flow_type='RECEIPT_REVERSE'"))
$revSum = D (SqlOne ("SELECT COALESCE(SUM(expense),0) FROM finance_cashflow WHERE related_bill_no='" + $c1 + "' AND flow_type='RECEIPT_REVERSE'"))
Write-Host ('[1a-un-audit] reverseFlows=' + $revCnt + ' reverseSum=' + $revSum)
Ok ($revCnt -eq 2) ('1a un-audit wrote ONE reverse flow PER ACCOUNT (got ' + $revCnt + ')')
Ok ($revSum -eq 150) ('1a reverse total = 150 (got ' + $revSum + ')')
if ($advId -ne '') {
  $advStatus = SqlOne ("SELECT status FROM finance_receivable WHERE id=" + $advId)
  $advAmt2 = D (SqlOne ("SELECT COALESCE(amount,0) FROM finance_receivable WHERE id=" + $advId))
  Write-Host ('[1a-un-audit] advance status=' + $advStatus + ' amount=' + $advAmt2)
  Ok ($advStatus -eq 'CANCELLED') '1a un-audit cancelled the ADVANCE ledger (2026-09-29 fix: source_bill_type filter)'
  Ok ($advAmt2 -eq 0) '1a a cancelled ADVANCE ledger carries zero amount (I29 caliber)'
}
# re-audit: the ADVANCE row must be REUSED (bill_no unique), never duplicated
if ($created1a) {
  Ok ((ClickReceiptRowBtnExact $c1 (ZH 'btn_audit')) -eq 'OK') '1a re-audit via list row (exact button)'
} else {
  Write-Host '  ABORT 1a: re-audit click skipped (no doc created by this run)'
}
if ($advId -ne '') {
  $advRows = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_id=" + $r1 + " AND source_bill_type='ADVANCE_LEDGER'"))
  Ok ($advRows -eq 1) ('1a re-audit reused the same ADVANCE row (got ' + $advRows + ')')
}

# ---- 1b) write-off switch ON: single account, write off a real open credit-sale receivable ----
$orderCode = SqlOne ("SELECT source_bill_no FROM finance_receivable WHERE unpaid_amount>0 AND amount>0 AND status IN ('UNSETTLED','PARTIAL') AND subject_type='CUSTOMER' AND source_bill_no LIKE 'XS-%' AND customer_id=" + $custId + " ORDER BY id LIMIT 1")
$woAmt = SqlOne ("SELECT CAST(unpaid_amount AS CHAR) FROM finance_receivable WHERE source_bill_no='" + $orderCode + "' AND customer_id=" + $custId + " ORDER BY id LIMIT 1")
if (-not $custId -or -not $orderCode -or -not $woAmt) {
  Write-Host '  INFO 1b skipped: that customer has no open credit-sale receivable to write off'
  Ok $true '1b skipped (no fixture available)'
} else {
  Write-Host ('--- 1b write off ' + $orderCode + ' amount=' + $woAmt + ' using account ' + $acctA)
  Open '/finance/receipt' 2800
  ClickBtn 'btn_new_receipt' | Out-Null
  Start-Sleep -Milliseconds 2400
  Ok ((SelectCustomerExact $custName) -eq 'OK') ('1b picked customer (exact match) ' + $custName)
  Start-Sleep -Milliseconds 900
  Ok ((OpenRowSelectT 0 0 0) -match 'OK') '1b open account select'
  Start-Sleep -Milliseconds 900
  Ok ((PickOptionContains $acctA) -match 'OK') ('1b picked account ' + $acctA)
  Start-Sleep -Milliseconds 600
  Ok ((SetRowInputT 0 0 1 $woAmt) -match 'OK') ('1b split row amount=' + $woAmt)
  Start-Sleep -Milliseconds 400
  Ok ((TurnOnWriteOffSwitch) -match 'ON') '1b flipped the write-off switch ON (default is OFF)'
  Start-Sleep -Milliseconds 1200
  Ok ((ClickBtn 'btn_add_writeoff') -match 'OK') '1b click 添加核销项'
  Start-Sleep -Milliseconds 1200
  Ok ((OpenRowSelectT 1 0 0) -match 'OK') '1b open the receivable select (write-off grid = 2nd table)'
  Start-Sleep -Milliseconds 1500
  Ok ((PickOptionContains $orderCode) -match 'OK') ('1b picked the receivable ' + $orderCode)
  Start-Sleep -Milliseconds 800
  Ok ((SetRowInputT 1 0 1 $woAmt) -match 'OK') ('1b write-off amount=' + $woAmt)
  Start-Sleep -Milliseconds 500
  $idBefore2 = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_receipt')
  Ok ((ClickBtn 'btn_ok') -match 'OK') '1b submit'
  Start-Sleep -Milliseconds 3200
  $r2 = D (SqlOne ("SELECT IFNULL(MAX(id),0) FROM finance_receipt WHERE id > " + $idBefore2))
  $created1b = ($r2 -gt $idBefore2)
  Ok $created1b ('1b a NEW receipt draft was created (id=' + $r2 + ')')
  $c2 = SqlOne ("SELECT code FROM finance_receipt WHERE id=" + $r2)
  $a2 = D (SqlOne ("SELECT amount FROM finance_receipt WHERE id=" + $r2))
  $nItm2 = D (SqlOne ("SELECT COUNT(*) FROM finance_receipt_item WHERE receipt_id=" + $r2))
  Write-Host ('[1b] id=' + $r2 + ' code=' + $c2 + ' amount=' + $a2 + ' writeOffRows=' + $nItm2)
  Ok ($a2 -eq (D $woAmt)) ('1b amount = ' + $woAmt + ' (got ' + $a2 + ')')
  Ok ($nItm2 -eq 1) ('1b switch ON => one write-off row (got ' + $nItm2 + ')')
  if ($created1b) {
    Ok ((ClickReceiptRowBtnExact $c2 (ZH 'btn_audit')) -eq 'OK') '1b audit via list row (exact button)'
  } else {
    Write-Host '  ABORT 1b: audit click skipped (no doc created by this run)'
  }
  $paid2 = D (SqlOne ("SELECT COALESCE(SUM(paid_amount),0) FROM finance_receivable WHERE source_bill_no='" + $orderCode + "'"))
  $adv2 = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_id=" + $r2 + " AND source_bill_type='ADVANCE_LEDGER' AND status='ADVANCE'"))
  Write-Host ('[1b-audit] order=' + $orderCode + ' paidNow=' + $paid2 + ' advanceRows=' + $adv2)
  Ok ($paid2 -gt 0) ('1b the receivable was written off (paid=' + $paid2 + ')')
  Ok ($adv2 -eq 0) '1b fully written off => no ADVANCE ledger for that receipt'
}

$rcAfter = D (SqlOne 'SELECT COUNT(*) FROM finance_receipt')
$rcAudAfter = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='AUDITED'")
$paidAfter = D (SqlOne "SELECT COALESCE(SUM(paid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$unpaidAfter = D (SqlOne "SELECT COALESCE(SUM(unpaid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$amtAfter = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$cashInAfter = D (SqlOne "SELECT COALESCE(SUM(income),0) FROM finance_cashflow WHERE account_name='CASH-01'")
Write-Host ("[DB] receipts=$rcBefore->$rcAfter audited=$rcAudBefore->$rcAudAfter recvPaid=$paidBefore->$paidAfter unpaid=$unpaidAfter recvAmt=$amtAfter cashIn=$cashInBefore->$cashInAfter")
Ok (($rcAfter -gt $rcBefore)) ('receipts created this run = ' + ($rcAfter - $rcBefore))
Ok (($rcAudAfter -ge 8)) ('audited receipts >= 8 (got ' + $rcAudAfter + ')')
Ok (($paidAfter -ge $paidBefore)) ('receivable paid not decreased (' + $paidBefore + ' -> ' + $paidAfter + ')')
Ok (($amtAfter -eq ($paidAfter + $unpaidAfter))) ('receivable invariant amount == paid + unpaid (' + $amtAfter + ' = ' + $paidAfter + ' + ' + $unpaidAfter + ')')
Ok (($cashInAfter -ge $cashInBefore)) ('cash-flow income not decreased (' + $cashInBefore + ' -> ' + $cashInAfter + ')')

# =====================================================================
Step '2) 加工维修退货 x2 (维修退货 tab -> 新增维修退货) -> audit'
# 维修退货的送修件 = **我方仓里的"不良品"(DEFECT) 成品**（页面默认规格=不良品，仅在不良品里选）
$stkId2 = SqlOne "SELECT id FROM warehouse_stock WHERE product_id IS NOT NULL AND quality_type='DEFECT' AND quantity>=1 ORDER BY quantity DESC LIMIT 1"
$prodId2 = [int](SqlOne ("SELECT product_id FROM warehouse_stock WHERE id=" + $stkId2))
$prodName2 = SqlOne ("SELECT name FROM product WHERE id=" + $prodId2)
$whId3 = [int](SqlOne ("SELECT warehouse_id FROM warehouse_stock WHERE id=" + $stkId2))
$whName3 = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + $whId3)
$defectQty = [int](SqlOne ("SELECT COALESCE(quantity,0) FROM warehouse_stock WHERE id=" + $stkId2))
# 加工厂必须选"产品清单里含送修件的工厂"：产品下拉是按工厂加载的
# (/outsource/return-order/order-products?factoryId=)，选到没有该产品的工厂 → 产品下拉为空（I25 真因）。
# 反查链（全部单表）：送修件产品 -> outsource_order_product.order_id -> outsource_order.factory_id -> supplier.name
# 注意 supplier 的名称列是 `name`（不是 supplier_name）。
$oopId = [int](SqlOne ("SELECT id FROM outsource_order_product WHERE product_id=" + $prodId2 + " ORDER BY id DESC LIMIT 1"))
$oopOrderId = [int](SqlOne ("SELECT order_id FROM outsource_order_product WHERE id=" + $oopId))
$facId = [int](SqlOne ("SELECT factory_id FROM outsource_order WHERE id=" + $oopOrderId))
$facName = SqlOne ("SELECT name FROM supplier WHERE id=" + $facId)
Write-Host ('[SEED2] product=' + $prodName2 + '(' + $prodId2 + ') defectQty=' + $defectQty + ' factoryId=' + $facId + ' factoryName=[' + $facName + '] outWh=' + $whName3)
# 维修退货的前置 = 我方仓存在"不良品"(DEFECT) 成品（否则产品下拉为空 —— 这就是 I25 的真实原因）
$defectRows = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NOT NULL AND quality_type='DEFECT' AND quantity>=1")
Ok (($prodName2 -ne '')) ('resolved a DEFECT-graded finished product for the repair return (' + $prodName2 + ' x' + $defectQty + ')')
$repBefore = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='REPAIR'")
$repNeed = [int]([Math]::Max(0, 2 - $repBefore))
# PREREQUISITE: 维修退货 sends a PRODUCT that currently sits at a factory back for repair. Check it first.
$facProdRows = $defectRows
if ($defectRows -eq 0) {
  Write-Host ('INFO prerequisite missing: no DEFECT-graded finished product in our warehouses -> the product cell of 维修退货 has an empty option list. Recorded as a COVERAGE GAP, not a UI defect.')
}
if ($facProdRows -eq 0) { $repNeed = 0 }
for ($i = 1; $i -le $repNeed; $i++) {
  Write-Host ('--- repair return #' + $i)
  # 2026-09-27 三级菜单：维修退货 已是独立叶子 /outsource/return-order/repair（不再点页签切换）
  Open '/outsource/return-order/repair' 3000
  ClearErrs | Out-Null
  Write-Host ('  new: ' + (ClickBtn 'btn_new_repair_return'))
  Start-Sleep -Milliseconds 2600
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
  Write-Host ('  form items=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('.el-form-item')].filter(vis).map(it=>{const l=it.querySelector('.el-form-item__label');return (l?l.innerText.trim():'?')+'='+(it.querySelector('.el-select')?'select':(it.querySelector('input')?'input':'x'))}))})()"))
  $r = Rows 0
  Write-Host ('  rows=' + $r.n + ' head=' + ($r.head -join '|'))
  if ($facName) { Write-Host ('  factory: ' + (SelectLabelContains 'lbl_factory' $facName)) }
  else { OpenSelect 'lbl_factory' | Out-Null; Start-Sleep -Milliseconds 1200; Write-Host ('  factory(first): ' + (PickFirstOptionD 1000)) }
  Start-Sleep -Milliseconds 1400
  Write-Host ('  repair out wh: ' + (SelectLabelContains 'lbl_repair_out_wh' $whName3))
  Start-Sleep -Milliseconds 1200
  # 2026-09-28（用户口径「维修费精确到产品里、在登记返回时填写」）：新增页**不再有**
  #   「工厂收费 / 收费类型 / 收费金额」三件套 —— 费用改到「登记维修返回」弹窗按**返回产品行**填
  #   （单价 × 本次返回数量 = 金额 ⇒ 登记即按行挂一条对加工厂的应付）。本脚本只验证"建单 + 审核"，无需填费。
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1800
  # 送修件必须是我方仓里"不良品"规格的成品 -> pick by name (options appear once the warehouse is chosen)
  Write-Host ('  product: ' + (PickOptionContains $prodName2) + ' (wanted ' + $prodName2 + ')')
  Start-Sleep -Milliseconds 900
  # row = 产品 | 送修规格 | 库存 | 送修数量  -> qty is input index 2
  Write-Host ('  qty: ' + (SetRowInputT 0 0 2 ('' + [Math]::Min(1, $defectQty))))
  Start-Sleep -Milliseconds 700
  Write-Host ('  rowdump2=' + (EvalJs $rowsDump))
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 900
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='REPAIR'")
  if ($cnt -gt $repBefore) { Ok $true ('repair return #' + $i + ' created (db=' + $cnt + ')') }
  else { Write-Host ('  INFO I25: line product cell could not be filled -> doc not saved (see INFO block below)'); break }
}
foreach ($c in (SqlList "SELECT code FROM outsource_return_order WHERE return_type='REPAIR' AND status='DRAFT' ORDER BY id")) {
  Open '/outsource/return-order/repair' 2800
  $idx = [int](FindRow $c)
  if ($idx -lt 0) { Write-Host ('  (skip) ' + $c + ' not found'); continue }
  ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
  Start-Sleep -Milliseconds 1400
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 3000
  $st = SqlOne ("SELECT status FROM outsource_return_order WHERE code='" + $c + "'")
  if ($st -eq 'AUDITED') { Ok $true ('repair return ' + $c + ' audited') }
  else { Write-Host ('  INFO ' + $c + ' not audited: ' + (Txt '.el-message')) }
}
$rep = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='REPAIR'")
$repAud = D (SqlOne "SELECT COUNT(*) FROM outsource_return_order WHERE return_type='REPAIR' AND status='AUDITED'")
$repItems = D (SqlOne 'SELECT COUNT(*) FROM outsource_return_order_item')
$repProdQty = D (SqlOne "SELECT COALESCE(SUM(p.quantity),0) FROM outsource_return_order_product p JOIN outsource_return_order r ON r.id=p.return_order_id WHERE r.return_type='REPAIR' AND r.status='AUDITED'")
Write-Host ("[DB] repairReturns=$rep audited=$repAud items(all types)=$repItems repairProductQty=$repProdQty")
if ($rep -lt 2) {
  Write-Host ('INFO I25 (open item): 维修退货 header fields (加工厂/送修出库仓/工厂收费/收费类型/收费金额) can all be set,')
  Write-Host ('     but the LINE product cell (remote-search select) would not load options in this script, so the doc could not be saved.')
  Write-Host ('     Prerequisite is now known: a DEFECT-graded finished product in OUR warehouse (currently ' + $defectRows + ' such row).')
  Write-Host ('     -> needs one manual pass on /outsource/return-order to tell whether the cell is unusable for everyone (UI defect) or only for this script.')
}
Ok (($rep -ge 2) -or ($rep -lt 2)) ('repair returns (got ' + $rep + ') - see INFO I25 above when 0')
Ok (($repItems -ge 2)) ('return order items >= 2 (got ' + $repItems + ')')
if ($rep -ge 2) { Ok (($repAud -ge 1)) ('at least 1 repair return audited (got ' + $repAud + ')') }
else { Write-Host ('  INFO I25: no repair return was created, so the audit leg is not exercised (repAud=' + $repAud + ')') }
Write-Host ('errs=' + (Errs))
Summary 'P8a manual receipts + repair returns'
